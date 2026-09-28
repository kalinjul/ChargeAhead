# Committed Trip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** "An Maps senden" commits the planned trip. The committed trip survives a restart as it was sent, has its own full-screen view behind an "Aktive Route" button, drives the car's home screen, and replaces saved routes (Favoriten) entirely.

**Architecture:** The committed trip is the sent `TripPlan` plus the start charge, persisted as JSON in `SettingsStore`. It is a separate thing from the plan being looked at: `TripStore` keeps holding the transient plan, the trip sheet and the destination header stay as they are, and committing clears them. A new `CommittedTripViewModel` owns the committed trip's view (list with section selection, send again, re-plan, end). Everything that today reads `savedRoutes` reads the committed trip instead, everything that writes saved routes is deleted.

**Tech Stack:** KMP `shared` (Koin, kotlinx.serialization, kotlin-test), `phone-ui` Compose (Material 3, Navigation 3 pages), `androidApp` car (Car App Library), `ui-tests` (Robolectric + screenshot goldens, `./gradlew testAll`).

**Spec:** `docs/2026-09-22-committed-trip-roadmap.md`, "What the app is for" and "The loop" steps 1 and 6, refined in conversation on 2026-09-28 (decisions below). Progress, reachability, re-plan prompts and the background host are later plans.

## Global Constraints

- One committed trip per device. Sending another plan replaces it without asking.
- Persist the plan as sent. Re-planning happens only on request ("Neu planen" in the active route view).
- No saved-route feature in disguise: no history, no names. Recents in the search cover that.
- German UI strings, English identifiers. `./gradlew testAll` green after every task. No attribution trailers.

---

## Decisions (from the interview, so no task re-decides)

| Question | Decision |
|---|---|
| How does the driver commit? | "An Maps senden" in the trip sheet commits the whole plan (also when a section is sent), hands off to Maps, clears the transient plan, and opens the active route view. |
| Home screen after sending / on return | The active route view is on top. Back from it lands on the browsing home screen (search bar, no trip sheet). |
| Restart | The stored plan shows exactly as sent; "Aktive Route" is enabled. Nothing is re-planned until asked. |
| Planning while a route is committed | Free. The trip sheet works as today; sending replaces the committed route. |
| Bottom pill | "Aktive Route" replaces "Favoriten". Disabled (dimmed, not clickable) when nothing is committed; otherwise it pushes the active route page from the right. |
| Active route view | A full page (`PhonePages`, slides in from the right) with an explicit back arrow in the app bar. Content: centred header (destination name, "776 km · 8h 54m · 3 Stopps"), the trip's stop list with section selection like the trip sheet, tap a stop for its detail sheet, "An Maps senden", "Neu planen" (plans the same destination from here and replaces the stored plan), and "Navigieren beenden" (clears the committed trip from the store and pops the page). No charge-level editing on this page. |
| Car | The favourites rows become one "Aktive Route" row pushing `RouteScreen` with the stored plan; gone when nothing is committed. |
| Storage | `trip.committed` in `PersistentSettingsStore`; the domain types of a `TripPlan` become `@Serializable`. `routes.saved` leaves `ALL_KEYS`. |

---

### Task 1: Domain type, port and persistence

**Files:** `shared/.../domain/CommittedTrip.kt` (new), `domain/Model.kt`, `Route.kt`, `TripPlan.kt`, `Destination.kt` (`@Serializable`), `domain/Ports.kt`, `settings/PersistentSettingsStore.kt`; test `shared/src/jvmTest/.../settings/CommittedTripSettingsTest.kt`.

```kotlin
@Serializable
data class CommittedTrip(val plan: TripPlan, val startSocPercent: Double?, val committedAtEpochMillis: Long)
// SettingsStore
val committedTrip: Flow<CommittedTrip?>
suspend fun commitTrip(trip: CommittedTrip)
suspend fun clearCommittedTrip()
```

- [x] Failing test: commit a trip built from the fixtures, read it back from a second store over the same datastore, clear it, read null.
- [x] Implement; `./gradlew :shared:jvmTest` green.
- [x] Commit: `feat: settings remember the committed trip, plan and all`.

### Task 2: Interactors and view models

**Files:** `usecases/CommitTripInteractor.kt`, `usecases/EndTripInteractor.kt` (new), `ChargeStopsModule.kt`, `ui/TripViewModel.kt`, `ui/CommittedTripViewModel.kt` (new), `ui/SharedUiModule.kt`; tests `ui/CommittedTripViewModelTest.kt`.

```kotlin
class CommitTripInteractor(settings, clock: () -> Long) : Interactor<CommitTripInteractor.Params, Unit>   // Params(plan, startSocPercent)
class EndTripInteractor(settings) : Interactor<Unit, Unit>

// TripViewModel: toggleSaved/isSaved/RouteSaved/RouteRemoved go; new
fun commit()                     // commits the current plan; event TripCommitted
data object TripCommitted : TripEvent

// CommittedTripViewModel
data class CommittedTripUiState(val trip: CommittedTrip?, val selection: SectionSelection, val planning: Boolean, val startPosition: LatLon?) {
    val mapsUrl: String? get() = trip?.plan?.mapsUrl(startPosition, selection)
}
sealed interface CommittedTripEvent { Replanned; NoRoute; Ended }
fun onSectionSelectingToggled(); fun onSectionPointPicked(index); fun onSectionSent()
fun replan()      // PlanTripInteractor from the current fix, then commit the result; TripStore is cleared afterwards so the home stays browsing
fun endTrip()
```

- [x] Failing tests: replan stores a new plan; endTrip clears; mapsUrl follows the selection.
- [x] Implement, register in Koin, `./gradlew :shared:jvmTest` green.
- [x] Commit: `feat: committing, re-planning and ending a trip in shared`.

### Task 3: Phone — sending commits, the active route page, the pill

**Files:** `Destinations.kt` (`ActiveRoute`), `PhonePages.kt` (entry), `ActiveRouteScreen.kt` (new: `ActiveRouteRoute` + `ActiveRouteScreen`), `TripSheet.kt` (drop `isSaved`/`onToggleSave` and the heart; `socEditable` flag for the rail), `TripSheetScaffold.kt`, `HomeScreen.kt` / `HomeChrome.kt` (`HomePill(enabled)`, pill "Aktive Route" with `ic_route`), `PhoneApp.kt` (commit on send, open the page on `TripCommitted`, `PhoneAppSheet.ROUTES` gone), `strings.xml`.

- [x] Failing tests: `ActiveRouteScreenTest` (header, stops, buttons fire), `HomeScreenTest` (pill disabled/enabled), `PhoneAppFlowTest` (`an maps senden commits: the page opens, back lands on browsing, aktive route reopens it, navigieren beenden disables the pill`).
- [x] Implement. Screenshots: `ActiveRoute` previews, pill previews.
- [x] `./gradlew testAll` green, PNGs re-recorded and eyeballed.
- [x] Commit: `feat: an maps senden commits the trip; aktive route shows it on its own page`.

### Task 4: Car — the committed trip on the home screen

**Files:** `car/CarHomeScreen.kt`, `car/RouteScreen.kt` (optional stored plan, shown without planning), `strings.xml` (`car_home_active_route`).

- [x] Implement; `./gradlew :androidApp:assembleDebug`. DHU check is Raphael's (still open).
- [x] Commit: `feat: the car home offers the active route instead of favourites`.

### Task 5: Remove saved routes for good

**Files:** delete `SavedRoute.kt`, the four saved-route interactors, `RoutesViewModel.kt`, `RoutesSheet.kt`, `ic_heart*.xml`; strip `Ports.kt`, `PersistentSettingsStore.kt`, `ChargeStopsModule.kt`, `SharedUiModule.kt`, strings, tests (`GarageAndRoutesSettingsTest` → `GarageSettingsTest`, module tests), `ROADMAP.md`, `docs/2026-09-07-phone-ui-design.md` mentions.

- [x] `grep -rn "SavedRoute\|savedRoute\|Favorit\|favorit\|ic_heart"` over sources and docs is empty except the roadmap's history note.
- [x] `./gradlew testAll` green. Commit: `chore: saved routes are gone; recents and the active route cover it`.

### Task 6: Docs and PR

- [x] Roadmap "Order of work": items 2 and 5 done; item 1 (decision logic) next.
- [ ] PR: `to verify:` plan a trip, An Maps senden, come back: Aktive Route page; back, kill the app, reopen: pill enabled, page shows the same stops; Navigieren beenden: pill disabled; Android Auto: "Aktive Route" row.
