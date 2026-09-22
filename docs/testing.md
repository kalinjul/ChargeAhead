# Testing

How the project is tested, what each layer is for, and the commands. The
analysis that led here is at the bottom.

## Layers

| Layer | Where | Runs on | Answers |
|---|---|---|---|
| Unit tests | `shared/src/commonTest`, `shared/src/jvmTest` | JVM | Does the planning, formatting and ViewModel logic do the right thing? |
| Behaviour tests | `ui-tests/src/test` | JVM (Robolectric) | Do the phone screens show the right state and fire the right callbacks? |
| Shell flow tests | `ui-tests/src/test/.../shell` | JVM (Robolectric) | Does the whole phone shell hold together: search → plan → sheet, X, back order, layout toggle, replan? |
| Screenshot tests | `ui-tests/src/screenshotTest` | JVM (Layoutlib) | Do the components still look like the approved picture? |
| Device | by hand | phone / DHU | Map, live data, Android Auto, feel |

Nothing in the test modules talks to a backend, the geocoder, Google Maps
or the network. Composables get fixed data from `Fixtures.kt`; the clock is
pinned through `LocalNow` so trip times don't drift.

## Modules

- `:phone-ui` — every phone composable and its resources. Library, so tests
  can depend on it.
- `:ui-tests` — depends on `:phone-ui`, holds both UI layers. Nothing here
  ships in the app.
- `:androidApp` — the shell (MainActivity), Android Auto, Koin wiring. Has
  no tests of its own.

## Commands

```bash
./gradlew testAll                                  # all of the below, minus recording

./gradlew :shared:jvmTest                          # unit tests
./gradlew :ui-tests:testDebugUnitTest              # behaviour tests
./gradlew :ui-tests:validateDebugScreenshotTest    # screenshots vs. goldens
./gradlew :ui-tests:updateDebugScreenshotTest      # re-record goldens
```

Goldens live in `ui-tests/src/screenshotTestDebug/reference/`, one PNG per
`@PreviewTest` function. The validate task writes an HTML report with
reference, actual and diff to
`ui-tests/build/reports/screenshotTest/preview/debug/index.html`.

Tolerance is 1 % (`imageDifferenceThreshold` in `ui-tests/build.gradle.kts`):
enough for glyph anti-aliasing differences between JDKs and operating
systems, far below a missing line or a shifted chip. CI validates on Linux;
when a golden changes on purpose, record it locally, look at the diff, commit
the PNG.

## Writing tests

- Behaviour: `@RunWith(RobolectricTestRunner::class)`,
  `createAndroidComposeRule<ComponentActivity>()`, content wrapped in
  `ChargeAheadTheme`, strings via `compose.activity.getString(R.string.x)`
  so the German copy is matched, not retyped. Match by text or content
  description; a `testTag` is the last resort.
- Screenshot: a public top-level `@PreviewTest @Preview(showBackground = true)`
  composable in `ui-tests/src/screenshotTest`, wrapped in `ChargeAheadTheme`,
  fed from `Fixtures.kt`. Trip rows need
  `CompositionLocalProvider(LocalNow provides { LocalTime.of(14, 30) })`.
  Renaming a preview function orphans its golden; re-record.
- Robolectric runs SDK 35 (`ui-tests/src/test/resources/robolectric.properties`)
  because it doesn't emulate 37 yet, and the test JVM opens a few `java.base`
  packages that Robolectric reflects into (`ui-tests/build.gradle.kts`).

## Shell flow tests

`ShellFlowTest` composes the real `PhoneApp` on the real Koin graph
(`chargeStopsModule`, `sharedUiModule`) with every world-facing port replaced
in `PhoneAppHarness`: in-memory settings with a test vehicle, one fixed
location in Hamburg, a geocoder that knows München, a straight-line route
engine, three charge sites along that line, empty live status and network
lists. Nothing reaches a server. Room runs for real on the bundled SQLite
driver.

Hand-offs to Google Maps are asserted on the `Intent` Robolectric records:
the whole trip, a picked section (first stop as waypoint, last as
destination) and a stop's "Navigation starten" `geo:` URI.

One wart: the Maps SDK's `CameraUpdateFactory` is only initialised by a
rendering map, which Robolectric has not. The harness installs a no-op
delegate through the SDK's obfuscated `CameraUpdateFactory.zza`. If a Maps
SDK update renames that, the shell tests fail at start-up with
"CameraUpdateFactory is not initialized" — fix the harness, not the app.

## Not covered, on purpose

- The Google Map and its markers on the map. Markers are tested as the
  bitmaps they're rendered from (`ChargerPill`, `ChargerDot`).
- Animations. Tests assert resting states before and after.
- End-to-end flows against the real backend, and Android Auto. Both stay on
  the device.

## Migration note

The screenshot plugin `com.android.compose.screenshot` is deprecated in
favour of AGP test suites, which arrive with AGP 9.5. Once 9.5 is stable:
drop the plugin, add `testOptions.screenshotTests.create("screenshotTest")`
with the engine version, keep the source set, goldens and tests as they are.

## Analysis (2026-09-22)

- DI: Koin (shared `sharedUiModule`, app `appModule`). Fine for tests, the
  UI layers don't need it: composables are stateless behind `*Route` wrappers.
- Unit framework: kotlin-test, `runBlocking`; coroutine ViewModel tests in
  `jvmTest` with `Dispatchers.setMain(Unconfined)`. 542 tests.
- Mocking: none, hand-written fakes. Kept that way.
- UI: 100 % Compose on the phone, Car App Library templates in the car.
- Before this setup: no behaviour tests, no screenshot tests, one `@Preview`.
  The regressions of the September UI rewrite (rail line missing, chip on the
  pill border, tiles laid out off-screen) were all visual or interaction
  bugs no unit test could see.
- Added: `:phone-ui` extraction, `:ui-tests` with Robolectric + Compose test
  APIs (JUnit4) and Compose Preview Screenshot Testing, jacoco on the test
  module, CI steps for both.
- Not added: Dropshots, UI Automator, Mockk — nothing asked for them.
