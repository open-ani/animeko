# PR #3446 runtime verification

Date: 2026-09-22. Tested application source: `2af57659b1cfde7a896d9224fae91d11aa82bcff`.
Screenshots are unedited captures from the Android emulator and the packaged macOS app.
`android-paused-before.png` records the paused-frame cropping at PR commit `318bc69d38319bfaaee5d91b6aa85951b87a716b`.

## Android

Android 15 / API 35, arm64, `6.7in Foldable` AVD, 1080 × 2636 pixels at 480 dpi (360 × 879 dp), host GPU.
The APK is the `defaultDebug` arm64 build, package `me.him188.ani.debug2`.

The emulator image maps device state 1 to the WindowManager half-open posture and state 3 to flat.
The mapping was checked with `adb shell cmd overlay lookup android android:array/config_device_state_postures`.
Use `adb shell cmd device_state state 1` for tabletop and `adb shell cmd device_state state 3` for flat on this AVD.
`adb shell wm set-ignore-orientation-request false` allows the ordinary fullscreen landscape request to rotate the display.

| Action | Observed result | Screenshot |
| --- | --- | --- |
| Enter fullscreen in tabletop, pause, unfold, and return to tabletop | The complete paused frame follows the media pane in both directions; position remains 12:49 | [Tabletop](android-tabletop-paused.png), [flat](android-flat-paused.png) |
| Tap the lower blank area, tap again, and resume with the central play button | Controls hide and return; playback resumes in the upper pane | [Tabletop controls](android-tabletop-paused.png) |
| Toggle hover mode in player settings | Both enabled and disabled states are available; the disabled state also survived an app/emulator restart during verification | [Enabled](android-setting-on.png), [disabled](android-setting-off.png) |
| Disable hover mode and enter fullscreen | Ordinary landscape playback and overlay controls, 2636 × 1080 pixels | [Landscape](android-hover-off.png) |

No Android crash-buffer entries were observed during these checks.
The flat portrait overlay at 360 dp still wraps the English `Speed` label and crowds the trailing fullscreen button; see the flat screenshot. That narrow-layout case is not a clean visual pass.
Double-tap and all gesture combinations are not covered by the runtime result.

## Desktop

macOS 26.5.1, Apple M4, packaged Compose Desktop application, JBRSDK/JCEF 21.0.4.
Actual network video playback, pause/resume, right-arrow seeking, fullscreen entry, Escape exit, and window resizing were exercised.

| State | Screenshot |
| --- | --- |
| Playing with the sidebar and overlay controls at 1440 × 900 | [Window](desktop-window.png) |
| Paused video in native fullscreen at 1920 × 1080 | [Fullscreen](desktop-fullscreen.png) |
| Paused video after exiting fullscreen and resizing to 960 × 640 | [Small window](desktop-small.png) |

No regression was observed in these desktop paths. Windows, Linux, iOS, and physical foldable hardware were not exercised.

## Build and automated checks

- `:app:shared:video-player:desktopTest`: 54 tests, zero failures/errors/skips.
- `:app:shared:app-data:desktopTest --tests 'me.him188.ani.app.data.models.preference.VideoScaffoldConfigTest'`: 10 tests, zero failures/errors/skips.
- `:app:android:assembleDefaultDebug -Pani.android.abis=arm64-v8a`: successful.
- `:app:desktop:createDistributable`: successful; the resulting executable was used for the desktop checks.
- `git diff --check`: passed.

The runtime checks use `.agents/skills/android-ui-verify/scripts/droid.sh` and `.agents/skills/desktop-ui-verify/scripts/desk.sh`.
For a paused-frame regression check, load a video, pause in tabletop fullscreen, alternate tabletop and flat posture without seeking or resuming, and inspect both the complete video frame and the unchanged playback position.
