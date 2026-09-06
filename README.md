# Issue #3391 / #3368 verification evidence

Code: 741a896400974bda3f50694ad27b0f65eaa2162f

Captured 2026-09-06 using repo android-ui-verify and desktop-ui-verify skills.
Android: Pixel 8 Pro API 35 arm64, 2992x1344 landscape, density 480, ExoPlayer, debug APK built from the fix.
Desktop: macOS arm64, JBR/JCEF 21 packaged Ani.app built from the fix, 1440x900 native window, mpv.
Media: 孤独摇滚！episode 1, 嘀嗒影视, H.264/AAC.

- compose-before-fix-cancelled.png: final regression test run against unchanged main 28d6ff9c6e; changing the pointerInput key while held leaves 2.50x visible and the speed assertion fails (expected 1.3, actual 2.5).
- compose-fixed-cancelled.png: same cancellation with the fix; speed restored to 1.3 and indicator removed. This is also the committed screenshot baseline.
- android-held.png / android-released.png: touch hold at (2400,1050) for 2s yields 2.5x; UP and 3s wait restores 1x and hides indicator.
- android-before-cancel.png / android-cancelled.png: a subsequent 2s hold yields 2.5x; native ACTION_CANCEL and 3s wait restores 1x and hides indicator. Android ACTION_CANCEL already worked before this fix; this is a runtime regression check.
- desktop-held-final.png / desktop-released-final.png: mouse press at (900,700), hold 2s, release. Playback renders normally and no touch acceleration indicator appears, as designed for mouse input. Actual coroutine cancellation is covered by the Compose regression test.

Final video-player desktopTest: 51 tests, 0 failures/errors/skips (6 new gesture tests).
The original #3368 A/V-sync and physical-device symptoms remain unverified.
