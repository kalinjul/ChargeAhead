---
name: viewmodels
description: The MVI-style ViewModel pattern this repository uses for UI state — shared KMP ViewModels in de.autoapp.shared.ui, one uiState StateFlow per screen, stateless Compose screens behind a Route composable. Always use this skill when writing or changing a screen, a ViewModel, a UiState, or anything that holds state for the phone UI; when state currently lives in a composable (remember, mutableStateOf, produceState, LaunchedEffect) and should move; when adding a new screen, sheet or dialog; and when wiring dependencies for a ViewModel. Also when someone asks where state belongs, why a screen loses its input on rotation, or how to test UI state.
---

# ViewModels in this repository

Every phone screen's state lives in a **ViewModel in `shared`**, not in the
composable. The pattern is the one from Google's "Now in Android"
([InterestsViewModel](https://github.com/android/nowinandroid/blob/main/feature/interests/impl/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/interests/impl/InterestsViewModel.kt)):
one `uiState` flow per screen, plain functions for what the user does.

The ViewModels are **KMP-compliant** — `androidx.lifecycle:lifecycle-viewmodel`
is a multiplatform artifact, and `:shared:compileKotlinIosSimulatorArm64`
compiles them for iOS. SwiftUI can bind to the same state holders later;
that is the entire reason they are not in `androidApp`.

## Where things go

```
shared/src/commonMain/kotlin/de/autoapp/shared/ui/<Screen>ViewModel.kt
    <Screen>UiState  + <Screen>ViewModel        no Android, no Compose imports

androidApp/.../phone/<Screen>.kt
    <Screen>Route    fetches the ViewModel, collects, delegates
    <Screen>Screen   stateless: takes uiState + callbacks, holds nothing

shared/src/commonMain/kotlin/de/autoapp/shared/ui/SharedUiModule.kt
    the Koin module that declares every ViewModel (viewModelOf); the
    dependencies come from appModule in androidApp
```

A `commonMain` file that imports anything from `android.*` or
`androidx.compose.*` is in the wrong module. `androidx.lifecycle.ViewModel`
is fine — that one is multiplatform.

## The rules

1. **One ViewModel per screen**, one `uiState` per ViewModel. A sheet or a
   dialog with state of its own counts as a screen.
2. **`uiState` is built with `combine(...) { ... }.stateIn(viewModelScope,
   WhileUiSubscribed, <initial>)`.** Never `MutableStateFlow` as the public
   type, never a `var` the UI writes into. The exception is state that has
   no upstream flow at all (see `ChargeNowViewModel`).
3. **`WhileUiSubscribed`, not `Eagerly` or `Lazily`.** It is defined once in
   `UiStateSharing.kt`; use it, don't spell out `WhileSubscribed(5_000)`.
4. **No user-visible text in a UiState.** `shared` has no resources. A
   reason travels as a type (`TripEvent.NoRoute`), the wording comes from
   `strings.xml`. Same rule as everywhere else in this repository.
5. **Events are methods named `on…`** — `onQueryChanged(query)`,
   `onVehicleRemoved(name)`. They return `Unit`, launch into
   `viewModelScope`, and never hand state back.
6. **One-shot outcomes are a second flow**: `StateFlow<XEvent?>` plus
   `onEventHandled()`. The UI reacts, then consumes. Not a `Channel`, not a
   callback into the composable.
7. **The screen composable is stateless.** It takes `uiState` and lambdas.
   Everything that survives a rotation belongs in the ViewModel; state that
   only lives for a gesture (a slider being dragged, an expanded row) may
   stay in `remember`.
8. **Navigation stays in the UI.** ViewModels do not know destinations.
   Which entry is showing is `MainActivity`'s Navigation3 back stack
   (`Destinations.kt`).
9. **Writes run on `viewModelScope`**, whose dispatcher is the main one on
   both platforms. Don't move store writes to `Dispatchers.Default` — two
   edits would then race and the older one could win. Heavy computation
   inside a flow is a different matter and belongs in `withContext`
   (`NetworksViewModel` does this).
10. **Dependencies are app-scoped objects**: `SettingsStore`,
    `ChargeStopsFeature`, `PlanningFeature`. A ViewModel takes what it
    needs in its constructor and nothing else — no `Context`, no
    `Activity`, no `CoroutineScope`.

## The template

```kotlin
// shared/src/commonMain/kotlin/de/autoapp/shared/ui/ExampleViewModel.kt
package de.autoapp.shared.ui

data class ExampleUiState(
    val items: List<Thing> = emptyList(),
    val query: String = "",
) {
    // Derived, not stored: one source of truth per fact.
    val isEmpty: Boolean get() = items.isEmpty()
}

class ExampleViewModel(
    private val settings: SettingsStore,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<ExampleUiState> = combine(
        query,
        settings.someFlow,
    ) { query, things ->
        ExampleUiState(items = things.filter { it.matches(query) }, query = query)
    }.stateIn(viewModelScope, WhileUiSubscribed, ExampleUiState())

    fun onQueryChanged(query: String) {
        this.query.value = query
    }

    fun onThingRemoved(id: String) {
        viewModelScope.launch { settings.remove(id) }
    }
}
```

```kotlin
// androidApp/.../phone/ExampleScreen.kt
@Composable
fun ExampleRoute(
    onOpenDetail: (String) -> Unit,        // navigation comes from the caller
    modifier: Modifier = Modifier,
    viewModel: ExampleViewModel = phoneViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExampleScreen(
        uiState = uiState,
        onSearchChange = viewModel::onQueryChanged,
        onRemove = viewModel::onThingRemoved,
        onOpenDetail = onOpenDetail,
        modifier = modifier,
    )
}

@Composable
fun ExampleScreen(
    uiState: ExampleUiState,
    onSearchChange: (String) -> Unit,
    onRemove: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) { /* draws uiState, calls the lambdas — no remember of app state */ }
```

Then declare it once:

```kotlin
// SharedUiModule.kt, in sharedUiModule()
viewModelOf(::ExampleViewModel)
```

`phoneViewModel()` resolves it through Koin. Forgetting the declaration
fails at runtime, not at compile time — add it in the same commit.

## Sealed UiState, or a data class?

A data class when the screen always shows the same thing with different
values (`GarageUiState`, `NetworksUiState`). A sealed interface when the
screen genuinely has different shapes and the UI must not be able to read a
field that makes no sense yet — `TripUiState.NoPlan | Planning | Planned`,
`ChargeNowUiState.NoPosition | Loading | Ready`. Don't model "loading" as
`isLoading: Boolean` next to a nullable result if the two can contradict
each other.

## One-shot events

`TripViewModel` is the worked example:

```kotlin
private val events = MutableStateFlow<TripEvent?>(null)
val event: StateFlow<TripEvent?> = events.asStateFlow()
fun onEventHandled() { events.value = null }
```

```kotlin
LaunchedEffect(tripEvent) {
    when (val event = tripEvent) {
        null -> return@LaunchedEffect
        TripEvent.PlanReady -> page = Page.TRIP
        TripEvent.NoRoute -> snackbar.show(scope, getString(R.string.plan_failed_no_route))
        // ...
    }
    tripViewModel.onEventHandled()
}
```

Show the snackbar in a **remembered scope**, not in the effect itself:
consuming the event changes the effect's key, which would cancel it
mid-message.

## Testing

ViewModel tests live in `shared/src/jvmTest/.../ui/` and need no Android and
no Compose — see `PhoneViewModelTest`:

- `Dispatchers.setMain(Dispatchers.Unconfined)` in `@BeforeTest`,
  `Dispatchers.resetMain()` in `@AfterTest`. `viewModelScope` runs on the
  main dispatcher and a plain test JVM has none; `Unconfined` also keeps
  writes in the order they were issued.
- `PersistentSettingsStore(InMemoryKeyValueStorage())` is a real store on
  in-memory storage — no fake needed.
- `uiState` only produces while something collects, so assert with
  `withTimeout(5_000) { viewModel.uiState.first { <condition> } }` instead
  of reading `.value`.

## Before you call it done

```bash
./gradlew :shared:jvmTest
./gradlew :shared:compileKotlinIosSimulatorArm64   # proves the KMP claim
./gradlew :androidApp:assembleDebug
```

Run them through the `gradle-run` wrapper (AGENTS.md, "Build"). The iOS
compile is not optional here: it is the only thing that catches a ViewModel
that quietly stopped being multiplatform.
