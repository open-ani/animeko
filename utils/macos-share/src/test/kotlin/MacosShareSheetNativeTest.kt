/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.macos.share

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacosShareSheetNativeTest {
    @Test
    fun `native library loads exactly on macOS hosts`() {
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            // 本机构建出的 dylib 必须能装进来
            assertTrue(MacosShareSheetNative.isAvailable)
        } else {
            assertFalse(MacosShareSheetNative.isAvailable)
            assertFailsWith<UnsatisfiedLinkError> { MacosShareSheetNative.ensureLoaded() }
        }
    }
}
