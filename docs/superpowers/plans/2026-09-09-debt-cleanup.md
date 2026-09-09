# Debt Cleanup (Phase 1 of 4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the verified DRY and string-resource fixes from the 2026-09-09 architect review, plus delegate iOS's hand-rolled number formatting to the shared formatter — no behavior change except iOS numbers gaining the German comma.

**Architecture:** Pure consolidation: every task moves an existing implementation to a single home or routes an existing value through an existing mechanism (strings.xml, ChargeStopFormatter). No new abstractions.

**Tech Stack:** Kotlin Multiplatform (shared), Compose (androidApp), SwiftUI (iosApp), existing test suites (`:shared:jvmTest`, commonTest).

**Spec:** The architect review conclusions of 2026-09-09 (this session), scope confirmed by Raphael: "DRY table + three string-resource fixes + iOS formatter delegation". Dropped from the review's DRY table after code verification: the "ConnectorCodec duplicated in PersistentSettingsStore" finding (its `readConnectors` parses a comma-separated enum-name list, not the `type:kW:count` triple — no duplication exists) and the car collect→invalidate helper (4-line idiom, extraction is taste, skipped per KISS).

## Global Constraints

- Branch: `debt/cleanup` off `main`. Commit per task, style `type: subject`, lowercase, ≤70 chars, no ticket, no co-author trailer. Never push.
- Gradle only via the `gradle-run` wrapper (AGENTS.md "Build"); `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` first; resolve `<skill-dir>` once per session.
- Verification gate before claiming done (AGENTS.md): `:androidApp:assembleDebug`, `:shared:jvmTest`, `:shared:compileKotlinIosSimulatorArm64` (shared touched), `tools/check-swift.sh` (Swift touched — syntax only, report as such).
- User-visible text: German, from resources only. Comments: English, why-only.
- Don't touch build files or the version catalog (nothing in this phase needs them).

---

### Task 1: One MIN_DC_POWER_KW

**Files:**
- Create: `shared/src/commonMain/kotlin/de/autoapp/shared/core/Charging.kt`
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/core/ChargeNowRanker.kt:111` (delete private const)
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/core/TripPlanner.kt:332` (delete companion const)

**Interfaces:**
- Produces: `internal const val MIN_DC_POWER_KW: Double = 50.0` in package `de.autoapp.shared.core` — unqualified references in both files keep resolving.

- [ ] **Step 1: Create the shared constant**

```kotlin
package de.autoapp.shared.core

/**
 * The DC fast-charging floor shared by trip planning and "charge now":
 * below this, a stop is parking, not getting back on the road.
 */
internal const val MIN_DC_POWER_KW = 50.0
```

- [ ] **Step 2: Delete `private const val MIN_DC_POWER_KW = 50.0` from ChargeNowRanker.kt:111 and `const val MIN_DC_POWER_KW = 50.0` from TripPlanner.kt's companion (line 332)**

- [ ] **Step 3: Run `:shared:jvmTest`** — expected: PASS (ChargeNowRankerTest, TripPlannerTest unchanged).

- [ ] **Step 4: Commit** — `fix: one dc-power floor, not one per planner`

---

### Task 2: The decimal helpers move in together

**Files:**
- Create: `androidApp/src/main/kotlin/de/autoapp/android/phone/Formatting.kt`
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/GarageScreen.kt:212-215` (delete `oneDecimal`)
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/SubscriptionsScreen.kt:90-94` (delete `twoDecimals` + its kdoc)

**Interfaces:**
- Produces: `internal fun Double.oneDecimal(): String`, `internal fun Double.twoDecimals(): String` in package `de.autoapp.android.phone` — all call sites (DrawerContent, GarageScreen, ChargeMap, AddCarScreen, TripPlanScreen, SubscriptionsScreen, PlanSheets) are same-package, zero import changes.

- [ ] **Step 1: Create Formatting.kt with the two implementations moved verbatim** (including the `"0.49" → "0,49"` kdoc from SubscriptionsScreen). Body of `oneDecimal` is the existing GarageScreen implementation (`roundToInt`-based); keep its `import kotlin.math.roundToInt`.

```kotlin
package de.autoapp.android.phone

import kotlin.math.roundToInt

internal fun Double.oneDecimal(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

/** "0.49" → "0,49": prices are user-visible text and therefore German. */
internal fun Double.twoDecimals(): String {
    val cents = (this * 100).toInt()
    return "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
}
```

- [ ] **Step 2: Delete both original definitions; drop now-unused imports if the IDE-check (`assembleDebug` warnings) flags them**

- [ ] **Step 3: Run `:androidApp:assembleDebug`** — expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** — `chore: the decimal helpers move in together`

---

### Task 3: Car icon builder, written once

**Files:**
- Create: `androidApp/src/main/kotlin/de/autoapp/android/car/CarIcons.kt`
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/car/CarHomeScreen.kt:138-139`, `ChargeNowScreen.kt:150-151`, `RouteScreen.kt:243-244` (delete the three identical private methods)

**Interfaces:**
- Produces: `internal fun Screen.icon(resId: Int): CarIcon` — call sites keep the bare `icon(R.drawable.…)` form because the receiver is the Screen.

- [ ] **Step 1: Create CarIcons.kt**

```kotlin
package de.autoapp.android.car

import androidx.car.app.Screen
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

internal fun Screen.icon(resId: Int): CarIcon =
    CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
```

- [ ] **Step 2: Delete the three private `icon` methods; remove `CarIcon`/`IconCompat` imports where nothing else uses them**

- [ ] **Step 3: Run `:androidApp:assembleDebug`** — expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** — `chore: car icon builder, written once instead of three times`

---

### Task 4: Settings store drops its runCatching chorus

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/settings/PersistentSettingsStore.kt`
- Test (existing, must stay green): `shared/src/jvmTest/.../settings/PersistentSettingsStoreTest.kt`, `GarageAndRoutesSettingsTest.kt`

**Interfaces:**
- Produces (file-private): `putJson(key, value)` writes JSON or removes the key on null; `getJson<T>(key): T?` returns null on missing key or corrupt payload. Callers keep their own defaults (`?: emptyList()` etc.) — the forgiveness semantics ("corrupt history costs the history, not the app") are unchanged.

- [ ] **Step 1: Move `val json = Json { ignoreUnknownKeys = true }` from the private companion to file-level private, and add at file bottom:**

```kotlin
private val json = Json { ignoreUnknownKeys = true }

private inline fun <reified T> KeyValueStorage.putJson(key: String, value: T?) {
    putString(key, value?.let { json.encodeToString(it) })
}

private inline fun <reified T> KeyValueStorage.getJson(key: String): T? =
    getStringOrNull(key)?.let { raw -> runCatching { json.decodeFromString<T>(raw) }.getOrNull() }
```

- [ ] **Step 2: Rewrite the ten call-site pairs mechanically** (writeGarage/readGarage, setDestination/readDestinations, setNetworks/readNetworks, setChargeFilters/readFilters, setActiveTariffIds/readTariffIds, writeSavedRoutes/readSavedRoutes, recordCarDataPoint/readCarData, recordSoCDiagnostics/readDiagnostics). Pattern, using readGarage as the example:

```kotlin
private fun writeGarage(vehicles: List<VehicleProfile>) {
    storage.putJson(
        KEY_GARAGE,
        vehicles.takeIf { it.isNotEmpty() }?.map {
            StoredVehicle(
                name = it.displayName,
                batteryKwh = it.usableBatteryKwh,
                consumption = it.consumptionKwhPer100Km,
                connectors = it.acceptedConnectors.map(ConnectorType::name),
                dcPeakKw = it.dcPeakPowerKw,
            )
        },
    )
    mutableVehicles.value = vehicles
}

private fun readGarage(): List<VehicleProfile> =
    storage.getJson<List<StoredVehicle>>(KEY_GARAGE)
        .orEmpty()
        .mapNotNull { it.toProfileOrNull() }
```

The `takeIf { isNotEmpty() }` guards stay — writing null removes the key, exactly what `putString(key, null)` did. Scalar keys (`KEY_MANUAL_SOC`, `KEY_ONLY_PREFERRED`, `KEY_MAP_LABEL`, legacy vehicle keys) are not JSON and stay on `putString`/`getStringOrNull`.

- [ ] **Step 3: Run `:shared:jvmTest`** — expected: PASS, especially PersistentSettingsStoreTest's corrupt-payload cases.

- [ ] **Step 4: Run `:shared:compileKotlinIosSimulatorArm64`** — expected: SUCCESS (shared touched).

- [ ] **Step 5: Commit** — `chore: settings store learns putJson, drops the runCatching chorus`

---

### Task 5: User text back into strings.xml

**Files:**
- Modify: `androidApp/src/main/res/values/strings.xml` (line 97 + one new string)
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/MainActivity.kt:361-363` (+ call site :284)
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/TripPlanScreen.kt:331`
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/CorridorStopDialog.kt:68`

**Interfaces:**
- Consumes existing resources: `trip_summary_distance` (`%1$d km`), plural `trip_summary_stops` (`%1$d Stopps`).
- Produces: `phone_detail_source` becomes `Angaben aus: %1$s` (single call site); new `trip_summary_cost` = `≈ %1$s €`.

- [ ] **Step 1: strings.xml** — change line 97 to `<string name="phone_detail_source">Angaben aus: %1$s</string>`; add next to `trip_summary_charging`: `<string name="trip_summary_cost">≈ %1$s €</string>`

- [ ] **Step 2: CorridorStopDialog.kt:68** → `text = stringResource(R.string.phone_detail_source, source),`

- [ ] **Step 3: TripPlanScreen.kt:331** (inside the existing `buildString`, which is inline — `stringResource` is already called inside it one line up):

```kotlin
plan.estimatedCostEuro?.let {
    append(" · ")
    append(stringResource(R.string.trip_summary_cost, it.twoDecimals()))
}
```

- [ ] **Step 4: MainActivity — `summaryLine` takes a Context and uses the existing resources; delete the German literal:**

```kotlin
/** Shown under a saved route's name — built from the same resources the trip header uses. */
private fun TripPlan.summaryLine(context: Context): String =
    context.getString(R.string.trip_summary_distance, route.distanceKm.roundToInt()) + " · " +
        context.resources.getQuantityString(R.plurals.trip_summary_stops, stops.size, stops.size)
```

Call site (line 284): `onToggleSave = { tripViewModel.toggleSaved(trip.plan.summaryLine(context)) },`

- [ ] **Step 5: Run `:androidApp:assembleDebug`** — expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit** — `fix: user text back into strings.xml where it belongs`

---

### Task 6: iOS stops inventing its own number formats

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/ChargeStopFormatter.kt` (three new public label functions)
- Test: `shared/src/commonTest/kotlin/de/autoapp/shared/ChargeStopFormatterTest.kt`
- Modify: `iosApp/AutoApp/Phone/HomeMapView.swift:253,258`, `iosApp/AutoApp/Phone/TripPlanView.swift:46-50,55,110`

**Interfaces:**
- Produces on `ChargeStopFormatter`: `powerKwLabel(powerKw: Double): String` → `"150 kW"`; `pricePerKwhLabel(euroPerKwh: Double): String` → `"0,54 €/kWh"`; `minutesLabel(minutes: Double): String` → `"25 min"`. Swift reaches the object as `ChargeStopFormatter.shared` (same as the existing `MapsHandoff.shared` usage).

- [ ] **Step 1: Write the failing tests in ChargeStopFormatterTest**

```kotlin
@Test
fun labels_for_platform_composed_lines_use_german_formats() {
    assertEquals("150 kW", ChargeStopFormatter.powerKwLabel(150.4))
    assertEquals("0,54 €/kWh", ChargeStopFormatter.pricePerKwhLabel(0.54))
    assertEquals("0,05 €/kWh", ChargeStopFormatter.pricePerKwhLabel(0.049))
    assertEquals("25 min", ChargeStopFormatter.minutesLabel(24.6))
}
```

- [ ] **Step 2: Run `:shared:jvmTest --tests '*ChargeStopFormatterTest'`** — expected: FAIL, unresolved references.

- [ ] **Step 3: Implement in ChargeStopFormatter (next to `distanceLabel`, reusing the private helpers):**

```kotlin
/** e.g. "150 kW" — for lines a platform composes itself (iOS phone rows). */
fun powerKwLabel(powerKw: Double): String = "${formatPowerKw(powerKw)} kW"

/** e.g. "0,54 €/kWh" — same digits and comma as the car's charge-now row. */
fun pricePerKwhLabel(euroPerKwh: Double): String = "${formatEuro(euroPerKwh)}/kWh"

/** e.g. "25 min". */
fun minutesLabel(minutes: Double): String = "${formatWholeNumber(minutes)} min"
```

- [ ] **Step 4: Run the test again** — expected: PASS.

- [ ] **Step 5: Swift call sites** — replace the five `String(format:)` uses:

HomeMapView.swift:253:
```swift
Text("\(ChargeStopFormatter.shared.distanceLabel(distanceKm: candidate.distanceKm)) · \(ChargeStopFormatter.shared.powerKwLabel(powerKw: candidate.maxPowerKw))")
```
HomeMapView.swift:258, TripPlanView.swift:55 and :110:
```swift
Text(ChargeStopFormatter.shared.pricePerKwhLabel(euroPerKwh: best.euroPerKwh))
```
(at :110 the receiver is `price.euroPerKwh`)

TripPlanView.swift:46-50:
```swift
Text("\(ChargeStopFormatter.shared.minutesLabel(minutes: stop.chargeMinutes)) · \(ChargeStopFormatter.shared.powerKwLabel(powerKw: stop.maxPowerKw))")
```

- [ ] **Step 6: Run `:shared:compileKotlinIosSimulatorArm64` and `tools/check-swift.sh`** — expected: SUCCESS / parses. Note in the report: Swift is syntax-checked only; the `.shared` accessor shape follows the existing `MapsHandoff.shared` precedent but stays unverified without a Mac (AGENTS.md).

- [ ] **Step 7: Commit** — `fix: ios stops inventing its own number formats`

---

### Task 7: Full verification gate

- [ ] **Step 1: gradle-run workflow:** `:androidApp:assembleDebug`, `:shared:jvmTest`, `:shared:compileKotlinIosSimulatorArm64` — all green, quote the wrapper's answers.
- [ ] **Step 2: `tools/check-swift.sh`** — parses (or report exit 127 as "syntactically unverified").
- [ ] **Step 3: `git status`** — nothing outside the listed files touched.

## Self-Review Notes

- Spec coverage: DRY table (constants ✔ T1, decimal helpers ✔ T2, icon ✔ T3, putJson ✔ T4; codec + invalidate-bridge deliberately dropped, documented in header) — strings ✔ T5 — iOS formatter ✔ T6.
- Type consistency: `icon(resId: Int)` matches existing call sites; `summaryLine(context)` signature change has exactly one caller (MainActivity.kt:284); formatter label names used identically in Kotlin test, impl, and Swift.
- No placeholders: every step carries the code it needs.
