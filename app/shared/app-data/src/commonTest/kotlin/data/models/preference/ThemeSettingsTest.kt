/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeSettingsTest {
    /**
     * 回归: [ThemeSettings.effectiveUiScale] 曾用 companion 里的 `val UI_SCALE_RANGE` 做 clamp,
     * 而 `Default` 在同一个 companion 里排在它前面 —— 类初始化时 range 还是 null, 构造 `Default`
     * 当场 NPE. `Default` 在 Koin 启动阶段就被触碰, 于是应用一启动就崩.
     */
    @Test
    fun `touching Default does not throw`() {
        assertEquals(1f, ThemeSettings.Default.effectiveUiScale)
    }

    @Test
    fun `ui scale is clamped into range`() {
        assertEquals(ThemeSettings.UI_SCALE_MAX, ThemeSettings(uiScale = 99f).effectiveUiScale)
        assertEquals(ThemeSettings.UI_SCALE_MIN, ThemeSettings(uiScale = 0f).effectiveUiScale)
        assertEquals(1.5f, ThemeSettings(uiScale = 1.5f).effectiveUiScale)
    }

    @Test
    fun `non-finite ui scale falls back to 1`() {
        assertEquals(1f, ThemeSettings(uiScale = Float.NaN).effectiveUiScale)
        assertEquals(1f, ThemeSettings(uiScale = Float.POSITIVE_INFINITY).effectiveUiScale)
    }

    /** 4K 面板误报 1080p 密度时需要的补偿正好是 2.0, 上界必须留有余量, 否则这类设备只能顶满档用. */
    @Test
    fun `range leaves headroom above the 2x correction`() {
        assertTrue(ThemeSettings.UI_SCALE_MAX > 2f)
        assertEquals(ThemeSettings.UI_SCALE_MIN..ThemeSettings.UI_SCALE_MAX, ThemeSettings.UI_SCALE_RANGE)
    }
}
