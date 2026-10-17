/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.first
import kotlinx.io.files.Path
import me.him188.ani.app.domain.media.DroppedFileMedia
import me.him188.ani.app.domain.media.selector.MatchMetadata.EpisodeMatchKind
import me.him188.ani.app.domain.media.selector.MatchMetadata.SubjectMatchKind
import me.him188.ani.app.domain.media.selector.testFramework.SimpleMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.test.TestContainer
import me.him188.ani.utils.io.inSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestContainer
class MediaSelectorSelectedMaybeExcludedMediaTest {
    /**
     * Bangumi 中文名是「更多 出包王女」, 站点用别名「出包王女 第二季」的形式命名页面.
     */
    private fun SimpleMediaSelectorTestSuite.initMottoToLoveRu() {
        initSubject("更多 出包王女") {
            aliases("もっとTo LOVEる -とらぶる-", "Motto To Love-Ru: Trouble", "出包王女 第二季")
            episodeSort = EpisodeSort(3)
            episodeEp = EpisodeSort(3)
        }
    }

    private fun SimpleMediaSelectorTestSuite.webMedia(subjectName: String): DefaultMedia = media(
        sourceId = "girigiri",
        kind = MediaSourceKind.WEB,
        alliance = "简中",
        subjectName = subjectName,
        originalTitle = "$subjectName 03",
        episodeRange = EpisodeRange.single(EpisodeSort(3)),
        mediaId = "girigiri.$subjectName-03",
    )

    @Test
    fun `null when nothing is selected`() = runSimpleMediaSelectorTestSuite {
        initMottoToLoveRu()
        mediaApi.addMedia(webMedia("出包王女第二季"))

        assertNull(selector.selectedMaybeExcludedMedia.first())
    }

    @Test
    fun `selected candidate has the metadata from the candidate list`() = runSimpleMediaSelectorTestSuite {
        initMottoToLoveRu()
        val media = mediaApi.addMedia(webMedia("出包王女第二季"))
        selector.select(media)

        @OptIn(UnsafeOriginalMediaAccess::class)
        assertEquals(
            selector.filteredCandidates.first().single { it.original == media },
            selector.selectedMaybeExcludedMedia.first(),
        )
    }

    @Test
    fun `media created outside the candidates is matched against subject aliases`() = runSimpleMediaSelectorTestSuite {
        initMottoToLoveRu()
        // 浏览手动选集和浏览记忆回放现场创建资源, 与自动匹配搜到的不是同一个实例, 也可能根本没被搜到
        selector.select(webMedia("出包王女第二季"))

        val selected = assertIs<MaybeExcludedMedia.Included>(selector.selectedMaybeExcludedMedia.first())
        assertEquals(SubjectMatchKind.EXACT, selected.metadata.subjectMatchKind)
        assertEquals(EpisodeMatchKind.SORT, selected.metadata.episodeMatchKind)
        assertTrue(selected.isPerfectMatch())
    }

    @Test
    fun `media created outside the candidates with an unrelated name is not a perfect match`() =
        runSimpleMediaSelectorTestSuite {
            initMottoToLoveRu()
            selector.select(webMedia("出包王女"))

            assertFalse(selector.selectedMaybeExcludedMedia.first()!!.isPerfectMatch())
        }

    @Test
    fun `dropped file is not a perfect match`() = runSimpleMediaSelectorTestSuite {
        initMottoToLoveRu()
        selector.selectTemporarily(DroppedFileMedia.create(Path("/videos/出包王女第二季 03.mkv").inSystem))

        assertFalse(selector.selectedMaybeExcludedMedia.first()!!.isPerfectMatch())
    }
}
