# Issue 2877 runtime evidence

- macOS arm64: built `:app:desktop:createDistributable` with JBR/JCEF 21.0.11; launched the packaged app via the desktop-ui-verify skill.
- Playback screenshots use the existing `me.him188.ani.app.desktop.MpvVerifyKt` entry point in the packaged app and its bundled mediamp/mpv runtime. The test file is selected with `-Dani.seekverify.video=...` in the temporary packaged app configuration; the normal app entry point is restored afterward.
- Generated 60-second 1280x720 24fps Matroska files contain HEVC Main 10 / yuv420p10le video, AAC audio, and a default internal ASS or SRT subtitle stream. The subtitle text is a separate muxed track, positioned at the top to avoid the verification window's progress overlay. No subtitle text is burned into the video.
- ASS and SRT both render Chinese and English text while player state is Ready / playWhenReady=true / isBuffering=false.
- Android: Pixel 8 Pro API 35, Android 15, 1344x2992 at 480 dpi; installed the default debug arm64 APK, launched the actual MainActivity, navigated to Settings > BitTorrent, and toggled PikPak with empty credentials. This is a UI smoke check, not a live premium-cloud download test.
- Cache routing, cloud failures, saved-download restoration and subtitle filtering are covered by deterministic desktop tests. Android instrumented tests were attempted but blocked at :app:shared:app-data:dexBuilderAndroidDeviceTest by existing invalid dex names in DefaultBangumiMergeRepositoryTest.

Fixture commands (run in this directory with ffmpeg):

```sh
ffmpeg -f lavfi -i 'testsrc2=size=1280x720:rate=24:duration=60' -f lavfi -i 'sine=frequency=440:duration=60' -i subtitle.ass -map 0:v -map 1:a -map 2:s -c:v libx265 -preset ultrafast -crf 32 -pix_fmt yuv420p10le -x265-params 'log-level=error:pools=2' -c:a aac -c:s ass -metadata:s:s:0 language=chi -disposition:s:0 default hevc10-ass.mkv
ffmpeg -i hevc10-ass.mkv -i subtitle.srt -map 0:v -map 0:a -map 1:s -c copy -metadata:s:s:0 language=chi -disposition:s:0 default hevc10-srt.mkv
```
