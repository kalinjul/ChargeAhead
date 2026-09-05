# iosApp

CarPlay integration and a minimal SwiftUI phone UI for the charging-stop
assistant (as of M1 — see [../ARCHITECTURE.md](../ARCHITECTURE.md) and
[../AGENTS.md](../AGENTS.md)).

```
AutoApp/
├── AppDelegate.swift              Scene routing: phone vs. CarPlay
├── Info.plist                     UIApplicationSceneManifest, permission texts
├── AutoApp.entitlements           com.apple.developer.carplay-charging
├── de.lproj/Localizable.strings   German text for the phone UI and CarPlay
├── CarPlay/
│   ├── CarPlaySceneDelegate.swift CPTemplateApplicationSceneDelegate, CPListTemplate
│   └── ChargeStopsViewModel.swift bridge to Shared.ChargeStopsFeature
└── Phone/
    ├── ContentView.swift          SwiftUI control view
    └── PhoneSceneDelegate.swift   hosts ContentView in a UIHostingController
project.yml                        XcodeGen spec for the Xcode project
```

## Important: the Swift part is unverified

This code was written on Linux. The two halves of the iOS side are checked
to different degrees, and the distinction matters:

**`shared/src/iosMain` (Kotlin) compiles.** Kotlin/Native builds the Apple
targets on Linux too:

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
```

That means `CoreLocationSource.kt`, `IosEntryPoints.kt`, `Time.ios.kt`,
`HttpClientFactory.ios.kt`, and `Platform.ios.kt` are checked against the
**real** cinterop bindings of CoreLocation, Foundation, and UIKit. What's
skipped on Linux is linking (`linkDebugFrameworkIos*`) — so no
`Shared.framework` and no Objective-C header are produced.

**The Swift part in this directory is checked syntactically, nothing more.**
With Swift 6.1.3 installed, all five files pass `tools/check-swift.sh`
(`swiftc -parse`) cleanly. That finds typos and unbalanced brackets —
**not** type errors, wrong API signatures, or missing symbols. `import
CarPlay`, `import UIKit`, `import SwiftUI`, and `import Shared` can't be
resolved here.

The following spots in particular are candidates for correction once a Mac
with Xcode is available — they all depend on the shape of the Objective-C
header, which doesn't get produced here:

- `AppDelegate.swift`: `UISceneSession.Role.carTemplateApplication` — the
  name of this static member for
  `CPTemplateApplicationSceneSessionRoleApplication` comes from memory of
  Apple's documentation, not from a locally verified source.
- **Kotlin enums in the Swift header.** `ChargeStopsViewModel.statusKey`
  compares `state.phase` with `==` instead of `switch`, because Kotlin
  `enum`s are exported as Objective-C classes and aren't Swift `enum`s.
  Whether the case names really are `ChargeStopsStatePhase.waitingForLocation`
  etc. is unverified.
- **`ChargeStopFormatter.shared`** and whether `operator` (a Kotlin property
  on `ChargeSite`) becomes `operator_` in the Swift header — both depend on
  the specific Kotlin/Native export run.

`CoreLocationSource.kt` is **no longer** on this list: the cinterop
signatures have been confirmed by the Kotlin/Native run. What stays
unverified there is only runtime behavior — above all, whether
`flowOn(Dispatchers.Main)` is enough. `CLLocationManager` only delivers its
callbacks to a thread with a running run loop; without that, the stream
would stay silent forever with no error anywhere. That only shows up on a
device.

## OpenChargeMap key

The key does not belong in the repository. It reaches the code through the
build setting `OPEN_CHARGE_MAP_API_KEY` into `Info.plist`, and from there
via `Bundle.main.object(forInfoDictionaryKey: "OpenChargeMapApiKey")`.

For that, create a `Secrets.xcconfig` next to `project.yml` (it's in
`.gitignore`):

```
OPEN_CHARGE_MAP_API_KEY = YOUR_KEY
```

If the file is missing, XcodeGen ignores the `configFiles` entry, the key
stays empty, and the shared module returns labeled demo data
(`state.isDemo`).

## Steps on a Mac

1. **Apply for the CarPlay entitlement from Apple**, if not already done —
   without approval of `com.apple.developer.carplay-charging`, every
   build/signing attempt fails regardless of what's locally set in
   `AutoApp.entitlements` (see ARCHITECTURE.md, section 1.4).
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
