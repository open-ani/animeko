/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.episode

import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.repository.subject.toEntity1
import me.him188.ani.client.models.AniEpisodeCollection
import me.him188.ani.client.models.AniEpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 剧照直链 (`imageMedium` / `imageLarge`) 在网络模型、Room 实体与 [me.him188.ani.app.data.models.episode.EpisodeInfo] 之间原样传递.
 */
class EpisodeCollectionMappersTest {
    private fun aniEpisode(imageMedium: String?, imageLarge: String?) = AniEpisodeCollection(
        episodeId = 10,
        subjectId = 1,
        sort = "1",
        type = AniEpisodeType.MAIN,
        name = "ep",
        nameCn = "第1集",
        description = "",
        ep = "1",
        airdate = "2026-01-01",
        imageMedium = imageMedium,
        imageLarge = imageLarge,
    )

    @Test
    fun `still urls survive network to entity to info`() {
        val entity = aniEpisode("m", "l").toEntity1(subjectId = 1, lastFetched = 0L)
        assertEquals("m", entity.imageMedium)
        assertEquals("l", entity.imageLarge)

        val info = entity.toEpisodeCollectionInfo().episodeInfo
        assertEquals("m", info.imageMedium)
        assertEquals("l", info.imageLarge)
    }

    @Test
    fun `still urls survive network to info directly`() {
        val info = aniEpisode("m", "l").toEpisodeCollectionInfo().episodeInfo
        assertEquals("m", info.imageMedium)
        assertEquals("l", info.imageLarge)
    }

    @Test
    fun `still urls survive info to entity`() {
        val info = aniEpisode("m", "l").toEpisodeCollectionInfo()
        val entity = EpisodeCollectionInfo(info.episodeInfo, UnifiedCollectionType.DONE).toEntity(subjectId = 1, lastFetched = 0L)
        assertEquals("m", entity.imageMedium)
        assertEquals("l", entity.imageLarge)
        assertEquals(UnifiedCollectionType.DONE, entity.selfCollectionType)
    }

    @Test
    fun `missing still urls stay null everywhere`() {
        val entity = aniEpisode(null, null).toEntity1(subjectId = 1, lastFetched = 0L)
        assertNull(entity.imageMedium)
        assertNull(entity.imageLarge)
        val info = entity.toEpisodeCollectionInfo().episodeInfo
        assertNull(info.imageMedium)
        assertNull(info.imageLarge)
        assertNull(aniEpisode(null, null).toEpisodeCollectionInfo().episodeInfo.imageMedium)
    }
}
