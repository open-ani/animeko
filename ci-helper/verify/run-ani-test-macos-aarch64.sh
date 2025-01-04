#!/usr/bin/env bash

#
# Copyright (C) 2024 OpenAni and contributors.
#
# 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
# Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
#
# https://github.com/open-ani/ani/blob/main/LICENSE
#

# This script either:
#   - extracts a DMG directly, OR
#   - extracts a ZIP that contains a DMG, then extracts that DMG.
#
# Finally, it sets ANIMEKO_DESKTOP_TEST_TASK and runs Ani.app.
#
# USAGE:
#   ./run-ani-test-macos-aarch64.sh <path-to-dmg-or-zip> <test-string>
#
# Example:
#   ./run-ani-test-macos-aarch64.sh /path/to/file.zip "SOME_TEST"
#   ./run-ani-test-macos-aarch64.sh /path/to/file.dmg "SOME_TEST"

set -e  # Exit immediately on error

# --- Step 0: Check arguments ---
if [ $# -ne 2 ]; then
  echo "Usage: $0 <path-to-dmg-or-zip> <test-string>"
  exit 1
fi

INPUT_PATH="$1"
TEST_STRING="$2"

# --- Step 1: Cleanup old extraction directories ---
echo "Step 1: Cleaning up old extraction folders..."
rm -rf extracted_zip extracted_dmg

# --- Step 2: Distinguish between ZIP vs. DMG ---
FILE_BASENAME="$(basename "$INPUT_PATH")"
FILE_EXTENSION="${FILE_BASENAME##*.}"

if [ ! -f "$INPUT_PATH" ]; then
  echo "Error: No file found at $INPUT_PATH"
  exit 1
fi

DMG_PATH=""

if [ "$FILE_EXTENSION" == "zip" ]; then
  echo "Step 2: Detected a ZIP file. Extracting..."

  # Create a folder to unzip into
  mkdir -p extracted_zip
  # You can use 'unzip' or '7z' to extract the zip
  7z x "$INPUT_PATH" -oextracted_zip
  
  # Find the single DMG inside extracted_zip
  FOUND_DMGS=($(find extracted_zip -type f -iname '*.dmg'))
  
  if [ ${#FOUND_DMGS[@]} -eq 0 ]; then
    echo "Error: No .dmg file found inside the zip!"
    exit 1
  elif [ ${#FOUND_DMGS[@]} -gt 1 ]; then
    echo "Warning: More than one .dmg file found. Using the first one."
  fi

  DMG_PATH="${FOUND_DMGS[0]}"
  echo "Found DMG at: $DMG_PATH"

elif [ "$FILE_EXTENSION" == "dmg" ]; then
  echo "Step 2: Detected a DMG file."
  DMG_PATH="$INPUT_PATH"

else
  echo "Error: The file must be either .zip or .dmg. Detected extension: $FILE_EXTENSION"
  exit 1
fi

# --- Step 3: Extract the DMG contents with 7z ---
echo "Step 3: Extracting DMG with 7z..."
mkdir -p extracted_dmg
7z x "$DMG_PATH" -oextracted_dmg

# --- Step 4: Set environment variable for the test ---
echo "Step 4: Setting ANIMEKO_DESKTOP_TEST_TASK to '$TEST_STRING'..."
export ANIMEKO_DESKTOP_TEST_TASK="$TEST_STRING"
export ANI_DISALLOW_PROJECT_DIRECTORIES_FALLBACK="true"

# The final Ani.app is likely found at:
#   extracted_dmg/Ani/Ani.app/Contents/MacOS/Ani
# or
#   extracted_dmg/Ani.app/Contents/MacOS/Ani
# Adjust as necessary based on your actual structure.
ANI_BINARY="extracted_dmg/Ani/Ani.app/Contents/MacOS/Ani"
if [ ! -f "$ANI_BINARY" ]; then
  # Fallback check in case there's no top-level "Ani" folder:
  ANI_BINARY_FALLBACK="extracted_dmg/Ani.app/Contents/MacOS/Ani"
  if [ -f "$ANI_BINARY_FALLBACK" ]; then
    ANI_BINARY="$ANI_BINARY_FALLBACK"
  else
    echo "Error: Could not find Ani binary at either:"
    echo "       $ANI_BINARY"
    echo "       $ANI_BINARY_FALLBACK"
    ls -R extracted_dmg || true
    exit 1
  fi
fi

echo "Ani binary found at: $ANI_BINARY"
echo "Making it executable..."
chmod +x "$ANI_BINARY"

# --- Step 5: Run Ani.app and capture exit code ---
echo "Step 5: Running Ani..."
"$ANI_BINARY"
EXIT_CODE=$?

if [ "$EXIT_CODE" -ne 0 ]; then
  echo "Error: Ani exited with non-zero code: $EXIT_CODE"
  exit $EXIT_CODE
fi

echo "Success: Ani exited normally (code 0)."
exit 0
