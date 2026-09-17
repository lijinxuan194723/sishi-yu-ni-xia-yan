# Journal editor and dialog checkpoint — 2026-09-17

Verified source commit: `e820ee868ac5ec7bd1b0b8d64ce4371c151fb9f5`.
Branch: `feature/v2.0.10-native-migration`.
This is a component and persistence checkpoint, not a production APK or complete migration.

## Changes actually committed

- `JournalViewModel.kt`: connects the application-owned journal editor and Room repository to UI state. Switching or closing an editor waits for accepted input to be saved. A failed save retains the current editor. Export and recovery-copy commands expose errors rather than discarding text.
- `JournalEditor.kt`: full-window Compose editor with a fixed title, separately inset-aware formatting dock, transparent companion asset, title/body fields, mood entry, selectable reading mode, text export, retry and recovery-copy actions. Formatting tools use the actual selection for heading, list, checkbox, bold and quote. Reading mode currently displays selectable plain text; rendered Markdown parity is still pending.
- `MemoEditorSession.kt`: freezes new input before the final close write. Stale references reject edits after close; failed or cancelled closes restore editability. An already-running save still includes newer text accepted before the close boundary.
- `JournalRepository.kt`: serializes editor detachment/reopening; metadata changes cannot leave an old writable editor attached to a replaced revision. Recovery copies insert title/body/mood atomically and retain the original conflict. Purge rechecks Trash eligibility inside the same database transaction as deletion.
- `NativeDialog`: measures available resized-window height; its title remains outside the body. Simple long content may opt into `scrollBody=true`. Existing forms with a weighted field scroller and fixed save row keep finite constraints through the default `scrollBody=false`.
- `native/tools/report_tests.py`: reports real JUnit cases by class, records failures, rejects missing/empty test output, and prints bounded navigation diagnostics and PNG dimensions from disposable emulator artifacts. Existing navigation assertions remain enabled and unchanged.

## Runs and outcomes

| Scope | Run | Job | Actual result |
|---|---|---|---|
| Native host and instrumentation APK compilation; core JVM | 35185999724 | 105088083909 | Compilation succeeded; 170 tests, 0 failures/errors/skipped |
| Android API 30 Compose | 35185999724 | 105088682621 | 79 tests, 0 failures/errors/skipped |
| Android API 35 Compose | 35185999724 | 105088682645 | 79 tests, 2 failures, 0 errors/skipped |
| Android API 30 Room | 35185999735 | 105088083996 | 75 tests, 0 failures/errors/skipped |

UI fixtures use Pixel 2, x86_64, overridden 320×640 resolution at 160 dpi. UI animations remain enabled; individual reduced-motion scenarios still exercise their declared fallback. Room tests use real Android Room/SQLite in-memory databases. These are not physical-device, disk power-loss, full app startup or 120Hz tests.

Relevant UI classes pass on BOTH Android API 30 and 35:

| Class | Cases per API |
|---|---:|
| JournalEditorUiTest | 5 |
| DialogLayoutUiTest | 4 |
| SettingsUiTest | 11 |
| SettingsVisualTest | 3 |
| PomodoroUiTest | 6 |
| LayeredScrollingTest | 5 |
| MotionUiTest | 14 |

The new six `MemoEditorLifecycleTest` JVM cases and five `JournalLifecyclePersistenceTest` Android cases are included in the counts above, not added to them again.

## Regression found and fixed during this iteration

Commit `27731825d26f5e6d95f4b948b1edae9db0d10dc7`, run `35185137006`, exposed two API 30 failures after automatically wrapping every dialog body in a scroller:

- `PomodoroUiTest.presetSheetAtLargeTextKeepsSaveAndCloseReachable`
- `SettingsVisualTest.enlargedProfileCanScrollFieldsWhileKeepingItsSaveActionVisible`

The wrapper had removed finite constraints from form-owned weighted scrollers and displaced their save controls. The final commit uses an explicit scroll-body opt-in instead; both original tests and a new fixed-action regression test pass. The assertions were not removed or relaxed.

## Android 15 navigation remains unresolved

Two existing real-pixel checks still fail at the verified source commit:

1. `NavigationGlyphsUiTest.initialNightAndDialogReturnKeepActualSystemButtonsReadable`
2. `PlatformNavigationBaselineUiTest.plainAndroidWindowCanDisplayLightNavigationButtons`

Both report `Night navigation button 0 lacks visible light glyph pixels: 0`. The second case deliberately uses a plain Android window without Compose chrome. API 35 diagnostic output reports a visible navigation inset, appearance 0 and darkIntensity 0.0; captured navigation and plain-window PNGs are 320×640. These facts do not establish that the app is correct, nor do they identify a root cause. Configuration flags are not a substitute for readable pixels. No threshold has been lowered, no failing case has been skipped, and the workflow remains failed. Verify actual SystemUI/Launcher button bounds, tint and screenshot content before further production styling changes.

## Artifact identifiers

The UI run retains compilation reports, a native-only source checkpoint, separate API 30/API 35 JUnit results, and screenshots. Confirm the head SHA before comparing results from a later run.

- Compile reports: artifact 10482426909; reported archive SHA-256 `e2323e1f118e096af185ad29101cdb74300d716adf31500f2cdb5fc5e317bdd2`.
- Native source checkpoint: artifact 10482267473; reported archive SHA-256 `405704ee794947c2ee174d96fc0d98a8c48d699593aa14919f08a74b9428f88b`. This is not a production-app release.
- API 30 UI: artifact 10482442502; reported archive SHA-256 `689374fb01ba0843ed9ba160f6ed7c1756c7868f53a9fed6e74456b4a0dfbb5e`.
- Room: artifact 10482047422; reported archive SHA-256 `6c171b8f3d90d3054768015ebf8f6de896ccee76bdfdd697357eb2823741cc83`.

Hashes above are reported by GitHub Actions upload-artifact, not a claim of a second local archive verification. The local execution/image-viewing environment was unavailable during this iteration. Captured editor screenshots have not received a separate visual review here.

## Delivery limits

The attempted complete `JournalScreen.kt` list/search/folder UI upload was blocked by the tool safety check. That file was not committed, and that blocked operation was not retried through another path or encoding. The successfully committed editor/ViewModel and repository tests do not stand in for a complete journal navigation flow.

Full production `:app` integration, journal list and rendered reading mode, remaining plan/moment/chat parity, physical-device gallery/IME/notification behavior, large real backup round trips and high-refresh-rate measurements remain pending. Do not replace the working 2.0.9 app with the instrumentation host. Main was not merged and no Release was created.
