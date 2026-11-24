# NovaBrowser

Android browser (Kotlin, Android WebView) with a built-in media player, a link detector for streams and subtitles, and optional torrent streaming. **Experimental, v0.1.**

## What is in the code
- Tabs, bottom address bar, bookmarks, history, desktop mode, downloads, popup blocker, basic ad/tracker host blocklist.
- Link detector: watches page network requests and scans the page for HLS (`.m3u8`), DASH (`.mpd`), mp4/webm/mkv, audio, subtitle (`.vtt/.srt/.ass/.ttml`), magnet and `.torrent` links. Tap an entry to play it; detected subtitles are passed to the player automatically.
- Player (Media3 ExoPlayer): HLS, DASH, progressive files, subtitles, load a subtitle file, speed, zoom/fill.
- Torrent streaming (libtorrent4j): only after you confirm. Largest file in the torrent streams over a loopback server; upload is capped at 64 KB/s and data is deleted when the player closes.

## Limits (honest list)
- Cannot capture DRM-protected streams (Widevine etc.), `blob:` players that hide their source, or anything behind a login you do not have. DRM and subscription protections are not bypassed.
- Not every codec or container plays; ExoPlayer decides.
- Engine is Android WebView, not GeckoView (needed for request inspection). The original brief's GeckoView plan is not implemented.
- Not yet tested on a physical device or emulator. CI runs the unit tests only.
- No cookies are forwarded to the player, so streams that need a session cookie will fail.

## Build
`gradle :app:assembleDebug` (JDK 17, Android SDK 34). CI: `.github/workflows/build.yml`. Tag `v*` to publish a release APK.

See `PROJECT_BRIEF.md` for the original project plan.
