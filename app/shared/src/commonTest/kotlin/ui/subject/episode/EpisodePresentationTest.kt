/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [EpisodePresentation.isKnownBroadcast] 与 [EpisodePresentation.isKnownNotYetAired] 都是保守判定,
 * 因此不是互补关系: 拿不到播出日期时两者同时为 `false`.
 */
class EpisodePresentationTest {
    private fun episode(
        airDate: PackedDate,
        type: EpisodeType = EpisodeType.MainStory,
    ) = EpisodeCollectionInfo(
        episodeInfo = EpisodeInfo(
            episodeId = 1,
            type = type,
            name = "test",
            airDate = airDate,
            sort = EpisodeSort(1),
        ),
        collectionType = UnifiedCollectionType.WISH,
    ).toPresentation(recurrence = null)

    @Test
    fun `episode without air date is neither known broadcast nor known not-yet-aired`() {
        val presentation = episode(PackedDate.Invalid, EpisodeType.SP)
        assertFalse(presentation.isKnownBroadcast, "没有播出日期 => 不能断定已播出")
        assertFalse(
            presentation.isKnownNotYetAired,
            "没有播出日期 => 也不能断定还没播出",
        )
    }

    @Test
    fun `episode aired in the past is known broadcast`() {
        val presentation = episode(PackedDate(2000, 1, 1))
        assertTrue(presentation.isKnownBroadcast)
        assertFalse(presentation.isKnownNotYetAired)
    }

    @Test
    fun `episode airing in the future is known not-yet-aired`() {
        val presentation = episode(PackedDate(9999, 12, 31))
        assertFalse(presentation.isKnownBroadcast)
        assertTrue(presentation.isKnownNotYetAired)
    }
}
