# PR 3466 simulator verification

Tested head: `d6b728c3cd4faa99836a3d309b2f671f8bb54213` (2026-09-22).
No application source changes.

- Android: Pixel 8 Pro, Android 15/API 35, ARM64, headless emulator with SwiftShader. Built and installed `:app:android:installDefaultDebug -Pani.android.abis=arm64-v8a` using JBR 21.
- iOS: iPhone 17 Pro/iOS 26.4 and iPhone 16 Pro/iOS 18.2 simulators, ARM64, Xcode 26.5. Built `:app:ios:buildDebugForSimulator` with `ani.enable.ios=true` and `ani.build.framework=true` after generating the dummy framework and installing pods.
- Playback fixture: generated 10-minute 960x540/24fps H.264/AAC MP4, FFmpeg testsrc2 with advancing frame counter, served by a local HTTP server with Range support. Loaded through a web-selector source named `PR3466 Local PiP Fixture` in the real application. Screens show the subject metadata alongside the synthetic video, not that anime's video content.

## Android observations

Automatic PiP on Home, manual entry, system pause/resume, and restore to inline playback succeeded. The pause screenshots retain 01:49.667 across a three-second interval; the resumed frame advances to 01:51.875. Restoring shows inline playback at 02:07.250 with danmaku. Pausing inline before Home leaves the launcher without PiP and preserves the paused position on return.

Repeated transitions also produced a persistent black PiP window. One return to the app showed “Playback failed. Please switch resources”; another later repeat remained buffering. Restarting the app allowed manual PiP to succeed, as shown in `android-manual-retry.mp4`. The log excerpt establishes player failure and surface reconfiguration activity, but does not establish the underlying cause or behavior on physical devices.

## iOS observations

Both simulators launch the application and display subject details, then crash after tapping “Start watching”, before video playback. iOS 26.4 reproduced on three launches; iOS 18.2 reproduced on its tested launch. `ios26-episode-crash.mp4` records the transition. The iOS 18.2 screenshots show before/after.

LLDB queries inside the application returned `NO` for `AVPictureInPictureController.isPictureInPictureSupported` on both runtimes. The Kotlin null-pointer exception symbolicates to native PiP controller initialization at `PictureInPictureController.ios.kt:92`. The local SDK declares this initializer nullable and documents nil initialization when PiP is unsupported. The factory currently only checks the player/layer, without checking platform PiP support. Native PiP playback could not be validated on either simulator.
