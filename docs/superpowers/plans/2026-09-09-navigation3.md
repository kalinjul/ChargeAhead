# Navigation3 (Phase 3 of 4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `Page` enum navigation in `MainActivity` with a Navigation3 back stack — real push/pop semantics, `StopDetail` with an explicit serializable argument, hand-rolled back logic deleted. Plus the two review findings that live in the same files: TripPlanScreen's section-selection state moves into `TripViewModel`, and `PlanSheets.kt` splits into three files.

**Architecture:** `androidx.navigation3` (androidApp only — the iOS phone UI is SwiftUI by documented decision, so the JetBrains multiplatform artifact would navigate nothing; swapping coordinates later is trivial if CMP-for-iOS ever happens). Destinations are `@Serializable` `NavKey` objects; `rememberNavBackStack` + `NavDisplay` + `entryProvider` replace the `when (page)` block. Sheets and drawer stay component state (modeling modal sheets as Nav3 scenes is ceremony the current code explicitly avoids). **ViewModels stay activity-scoped:** the per-entry scoping add-on `lifecycle-viewmodel-navigation3` rides the lifecycle 2.11 train, and lifecycle 2.11 needs compileSdk 37, which ARCHITECTURE §9 documents as unavailable — when Platform 37 lands, per-entry scope is one artifact plus one decorator line.

**Tech Stack:** `androidx.navigation3:navigation3-runtime` / `navigation3-ui` 1.1.6 (stable Aug 2026; requires compileSdk 36 ✓, minSdk 23 ≤ 26 ✓). kotlinx-serialization plugin added to androidApp (NavKeys must be `@Serializable` for `rememberNavBackStack`'s saved state).

**Version risk, resolved empirically at Task 1:** if 1.1.6 transitively drags lifecycle ≥ 2.11 (AAR metadata check fails against compileSdk 36), step down to the highest navigation3 version that resolves — 1.0.0 is the floor. Record the outcome in the commit body.

**Spec:** plans/technical-debts.md Navigation3 item + the 2026-09-09 review proposal (approved). Branch `debt/navigation3` off `debt/koin`.

## Global Constraints

- Same as Phases 1–2 (commit style, never push, bare-gradlew fallback with scratchpad logs, JAVA_HOME export).
- Gate: `:androidApp:assembleDebug`, `:shared:jvmTest`, `:shared:compileKotlinIosSimulatorArm64` (shared is touched by Task 4), plus emulator navigation walk (back behavior is runtime behavior).
- Navigation semantics to preserve exactly (today's `BackHandler`/`onBack` hierarchy): drawer closes before sheet before page; STOP_DETAIL→TRIP, VEHICLE_EDIT/ADD_CAR→GARAGE, everything else→HOME; back on HOME with nothing open leaves the app. A real stack gives all of this for free *if* drawer navigation pops to home before pushing (drawer targets never stack on each other today).

---

### Task 1: Navigation3 enters the catalog

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `androidApp/build.gradle.kts` (serialization plugin + two deps)

- [ ] **Step 1: Catalog** — version `navigation3 = "1.1.6"`; libraries:

```toml
# --- Navigation (phone app only — the car has the Car App Library's own stack) ---
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
```

- [ ] **Step 2: androidApp/build.gradle.kts** — plugins block gains `alias(libs.plugins.kotlin.serialization)`; dependencies gain, next to the koin pair:

```kotlin
implementation(libs.androidx.navigation3.runtime)
implementation(libs.androidx.navigation3.ui)
```

- [ ] **Step 3: Run `:androidApp:assembleDebug`.** If the AAR metadata check rejects a transitive lifecycle ≥ 2.11 against compileSdk 36: lower `navigation3` until it resolves (floor 1.0.0); note the chosen version + reason in the commit body.

- [ ] **Step 4: Commit** — `chore: navigation3 enters the catalog`

---

### Task 2: The Page enum becomes a back stack

**Files:**
- Create: `androidApp/src/main/kotlin/de/autoapp/android/phone/Destinations.kt`
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/MainActivity.kt` (the whole navigation skeleton)
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/DrawerContent.kt:46` (+4 call sites: `Page` → destination)
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/PhoneViewModel.kt` (kdoc only: activity scope now blocked by the compileSdk-36 cap, not by "no per-destination store exists")

**Interfaces:**
- Produces `PhoneDestination` (sealed, `NavKey`): `Home`, `Trip`, `StopDetail(index: Int)`, `Garage`, `AddCar`, `VehicleEdit`, `Subscriptions`, `Networks`, `CarData`. `StopDetail.index` indexes `TripUiState.Planned.plan.stops`.

- [ ] **Step 1: Destinations.kt**

```kotlin
package de.autoapp.android.phone

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The phone's destinations. StopDetail carries an index into the current
 * plan's stops: the plan lives in TripViewModel, and an index survives
 * process death where a PlannedStop object would not.
 */
internal sealed interface PhoneDestination : NavKey

@Serializable internal data object Home : PhoneDestination

@Serializable internal data object Trip : PhoneDestination

@Serializable internal data class StopDetail(val index: Int) : PhoneDestination

@Serializable internal data object Garage : PhoneDestination

@Serializable internal data object AddCar : PhoneDestination

@Serializable internal data object VehicleEdit : PhoneDestination

@Serializable internal data object Subscriptions : PhoneDestination

@Serializable internal data object Networks : PhoneDestination

@Serializable internal data object CarData : PhoneDestination
```

- [ ] **Step 2: MainActivity — the skeleton.** Delete `Page` enum, `page` var, `detailStop` var. New spine (branch bodies move verbatim from the current `when (page)`):

```kotlin
val backStack = rememberNavBackStack(Home)
val current = backStack.lastOrNull() as? PhoneDestination ?: Home

// Drawer targets never stack on each other (today: page = target, back → HOME).
fun openFromRoot(target: PhoneDestination) {
    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    if (target != Home) backStack.add(target)
}
fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
```

- `LaunchedEffect(tripEvent)`: `TripEvent.PlanReady -> openFromRoot(Trip)`.
- Drawer: `onOpen = { target -> scope.launch { drawerState.close(); openFromRoot(target) } }`.
- TopBar: `if (current != Home)` + `when (current)` (exhaustive over `PhoneDestination`); STOP_DETAIL subtitle derives the stop from `(current as? StopDetail)?.index` + `planned?.plan`; `onBack = ::pop`.
- Scaffold content becomes:

```kotlin
NavDisplay(
    backStack = backStack,
    onBack = { pop() },
    entryProvider = entryProvider {
        entry<Home> { /* HomeRoute(...) — body verbatim, incl. onPlan/onChargeNow/onRoutes sheet openers */ }
        entry<Trip> {
            planned?.let { trip ->
                TripPlanScreen(
                    /* args verbatim, except: */
                    onOpenStop = { stop -> backStack.add(StopDetail(trip.plan.stops.indexOf(stop))) },
                )
            } ?: run { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
        }
        entry<StopDetail> { key ->
            planned?.plan?.stops?.getOrNull(key.index)?.let { stop ->
                StopDetailScreen(stop = stop, onSendToMaps = ::sendToMaps, modifier = ...)
            } ?: run { pop() }
        }
        entry<Garage> { GarageRoute(onOpenAdvanced = { backStack.add(VehicleEdit) }, onOpenAdd = { backStack.add(AddCar) }, ...) }
        entry<AddCar> { AddCarRoute(onAdded = { preset -> pop(); snackbar.show(scope, context.getString(R.string.garage_added, preset.name)) }, ...) }
        entry<VehicleEdit> { VehicleSettingsRoute(...) }
        entry<Subscriptions> { SubscriptionsRoute(...) }
        entry<Networks> { NetworksRoute(...) }
        entry<CarData> { CarDataDebugRoute(...) }
    },
    modifier = Modifier.fillMaxSize(),
)
```

(If the resolved navigation3 version's `NavDisplay.onBack` takes a count — `onBack = { count -> repeat(count) { pop() } }` — adapt at compile time; both shapes exist across 1.x.)

- `BackHandler` shrinks to drawer/sheet only and MOVES BELOW the `ModalNavigationDrawer` block — registered later beats NavDisplay's own back handling while enabled, which is exactly the drawer-before-sheet-before-pop order of today:

```kotlin
// Drawer and sheets close before the stack pops — same order the old
// hand-rolled hierarchy had. NavDisplay handles the page pops itself.
BackHandler(enabled = drawerState.isOpen || sheet != Sheet.NONE) {
    when {
        drawerState.isOpen -> scope.launch { drawerState.close() }
        else -> sheet = Sheet.NONE
    }
}
```

- [ ] **Step 3: DrawerContent.kt** — `onOpen: (Page) -> Unit` → `onOpen: (PhoneDestination) -> Unit`; the four call sites `Page.GARAGE`→`Garage`, `Page.SUBSCRIPTIONS`→`Subscriptions`, `Page.NETWORKS`→`Networks`, `Page.CAR_DATA`→`CarData`.

- [ ] **Step 4: Run `:androidApp:assembleDebug`** — expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit** — `feat: page enum retires, navigation3 back stack takes over`

---

### Task 3: Emulator navigation walk

Via `app-laufen-lassen` (emulator already running):

- [ ] Home → drawer → Garage → "Auto hinzufügen" (AddCar) → system back → lands on Garage → back → Home. Screenshot each hop.
- [ ] Home → Planen sheet → pick recent destination ("München") → plan → lands on Trip; tap a stop → StopDetail; back → Trip; back → Home.
- [ ] On Home with drawer open: back closes the drawer, second back leaves the app (check via `adb shell dumpsys activity activities | grep -c ChargeAhead` or the launcher appearing).

No commit — verification only. Any mismatch with the "semantics to preserve" list above is a blocker: fix before proceeding.

---

### Task 4: Section selection moves into TripViewModel

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/ui/TripViewModel.kt`
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/TripPlanScreen.kt:79-103,134,176-279` (remember vars out, params in)
- Modify: `androidApp/src/main/kotlin/de/autoapp/android/phone/MainActivity.kt` (Trip entry call site)
- Test: `shared/src/jvmTest/kotlin/de/autoapp/shared/ui/PhoneViewModelTest.kt`

**Interfaces:**
- Produces in shared/ui: `data class SectionSelection(val selecting: Boolean = false, val a: Int? = null, val b: Int? = null)`; `TripUiState.Planned.selection: SectionSelection`; `TripViewModel.onSectionSelectingToggled()`, `onSectionPointPicked(index: Int)`, `onSectionSent()`.
- TripPlanScreen signature gains `selection: SectionSelection`, `onToggleSelecting: () -> Unit`, `onPickPoint: (Int) -> Unit`, `onSectionSent: () -> Unit`.

- [ ] **Step 1: Failing test in PhoneViewModelTest** (reuse the file's existing fixture for constructing a TripViewModel; adapt names to the fixture on sight):

```kotlin
@Test
fun sectionSelection_cyclesPicksAndResetsOnNewPlan() = runBlocking {
    val viewModel = /* fixture's TripViewModel */
    viewModel.onSectionSelectingToggled()
    viewModel.onSectionPointPicked(0)
    viewModel.onSectionPointPicked(2)
    var planned = viewModel.uiState.first { it is TripUiState.Planned } as TripUiState.Planned
    assertEquals(SectionSelection(selecting = true, a = 0, b = 2), planned.selection)

    viewModel.onSectionPointPicked(1)   // third pick restarts the pair
    planned = viewModel.uiState.first { it is TripUiState.Planned } as TripUiState.Planned
    assertEquals(SectionSelection(selecting = true, a = 1, b = null), planned.selection)

    viewModel.plan(destination)          // a new plan clears the selection
    planned = viewModel.uiState.first { it is TripUiState.Planned } as TripUiState.Planned
    assertEquals(SectionSelection(), planned.selection)
}
```

(Precondition inside the fixture: a plan already exists so `uiState` is `Planned` — same arrangement the existing TripViewModel tests use.)

- [ ] **Step 2: Run it, expect FAIL (unresolved SectionSelection).**

- [ ] **Step 3: Implement in TripViewModel.kt:**

```kotlin
/** Two picked points along start → stops → destination, for sending a section. */
data class SectionSelection(
    val selecting: Boolean = false,
    val a: Int? = null,
    val b: Int? = null,
)
```

- `Planned` gains `val selection: SectionSelection = SectionSelection()`.
- `private val selection = MutableStateFlow(SectionSelection())`; because `combine` tops out at five typed flows, pre-combine the plan-local trio:

```kotlin
private data class PlanInputs(val plan: TripPlan?, val planning: Boolean, val selection: SectionSelection)

val uiState: StateFlow<TripUiState> = combine(
    combine(currentPlan, isPlanning, selection, ::PlanInputs),
    settings.savedRoutes,
    settings.manualSocPercent,
    feature.state,
) { inputs, saved, socPercent, state -> /* as today, plus selection = inputs.selection */ }
    .stateIn(viewModelScope, WhileUiSubscribed, TripUiState.NoPlan)
```

- Events (the screen's `pick()` cycle and both reset sites, moved verbatim):

```kotlin
fun onSectionSelectingToggled() {
    selection.value =
        if (selection.value.selecting) SectionSelection() else SectionSelection(selecting = true)
}

fun onSectionPointPicked(index: Int) {
    val current = selection.value
    selection.value = when {
        current.a == null -> current.copy(a = index)
        current.b == null && index != current.a -> current.copy(b = index)
        else -> current.copy(a = index, b = null)
    }
}

fun onSectionSent() { selection.value = SectionSelection() }
```

- `plan()` sets `selection.value = SectionSelection()` first — preserves the screen's `remember(plan)` reset semantics.

- [ ] **Step 4: TripPlanScreen** — delete the three `remember(plan)` vars; add the four parameters; `selecting`→`selection.selecting`, `selectionA`→`selection.a`, `selectionB`→`selection.b`, `pick(i)`→`onPickPoint(i)`, the toggle button's body→`onToggleSelecting()`, the send button's trailing reset line→`onSectionSent()`. MainActivity's Trip entry passes `trip.selection` and the three `tripViewModel::…` references.

- [ ] **Step 5: Test green; `:shared:jvmTest` + `:shared:compileKotlinIosSimulatorArm64` + `:androidApp:assembleDebug` green.**

- [ ] **Step 6: Commit** — `fix: section selection survives rotation, moves into TripViewModel`

---

### Task 5: PlanSheets.kt splits along its three sheets

**Files:**
- Modify/split: `androidApp/src/main/kotlin/de/autoapp/android/phone/PlanSheets.kt` → `PlanSheet.kt`, `ChargeNowSheet.kt`, `RoutesSheet.kt`

- [ ] **Step 1:** Read PlanSheets.kt top-to-bottom; move `PlanSheetRoute`/`PlanSheetContent` (+their private helpers) to `PlanSheet.kt`, `ChargeNowRoute`/content to `ChargeNowSheet.kt`, `RoutesRoute`/content (+RenameDialog) to `RoutesSheet.kt`. Helpers used by more than one sheet stay in whichever file uses them most and get referenced (same package — no import churn); delete PlanSheets.kt. Pure move, zero logic edits.

- [ ] **Step 2: `:androidApp:assembleDebug` green.**

- [ ] **Step 3: Commit** — `chore: the three sheets stop sharing one 540-line file`

---

### Task 6: Docs + debt bookkeeping

**Files:**
- Modify: `plans/technical-debts.md` (Navigation3 item → checked, with the scoping caveat)
- Modify: `.claude/skills/viewmodels/SKILL.md` (rule 8 "Until Navigation3 lands…" + template's navigation comment)
- Modify: `ARCHITECTURE.md` §8 (scoping paragraph: Navigation3 landed, per-entry scope pending Platform 37)

- [ ] **Step 1: technical-debts.md**

```markdown
- [x] Use Navigation3 library for navigation — done: androidx.navigation3
      back stack in MainActivity (Destinations.kt), Page enum gone,
      StopDetail carries its index. Per-destination ViewModel scope is NOT
      wired yet: lifecycle-viewmodel-navigation3 rides lifecycle 2.11,
      which needs the compileSdk 37 that ARCHITECTURE §9 documents as
      unavailable — when Platform 37 lands it is one artifact plus one
      NavDisplay decorator line.
```

- [ ] **Step 2: SKILL.md rule 8** — "Navigation stays in the UI. ViewModels do not know pages. Which entry is showing is `MainActivity`'s back stack (Navigation3)."

- [ ] **Step 3: ARCHITECTURE.md §8** — the scoping paragraph's last sentence becomes: Navigation3 is in; ViewModels stay activity-scoped until Platform 37 unlocks lifecycle 2.11's per-entry decorator.

- [ ] **Step 4: Commit** — `docs: navigation3 landed, the scoping caveat is written down`

---

### Task 7: Full verification gate

- [ ] `:androidApp:assembleDebug`, `:shared:jvmTest`, `:shared:compileKotlinIosSimulatorArm64` green; `git status` clean; emulator walk from Task 3 re-checked if Tasks 4–5 touched navigation call sites (they do: Trip entry) — one more Trip→StopDetail→back pass.

## Self-Review Notes

- Semantics table covered: drawer-first back (BackHandler below NavDisplay), pop order via real stack, HOME-exits preserved (`NavDisplay` back disabled at size 1, our handler disabled with nothing open).
- Type consistency: `StopDetail(index)` produced in Task 2, consumed in Task 2's entry and Task 4's call-site rewrite; `SectionSelection` field names (`a`, `b`) match test, VM, and screen usage.
- The lifecycle-cap decision is recorded in three places (plan header, technical-debts, ARCHITECTURE) so nobody re-discovers it the hard way.
