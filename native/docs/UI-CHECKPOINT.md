# Native Compose UI checkpoint — in progress

This branch is a staged migration, not the finished replacement for 2.0.9. Do not install the instrumentation host as a product build or announce full feature parity.

## Implemented source in this checkpoint

- Seasonal day/night Compose design system, text-only app scaling, independent chat sp.
- Finger-linked PagerState tab indicators and a six-item bottom navigation component; cancellable spring click transitions.
- Native content-layer backdrop on API 31+, gradient fallback on older devices, fixed foreground text and measured Scaffold insets.
- Mechanical dial, bounded horizontal countdown ruler, cancellation/multi-touch handling and accessible selection.
- Chat sessions, drafts, archive/rename/search/star UI, cancellation-safe model response binding, original draft retained on send failures.
- Application-owned revisioned draft persistence with JVM race/failure tests.
- Skills native import/review/enable/update/uninstall UI, digest-checked confirmation, no script execution.
- Seasonal weather Canvas and forecast/city UI using the existing Open-Meteo adapter.
- Home sections and an actual hero-to-photo SharedTransitionLayout transition.

`uitesthost` is deliberately isolated: it has no launcher intent and supplies individual screens only for Android instrumentation. Product app/MainActivity, full settings, journal/plans/moments, decorative bubble artwork, attachments, persona asset, complete legacy migration parity and end-to-end integration remain pending. The old application remains intact in the parent directory.

The `assets/images` tree reuses the currently available repository images without editing them. This alone does not prove it contains every image in the most recent locally delivered 2.0.9 ZIP.

## Verification

The previous checkpoint `23ded9991d6170453ce5a7b005c5d37011103540` compiled the design system, timer and chat libraries and passed its JVM tests in GitHub Actions run 35098359157. New home/weather/Skills and instrumentation files in this checkpoint must pass their own exact-commit build before being marked verified.

UI tests cover finger-following pixels, reverse/rapid tab changes, ruler cancellation, 15 dial size/system-font combinations, conversation draft preservation, avatar alignment, Skills confirmation and a shared photo transition. They are not a 120Hz benchmark and do not replace physical-device tests, exact visual review, model API connectivity or all-feature parity.
