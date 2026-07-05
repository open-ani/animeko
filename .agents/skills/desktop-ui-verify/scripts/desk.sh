#!/usr/bin/env bash
# Desktop UI verification toolbox for the Animeko client (macOS).
# Wraps build/launch/screenshot/click/type/quit/logs so an agent can drive the desktop app.
# Run `desk.sh help` for the command list.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROC="${ANI_DESKTOP_PROCESS:-Ani}"
OUT_DIR="${ANI_UI_OUT_DIR:-${TMPDIR:-/tmp}/animeko-ui-verify}"

cmd="${1:-help}"
shift || true

window_origin() { # prints "x y" of window 1 of $PROC, or fails
  osascript <<OSA
tell application "System Events"
  tell process "$PROC"
    set p to position of window 1
    return (item 1 of p as text) & " " & (item 2 of p as text)
  end tell
end tell
OSA
}

window_id() { # prints the CGWindowID of $PROC's main window (match by pid: CGWindow owner name differs)
  local app_pid
  app_pid="$(pgrep -xn "$PROC" || true)"
  if [[ -n "$app_pid" ]]; then
    swift "$SCRIPT_DIR/find_window_id.swift" --pid "$app_pid" 2>/dev/null
  else
    swift "$SCRIPT_DIR/find_window_id.swift" "$PROC" 2>/dev/null
  fi
}

normalize_window() { # move to (40,80) and resize to $1x$2 so coordinates match screenshots
  osascript >/dev/null 2>&1 <<OSA || true
tell application "System Events"
  tell process "$PROC"
    set frontmost to true
    set position of window 1 to {40, 80}
    set size of window 1 to {${1:-1440}, ${2:-900}}
    delay 0.3
  end tell
end tell
OSA
}

case "$cmd" in
  build)
    "$SCRIPT_DIR/build_desktop_distributable.sh" "$@"
    ;;

  app)
    "$SCRIPT_DIR/find_desktop_app.sh" "$@"
    ;;

  launch)
    "$SCRIPT_DIR/launch_desktop_app.sh" "$@"
    ;;

  screenshot|shot)
    out="${1:-$OUT_DIR/desktop-$(date +%Y%m%d-%H%M%S).png}"
    "$SCRIPT_DIR/capture_macos_window.sh" "$out" "$PROC" "${2:-1440}" "${3:-900}" >&2
    echo "$out"
    ;;

  click)
    x="${1:?usage: desk.sh click <x> <y>   (window points; screenshot PNG px / 2)}"
    y="${2:?y}"
    read -r wx wy < <(window_origin) || { echo "cannot locate $PROC window (Accessibility permission?)" >&2; exit 1; }
    osascript <<OSA
tell application "System Events"
  tell process "$PROC"
    set frontmost to true
    click at {$((wx + x)), $((wy + y))}
  end tell
end tell
OSA
    echo "clicked window point ($x,$y) = screen ($((wx + x)),$((wy + y)))"
    ;;

  type)
    t="${1:?usage: desk.sh type <ascii-text>}"
    esc="${t//\\/\\\\}"
    esc="${esc//\"/\\\"}"
    osascript -e "tell application \"System Events\"
      tell process \"$PROC\" to set frontmost to true
      keystroke \"$esc\"
    end tell"
    echo "typed: $t"
    ;;

  key)
    name="${1:?usage: desk.sh key return|tab|esc|space|delete|up|down|left|right}"
    case "$name" in
      return) code=36 ;; tab) code=48 ;; esc) code=53 ;; space) code=49 ;;
      delete) code=51 ;; up) code=126 ;; down) code=125 ;; left) code=123 ;; right) code=124 ;;
      *) echo "unknown key: $name" >&2; exit 2 ;;
    esac
    osascript -e "tell application \"System Events\"
      tell process \"$PROC\" to set frontmost to true
      key code $code
    end tell"
    echo "pressed $name"
    ;;

  record)
    secs="${1:?usage: desk.sh record <seconds> [out.mov]}"
    out="${2:-$OUT_DIR/desktop-rec-$(date +%Y%m%d-%H%M%S).mov}"
    mkdir -p "$(dirname "$out")"
    normalize_window
    wid="$(window_id)"
    [[ -n "$wid" ]] || { echo "cannot find $PROC window (app running? Screen Recording permission?)" >&2; exit 1; }
    echo "Recording window $wid for ${secs}s..." >&2
    screencapture -x -v -V "$secs" -l "$wid" "$out"
    echo "$out"
    ;;

  record-start)
    out="${1:-$OUT_DIR/desktop-rec-$(date +%Y%m%d-%H%M%S).mov}"
    mkdir -p "$(dirname "$out")"
    normalize_window
    wid="$(window_id)"
    [[ -n "$wid" ]] || { echo "cannot find $PROC window (app running? Screen Recording permission?)" >&2; exit 1; }
    # redirect: the detached recorder must not inherit our stdout, or callers using
    # $(record-start) block until the recorder exits
    screencapture -x -v -l "$wid" "$out" >"$OUT_DIR/.desk-rec.log" 2>&1 &
    echo "$! $out" > "$OUT_DIR/.desk-rec"
    sleep 1
    echo "recording window $wid; stop with: desk.sh record-stop" >&2
    echo "$out"
    ;;

  record-stop)
    [[ -f "$OUT_DIR/.desk-rec" ]] || { echo "no recording in progress (use record-start first)" >&2; exit 1; }
    read -r rec_pid out < "$OUT_DIR/.desk-rec"
    rm -f "$OUT_DIR/.desk-rec"
    kill -INT "$rec_pid" 2>/dev/null || true
    for _ in 1 2 3 4 5 6 7 8 9 10; do kill -0 "$rec_pid" 2>/dev/null || break; sleep 0.5; done
    [[ -s "$out" ]] || { echo "recording produced no file at $out" >&2; exit 1; }
    echo "$out"
    ;;

  frames)
    python3 "$SCRIPT_DIR/frame_diff.py" "$@"
    ;;

  quit)
    "$SCRIPT_DIR/quit_desktop_app.sh" "${1:-$PROC}"
    ;;

  logs)
    n="${1:-100}"
    log="$(ls -t "$HOME/Library/Application Support/"*Ani*/logs/*.log 2>/dev/null | head -1 || true)"
    if [[ -z "$log" ]]; then # dev-run (:app:desktop:run) sandbox
      repo_root="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
      log="$(ls -t "$repo_root/app/desktop/test-sandbox/logs/"*.log 2>/dev/null | head -1 || true)"
    fi
    [[ -n "$log" ]] || { echo "no desktop log file found" >&2; exit 1; }
    echo "=== $log ===" >&2
    tail -n "$n" "$log"
    ;;

  help|*)
    cat <<'EOF'
Animeko desktop UI verification toolbox (macOS). Set ANI_DESKTOP_PROCESS to override the process name (default Ani).
click/type/key/screenshot need macOS Accessibility permission for the terminal running them.

  build [gradle args]         build the distributable .app (JCEF JBR auto-selected)
  app                         print the built .app path
  launch [app]                open the .app and wait for the process
  screenshot [out.png] [w h]  activate+resize window, capture it; prints PNG path (Read it to see the window)
  click <x> <y>               click at window-relative POINTS (screenshot PNG pixels / 2 on retina)
  type <ascii>                keystroke text into the frontmost window (ASCII only)
  key return|tab|esc|space|delete|up|down|left|right
  record <secs> [out.mov]     record the app window for N seconds (window-id capture; overlap-proof)
  record-start [out.mov]      start recording in background; interact, then record-stop
  record-stop                 stop recording, print the video path
  frames <video> [args]       analyze a recording for transient glitches (frame_diff.py; --help for options)
  quit [process]              quit the app (graceful, then pkill)
  logs [n]                    tail newest app log (Application Support .../logs, or test-sandbox for dev runs)
EOF
    ;;
esac
