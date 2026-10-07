#!/usr/bin/env bash
#
# Builds the iOS app and runs it in the simulator, from the command line.
#
#   tools/ios-sim.sh run          # build, boot, install, launch (the usual one)
#   tools/ios-sim.sh build        # Xcode project + app, nothing else
#   tools/ios-sim.sh restart      # relaunch the installed app, no build
#   tools/ios-sim.sh stop         # quit the app
#   tools/ios-sim.sh screenshot [file.png]
#   tools/ios-sim.sh logs         # the app's log, live, until Ctrl+C
#   tools/ios-sim.sh location <lat> <lon>
#   tools/ios-sim.sh devices      # simulators to choose from
#
# IOS_SIM_DEVICE picks the simulator by name ("iPhone 17 Pro"); without it,
# the booted iPhone, or else an iPhone on the newest runtime. Runs under
# macOS's stock bash 3.2. Needs Xcode, an iOS simulator runtime
# (`xcodebuild -downloadPlatform iOS`) and xcodegen (`brew install xcodegen`).
# The backend comes from iosApp/Secrets.xcconfig, see iosApp/README.md.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IOS_DIR="$REPO_ROOT/iosApp"
BUILD_DIR="$IOS_DIR/build"
APP="$BUILD_DIR/Debug-iphonesimulator/ChargeAhead.app"
BUNDLE_ID="org.julakali.chargeahead"
STUDIO_JDK="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

die() { echo "$*" >&2; exit 1; }

# One device for every command: the named one, else the booted iPhone, else an
# iPhone on the newest runtime. Several booted and none named is refused.
device_udid() {
  xcrun simctl list devices available --json | python3 -c '
import json, sys
name = sys.argv[1]
runtimes = json.load(sys.stdin)["devices"]
devices = [d for key in sorted(runtimes, reverse=True) for d in runtimes[key]]
if name:
    hits = [d for d in devices if d["name"] == name]
    hits = [d for d in hits if d["state"] == "Booted"] or hits
else:
    iphones = [d for d in devices if d["name"].startswith("iPhone")]
    booted = [d for d in iphones if d["state"] == "Booted"]
    if len(booted) > 1:
        print("several")
        sys.exit()
    hits = booted or iphones
print(hits[0]["udid"] if hits else "")
' "${IOS_SIM_DEVICE:-}"
}

udid() {
  local id
  id="$(device_udid)" || die "could not list the simulators (xcrun simctl)"
  [ "$id" != "several" ] || die "several iPhones are booted; pick one with IOS_SIM_DEVICE=\"<name>\" (see: $0 devices)"
  [ -n "$id" ] || die "no simulator found${IOS_SIM_DEVICE:+ named \"$IOS_SIM_DEVICE\"}; install a runtime with: xcodebuild -downloadPlatform iOS"
  echo "$id"
}

boot() {
  # Booting a booted device fails; that's fine.
  xcrun simctl boot "$1" 2>/dev/null || true
  open -a Simulator --args -CurrentDeviceUDID "$1"
  xcrun simctl bootstatus "$1" -b >/dev/null
}

build() {
  command -v xcodegen >/dev/null || die "xcodegen missing: brew install xcodegen"
  [ -f "$IOS_DIR/Secrets.xcconfig" ] || echo "warning: iosApp/Secrets.xcconfig missing, the app will stop at launch" >&2
  (cd "$IOS_DIR" && xcodegen generate --quiet)
  # Only a JDK that exists; otherwise project.yml's script phase finds one itself.
  local jdk=()
  if [ -n "${JAVA_HOME:-}" ]; then jdk=("JAVA_HOME=$JAVA_HOME")
  elif [ -d "$STUDIO_JDK" ]; then jdk=("JAVA_HOME=$STUDIO_JDK"); fi
  xcodebuild -project "$IOS_DIR/ChargeAhead.xcodeproj" -target ChargeAhead \
    -configuration Debug -sdk iphonesimulator -arch arm64 \
    CODE_SIGNING_ALLOWED=NO SYMROOT="$BUILD_DIR" ${jdk[@]+"${jdk[@]}"} \
    build -quiet
  echo "built $APP"
}

launch() {
  local udid="$1"
  xcrun simctl terminate "$udid" "$BUNDLE_ID" 2>/dev/null || true
  xcrun simctl launch "$udid" "$BUNDLE_ID"
}

case "${1:-run}" in
  run)
    build
    id="$(udid)"
    boot "$id"
    xcrun simctl install "$id" "$APP"
    # Without it the first launch stops at the permission prompt, and the map has no position.
    xcrun simctl privacy "$id" grant location "$BUNDLE_ID"
    launch "$id"
    ;;
  build) build ;;
  restart)
    id="$(udid)"
    boot "$id"
    launch "$id"
    ;;
  stop)
    id="$(udid)"
    xcrun simctl terminate "$id" "$BUNDLE_ID"
    ;;
  screenshot)
    id="$(udid)"
    file="${2:-$BUILD_DIR/screenshot.png}"
    mkdir -p "$(dirname "$file")"
    xcrun simctl io "$id" screenshot "$file" >/dev/null
    echo "$file"
    ;;
  logs)
    id="$(udid)"
    xcrun simctl spawn "$id" log stream --style compact --predicate 'process == "ChargeAhead"'
    ;;
  location)
    [ $# -eq 3 ] || die "usage: $0 location <lat> <lon>"
    id="$(udid)"
    xcrun simctl location "$id" set "$2,$3"
    ;;
  devices) xcrun simctl list devices available | grep -E "iPhone|iPad|==" ;;
  *) die "usage: $0 [run|build|restart|stop|screenshot [file]|logs|location <lat> <lon>|devices]" ;;
esac
