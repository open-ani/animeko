/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @see me.him188.ani.app.domain.episode.SubjectRecommendation
 */
class SubjectRecommendationTest {
    private fun recommendation(name: String, nameCn: String?) = SubjectRecommendation(
        subjectId = 1,
        name = name,
        nameCn = nameCn,
        desc1 = "",
        desc2 = "",
        imageUrl = "",
        uri = null,
    )

    @Test
    fun `preferredDisplayName_with_useOriginalTitleTrue_returns_the_original_name`() {
        val item = recommendation(name = "進撃の巨人", nameCn = "进击的巨人")
        assertEquals("進撃の巨人", item.preferredDisplayName(useOriginalTitle = true))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitleFalse_returns_nameCn`() {
        val item = recommendation(name = "進撃の巨人", nameCn = "进击的巨人")
        assertEquals("进击的巨人", item.preferredDisplayName(useOriginalTitle = false))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitleTrue_falls_back_to_nameCn_when_name_is_blank`() {
        val item = recommendation(name = "", nameCn = "进击的巨人")
        assertEquals("进击的巨人", item.preferredDisplayName(useOriginalTitle = true))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitleFalse_falls_back_to_name_when_nameCn_is_null_or_blank`() {
        assertEquals("進撃の巨人", recommendation(name = "進撃の巨人", nameCn = null).preferredDisplayName(useOriginalTitle = false))
        assertEquals("進撃の巨人", recommendation(name = "進撃の巨人", nameCn = "").preferredDisplayName(useOriginalTitle = false))
    }
}
