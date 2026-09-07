#!/usr/bin/env bash
#
# Starts the Desktop Head Unit against a connected phone: forwards the
# head-unit port and launches the DHU. Phone-side setup (developer mode,
# "Start head unit server", "Allow unknown sources") is described in
# docs/android-auto-testen.md — this script checks the one thing that
# silently goes wrong, because `adb forward` succeeding proves nothing.
#
# Usage: tools/start-dhu.sh [adb-serial]
# The serial is only needed when several devices are attached and none or
# more than one of them is a real phone.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
[[ -d "$SDK" ]] || SDK="$HOME/Android/Sdk"
ADB="$SDK/platform-tools/adb"
DHU="$SDK/extras/google/auto/desktop-head-unit"

fail() { echo "Error: $*" >&2; exit 1; }

[[ -x "$ADB" ]] || fail "adb not found at $ADB — set ANDROID_HOME"
[[ -x "$DHU" ]] || fail "DHU not installed — run: sdkmanager \"extras;google;auto\""

# macOS quarantines downloaded binaries; Gatekeeper would block the first
# launch with a dialog that never names the real problem.
command -v xattr >/dev/null && xattr -d com.apple.quarantine "$DHU" 2>/dev/null || true

serial="${1:-}"
if [[ -z "$serial" ]]; then
    devices=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
    phones=$(grep -v '^emulator-' <<<"$devices" || true)
    phone_count=$(echo "$phones" | grep -c . || true)

    if [[ "$phone_count" -eq 1 ]]; then
        serial="$phones"
    elif [[ "$phone_count" -eq 0 ]]; then
        # Emulators can't help: their Android Auto is a stub with no
        # head-unit server (docs/android-auto-testen.md).
        fail "no phone connected — the DHU needs a real device with Android Auto"
    else
        fail "several phones connected — pass one: $0 <serial>"$'\n'"$phones"
    fi
fi

"$ADB" -s "$serial" forward tcp:5277 tcp:5277

# Only the phone knows whether the head-unit server is actually running;
# the forward accepts connections on this machine either way.
if ! "$ADB" -s "$serial" shell netstat -lnt 2>/dev/null | grep -q 5277; then
    fail "head-unit server not running on $serial — Android Auto developer menu →
'Headunit-Server starten'. It stops on every disconnect and must be restarted."
fi

echo "Connecting DHU to $serial — commands go to this terminal ('help' lists them)."
exec "$DHU"
