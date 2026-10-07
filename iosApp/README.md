# iosApp

CarPlay integration and a minimal SwiftUI phone UI for the charging-stop
assistant (as of M1 — see [../ARCHITECTURE.md](../ARCHITECTURE.md) and
[../AGENTS.md](../AGENTS.md)).

```
ChargeAhead/
├── AppDelegate.swift              Scene routing: phone vs. CarPlay; SharedEntry starts the graph at launch and creates features
├── Info.plist                     UIApplicationSceneManifest, permission texts
├── ChargeAhead.entitlements       com.apple.developer.carplay-charging
├── Localized.swift                localized("key"): text from shared's composeResources
├── {en,de}.lproj/InfoPlist.strings location permission text, the one native string
├── CarPlay/
│   ├── CarPlaySceneDelegate.swift CPTemplateApplicationSceneDelegate, CPListTemplate
│   └── ChargeStopsViewModel.swift corridor list for CarPlay (ChargeStopsWatcher)
└── Phone/
    ├── HomeMapView.swift          map home, destination search, Jetzt laden — shared ViewModels via SKIE
    ├── TripPlanView.swift         planned trip and its stops
    ├── ContentView.swift          platform info
    └── PhoneSceneDelegate.swift   hosts HomeMapView in a UIHostingController
project.yml                        XcodeGen spec for the Xcode project
```

## Building

To build it and run it in the simulator in one go (see the `ios-simulator`
skill for restart, screenshots, logs and location):

```bash
tools/ios-sim.sh run
```

By hand, the app compiles and links against the real `Shared.framework` (SKIE
included) on a Mac with Xcode. Without a simulator runtime installed, build
for the simulator SDK directly:

```bash
cd iosApp
xcodegen generate
xcodebuild -project ChargeAhead.xcodeproj -target ChargeAhead \
  -configuration Debug -sdk iphonesimulator -arch arm64 \
  CODE_SIGNING_ALLOWED=NO \
  JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" build
```

`JAVA_HOME` goes in as a build setting because Xcode's script phase that runs
Gradle doesn't inherit it from the shell.

Still unverified, because it only shows up at runtime on a device:
`CLLocationManager` delivers its callbacks only to a thread with a running
run loop, so whether `flowOn(Dispatchers.Main)` in `CoreLocationSource.kt`
is enough decides between a working location stream and one that stays
silent with no error anywhere.

## Backend

The app gets everything from the ChargeAhead backend and does not run
without it. Address and token do not belong in the repository. They reach
the code through the build settings `CHARGEAHEAD_BASE_URL` and
`CHARGEAHEAD_TOKEN` into `Info.plist`, and from there via
`Bundle.main.object(forInfoDictionaryKey:)`.

For that, create a `Secrets.xcconfig` next to `project.yml` (it's in
`.gitignore`):

```
CHARGEAHEAD_BASE_URL = https:/$()/YOUR_HOST
CHARGEAHEAD_TOKEN = YOUR_TOKEN
```

`$()` keeps the `//` from starting a comment in the `.xcconfig`. If the file
is missing, XcodeGen ignores the `configFiles` entry and the project still
builds, but the app stops at launch.

## Steps on a Mac

1. **Apply for the CarPlay entitlement from Apple**, if not already done —
   without approval of `com.apple.developer.carplay-charging`, every
   build/signing attempt fails regardless of what's locally set in
   `ChargeAhead.entitlements` (see ARCHITECTURE.md, section 1.4).
2. **Build and embed the shared framework:**
   ```bash
   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # simulator, debug
   # or, for an Xcode target without CocoaPods, the standard embed script:
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```
   The result is `Shared.framework`, which resolves `import Shared` in
   Swift.
3. **Generate the Xcode project** (requires XcodeGen: `brew install
   xcodegen`):
   ```bash
   cd iosApp
   xcodegen generate
   ```
   Don't check in the generated `.xcodeproj` — it's reproducibly generated
   from `project.yml`.
4. **Set the team/signing** and launch in Xcode or the CarPlay simulator.
5. `tools/check-swift.sh` remains useful *in addition to* a real Xcode build
   as a fast syntax check, but doesn't replace it.

## Open point

Whether the Xcode CarPlay simulator even launches with a locally set but
Apple-unapproved entitlement is documented inconsistently and must be
verified in practice on the Mac (ROADMAP.md, open point 1).
