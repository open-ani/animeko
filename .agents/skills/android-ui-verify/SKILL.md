---
name: android-ui-verify
description: Drive the Animeko Android app on an emulator like a real user to verify UI behavior — start emulator, build & install the app, then screenshot/tap/swipe/type in a loop and confirm results visually. Can also record the screen and diff frames to catch sub-second transient glitches (flicker, jump-and-revert bugs). Use when a change needs runtime Android UI evidence, when asked to "verify on Android", "test in the emulator", "simulate user interaction", or to reproduce an Android UI bug.
---

# Animeko Android UI Verification

Drive the real app on a real Android runtime. The core loop is: **act → screenshot → Read the PNG → decide next action**. Never claim a UI behavior works without a screenshot that shows it.

All commands below run from the `ani` repo root. The toolbox script is:

```bash
DROID=.agents/skills/android-ui-verify/scripts/droid.sh
```

## 1. Setup: emulator + app

```bash
$DROID emulator     # starts (or reuses) Pixel_8_Pro_API_35 headless, waits for boot, disables animations
```

- Must be an **API 35+** AVD: this checkout's debug APK has `minSdkVersion 35`. Default AVD is `Pixel_8_Pro_API_35`; override with `ANI_AVD=...`. `HEADED=1 $DROID emulator` shows the window if the user wants to watch.
- Boot takes ~30–90 s. Emulator log: `/tmp/animeko-emulator-<AVD>.log`.

Build and install (one Gradle task does both):

```bash
./gradlew :app:android:installDefaultDebug -Pani.android.abis=arm64-v8a
```

- Expect `Installed on 1 device.` A full build can take several minutes; if an up-to-date APK already exists under `app/android/build/outputs/apk/default/debug/`, `$DROID install` adb-installs it directly.
- Installed debug package: `me.him188.ani.debug` (launcher activity `me.him188.ani.android.activity.MainActivity`).
- If validation needs a real API server, start the local one (see `../ani-api-server` skill `animeko-server-local-test-server`) and build with `-Pani.api.server=http://10.0.2.2:4394` — `10.0.2.2` is the emulator's alias for the host's localhost.

## 2. The interaction loop

```bash
$DROID launch                 # or: relaunch (force-stop + start), clear (wipe data → first-run onboarding)
sleep 8                       # first launch is slow; later navigations need 1–3 s
$DROID screenshot             # prints a PNG path — ALWAYS Read that file to actually see the screen
```

To interact, get coordinates from the UI hierarchy, not by guessing pixels off the screenshot:

```bash
$DROID find 追番              # searches text/content-desc/resource-id, prints tap-ready center=(x,y)px + dp size
$DROID tree                   # indented Compose semantics tree with dp sizes/positions (--all includes plain containers)
$DROID tap 540 1200
$DROID swipe 540 2000 540 800 300      # scroll up (drag content upward)
$DROID text hello                      # ASCII only — `adb input text` cannot type CJK; avoid tests requiring Chinese IME input
$DROID back                            # keyevent 4; also: key <code>, home
$DROID ui                              # raw hierarchy XML path, grep it when tree/find aren't enough
```

`tree` and `find` read the app-level **Compose semantics tree** (Compose projects it onto the accessibility tree, which uiautomator dumps). What it can and cannot tell you:

- **Visible**: all semantic text, `contentDescription`, exact on-screen bounds (printed both as dp — `WxHdp@(x,y)dp` — and px center), clickable/scrollable/selected/checked/disabled state.
- **Not visible**: composable function names, modifier chains, padding/arrangement parameters, colors. Colors and visual style are verified from screenshots; for composable-level internals use Android Studio's Layout Inspector manually.
- The repo's `Modifier.testTag`s DO show up as `resource-id` in debug builds (e.g. `find buttonNextStep` → `id=buttonNextStep` in the onboarding wizard) — MainActivity enables `testTagsAsResourceId` when `BuildConfig.DEBUG`. Prefer testTag ids over visible text when they exist; note they only cover ~44 spots (wizard nav, video player controls).
- Many container nodes are unlabeled `android.view.View`; `tree` hides the purely structural ones by default.
- After every action that should change the screen: `sleep 1-3` (longer for network content), then `screenshot` + Read, and compare against what you expected. If the screen didn't change, re-check coordinates with `find` before retrying.
- App misbehaving? `$DROID logcat 150` (app log tail) and `$DROID crashes` (crash buffer) before drawing conclusions.

## 3. Responsive / wide-screen checks

```bash
$DROID display wide      # 2560x1600 @ 320dpi ≈ PC-like 1280x800dp; also: tablet, phone, reset
$DROID relaunch && sleep 5
$DROID screenshot
```

Report the active `wm size`/`wm density` with such screenshots. This validates responsive Compose layout on Android; it does not validate desktop-only code paths (for those, see `.agents/skills/desktop-ui-verify`).

## 4. Figma design comparison

When asked whether a screen matches its Figma design:

1. Get the design reference. If a Figma MCP server is available, call `get_screenshot` on the frame/node URL for the visual, and `get_design_context` (or `get_variable_defs`) for exact colors, spacing, radii, and typography. Otherwise ask the user for an exported PNG of the frame.
2. Match the runtime viewport to the frame before capturing: pick the closest display preset (`$DROID display phone|tablet|wide`), `relaunch`, and navigate to the target screen.
3. `$DROID screenshot`, then Read **both** images and compare. Do NOT expect a pixel-perfect diff — status bar, fonts, and rendering always differ. Compare structurally:
   - **Layout & sizes**: `$DROID tree` prints the runtime Compose semantics tree with dp sizes/positions — Figma values are already in dp, so element sizes, spacing, and ordering can be checked numerically against the design, no unit conversion needed.
   - **Content**: exact texts. `$DROID find <text>` proves presence and gives that element's dp size + px center.
   - **Style**: colors, corner radii, icon shapes — judge against `get_design_context` values plus the screenshots (these are NOT in the semantics tree).
4. Report a per-item verdict list (match / mismatch + what differs), citing both image paths.

## 5. Logs

```bash
$DROID logcat 150     # tail of the app process's logcat (falls back to crash buffer if not running)
$DROID crashes        # crash buffer only (`logcat -b crash`)
```

Use these whenever behavior looks wrong before drawing conclusions, and attach relevant lines to findings.

## 6. Screen recording — catching transient glitches

Some bugs are visible for well under a second: a seek bar that jumps to a wrong position and back, a flash of empty/mis-styled content, a one-frame layout shift during a transition. Screenshots will miss them — record the screen and diff the frames instead:

```bash
REC=$($DROID record-start)     # starts recording in background, prints the output .mp4 path
$DROID tap ... / swipe ...     # do the interaction that should (or should not) glitch
$DROID record-stop             # stops, pulls, prints the video path
$DROID frames "$REC"           # scans all frames, prints change events + exported images
```

`frames` (= `scripts/frame_diff.py`, needs `ffmpeg` on PATH) scores every consecutive-frame difference, groups spikes into events, and per event exports a **contact sheet** (frames tiled left→right, top→bottom — Read it to see the whole sequence at a glance) plus full-res frames named by video timestamp. How to work with it:

- **Recordings are VFR**: frames are written only when the screen changes, so a gap in timestamps *proves* the screen was static there. Any frame = something changed.
- **Judge events against your own actions.** Count and order your taps: an event when nothing should have changed, or a change-then-revert pair in quick succession, is the glitch. Timestamps are video-relative (recording starts ~1–2 s before `record-start` returns), so correlate by order/spacing, not wall clock.
- Zoom into a moment with `$DROID frames <video> --around <t> --window 0.5`; print the full per-frame score timeline with `--list`.
- **Small elements barely move the frame-wide score** (a progress bar is ~2 % of pixels). Crop to the region of interest: `--crop W:H:X:Y` in *video* pixels, and/or lower `--threshold` (default 0.01, try 0.003).
- Recordings are made at **half display resolution** (`--size` display/2, 16-aligned — the emulator encoder rejects full size, and its unsized fallback produces a broken 1-frame video). So video pixel ≈ screenshot/tap pixel ÷ 2.
- `screenrecord` bakes the display's rotation in at start: if the app rotates mid-recording (e.g. entering the fullscreen player), the video letterboxes. Record within one orientation; `record <seconds>` (blocking) suits no-interaction captures like a launch animation.

## 7. Verify & report

- For each checked behavior state: the action taken, the expected result, and the screenshot path proving it.
- Screenshots land in `${TMPDIR}/animeko-ui-verify/` by default; pass an explicit path to keep them (e.g. under `../reaction-screenshots/<task>/`).
- A blank/white screenshot right after launch usually means the app is still loading — wait and retake before concluding breakage.
- Verification fails ≠ tooling fails: report app crashes (with `crashes` output) as findings, not as skill errors.

## 8. Cleanup

Unless the user asked to keep the session running:

```bash
$DROID display reset
$DROID kill-emulator
```

## Instrumented tests on the emulator

Application module:

```bash
./gradlew :app:android:connectedDefaultDebugAndroidTest
```

Kotlin Multiplatform Android library modules:

```bash
./gradlew :app:shared:app-data:compileAndroidDeviceTest
./gradlew :app:shared:app-data:connectedAndroidDeviceTest
```

Narrow to one class with runner args (note: the full device-test APK is still packaged, so dex/package failures elsewhere can still fail the task):

```bash
./gradlew :app:shared:app-data:connectedAndroidDeviceTest \
  -Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.TestClass>
```

Known blocker (still present as of 2026-07-05): `:app:shared:app-data:connectedAndroidDeviceTest` fails at dexing because some `commonTest` backtick method names are invalid dex names, e.g. `OfflineDownloadMediaResolverTest`'s `` `resolve - without fallback, engine failure surfaces as MediaResolutionException` ``. Treat as a project test-compatibility blocker, not an emulator failure.

## Environment facts

- Android SDK: `$ANDROID_HOME`, falling back to `~/Library/Android/sdk`. `adb` is on PATH; `emulator` is not (script handles it).
- Existing AVDs on this machine: `Pixel_2`, `Pixel_4a_API_30` (too old, minSdk 35 blocks install), `Pixel_8_Pro_API_35`.
- Multiple devices connected → set `ANDROID_SERIAL=emulator-5554` (or pass `-s` to raw adb calls).
- `$DROID help` lists every subcommand.
