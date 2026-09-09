- [x] Use ViewModels, KMP-Compliant so they can be reused in iOS later on
      (https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html) —
      done: `shared/src/commonMain/kotlin/de/autoapp/shared/ui/`, one per
      phone screen, in the MVI-like "Now in Android" style. Rules in the
      `viewmodels` skill, reasoning in ARCHITECTURE.md section 8.
- [x] Use Navigation3 library for navigation — done: androidx.navigation3
      back stack in `MainActivity` (`Destinations.kt`), the Page enum is
      gone, StopDetail carries its stop index. The androidx artifact, not
      the multiplatform one: the iOS phone UI is SwiftUI, so there is
      nothing to navigate there — the swap is coordinate-level if that ever
      changes. Per-destination ViewModel scope is NOT wired yet:
      lifecycle-viewmodel-navigation3 rides lifecycle 2.11, which needs the
      compileSdk 37 that ARCHITECTURE.md §9 documents as unavailable — once
      Platform 37 lands it is one artifact plus one NavDisplay decorator
      line in `phoneViewModel()`.
- [x] Use Room for database access — done: Room 2.8.4 KMP replaces
      SQLDelight for the tile cache (same tables, same queries, entities
      and DAO in commonMain, BundledSQLiteDriver on every target). Fresh
      v1 database file: Room cannot adopt a SQLDelight file, and faking
      its identity table for a 3-day-TTL cache is risk without payoff —
      one refetch per area, once; the Android factory deletes the old
      file. Room 3.0 (KMP-first, currently alpha) is a coordinate swap
      later.
- [x] Use Koin for dependency injection (NO ANNOTATIONS!) — done, plain
      DSL: `appModule` (androidApp) holds the app-scoped singletons,
      `sharedUiModule` (`shared/ui`) declares every ViewModel via
      `viewModelOf`, `startKoin` runs in `ChargeAheadApp`. The hand-wired
      `PhoneViewModels.kt` and `ChargeStopsFeatureProvider` are gone; no
      ViewModel was touched. `ChargeStopsFeatureFactory` stays — it is
      shared assembly, not wiring.
- [x] Use MVI-like pattern as in "Now in android", see
      https://github.com/android/nowinandroid/blob/main/feature/interests/impl/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/interests/impl/InterestsViewModel.kt
      — done together with the ViewModels above.
- [x] Write Skills and/or docs for all the above if required — the
      `viewmodels` skill covers registration via `sharedUiModule` (Koin)
      and the Navigation3 rule; ARCHITECTURE.md §8/§9 and AGENTS.md were
      updated with each round; the per-phase plans under
      `docs/superpowers/plans/2026-09-09-*` are the decision records.
