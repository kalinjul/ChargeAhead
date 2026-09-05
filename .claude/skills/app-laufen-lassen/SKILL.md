---
name: app-laufen-lassen
description: Installs, launches and drives the charging-stop assistant on an emulator or phone — set location, tap, type text, take screenshots, read back app state. Always use this skill when the app should be run, launched, installed, tried out or looked at, when a screenshot is wanted, when checking whether a change actually landed, when an emulator, phone, adb or device is mentioned, or when someone asks how something looks or whether it works. For the Android Auto car surface, use the `dhu` skill instead.
---

# Running the app on a device

This app shows nothing until it has a **location** and a **network**. Both are
missing on a fresh install, and both explain most "nothing's showing"
moments. `scripts/app.sh` takes care of that.

This skill covers the phone UI. The car UI does **not** run on any
emulator — that's what the `dhu` skill is for.

## The usual flow

```bash
A=.claude/skills/app-laufen-lassen/scripts/app.sh

$A devices                       # what's connected?
$A emulator                      # only if none is running yet
$A install --emulator
$A permissions --emulator        # location and network
$A start --emulator
$A where 48.95 11.45 --emulator  # latitude first; without a location the list stays empty
$A shot /tmp/app.png --emulator
```

With multiple devices attached, every command requires `--phone`,
`--emulator` or `--device <serial>` — better to ask than to install on the
wrong device.

## Interacting

**Never reuse coordinates.** Always `shot` first, look at the image, read the
position off it, then `tap`. The UI shifts with every change: when a fourth
button was added to the header, every old coordinate landed on the wrong
spot — and since a missed tap usually does nothing visible, you only notice
at the next screenshot.

`shot` reports the resolution (typically 1080×2400), and `tap` expects
exactly those device pixels. Watch out if an image was scaled down for
display: then the coordinates you read off it must be scaled back up by that
ratio.

```bash
$A tap 418 145        # tap
$A type "München Hauptbahnhof"
$A key escape         # dismiss the keyboard; also back, home, enter
```

## When nothing shows up

`$A state` shows in one shot what the app thinks: the vehicle profile, the
charge level, the set destination, the network filter — and how many sites
per source are in the local store. That answers most questions faster than
any screenshot.

`$A log` shows only the app's warnings. It deliberately catches errors so it
keeps running, but logs them with a cause. Common findings:

- **`UnknownHostException`** — on Android derivatives like GrapheneOS,
  `INTERNET` is a revocable permission and is off for freshly installed apps.
  `$A permissions` grants it too.
- **Nothing in the log, but the list is empty** — usually a missing location.
  The app then waits visibly ("determining location"); when in doubt, follow
  up with `$A where`.
- **Short list despite data** — the network filter is active. `$A state`
  shows it under `networks.onlyPreferred`.

## Location on a phone

`where` only works in the emulator. On a phone it needs a mock-location app,
registered as such in developer options — otherwise the real location
applies, which is the right thing for an on-site test.

## Resetting the local store

The tile cache intentionally survives restarts. To force a fresh fetch,
delete it:

```bash
adb -s <serial> shell run-as de.autoapp.android \
  rm -f /data/data/de.autoapp.android/databases/charge_sites.db
```

That's how to check whether a change to the sources actually takes effect —
otherwise the app keeps showing the old data for days, and rightly so.
