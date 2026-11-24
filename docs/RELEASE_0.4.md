# Nova Trial 0.4

Experimental Android 8+ ARM64 update, installed alongside Nova Trial 0.3. Package app.nova.browser.preview, versionCode 4. Old app/data remain untouched. New app starts with separate settings/history. A persistent signing identity is now used for this package.

## Included
- Centered play/pause and direct ten-second back/forward controls, separated from volume/tools.
- Arabic UI cleanup and app language selection: device, Arabic or English.
- Persisted search engine selection: DuckDuckGo, Google or Bing.
- Nova-owned subtitle font, size, vertical offset and background settings with reset.
- Page-scoped source capture cleared on refresh/navigation, expired playback-session snapshots.
- Wrapped media URL hints, extensionless subtitle metadata/context handling, source-switch safety.

## Verification
- Build, unit and native local-seeder tests pass.
- Emulator real-button play/pause and ten-second seeking passed in Arabic/English portrait/landscape, with inspected screenshots. Language, search engine and subtitle-background settings survive app restart.
- Page refresh/same-document navigation capture invalidation and wrapped-source handoff/canceled torrent selection passed local fixtures.
- Final package/signature installation check is recorded with the published artifact.

## Limits
Not a finished browser, and not a promise of all-site playback. No new Widevine/DRM playback integration is included. Encrypted streams need supported device DRM and a valid source-associated license flow. Some source sites expose subtitles only after choosing them in their own player. Distinct signed URLs are preserved, not claimed to be distinct translations. PiP and broad real-provider compatibility remain unverified. No private-tab, complete site-permission or production-grade persistence claim.
