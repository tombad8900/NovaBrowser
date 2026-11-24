# Nova Trial 0.4.1

Focused subtitle-position patch for the existing Nova Trial 0.4 app. Android 8+, ARM64. Package app.nova.browser.preview, versionCode 5, versionName 0.4.1-preview. Same persistent signing identity as 0.4.0. Installs as an update without intentionally clearing existing data. The separate 0.3 app and previous release assets remain available.

## Changes
- Arabic subtitles no longer compete with the center playback controls in portrait or the timeline in landscape at default height.
- Position is measured against the fitted video and visible lower controls. Hiding controls restores the user's chosen lower placement. User subtitle height remains the minimum offset.
- Saved font size is verified through the actual settings control and rendered caption, with 20sp and 26sp samples.
- Includes the 0.4 player, source-lifetime, language, search and subtitle settings improvements. No new DRM implementation.

## Verification scope
Build/unit/native local-seeder tests and exact-build emulator integration are required before this release is published. Integration covers actual play/pause and ten-second seek taps in English/Arabic portrait/landscape, source clearing on refresh/navigation, persisted settings, actual Google omnibox routing, Arabic caption pixels with controls shown/hidden, and in-place update/data retention from 0.4.0. Final evidence and SHA-256 are recorded in the GitHub release notes.

## Limits
No physical-device validation yet. Emulator playback uses controlled media fixtures and does not prove compatibility with arbitrary providers. Widevine/DRM license flows are not implemented. PiP and broad real-provider compatibility remain unverified. This is an experimental app, not a finished browser.
