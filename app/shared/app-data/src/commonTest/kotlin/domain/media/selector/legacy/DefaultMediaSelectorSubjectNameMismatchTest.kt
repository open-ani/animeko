/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.legacy

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @suppress 已弃用, 新的 test 使用 [me.him188.ani.app.domain.media.selector.testFramework.TestMediaFetchSessionBuilder].
 * @see me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelect
 */
class DefaultMediaSelectorSubjectNameMismatchTest : AbstractDefaultMediaSelectorTest() {
    @Test
    fun `exclude name mismatch when subjectName is available`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Companion.Empty.copy(
                nameCn = "条目名称",
            ),
            episodeInfo = EpisodeInfo.Companion.Empty.copy(sort = EpisodeSort(1)),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(
                // kept
                alliance = "字幕组1",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称I",
                originalTitle = "random value",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ).also { target = it },
            media(
                // excluded
                alliance = "字幕组2",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "a",
                originalTitle = "random value 2",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ),
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, MediaExclusionReason.SubjectNameMismatch),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `exclude name mismatch when subjectName is not available`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Companion.Empty.copy(
                nameCn = "条目名称",
            ),
            episodeInfo = EpisodeInfo.Companion.Empty.copy(sort = EpisodeSort(1)),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(
                // kept
                alliance = "字幕组1",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = null,
                originalTitle = "条目名称I 第1话",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ).also { target = it },
            media(
                // excluded
                alliance = "字幕组2",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = null,
                originalTitle = "ab 第2话",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ),
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, MediaExclusionReason.SubjectNameMismatch),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `dont exclude if epInfo is null`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Companion.Empty.copy(
                nameCn = "条目名称",
            ),
            episodeInfo = EpisodeInfo.Companion.Empty,
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(
                // kept
                alliance = "字幕组1",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称I",
                originalTitle = "条目名称I",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ).also { target = it },
            media(
                // excluded
                alliance = "字幕组2",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "a",
                originalTitle = "a",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ),
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, null),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `dont exclude if subjectInfo name is blank`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Companion.Empty.copy(
                nameCn = "",
            ),
            episodeInfo = EpisodeInfo.Companion.Empty.copy(sort = EpisodeSort(1)),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(
                // kept
                alliance = "字幕组1",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称I",
                originalTitle = "条目名称I",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ).also { target = it },
            media(
                // excluded
                alliance = "字幕组2",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "a",
                originalTitle = "a",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ),
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, null),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `dont exclude if both infos are empty`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Companion.Empty,
            episodeInfo = EpisodeInfo.Companion.Empty,
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(
                // kept
                alliance = "字幕组1",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称I",
                originalTitle = "条目名称I",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ).also { target = it },
            media(
                // excluded
                alliance = "字幕组2",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "a",
                originalTitle = "a",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ),
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, null),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(target, selector.trySelectDefault())
    }
}
