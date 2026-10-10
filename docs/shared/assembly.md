# State, assembly and the iOS bridge

What `org.julakali.chargeahead.shared` puts together from the domain, and how
Swift reaches it.

```kotlin
// Location and charge state, one per location source (phone, car session).
class ChargeStopsFeature(locationSource, socSource, ...) {
    val currentFix: StateFlow<Fix?>
    val currentEnergy: StateFlow<EnergyState?>
    val locationFailed: StateFlow<Boolean>
    fun start(); fun locate(); fun close()
}

// The corridor list for iOS: ChargeStopsObserver over the feature's flows,
// turned into this state by CorridorViewModel. Flat instead of sealed, so it
// crosses to Swift losslessly. Being phased out: CarPlay follows Android
// Auto's screens (#152), the iOS map moves to MapChargersObserver (#153).
data class ChargeStopsState(
    val stops: List<ChargeStop>,
    val phase: Phase,              // WAITING_FOR_LOCATION | LOADING | READY | FAILED
    val failure: FailureReason?,   // LOCATION_UNAVAILABLE | SITES_UNAVAILABLE
)

// The data graph (Koin), declared once for both platforms. Platform modules
// supply LocationSource, DatabaseFactory, settingsModule, TripStorage and
// BackendConfig; one HttpClient, database and repository per process, shared
// by phone and car.
fun chargeStopsModule(): Module

// Koin singles in chargeStopsModule: inject these instead of calling
// CoroutineScope(...) or Dispatchers.* anywhere else. AppScope is cancelled
// when the graph closes; a class with its own lifecycle takes childScope() of it.
data class AppCoroutineDispatchers(io, computation, main)
val AppScope: Qualifier   // get<CoroutineScope>(AppScope)
data class BackendConfig(baseUrl: String, token: String)

// A feature the caller owns and closes: the car session's, with the car's battery.
fun Koin.newChargeStopsFeature(locationSource, hardwareSoCSource = null): ChargeStopsFeature

// So Android and iOS show the same lines.
object ChargeStopFormatter {
    fun primaryLine(stop: ChargeStop): String    // "12 km · CCS 150 kW"
    fun secondaryLine(stop: ChargeStop): String  // "Ankunft ca. 34 %" | "6 Ladepunkte"
}

expect fun platformName(): String
expect fun currentTimeMillis(): Long
```

## The iOS bridge

The iOS framework is called **`Shared`** (`import Shared`) and is built with
SKIE: a `StateFlow` arrives in Swift as an `AsyncSequence`, `suspend` as
`async`, Kotlin enums and sealed types as Swift enums. The app delegate builds
the graph once at launch with `IosEntryPointsKt.startChargeAhead(backendBaseUrl:backendToken:)`;
after that Swift creates a feature through `IosEntryPointsKt.createChargeStopsFeature()`
and the phone screens' ViewModels through `PhoneViewModels` (both in
`iosMain`). A SwiftUI view holds them in a `ViewModelOwner` (`@StateObject`,
clears them on `deinit`) and reads `uiState` with SKIE's `Observing`. CarPlay
still goes through `ChargeStopsWatcher` until #152.
