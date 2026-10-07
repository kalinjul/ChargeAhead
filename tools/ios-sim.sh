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
# the first available iPhone. Needs Xcode, an iOS simulator runtime
# (`xcodebuild -downloadPlatform iOS`) and xcodegen (`brew install xcodegen`).
# The backend comes from iosApp/Secrets.xcconfig, see iosApp/README.md.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IOS_DIR="$REPO_ROOT/iosApp"
BUILD_DIR="$IOS_DIR/build"
APP="$BUILD_DIR/Debug-iphonesimulator/ChargeAhead.app"
BUNDLE_ID="org.julakali.chargeahead"
JDK="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

die() { echo "$*" >&2; exit 1; }

device_udid() {
  local name="${IOS_SIM_DEVICE:-}"
  xcrun simctl list devices available --json | python3 -c '
import json, sys
name = sys.argv[1]
devices = [d for rt in json.load(sys.stdin)["devices"].values() for d in rt if d.get("isAvailable")]
iphones = [d for d in devices if (d["name"] == name if name else d["name"].startswith("iPhone"))]
print(iphones[0]["udid"] if iphones else "")
' "$name"
}

booted_udid() {
  local udid
  udid="$(device_udid)"
  [ -n "$udid" ] || die "no simulator found${IOS_SIM_DEVICE:+ named \"$IOS_SIM_DEVICE\"}; install a runtime with: xcodebuild -downloadPlatform iOS"
  # Booting a booted device fails; that's fine.
  xcrun simctl boot "$udid" 2>/dev/null || true
  open -a Simulator --args -CurrentDeviceUDID "$udid"
  xcrun simctl bootstatus "$udid" -b >/dev/null
  echo "$udid"
}

build() {
  command -v xcodegen >/dev/null || die "xcodegen missing: brew install xcodegen"
  [ -f "$IOS_DIR/Secrets.xcconfig" ] || echo "warning: iosApp/Secrets.xcconfig missing, the app will stop at launch" >&2
  (cd "$IOS_DIR" && xcodegen generate --quiet)
  xcodebuild -project "$IOS_DIR/ChargeAhead.xcodeproj" -target ChargeAhead \
    -configuration Debug -sdk iphonesimulator -arch arm64 \
    CODE_SIGNING_ALLOWED=NO SYMROOT="$BUILD_DIR" JAVA_HOME="$JDK" \
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
    udid="$(booted_udid)"
    xcrun simctl install "$udid" "$APP"
    # Without it the first launch stops at the permission prompt, and the map has no position.
    xcrun simctl privacy "$udid" grant location "$BUNDLE_ID"
    launch "$udid"
    ;;
  build) build ;;
  restart) launch "$(booted_udid)" ;;
  stop) xcrun simctl terminate booted "$BUNDLE_ID" ;;
  screenshot)
    file="${2:-$BUILD_DIR/screenshot.png}"
    xcrun simctl io booted screenshot "$file" >/dev/null
    echo "$file"
    ;;
  logs) xcrun simctl spawn booted log stream --style compact --predicate 'process == "ChargeAhead"' ;;
  location)
    [ $# -eq 3 ] || die "usage: $0 location <lat> <lon>"
    xcrun simctl location booted set "$2,$3"
    ;;
  devices) xcrun simctl list devices available | grep -E "iPhone|iPad|==" ;;
  *) die "usage: $0 [run|build|restart|stop|screenshot [file]|logs|location <lat> <lon>|devices]" ;;
esac
