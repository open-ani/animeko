/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun `android tv is described as Android TV and keeps the Android id`() {
        val platform = Platform.Android(Arch.ARMV8A, isTv = true)
        assertEquals("Android", platform.name)
        assertEquals("Android TV arm64-v8a", platform.toString())
        assertTrue(platform.isAndroid())
        assertTrue(platform.isAndroidTv())
    }

    @Test
    fun `android phone is not tv`() {
        val platform = Platform.Android(Arch.ARMV7A)
        assertEquals("Android armeabi-v7a", platform.toString())
        assertFalse(platform.isAndroidTv())
    }

    @Test
    fun `other platforms are not tv`() {
        assertEquals("Windows x86_64", Platform.Windows(Arch.X86_64).toString())
        assertFalse(Platform.Windows(Arch.X86_64).isAndroidTv())
        assertFalse(Platform.Ios.isAndroidTv())
    }
}
