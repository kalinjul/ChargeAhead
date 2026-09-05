#!/usr/bin/env bash
#
# Starts and controls the Android Auto Desktop Head Unit.
#
# The DHU reads its commands from stdin. Without input held open, it exits
# silently as soon as it sees EOF — hence the FIFO with a permanent writer.
# And without stdbuf it block-buffers its output, so responses only appear
# after kilobytes.
set -euo pipefail

DHU_BIN="${DHU_BIN:-$HOME/Android/Sdk/extras/google/auto/desktop-head-unit}"
RUN_DIR="${DHU_RUN_DIR:-${TMPDIR:-/tmp}/dhu-control}"
FIFO="$RUN_DIR/in"
LOG="$RUN_DIR/out.log"
PIDFILE="$RUN_DIR/dhu.pid"

usage() {
    cat <<'USAGE'
dhu.sh <command>

  start [--adb]   Start the DHU. Without an argument, over USB (AOAP); with
                  --adb, over the forwarded port 5277.
  send "<text>"   Send an arbitrary console command, e.g. "keycode home".
  tap <x> <y>     Tap. Coordinates in DHU resolution, (0,0) top left.
  shot <file>     Write a DHU screenshot to the file.
  log [lines]     Show the last lines of DHU output.
  status          Is it running? Is a phone attached?
  stop            Stop the DHU and its permanent writer.
USAGE
}

running() { [[ -f "$PIDFILE" ]] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; }

require_running() {
    running || { echo "DHU is not running. Run 'dhu.sh start' first." >&2; exit 1; }
}

cmd_start() {
    if running; then echo "DHU is already running (PID $(cat "$PIDFILE"))."; return 0; fi
    [[ -x "$DHU_BIN" ]] || { echo "DHU not found: $DHU_BIN" >&2; exit 1; }

    mkdir -p "$RUN_DIR"; rm -f "$FIFO"; mkfifo "$FIFO"; : > "$LOG"

    # Permanent writer: keeps the FIFO open so the DHU never sees EOF.
    setsid nohup sh -c "sleep 86400 > '$FIFO'" >/dev/null 2>&1 &
    echo $! > "$RUN_DIR/writer.pid"
    sleep 1

    local mode="--usb"
    [[ "${1:-}" == "--adb" ]] && mode=""
    if [[ -z "$mode" ]]; then
        adb forward tcp:5277 tcp:5277 >/dev/null
        # Only the listener ON THE PHONE counts: adb forward accepts the
        # connection locally and closes it immediately if nothing is
        # listening on the other end. That looks like a port that's
        # answering, and isn't.
        if ! adb shell netstat -lnt 2>/dev/null | grep -q 5277; then
            echo "No head unit server running on the phone." >&2
            echo "In Android Auto: Settings -> tap the version number 10x" >&2
            echo "-> three-dot menu -> 'Start head unit server'." >&2
            exit 1
        fi
    fi

    ( cd "$(dirname "$DHU_BIN")" \
      && setsid nohup sh -c "exec stdbuf -o0 -e0 '$DHU_BIN' $mode < '$FIFO'" >> "$LOG" 2>&1 &
      echo $! > "$PIDFILE" )
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

cmd_status() {
    if running; then echo "DHU: running (PID $(cat "$PIDFILE"))"; else echo "DHU: not running"; fi
    local dev; dev=$(adb devices | awk 'NR>1 && $2=="device"{print $1}' | tr '\n' ' ')
    echo "Devices on adb: ${dev:-none}"
}

cmd_stop() {
    [[ -f "$PIDFILE" ]] && kill "$(cat "$PIDFILE")" 2>/dev/null || true
    [[ -f "$RUN_DIR/writer.pid" ]] && kill "$(cat "$RUN_DIR/writer.pid")" 2>/dev/null || true
    rm -f "$PIDFILE" "$RUN_DIR/writer.pid" "$FIFO"
    echo "DHU stopped."
}

case "${1:-}" in
    start)  shift; cmd_start "${1:-}" ;;
    send)   shift; cmd_send "${1:?missing command}" ;;
    tap)    shift; cmd_send "tap ${1:?missing x} ${2:?missing y}" ;;
    shot)   shift; cmd_shot "${1:-}" ;;
    log)    tail -n "${2:-20}" "$LOG" | grep -vE "ALSA|[Jj]ack|Cannot connect to server" ;;
    status) cmd_status ;;
    stop)   cmd_stop ;;
    *)      usage; exit 1 ;;
esac
