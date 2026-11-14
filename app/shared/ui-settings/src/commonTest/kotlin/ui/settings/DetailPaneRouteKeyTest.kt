/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 深链接参数 (`NavRoutes.Settings.detail`) 与返回栈快照共用同一张 key 映射表,
 * 未知 key 不映射到任何页面, 让调用方停留在当前分节.
 */
class DetailPaneRouteKeyTest {
    @Test
    fun `time zone key maps to the time zone page`() {
        assertEquals(DetailPaneRoutes.TimeZone, detailPaneRouteOfKey(TIME_ZONE_DETAIL_KEY))
    }

    @Test
    fun `restored keys map back to their pages`() {
        assertEquals(DetailPaneRoutes.Main, detailPaneRouteOfKey("Main"))
        assertEquals(DetailPaneRoutes.Acknowledgements, detailPaneRouteOfKey("Acknowledgements"))
        assertEquals(DetailPaneRoutes.OpenSourceLicenses, detailPaneRouteOfKey("OpenSourceLicenses"))
        assertEquals(DetailPaneRoutes.Developers, detailPaneRouteOfKey("Developers"))
        assertEquals(DetailPaneRoutes.BuildInfo, detailPaneRouteOfKey("BuildInfo"))
        assertEquals(DetailPaneRoutes.BangumiSync, detailPaneRouteOfKey("BangumiSync"))
        assertEquals(DetailPaneRoutes.GithubAccount, detailPaneRouteOfKey("GithubAccount"))
        assertEquals(DetailPaneRoutes.DevBuilds, detailPaneRouteOfKey("DevBuilds"))
    }

    @Test
    fun `unknown key maps to no page`() {
        assertNull(detailPaneRouteOfKey("NotAPage"))
    }
}
