- [x] Use ViewModels, KMP-Compliant so they can be reused in iOS later on
      (https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html) —
      done: `shared/src/commonMain/kotlin/de/autoapp/shared/ui/`, one per
      phone screen, in the MVI-like "Now in Android" style. Rules in the
      `viewmodels` skill, reasoning in ARCHITECTURE.md section 8.
- [ ] Use Navigation3 library for navigation (https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html#multiplatform-support)
      Which page is showing is still an enum in `MainActivity`; the
      ViewModels are activity-scoped for the same reason. Navigation3 brings
      per-destination scopes — `phoneViewModel()` is the only place that
      then changes.
- [ ] Use Room for database access
- [ ] Use Koin for dependency injection (NO ANNOTATIONS!)
      `PhoneViewModels.kt` and `ChargeStopsFeatureProvider` are the
      hand-wired stand-ins; a Koin module replaces both without touching a
      single ViewModel.
- [x] Use MVI-like pattern as in "Now in android", see
      https://github.com/android/nowinandroid/blob/main/feature/interests/impl/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/interests/impl/InterestsViewModel.kt
      — done together with the ViewModels above.
- [ ] Write Skills and/or docs for all the above if required
      Done for the ViewModels (`.claude/skills/viewmodels/`); the remaining
      items still need theirs.
