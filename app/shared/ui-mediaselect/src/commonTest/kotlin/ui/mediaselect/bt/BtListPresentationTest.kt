/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.bt

import kotlinx.datetime.TimeZone
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.TestMatchMetadata
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class BtListPresentationTest {
    private fun media(
        id: String,
        sourceId: String = "dmhy",
        kind: MediaSourceKind = MediaSourceKind.BitTorrent,
        resolution: String = "1080P",
        alliance: String = "桜都字幕组",
        subtitleLanguageIds: List<String> = listOf("CHS"),
        episodeSort: Int = 1,
    ): Media = createTestDefaultMedia(
        mediaId = "$sourceId.$id",
        mediaSourceId = sourceId,
        originalUrl = "https://example.com/$id",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:$id"),
        originalTitle = "title $id",
        publishedTime = 0,
        properties = createTestMediaProperties(
            subtitleLanguageIds = subtitleLanguageIds,
            resolution = resolution,
            alliance = alliance,
            size = 100.megaBytes,
            subtitleKind = null,
        ),
        episodeRange = EpisodeRange.single(EpisodeSort(episodeSort)),
        location = MediaSourceLocation.Online,
        kind = kind,
    )

    private fun included(media: Media) = MaybeExcludedMedia.Included(media, TestMatchMetadata)
    private fun excluded(media: Media, reason: MediaExclusionReason) = MaybeExcludedMedia.Excluded(media, reason)

    private val web = media("web", sourceId = "nyafun", kind = MediaSourceKind.WEB)
    private val a = media("a", alliance = "A", resolution = "1080P", subtitleLanguageIds = listOf("CHS", "CHT"))
    private val b = media("b", sourceId = "acg", alliance = "B", resolution = "720P", subtitleLanguageIds = listOf("CHT"))
    private val c = media("c", alliance = "A", resolution = "1080P", subtitleLanguageIds = emptyList(), episodeSort = 2)
    private val cache = media("cache", sourceId = "local", kind = MediaSourceKind.LocalCache, alliance = "C", resolution = "480P")

    private fun candidates(
        filtered: List<MaybeExcludedMedia>,
        subject: List<MaybeExcludedMedia> = filtered,
        preference: MediaPreference = MediaPreference.Empty,
        selected: Media? = null,
    ) = BtCandidates(filtered, subject, preference, selected)

    @Test
    fun `episode filter on uses filtered list and keeps EpisodeMismatch in excluded`() {
        val filtered = listOf(included(a), excluded(c, MediaExclusionReason.EpisodeMismatch(c.episodeRange)))
        val subject = listOf(included(a), included(c))
        val result = projectBtList(candidates(filtered, subject), episodeFilterEnabled = true, sourceFilter = null)

        assertEquals(listOf(a.mediaId), result.included.map { it.id })
        assertEquals(listOf(c.mediaId), result.excluded.map { it.id })
        val exclusion = assertIs<BtRowExclusion.Reason>(result.excluded.single().exclusion)
        assertIs<MediaExclusionReason.EpisodeMismatch>(exclusion.reason)
        assertTrue(result.episodeFilterEnabled)
    }

    @Test
    fun `episode filter off uses subject list`() {
        val filtered = listOf(included(a), excluded(c, MediaExclusionReason.EpisodeMismatch(c.episodeRange)))
        val subject = listOf(included(a), included(c))
        val result = projectBtList(candidates(filtered, subject), episodeFilterEnabled = false, sourceFilter = null)

        assertEquals(listOf(a.mediaId, c.mediaId), result.included.map { it.id })
        assertTrue(result.excluded.isEmpty())
        assertFalse(result.episodeFilterEnabled)
    }

    @Test
    fun `non bt kinds are dropped and local cache stays in main list`() {
        val list = listOf(included(web), included(a), included(cache))
        val result = projectBtList(
            candidates(list, preference = MediaPreference.Empty.copy(resolution = "1080P")),
            episodeFilterEnabled = true,
            sourceFilter = null,
        )

        assertEquals(listOf(a.mediaId, cache.mediaId), result.included.map { it.id })
        assertTrue(result.included.last().isCached)
        assertEquals(2, result.totalCount)
        assertFalse(result.sourceCounts.containsKey(web.mediaSourceId))
    }

    @Test
    fun `included but outside preference becomes below preference`() {
        val list = listOf(included(a), included(b), excluded(c, MediaExclusionReason.MediaWithoutSubtitle))
        val result = projectBtList(
            candidates(list, preference = MediaPreference.Empty.copy(alliance = "A")),
            episodeFilterEnabled = true,
            sourceFilter = null,
        )

        assertEquals(listOf(a.mediaId), result.included.map { it.id })
        assertEquals(listOf(b.mediaId, c.mediaId), result.excluded.map { it.id })
        assertEquals(BtRowExclusion.BelowPreference, result.excluded[0].exclusion)
        val reason = assertIs<BtRowExclusion.Reason>(result.excluded[1].exclusion)
        assertEquals(MediaExclusionReason.MediaWithoutSubtitle, reason.reason)
        assertTrue(result.excluded.all { it.isExcluded })
    }

    @Test
    fun `source filter narrows rows but not counts or available values`() {
        val list = listOf(included(a), included(b), excluded(c, MediaExclusionReason.MediaWithoutSubtitle))
        val result = projectBtList(candidates(list), episodeFilterEnabled = true, sourceFilter = "acg")

        assertEquals(listOf(b.mediaId), result.included.map { it.id })
        assertTrue(result.excluded.isEmpty())
        assertEquals("acg", result.sourceFilter)
        assertEquals(mapOf("dmhy" to 2, "acg" to 1), result.sourceCounts)
        assertEquals(3, result.totalCount)
        assertEquals(listOf("1080P", "720P"), result.availableResolutions)
        assertEquals(listOf("A", "B"), result.availableAlliances)
    }

    @Test
    fun `available values are distinct and keep base order`() {
        val list = listOf(included(b), included(a), included(c))
        val result = projectBtList(candidates(list), episodeFilterEnabled = true, sourceFilter = null)

        assertEquals(listOf("720P", "1080P"), result.availableResolutions)
        assertEquals(listOf("CHT", "CHS"), result.availableSubtitleLanguageIds)
        assertEquals(listOf("B", "A"), result.availableAlliances)
    }

    @Test
    fun `active filter count and selection`() {
        val list = listOf(included(a), included(b))
        val result = projectBtList(
            candidates(
                list,
                preference = MediaPreference.Empty.copy(resolution = "1080P", subtitleLanguageId = "CHS"),
                selected = a,
            ),
            episodeFilterEnabled = true,
            sourceFilter = null,
        )

        assertEquals(2, result.activeFilterCount)
        assertEquals("1080P", result.resolution)
        assertEquals("CHS", result.subtitleLanguageId)
        assertEquals(null, result.alliance)
        assertEquals(a, result.selected)
        assertTrue(result.included.single { it.id == a.mediaId }.isSelected)
        assertFalse(result.excluded.single { it.id == b.mediaId }.isSelected)
        assertFalse(result.isPlaceholder)
    }

    @Test
    fun `empty input`() {
        val result = projectBtList(candidates(emptyList()), episodeFilterEnabled = true, sourceFilter = null)
        assertTrue(result.included.isEmpty())
        assertTrue(result.excluded.isEmpty())
        assertEquals(0, result.totalCount)
        assertEquals(0, result.activeFilterCount)
        assertFalse(result.isPlaceholder)
    }

    @Test
    fun `format published date`() {
        assertEquals("", formatPublishedMMdd(0, TimeZone.UTC))
        // 2024-03-05T00:00:00Z
        assertEquals("03-05", formatPublishedMMdd(1_709_596_800_000, TimeZone.UTC))
    }

    @Test
    fun `format size`() {
        assertEquals("", formatBtSize(FileSize.Unspecified))
        assertEquals("", formatBtSize(FileSize.Zero))
        assertEquals("122.0 MB", formatBtSize(122.megaBytes))
    }
}
