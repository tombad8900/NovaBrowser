# Nova implementation ledger

The complete user specification is preserved in USER_REQUIREMENTS_2026-11-24.ar.md. It is the outcome reference, not a completion claim. No feature below is done until implementation and evidence are recorded.

## Acceptance ledger
- [ ] Browser stable navigation, back/forward/reload, error/retry UI, bounded tabs and capture work
- [ ] Modern Arabic/English RTL-aware browser and organized settings
- [ ] Protection against ads/trackers/popups/unwanted redirects, per-site controls, breakage tests
- [ ] Quiet background capture, iframe and response/request observation, no technical link-count UI
- [ ] Source identity with MIME/evidence/state, request context, all sources and subtitles in one session
- [ ] Dedupe repeated observations without corrupting signed URLs or mixing different request contexts
- [ ] Validate media response, reject HTML/errors, clear recoverable playback errors
- [ ] Scoped credentials/headers on playlists, segments and separate subtitle requests; no redirect leakage
- [ ] Player Sources, Arabic-first Subtitles, actual Quality tracks, Audio, Speed panels
- [ ] Rotate/fullscreen/aspect/seek/volume/brightness/play-pause/show-hide/portrait-landscape controls
- [ ] Source switching preserves position where sensible; subtitle switching works
- [ ] Profiling of reported hangs, bounded resource use, no guessed root cause
- [ ] Unit/integration tests and emulator visual review; physical feedback required
- [ ] Honest limits, licensing and build-tool review before next release

## Review, 2026-11-24
Reference commits: CinemaStream-Native 720f5272, SkyStream f5523ae0, Zangetsu 6814f950, CloudStream a28e6468.

CinemaStream carries a BrowserCapture(url,kind,playable,isArabicSubtitle,headers); filters request headers, fills URL-specific cookies, merges repeat capture headers and guards page generations. Its handoff is one URL plus one header map and subtitle URL list. Keep the idea of linked request context and stale-session guards, not its URL keyword classifier, blanket header factory, bridge or design. SkyStream separates source status/quality panels; Zangetsu separates top/center/bottom controls and overflow; CloudStream separates per-link and subtitle request factories. These are behavioral references, not copied implementation. Their LICENSE files are GPLv3; Zangetsu also includes an MPL file. No code/assets copied. CinemaStream has no root license file in this checkout; do not assume redistribution permissions from public visibility.

Retain GeckoView, Media3, tested torrent transport isolated as experimental, build scripts, classifier test cases. Fix navigation errors, lifecycles, capture limits/thread use and data transport. Rebuild browser chrome/settings and player controls as one product, not a catalog app. Do not migrate to Flutter or copy reference skins.

No measured ANR root cause yet. Static risks in old Nova: unlimited live Gecko sessions, repeated whole-DOM serialization in every iframe, per-event UI posting, JSON history read/rewrite on UI thread, missing lifecycle state restore and blank load failures. Instrument and test rather than claim these explain the user's exact hangs.

## Intended flow
Gecko tab/page generation -> extension network observations -> candidate repository (bounded, batched, MIME/status/URL/headers with provenance) -> opaque in-memory PlaybackSession ID -> player with all sources/subtitles -> scoped HTTP factory per resource -> Media3 tracks -> actual quality/audio/subtitle controls.

Credentials stay in memory, excluded from URLs shown to the user, logs, saved history and public diagnostics. Captured headers are scoped by observed origin and path directory, expire with the session, and are not blindly inherited across redirects. Cross-origin resources need their own observed context; unsupported protected streams fail clearly, not by disabling TLS or bypassing access controls. Signed query strings are never rewritten. Capture source hints do not become quality labels without evidence.

## Ad filtering route
Nova requests the unmodified Mozilla-signed uBlock Origin 1.77.0 package from its AMO download URL using GeckoView's normal signature-checked install API. It is not bundled, relicensed or copied into Nova. On failure the UI must show unavailable, not pretend filtering is on. Installation, startup, per-site popup controls and breakage need emulator verification. uBlock's own per-site switch is separate from Gecko tracking protection. Package source: https://addons.mozilla.org/en-US/firefox/addon/ublock-origin/ ; upstream source: https://github.com/gorhill/uBlock ; API design: https://firefox-source-docs.mozilla.org/mobile/android/geckoview/design/managing-extensions.html . The current GeckoView AAR public API was inspected directly rather than relying on the older design page's method names.
