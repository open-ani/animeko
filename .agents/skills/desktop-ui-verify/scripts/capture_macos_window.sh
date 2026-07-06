#!/usr/bin/env bash
set -euo pipefail

out="${1:?usage: capture_macos_window.sh /absolute/path/to/output.png [process-name] [width] [height]}"
process="${2:-Ani}"
width="${3:-1440}"
height="${4:-900}"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

mkdir -p "$(dirname "$out")"

# Normalize the window position/size so screenshot coordinates are predictable.
# Requires Accessibility permission; failure here is non-fatal (capture still works by window id).
rect="$(osascript 2>/dev/null <<OSA || true
tell application "System Events"
  if not (exists process "$process") then error "process not found: $process"
  tell process "$process"
    set frontmost to true
    repeat 30 times
      if exists window 1 then exit repeat
      delay 0.2
    end repeat
    if not (exists window 1) then error "window not found: $process"
    set position of window 1 to {40, 80}
    set size of window 1 to {$width, $height}
    delay 0.5
    set p to position of window 1
    set s to size of window 1
    return (item 1 of p as text) & "," & (item 2 of p as text) & "," & (item 1 of s as text) & "," & (item 2 of s as text)
  end tell
end tell
OSA
)"

# Prefer capture by window id: unlike rect capture, it shows the target window's own
# content even when other windows overlap it (the desktop may be in active use).
# Match by pid: the CGWindow owner name is the app display name (e.g. "Animeko"), not
# the System Events process name (e.g. "Ani").
app_pid="$(pgrep -xn "$process" || true)"
if [[ -n "$app_pid" ]]; then
  window_id="$(swift "$script_dir/find_window_id.swift" --pid "$app_pid" 2>/dev/null || true)"
else
  window_id="$(swift "$script_dir/find_window_id.swift" "$process" 2>/dev/null || true)"
fi

if [[ -n "$window_id" ]]; then
  echo "Capturing window id $window_id (rect: ${rect:-unknown})"
  screencapture -x -o -l "$window_id" "$out"
elif [[ -n "$rect" ]]; then
  echo "No window id found; capturing screen rectangle: $rect" >&2
  screencapture -x -R "$rect" "$out"
else
  echo "Could not locate the $process window; capturing full screen instead." >&2
  screencapture -x "$out"
fi

file "$out"
ls -lh "$out"
