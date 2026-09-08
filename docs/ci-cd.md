# CI and release pipeline

Two workflows, one fastlane configuration:

| What | Where | Trigger |
| --- | --- | --- |
| Build and test | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) | push to `main`, pull request against `main` |
| Ship to the Play internal track | [`.github/workflows/release.yml`](../.github/workflows/release.yml) | a GitHub release is published |
| What a release actually does | [`fastlane/Fastfile`](../fastlane/Fastfile) | called by the release workflow |

fastlane sits between the workflow and the build for one reason: the second
platform is already planned (ARCHITECTURE.md section 1.4, CarPlay). The
Fastfile has an `ios` platform block for TestFlight next to the `android`
one, and the GitHub job stays a thin wrapper that only hands over secrets.

## CI

Three parallel jobs, matching what AGENTS.md demands before anything counts
as done:

- **Android build + shared tests** — `:androidApp:assembleDebug` and
  `:shared:jvmTest`. Test reports are uploaded on failure, the debug APK
  always.
- **Kotlin/Native (iosMain)** — `:shared:compileKotlinIosSimulatorArm64`.
  Compiles `iosMain` against the real cinterop bindings; linking is skipped
  on Linux, so no framework and no Objective-C header come out of it.
- **Swift syntax check** — `tools/check-swift.sh`. If the runner image has no
  `swiftc`, the job emits a warning annotation and passes: the check was
  never load-bearing, and a red build here would say nothing about the code.

CI gets **no secrets**. Pull requests from forks never see them anyway, so
the build has to work without: the app falls back to labeled demo data and
to the map placeholder. That path is therefore continuously exercised.

Not part of CI (deliberately): the live contract tests against
OpenChargeMap, OSRM, Nominatim and BNetzA. They only run with `OCM_LIVE=1`
and friends — a build that goes red because a third-party service hiccups
says nothing about this repository.

## Releasing

1. Bump nothing by hand. `versionName` comes from the tag, `versionCode`
   from Play (see below).
2. Cut a GitHub release on a tag such as `v0.3.0`.
3. The workflow builds the signed AAB and uploads it to the **internal test
   track**, release status `completed` — testers on the internal list get it
   within minutes.

A prerelease ships too. If that is not wanted, the job needs
`if: ${{ !github.event.release.prerelease }}`.

To repeat a failed upload, use **Run workflow** on the Release workflow and
give it the tag. Rebuilding the same tag is safe.

### Where the version numbers come from

`versionName` is the tag without a leading `v`.

`versionCode` is asked of Play: fastlane reads the highest code across
`internal`, `alpha`, `beta` and `production` and adds one. Deriving it
locally would be more fragile — a run number restarts at 1 after a
repository move, and a re-cut tag would produce the same number twice, which
Play refuses.

Consequence: **the very first bundle has to be uploaded through the Play
Console by hand.** The API cannot create the app entry, and until a bundle
exists there is no code to increment. The lane says so instead of failing
obscurely.

A local build without `VERSION_CODE` in the environment keeps the
checked-in fallback (`androidApp/build.gradle.kts`), so nothing about
`assembleDebug` changes.

## Secrets

All of these are repository secrets (*Settings → Secrets and variables →
Actions*). Only `release.yml` reads them.

| Secret | Contents |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the upload keystore, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | alias of the key inside the keystore |
| `ANDROID_KEY_PASSWORD` | password of that key |
| `PLAY_SERVICE_ACCOUNT_JSON` | the service account JSON, whole file |
| `GOOGLE_MAPS_API_KEY` | Maps SDK key, goes into the manifest |
| `OPEN_CHARGE_MAP_API_KEY` | OpenChargeMap key, goes into `BuildConfig` |

### Keystore

Create it once and keep it — Play ties the app to this key, and a lost
upload key means asking Google to reset it:

```bash
keytool -genkeypair -v -keystore upload.keystore -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Encode it for the secret (`-w0`, otherwise the line breaks end up in the
secret and `base64 --decode` produces garbage):

```bash
base64 -w0 upload.keystore
```

The workflow decodes it into `$RUNNER_TEMP`, never into the workspace, and
deletes it again in an `always()` step. `*.keystore` and `*.jks` are in
`.gitignore`, but the safe place for the file is outside the working tree
entirely.

### Play service account

1. Play Console → *Setup → API access* → link a Google Cloud project.
2. Create a service account there, grant it the **Release manager** role (or
   at minimum: release to testing tracks) for this app.
3. Create a JSON key for it, and paste the entire file into
   `PLAY_SERVICE_ACCOUNT_JSON`.

Permissions in the Play Console take a while to propagate; a fresh account
can answer with 403 for an hour or so.

### A local build signs nothing

Without a keystore no `signingConfig` is created at all, and
`:androidApp:bundleRelease` produces an unsigned artifact rather than
failing. To sign locally, put these into `local.properties` (they take
precedence over the environment):

```properties
releaseKeystoreFile=/absolute/path/upload.keystore
releaseKeystorePassword=...
releaseKeyAlias=upload
releaseKeyPassword=...
```

## iOS: what is prepared and what is not

`fastlane/Fastfile` has an `ios` platform with a `beta` lane (XcodeGen →
`build_app` → TestFlight). **It has never run.** It was written on Linux
without a Mac, without Xcode and without an App Store Connect account —
treat it as an ordered list of the required steps, not as a working lane.
No workflow calls it.

Before the first run, three things need deciding:

1. Apple's approval for `com.apple.developer.carplay-charging`. Without it,
   signing fails regardless of what is configured (ARCHITECTURE.md 1.4).
2. Signing: App Store Connect API key with automatic signing, or fastlane
   `match` with a certificates repository. `match` is the better fit as soon
   as a second machine signs.
3. Where the build number comes from — `iosApp/AutoApp/Info.plist` is
   hand-maintained, and `project.yml` deliberately does not regenerate it.

The job for it belongs on `macos-latest` with `brew install xcodegen`, in
the same release workflow next to `play-internal`.

## Ruby

`Gemfile` pins fastlane by major version. There is no `Gemfile.lock` in the
repository (no Ruby on the development machine to generate one); CI resolves
on every run. Generating one on a machine with Ruby and committing it makes
release builds reproducible and is worth doing:

```bash
bundle lock
```
