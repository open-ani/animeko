/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SeasonPartInfo
import me.him188.ani.app.data.models.subject.SeriesMainSubject
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.selector.filter.MediaSelectorFilterSortAlgorithm
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 用真实站点的条目与集号 (`resources/season-part-sites/`) 回归分部条目的过滤: 对每个分部组的每个采样剧集,
 * 过滤后保留的 (站点条目, 集号) 必须是标准答案的子集, 且标准答案非空时不能一个都不留.
 */
class SeasonPartSiteDatasetTest {
    @Serializable
    private data class Dataset(val source: String, val groups: List<Group>)

    @Serializable
    private data class Group(
        val base: String,
        val partIds: List<Int>,
        val mainline: List<MainlineSubject>,
        val entries: List<Entry>,
        val cases: List<Case>,
    )

    @Serializable
    private data class MainlineSubject(val id: Int, val names: List<String>, val mainEpisodeCount: Int)

    @Serializable
    private data class Entry(val siteId: String, val name: String, val numbers: List<Int>)

    @Serializable
    private data class Case(
        val subjectId: Int,
        val index: Int,
        val sort: Double,
        val ep: Double,
        val truth: List<Selection>,
        val verifiable: Boolean,
        val flags: List<String> = emptyList(),
        val conventions: List<String> = emptyList(),
    )

    @Serializable
    private data class Selection(val siteId: String, val number: Int)

    private val json = Json { ignoreUnknownKeys = true }
    private val algorithm = MediaSelectorFilterSortAlgorithm()

    @Test
    fun `girigiri`() = run("girigiri")

    @Test
    fun `xifan`() = run("xifan")

    @Test
    fun `dida`() = run("dida")

    @Test
    fun `jibi`() = run("jibi")

    @Test
    fun `senfun`() = run("senfun")

    @Test
    fun `dilidili`() = run("dilidili")

    @Test
    fun `rebo`() = run("rebo")

    @Test
    fun `yinghua`() = run("yinghua")

    @Test
    fun `fanqie`() = run("fanqie")

    @Test
    fun `haixing`() = run("haixing")

    private fun run(source: String) {
        val file = File("src/desktopTest/resources/season-part-sites/$source.json")
        val dataset = json.decodeFromString(Dataset.serializer(), file.readText())
        val failures = mutableListOf<String>()
        var correct = 0
        var noTruth = 0
        var skipped = 0
        for (group in dataset.groups) {
            val mainline = group.mainline.map { SeriesMainSubject(it.id, it.names, it.mainEpisodeCount) }

            val medias = group.entries.flatMap { entry -> entry.numbers.map { entryMedia(entry.siteId, entry.name, it) } }
            for (case in group.cases) {
                if (!case.verifiable) {
                    skipped++
                    continue
                }
                val part = assertNotNull(SeasonPartInfo.compute(mainline, case.subjectId), "${group.base}: part group not detected")
                val context = context(mainline, case.subjectId, part, case)
                val selected = algorithm.filterMediaList(medias, MediaPreference.Empty, MediaSelectorSettings.Default, context)
                    .filterIsInstance<MaybeExcludedMedia.Included>()
                    .map { it.result.mediaId.substringAfter(':') to it.result.episodeRange!!.knownSorts.single().number!!.toInt() }
                    .toSet()
                val truth = case.truth.map { it.siteId to it.number }.toSet()
                val subjectName = mainline.first { it.subjectId == case.subjectId }.names.first()
                val label = "${group.base} / $subjectName ep ${case.ep} (sort ${case.sort}, ${case.conventions.joinToString()})"
                when {
                    truth.isEmpty() && selected.isEmpty() -> noTruth++
                    truth.isEmpty() -> failures += "$label: selected $selected but the site has no matching entry"
                    selected.isEmpty() -> failures += "$label: nothing selected, expected one of $truth"
                    !truth.containsAll(selected) -> failures += "$label: selected $selected, expected a subset of $truth"
                    else -> correct++
                }
            }
        }
        println("$source: correct=$correct noTruth=$noTruth skipped=$skipped failures=${failures.size}")
        if (failures.isNotEmpty()) fail(failures.joinToString("\n", prefix = "${failures.size} failures:\n"))
        assertTrue(correct > 0)
    }

    private fun context(mainline: List<SeriesMainSubject>, subjectId: Int, part: SeasonPartInfo, case: Case): MediaSelectorContext {
        val own = mainline.first { it.subjectId == subjectId }
        val siblingNames = mainline.filter { it.subjectId != subjectId }.flatMap { it.names }
            .filter { sibling -> own.names.none { MediaListFilters.specialEquals(it, sibling) } }
            .toSet()
        val sequelNames = mainline.dropWhile { it.subjectId != subjectId }.drop(1).flatMap { it.names }.toSet()
        return MediaSelectorContext(
            subjectFinished = true,
            mediaSourcePrecedence = emptyList(),
            subtitlePreferences = MediaSelectorSubtitlePreferences.AllNormal,
            subjectSeriesInfo = SubjectSeriesInfo(
                seasonSort = mainline.indexOfFirst { it.subjectId == subjectId } + 1,
                sequelSubjectNames = sequelNames intersect siblingNames,
                seriesSubjectNamesWithoutSelf = siblingNames,
                seasonPart = part,
            ),
            subjectInfo = SubjectInfo.Empty.copy(
                subjectId = subjectId,
                nameCn = own.names.first(),
                name = own.names.getOrElse(1) { "" },
                aliases = own.names.drop(2),
            ),
            episodeInfo = EpisodeInfo.Empty.copy(
                episodeId = case.index,
                sort = EpisodeSort(case.sort.toString()),
                ep = EpisodeSort(case.ep.toString()),
            ),
            mediaSourceTiers = MediaSelectorSourceTiers.Empty,
        )
    }

    private fun entryMedia(siteId: String, name: String, number: Int): Media = createTestDefaultMedia(
        mediaId = "site:$siteId",
        mediaSourceId = "site",
        originalUrl = "https://example.com/$siteId/$number",
        download = ResourceLocation.WebVideo("https://example.com/$siteId/$number"),
        originalTitle = "$name 第${number}集",
        publishedTime = 0,
        properties = createTestMediaProperties(subjectName = name, alliance = "线路1"),
        episodeRange = EpisodeRange.single(EpisodeSort(number)),
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.WEB,
    )
}
