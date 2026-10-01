# Code review, 2026-10-01

State of `main` at 3961525 (after PRs #142–#164). Four passes, each by a
separate reviewer: SOLID in `shared`, library-native code in the data layer,
stock components in the phone UI and the car app, test coverage across the
repo. Every claim was checked against the pinned library versions in
`gradle/libs.versions.toml`. Paths are relative to the module source roots.

What is clean, so nobody re-reviews it: use cases depend only on ports and
domain; `Dispatchers.*` appears only in Koin modules; every settings write goes
through an interactor; `TripRepository.update` is internal; the core planner
reimplements nothing a dependency offers; `ChargeAheadMotion`, `LazyFlowRow`,
the marker bitmap cache and `SheetScene` are justified custom code.

## Start here

The ten items with the best ratio of risk removed to effort, in order.

1. Car app host validator ships `ALLOW_ALL_HOSTS_VALIDATOR`
   (`car/ChargeCarAppService.kt:15`). Any app can bind the car service.
   `HostValidator.Builder(context).addAllowedHosts(R.array.hosts_allowlist_sample)`.
2. Persisted schema mirrored by hand: `data/DataStoreTripStorage.kt:68-248` keeps
   ~180 lines of `Stored*` copies of types that are already `@Serializable`
   (`TripPlan`, `ChargeSite`, `Route`, …), and `db/Converters.kt:40-50` does the
   same for `Connector`/`LatLon`. Two encodings of the same type, one of them
   (`SettingsLegacyTripSource`) already decodes `CommittedTrip` directly. Make
   `TripState` `@Serializable` and delete the mirrors.
3. `SiteRepository.storedSitesIn` and `invalidate` have defaults that return
   an empty flow and do nothing (`domain/Ports.kt:71-80`); `LocationSource.currentFix`
   defaults to `null` (`Ports.kt:14`) and iOS inherits it, so "locate" is a no-op
   there. Make them abstract.
4. Base URL and bearer token plumbed by hand into six backend sources
   (`data/Backend*.kt`). Ktor's `DefaultRequest` plugin does it once in
   `createHttpClient`; `BackendConfig` leaves `ChargeStopsModule`.
5. App startup side effects hidden in the location feature:
   `ChargeStopsModule.kt:183-190` runs cache pruning, trip restore and the network
   refresh as `ChargeStopsFeature.onStart`, so every car session repeats them.
   One `StartAppInteractor` from the platform entry point, `onStart` goes.
6. Three hand-maintained ViewModel constructor lists (`ui/SharedUiModule.kt:11`,
   `iosMain/IosEntryPoints.kt:116-122`, `ui/car/CarViewModels.kt:15-25`). A
   constructor change compiles on Android and crashes elsewhere. Share
   `sharedUiModule()` with `parametersOf(feature)`.
7. User-visible strings built in code: durations in `TripSheet.kt:550-554`,
   clock times via `"%02d:%02d".format` at `:563`, `"${step} kW"` in
   `DrawerContent.kt:226`, `oneDecimal()` printing `3.5` where German wants `3,5`
   (`Formatting.kt:5`). Resource format strings and `DateFormat.getTimeFormat`.
8. Modal sheet heights depend on the first frame (`components/AppSheet.kt:34`,
   `ChargeNowSheet.kt`). Fixed on branch `charge-now-sheet-height`: the charge-now
   content has a constant height and starts on the skeleton.
9. No test at all for the car surface (`androidApp/car`, 1322 lines) and none
   for the Room converters or the diagnostics repository, both persisted schemas.
   See the coverage list below for the first tests to write.
10. Business logic in ViewModels: catalog search and ordering in
    `ui/NetworksViewModel.kt:63-82,160-177`, range and preset lookup in
    `ui/GarageViewModel.kt:58-59` and `ui/AddCarViewModel.kt:41-43`, the Maps URL
    builder in `ui/TripViewModel.kt:91-105`. Each is one observer per the
    interactors skill.

## SOLID (shared)

Must fix

- `ChargeStopsModule.kt:183-190`: startup work in the feature's `onStart` (item 5).
- `domain/Ports.kt:71-80`, `:14`: defaulted port members that silently satisfy
  the contract (item 3). Test fakes already lean on them
  (`jvmTest/ChargeNowObserverTest.kt:38-46`).
- Three ViewModel wiring lists (item 6).
- `ui/NetworksViewModel.kt`: filtering and ordering with a 322-line test of
  their own. Extract `SelectableNetworksObserver(Params(query))`; the VM keeps
  `staged`, the debounce and `displayOrder`.

Should fix

- `ui/TripViewModel.kt:91-105` + `ui/CommittedTripViewModel.kt:38`: `TripPlan.mapsUrl`
  and `SectionSelection` are domain logic living in `ui`; move next to `MapsHandoff`.
- `ui/TripViewModel.kt:178-188`: `plan()` launches `UpdateManualSocInteractor` beside
  `PlanTripInteractor`; one interactor with a `storeSoc` flag, as
  `ReplanCommittedTripInteractor` already does.
- `ui/GarageViewModel.kt:58-59`, `ui/AddCarViewModel.kt:41-43`: `ui` imports `core`.
- `ui/HomeViewModel.kt:147-158`: fabricates a `ChargeStop(reachability = UNKNOWN)` to
  reuse the detail dialog; carry a `SelectedSite(site, distanceKm)` instead.
- `settings/SettingsModule.kt:5,25`, `settings/DataStoreDestinationHistory.kt:9,46`:
  `settings` depends on `data.LegacyTripSource`, and the class hides in the
  history file. Interface to `domain`, class to its own file.
- `iosMain/IosEntryPoints.kt:38-52`: mutable global `graph` with three
  `requireNotNull` sites (tracked as #39); one `requireGraph()` until then.
- Public ViewModel entry points not named `on…`: `TripViewModel.plan/clear/commit`,
  `CommittedTripViewModel.replan/endTrip`.

Nice to have

- `ChargeStopsModule.kt:92-153`: `BackendConfig` unpacked six times (item 4 removes it).
- `ChargeStopsModule.kt:94-103`: `MergingSiteRepository` wraps exactly one repository.
- `domain/NetworkCatalog.kt`: 1519 lines, only its test references it.
- `domain/VehicleCatalog.kt`: 380 lines of hand-typed presets; a JSON resource.
- `domain/usecases/ChargeStopsObserver.kt:21`, `domain/Interactor.kt:10`: domain
  imports the top-level logging globals; a `Logger` port.
- `data/TiledSiteRepository.kt`: in-flight de-duplication (54-75), coverage
  bookkeeping (107-136) and entity mapping (166-250) in one class.
- `ChargeStopFormatter.kt:233`: `Place.toDestination()` is a domain mapping in a
  formatter file.

## Library-native code (data layer)

Must fix

- Items 2, 4 and 5 above.
- `ChargeStopsModule.kt:157,166-192` and `car/ChargeSession.kt:24-28`: `getKoin()`
  inside a module and `KoinComponent` + `get()` at the car edge assemble the
  graph by hand. A `factory { (location, hardware) -> ChargeStopsFeature(...) }`
  resolved with `parametersOf`.

Should fix

- `Time.kt` + three actuals + `TimeProvider`: Kotlin 2.4.20 ships `kotlin.time.Clock`;
  bind `Clock.System`. `CarDataDebugScreen.kt:107` and `CarHardwareStatus.kt:74`
  reach around DI to the top-level function.
- `shared/build.gradle.kts:84`: `kotlinx-datetime` 0.8.0 on the classpath, unused.
- Three differently configured persistence `Json` instances
  (`settings/SettingsKeys.kt:38`, `db/Converters.kt:38`, `data/DataStoreTripStorage.kt:64`);
  an unknown enum survives in Room and fails in settings. One `persistenceJson`.
- `settings/SettingsKeys.kt:40-48`, `DataStoreVehicleRepository.kt:75-79,104-118`,
  `DataStorePreferencesRepository.kt:22,43`: everything stored as strings and
  parsed by hand. DataStore has typed keys; one `DataMigration` converts the
  legacy `vehicle.*` keys into one `@Serializable VehicleProfile`.
- `settings/DataStoreCarDiagnosticsRepository.kt:57-72`: enum names resolved via
  `entries.firstOrNull { it.name == … }`; `@Serializable` enums with
  `coerceInputValues` give the same forward compatibility.
- `db/NetworkDao.kt:36-37`, `ChargeSiteDao.kt:44-48`: `@Insert(REPLACE)` as upsert;
  Room 2.8.5 has `@Upsert`, and REPLACE deletes first.
- `data/HttpClientFactory.kt:14-34`: no `HttpRequestRetry`; either install it or
  say in one comment that retries are off on purpose.
- `androidMain/FusedLocationSource.kt:116-125`: hand-rolled `Task.await` with its
  own TODO; `kotlinx-coroutines-play-services`.

Nice to have

- `Combine.kt`: 15 arities, two call sites use 8; trim or group inputs.
- `db/DatabaseFactory.kt`: expect/actual around a one-line builder; bind the
  `RoomDatabase.Builder` per platform module.
- `BackendChargePointStatusSource.kt:42-48`, `BackendDataSourceDirectory.kt:43-48`:
  string-to-enum `when`s forced by the api-model contract typing them as `String`.

## Stock components (phone UI and car)

Must fix

- Item 1 (host validator), item 7 (strings in code), item 8 (sheet heights).

Should fix

- `DrawerContent.kt:206`: hand-rolled segmented control;
  `SingleChoiceSegmentedButtonRow` + `SegmentedButton`.
- `DrawerContent.kt:157`: `AcModeToggle` keeps an optimistic copy reconciled by
  `LaunchedEffect`; drive it from `filters.slowMode` like `SwitchRow`.
- `HomeSearch.kt:126-138`: 40 lines of `onSizeChanged`/`onTextLayout`/`offset` to
  centre the placeholder; `SearchBarDefaults.InputField` does everything but the
  centring animation. Decide whether the animation is worth it.
- `TripSheet.kt:568` vs `TripSheetScaffold.kt:74-75`: peek height from
  `screenHeightDp / 3` while the scaffold measures with `BoxWithConstraints`;
  system bars make them disagree. Use `maxHeight / 3` and pass it down.
- `TripSheetScaffold.kt:158-169`: `visibleSheetHeight` reads the sheet offset in
  the layout phase every frame. Stock shape: fixed-height column, list
  `weight(1f)`, actions pinned.
- `ChargeMap.kt:256`: `TOP_CONTROLS_HEIGHT = 150.dp` guesses the status bar; derive
  from `WindowInsets.statusBars`.
- `PhoneApp.kt:79-80,174-177`: remembered title and a plan picked by inspecting
  the back stack are business decisions in a composable; the VM should expose
  `lastDestinationName` and the selected stop's times.
- `GarageScreen.kt:81`: `deleteMode` in `remember`, lost on rotation.
- Car screens (`CarHomeScreen.kt:40`, `ChargeNowScreen.kt:37`, `RouteScreen.kt:52`,
  `DestinationSearchScreen.kt:31`, `SoCScreen.kt:34`): collect `uiState` from `init`
  for the screen's whole life, so buried screens never unsubscribe;
  `repeatOnLifecycle(STARTED)`.
- `CarHomeScreen.kt:46`: `feature.start()` from a screen; `ChargeSession` owns the
  feature and should start it.

Nice to have

- `components/AppSheet.kt:42-48`: custom handle → `BottomSheetDefaults.DragHandle`;
  the `navigationBarsPadding().padding(bottom = 24.dp)` chain appears five times,
  `sheetListPadding()` already exists.
- `HomeControls.kt`: `HomePill` + `DISABLED_ALPHA` → `ElevatedButton`;
  `RoundIconButton` → `SmallFloatingActionButton`; `HintChip` → `ElevatedAssistChip`.
- `components/Cards.kt:24` → `OutlinedCard`; `components/Rows.kt:113` and the
  tick/switch rows → `ListItem`; `components/Inputs.kt:54-92` → `OutlinedTextField(shape)`.
- `HomeSearch.kt:193`: a `TopAppBar` as a two-line pill → `ListItem`.
- `ChargeMap.kt:212,222`: `rememberMarkerState` is deprecated in maps-compose 8.6.0.
- `ChargeMap.kt:117`: `delay(350)` in a `LaunchedEffect` → `debounce` in the VM.
- `CarHardwareStatus.kt:73`, `CarDataDebugScreen.kt:106`: reimplement
  `DateUtils.getRelativeTimeSpanString`.
- `PhoneNavigator.kt:55-60`: `ScreenStep` + `DisposableEffect` where one
  `BackHandler` does.
- `ChargeNowSheet.kt:60`, `NetworkSettingsScreen.kt:55`: enter/leave effects for
  per-destination VMs that `init`/`onCleared` already cover.

## Test coverage

No coverage tool runs (jacoco is applied in `ui-tests` without a report task),
so this is by reference. CI runs `:shared:jvmTest`, the UI behaviour tests, the
screenshot validation, the iOS compile and a Swift parse. Not run anywhere:
Android lint, any coverage report, iOS tests (none exist); no detekt/ktlint.

| Area | Tested | Biggest gap |
|---|---|---|
| `core/` planners | well (TripPlanner 24, ChargeStopPlanner 18, SiteMerger 19) | `RouteMeasure`/`RouteProgress` never asserted directly |
| `domain/` | mostly | `VehicleCatalog`, `MapChargerQuery`, `ChargeNowArea`, `CorridorPlanning`: no references |
| `domain/usecases` | 10 of 23 own tests | `CommitTrip`, `EndTrip`, `DismissPlannedTrip`, `RefreshNetworks`, `LoadDataSources`, `LiveConnectorsObserver` only via VMs |
| `data/` | good for mappers | `HttpClientFactory`; error paths of status and network sources |
| `db/` | thin | `Converters` round trip, a persisted schema |
| `settings/` | good (22) | `DataStoreCarDiagnosticsRepository` round trip |
| `ui/` phone VMs | partial | `ChargeNowViewModel` (now has two tests on the sheet branch), `CarDataViewModel`, garage arrival-level editing, `HomeViewModel` selection |
| `ui/car` VMs | partial | `CarHomeViewModel`, `CarViewModels`: none |
| `androidApp/car` | none | 1322 lines, not one test |
| phone-ui behaviour | 71 tests | drawer, garage, add car, vehicle settings, charge-now sheet, SoC dialog, debug screen |
| screenshots | 56 | drawer, garage, charge-now, detail sheet, networks, SoC dialog |
| `PhoneAppFlowTest` | 11 flows | drawer navigation, add car, charge-now pill, stop detail from the map, rotation, font scale |
| iOS | none | no XCTest target |

Test quality: `MapChargersObserverTest:121,125` busy-polls, `NetworksViewModelTest:293`
uses a bare `delay(50)`; `site(` is redefined ten times and `fix(` six times across
`jvmTest`, a shared `Fixtures.kt` would remove ~200 lines; `ChargeStopsModuleTest`
only checks that Koin resolves.

The ten tests worth writing first:

1. `db/ConvertersTest`: round trip of connectors, points and strings, including
   empty lists, `count = null`, `UNKNOWN`. A serializer change corrupts the cache.
2. `settings/CarDiagnosticsRepositoryTest`: survives a restart, bounded list,
   corrupt payload reads as empty.
3. `usecases/TripLifecycleInteractorsTest`: commit without a plan, end keeps or
   drops the destination, dismiss leaves the committed trip intact.
4. `domain/MapChargerQueryTest`: the cap, nearest-first, slow-mode power rule, box
   padding and the clamp at ±90.
5. `ui/GarageViewModelTest`: arrival-level edit flow and removing the selected
   vehicle.
6. `ui/car/CarHomeViewModelTest`: pill states per `TripState` and fix.
7. `data/BackendErrorPathsTest`: 500 and timeout on the status and network
   sources; the caching repository keeps stale data on timeout.
8. `commonTest/domain/VehicleCatalogTest`: every preset yields a valid profile,
   `presetFor` is case-insensitive, names unique.
9. `PhoneAppFlowTest` additions: drawer to garage to add car, charge-now pill
   opens the sheet nearest-first, map stop opens the detail sheet, `recreate()`
   keeps search mode and layout.
10. `SheetsPreviews`: charge-now (loaded, skeleton), detail sheet, drawer, SoC
    dialog, garage, each with a dark twin.

Then a `RouteScreenTest` with `androidx.car.app.testing` for the car's
message templates and the constraint-trimmed list.
