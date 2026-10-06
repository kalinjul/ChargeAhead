# AGENTS.md — Working rules for this repository

Applies to all agents and humans writing code here. Architecture and
rationale live in [ARCHITECTURE.md](ARCHITECTURE.md), milestones and open
points in [ROADMAP.md](ROADMAP.md) — this document only says *how* work
happens here.

## What the project is

Charging-stop assistant for **Android Auto** and **Apple CarPlay**. Shows the
next reachable charging stations ahead while driving. Shared logic in Kotlin
Multiplatform, car UI native twice.

The app determines the location, searches ahead (a sector in the direction
of travel, or the strip along a planned route), queries the ChargeAhead
backend, and classifies the results against the remaining range. The driver
picks the car from the backend's catalog or types it in, and enters the
charge level unless the car reports it. Without a car or a charge level,
reachability stays `UNKNOWN` and the list shows only distances; that's a
valid state, not an error.

## Layout

```
shared/      Kotlin Multiplatform — domain, data layer, use cases, ViewModels
phone-ui/    Compose phone UI as an Android library (+ its resources)
androidApp/  Android app: CarAppService (Android Auto) + MainActivity hosting the phone UI
ui-tests/    Phone UI tests: Robolectric behaviour tests + screenshot goldens (docs/testing.md)
iosApp/      Swift: CarPlay scene + SwiftUI phone UI
tools/       Helper scripts (Swift syntax check, screenshot goldens)
docs/        Code documentation (docs/shared/), testing, CI
```

Dependency direction: `androidApp` → `phone-ui` → `shared`, `ui-tests` → `phone-ui`,
`iosApp` → `shared`. Never the reverse.
`shared` knows neither Android nor iOS frameworks in `commonMain`.

## Language

- **Identifiers, types, filenames: English.** `ChargeSite`, not `Ladesaeule`.
- **Mobile vocabulary, not web or desktop jargon.** No "chrome" for the UI
  around the content, in code or in prose. Name the actual thing: search bar,
  controls, app shell, top bar.
- **Comments and documentation: English.** Write a comment only when it's
  truly needed — not a blind comment on every function. A comment explains
  *why*, not *what*: a hidden constraint, a subtle invariant, a workaround, a
  reason that would surprise a reader. No comment that just retells the line
  below it. Classes and objects in particular don't get a doc comment by
  default — a self-explanatory type stays uncommented, even if that means no
  comment at all. Don't explain the domain ("OCM omits the unit count for
  about half the sites") or restate what a property's name and type already
  say; do note the one field or branch that's a genuine special case (e.g.
  `Fix.bearingDeg` is `null` while stationary, and is never guessed).

  **Keep them short, and leave these out** — the codebase was combed through
  once to remove them, so don't write them back in:

  - **Length.** One or two sentences. A doc comment that needs several
    paragraphs is describing a design, and a design belongs in an issue.
  - **No rejected alternatives.** What was considered and not done, and why
    some other library or approach would be worse, is not the reader's
    problem. `// Deliberately not DataStore: that would add a dependency for
    a handful of strings` → drop it.
  - **No document cross-references.** Not `ARCHITECTURE.md`, `ROADMAP`,
    `AGENTS.md`, `docs/mockup`, and no milestone numbers (M1, M5, "open item
    8"). They rot as soon as those files move. If a rule matters at the call
    site, state the rule.
  - **No evidence or anecdotes.** No measurement dumps, counts, or the
    charging park where it was first noticed: `// 294 pairs out of 98 entries
    sat within 25 m of each other, "mblty Denkendorf" seven times at the same
    coordinate` → `// Several rows can describe the same site`.
  - **No bug history.** "used to", "before the fix", "that's what issue #36
    reported" — the reason the code is the way it is, not the story of how it
    got there. Git and the issue keep the story.
  - **No praise or dramatics.** Not "and that's the whole point", "the core of
    the design", "deliberately", "would be dangerous".

  Pending work is a `TODO`, one line, with the issue where one exists:
  `// TODO use Room type converters instead (#93)`. If you catch yourself
  justifying an architecture decision in a comment, that's the signal to ask
  whether the decision should change and open an issue — not to write the
  justification down.

  Test comments follow the same rules: state the rule the test pins down
  ("Unrecognised sites are hidden when a filter is active"), not the
  regression narrative. An issue number as the KDoc of a test is fine.
- **User-visible text: German**, and exclusively from resources
  (`strings.xml`, `Localizable.strings`) — never as a literal in code. This
  is a deliberate product decision for the German market; it does not extend
  to code, comments, or documentation.
- **No secrets in the code or in the repository** — no API keys, tokens, or
  credentials, not even as an example value that looks real. See "API keys"
  below for where they actually go.

## Build

Every agent-initiated Gradle command goes through the `gradle-run` wrapper
from the `chrisbanes-skills` plugin (see "Agent skills"). It returns a
bounded JSON summary instead of the full build log, and keeps the log on
disk rather than in the conversation. `python3` is a prerequisite.

Resolve `<skill-dir>` once per session and reuse it. The plugin cache path
carries the plugin version, so don't memorise it — it changes on every
upstream release:

```bash
ls -d ~/.claude/plugins/cache/chrisbanes-skills/chrisbanes-skills/*/skills/gradle-run | tail -1
```

Open one workflow, keep its ID, and close it when the last validation
passed:

```bash
python3 <skill-dir>/scripts/gradle_run.py create
python3 <skill-dir>/scripts/gradle_run.py finish --workflow <id>
```

`create`, each `run`, and `finish` are each the *entire* shell command for
that tool call — no pipes, no `&&`, no variable assignment around them.

The tasks that matter here, each as one `run`:

```bash
python3 <skill-dir>/scripts/gradle_run.py run --workflow <id> --scope targeted \
  --question "Does the Android app still build?" -- \
  ./gradlew :androidApp:assembleDebug

python3 <skill-dir>/scripts/gradle_run.py run --workflow <id> --scope targeted \
  --question "Do the shared tests pass?" -- \
  ./gradlew :shared:jvmTest

python3 <skill-dir>/scripts/gradle_run.py run --workflow <id> --scope targeted \
  --question "Does iosMain still compile?" -- \
  ./gradlew :shared:compileKotlinIosSimulatorArm64

python3 <skill-dir>/scripts/gradle_run.py run --workflow <id> --scope targeted \
  --question "Does the app install on the device?" -- \
  ./gradlew :androidApp:installDebug
```

`:shared:compileKotlinIosSimulatorArm64` works on Linux. `--question` must
be a real verification question; quote it and its answer when reporting.
The wrapper adds `--console=plain --no-scan` itself.

The Swift check stays outside the wrapper, because it isn't Gradle:

```bash
tools/check-swift.sh                    # Swift syntax check (see below)
```

A human at a terminal can of course run the bare `./gradlew` task — it's the
part after `--`. The wrapper is the rule for agents.

Gradle needs `local.properties` with `sdk.dir` (plus the API keys from "API
keys"). It's git-ignored, so a fresh clone **and every new worktree** needs
its own; without it `:androidApp:assembleDebug` fails with `SDK location not
found` before compiling anything.

In a worktree, `tools/link-local-properties.sh` takes care of that: it
replaces a keyless `local.properties` with a symlink to the main checkout's.
A `SessionStart` hook in `.claude/settings.json` runs it, so nobody has to
think about it. A worktree without the keys is the more insidious case
anyway — it builds, but the app then stops at startup for lack of a backend
and shows a "no maps key" notice instead of a map. The script leaves a `local.properties`
that carries entries of its own untouched.

Tests that need to run coroutines live in `shared/src/jvmTest` and use
`runBlocking` — it doesn't exist in `commonTest`. `kotlinx-coroutines-test`
is on the `jvmTest` classpath for one reason only: `viewModelScope` runs on
the main dispatcher and a plain test JVM has none, so ViewModel tests call
`Dispatchers.setMain(Dispatchers.Unconfined)`. Everything else stays on
`runBlocking`.

Versions live exclusively in `gradle/libs.versions.toml`. **Never write a
version number directly into a `build.gradle.kts`.** Anyone who needs a new
dependency adds it to the catalog.

The `build.gradle.kts` files, `settings.gradle.kts`, and the version catalog
are maintained centrally. Agents contributing source code don't change them
without being asked — if a dependency is missing, report it instead of
adding it yourself.

## Agent skills

Two kinds of skills apply here, both configured in the repository so every
contributor gets the same set.

**Repository-owned skills** live in `.claude/skills/` and load automatically
— nothing to install:

- `app-laufen-lassen` — build, install, and drive the phone app on a device
- `dhu` — the Android Auto Desktop Head Unit for the car surface
- `viewmodels` — the state-holder pattern every phone screen follows
- `interactors` — the domain use cases (`XInteractor`, `XObserver`) ViewModels build on

**Chris Banes' Kotlin/Compose skills** come from an external marketplace,
registered in `.claude/settings.json`. Because the source is a third-party
GitHub repository, Claude Code will not pull it in silently. Each
contributor runs both steps once, after trusting the repository folder:

```bash
claude plugin marketplace add chrisbanes/skills   # clones the catalog
claude plugin install chrisbanes-skills@chrisbanes-skills
```

Inside a running session the same two steps are `/plugin marketplace add
chrisbanes/skills` and `/plugin install chrisbanes-skills@chrisbanes-skills`.

The first step is easy to skip, because `settings.json` already makes the
marketplace *name* known. Skipping it yields `Plugin "chrisbanes-skills" not
found in marketplace "chrisbanes-skills"` — the name resolves, but no
catalog has been fetched behind it.

Until the install completes, Claude Code reports the plugin as not
installed. The skills are namespaced
(`/chrisbanes-skills:compose-performance` and so on) and cover Compose state
and effects, recomposition performance, component and slot APIs, animations,
focus and D-pad navigation, Compose UI testing, coroutines and Flow
modelling, Kotlin control flow, and KMP-aware API design — all directly
relevant to `shared/` and `androidApp/`.

The plugin also brings `gradle-run`, whose wrapper script the "Build"
section above now uses for every Gradle command. That skill is the reason
the build commands look the way they do, so the plugin is a prerequisite for
building here, not an optional extra.

Per-user overrides belong in `.claude/settings.local.json`, which is
git-ignored. Don't put personal preferences into `.claude/settings.json`.

## Shared code

`shared` holds the model, the data sources and repositories, the use cases
and the ViewModels. Android and iOS build on it and duplicate none of it.
What it contains is documented next to the code it describes:

- [docs/shared/domain-model.md](docs/shared/domain-model.md) — sites, stops,
  vehicles and the garage, search areas and routes
- [docs/shared/data-layer.md](docs/shared/data-layer.md) — data sources,
  repositories, the ChargeAhead backend, verifying a data source
- [docs/shared/use-cases.md](docs/shared/use-cases.md) — interactors and
  observers
- [docs/shared/assembly.md](docs/shared/assembly.md) — `ChargeStopsFeature`,
  the Koin graph, `ChargeStopFormatter`, the iOS bridge

Whoever changes one of those signatures updates its page in the same change.

The rules that hold everywhere:

- **Domain knows no one.** `domain` declares the data source and repository
  interfaces; every implementation lives in `data`, `settings` or the
  platform.
- **Business logic is a use case** in `domain.usecases`, a Koin factory in
  `chargeStopsModule()`. ViewModels only wire use cases up.
- **No hand-made coroutine scopes or dispatchers.** Inject
  `AppCoroutineDispatchers` and `get<CoroutineScope>(AppScope)`; never
  `CoroutineScope(...)` or `Dispatchers.*` outside the Koin module.
- **One settings file per process**, opened once by `settingsModule { … }`.
- **The ChargeAhead backend is required.** The app stops at startup without
  it, and always runs against the current backend: a field the backend sends
  is required in the app, even when `api-model` declares it optional. Base
  URL and token never go into the repository (`local.properties`,
  `iosApp/Secrets.xcconfig`).
- **Whoever changes a data source's mapping runs both its tests**, the
  fabricated one and the live contract test (docs/shared/data-layer.md).

## Rules for the phone UI

Every screen's state lives in a **ViewModel in
`shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/ui/`**, publishing one
`uiState: StateFlow<XUiState>`; the Compose screen is stateless and takes
that state plus lambdas. The pattern is the MVI-like one from "Now in
Android"; the rules, the template and the test setup are in the
`viewmodels` skill, and the reasoning is in ARCHITECTURE.md section 8.

The short version, for the cases where the skill isn't loaded:

- New screen, sheet or dialog with state → new ViewModel in `shared/ui`,
  declared in `shared/ui/SharedUiModule.kt` (Koin, `viewModelOf`).
- `remember { mutableStateOf(...) }` in a composable is for state that dies
  with the gesture. Anything that should survive a rotation is ViewModel
  state.
- No user-visible text in a `UiState` — `shared` has no resources. States
  and reasons are types; the wording comes from `strings.xml`.
- ViewModels take the settings repositories they read (`VehicleRepository`,
  `PreferencesRepository`, …), `ChargeStopsFeature` and domain use
  cases — never a `Context`, never a `CoroutineScope`.
- Because they live in `shared`, `:shared:compileKotlinIosSimulatorArm64`
  is mandatory after touching them. It is what keeps the "reusable on iOS"
  claim honest.

The car screens follow the same pattern with their own ViewModels in
`shared/ui/car`; see "Rules for the car UI".

## Rules for the car UI

Both platforms only translate — they don't compute and don't format
themselves. Every number shown in the car comes from `ChargeStopFormatter`.

Each car screen's state lives in a ViewModel in `shared/ui/car`
(`CarXViewModel`, one `uiState`), created through `CarViewModels` over the
car session's `ChargeStopsFeature`. An Android Auto `Screen` gets it with
`screenViewModel { viewModels.x() }`, which clears it in `onDestroy`,
collects `uiState` and calls `invalidate()`; `onGetTemplate()` maps
`uiState.value` to a template. No `KoinComponent` in a screen, no planning or
selecting — only templates, permissions, navigation and the Maps hand-off.

**Android Auto**

- The category is `androidx.car.app.category.POI`. **Not** `CHARGING` or
  `PARKING` — both have been deprecated since Car App Library 1.3.
- The list's row count isn't free to choose. Always query it via
  `carContext.getCarService(ConstraintManager::class.java)
   .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)` and trim the
  list to that.
- Reachability via `CarColor.GREEN` / `YELLOW` / `RED`. Color is a
  supplement, never the sole carrier of the information — the text must name
  the state too.
- Header via `Header` and `setHeader(...)`. `setTitle`, `setHeaderAction`,
  and `setActionStrip` directly on the template have been deprecated since
  1.7.0.
- Actions at the right edge of the header must carry an icon and must not
  carry their own title (`ActionsConstraints.ACTIONS_CONSTRAINTS_MULTI_HEADER`).
  A violation only throws at runtime.
- An empty list is never the outcome. If there's nothing to show, a
  `MessageTemplate` says why, from the screen's own state (on the route
  screen, the failed `TripPlanResult`).

**CarPlay**

- CarPlay offers the same screens as Android Auto: home, destination
  search, route, charge now, charge level. The single corridor list it shows
  today is being replaced (#152); new car features go to both platforms.
- The entitlement is `com.apple.developer.carplay-charging`. Without
  approval from Apple, no build is possible; the code is still written in
  full.
- `CPTemplateApplicationSceneDelegate`, `CPListTemplate` with `CPListItem`.

## The iOS side on this machine

Development happens on Linux. The iOS side splits here into two parts that
can be checked to **very different** degrees — conflating them means
reporting unverified work as verified.

### Kotlin (`shared/src/iosMain`) — compiles fully

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
```

Kotlin/Native compiles the Apple targets on Linux too. This checks
`iosMain` against the **real** cinterop bindings of CoreLocation, Foundation,
and UIKit — wrong property names, wrong delegate signatures, and missing
`actual` declarations show up here, not only on a Mac.

What does **not** work on Linux is linking: `linkDebugFrameworkIos*` is
silently skipped by the Kotlin plugin (SKIPPED, not an error). That means no
`Shared.framework` and no Objective-C header are produced.

### Swift (`iosApp/`) — syntax at best

`import CarPlay`, `import UIKit`, `import SwiftUI`, and `import Shared`
can't be resolved here; an actual compile is impossible. What does work is a
**syntax check**, provided a Swift compiler is installed:

```bash
tools/check-swift.sh
```

The script runs `swiftc -parse` over every Swift file. It finds syntax
errors, **not** type errors. A green result means "parses cleanly", not
"compiles". If `swiftc` is missing, the script exits with code 127 — then
the Swift code is **also syntactically unverified**, and should be reported
as such.

Swift 6.1.3 is installed on this machine (`sudo apt install swiftlang` on
Ubuntu 26.04), so the script runs.

Because the Objective-C header doesn't get produced here, everything that
depends on its shape stays unverified: whether Kotlin `object`s show up as
`.shared`, whether `operator` becomes `operator_`, what Kotlin `enum`s are
called in Swift.

### On a Mac with Xcode — the whole app builds

There the framework links and Swift compiles against the real header, so
everything above that stays unverified on Linux gets checked: `xcodegen
generate`, then the `xcodebuild` command from `iosApp/README.md`. It
replaces `tools/check-swift.sh`.

## Before reporting something as done

1. `./gradlew :androidApp:assembleDebug` completes — show the output, don't
   just claim it.
2. `./gradlew :shared:jvmTest` completes.
2b. `./gradlew :ui-tests:testDebugUnitTest` and `tools/screenshots.sh validate`
   complete, if `phone-ui/` or `ui-tests/` was touched. A screenshot that
   changed on purpose is re-recorded with `tools/screenshots.sh update` and the
   PNG committed — goldens only match where they were rendered, see
   docs/testing.md.
3. `./gradlew :shared:compileKotlinIosSimulatorArm64` completes, if
   `shared/` was touched. This is mandatory, not optional: otherwise
   `commonMain` is only checked against JVM and Android, and Kotlin/Native
   is stricter.
4. `tools/check-swift.sh` completes, if Swift files were touched (on a
   Mac: the `xcodebuild` from `iosApp/README.md`). If
   `swiftc` isn't installed, the script exits with code 127 — then the
   Swift files are **also not** syntactically checked, and that must be
   reported as such.
5. No file outside the assigned directory was touched.
6. Anything that couldn't be built or checked is named explicitly as
   unverified.

Don't claim something works when it was only written.
