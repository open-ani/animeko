/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopUpdatePreparationUrlsTest {
    @Test
    fun `macos downloads the zip published next to the dmg`() {
        assertEquals(
            listOf(
                "https://d2.myani.org/v6.4.0/ani-6.4.0-macos-aarch64.zip",
                "https://ghfast.top/https://github.com/open-ani/ani/releases/download/v6.4.0/ani-6.4.0-macos-aarch64.zip",
                "https://d2.myani.org/v6.4.0/ani-6.4.0-macos-x86_64.zip",
            ),
            MacOSUpdateInstaller.getUpdatePreparationUrls(
                listOf(
                    "https://d2.myani.org/v6.4.0/ani-6.4.0-macos-aarch64.dmg",
                    "https://ghfast.top/https://github.com/open-ani/ani/releases/download/v6.4.0/ani-6.4.0-macos-aarch64.DMG",
                    "https://d2.myani.org/v6.4.0/ani-6.4.0-macos-x86_64.zip",
                ),
            ),
        )
    }

    @Test
    fun `windows keeps the package urls`() {
        val urls = listOf("https://d2.myani.org/v6.4.0/ani-6.4.0-windows-x86_64.zip")
        assertEquals(urls, WindowsUpdateInstaller.getUpdatePreparationUrls(urls))
    }
}
