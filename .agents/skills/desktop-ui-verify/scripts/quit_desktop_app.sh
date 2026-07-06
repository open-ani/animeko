#!/usr/bin/env bash
set -euo pipefail

process="${1:-Ani}"

osascript -e "tell application \"$process\" to quit" >/dev/null 2>&1 || true
sleep 2

if pgrep -x "$process" >/dev/null 2>&1; then
  pkill -x "$process" || true
fi

if pgrep -x "$process" >/dev/null 2>&1; then
  echo "$process is still running" >&2
  pgrep -fl "$process" >&2 || true
  exit 1
fi

echo "$process is not running."
