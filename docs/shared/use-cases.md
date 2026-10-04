# Use cases

Business logic lives in `org.julakali.chargeahead.shared.domain.usecases`,
as `Interactor` (a one-shot action) or `SubjectInteractor` (an observer,
`XObserver`). They are Koin factories in `chargeStopsModule()`; ViewModels
only wire them up, and Swift goes through the bridges in `iosMain`. An
observer reads from a repository; a `Refresh…Interactor` only refills that
repository. The pattern and its rules are in the `interactors` skill.

```kotlin
// Charging sites
class ChargeStopsObserver : SubjectInteractor<Params, ChargeStops?>      // the corridor list: iOS only, being phased out (#152, #153)
class RefreshChargeStopsInteractor : Interactor<Params, Unit>
class MapChargersObserver : SubjectInteractor<Params, List<MapCharger>>  // phone map, with live availability
class RefreshMapChargersInteractor : Interactor<Params, Unit>
class RefreshChargerAvailabilityInteractor : Interactor<Params, Unit>
class ChargeNowObserver : SubjectInteractor<Params, ChargeNowResult?>    // best 3, nearest first; relax ladder: power → networks → distance
class RefreshChargeNowInteractor : Interactor<Params, Unit>

// Trips
class DestinationSearchObserver : SubjectInteractor<Params, DestinationSearch>
class PlanTripInteractor : Interactor<Params, TripPlanResult>               // sets destination and planned trip
class ReplanWithArrivalSocInteractor : Interactor<Params, TripPlanResult?>  // stores the level, re-plans the planned trip
class CommitTripInteractor : Interactor<Params, CommittedTrip>             // "An Maps senden" makes the plan the active route
class ReplanCommittedTripInteractor : Interactor<Params, TripPlanResult?>  // plans the active route anew and commits it
class DismissPlannedTripInteractor : Interactor<Unit, Unit>
class EndTripInteractor : Interactor<Unit, Unit>

// Garage and catalog
class GarageObserver : SubjectInteractor<Params, Garage>
class VehiclePresetsObserver : SubjectInteractor<Params, List<VehiclePreset>>  // catalog minus the garage, filtered by name
class SelectVehicleInteractor : Interactor<Params, Unit>
class RemoveVehicleInteractor : Interactor<Params, Unit>
class RefreshVehicleCatalogInteractor : Interactor<Unit, Unit>    // also brings uncustomized catalog cars up to date
class RestoreCatalogValuesInteractor : Interactor<Unit, Unit>     // drops the driver's changes to the selected catalog car

// Start-up
class StartAppInteractor : Interactor<Unit, Unit>                 // prune the site cache, restore the trip, refresh networks and catalog
class RefreshNetworksInteractor : Interactor<Unit, Unit>
```

The list is an overview, not complete; the settings writes
(`Update…Interactor`) and `SetChargeModeInteractor` sit in the same package.
