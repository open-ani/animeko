#
# Copyright (C) 2024 OpenAni and contributors.
#
# 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
# Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
#
# https://github.com/open-ani/ani/blob/main/LICENSE
#

export ANIMEKO_DESKTOP_TEST_TASK="download-update-and-install"
export ANIMEKO_DESKTOP_TEST_ARGC=1
export ANIMEKO_DESKTOP_TEST_ARGV_0="https://d.myani.org/v4.0.0-release-checksum-2/ani-4.0.0-release-checksum-2-windows-x86_64.zip"
./Ani.exe
read -p "Press enter to continue"
