---
name: ios-simulator
description: Builds the iOS app and runs, restarts or stops it in the iOS simulator, takes simulator screenshots, streams the app's log and sets the simulated location — all from the command line through tools/ios-sim.sh. Use it when the iOS app, the iPhone app, the simulator, Xcode or xcodebuild comes up, when the iOS app should be built, run, launched, restarted or looked at, or when a change to Swift or iosMain should be checked in the running app. For Android use app-laufen-lassen, for Android Auto the dhu skill.
---

# Running the iOS app in the simulator

Everything goes through one script; Xcode itself never has to be opened.

```bash
tools/ios-sim.sh run                  # build, boot the simulator, install, grant location, launch
tools/ios-sim.sh restart              # relaunch what is installed, no build (after a crash, or to see the launch again)
tools/ios-sim.sh location 48.14 11.58 # latitude first; the map follows
tools/ios-sim.sh screenshot /tmp/ios.png
tools/ios-sim.sh logs                 # the app's log, live; Ctrl+C ends it
tools/ios-sim.sh stop
tools/ios-sim.sh devices              # which simulators exist
IOS_SIM_DEVICE="iPhone 17 Pro" tools/ios-sim.sh run   # a specific one
```

`run` rebuilds every time, the Kotlin framework included (Xcode's script
phase calls Gradle), so a change in `shared/` shows up without anything else.

## Before the first run

- **Xcode** with an **iOS simulator runtime**. Without one there are no
  simulators: `xcodebuild -downloadPlatform iOS` (about 8 GB).
- **xcodegen**: `brew install xcodegen`. The Xcode project is generated from
  `iosApp/project.yml` on every build and is not checked in.
- **`iosApp/Secrets.xcconfig`** with `CHARGEAHEAD_BASE_URL` and
  `CHARGEAHEAD_TOKEN` (see `iosApp/README.md`). Without it the app builds and
  then stops at launch, on purpose. The values are secrets: copy the file or
  let the developer fill it in, never read them.
- Gradle needs a JDK; the script takes `JAVA_HOME` or Android Studio's.

## When something is off

- **The app vanishes right after launch**: `tools/ios-sim.sh logs` in a second
  terminal, then `restart`. A missing backend config says so in the log.
- **The map shows no position**: set one with `location`; a fresh simulator
  has none.
- **Build output** lands in `iosApp/build/` (ignored).
- CarPlay needs Apple's `carplay-charging` entitlement, so the CarPlay scene
  does not run here; the phone UI does.
