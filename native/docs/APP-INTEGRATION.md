# 2.0.10 native application integration

This is an incomplete development build, NOT full feature parity or a replacement for 2.0.9.

The `:app` module now has real `LukeApplication` and `MainActivity` implementations. The launcher shares one AppGraph with receivers/workers and reads the native Room/DataStore data. It does not import, overwrite, or delete the old app's storage.

Connected production destinations: Home, Chat (including composer and conversation tools), Timer (including Pomodoro), Settings, and Skills. The six original main tabs remain. Moments, Plans and Journal still display an explicit migration gate instead of a fake empty collection; their complete screen upload/integration has not been completed. The existing journal editor remains available to its component tests, not exposed as a write-only product page without a list. No blocked journal/plan screen operation was retried through a different file.

Main pager state and per-route saveable state survive activity recreation and settings/skills return. Drafts remain application-owned. The keyboard hides the bottom navigation rather than compressing the composer between two navigation bars. Animated pages use existing theme/motion policies; a requested high-refresh mode is limited to the current physical resolution and restored when stopped. This requests a display mode, not a 120Hz performance guarantee.

Build with JDK 17, Android SDK 35 and installed Gradle 8.11.1 from `native/`:

```sh
gradle --no-daemon --console=plain --max-workers=2 :app:assembleDebug :app:assembleDebugAndroidTest :core:testDebugUnitTest
```

Debug output: `app/build/outputs/apk/debug/app-debug.apk`. Release is unsigned unless the builder supplies their own signing configuration. This repository does not contain a production signing key. The native package is `cn.sishiyuni.nativeapp`, separate from the old app. Keep the working old app and backups.

Verification is pending for the new launcher until the dedicated application workflow has run. Existing component tests and known Android 15 navigation pixel failures remain in place. Building a launcher does not complete the remaining feature migration, data round trip, or physical-device acceptance.
