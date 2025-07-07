/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("DEPRECATION")

package me.him188.ani.app.domain.media.selector.legacy

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.OptionalPreference
import me.him188.ani.app.domain.media.selector.SelectEvent
import me.him188.ani.app.domain.media.selector.preferredValueOrNull
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.Subtitle
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.utils.coroutines.cancellableCoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * @suppress 已弃用, 新的 test 使用 [me.him188.ani.app.domain.media.selector.testFramework.TestMediaFetchSessionBuilder].
 * @see me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelect
 */
@Deprecated(MediaSelectorDeprecationMessage)
class DefaultMediaSelectorTest : AbstractDefaultMediaSelectorTest() {
    ///////////////////////////////////////////////////////////////////////////
    // Select contract
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `select two times returns false`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleLanguages = listOf("CHS")),
            media(alliance = "字幕组2", resolution = "Special").also { target = it },
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组4", subtitleLanguages = listOf("CHS", "CHT", "R")),
            media(alliance = "字幕组5", subtitleLanguages = listOf("CHS")),
        )
        assertTrue { selector.select(target) }
        assertFalse { selector.select(target) }
    }

    ///////////////////////////////////////////////////////////////////////////
    // 单个选项测试
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `initial empty preferences`() = runTest {
        assertEquals(null, selector.alliance.defaultSelected.first())
        assertEquals(null, selector.alliance.userSelected.first().preferredValueOrNull)
        assertEquals(null, selector.alliance.finalSelected.first())
    }

    @Test
    fun `initial empty preferences when list is not empty`() = runTest {
        addMedia(media(alliance = "字幕组"))
        assertEquals(null, selector.alliance.defaultSelected.first())
        assertEquals(null, selector.alliance.userSelected.first().preferredValueOrNull)
        assertEquals(null, selector.alliance.finalSelected.first())
    }

    @Test
    fun `prefer alliance`() = runTest {
        addMedia(media(alliance = "字幕组"))
        selector.alliance.prefer("字幕组")
        assertEquals(null, selector.alliance.defaultSelected.first())
        assertEquals("字幕组", selector.alliance.userSelected.first().preferredValueOrNull)
        assertEquals("字幕组", selector.alliance.finalSelected.first())
    }

    @Test
    fun `default prefer alliance`() = runTest {
        addMedia(media(alliance = "字幕组"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(alliance = "字幕组")
        assertEquals("字幕组", selector.alliance.defaultSelected.first())
        assertEquals(null, selector.alliance.userSelected.first().preferredValueOrNull)
        assertEquals("字幕组", selector.alliance.finalSelected.first())
    }

    @Test
    fun `user override alliance`() = runTest {
        addMedia(media(alliance = "字幕组"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(alliance = "字幕组")
        assertEquals("字幕组", selector.alliance.defaultSelected.first())
        assertEquals(null, selector.alliance.userSelected.first().preferredValueOrNull)
        assertEquals("字幕组", selector.alliance.finalSelected.first())
    }

    @Test
    fun `user override no preference`() = runTest {
        addMedia(media(alliance = "字幕组"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(alliance = "字幕组")
        assertEquals("字幕组", selector.alliance.defaultSelected.first())
        assertEquals(false, selector.alliance.userSelected.first().isPreferNoValue)
        assertNotEquals(null, selector.alliance.finalSelected.first())
        selector.alliance.removePreference()
        assertEquals(true, selector.alliance.userSelected.first().isPreferNoValue)
        assertEquals(null, selector.alliance.finalSelected.first())
    }

    ///////////////////////////////////////////////////////////////////////////
    // 选择数据测试
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `empty preferences select all`() = runTest {
        addMedia(media(alliance = "字幕组"), media(alliance = "字幕组2"))
        assertEquals(mediaList.value, selector.preferredCandidatesMedia.first())
    }

    @Test
    fun `select by user alliance`() = runTest {
        val media = media(alliance = "字幕组")
        addMedia(media, media(alliance = "字幕组2"))
        savedUserPreference.value = DEFAULT_PREFERENCE.copy(alliance = "字幕组")
        assertEquals(media, selector.preferredCandidatesMedia.first().single())
    }

    @Test
    fun `select by default alliance`() = runTest {
        val media = media(alliance = "字幕组")
        addMedia(media, media(alliance = "字幕组2"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(alliance = "字幕组")
        assertEquals(media, selector.preferredCandidatesMedia.first().single())
    }

    @Test
    fun `select by user resolution`() = runTest {
        val media = media(resolution = "字幕组")
        addMedia(media, media(resolution = "字幕组2"))
        savedUserPreference.value = DEFAULT_PREFERENCE.copy(resolution = "字幕组")
        assertEquals(media, selector.preferredCandidatesMedia.first().single())
    }

    @Test
    fun `select by default resolution`() = runTest {
        val media = media(resolution = "字幕组")
        addMedia(media, media(resolution = "字幕组2"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(resolution = "字幕组")
        assertEquals(media, selector.preferredCandidatesMedia.first().single())
    }

    @Test
    fun `select by user subtitle one of`() = runTest {
        val media = media(subtitleLanguages = listOf("字幕组", "a"))
        addMedia(media, media(subtitleLanguages = listOf("b")))
        savedUserPreference.value = DEFAULT_PREFERENCE.copy(subtitleLanguageId = "a")
        assertEquals(media, selector.preferredCandidatesMedia.first().single())
    }

    @Test
    fun `select by default subtitle one of`() = runTest {
        val media = media(subtitleLanguages = listOf("字幕组", "a"))
        addMedia(media, media(subtitleLanguages = listOf("b")))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(subtitleLanguageId = "a")
        assertEquals("a", selector.subtitleLanguageId.finalSelected.first())
        assertEquals(media, selector.preferredCandidatesMedia.first().single())
    }

    @Test
    fun `select none because pref not match`() = runTest {
        addMedia(media(alliance = "字幕组"), media(alliance = "字幕组"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(alliance = "a")
        assertEquals(0, selector.preferredCandidatesMedia.first().size)
    }

    @Test
    fun `select with user override no preference`() = runTest {
        addMedia(media(alliance = "字幕组"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(alliance = "组")
        assertEquals(0, selector.preferredCandidatesMedia.first().size)
        selector.alliance.removePreference()
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
    }

    @Test
    fun `select with user override no preference then prefer`() = runTest {
        addMedia(media(alliance = "字幕组"), media(alliance = "字幕组2"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(alliance = "组")
        assertEquals(0, selector.preferredCandidatesMedia.first().size)
        selector.alliance.removePreference()
        assertEquals(2, selector.preferredCandidatesMedia.first().size)
        selector.alliance.prefer("字幕组")
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Default selection
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `select default first with no preference`() = runTest {
        val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS"))
        addMedia(
            target,
            media(alliance = "字幕组2", subtitleLanguages = listOf("CHT")),
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组2", subtitleLanguages = listOf("CHS")),
        )
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `select default by alliance`() = runTest {
        val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS"))
        addMedia(
            media(alliance = "字幕组2", subtitleLanguages = listOf("CHT")),
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            target,
            media(alliance = "字幕组2", subtitleLanguages = listOf("CHS")),
        )
        selector.alliance.prefer("字幕组")
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `select default by alliance regex`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleLanguages = listOf("CHS")),
            media(alliance = "字幕组2", subtitleLanguages = listOf("CHT")),
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组4", subtitleLanguages = listOf("CHS", "CHT")).also { target = it },
            media(alliance = "字幕组5", subtitleLanguages = listOf("CHS")),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(alliancePatterns = listOf("4"))
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `select default by subtitle language`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleLanguages = listOf("CHS")),
            media(alliance = "字幕组2", subtitleLanguages = listOf("CHT")),
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组4", subtitleLanguages = listOf("CHS", "CHT", "R")).also { target = it },
            media(alliance = "字幕组5", subtitleLanguages = listOf("CHS")),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(subtitleLanguageId = "R")
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `select default by first fallback subtitle language`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleLanguages = listOf("CHS")),
            media(alliance = "字幕组2", subtitleLanguages = listOf("CHT")),
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组4", subtitleLanguages = listOf("CHS", "CHT", "R")).also { target = it },
            media(alliance = "字幕组5", subtitleLanguages = listOf("CHS")),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(fallbackSubtitleLanguageIds = listOf("R", "CHS"))
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `select default by resolution`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleLanguages = listOf("CHS")),
            media(alliance = "字幕组2", resolution = "Special").also { target = it },
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组4", subtitleLanguages = listOf("CHS", "CHT", "R")),
            media(alliance = "字幕组5", subtitleLanguages = listOf("CHS")),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(resolution = "Special")
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `select default by first fallback resolution`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleLanguages = listOf("CHS")),
            media(alliance = "字幕组2", resolution = "Special").also { target = it },
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组4", subtitleLanguages = listOf("CHS", "CHT", "R")),
            media(alliance = "字幕组5", subtitleLanguages = listOf("CHS")),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(fallbackResolutions = listOf("Special", "1080P"))
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `do not select default when user already selected`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleLanguages = listOf("CHS")),
            media(alliance = "字幕组2", resolution = "Special").also { target = it },
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组4", subtitleLanguages = listOf("CHS", "CHT", "R")),
            media(alliance = "字幕组5", subtitleLanguages = listOf("CHS")),
        )
        selector.select(target)
        assertEquals(null, selector.trySelectDefault())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Media source precedence
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `select first preferred media source`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(sourceId = "1"),
            media(sourceId = "2").also { target = it },
            media(sourceId = "1"),
            media(sourceId = "1"),
            media(sourceId = "1"),
        )
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            mediaSourcePrecedence = listOf("2", "1"),
        )
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `select second preferred media source`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(sourceId = "1"),
            media(sourceId = "2").also { target = it },
            media(sourceId = "1"),
            media(sourceId = "1"),
            media(sourceId = "1"),
        )
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            mediaSourcePrecedence = listOf("3", "2", "1"),
        )
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `select with no media source preference`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(sourceId = "1").also { target = it },
            media(sourceId = "2"),
            media(sourceId = "1"),
            media(sourceId = "1"),
            media(sourceId = "1"),
        )
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            mediaSourcePrecedence = emptyList(),
        )
        assertEquals(target, selector.trySelectDefault())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Cached
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `always show cached even if preferences dont match`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleLanguages = listOf("CHS")),
            media(
                alliance = "字幕组2",
                subtitleLanguages = listOf("CHS"),
                kind = MediaSourceKind.LocalCache,
            ).also { target = it },
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组4", subtitleLanguages = listOf("CHS", "CHT", "R")),
            media(alliance = "字幕组5", subtitleLanguages = listOf("CHS")),
        )
        selector.alliance.prefer("a")
        assertEquals(listOf(target), selector.preferredCandidatesMedia.first())
    }

    @Test
    fun `select cached`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleLanguages = listOf("CHS")),
            media(
                alliance = "字幕组2", resolution = "Special",
                kind = MediaSourceKind.LocalCache,
            ).also { target = it },
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组4", subtitleLanguages = listOf("CHS", "CHT", "R")),
            media(alliance = "字幕组5", subtitleLanguages = listOf("CHS")),
        )
        assertEquals(target, selector.trySelectCached())
    }

    ///////////////////////////////////////////////////////////////////////////
    // 隐藏生肉
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `can hide raw`() = runTest {
        val target: DefaultMedia
        savedDefaultPreference.value = MediaPreference.Companion.Empty.copy(
            alliance = "字幕组2",
            showWithoutSubtitle = true,
        )
        addMedia(
            media(alliance = "字幕组1", subtitleLanguages = listOf("CHS")),
            media(
                alliance = "字幕组2", resolution = "Special",
                subtitleLanguages = listOf(),
            ).also { target = it },
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组4", subtitleLanguages = listOf("CHS", "CHT", "R")),
            media(alliance = "字幕组5", subtitleLanguages = listOf("CHS")),
        )
        assertEquals(target, selector.trySelectDefault())
        savedDefaultPreference.value = MediaPreference.Companion.Empty.copy(
            alliance = "字幕组2",
            showWithoutSubtitle = false,
        )
        selector.unselect()
        assertEquals(null, selector.trySelectDefault())
    }

    // 当 Media 有 extraFiles.subtitles 时不隐藏
    @Test
    fun `do not hide media with extraFiles`() = runTest {
        val target: DefaultMedia
        savedDefaultPreference.value = MediaPreference.Companion.Empty.copy(
            alliance = "字幕组2",
            showWithoutSubtitle = true,
        )
        addMedia(
            media(alliance = "字幕组1", subtitleLanguages = listOf("CHS")),
            media(
                alliance = "字幕组2", resolution = "Special",
                subtitleLanguages = listOf(),
                extraFiles = MediaExtraFiles(listOf(Subtitle("dummy"))),
            ).also { target = it },
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组4", subtitleLanguages = listOf("CHS", "CHT", "R")),
            media(alliance = "字幕组5", subtitleLanguages = listOf("CHS")),
        )
        assertEquals(target, selector.trySelectDefault())
        savedDefaultPreference.value = MediaPreference.Companion.Empty.copy(
            alliance = "字幕组2",
            showWithoutSubtitle = false,
        )
        selector.unselect()
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `can select cached raw`() = runTest {
        val target: DefaultMedia
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(
            alliance = "字幕组2",
            showWithoutSubtitle = true,
        )
        addMedia(
            media(alliance = "字幕组1", subtitleLanguages = listOf("CHS")),
            media(
                alliance = "字幕组2", resolution = "Special",
                subtitleLanguages = listOf(),
                kind = MediaSourceKind.LocalCache,
            ).also { target = it },
            media(alliance = "字幕组3", subtitleLanguages = listOf("CHS", "CHT")),
            media(alliance = "字幕组4", subtitleLanguages = listOf("CHS", "CHT", "R")),
            media(alliance = "字幕组5", subtitleLanguages = listOf("CHS")),
        )
        assertEquals(target, selector.trySelectCached())
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(
            alliance = "字幕组2",
            showWithoutSubtitle = false,
        )
        selector.unselect()
        assertEquals(target, selector.trySelectCached())
    }

    ///////////////////////////////////////////////////////////////////////////
    // 已完结后隐藏单集资源
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `can hide single episode after finished`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(true)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(
            hideSingleEpisodeForCompleted = true,
            preferSeasons = false,
        )
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.Companion.single("1")),
            media(
                alliance = "字幕组2", episodeRange = EpisodeRange.Companion.range(1, 2),
            ).also { target = it },
            media(
                alliance = "字幕组6", episodeRange = EpisodeRange.Companion.season(1),
            ),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.Companion.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.Companion.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.Companion.single("4")),
        )
        assertEquals(2, selector.preferredCandidatesMedia.first().size)
        assertEquals(target, selector.preferredCandidatesMedia.first()[0])
    }

    @Test
    fun `do not hide single episode after finished if settings off`() = runTest {
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(true)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(
            hideSingleEpisodeForCompleted = false,
            preferSeasons = false,
        )
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.Companion.single("1")),
            media(alliance = "字幕组2", episodeRange = EpisodeRange.Companion.range(1, 2)),
            media(alliance = "字幕组6", episodeRange = EpisodeRange.Companion.season(1)),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.Companion.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.Companion.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.Companion.single("4")),
        )
        assertEquals(6, selector.preferredCandidatesMedia.first().size)
    }

    ///////////////////////////////////////////////////////////////////////////
    // 已完结后优先选择季度全集资源
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `prefer seasons after finished`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(true)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(
            hideSingleEpisodeForCompleted = false,
            preferSeasons = true,
        )
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE // 允许选择 1080P 等
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.Companion.single("1")),
            media(alliance = "字幕组2", episodeRange = EpisodeRange.Companion.season(1)).also { target = it },
            media(alliance = "字幕组3", episodeRange = EpisodeRange.Companion.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.Companion.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.Companion.single("4")),
        )
        assertEquals(5, selector.preferredCandidatesMedia.first().size)
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `do not prefer season if disabled`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(true)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(
            hideSingleEpisodeForCompleted = false,
            preferSeasons = false,
        )
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.Companion.single("1")).also { target = it },
            media(alliance = "字幕组2", episodeRange = EpisodeRange.Companion.season(1)),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.Companion.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.Companion.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.Companion.single("4")),
        )
        assertEquals(5, selector.preferredCandidatesMedia.first().size)
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `do not prefer season if not matched`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(true)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(
            hideSingleEpisodeForCompleted = false,
            preferSeasons = true,
        )
        savedUserPreference.value = MediaPreference.Companion.Empty
        savedDefaultPreference.value = MediaPreference.Companion.Empty // 啥都不要, 就一定会 fallback 成选第一个
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.Companion.single("1")).also { target = it },
            media(alliance = "字幕组2", episodeRange = EpisodeRange.Companion.season(1)),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.Companion.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.Companion.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.Companion.single("4")),
        )
        assertEquals(5, selector.preferredCandidatesMedia.first().size)
        assertEquals(target, selector.trySelectDefault())
    }

    ///////////////////////////////////////////////////////////////////////////
    // 当资源条目名称精确匹配其他季度名称时自动排除 #1385
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `exclude other seasons - control group`() = runTest {
        // 对照组

        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectSeriesInfo = SubjectSeriesInfo.Companion.Fallback.copy(seriesSubjectNamesWithoutSelf = setOf("条目名称I")),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(
                alliance = "字幕组1", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称", subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ).also { target = it },
            media(
                alliance = "字幕组2", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称", subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ),
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, null),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(
            target,
            selector.trySelectDefault(),
        )
    }


    // #1827
    @Test
    fun `do not exclude exactly matched by FromSequelSeason if the subject has OVA series`() = runTest {
        var target: Media
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            subjectCompleted = true,
            subjectSeriesInfo = SubjectSeriesInfo(
                seasonSort = 2,
                sequelSubjectNames = setOf(
                    "暗杀教室 Episode:0 相遇的时间",
                    "暗殺教室 Episode:0 出会いの時間",
                    "暗杀教室 第0话",
                    "暗杀教室 OVA",
                    "暗杀教室 第二季",
                    "暗殺教室 第2期",
                    "暗杀教室 第二季 课外授课篇",
                    "暗殺教室 第2期 課外授業編",
                ),
                seriesSubjectNamesWithoutSelf = setOf(
                    "暗杀教室 第二季",
                    "暗殺教室 第2期",
                ),
            ),
            subjectInfo = SubjectInfo.Companion.Empty.copy(name = "暗杀教室", nameCn = "暗杀教室"),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE

        addMedia(
            media(
                // kept
                alliance = "咕咕新线(优先使用)",
                episodeRange = EpisodeRange.Companion.single("1"),
                kind = MediaSourceKind.WEB,
                subjectName = "暗杀教室",
                episodeName = "第01集",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
                originalTitle = "暗杀教室 第01集",
            ).also { target = it },
        )

        assertEquals(1, selector.preferredCandidates.first().size)
        assertEquals(listOf(null), selector.preferredCandidates.first().map { it.exclusionReason })
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `exclude other seasons`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectSeriesInfo = SubjectSeriesInfo.Companion.Fallback.copy(seriesSubjectNamesWithoutSelf = setOf("条目名称I")),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(
                // filtered out
                alliance = "字幕组1", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称I", subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ),
            media(
                // kept
                alliance = "字幕组2", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称", subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ).also { target = it },
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, MediaExclusionReason.FromSeriesSeason),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(
            target,
            selector.trySelectDefault(),
        )
    }

    @Test
    fun `exclude other seasons does not filter out season of longer name`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectSeriesInfo = SubjectSeriesInfo.Companion.Fallback.copy(seriesSubjectNamesWithoutSelf = setOf("条目名称I")),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(
                // filtered out
                alliance = "字幕组1", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称I", subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ),
            media(
                // kept
                alliance = "字幕组2", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称III", subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ).also { target = it },
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, MediaExclusionReason.FromSeriesSeason),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(
            target,
            selector.trySelectDefault(),
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // trySelectFromMediaSources
    ///////////////////////////////////////////////////////////////////////////
    /**
     * 注意, [me.him188.ani.app.domain.media.selector.MediaSelector.trySelectFromMediaSources] 也在 [MediaSelectorFastSelectSourcesTest] 中测试.
     */
    @Suppress("unused")
    private val _doc1 = Unit

    @Test
    fun `trySelectFromMediaSources does not override user selection by default`() = runTest {
        val userSelection: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any
        savedDefaultPreference.value = MediaPreference.Companion.Any
        addMedia(
            media(
                sourceId = "1",
                episodeRange = EpisodeRange.Companion.single("1"),
                kind = MediaSourceKind.WEB,
            ).also { userSelection = it },
            media(sourceId = "2", episodeRange = EpisodeRange.Companion.season(1), kind = MediaSourceKind.WEB),
            media(sourceId = "3", episodeRange = EpisodeRange.Companion.single("2"), kind = MediaSourceKind.WEB),
            media(sourceId = "4", episodeRange = EpisodeRange.Companion.single("3"), kind = MediaSourceKind.WEB),
            media(sourceId = "5", episodeRange = EpisodeRange.Companion.single("4"), kind = MediaSourceKind.WEB),
        )
        selector.select(userSelection)
        assertEquals(1, selector.preferredCandidatesMedia.first().size) // 因为会自动设置 preference
        assertEquals(
            null,
            selector.trySelectFromMediaSources(
                listOf("2"),
            ),
        )
    }

    @Test
    fun `trySelectFromMediaSources can override user selection`() = runTest {
        val userSelection: DefaultMedia
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any
        savedDefaultPreference.value = MediaPreference.Companion.Any
        addMedia(
            media(
                sourceId = "1",
                episodeRange = EpisodeRange.Companion.single("1"),
                kind = MediaSourceKind.WEB,
            ).also { userSelection = it },
            media(
                sourceId = "2",
                episodeRange = EpisodeRange.Companion.season(1),
                kind = MediaSourceKind.WEB,
            ).also { target = it },
            media(sourceId = "3", episodeRange = EpisodeRange.Companion.single("2"), kind = MediaSourceKind.WEB),
            media(sourceId = "4", episodeRange = EpisodeRange.Companion.single("3"), kind = MediaSourceKind.WEB),
            media(sourceId = "5", episodeRange = EpisodeRange.Companion.single("4"), kind = MediaSourceKind.WEB),
        )
        selector.select(userSelection)
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
        assertEquals(
            target,
            selector.trySelectFromMediaSources(
                listOf("2"),
                overrideUserSelection = true,
                allowNonPreferred = true,
            ),
        )
    }

    @Test
    fun `trySelectFromMediaSources does not update preferences`() = runTest {
        val userSelection: DefaultMedia
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any
        savedDefaultPreference.value = MediaPreference.Companion.Any
        addMedia(
            media(
                sourceId = "1",
                episodeRange = EpisodeRange.Companion.single("1"),
                kind = MediaSourceKind.WEB,
            ).also { userSelection = it },
            media(
                sourceId = "2",
                episodeRange = EpisodeRange.Companion.season(1),
                kind = MediaSourceKind.WEB,
            ).also { target = it },
            media(sourceId = "3", episodeRange = EpisodeRange.Companion.single("2"), kind = MediaSourceKind.WEB),
            media(sourceId = "4", episodeRange = EpisodeRange.Companion.single("3"), kind = MediaSourceKind.WEB),
            media(sourceId = "5", episodeRange = EpisodeRange.Companion.single("4"), kind = MediaSourceKind.WEB),
        )
        selector.select(userSelection)
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
        assertEquals(
            target,
            selector.trySelectFromMediaSources(
                listOf("2"),
                overrideUserSelection = true,
                allowNonPreferred = true,
            ),
        )
        assertEquals(
            OptionalPreference.Companion.prefer("1"),
            selector.mediaSourceId.userSelected.first(),
        )
    }

    @Test
    fun `trySelectFromMediaSources does not select from blacklisted`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any
        savedDefaultPreference.value = MediaPreference.Companion.Any
        addMedia(
            media(sourceId = "1", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB),
            media(
                sourceId = "2",
                episodeRange = EpisodeRange.Companion.season(1),
                kind = MediaSourceKind.WEB,
            ).also { target = it },
            media(sourceId = "3", episodeRange = EpisodeRange.Companion.single("2"), kind = MediaSourceKind.WEB),
            media(sourceId = "4", episodeRange = EpisodeRange.Companion.single("3"), kind = MediaSourceKind.WEB),
            media(sourceId = "5", episodeRange = EpisodeRange.Companion.single("4"), kind = MediaSourceKind.WEB),
        )
        assertEquals(5, selector.preferredCandidatesMedia.first().size)
        assertEquals(null, selector.trySelectFromMediaSources(listOf("2"), blacklistMediaIds = setOf(target.mediaId)))
    }

    @Test
    fun `trySelectFromMediaSources order source count smaller than actual count`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any
        savedDefaultPreference.value = MediaPreference.Companion.Any
        addMedia(
            media(sourceId = "1", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB),
            media(
                sourceId = "2",
                episodeRange = EpisodeRange.Companion.season(1),
                kind = MediaSourceKind.WEB,
            ).also { target = it },
            media(sourceId = "3", episodeRange = EpisodeRange.Companion.single("2"), kind = MediaSourceKind.WEB),
            media(sourceId = "4", episodeRange = EpisodeRange.Companion.single("3"), kind = MediaSourceKind.WEB),
            media(sourceId = "5", episodeRange = EpisodeRange.Companion.single("4"), kind = MediaSourceKind.WEB),
        )
        assertEquals(5, selector.preferredCandidatesMedia.first().size)
        assertEquals(target, selector.trySelectFromMediaSources(listOf("2")))
    }

    @Test
    fun `trySelectFromMediaSources order source count greater than actual count`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any
        savedDefaultPreference.value = MediaPreference.Companion.Any
        addMedia(
            media(sourceId = "0", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB),
            media(
                sourceId = "2",
                episodeRange = EpisodeRange.Companion.season(1),
                kind = MediaSourceKind.WEB,
            ).also { target = it },
        )
        assertEquals(2, selector.preferredCandidatesMedia.first().size)
        assertEquals(target, selector.trySelectFromMediaSources(listOf("1", "2", "3")))
    }

    @Test
    fun `trySelectFromMediaSources order source no intersection with actual count`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any
        savedDefaultPreference.value = MediaPreference.Companion.Any
        addMedia(
            media(sourceId = "0", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB),
            media(
                sourceId = "4",
                episodeRange = EpisodeRange.Companion.season(1),
                kind = MediaSourceKind.WEB,
            ).also { target = it },
        )
        assertEquals(2, selector.preferredCandidatesMedia.first().size)
        assertEquals(null, selector.trySelectFromMediaSources(listOf("1", "2", "3")))
    }

    @Test
    fun `trySelectFromMediaSources does not select non-preferred`() = runTest {
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Empty.copy(
            alliance = "123123123", // matches nothing
        )
        savedDefaultPreference.value = MediaPreference.Companion.Empty // 啥都不要, 就一定会 fallback 成选第一个
        addMedia(
            media(sourceId = "1", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB),
        )
        assertEquals(0, selector.preferredCandidatesMedia.first().size)
        assertEquals(1, selector.filteredCandidatesMedia.first().size)
        assertEquals(null, selector.trySelectFromMediaSources(listOf("1"), allowNonPreferred = false))
    }

    @Test
    fun `trySelectFromMediaSources does not select non-preferred if media source ids do not match`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any.copy(
            alliance = "123123123", // matches nothing
        )
        savedDefaultPreference.value = MediaPreference.Companion.Any
        addMedia(
            media(
                sourceId = "1",
                episodeRange = EpisodeRange.Companion.single("1"),
                kind = MediaSourceKind.WEB,
            ).also { target = it },
        )
        assertEquals(0, selector.preferredCandidatesMedia.first().size)
        assertEquals(1, selector.filteredCandidatesMedia.first().size)
        assertEquals(null, selector.trySelectFromMediaSources(listOf("0"), allowNonPreferred = true))
    }

    @Test
    fun `trySelectFromMediaSources can select non-preferred`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any.copy(
            alliance = "123123123", // matches nothing
        )
        savedDefaultPreference.value = MediaPreference.Companion.Any
        addMedia(
            media(
                sourceId = "1",
                episodeRange = EpisodeRange.Companion.single("1"),
                kind = MediaSourceKind.WEB,
            ).also { target = it },
        )
        assertEquals(0, selector.preferredCandidatesMedia.first().size)
        assertEquals(1, selector.filteredCandidatesMedia.first().size)
        assertEquals(target, selector.trySelectFromMediaSources(listOf("1"), allowNonPreferred = true))
    }

    @Test
    fun `trySelectFromMediaSources respects to preference`() = runTest {
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any.copy(
            alliance = "2", // 用户偏好 2
        )
        savedDefaultPreference.value = MediaPreference.Companion.Any
        val media1 =
            media(sourceId = "1", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB)
        val media2 = media(
            sourceId = "2",
            alliance = "2",
            episodeRange = EpisodeRange.Companion.single("2"),
            kind = MediaSourceKind.WEB,
        )
        addMedia(
            media1,
            media2,
        )
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
        assertEquals(2, selector.filteredCandidatesMedia.first().size)
        assertEquals(
            media2,
            selector.trySelectFromMediaSources(
                listOf("1", "2"),  // 顺序是 1 更先, 但是用户偏好 2, 所以要选择 2
                allowNonPreferred = false,
            ),
        )
        selector.unselect()
        assertEquals(
            media2,
            selector.trySelectFromMediaSources(
                listOf("1", "2"),
                allowNonPreferred = true, // 仍然选 2, 因为只有在没有任何资源匹配偏好时才会选别的
            ),
        )
    }

    @Test
    fun `trySelectFromMediaSources respects to preferred mediaSourceId`() = runTest {
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any.copy(
            mediaSourceId = "2", // 用户偏好 2
        )
        savedDefaultPreference.value = MediaPreference.Companion.Empty
        val media1 =
            media(sourceId = "1", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB)
        val media2 = media(
            sourceId = "2",
            alliance = "2",
            episodeRange = EpisodeRange.Companion.single("2"),
            kind = MediaSourceKind.WEB,
        )
        addMedia(
            media1,
            media2,
        )
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
        assertEquals(2, selector.filteredCandidatesMedia.first().size)
        assertEquals(
            media2,
            selector.trySelectFromMediaSources(
                listOf("1", "2"),  // 顺序是 1 更先, 但是用户偏好 2, 所以要选择 2
                allowNonPreferred = false,
            ),
        )
        selector.unselect()
        assertEquals(
            media2,
            selector.trySelectFromMediaSources(
                listOf("1", "2"),
                allowNonPreferred = true, // 仍然选 2, 因为只有在没有任何资源匹配偏好时才会选别的
            ),
        )


        // 对照组
        savedUserPreference.value = MediaPreference.Companion.Any // 没有任何偏好, 按 order 选
        selector.unselect()
        assertEquals(
            media1,
            selector.trySelectFromMediaSources(
                listOf("1", "2"),
                allowNonPreferred = true,
            ),
        )
    }

    @Test
    fun `trySelectFromMediaSources selects non-preferred if all preferred ones are blacklisted`() = runTest {
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any.copy(
            alliance = "2", // 用户偏好 2
        )
        savedDefaultPreference.value = MediaPreference.Companion.Any
        val media1 =
            media(sourceId = "1", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB)
        val media2 = media(
            sourceId = "2",
            alliance = "2",
            episodeRange = EpisodeRange.Companion.single("2"),
            kind = MediaSourceKind.WEB,
        )
        addMedia(
            media1,
            media2,
        )
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
        assertEquals(2, selector.filteredCandidatesMedia.first().size)
        assertEquals(
            null, // 用户偏好 2, 但是 2 被黑名单了, 而 allowNonPreferred = false, 选不了别的, 所以是 `null`
            selector.trySelectFromMediaSources(
                listOf("1", "2"),
                allowNonPreferred = false,
                blacklistMediaIds = setOf(media2.mediaId),
            ),
        )
        selector.unselect()
        assertEquals(
            media1, // 用户偏好 2, 但是 2 被黑名单了, 但是 allowNonPreferred = true, 所以可以选别的
            selector.trySelectFromMediaSources(
                listOf("1", "2"),
                allowNonPreferred = true,
                blacklistMediaIds = setOf(media2.mediaId),
            ),
        )
    }

    @Test
    fun `trySelectFromMediaSources prefers high similarity`() = runTest {
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            false,
            subjectInfo = SubjectInfo.Companion.Empty.copy(nameCn = "孤独摇滚", name = "Bocchi The Rock!"),
            episodeInfo = EpisodeInfo.Companion.Empty.copy(sort = EpisodeSort(1)),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(preferKind = MediaSourceKind.WEB)
        savedUserPreference.value = MediaPreference.Companion.Any
        savedDefaultPreference.value = MediaPreference.Companion.Any
        val media1 = media(
            sourceId = "1",
            subjectName = "孤独摇滚 第二季",
            episodeRange = EpisodeRange.Companion.single("1"),
            kind = MediaSourceKind.WEB,
        )
        val media2 = media(
            sourceId = "2",
            subjectName = "孤独摇滚",
            episodeRange = EpisodeRange.Companion.single("1"),
            kind = MediaSourceKind.WEB,
        )
        addMedia(media1, media2)
        assertEquals(2, selector.preferredCandidatesMedia.first().size)
        assertEquals(2, selector.filteredCandidatesMedia.first().size)
        assertEquals(
            media2, // 数据源 1 优先级更高, 但是 media2 有更高的相似度, 所以选 media2
            selector.trySelectFromMediaSources(
                listOf("1", "2"),
                allowNonPreferred = true,
            ),
        )
    }

    @Test
    fun `trySelectFromMediaSources prefers high similarity 2`() = runTest {
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            false,
            subjectInfo = SubjectInfo.Companion.Empty.copy(nameCn = "日常", name = "Nichijou"),
            episodeInfo = EpisodeInfo.Companion.Empty.copy(sort = EpisodeSort(1)),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(preferKind = MediaSourceKind.WEB)
        savedUserPreference.value = MediaPreference.Companion.Any
        savedDefaultPreference.value = MediaPreference.Companion.Any
        val media1 = media(
            sourceId = "1",
            subjectName = "版本日常",
            episodeRange = EpisodeRange.Companion.single("1"),
            kind = MediaSourceKind.WEB,
        )
        val media2 = media(
            sourceId = "2",
            subjectName = "日常",
            episodeRange = EpisodeRange.Companion.single("1"),
            kind = MediaSourceKind.WEB,
        )
        addMedia(media1, media2)
        assertEquals(2, selector.preferredCandidatesMedia.first().size)
        assertEquals(2, selector.filteredCandidatesMedia.first().size)
        assertEquals(
            media2, // 数据源 1 优先级更高, 但是 media2 有更高的相似度, 所以选 media2
            selector.trySelectFromMediaSources(
                listOf("1", "2"),
                allowNonPreferred = true,
            ),
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // 排除第二季 (#1324)
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `respect to subjectSequelNames`() = runTest {
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            false,
            subjectSequelNames = setOf("孤独摇滚 第二季"),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy()
        savedUserPreference.value = MediaPreference.Companion.Any.copy(alliance = "1") // prefer 被过滤掉的那个, 以便测试
        savedDefaultPreference.value = MediaPreference.Companion.Any
        val target: DefaultMedia
        val media1 = media(
            sourceId = "1",
            originalTitle = "孤独摇滚 第二季",
            episodeRange = EpisodeRange.Companion.single("1"),
            kind = MediaSourceKind.WEB,
        )
        val media2 = media(
            sourceId = "2",
            originalTitle = "孤独摇滚",
            alliance = "2",
            episodeRange = EpisodeRange.Companion.single("2"),
            kind = MediaSourceKind.WEB,
        ).also {
            target = it
        }
        addMedia(media1, media2)
        assertEquals(0, selector.preferredCandidatesMedia.first().size)
        assertEquals(1, selector.filteredCandidatesMedia.first().size)
        assertEquals(
            null,
            selector.trySelectDefault(),
        )
        assertEquals(
            target,
            selector.trySelectFromMediaSources(
                listOf("1", "2"),
                allowNonPreferred = true,
            ),
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // 优先选择在线数据源
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `prefer any sources`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(
            preferKind = null,
        )
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.Companion.single("1")).also { target = it },
            media(
                alliance = "字幕组2", episodeRange = EpisodeRange.Companion.single("2"), kind = MediaSourceKind.WEB,
            ),
            media(alliance = "字幕组6", episodeRange = EpisodeRange.Companion.single("1")),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.Companion.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.Companion.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.Companion.single("4")),
        )
        assertEquals(6, selector.preferredCandidatesMedia.first().size)
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `prefer bt sources`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(
            preferKind = MediaSourceKind.BitTorrent,
        )
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB),
            media(
                alliance = "字幕组2", episodeRange = EpisodeRange.Companion.single("2"), kind = MediaSourceKind.WEB,
            ),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.Companion.single("1")).also { target = it },
            media(alliance = "字幕组4", episodeRange = EpisodeRange.Companion.single("2")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.Companion.single("3")),
            media(alliance = "字幕组6", episodeRange = EpisodeRange.Companion.single("4")),
        )
        assertEquals(6, selector.preferredCandidatesMedia.first().size)
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `prefer web sources`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(
            preferKind = MediaSourceKind.WEB,
        )
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.Companion.single("1")),
            media(
                alliance = "字幕组2", episodeRange = EpisodeRange.Companion.single("2"), kind = MediaSourceKind.WEB,
            ).also { target = it },
            media(alliance = "字幕组6", episodeRange = EpisodeRange.Companion.single("1")),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.Companion.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.Companion.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.Companion.single("4")),
        )
        assertEquals(6, selector.preferredCandidatesMedia.first().size)
        assertEquals(target, selector.trySelectDefault())
    }

    ///////////////////////////////////////////////////////////////////////////
    // sort
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `sort by date`() = runTest {
        // https://github.com/open-ani/ani/issues/445
        val m1 = media(alliance = "字幕组1", publishedTime = 1)
        val m4 = media(alliance = "字幕组2", publishedTime = 4)
        val m3 = media(alliance = "字幕组6", publishedTime = 3)
        val m2 = media(alliance = "字幕组5", publishedTime = 2)
        addMedia(m1, m4, m3, m2)
        assertEquals(listOf(m4, m3, m2, m1), selector.filteredCandidatesMedia.first())
        assertEquals(listOf(m4, m3, m2, m1), selector.preferredCandidatesMedia.first())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Events
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `do not save subtitle language when it is ambiguous`() = runTest {
        savedUserPreference.value = MediaPreference.Companion.Empty
        savedDefaultPreference.value = MediaPreference.Companion.Empty // 方便后面比较
        val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS", "CHT"))
        addMedia(target)
        runCollectEvents {
            selector.select(target)
        }.run {
            assertEquals(1, onSelect.size)
            assertEquals(
                SelectEvent(
                    media = target,
                    subtitleLanguageId = null,
                    previousMedia = null,
                ),
                onSelect.first()
            )
            assertEquals(1, onChangePreference.size)
            assertEquals(
                // 
                MediaPreference.Companion.Empty.copy(
                    alliance = "字幕组",
                    resolution = target.properties.resolution,
                    mediaSourceId = "dmhy",
                ),
                onChangePreference.first(),
            )
        }
    }

    @Test
    fun `event select`() = runTest {
        savedUserPreference.value = MediaPreference.Companion.Empty
        savedDefaultPreference.value = MediaPreference.Companion.Empty // 方便后面比较
        val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS"))
        addMedia(target)
        runCollectEvents {
            selector.select(target)
        }.run {
            assertEquals(1, onSelect.size)
            assertEquals(
                SelectEvent(
                    media = target,
                    subtitleLanguageId = null,
                    previousMedia = null,
                ),
                onSelect.first()
            )
            assertEquals(1, onChangePreference.size)
            assertEquals(
                MediaPreference.Companion.Empty.copy(
                    alliance = "字幕组",
                    resolution = target.properties.resolution,
                    subtitleLanguageId = "CHS",
                    mediaSourceId = "dmhy",
                ),
                onChangePreference.first(),
            )
        }
    }

    @Test
    fun `event select replaces previous`() = runTest {
        savedUserPreference.value = MediaPreference.Companion.Empty
        savedDefaultPreference.value = MediaPreference.Companion.Empty

        val first = media(alliance = "A", subtitleLanguages = listOf("CHS"))
        val second = media(alliance = "B", subtitleLanguages = listOf("CHT"))

        addMedia(first)
        addMedia(second)

        selector.select(first)

        runCollectEvents {
            selector.select(second)
        }.run {
            assertEquals(1, onSelect.size)
            val event = onSelect.first()

            assertEquals(second, event.media)
            assertEquals(null, event.subtitleLanguageId)
            assertEquals(first, event.previousMedia)

            assertEquals(1, onChangePreference.size)
            assertEquals(
                MediaPreference.Companion.Empty.copy(
                    alliance = "B",
                    resolution = second.properties.resolution,
                    subtitleLanguageId = "CHT",
                    mediaSourceId = "dmhy"
                ),
                onChangePreference.first()
            )
        }
    }


    @Test
    fun `event prefer`() = runTest {
        savedUserPreference.value = MediaPreference.Companion.Empty
        savedDefaultPreference.value = MediaPreference.Companion.Empty // 方便后面比较
        val target = media(alliance = "字幕组")
        addMedia(target)
        runCollectEvents {
            selector.alliance.prefer("字幕组")
        }.run {
            assertEquals(0, onSelect.size)
            assertEquals(1, onChangePreference.size)
            assertEquals(
                MediaPreference.Companion.Empty.copy(
                    alliance = "字幕组",
                ),
                onChangePreference.first(),
            )
        }
    }

    class CollectedEvents(
        val onSelect: MutableList<SelectEvent> = mutableListOf(),
        val onChangePreference: MutableList<MediaPreference> = mutableListOf(),
    )

    private suspend fun runCollectEvents(block: suspend () -> Unit): CollectedEvents {
        return CollectedEvents().apply {
            cancellableCoroutineScope {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    selector.events.onSelect.collect {
                        onSelect.add(it)
                    }
                }
                launch(start = CoroutineStart.UNDISPATCHED) {
                    selector.events.onChangePreference.collect {
                        onChangePreference.add(it)
                    }
                }
                try {
                    block()
                    yield()
                } finally {
                    cancelScope()
                }
            }
        }
    }
}