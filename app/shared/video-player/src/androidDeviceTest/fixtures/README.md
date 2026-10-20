# Compressed subtitle fixtures

`../assets/compressed-ass.mkv` and `../assets/compressed-srt.mkv` are synthetic:
a solid VP8 video and three ASS or SRT subtitle tracks.
Track 2 declares `ContentCompression` with the default zlib algorithm; track 3 explicitly sets
`ContentCompAlgo=0`; track 4 contains ordinary uncompressed subtitles. The compressed payloads include
NUL bytes. The Chinese events run at 1–4 s and 5–8 s, with different text and intact punctuation.

Regenerate from the repository root with Python 3 and ffmpeg installed:

```sh
python3 app/shared/video-player/src/androidDeviceTest/fixtures/generate.py
python3 app/shared/video-player/src/androidDeviceTest/fixtures/generate.py --codec srt
```

Run the device tests on an API 30+ emulator (the existing common tests use spaced method names):

```sh
./gradlew :app:shared:video-player:connectedAndroidDeviceTest \
  -Pandroid.min.sdk=30 \
  -Pandroid.testInstrumentationRunnerArguments.class=me.him188.ani.app.videoplayer.media.CompressedSubtitleMatroskaExtractorTest,me.him188.ani.app.videoplayer.media.CompressedAssPlaybackTest
```

The extraction tests check native libass events for ASS and Media3 cues for SRT, including
text, timestamps, and durations on implicit zlib, explicit zlib, and plain tracks.

The playback test uses the production player and video surface in a test activity. It plays,
pauses, seeks to the second Chinese event, and switches to the plain ASS track. The optional
`-Pandroid.testInstrumentationRunnerArguments.subtitleVerificationHoldMillis=10000` leaves each
state visible for external emulator screenshots. Verification screenshots belong outside the repository.
