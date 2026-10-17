/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.source

import me.him188.ani.datasources.api.EpisodeSort
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaFetchRequestTest {
    private fun request(fallbackSearchKeywords: List<String>, episodeId: String = "1") = MediaFetchRequest(
        subjectId = "1",
        episodeId = episodeId,
        subjectNames = listOf("作品"),
        episodeSort = EpisodeSort(1),
        episodeName = "",
        fallbackSearchKeywords = fallbackSearchKeywords,
    )

    @Test
    fun `same subject query ignores the current episode`() {
        assertTrue(request(listOf("基础名")).isSameSubjectQuery(request(listOf("基础名"), episodeId = "2")))
    }

    @Test
    fun `same subject query compares fallback search keywords`() {
        assertFalse(request(listOf("基础名")).isSameSubjectQuery(request(emptyList())))
    }
}
