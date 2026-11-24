# NovaBrowser

Experimental Android browser using GeckoView 160, with a built-in Media3 player and media/subtitle detector. This is a prototype for testing, not a completed implementation of the project brief.

## Included
- Tabs, bottom address bar, bookmarks, history, desktop mode and basic downloads.
- Gecko Enhanced Tracking Protection configured for strict blocking, cookie isolation, tracking-parameter stripping and Global Privacy Control. Effectiveness and site compatibility have not been broadly tested. This is not Brave parity.
- Built-in WebExtension uses webRequest plus page scanning to find media and subtitle URLs. HLS, DASH, progressive media, common subtitle formats and torrent links are classified.
- Media3 player with subtitle handoff, subtitle file selection, playback speed and resize controls.
- Experimental torrent streaming via libtorrent4j, requiring confirmation. Peers see your IP address; uploading is capped at 64 KB/s while streaming. Cached torrent data is removed on normal player close.

## Verified
The Gecko prototype was built in CI and tested on an Android 11 x86_64 emulator: launch, readable web pages, detection of HLS/MP4/VTT links, and test-video playback with visible subtitles. Classifier unit tests and a local-seeder JVM torrent range-read/hash test passed. These checks are not physical-device or real-swarm torrent testing.

## Limits
- No physical-device testing yet. Android 8+ is required. Choose the APK matching your device architecture.
- uBlock Origin is not bundled or verified. No extension manager, cosmetic-filtering claim, per-site Shields controls or guarantee against anti-adblock detection. Some sites may break.
- Torrent streaming on Android remains unverified. An earlier native JVM test crashed before a teardown fix; later CI tests passed. Treat torrent playback as experimental.
- Streams requiring cookies or page Referer/Origin headers may fail. Nova does not forward those browser credentials/context headers to the player.
- DRM, paywalls and authentication are not bypassed. Some blob-based players expose detectable underlying requests; others will not work. Not every codec/container is supported.
- Downloads needing authenticated cookies may fail. Site camera/microphone/location/file-upload permission flows are not implemented.
- No private tabs, session restore, reader mode, AI assistant, full Arabic UI, PiP or passkey testing yet. UI uses Android Views, storage uses SharedPreferences, not the brief's planned Compose/Room/DataStore stack.
- Debug-signed APK for a trial. Do not use it for sensitive accounts yet. The brief remains the roadmap, not a completion checklist.

## Build
JDK 17, Gradle 8.14.3, Android SDK 37, Kotlin 2.4.20 and AGP 8.11.1.

```
gradle :app:assembleArm64Debug :app:assembleX64Debug
```

CI build and emulator workflows are in `.github/workflows/`. GeckoView security updates require a new Nova build.

See `PROJECT_BRIEF.md` for the original plan.
