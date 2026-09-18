---
name: interactors
description: The domain-layer use-case pattern this repository uses — Interactor (one-shot action) and SubjectInteractor (observer, "ObserveX") from Tivi, in org.julakali.chargeahead.shared.domain. Always use this skill when writing or changing a use case, interactor or observer; when a ViewModel calls ChargeStopsFeature or a repository directly, or runs its own mapLatest/flatMapLatest pipeline over settings; when business logic (filtering, ranking, selecting) sits in a ViewModel or a feature class; and when wiring an interactor into Koin or a ViewModel's combine(). Also when someone asks where business logic belongs or how to test it without a ViewModel.
---

# Interactors and observers

Business logic the phone UI needs lives in **use cases in `shared` →
`domain`**, built on the two base classes in
`shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/domain/Interactor.kt`
(taken from [Tivi](https://github.com/chrisbanes/tivi/blob/main/domain/src/commonMain/kotlin/app/tivi/domain/Interactor.kt)).
The ViewModel wires them together; it does not select, filter or fetch
itself.

The worked example is `ObserveMapChargers`, used by `HomeViewModel`.

## Which base class

| You need…                                     | Base class             | Name          | Example                |
|-----------------------------------------------|------------------------|---------------|------------------------|
| a stream the screen shows, driven by input    | `SubjectInteractor<P, T>` | `ObserveX` | `ObserveMapChargers`   |
| an action that runs once and has an outcome   | `Interactor<P, R>`     | verb phrase   | `PlanTrip`             |

## The rules

1. **Domain only.** A use case lives in `org.julakali.chargeahead.shared.domain`
   and depends on ports (`SiteRepository`, `SettingsStore`,
   `ChargePointStatusSource`, …) and domain types. Never on
   `ChargeStopsFeature`, `core`, `data` or `ui`. Constants and models it
   needs move into `domain` (as `MIN_DC_POWER_KW` and `MapCharger` did);
   an algorithm too big to move is reached through a port (`TripPlanning`,
   implemented by `core.TripPlanner`).
2. **Params carry only what the UI knows.** The viewport, a search query, a
   position. Everything that comes from settings or a repository the use case
   reads itself — for an observer, as a flow, so a settings change re-runs it
   without the ViewModel noticing.
3. **An observer's params are a `data class Params`** nested in the observer,
   even for one field; `null` inside it is a legitimate input
   (`Params(viewport = null)` → empty result), because `SubjectInteractor`
   requires `P : Any`.
4. **Observers read from a store flow, never emit by hand.** An observer
   maps a repository flow (`SiteRepository.storedSitesIn`, a Room query) and
   starts a network refill alongside it (`merge(stored.map { … }, flow {
   refill() })`); the refill writes to the store and the store's flow
   re-emits. `SubjectInteractor.flow` is `distinctUntilChanged`, so identical
   results are swallowed — don't rely on an emission per trigger.
5. **If the UI must know which input a result belongs to, return it with the
   result** (`MapChargers(filter, chargers)`), instead of tracking flags in
   the ViewModel.
6. **Heavy computation runs in `withContext(Dispatchers.Default)`** inside the
   use case. Repository calls stay on the caller's dispatcher — they are
   main-safe.
7. **Failures of a refill are swallowed with `cancellableRunCatching`**, never
   plain `runCatching`: that one would eat the cancellation `flatMapLatest`
   relies on. An `Interactor` returns `Result<R>`; the ViewModel decides what
   a failure looks like.
8. **Load times are measured by the base class.** `SubjectInteractor` logs
   (`logDebug`) how long each new param took from being picked up to its
   first result; override `onFirstResult` only to report it elsewhere.
9. **Koin: `factory`, not `single`.** A `SubjectInteractor` keeps its params
   per instance; two ViewModels sharing one would steer each other. Declare
   it in `chargeStopsModule()` next to its ports.
10. **Observers are used only in ViewModels — and in car screens.** The
    Swift bridge or a feature class never calls a `SubjectInteractor` or
    collects its `flow`; it goes through the shared ViewModel for that
    screen and collects its `uiState`, created with `ViewModelHost` and
    `clear()`ed when it ends (see `PlanningBridge`). Car `Screen`s are the
    exception: they call the observer and collect its `flow` in
    `lifecycleScope` themselves (see `ChargeNowScreen`).

## In the ViewModel

```kotlin
class HomeViewModel(
    private val observeMapChargers: ObserveMapChargers,
    settings: SettingsStore,
    …
) : ViewModel() {

    val uiState = combine(
        observeMapChargers.flow,          // the observer is a combine() parameter
        settings.chargeFilters,
        …
    ) { mapChargers, filters, … -> HomeUiState(chargers = mapChargers.chargers, …) }
        .stateIn(viewModelScope, WhileUiSubscribed, HomeUiState())

    init {
        // Without initial params the flow never emits, and combine() with it.
        observeMapChargers(ObserveMapChargers.Params(viewport = null))
    }

    fun onViewportChanged(viewport: BoundingBox?) {
        observeMapChargers(ObserveMapChargers.Params(viewport))
    }
}
```

An `Interactor` is called from an `on…` event (see `TripViewModel`):

```kotlin
fun onPlanRequested(destination: Destination) {
    viewModelScope.launch {
        planTrip(PlanTrip.Params(from, destination))
            .onSuccess { … }
            .onFailure { events.value = TripEvent.Failed }
    }
}
```

`inProgress` on an `Interactor` is the loading flag — don't keep a separate
`isLoading` next to it.

## Testing

Use-case tests live in `shared/src/jvmTest/.../domain/` and need no
ViewModel — see `ObserveMapChargersTest`:

- Fake the ports with an `object : SiteRepository { … }` whose
  `storedSitesIn` returns a `MutableStateFlow` the fake fetch updates; use
  `PersistentSettingsStore(InMemoryPreferencesDataStore())` as the real store.
- Call the observer with its params, then
  `withTimeout(5_000) { observer.flow.first { <condition> } }`.
- To see the post-refill result, make the fake's fetch change what it
  stores — otherwise `distinctUntilChanged` hides it and the wait times out.

ViewModel tests construct the real use case over the same fakes, not a mock.

## Before you call it done

Same checks as for ViewModels, through the `gradle-run` wrapper (AGENTS.md,
"Build"): `:shared:jvmTest`, `:shared:compileKotlinIosSimulatorArm64`,
`:androidApp:assembleDebug`.
