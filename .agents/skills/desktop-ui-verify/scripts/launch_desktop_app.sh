#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
app="${1:-}"

if [[ -z "$app" ]]; then
  app="$("$script_dir/find_desktop_app.sh")"
fi

if [[ ! -d "$app" ]]; then
  echo "App bundle not found: $app" >&2
  exit 1
fi

bundle_id="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$app/Contents/Info.plist" 2>/dev/null || true)"
exe="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$app/Contents/Info.plist" 2>/dev/null || true)"

echo "Launching: $app"
[[ -n "$bundle_id" ]] && echo "Bundle ID: $bundle_id"
[[ -n "$exe" ]] && echo "Executable: $exe"

open -n "$app"
sleep "${ANI_DESKTOP_LAUNCH_WAIT_SECONDS:-8}"

if [[ -n "$exe" ]]; then
  pgrep -fl "$app/Contents/MacOS/$exe|/Contents/MacOS/$exe|$exe" || true
fi
