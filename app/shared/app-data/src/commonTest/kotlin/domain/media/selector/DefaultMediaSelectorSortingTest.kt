/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultMediaSelectorSortingTest : AbstractDefaultMediaSelectorTest() {

    @Test
    fun `sort by subjectName similarity`() = runTest {
        preferAnyMedia()
        val expectedSort = listOf(
            media(subjectName = "孤独摇滚", kind = MediaSourceKind.WEB),
            media(subjectName = "Bocchi The Rock!", kind = MediaSourceKind.WEB),
            media(subjectName = "孤独摇滚!", kind = MediaSourceKind.WEB),
            media(subjectName = "孤独摇滚 第二季", kind = MediaSourceKind.WEB),
            media(subjectName = "孤单摇滚", kind = MediaSourceKind.WEB),
            media(subjectName = "孤单摇滚 第二季", kind = MediaSourceKind.WEB),
        )
        addMedia(*expectedSort.toTypedArray<DefaultMedia>().apply { shuffle(Random(10000)) })
        assertEquals(
            expectedSort.indices.toList().take(4),
            selector.filteredCandidatesMedia.first().map { expectedSort.indexOf(it) },
        )
    }

    @Test
    fun `sort by subjectName filters out unrelated WEB`() = runTest {
        preferAnyMedia()
        val expectedSort = listOf(
            media(subjectName = "unrelated item unrelated item", kind = MediaSourceKind.WEB),
        )
        addMedia(*expectedSort.toTypedArray())
        assertEquals(
            listOf(MediaExclusionReason.SubjectNameMismatch),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
    }

    @Test
    fun `sort by subjectName does not filter out BT`() = runTest {
        preferAnyMedia()
        val expectedSort = listOf(
            media(subjectName = "unrelated item", kind = MediaSourceKind.BitTorrent),
        )
        addMedia(*expectedSort.toTypedArray())
        assertEquals(
            listOf(null),
            selector.filteredCandidates.first().map { it.exclusionReason },
        )
    }

    @Test
    fun `sort by subjectName does not filter out Cache`() = runTest {
        preferAnyMedia()
        val expectedSort = listOf(
            media(subjectName = "unrelated item", kind = MediaSourceKind.LocalCache),
        )
        addMedia(*expectedSort.toTypedArray())
        assertEquals(
            listOf(null),
            selector.filteredCandidates.first().map { it.exclusionReason },
        )
    }

    @Test
    fun `preferKind is null - keep original order`() = runTest {
        preferAnyMedia()
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(preferKind = null)
        val expectedSort = listOf(
            media(kind = MediaSourceKind.WEB),
            media(kind = MediaSourceKind.BitTorrent),
            media(kind = MediaSourceKind.WEB),
            media(kind = MediaSourceKind.BitTorrent),
            media(kind = MediaSourceKind.WEB),
            media(kind = MediaSourceKind.BitTorrent),
        )
        addMedia(*expectedSort.toTypedArray())
        assertEquals(
            expectedSort.indices.toList(),
            selector.filteredCandidatesMedia.first().map { expectedSort.indexOf(it) },
        )
    }

    inner class PreferKindMedias {
        val cache1 = media(mediaId = "cache1", kind = MediaSourceKind.LocalCache)
        val cache2 = media(mediaId = "cache2", kind = MediaSourceKind.LocalCache)
        val web1 = media(mediaId = "web1", kind = MediaSourceKind.WEB)
        val web2 = media(mediaId = "web2", kind = MediaSourceKind.WEB)
        val bt1 = media(mediaId = "bt1", kind = MediaSourceKind.BitTorrent)
        val bt2 = media(mediaId = "bt2", kind = MediaSourceKind.BitTorrent)
    }

    @Test
    fun `preferKind is null - cache always on top`() = runTest {
        preferAnyMedia()
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(preferKind = null)
        PreferKindMedias().run {
            addMedia(web1, bt1, cache1)
            assertEquals(
                cache1,
                selector.filteredCandidatesMedia.first()[0],
            )
        }
    }

    @Test
    fun `preferKind is BT - BT first`() = runTest {
        preferAnyMedia()
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(preferKind = MediaSourceKind.BitTorrent)
        PreferKindMedias().run {
            addMedia(web1, bt1, web2, bt2)
            assertEquals(
                listOf(bt1, bt2, web1, web2),
                selector.filteredCandidatesMedia.first(),
            )
        }
    }

    @Test
    fun `preferKind is BT - cache on top`() = runTest {
        preferAnyMedia()
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(preferKind = MediaSourceKind.BitTorrent)
        PreferKindMedias().run {
            addMedia(web1, bt1, web2, bt2, cache1)
            assertEquals(
                listOf(cache1, bt1, bt2, web1, web2),
                selector.filteredCandidatesMedia.first(),
            )
        }
    }

    @Test
    fun `preferKind is WEB - WEB first`() = runTest {
        preferAnyMedia()
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(preferKind = MediaSourceKind.WEB)
        PreferKindMedias().run {
            addMedia(web1, bt1, web2, bt2)
            assertEquals(
                listOf(web1, web2, bt1, bt2),
                selector.filteredCandidatesMedia.first(),
            )
        }
    }

    @Test
    fun `preferKind is WEB - cache on top`() = runTest {
        preferAnyMedia()
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(preferKind = MediaSourceKind.WEB)
        PreferKindMedias().run {
            addMedia(web1, bt1, web2, bt2)
            assertEquals(
                listOf(web1, web2, bt1, bt2),
                selector.filteredCandidatesMedia.first(),
            )
        }
    }

    private fun preferAnyMedia() {
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            subjectInfo = SubjectInfo.Empty.copy(nameCn = "孤独摇滚", name = "Bocchi The Rock!"),
            episodeInfo = EpisodeInfo.Empty.copy(sort = EpisodeSort(1)),
        )
        savedDefaultPreference.value = MediaPreference.Any
        savedUserPreference.value = MediaPreference.Any
    }
}