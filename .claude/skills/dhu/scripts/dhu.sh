#!/usr/bin/env bash
#
# Starts and controls the Android Auto Desktop Head Unit.
#
# The DHU reads its commands from stdin. Without input held open, it exits
# silently as soon as it sees EOF — hence the FIFO with a permanent writer.
# And without stdbuf it block-buffers its output, so responses only appear
# after kilobytes.
set -euo pipefail

# --- Where this machine keeps the SDK (Linux, macOS, or whatever the IDE wrote) ---
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
sdk_root() {
    local candidate
    for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        [[ -n "$candidate" && -d "$candidate" ]] && { echo "$candidate"; return; }
    done
    [[ -f "$REPO/local.properties" ]] && sed -n 's/^sdk\.dir=//p' "$REPO/local.properties" | head -1
}
SDK="$(sdk_root || true)"
# The SDK's own copy first, then PATH: the sandbox shell has neither on PATH.
ADB="${ADB:-$SDK/platform-tools/adb}"; [[ -x "$ADB" ]] || ADB=adb
adb() { "$ADB" "$@"; }
# adb commands aimed at the phone go to ANDROID_SERIAL when set, so a second
# device (an emulator beside the phone, or the other way round) can't catch them.
padb() { adb ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} "$@"; }

# Android Auto on the device: "stub" is the placeholder on emulator images, with
# no head unit server and no launcher; the real app comes from Play or an APK.
gearhead_version() {
    padb shell dumpsys package com.google.android.projection.gearhead 2>/dev/null \
        | sed -n 's/.*versionName=//p' | head -1 | tr -d '\r'
}

DHU_BIN="${DHU_BIN:-$SDK/extras/google/auto/desktop-head-unit}"
RUN_DIR="${DHU_RUN_DIR:-${TMPDIR:-/tmp}/dhu-control}"
FIFO="$RUN_DIR/in"
LOG="$RUN_DIR/out.log"
PIDFILE="$RUN_DIR/dhu.pid"

usage() {
    cat <<'USAGE'
dhu.sh <command>

  start [--adb] [--config <name>]
                  Start the DHU. Without --adb, over USB (AOAP); with
                  --adb, over the forwarded port 5277. --config picks a
                  screen layout from the DHU's config/ folder (e.g.
                  default_wide, default_720p, default_1080p) or an .ini path;
                  default is 800x480.
                  With several phones attached, set ANDROID_SERIAL to pick
                  one — the DHU otherwise takes whichever answers first.
  send "<text>"   Send an arbitrary console command, e.g. "keycode home".
  tap <x> <y>     Tap. Coordinates in DHU resolution, (0,0) top left.
  shot <file>     Write a DHU screenshot to the file.
  log [lines]     Show the last lines of DHU output.
  status          Is it running? Is a phone attached?
  doctor          Check SDK, DHU, adb, phone and head unit server; say what is missing.
  stop            Stop the DHU and its permanent writer.
USAGE
}

running() { [[ -f "$PIDFILE" ]] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; }

# Runs a command detached from this shell. setsid is Linux; macOS has none and
# nohup in the background is enough there.
detach() {
    if command -v setsid >/dev/null 2>&1; then
        setsid nohup "$@" >/dev/null 2>&1 &
    else
        nohup "$@" >/dev/null 2>&1 &
    fi
    echo $!
}

# The DHU block-buffers its output unless it thinks it has a terminal. stdbuf
# is coreutils, so Linux has it; macOS gets a pty from python's pty module
# instead (BSD script would want a terminal on stdin, and stdin is the FIFO).
unbuffered() {
    if [[ "$(uname)" == Darwin ]]; then echo "python3 -c 'import pty,sys; sys.exit(pty.spawn(sys.argv[1:]))'"
    elif command -v stdbuf >/dev/null 2>&1; then echo "stdbuf -o0 -e0"
    else echo ""; fi
}

require_running() {
    running || { echo "DHU is not running. Run 'dhu.sh start' first." >&2; exit 1; }
}

cmd_start() {
    if running; then echo "DHU is already running (PID $(cat "$PIDFILE"))."; return 0; fi
    [[ -x "$DHU_BIN" ]] || { echo "DHU not found: $DHU_BIN" >&2; exit 1; }

    local mode="--usb${ANDROID_SERIAL:+=$ANDROID_SERIAL}" config=""
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --adb) mode="" ;;
            --config)
                config="${2:?--config needs a name or file}"; shift
                [[ -f "$config" ]] || config="$(dirname "$DHU_BIN")/config/${config%.ini}.ini"
                [[ -f "$config" ]] || { echo "No such DHU config: $config" >&2; exit 1; }
                config="--config=$(realpath "$config")"
                ;;
            *) echo "Unknown start option: $1" >&2; exit 1 ;;
        esac
        shift
    done

    if [[ -n "$mode" && -z "${ANDROID_SERIAL:-}" ]] && (( $(adb devices | grep -c $'\tdevice$') > 1 )); then
        echo "Several phones attached; set ANDROID_SERIAL to choose one." >&2
        exit 1
    fi

    mkdir -p "$RUN_DIR"; rm -f "$FIFO"; mkfifo "$FIFO"; : > "$LOG"

    # Permanent writer: keeps the FIFO open so the DHU never sees EOF.
    detach sh -c "sleep 86400 > '$FIFO'" > "$RUN_DIR/writer.pid"
    sleep 1

    if [[ -z "$mode" ]]; then
        local version; version="$(gearhead_version)"
        if [[ "$version" == *-stub ]]; then
            echo "Android Auto on this device is the emulator stub ($version): no head unit server, nothing to connect to." >&2
            echo "Install the real Android Auto on it first (Play Store, or its APK via 'adb install-multiple')." >&2
            exit 1
        fi
        padb forward tcp:5277 tcp:5277 >/dev/null
        # Only the listener ON THE PHONE counts: adb forward accepts the
        # connection locally and closes it immediately if nothing is
        # listening on the other end. That looks like a port that's
        # answering, and isn't.
        if ! padb shell netstat -lnt 2>/dev/null | grep -q 5277; then
            echo "No head unit server running on the phone." >&2
            echo "In Android Auto: Settings -> tap the version number 10x" >&2
            echo "-> three-dot menu -> 'Start head unit server'." >&2
            exit 1
        fi
    fi

    local prefix; prefix="$(unbuffered)"
    ( cd "$(dirname "$DHU_BIN")" \
      && detach sh -c "exec $prefix '$DHU_BIN' $mode $config < '$FIFO' >> '$LOG' 2>&1" > "$PIDFILE" )
    sleep 12

    if grep -q "Attached\|connected" "$LOG" 2>/dev/null; then
        echo "DHU connected (PID $(cat "$PIDFILE"))."
    else
        echo "DHU started, but no connection. Log:" >&2
        grep -vE "ALSA|[Jj]ack|Cannot connect to server" "$LOG" | tail -8 >&2
        exit 1
    fi
}

cmd_send() { require_running; printf '%s\n' "$1" > "$FIFO"; sleep "${DHU_SETTLE:-2}"; }

cmd_shot() {
    require_running
    local out="${1:?missing filename}"
    rm -f "$out"
    printf 'screenshot %s\n' "$out" > "$FIFO"
    for _ in $(seq 1 20); do [[ -s "$out" ]] && { echo "$out"; return 0; }; sleep 0.5; done
    echo "No screenshot appeared — is the connection still up?" >&2; exit 1
}

# Everything that has to be true before 'start' can work, with the fix for each.
cmd_doctor() {
    local ok=0
    say() { printf '%s %s\n' "$1" "$2"; }
    [[ -n "$SDK" && -d "$SDK" ]] && say "ok  " "SDK: $SDK" || { say "FAIL" "no Android SDK found; set ANDROID_HOME"; ok=1; }
    [[ -x "$DHU_BIN" ]] && say "ok  " "DHU: $DHU_BIN" \
        || { say "FAIL" "DHU not installed: Android Studio -> SDK Manager -> SDK Tools -> 'Android Auto Desktop Head Unit emulator'"; ok=1; }
    "$ADB" version >/dev/null 2>&1 && say "ok  " "adb: $ADB" || { say "FAIL" "adb not found; platform-tools missing from $SDK"; ok=1; }
    [[ -n "$(unbuffered)" ]] && say "ok  " "unbuffered output via: $(unbuffered)" || say "warn" "no stdbuf/script: DHU output will lag"
    local devices; devices=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}' | tr '\n' ' ')
    if [[ -z "$devices" ]]; then say "FAIL" "nothing on adb: plug in a phone, or start an emulator with the real Android Auto on it"; return 1; fi
    say "ok  " "on adb: $devices"
    local serial="${ANDROID_SERIAL:-${devices%% *}}"
    [[ "$devices" == *" "*" "* ]] && [[ -z "${ANDROID_SERIAL:-}" ]] && say "warn" "several devices; set ANDROID_SERIAL (checking $serial)"
    local version; version="$(ANDROID_SERIAL=$serial gearhead_version)"
    case "$version" in
        "")      say "FAIL" "no Android Auto on $serial; install it (Play Store, or its APK via 'adb install-multiple')"; ok=1 ;;
        *-stub)  say "FAIL" "Android Auto on $serial is the emulator stub ($version): no head unit server, no launcher. Install the real app over it: Play Store on the emulator, or the APK bundle via 'adb -s $serial install-multiple'"; ok=1 ;;
        *)       say "ok  " "Android Auto $version on $serial" ;;
    esac
    if adb -s "$serial" shell netstat -lnt 2>/dev/null | grep -q 5277; then
        say "ok  " "head unit server listening on $serial"
    else
        say "warn" "no head unit server on $serial: needed for 'start --adb' (Android Auto -> Settings -> version 10x -> menu -> Start head unit server; it stops on every disconnect)"
    fi
    adb -s "$serial" shell pm list packages 2>/dev/null | grep -q org.julakali.chargeahead \
        && say "ok  " "app installed on $serial" || say "warn" "app not on $serial: ANDROID_SERIAL=$serial ./gradlew :androidApp:installDebug"
    return $ok
}

cmd_status() {
    if running; then echo "DHU: running (PID $(cat "$PIDFILE"))"; else echo "DHU: not running"; fi
    local dev; dev=$(adb devices | awk 'NR>1 && $2=="device"{print $1}' | tr '\n' ' ')
    echo "Devices on adb: ${dev:-none}"
}

# Kills a process and everything under it. Without setsid (macOS) the DHU and
# its pty wrapper are children of the shell we recorded, not a process group.
kill_tree() {
    local pid=$1 child
    for child in $(pgrep -P "$pid" 2>/dev/null); do kill_tree "$child"; done
    kill "$pid" 2>/dev/null || true
}

cmd_stop() {
    [[ -f "$PIDFILE" ]] && kill_tree "$(cat "$PIDFILE")"
    [[ -f "$RUN_DIR/writer.pid" ]] && kill_tree "$(cat "$RUN_DIR/writer.pid")"
    rm -f "$PIDFILE" "$RUN_DIR/writer.pid" "$FIFO"
    echo "DHU stopped."
}

case "${1:-}" in
    start)  shift; cmd_start "$@" ;;
    send)   shift; cmd_send "${1:?missing command}" ;;
    tap)    shift; cmd_send "tap ${1:?missing x} ${2:?missing y}" ;;
    shot)   shift; cmd_shot "${1:-}" ;;
    log)    tail -n "${2:-20}" "$LOG" | grep -vE "ALSA|[Jj]ack|Cannot connect to server" ;;
    status) cmd_status ;;
    doctor) cmd_doctor ;;
    stop)   cmd_stop ;;
    *)      usage; exit 1 ;;
esac
