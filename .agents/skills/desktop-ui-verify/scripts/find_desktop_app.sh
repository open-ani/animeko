#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
default_repo="$(cd "$script_dir/../../../.." && pwd)"
repo="${ANIMEKO_REPO:-$default_repo}"
mode="${1:-}"
root="$repo/app/desktop/build/compose/binaries"

if [[ ! -d "$root" ]]; then
  echo "Desktop binaries directory does not exist: $root" >&2
  echo "Run scripts/build_desktop_distributable.sh first." >&2
  exit 1
fi

# macOS ships bash 3.2, which has no mapfile
apps=()
while IFS= read -r line; do
  apps+=("$line")
done < <(find "$root" -type d -name "*.app" -prune | sort)

if [[ "${#apps[@]}" -eq 0 ]]; then
  echo "No .app bundle found under $root" >&2
  echo "Run scripts/build_desktop_distributable.sh first." >&2
  exit 1
fi

if [[ "$mode" == "--all" ]]; then
  printf "%s\n" "${apps[@]}"
else
  printf "%s\n" "${apps[0]}"
fi
