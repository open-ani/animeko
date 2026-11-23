/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.settings

import me.him188.ani.app.navigation.SettingsTab
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 入口带来的 [SettingsTab] 决定设置页首次展示的分节; TV 上没有对应分节的 tab 落到默认「界面」分节.
 */
class TvSettingsInitialSectionTest {
    @Test
    fun `appearance tab lands on the appearance section`() {
        assertEquals(TvSettingsSection.Appearance, SettingsTab.APPEARANCE.toTvSettingsSection())
    }

    @Test
    fun `tabs with a counterpart land on their own section`() {
        assertEquals(TvSettingsSection.Theme, SettingsTab.THEME.toTvSettingsSection())
        assertEquals(TvSettingsSection.Player, SettingsTab.PLAYER.toTvSettingsSection())
        assertEquals(TvSettingsSection.Sources, SettingsTab.MEDIA_SOURCE.toTvSettingsSection())
        assertEquals(TvSettingsSection.Watching, SettingsTab.MEDIA_SELECTOR.toTvSettingsSection())
        assertEquals(TvSettingsSection.About, SettingsTab.ABOUT.toTvSettingsSection())
    }

    @Test
    fun `tabs without a counterpart fall back to the appearance section`() {
        assertEquals(TvSettingsSection.Appearance, SettingsTab.PROFILE.toTvSettingsSection())
        assertEquals(TvSettingsSection.Appearance, SettingsTab.PROXY.toTvSettingsSection())
        assertEquals(TvSettingsSection.Appearance, SettingsTab.STORAGE.toTvSettingsSection())
        assertEquals(TvSettingsSection.Appearance, null.toTvSettingsSection())
    }
}
