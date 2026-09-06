---
name: desktop-ui-verify
description: Validate the Animeko desktop (Compose Desktop) app by building and launching the real executable, then screenshot/click/type against the macOS window. Can also record the window and diff frames to catch sub-second transient glitches (flicker, jump-and-revert bugs). Use for desktop-only code paths, JCEF/browser behavior, VLC/video playback integration, native libraries, desktop packaging, window chrome, or when asked for PC screenshots/evidence. For Android emulator verification (interactive taps/swipes, Android screenshots, wide-screen simulation) use .agents/skills/android-ui-verify instead.
---

# Animeko Desktop UI Verification

Use this skill from the `ani` repo root when a change needs **desktop** runtime evidence. Anything Android — including PC-style wide-screen layout checks, which run fine on an Android emulator — belongs to `.agents/skills/android-ui-verify`; only desktop-only code paths (Compose Desktop behavior, packaging, JCEF, VLC/native libraries, window chrome) need this skill.

The toolbox script is:

```bash
DESK=.agents/skills/desktop-ui-verify/scripts/desk.sh
```

The core loop mirrors the Android skill: **act → screenshot → Read the PNG → decide next action**. Never claim desktop behavior works without a captured window screenshot.

## 1. Build

Two launch modes:

- Dev launch: `./gradlew :app:desktop:run` starts the app from Gradle with `app/desktop/test-sandbox` as working directory. Quick, but misses packaging problems.
- Executable validation (preferred for evidence): build and launch the packaged `.app` — catches runtime-image/JCEF/native-library packaging problems that `run` misses.

```bash
./gradlew :app:shared:compileKotlinDesktop   # cheap compile-only gate first
$DESK build                                  # createDistributable with a JCEF-capable JBR
```

- `build` requires a JBR containing JCEF (`jmods/jcef.jmod` + `lib/libjcef.dylib`); `find_jcef_jbr.sh` auto-selects one and exports it as `ANI_COMPOSE_JAVA_HOME`. A full first build takes many minutes — run it in the background.
- It builds with `-Pani.api.server=http://localhost:4394`; for real API data start the repo-local server first (see `../ani-api-server` skill `animeko-server-local-test-server`) — desktop reaches it as plain `localhost`.
- When backgrounding Gradle, do NOT pipe to `tail` — the pipe replaces the build's exit code with tail's, so failures read as success. Use `-q` (quiet) and read the full output file, or check the artifact (`$DESK app`, mtime) before trusting a "successful" build.
- On macOS the packed app needs the JBR's `Contents/Frameworks` (CEF + jcef helpers) copied next to the packed runtime's `Home`; `app/desktop/build.gradle.kts` does this for both `createDistributable` and `createReleaseDistributable` (fixed 2026-07-05 — before that, non-release distributables crashed ~4 s after launch with a SIGSEGV in `libjcef.dylib FindClass`).

## 2. Launch and interact

```bash
$DESK launch                # opens the newest built .app, waits, prints the process
$DESK screenshot            # activates + resizes window to 1440x900, prints PNG path — ALWAYS Read it
$DESK click 300 200         # window-relative POINTS — on retina, screenshot PNG pixel / 2
$DESK type hello            # ASCII keystrokes into the frontmost window
$DESK key return            # also: tab, esc, space, delete, arrows
$DESK quit
```

### Injected input (preferred): no cursor move, no focus steal

`agent-attach` puts a tiny `-javaagent` (source: `scripts/input-agent/InputAgent.java`) into the .app's jpackage cfg; it injects synthetic AWT events in-process via a loopback socket — the system cursor never moves, the app need not be frontmost, and no Accessibility permission is involved. Validated end-to-end on the real Ani Compose window 2026-07-10 (clicks closed a dialog, switched nav, opened search; `type` inserted text into the search TextField — all while another app was frontmost). Injected events land on the Skia canvas (`inject` replies `-> org.jetbrains.skiko.SkiaLayer$1`) and Compose routes them internally. Ani draws its own window chrome, so `info` reports content == frame:
**inject coordinates = window points = screenshot PNG px / 2, no title-bar offset**.

```bash
$DESK agent-attach          # once per built .app, BEFORE launch
$DESK launch
$DESK inject info           # window bounds + content-area origin/size
$DESK inject click 300 200  # window-CONTENT points = window points minus title bar (~28pt);
                            # screenshot PNG pixel / 2, then subtract (content.y - frame.y)
$DESK inject type hello     # goes to focus owner, else last injected-click target
$DESK inject key 10         # AWT keycodes: 10=Enter, 27=Esc, 9=Tab, 37-40=arrows
$DESK inject wheel 500 300 3  # mouse wheel / trackpad scroll at (500,300): dy=3 (down); `wheel x y dy dx`,
                              # append `ctrl` for Ctrl+wheel
$DESK inject resize 1440 900 # resize the target frame (window points) without Accessibility
$DESK inject windows        # list visible frames; * marks the focused one
$DESK inject window Image   # route following commands to the frame whose title contains "Image"
                            # (secondary windows such as the image viewer); `window -` clears it
```

- `inject type` goes to Compose's internal focus, which follows injected clicks — **click INTO the
  text field
  first** (opening a screen is not enough: after opening search, typing did nothing until the field itself was clicked).
- For window-id screenshots WITHOUT activating/normalizing the window (keeps the app in the background), capture directly: `swift scripts/find_window_id.swift --pid $(pgrep -xn Ani)` then `screencapture -x -l <id> out.png`. These PNGs include a shadow border (e.g. 3016x1936 for a 1440x900 window — subtract (PNG_w−2880)/2 = 68px per side before converting px/2 → points).
- Fall back to `click`/`type`/cliclick below only if injection misbehaves. Injected hover (`inject move`) drives Compose hover states from event coordinates, but anything reading the REAL cursor position will disagree.

- **Screenshots capture the window by CGWindowID** (via `find_window_id.swift`), so they show the app's own content even when other windows overlap it — the user may be actively using the desktop. The AppleScript step first normalizes the window to position (40,80) size 1440x900 points.
- **Coordinate mapping**: retina PNGs are 2x, so `window point = PNG pixel / 2` (1440x900 window → 2880x1800 PNG; sanity-check your math against that).
- **Click immediately after a fresh screenshot**: toasts and update dialogs auto-dismiss, and a click computed from a stale screenshot lands on whatever is underneath. `click` prints the AX element it hit (e.g. `scroll area 1 of group 1 …`) — read it to confirm the intended target.
- `click`/`type`/`key` and the window-resize step drive macOS **System Events and require Accessibility permission**; `screencapture` needs **Screen Recording permission** (both verified granted for this terminal). On failure report the permission gap instead of concluding app breakage.
- **AppleScript `click at` can silently no-op** on screens whose Compose accessibility tree is shallow (observed 2026-07-06 on the subject detail page: explore-page card clicks worked, then every detail-page click — buttons, back arrow — did nothing, with the AX hit always reporting the same generic `UI element 1 of group 1 …`). Fall back to real CGEvents: `cliclick c:<screenX>,<screenY>` (Homebrew-installed; screen = window point + window origin (40,80)) after `set frontmost to true` — clicked the same button first try.
- **cliclick is a GLOBAL click — it hits whatever is topmost at that point.** If the user switches to another app mid-verification, every cliclick lands in *their* app (observed 2026-07-06: several clicks went into the user's Chrome session before a full-desktop capture revealed Ani wasn't frontmost). Immediately before each cliclick: `set frontmost to true`, then VERIFY with `osascript -e 'tell application "System Events" to get name of first process whose frontmost is true'` == `Ani`; if not, stop clicking and report. Also keep the window fully on-screen first (this display is 1728pt wide; a 1440-wide window moved to x=600 puts its right side off-screen where clicks silently miss).
- **The screenshot can capture the WRONG window.** The process may own several CGWindows (e.g. a stale 1004x688 popup-host window stacked over the real 1440x900 main window, both at (40,80)); the frontmost-match then screenshots the static one, so the UI looks frozen and clicks look like no-ops even though they worked (observed 2026-07-06: playback had actually started while screenshots kept showing the explore page). Sanity-check every capture: PNG pixel size must equal AX window size × 2 (+136px shadow for `screencapture -l`); on mismatch, enumerate CGWindows by owner `Animeko` (swift CGWindowListCopyWindowInfo) and capture the right id via `screencapture -l<id>`.
- There is no semantics/hierarchy dump on desktop (unlike Android's `droid.sh tree`); element targeting is visual. Prefer stable landmarks (window corners, sidebar order) and re-screenshot after every action.
- Wait 1–3 s after actions; JCEF/browser content and network data need longer.

## 3. Logs

```bash
$DESK logs 150     # newest app log: ~/Library/Application Support/*Ani*/logs/, or app/desktop/test-sandbox/logs/ for dev runs
```

The app logs `dataDir`/`logsDir` on startup and installs an uncaught-exception handler that logs `!!!ANI FATAL EXCEPTION!!!` — grep for that when the app dies.

## 4. Screen recording — catching transient glitches

For bugs visible under a second (a flash of wrong state, a one-frame layout jump, loading flicker), screenshots are not enough — record the window and diff the frames:

```bash
REC=$($DESK record-start)     # starts recording in background, prints the output .mov path
$DESK click ... / key ...     # do the interaction that should (or should not) glitch
$DESK record-stop             # stops (SIGINT-finalizes), prints the video path
$DESK frames "$REC"           # scans all frames, prints change events + exported images
```

`frames` (= `scripts/frame_diff.py`, a symlink to the android skill's copy; needs `ffmpeg` on PATH) scores every consecutive-frame difference, groups spikes into events, and per event exports a **contact sheet** (frames tiled left→right, top→bottom — Read it to see the whole sequence at a glance) plus full-res frames named by video timestamp. How to work with it:

- Recording captures the window **by CGWindowID** like screenshots, so it stays correct when other windows overlap. It is VFR at up to ~60 fps: frames are written only when the window content changes, so a timestamp gap *proves* the UI was static there.
- **Judge events against your own actions**: an event when nothing should have changed, or a change-then-revert pair, is the glitch. Timestamps are video-relative (capture starts before `record-start` returns — the window-id lookup takes a few seconds), so correlate by order/spacing, not wall clock. Mouse hovers create real events too (hover highlights, carousel arrows) — keep the pointer still when it shouldn't participate.
- Zoom into a moment with `$DESK frames <video> --around <t> --window 0.5`; print every frame's score with `--list`.
- **The video includes the window shadow**: a 1440x900 window records as 3016x1936, content at offset (68,68) — i.e. video px = screenshot px + 68. `--crop 2880:1800:68:68` trims to exactly the screenshot framing; for a sub-region use `--crop W:H:(x+68):(y+68)` with screenshot-pixel values.
- Small elements barely move the frame-wide score — crop to the region of interest and/or lower `--threshold` (default 0.01, try 0.003).
- `record <seconds>` is the blocking variant for no-interaction captures (launch animations, video playback checks).

## 5. Verify & report

- For each checked behavior state: the action, the expected result, and the screenshot path proving it.
- If the build fails, report the exact failing Gradle task and the first actionable compiler/jlink error.
- Do not claim desktop UI validation succeeded without a launched executable and a captured screenshot.
- Clean up: `$DESK quit` unless the user wants the app kept open.

## Desktop test tasks

```bash
./gradlew :app:desktop:test
./gradlew :app:shared:compileKotlinDesktop
```

Other desktop Gradle tasks observed here: `:app:desktop:runDistributable`, `:app:desktop:packageDistributionForCurrentOS`, and release variants. For Compose screenshot assertions in tests, see `utils/ui-testing`: `SemanticsNodeInteraction.assertScreenshot(expectedResource)` is implemented for Skiko-backed (desktop) targets.

## Environment facts

- Desktop main class: `me.him188.ani.app.desktop.AniDesktop`; System Events process name `Ani` (override with `ANI_DESKTOP_PROCESS`), but the CGWindow **owner name is `Animeko`** — window lookups therefore match by PID, not name.
- App data dir: `AppFolderResolver` with app name `Ani` (`Ani-debug` in debug builds), observed at `~/Library/Application Support/me.Him188.Ani/`.
- Healthy launch spawns JCEF helper processes (`jcef Helper (GPU)` etc.) next to the main process — `pgrep -fl "Ani.app/Contents/MacOS"` is a quick liveness check.
- `app/desktop/build.gradle.kts` supports `ANI_COMPOSE_JAVA_HOME`; the JBR must contain `jmods/jcef.jmod` and `lib/libjcef.dylib`.
- On this machine, `/Users/him188/Library/Java/JavaVirtualMachines/jbrsdk_jcef-21.0.4/Contents/Home` was verified end-to-end on 2026-07-05 (packages AND runs).
- macOS ships bash 3.2 — scripts here must stay bash-3.2 compatible (no `mapfile`, no `declare -A`).
- The May-2026 desktop compile blocker (`CommonKoinModule.kt` ApiInvoker type mismatch) was verified FIXED on 2026-07-05: `:app:shared:application:compileKotlinDesktop` passes.
