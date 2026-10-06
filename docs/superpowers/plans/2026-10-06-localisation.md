# Localisation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every user-visible word on phone, Android Auto and iOS comes from one English/German string source in `:shared`.

**Architecture:** Compose Multiplatform Resources in `shared/src/commonMain/composeResources` (`values/` English fallback, `values-de/` German). Compose reads via `stringResource(Res.string.x)`; Android Auto, `ChargeStopFormatter` and Swift read through a synchronous `Texts` object (`runBlocking` around CMP's `getString`, the same thing CMP's own `stringResource` does). Numbers get a locale-aware `formatDecimal`.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4 (`android.kmp.library`), Compose Multiplatform 1.12.1 resources, SKIE 0.10.15, Robolectric, Compose Preview Screenshot Testing.

**Spec:** `docs/superpowers/specs/2026-10-06-localisation-design.md`

## Global Constraints

- English is the fallback (`values/`), German is `values-de/`. No in-app language picker.
- Percent hugs its number in both languages: `42%`. CMP strings write `%` and `'` plainly (no `%%`, no `\'`).
- Counted nouns are `<plurals>` in both languages.
- Platform-owned strings stay native: `app_name` in `phone-ui/src/main/res/values/strings.xml` (`translatable="false"`), the iOS location permission text in `InfoPlist.strings`.
- No user-visible text in a `UiState`; words are resolved at the edge.
- Tests never match literal UI text; they read the same resource.
- Gradle: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`, `./gradlew --console=plain --no-scan`, full log to the scratchpad, grep summaries only.
- Goldens only via `tools/screenshots.sh` (Linux container); a native Mac run gives false failures.
- Commits: `type: subject`, lowercase, ≤70 chars, dry tone, no ticket, no co-author trailer.
- Work happens on branch `localisation-en` (worktree `.claude/worktrees/localisation-en`); everything lands in PR #183.

## File Structure

| File | Responsibility |
|---|---|
| `shared/src/commonMain/composeResources/values/strings.xml` | English, all keys (renamed from `values-en/`) |
| `shared/src/commonMain/composeResources/values-de/strings.xml` | German, all keys |
| `shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/Texts.kt` | synchronous string/plural lookup for non-Compose callers and Swift |
| `shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/NumberFormat.kt` | `expect fun formatDecimal(value: Double, fractionDigits: Int): String`, `expect fun currentLanguageTag(): String` |
| `shared/src/{android,jvm,ios}Main/.../NumberFormat.<platform>.kt` | the actuals |
| `shared/src/jvmTest/.../StringParityTest.kt` | both XML files share keys, plural quantities, placeholders |
| `shared/src/jvmTest/.../TextsTest.kt` | `Texts` follows `Locale.getDefault()` |
| `phone-ui/src/main/kotlin/.../CoarseDuration.kt` | returns `PluralStringResource` instead of an Android id |
| `androidApp/src/main/res/xml/locales_config.xml` | `en`, `de` for Android 13+ per-app language |
| `iosApp/ChargeAhead/{en,de}.lproj/InfoPlist.strings` | location permission text |

---

### Task 1: Compose Resources in `:shared`, both languages, `Texts`, parity test

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `shared/build.gradle.kts`
- Move: `shared/src/commonMain/composeResources/values-en/strings.xml` → `.../values/strings.xml`
- Create: `shared/src/commonMain/composeResources/values-de/strings.xml` (from `phone-ui/src/main/res/values/strings.xml`, minus `app_name`, escapes rewritten)
- Create: `shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/Texts.kt`
- Test: `shared/src/jvmTest/kotlin/org/julakali/chargeahead/shared/StringParityTest.kt`, `shared/src/jvmTest/kotlin/org/julakali/chargeahead/shared/TextsTest.kt`

**Interfaces:**
- Produces: `org.julakali.chargeahead.shared.resources.Res` (public), `Texts.string(resource: StringResource, vararg args: Any): String`, `Texts.plural(resource: PluralStringResource, quantity: Int, vararg args: Any): String`, `Texts.byKey(key: String, args: List<Any> = emptyList()): String` (Swift entry point).

- [ ] **Step 1: Gradle.** In `libs.versions.toml`: `composePlugin = "1.12.1"`; libraries `compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "composePlugin" }`, `compose-mp-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "composePlugin" }`; plugin `compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "composePlugin" }`. Root `build.gradle.kts`: `alias(libs.plugins.compose.multiplatform) apply false`. `shared/build.gradle.kts`:

```kotlin
plugins {
    // …existing…
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "org.julakali.chargeahead.shared.resources"
}
// in androidLibrary { … }
androidResources { enable = true }
// commonMain.dependencies
api(libs.compose.components.resources)
api(libs.compose.mp.runtime)
// jvmTest.dependencies — getString() on the desktop JVM asks skiko for the system theme
implementation(compose.desktop.currentOs)
```

- [ ] **Step 2: Strings.** `git mv` the `values-en` file to `values/`. Generate `values-de/strings.xml` from the phone-ui file with a script: drop `app_name`, replace `%%` → `%` and `\'` → `'`. Keep the phone-ui file untouched for now (Tasks 2–3 still compile against `R`).

- [ ] **Step 3: Write the failing tests.**

```kotlin
// StringParityTest.kt
class StringParityTest {
    private fun load(dir: String): Map<String, Set<String>> {
        val root = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(java.io.File("src/commonMain/composeResources/$dir/strings.xml")).documentElement
        val entries = mutableMapOf<String, Set<String>>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? org.w3c.dom.Element ?: continue
            val texts = when (node.tagName) {
                "string" -> listOf("" to node.textContent)
                "plurals" -> (0 until node.childNodes.length).mapNotNull { node.childNodes.item(it) as? org.w3c.dom.Element }
                    .map { it.getAttribute("quantity") to it.textContent }
                else -> continue
            }
            entries[node.getAttribute("name")] =
                texts.map { (quantity, text) -> quantity + Regex("%\\d\\$[sd]").findAll(text).map { it.value }.sorted() }.toSet()
        }
        return entries
    }

    @Test
    fun `english and german have the same keys, quantities and placeholders`() {
        assertEquals(load("values"), load("values-de"))
    }

    @Test
    fun `no android escapes, compose resources would show them literally`() {
        for (dir in listOf("values", "values-de")) {
            val text = java.io.File("src/commonMain/composeResources/$dir/strings.xml").readText()
            assertFalse("%%" in text, dir)
            assertFalse("\\'" in text, dir)
        }
    }
}
```

`one` vs `other` placeholder sets may legitimately differ (e.g. `"1 stop"` without `%1$d`); if a German plural uses a fixed word for `one`, adjust the English to match rather than loosening the test.

```kotlin
// TextsTest.kt
class TextsTest {
    private val saved = Locale.getDefault()
    @AfterTest fun restore() = Locale.setDefault(saved)

    @Test
    fun `texts follow the default locale`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("Jetzt laden", Texts.string(Res.string.cn_title))
        Locale.setDefault(Locale.US)
        assertEquals("Charge now", Texts.string(Res.string.cn_title))
    }

    @Test
    fun `plurals pick the quantity`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("1 Stopp", Texts.plural(Res.plurals.trip_summary_stops, 1, 1))
        assertEquals("3 Stopps", Texts.plural(Res.plurals.trip_summary_stops, 3, 3))
    }

    @Test
    fun `swift looks texts up by key`() {
        Locale.setDefault(Locale.US)
        assertEquals("Charge now", Texts.byKey("cn_title"))
    }
}
```

- [ ] **Step 4: Run, expect failure** (`Texts` missing): `./gradlew :shared:jvmTest --tests '*TextsTest*' --tests '*StringParityTest*'`.

- [ ] **Step 5: Implement `Texts`.**

```kotlin
package org.julakali.chargeahead.shared

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.allStringResources

/**
 * Strings for callers that can't suspend or compose: car templates, the formatter, Swift.
 * Blocking is what CMP's own stringResource does on Android and iOS; files are cached after the first read.
 */
object Texts {
    fun string(resource: StringResource, vararg args: Any): String =
        runBlocking { getString(resource, *args) }

    fun plural(resource: PluralStringResource, quantity: Int, vararg args: Any): String =
        runBlocking { getPluralString(resource, quantity, *args) }

    fun byKey(key: String, args: List<Any> = emptyList()): String =
        string(requireNotNull(Res.allStringResources[key]) { "no string $key" }, *args.toTypedArray())
}
```

If `Res.allStringResources` isn't generated in 1.12.1, check the generated sources under `shared/build/generated/compose/resourceGenerator` for the map's actual name before inventing one.

- [ ] **Step 6: Run tests, expect pass.** Same command, plus `:shared:compileKotlinIosSimulatorArm64` (runBlocking must resolve in commonMain for jvm/android/native).

- [ ] **Step 7: Commit** `feat: compose resources in shared, english and german, one source`.

---

### Task 2: Phone UI reads `Res`

**Files:**
- Modify: every `phone-ui/src/main/kotlin/**/*.kt` that references `R.string`/`R.plurals` (27 files), `CoarseDuration.kt`
- Modify: `ui-tests/src/test/kotlin/org/julakali/chargeahead/uitests/Fixtures.kt` and the 15 ui-tests files using `R.string`, the screenshot previews using `R.string`

**Interfaces:**
- Consumes: `Res`, `Texts` from Task 1.
- Produces: `fun coarseDuration(minutes: Long): Pair<PluralStringResource, Int>`; `fun ComposeRule.string(resource: StringResource, vararg args: Any): String = Texts.string(resource, *args)`.

- [ ] **Step 1: Mechanical rewrite** with a script over phone-ui and ui-tests Kotlin files:
  - `R.string.` → `Res.string.`, `R.plurals.` → `Res.plurals.`
  - imports `androidx.compose.ui.res.stringResource` → `org.jetbrains.compose.resources.stringResource`, same for `pluralStringResource`
  - add `import org.julakali.chargeahead.shared.resources.Res` and one import per used accessor (`import org.julakali.chargeahead.shared.resources.<key>`) — CMP accessors are extension properties, each needs its import. Generate the import list per file from the keys it uses.
  - drop `import org.julakali.chargeahead.android.phone.R` where no `R.` is left (`R.drawable` etc. stay).
- [ ] **Step 2: Non-Compose call sites.** `context.getString(R.string.x, …)` in `PhoneApp.kt` (lines ~92, 169, 174, 238–257) and `ActiveRouteScreen.kt:64-65` become `Texts.string(Res.string.x, …)` — they run in callbacks/effects, not in composition. That clears the 10 `LocalContextGetResourceValueCall` lint errors.
- [ ] **Step 3: `CoarseDuration`** returns `Res.plurals.phone_duration_*` (type `PluralStringResource`); callers use `pluralStringResource(plural, count, count)`.
- [ ] **Step 4: Test helper.** `Fixtures.kt`: `fun ComposeRule.string(resource: StringResource, vararg args: Any): String = Texts.string(resource, *args)`. `robolectric.properties` keeps `qualifiers=de-rDE` and gets the comment "Tests run in German: 24h clock and comma decimals in every assertion." (the "app ships German only" line is no longer true).
- [ ] **Step 5: Run** `./gradlew :phone-ui:compileDebugKotlin :ui-tests:testDebugUnitTest :phone-ui:lintDebug`. Expected: 134 tests green; lint has no `LocalContextGetResourceValueCall` left.
- [ ] **Step 6: Commit** `refactor: the phone ui reads its words from shared`.

---

### Task 3: Android Auto reads `Res`

**Files:**
- Modify: `androidApp/src/main/kotlin/org/julakali/chargeahead/android/car/*.kt` (9 files with `getString`), `androidApp/src/test/kotlin/org/julakali/chargeahead/android/car/*Test.kt` (5 files)

- [ ] **Step 1: Rewrite** `carContext.getString(R.string.x, args)` → `Texts.string(Res.string.x, args)`; `carContext.resources.getQuantityString(R.plurals.x, n, n)` → `Texts.plural(Res.plurals.x, n, n)`; same accessor-import rule as Task 2.
- [ ] **Step 2: Test helpers** `private fun string(id: Int, vararg args: Any)` → `private fun string(resource: StringResource, vararg args: Any) = Texts.string(resource, *args)`.
- [ ] **Step 3: Run** `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug`. Expected: 25 tests green.
- [ ] **Step 4: Commit** `refactor: android auto reads its words from shared too`.

---

### Task 4: Android leftovers — drop old strings, per-app language

**Files:**
- Modify: `phone-ui/src/main/res/values/strings.xml` (keep only `app_name`)
- Create: `androidApp/src/main/res/xml/locales_config.xml`, `androidApp/src/main/res/resources.properties`
- Modify: `androidApp/src/main/AndroidManifest.xml`

- [ ] **Step 1:** Reduce the phone-ui strings file to `app_name`. Build fails if anything still references `R.string.<other>` — that is the check.
- [ ] **Step 2:** `locales_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="de" />
</locale-config>
```

`resources.properties`: `unqualifiedResLocale=en`. Manifest `<application android:localeConfig="@xml/locales_config" …>`.
- [ ] **Step 3: Run** `./gradlew :androidApp:assembleDebug :androidApp:lintDebug :phone-ui:lintDebug`.
- [ ] **Step 4: Commit** `feat: android 13 lists the app under per-app language`.

---

### Task 5: Formatter speaks both languages, numbers follow the locale

**Files:**
- Modify: `shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/ChargeStopFormatter.kt`
- Create: `shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/NumberFormat.kt`, actuals in `androidMain`, `jvmMain`, `iosMain`
- Modify: both `strings.xml` (new keys, see below)
- Move: `shared/src/commonTest/.../ChargeStopFormatterTest.kt` → `shared/src/jvmTest/...` (needs `Locale.setDefault`)

**Interfaces:**
- Produces: `expect fun formatDecimal(value: Double, fractionDigits: Int): String`, `expect fun currentLanguageTag(): String`.

New keys (English / German):

| key | en | de |
|---|---|---|
| `fmt_arrival_about` | `Arrival ~%1$s%` | `Ankunft ca. %1$s%` |
| `fmt_reachable` | `Reachable` | `Erreichbar` |
| `fmt_marginal` | `Tight` | `Knapp` |
| `fmt_unreachable` | `Out of range` | `Nicht erreichbar` |
| `fmt_charge_points_unknown` | `Charge points unknown` | `Ladepunkte unbekannt` |
| plurals `fmt_charge_points` | `%1$d charge point` / `%1$d charge points` | `%1$d Ladepunkt` / `%1$d Ladepunkte` |
| `fmt_charge_point` | `Charge point` | `Ladepunkt` |
| `fmt_status_unknown` | `Status unknown` | `Status unbekannt` |
| `fmt_available_of` | `%1$d of %2$d free` | `%1$d von %2$d frei` |
| `fmt_out_of_order` | `%1$d out of order` | `%1$d außer Betrieb` |
| `fmt_after_distance` | `After %1$s` | `Nach %1$s` |
| `fmt_charge_to` | `%1$s charging to %2$s%` | `%1$s laden bis %2$s%` |
| `fmt_connector_type2` | `Type 2` | `Typ 2` |
| `fmt_connector_unknown` | `Unknown` | `Unbekannt` |

- [ ] **Step 1: Failing tests** in the moved `ChargeStopFormatterTest`: run every existing assertion under `Locale.GERMANY` (unchanged expectations), and add English twins, e.g.

```kotlin
@Test
fun `english secondary line`() {
    Locale.setDefault(Locale.US)
    assertEquals("Arrival ~34%", ChargeStopFormatter.secondaryLine(stopWithSoc(34.0)))
}

@Test
fun `distances below ten km use the locale's decimal separator`() {
    Locale.setDefault(Locale.GERMANY)
    assertEquals("8,4 km", ChargeStopFormatter.distanceLabel(8.43))
    Locale.setDefault(Locale.US)
    assertEquals("8.4 km", ChargeStopFormatter.distanceLabel(8.43))
}
```

(`stopWithSoc` = whatever fixture the existing `"Ankunft ca. 34%"` test builds; reuse it.)
- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Implement.** Actuals:

```kotlin
// androidMain and jvmMain
actual fun formatDecimal(value: Double, fractionDigits: Int): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault()).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        isGroupingUsed = false
    }.format(value)

actual fun currentLanguageTag(): String = java.util.Locale.getDefault().toLanguageTag()

// iosMain
actual fun formatDecimal(value: Double, fractionDigits: Int): String =
    NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        minimumFractionDigits = fractionDigits.toULong()
        maximumFractionDigits = fractionDigits.toULong()
        usesGroupingSeparator = false
        locale = NSLocale.currentLocale
    }.stringFromNumber(NSNumber(value))!!

actual fun currentLanguageTag(): String = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
```

Formatter: every German literal becomes `Texts.string(Res.string.fmt_…)` / `Texts.plural(…)`; `formatDistanceKm` uses `formatDecimal(distanceKm, 1)` below 10 km. Units (`km`, `kW`, `min`, `m`) stay literal — they are the same in both languages.
- [ ] **Step 4: Run** `./gradlew :shared:jvmTest :shared:compileKotlinIosSimulatorArm64 :androidApp:testDebugUnitTest :ui-tests:testDebugUnitTest`.
- [ ] **Step 5: Commit** `feat: the formatter learns english, decimals follow the locale`.

---

### Task 6: iOS reads the shared strings

**Files:**
- Modify: `iosApp/ChargeAhead/**/*.swift` (every `NSLocalizedString`), `iosApp/project.yml`, `iosApp/ChargeAhead/Info.plist`
- Delete: `iosApp/ChargeAhead/de.lproj/Localizable.strings`
- Create: `iosApp/ChargeAhead/en.lproj/InfoPlist.strings`, `iosApp/ChargeAhead/de.lproj/InfoPlist.strings`
- Modify: both `strings.xml` (iOS-only keys)

- [ ] **Step 1: Keys.** Reuse Android keys where the text matches; map `status_*` to the existing `phone_status_*` keys (same wording) by changing the Swift `statusKey` values. Add iOS-only keys to both files, prefixed `ios_`: `ios_car_list_title` (Charging stops / Ladestopps), `ios_home_map_placeholder`, `ios_home_pill_plan` (Plan / Planen), `ios_home_diagnostics` (Info / Info), `ios_plan_title` (Plan route / Route planen), `ios_trip_summary_time` (`%1$s on the road · %2$s of it charging` / `%1$s unterwegs · davon %2$s laden`), `ios_trip_summary_arrival` (`Arrive with %1$d%` / `Ankunft mit %1$d%`), `ios_cn_relaxed_note`, `ios_detail_plan` (`%1$d min · %2$d% → %3$d%`), `ios_hint_car_ui` (the CarPlay variant of `phone_hint_car_ui`), `ios_plan_failed_no_charger` (no-argument variant). Wording for the rest from the spec appendix.
- [ ] **Step 2: Swift.** `NSLocalizedString("x", comment: "")` → `Texts.shared.byKey(key: "x", args: [])`; `String(format: NSLocalizedString(...), a, b)` → `Texts.shared.byKey(key: "...", args: [a, b])`. `app_name` stays `Bundle.main` (`CFBundleDisplayName`) — `PowerTrip`, not translated.
- [ ] **Step 3: Platform strings.** `InfoPlist.strings` per language with `NSLocationWhenInUseUsageDescription` (de: the current text; en: "PowerTrip needs your location to show reachable charging stops in your direction of travel."). `Info.plist`/`project.yml`: `CFBundleDevelopmentRegion` → `en`, add `de` to known regions (`options.developmentLanguage: en` in project.yml).
- [ ] **Step 4: Verify** with the README's build (`cd iosApp && xcodegen generate && xcodebuild … build`), expect `** BUILD SUCCEEDED **`; `grep -r NSLocalizedString iosApp` is empty. A `byKey` test in `TextsTest` already covers the lookup; add one assertion per Swift-used key so a typo in Swift's key list fails in jvmTest:

```kotlin
@Test
fun `every key swift asks for exists`() {
    val swiftKeys = java.io.File("../iosApp/ChargeAhead").walk().filter { it.extension == "swift" }
        .flatMap { Regex("byKey\\(key: \"([a-z_0-9]+)\"").findAll(it.readText()).map { m -> m.groupValues[1] } }
        .toSet()
    assertTrue(swiftKeys.isNotEmpty())
    swiftKeys.forEach { assertNotNull(Res.allStringResources[it], it) }
}
```

Keys built dynamically (`statusKey`) must be returned as literals from a `switch` so the regex sees them: write them as `Texts.shared.byKey(key: "phone_status_loading", args: [])` inside each case instead of returning a key string.
- [ ] **Step 5: Commit** `feat: ios reads the shared strings, english first`.

---

### Task 7: `Accept-Language` to the backend

**Files:**
- Modify: `shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/data/HttpClientFactory.kt`
- Test: `shared/src/jvmTest/kotlin/org/julakali/chargeahead/shared/data/HttpClientFactoryTest.kt` (create if absent)

- [ ] **Step 1: Failing test** with Ktor `MockEngine`:

```kotlin
@Test
fun `requests carry the current language`() = runTest {
    Locale.setDefault(Locale.GERMANY)
    var seen: String? = null
    val engine = MockEngine { request ->
        seen = request.headers[HttpHeaders.AcceptLanguage]
        respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }
    createHttpClient(engine, BackendConfig.of("https://example.invalid", "t")!!).get("v1/ping")
    assertEquals("de-DE", seen)
}
```

- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Implement** in `DefaultRequest`: `header(HttpHeaders.AcceptLanguage, currentLanguageTag())`. `DefaultRequest` runs per request, so a language switch applies to the next call. Check whether any repository caches geocoder results by query; if so, add the language to the cache key.
- [ ] **Step 4: Run** `./gradlew :shared:jvmTest`, expect pass.
- [ ] **Step 5: Commit** `feat: the backend learns which language to answer in`.

---

### Task 8: Rules, docs, goldens, full verification

**Files:**
- Modify: `AGENTS.md` (Language section, "`shared` has no resources" rule), `docs/testing.md` if it mentions `R.string` helpers, `iosApp/README.md` (`de.lproj/Localizable.strings` line)
- Modify: a few screenshot previews: add `locale = "en"` twins for `TripSheetTiles`, `GarageArrivalSheet`, `ActiveRoute`

- [ ] **Step 1: AGENTS.md:** "User-visible text: English and German, exclusively from `shared/src/commonMain/composeResources` (`Res.string`) — never a literal in code. `app_name` and the iOS permission text are the only native strings." Replace "`shared` has no resources" with "All user-visible text lives in `shared`'s resources; states and reasons stay types, the words are resolved at the edge."
- [ ] **Step 2: English previews**, then `tools/screenshots.sh update`, inspect the new `en` PNGs (tile width with "Arrival 4:08 PM"), then `tools/screenshots.sh validate`.
- [ ] **Step 3: Full run:** `./gradlew :androidApp:assembleDebug :shared:jvmTest :shared:compileKotlinIosSimulatorArm64 :androidApp:testDebugUnitTest :ui-tests:testDebugUnitTest :phone-ui:lintDebug` and the iOS `xcodebuild`.
- [ ] **Step 4: Commit** `docs: agents.md stops insisting on german` and `chore: goldens, now also in english`.
- [ ] **Step 5:** code review of the whole branch (`/code-review`), fix, push, update the #183 description.
