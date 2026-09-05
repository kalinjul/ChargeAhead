# Seeing the app in Android Auto

The emulator alone isn't enough. Android Auto renders its UI in a host
process, and that host is either a real head unit or the **Desktop Head
Unit (DHU)** — a desktop program that simulates a head unit and connects to
the phone over ADB.

The DHU is already installed on this machine:

```
/home/julakali/Android/Sdk/extras/google/auto/desktop-head-unit
```

## It doesn't work on the emulator

This isn't a configuration issue but a platform limitation, and skipping
past it otherwise costs half a day of searching:

- The Android Auto preinstalled on the `google_apis_playstore` images is a
  **stub** (`versionName` ends in `-stub`). It has no launchable activity
  and no head unit server; `adb forward tcp:5277` finds nothing to connect
  to.
- The Play Store update can't be obtained: Google reports **"not compatible
  with your device"** there, because Android Auto filters out emulator
  hardware. A different phone image doesn't change this — verified.

For the projected UI there's therefore no way around a **real phone**.

Anyone who wants to see the templates on the machine anyway needs **Android
Automotive OS** instead of Android Auto: an AAOS image from the SDK plus
`androidx.car.app:app-automotive`, which brings `CarAppActivity` and hosts
the same `ChargeCarAppService`. That's a different target platform
(ARCHITECTURE.md 1.2) and deliberately not set up in this project; the
`ConstraintManager` row limit may differ there.

## Requirements on the phone

The DHU doesn't talk to the app, it talks to the **Android Auto app** on the
phone. That app must therefore be present and in developer mode.

1. Install Android Auto from the Play Store.
   On the emulator this only works with the `google_apis_playstore` image —
   the existing AVD `Medium_Phone_API_36.0` is one. Requires signing in
   with a Google account; that has to be done manually.
   A real phone over USB is more reliable.

   **The Android Auto preinstalled on the AVD isn't enough.** It's a stub —
   verifiable with:

   ```bash
   adb shell dumpsys package com.google.android.projection.gearhead | grep versionName
   ```

   If that shows `...-stub`, there's neither a launchable activity nor a
   head unit server, and `adb forward tcp:5277` finds nothing to connect to.
   Only the Play Store update turns it into the real app.

2. Unlock developer mode in Android Auto:
   Settings → scroll to the bottom → tap the version number ten times →
   accept the confirmation.

3. In the now-visible developer menu (three dots, top right):
   enable **"Start head unit server"**.

4. Allow unknown sources so the own debug app shows up:
   developer menu → "Allow unknown sources".

## Installing the app and starting the DHU

```bash
./gradlew :androidApp:installDebug
```

```bash
adb forward tcp:5277 tcp:5277
```

**Careful, this forward proves nothing.** `adb forward` accepts the
connection *on the computer* and only tries to deliver it to the phone
afterward. If no head unit server is running there, it's closed again
immediately — on the computer that looks like a port that's answering. The
DHU then only reports `Failed to read from transport - disconnect.
Exiting...`, which looks like a DHU bug and isn't one.

Only the phone can say whether the server is actually running:

```bash
adb shell netstat -lnt | grep 5277
```

No line means the server hasn't started — repeat step 3 above. It stops
when the connection is dropped and must be restarted before every attempt.

The DHU also reads its commands from standard input. Starting it with
`stdin` closed (e.g. from a script) doesn't produce an error, just an
immediate, silent exit.

```bash
/home/julakali/Android/Sdk/extras/google/auto/desktop-head-unit
```

The Android Auto launch screen appears in the DHU window. The charging-stop
assistant is among the apps — the `POI` category ensures it's sorted there
and not under Navigation.

## What can already be verified without the DHU

Even without the Android Auto app, the declaration can be checked. After
`installDebug`:

```bash
adb shell "dumpsys package de.autoapp.android | grep -A6 CarAppService"
```

Expected output — the service must appear with both action **and**
category:

```
androidx.car.app.CarAppService:
  de.autoapp.android/.car.ChargeCarAppService filter ...
    Action: "androidx.car.app.CarAppService"
    Category: "androidx.car.app.category.POI"
```

If `CHARGING` or `PARKING` shows up there, the declaration is wrong — both
categories have been deprecated since Car App Library 1.3.

## What else to check from M1 onward

The list is now built from location and network, not a fixed table anymore.
That means there are states worth actually seeing in the DHU:

1. **Without location permission**, no blank screen appears — instead a
   message with a "Grant location" button. In projection mode the head unit
   can't show the system dialog itself — the host instructs the driver to
   confirm it on the phone.
2. **Without an OpenChargeMap key**, the header reads "Charging stops · Demo
   data" and the list shows fabricated charging parks around the current
   location. That's intentional (see README.md), but it must never happen
   without this label.
3. **Without a network connection**, the most recently fetched list stays
   in place, and the header says "Charging stops · not current". A list
   that goes empty in a tunnel would be useless.

A moving location can be faked without actually driving:

```bash
adb emu geo fix 11.4779 48.9331
```

On a real phone this works through a location-mocking app, registered as a
"mock location app" in developer options. Without a heading, the app
searches all around instead of in a sector — so a single fixed point
produces results, but no corridor.

## Remote-controlling the DHU

**Shortcut: `.claude/skills/dhu/scripts/dhu.sh`.** The script wraps starting
it, sending commands, and taking screenshots, along with the quirks
explained below:

```bash
D=.claude/skills/dhu/scripts/dhu.sh
$D start && $D send "keycode home" && $D shot /tmp/car.png
```

What happens underneath:

The DHU reads commands from standard input and can take its own
screenshots. That makes it possible to check the car UI without any mouse
clicks:

```bash
mkfifo /tmp/dhu_in && setsid sh -c 'sleep 86400 > /tmp/dhu_in' &
setsid sh -c 'exec stdbuf -o0 ./desktop-head-unit --usb < /tmp/dhu_in' > /tmp/dhu.log 2>&1 &
```

After that, `echo "<command>" > /tmp/dhu_in` is enough. The important ones:

| Command | Effect |
|---|---|
| `keycode home` | go to the app launcher |
| `tap <x> <y>` | tap; (0,0) is top left, resolution matches the window |
| `screenshot file.png` | take a screenshot — more precise than a photo of the desktop |
| `location <lat> <lon> [accuracy] [altitude] [speed] [bearing]` | fake a location |
| `compass <bearing>` | set the heading |
| `help` | full list |

`stdbuf -o0` is necessary because otherwise output is block-buffered and
responses only appear much later.

## Pitfalls

- **The DHU doesn't show the app.** Almost always "Allow unknown sources" is
  missing in Android Auto's developer menu.
- **`adb forward` fails.** The head unit server on the phone isn't running;
  repeat step 3 above.
- **The list is shorter than expected.** That's not a bug. The host
  dictates how many rows are allowed via
  `ConstraintManager.getContentLimit(CONTENT_LIMIT_TYPE_LIST)` — typically
  six. The app trims to that.
- **The list stays empty even though the location is set.** First check
  whether the header says "demo data": then the key is missing and the demo
  source is active. If it doesn't say that, the empty list comes from
  OpenChargeMap — when in doubt, check `adb logcat` for the HTTP response.
- **The list stays empty and reports "charging station data is currently
  unavailable" even though the phone is online.** On Android derivatives
  like GrapheneOS, `android.permission.INTERNET` is a **revocable**
  permission and is off for freshly installed apps. The app then gets an
  `UnknownHostException` on every request. Check and grant it:

  ```bash
  adb shell pm grant de.autoapp.android android.permission.INTERNET
  ```

  On stock Android the permission is granted at install time and this line
  is unnecessary.
- **Nothing updates.** Recomputation only happens after 2 km, after 60 s, or
  on a heading change over 45° (`RefreshPolicy`). A single `adb emu geo fix`
  triggers exactly one computation. The refresh action on the right of the
  header forces one.
