/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.list

import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @see me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
 */
@OptIn(TestOnly::class)
class EpisodeListItemTest {
    private fun item(name: String, nameCn: String) =
        createTestEpisodeListItem(name = name, nameCn = nameCn)

    @Test
    fun `preferredDisplayName_with_useOriginalTitle=true_returns_original_name`() {
        val episode = item(name = "転がる岩、君に朝が降る", nameCn = "滚石与朝阳")
        assertEquals("転がる岩、君に朝が降る", episode.preferredDisplayName(useOriginalTitle = true))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitle=false_returns_nameCn`() {
        val episode = item(name = "転がる岩、君に朝が降る", nameCn = "滚石与朝阳")
        assertEquals("滚石与朝阳", episode.preferredDisplayName(useOriginalTitle = false))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitle=true_falls_back_to_nameCn_when_name_is_blank`() {
        val episode = item(name = "", nameCn = "滚石与朝阳")
        assertEquals("滚石与朝阳", episode.preferredDisplayName(useOriginalTitle = true))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitle=false_falls_back_to_name_when_nameCn_is_blank`() {
        val episode = item(name = "転がる岩、君に朝が降る", nameCn = "")
        assertEquals("転がる岩、君に朝が降る", episode.preferredDisplayName(useOriginalTitle = false))
    }
}
