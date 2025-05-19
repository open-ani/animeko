#!/usr/bin/env bash
#
# Copyright (C) 2024-2025 OpenAni and contributors.
#
# 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
# Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
#
# https://github.com/open-ani/ani/blob/main/LICENSE
#

# Usage: ./set-jvmargs.sh "<new JVM args>" [gradle.properties path]

set -euo pipefail

new_val="$1"
prop_file="${2:-gradle.properties}"

# If the key exists, replace its value; otherwise append the key-value pair.
if grep -q '^org\.gradle\.jvmargs=' "$prop_file"; then
  sed -i.bak -E "s|^org\.gradle\.jvmargs=.*|org.gradle.jvmargs=${new_val}|" "$prop_file"
else
  echo "org.gradle.jvmargs=${new_val}" >> "$prop_file"
fi
