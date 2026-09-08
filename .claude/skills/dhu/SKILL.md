---
name: dhu
description: Starts, connects to and remote-controls the Android Auto Desktop Head Unit (DHU) to check this app's car surface — list, detail view, navigation hand-off, destination entry in the car. Always use this skill when the DHU, the Desktop Head Unit, Android Auto, the car UI, the CarAppService, a head unit, or starting/viewing/operating the app "in the car" comes up — even if the DHU isn't named explicitly. Same for "Failed to read from transport", when the car UI stays empty, or when checking whether a change reaches the car.
---

# Checking the app in the car

The car UI is this app's actual purpose, and it's the one surface that
**cannot** be checked in an emulator: the preinstalled Android Auto there is
a stub, and the Play Store reports emulator hardware as "not compatible". It
needs a real phone connected to the Desktop Head Unit.

All the fiddly parts are wrapped in `scripts/dhu.sh`. Use the script instead
of starting the DHU by hand — manual starts fail on two quirks explained
below.

## Flow

```bash
D=.claude/skills/dhu/scripts/dhu.sh

$D status                       # already running? phone attached?
$D start                        # over USB
$D send "keycode home"          # to the app launcher
$D shot /tmp/car.png            # see where you are
$D tap 633 228                  # tap what you saw
$D stop
```

Take a `shot` after every `tap` and **actually look at the image**.
Positions in the app launcher depend on how many apps are installed and which
page you're on — guessed coordinates reliably hit the wrong thing.

## Before the first attempt

The app must be installed on the **phone**, not the emulator:

```bash
ANDROID_SERIAL=<serial> ./gradlew :androidApp:installDebug
```

One-time setup in Android Auto on the phone: Settings → scroll to the bottom
→ tap the version number ten times → in the three-dot menu, **"Allow
unknown sources"**. Without this the debug app never shows up in the
launcher.

## Two ways to reach the phone

**USB (`$D start`)** is the default and needs nothing but the cable.

**ADB (`$D start --adb`)** additionally needs the head unit server, started
from the same developer menu. It stops on every disconnect and must be
restarted before every attempt. The script checks this beforehand and says
so, instead of failing with a confusing abort.

## DHU console commands

| Command | Effect |
|---|---|
| `keycode home` | go to the app launcher |
| `tap <x> <y>` | tap, (0,0) top left, resolution matches the screenshot (default 800×480) |
| `screenshot <file>` | take a screenshot — `$D shot` does exactly that |
| `location <lat> <lon> [accuracy] [altitude] [speed] [bearing]` | fake a location |
| `compass <bearing>` | set the heading |
| `dpad up/down/left/right/click/back` | for rotary-controller input |
| `night` / `day` | day/night rendering |
| `help` | full list |

`location` and `compass` go to the *car*, not the phone. This app reads its
location through the phone's Play services and sees none of this — a
different location needs a mock-location app on the phone.

## Four traps that cost time

**`adb forward` proves nothing.** It accepts the connection *on the
computer* and only forwards it afterward. If nothing is listening on the
other end, it's closed immediately — locally that looks like a port that's
answering, and the DHU just reports `Failed to read from transport -
disconnect`. That looks like a DHU bug and isn't one. What actually matters
is `adb shell netstat -lnt | grep 5277`.

**The DHU dies on closed input.** It reads commands from stdin and exits
silently on EOF — i.e. on every start from a script. Hence the FIFO with a
permanent writer in the script.

**Output is block-buffered.** Without `stdbuf -o0`, responses only appear
after kilobytes of output. Without the script you wouldn't even see what's
going wrong.

**`pkill -f desktop-head-unit` catches its own shell**, because its command
line contains the pattern. Kill by PID instead — or use `$D stop`.

## When the list stays empty in the car

Check the app's log first, don't guess:

```bash
adb -s <serial> logcat -d | grep "W autoapp "
```

The app deliberately catches errors so it keeps running, but logs them with
a cause. Most common finding: `UnknownHostException`. On Android derivatives
like GrapheneOS, `android.permission.INTERNET` is a revocable permission and
is off for freshly installed apps:

```bash
adb -s <serial> shell pm grant org.julakali.chargeahead android.permission.INTERNET
```

If the UI shows the permission screen instead, the location is missing — in
projection mode the head unit can't show the system dialog itself, it
appears on the phone.
