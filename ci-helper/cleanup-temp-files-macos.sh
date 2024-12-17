#!/bin/zsh

#
# Copyright (C) 2024 OpenAni and contributors.
#
# 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
# Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
#
# https://github.com/open-ani/ani/blob/main/LICENSE
#

local TARGET_DIR

if [[ -n "$TMPDIR" ]]; then
  echo "env TMPDIR exists."
  TARGET_DIR="$TMPDIR"
else
  TARGET_DIR="/private/var/folders/fv/b5h2b9f577q7z5j0r5n8n6g40000gp/T"
  echo "env TMPDIR is not exist. Using $TARGET_DIR"
fi

# clear "debuginfo.knd1145141919810.tmp"
# strings.knt1145141919810.tmp
find $TARGET_DIR -type f -name "*.kn*.tmp" -exec rm {} + -print 2>/dev/null | wc -l