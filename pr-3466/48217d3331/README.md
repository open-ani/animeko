# PR #3466 iOS crash fix verification

Code commit: `48217d33316f8a0d96f6b454923604aa795f1933` (parent `fe00151bc4cf11ae188d59072a25045f2a0f2eb5`).
Verified on 2026-09-22 with the actual Debug simulator application built by `:app:ios:buildDebugForSimulator` (BUILD SUCCEEDED).

The iOS controller factory checks `AVPictureInPictureController.isPictureInPictureSupported()` and returns `NoOpPictureInPictureController` when unavailable. AVKit documents that its initializers return nil on unsupported devices; invoking that initializer through the Kotlin constructor bridge caused the previously reported NullPointerException.

## Runtime results

| Simulator | Open episode | Pause / resume | Leave and reopen episode |
| --- | --- | --- | --- |
| iPhone 16 Pro, iOS 18.2 | Pass | Pass: paused at 01:09.042, unchanged after 3 seconds; resumed to 01:13.750 | Pass, playing at 01:27.208 |
| iPhone 17 Pro, iOS 26.4 | Pass | Pass: paused at 01:42.167, unchanged after 3 seconds; resumed to 01:46.875 | Pass, playing at 01:49.542 |

Both simulator application processes reported PiP unsupported via LLDB; see [support query output](pip-support.txt). Neither console contained an uncaught Kotlin exception during these checks. Physical-device PiP entry is not covered by this verification.

The video is a local HTTP H.264/AAC moving test pattern with a frame counter, loaded through a real web-selector source and AVKit player. The anime subject is only the navigation entry. Verification used no signed-in account, disabled automatic episode marking, and restored the original simulator datastores afterward.

## Screenshots

| Runtime | Paused | Resumed | Reopened |
| --- | --- | --- | --- |
| iOS 18.2 | [Screenshot](ios18-paused.png) | [Screenshot](ios18-resumed.png) | [Screenshot](ios18-reopened.png) |
| iOS 26.4 | [Screenshot](ios26-paused.png) | [Screenshot](ios26-resumed.png) | [Screenshot](ios26-reopened.png) |

[Earlier crash report and stack trace](https://github.com/open-ani/animeko/pull/3466#issuecomment-5771669367).
