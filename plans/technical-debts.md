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
- [ ] Use Room for database access
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
- [ ] Write Skills and/or docs for all the above if required
      Done for the ViewModels (`.claude/skills/viewmodels/`); the remaining
      items still need theirs.
