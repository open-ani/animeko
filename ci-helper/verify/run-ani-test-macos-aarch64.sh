#!/usr/bin/env bash

#
# Copyright (C) 2024 OpenAni and contributors.
#
# 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
# Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
#
# https://github.com/open-ani/ani/blob/main/LICENSE
#

# Proud work of o1 pro

set -e

if [ $# -ne 2 ]; then
  echo "Usage: $0 <path-to-dmg> <test-string>"
  exit 1
fi

DMG_PATH="$1"
TEST_STRING="$2"

echo "Step 1: Checking DMG path..."
if [ ! -f "$DMG_PATH" ]; then
  echo "Error: File not found at $DMG_PATH"
  exit 1
fi

rm -rf ./extracted_dmg

echo "Step 2: Extracting DMG with 7z..."
7z x "$DMG_PATH" -oextracted_dmg

echo "Step 3: Setting environment variable ANIMEKO_DESKTOP_TEST_TASK..."
export ANIMEKO_DESKTOP_TEST_TASK="$TEST_STRING"
echo "ANIMEKO_DESKTOP_TEST_TASK=$ANIMEKO_DESKTOP_TEST_TASK"

# NOTE: Adjust the Ani binary path to match the extracted structure
ANI_BINARY="extracted_dmg/Ani/Ani.app/Contents/MacOS/Ani"

if [ ! -f "$ANI_BINARY" ]; then
  echo "Error: Ani binary not found at $ANI_BINARY"
  ls -l "extracted_dmg/Ani/Ani.app/Contents/MacOS" || true
  exit 1
fi

echo "Making $ANI_BINARY executable..."
chmod +x "$ANI_BINARY"

echo "Running Ani..."
"$ANI_BINARY"
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
  echo "Error: Ani exited with a non-zero code: $EXIT_CODE"
  exit $EXIT_CODE
fi

echo "Ani exited successfully with code 0."
