# Localisation (English + German) — design

> **Status: draft, awaiting Raphael's review.** Two defaults below are marked *to confirm*.

## Goal

Every user-visible word on the phone, in Android Auto and on iOS/CarPlay comes from **one** string source with an English and a German version. Adding a third language means adding one file.

## Decisions taken

- **One source: Compose Multiplatform Resources in `:shared`.** Spike on `spike/compose-resources` showed it builds for android/jvm/iosArm64/iosSimulatorArm64 on AGP 9.4 and reaches all three surfaces.
- **UI text lives in the app.** The backend only localises its own payload (place names) from an `Accept-Language` header; it never sends finished sentences.
- **Typography:** percent hugs the number in both languages (`42%`); counted nouns are plurals in both; arrival/departure are spelled out (`Ankunft`/`Arrival`, `Abfahrt`/`Departure`); *charge point* over *charging point*.
- **Later, not now:** a build-time generator that renders the one source into native `strings.xml` and `.xcstrings` (noted in `plans/technical-debts.md`).

## Defaults to confirm

1. **English is the fallback** (`values/`), German is `values-de/`. A French or Polish phone gets English.
2. **No in-app language picker.** The app follows the system language, and on Android 13+ the system's per-app language setting. CMP resources read `Locale.getDefault()` only, so a picker would not work below Android 13.

## Structure

```
shared/src/commonMain/composeResources/
  values/strings.xml       English (fallback)
  values-de/strings.xml    German
```

- `compose.resources { publicResClass = true; packageOfResClass = "org.julakali.chargeahead.shared.resources" }`, `androidResources { enable = true }` in `androidLibrary {}`.
- Same XML format as today. Two differences from Android: write `%` instead of `%%` and `'` instead of `\'` — CMP doesn't unescape them.
- **Platform-owned strings stay native**, because the OS reads them before any Kotlin runs: `app_name` (manifest label) in `phone-ui/res/values`, untranslatable; the iOS location permission text in `InfoPlist.strings` per `.lproj`. These are the only native leftovers.

## Reading strings

| Caller | API |
|---|---|
| Phone UI (Compose) | `stringResource(Res.string.x)`, `pluralStringResource(...)` |
| Android Auto screens, `ChargeStopFormatter`, iOS | `Texts.string(Res.string.x, args)`, `Texts.plural(...)` |

`Texts` is a small object in `shared`: a synchronous wrapper around CMP's suspend `getString`/`getPluralString` via `runBlocking`. That is the same thing CMP's own `stringResource` does on Android (`ResourceState.blocking.kt`), and strings are cached after the first read. It keeps `onGetTemplate()` synchronous and spares Swift an `async` per label.

**No text in `UiState`** stays a rule: ViewModels survive a language change, so resolved text would go stale. States stay types; words are resolved at the edge.

## Numbers

`ChargeStopFormatter` hand-writes the decimal comma today. It gets a locale-aware `formatDecimal(value, fractionDigits)` (expect/actual: `java.text.NumberFormat` on Android/JVM, `NSNumberFormatter` on iOS, both on the current locale). Units stay metric (km, kWh, kW).

## Per-app language on Android

The Android resource folders will hold no translations any more, so `generateLocaleConfig` has nothing to scan. Instead: a hand-written `res/xml/locales_config.xml` (`en`, `de`) and `android:localeConfig` in the manifest. That makes the app show up in *Settings → Apps → Language* on Android 13+. *Needs a device check by Raphael* that switching there updates `Locale.getDefault()` for the running process (the spike couldn't run a device).

## Migration

- The English already sits in `composeResources/values-en/` (inert until the plugin is on, escapes already in CMP form); it becomes `values/` with the fallback decision. Move `phone-ui/res/values/strings.xml` (German) to `values-de/` and rewrite its escapes.
- `R.string`/`R.plurals` → `Res.string`/`Res.plurals`: about 475 references in 51 files (phone-ui, androidApp car, ui-tests).
- The 10 `LocalContextGetResourceValueCall` lint errors (`context.getString` inside composables) go away in the same move.
- `ChargeStopFormatter` literals → `Res` strings + `formatDecimal`.
- iOS: `de.lproj/Localizable.strings` is deleted; Swift calls `Texts` through the framework. `InfoPlist.strings` for `en` and `de` added; `CFBundleDevelopmentRegion` becomes `en`.
- `HttpClientFactory` sends `Accept-Language` with the current locale. Passing it on to the geocoder (`lang`) is a backend change, tracked separately.
- AGENTS.md: "User-visible text: German" becomes "English and German, exclusively from `shared`'s `composeResources` — never a literal in code"; the "`shared` has no resources" rule becomes "all user-visible text lives in `shared`'s resources".

## Testing

- **Key parity test** (jvmTest): both `strings.xml` files have the same keys, plural quantities and placeholders. CMP has no `MissingTranslation` lint, this replaces it.
- `jvmTest` needs `compose.desktop.currentOs` (skiko) for `getString` on the desktop JVM.
- Formatter tests run per locale (`Locale.setDefault`) for both languages.
- Robolectric UI tests keep reading strings through resources, never literals (one test matched the literal `"an "` and already broke on a wording change).
- Screenshot previews stay `locale = "de"`; a handful of `en` previews for the densest screens (trip tiles, garage, active route). Goldens re-recorded once, in the container, with Raphael's go.

## Order

0. `ios-build-fix` — the iOS app builds at all (separate PR, before everything).
1. Infrastructure + move strings + phone UI + Android Auto + parity test (one mechanical PR, the big one).
2. Formatter + number formatting + iOS switch-over.
3. `Accept-Language` + AGENTS.md rules (can ride along with 2).

## Out of scope

Native string generator (side project), miles/imperial units, British English variant, store listing translations, legal text translation beyond the existing imprint wording.

## Appendix: English wording for strings without a key yet

```
ChargeStopFormatter:
  "Ankunft ca. %d%"           → "Arrival ~%d%"
  "Erreichbar"                → "Reachable"
  "Knapp"                     → "Tight"
  "Nicht erreichbar"          → "Out of range"
  "Ladepunkte unbekannt"      → "Charge points unknown"
  "1 Ladepunkt" / "%d Ladepunkte" → "1 charge point" / "%d charge points"
  "Ladepunkt"                 → "Charge point"
  "Status unbekannt"          → "Status unknown"
  "%d von %d frei"            → "%d of %d free"
  "%d außer Betrieb"          → "%d out of order"
  "Nach %s · …"               → "After %s · …"
  "%s laden bis %d%"          → "%s charging to %d%"
  "Typ 2" / "Unbekannt"       → "Type 2" / "Unknown"
  "8,4 km" (decimal comma)    → "8.4 km" via locale-aware number formatting

iOS only:
  home_map_placeholder        → "Map placeholder — map SDK to follow"
  home_pill_plan              → "Plan"
  home_diagnostics            → "Info"
  plan_title                  → "Plan route"
  trip_stops_heading          → "Charging stops"
  trip_summary_time_fmt       → "%@ on the road · %@ of it charging"
  trip_summary_arrival_fmt    → "Arrive with %d%%"
  cn_relaxed_note             → "Filters too strict — they were relaxed."
  detail_plan_fmt             → "%d min · %d%% → %d%%"
```
