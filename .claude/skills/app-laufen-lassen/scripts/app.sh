#!/usr/bin/env bash
#
# Installs, starts and drives the charging-stop assistant on an Android
# device — emulator or phone.
#
# Two things are wrapped here because they otherwise cause repeated mistakes:
# picking the right target with multiple devices attached, and the fact that
# 'adb emu geo fix' expects longitude BEFORE latitude.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
# The application id is not the Kotlin package: the code lives under
# de.autoapp.android, the installed app is org.julakali.chargeahead. So the
# activity has to be named fully qualified — the "$PKG/.Foo" shorthand
# resolves against the application id and silently misses.
PKG=org.julakali.chargeahead
ACTIVITY="$PKG/de.autoapp.android.phone.MainActivity"
AVD="${APP_AVD:-Medium_Phone_API_36.0}"
EMULATOR_BIN="${ANDROID_EMULATOR:-$HOME/Android/Sdk/emulator/emulator}"

usage() {
    cat <<'USAGE'
app.sh <command> [--phone | --emulator | --device <serial>]

  devices             What's connected?
  emulator            Start the emulator and wait for boot.
  install             Build and install.
  start               Start the app (stops a running instance first).
  permissions         Grant location and network.
  where <lat> <lon>   Set location (emulator only). Latitude first.
  shot <file>         Screenshot; reports the resolution too.
  tap <x> <y>         Tap, in device pixels (= screenshot pixels).
  type "<text>"       Type text; spaces are translated.
  key <name>          back | home | escape | enter
  log [lines]         Only the app's warnings.
  state               Show the device's settings and local store.
USAGE
}

# --- Determine target device -------------------------------------------------
SERIAL="${APP_DEVICE:-}"
ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --phone)    WANT=phone; shift ;;
        --emulator) WANT=emulator; shift ;;
        --device)   SERIAL="$2"; shift 2 ;;
        *)          ARGS+=("$1"); shift ;;
    esac
done
set -- "${ARGS[@]:-}"

pick_device() {
    [[ -n "$SERIAL" ]] && { echo "$SERIAL"; return; }
    local all; mapfile -t all < <(adb devices | awk 'NR>1 && $2=="device"{print $1}')
    local want="${WANT:-}"
    local matching=()
    for d in "${all[@]}"; do
        case "$want" in
            emulator) [[ "$d" == emulator-* ]] && matching+=("$d") ;;
            phone)    [[ "$d" != emulator-* ]] && matching+=("$d") ;;
            *)        matching+=("$d") ;;
        esac
    done
    case "${#matching[@]}" in
        0) echo "No matching device. Attached: ${all[*]:-none}" >&2; exit 1 ;;
        1) echo "${matching[0]}" ;;
        *) echo "Multiple devices: ${matching[*]}" >&2
           echo "Choose with --phone, --emulator or --device <serial>." >&2; exit 1 ;;
    esac
}

# --- Commands -----------------------------------------------------------------
cmd_devices() { adb devices -l | sed 1d | grep -v '^$' || echo "none"; }

cmd_emulator() {
    if adb devices | awk 'NR>1 && $1 ~ /^emulator-/ && $2=="device"' | grep -q .; then
        echo "Emulator is already running."; return
    fi
    setsid nohup "$EMULATOR_BIN" -avd "$AVD" >/dev/null 2>&1 &
    adb wait-for-device
    for _ in $(seq 1 60); do
        [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && {
            echo "Emulator ready."; return; }
        sleep 5
    done
    echo "Emulator did not finish booting in time." >&2; exit 1
}

cmd_install() {
    local dev; dev=$(pick_device)
    ( cd "$REPO" && ANDROID_SERIAL="$dev" ./gradlew :androidApp:installDebug ) \
        | grep -E "Installed|BUILD|^e:" || true
}

cmd_start() {
    local dev; dev=$(pick_device)
    adb -s "$dev" shell am force-stop "$PKG"
    adb -s "$dev" shell am start -n "$ACTIVITY" >/dev/null
    echo "Started on $dev."
}

cmd_permissions() {
    local dev; dev=$(pick_device)
    for p in android.permission.ACCESS_FINE_LOCATION android.permission.ACCESS_COARSE_LOCATION; do
        adb -s "$dev" shell pm grant "$PKG" "$p" 2>/dev/null || true
    done
    # On Android derivatives like GrapheneOS, INTERNET is revocable and off
    # for freshly installed apps. On stock Android this is a no-op.
    adb -s "$dev" shell pm grant "$PKG" android.permission.INTERNET 2>/dev/null || true
    adb -s "$dev" shell dumpsys package "$PKG" \
        | grep -E "(ACCESS_FINE_LOCATION|INTERNET): granted" | sed 's/^ *//'
}

cmd_where() {
    local dev; dev=$(pick_device)
    local lat="${1:?missing latitude}" lon="${2:?missing longitude}"
    [[ "$dev" == emulator-* ]] || {
        echo "Setting location only works in the emulator. On a phone it needs a" >&2
        echo "mock-location app, registered in developer options." >&2; exit 1; }
    # geo fix expects LONGITUDE first — swap them and you land in the Gulf of Guinea.
    adb -s "$dev" emu geo fix "$lon" "$lat" >/dev/null
    echo "Location set: $lat, $lon"
}

cmd_shot() {
    local dev; dev=$(pick_device)
    local out="${1:?missing filename}"
    adb -s "$dev" exec-out screencap -p > "$out"
    python3 - "$out" <<'PY'
import sys
try:
    from PIL import Image
    w, h = Image.open(sys.argv[1]).size
    print(f"{sys.argv[1]} — {w}x{h} device pixels; 'tap' expects exactly this.")
except ImportError:
    print(sys.argv[1])
PY
}

cmd_tap()  { adb -s "$(pick_device)" shell input tap "${1:?x}" "${2:?y}"; }
cmd_key()  {
    local code
    case "${1:?key}" in
        back) code=4 ;; home) code=3 ;; escape) code=111 ;; enter) code=66 ;;
        *) code="$1" ;;
    esac
    adb -s "$(pick_device)" shell input keyevent "$code"
}
cmd_type() { adb -s "$(pick_device)" shell input text "${1// /%s}"; }

cmd_log() {
    adb -s "$(pick_device)" logcat -d -t "${1:-400}" 2>/dev/null \
        | grep "W autoapp " || echo "(no app warnings)"
}

# Copies one file out of the app's private data directory. An empty result
# counts as failure: 'run-as cat' on a missing file writes to stderr, which is
# swallowed here, and the exit status alone would not tell the two apart.
pull_app_file() {
    local dev=$1 relative=$2 target=$3
    adb -s "$dev" shell run-as "$PKG" cat "/data/data/$PKG/$relative" > "$target" 2>/dev/null \
        && [[ -s "$target" ]]
}

cmd_state() {
    local dev; dev=$(pick_device)
    echo "--- Settings ---"
    adb -s "$dev" shell run-as "$PKG" cat "/data/data/$PKG/shared_prefs/de.autoapp.settings.xml" \
        2>/dev/null | grep -oE '<string name="[^"]*">[^<]*' | sed 's/<string name="//;s/">/ = /' \
        || echo "(none yet)"
    echo "--- Local store ---"
    # Room runs in WAL mode: while the app is running, the .db file is a stub
    # of a few kilobytes and every row sits in the -wal beside it. Pulling the
    # .db alone therefore yields a database without a single table. The files
    # have to land in one directory under their original names, so sqlite
    # finds the log and replays it on open.
    local dir; dir=$(mktemp -d)
    local db="$dir/charge_sites.room.db"
    if pull_app_file "$dev" "databases/charge_sites.room.db" "$db"; then
        pull_app_file "$dev" "databases/charge_sites.room.db-wal" "$db-wal" || rm -f "$db-wal"
        pull_app_file "$dev" "databases/charge_sites.room.db-shm" "$db-shm" || rm -f "$db-shm"
        python3 - "$db" <<'PY'
import sqlite3, sys

try:
    c = sqlite3.connect(sys.argv[1])
    for row in c.execute("select sourceId, count(*) from chargeSite group by sourceId"):
        print(f"  {row[0]}: {row[1]} sites")
    for row in c.execute("select sourceId, count(*) from tileCoverage group by sourceId"):
        print(f"  {row[0]}: {row[1]} tiles")
except sqlite3.Error as error:
    # A copy taken mid-write can be unreadable. That is worth one line, not a
    # traceback that buries the settings printed above it.
    print(f"  (database unreadable: {error})")
PY
    else
        echo "  (no database yet)"
    fi
    rm -rf "$dir"
}

case "${1:-}" in
    devices)     cmd_devices ;;
    emulator)    cmd_emulator ;;
    install)     cmd_install ;;
    start)       cmd_start ;;
    permissions) cmd_permissions ;;
    where)       shift; cmd_where "$@" ;;
    shot)        shift; cmd_shot "$@" ;;
    tap)         shift; cmd_tap "$@" ;;
    type)        shift; cmd_type "$@" ;;
    key)         shift; cmd_key "$@" ;;
    log)         shift; cmd_log "${1:-}" ;;
    state)       cmd_state ;;
    *)           usage; exit 1 ;;
esac
