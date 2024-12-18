/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.Subtitle
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.utils.coroutines.cancellableCoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
        assertEquals(mediaList.value, selector.filteredCandidates.first())
    }

    @Test
    fun `select by user alliance`() = runTest {
        val media = media(alliance = "字幕组")
        addMedia(media, media(alliance = "字幕组2"))
        savedUserPreference.value = DEFAULT_PREFERENCE.copy(alliance = "字幕组")
        assertEquals(media, selector.filteredCandidates.first().single())
    }

    @Test
    fun `select by default alliance`() = runTest {
        val media = media(alliance = "字幕组")
        addMedia(media, media(alliance = "字幕组2"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(alliance = "字幕组")
        assertEquals(media, selector.filteredCandidates.first().single())
    }

    @Test
    fun `select by user resolution`() = runTest {
        val media = media(resolution = "字幕组")
        addMedia(media, media(resolution = "字幕组2"))
        savedUserPreference.value = DEFAULT_PREFERENCE.copy(resolution = "字幕组")
        assertEquals(media, selector.filteredCandidates.first().single())
    }

    @Test
    fun `select by default resolution`() = runTest {
        val media = media(resolution = "字幕组")
        addMedia(media, media(resolution = "字幕组2"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(resolution = "字幕组")
        assertEquals(media, selector.filteredCandidates.first().single())
    }

    @Test
    fun `select by user subtitle one of`() = runTest {
        val media = media(subtitleLanguages = listOf("字幕组", "a"))
        addMedia(media, media(subtitleLanguages = listOf("b")))
        savedUserPreference.value = DEFAULT_PREFERENCE.copy(subtitleLanguageId = "a")
        assertEquals(media, selector.filteredCandidates.first().single())
    }

    @Test
    fun `select by default subtitle one of`() = runTest {
        val media = media(subtitleLanguages = listOf("字幕组", "a"))
        addMedia(media, media(subtitleLanguages = listOf("b")))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(subtitleLanguageId = "a")
        assertEquals("a", selector.subtitleLanguageId.finalSelected.first())
        assertEquals(media, selector.filteredCandidates.first().single())
    }

    @Test
    fun `select none because pref not match`() = runTest {
        addMedia(media(alliance = "字幕组"), media(alliance = "字幕组"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(alliance = "a")
        assertEquals(0, selector.filteredCandidates.first().size)
    }

    @Test
    fun `select with user override no preference`() = runTest {
        addMedia(media(alliance = "字幕组"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(alliance = "组")
        assertEquals(0, selector.filteredCandidates.first().size)
        selector.alliance.removePreference()
        assertEquals(1, selector.filteredCandidates.first().size)
    }

    @Test
    fun `select with user override no preference then prefer`() = runTest {
        addMedia(media(alliance = "字幕组"), media(alliance = "字幕组2"))
        savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(alliance = "组")
        assertEquals(0, selector.filteredCandidates.first().size)
        selector.alliance.removePreference()
        assertEquals(2, selector.filteredCandidates.first().size)
        selector.alliance.prefer("字幕组")
        assertEquals(1, selector.filteredCandidates.first().size)
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
    fun `always show cached even if preferences don't match`() = runTest {
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
        assertEquals(listOf(target), selector.filteredCandidates.first())
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
        savedDefaultPreference.value = MediaPreference.Empty.copy(
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
        savedDefaultPreference.value = MediaPreference.Empty.copy(
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
        savedDefaultPreference.value = MediaPreference.Empty.copy(
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
        savedDefaultPreference.value = MediaPreference.Empty.copy(
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
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(
            hideSingleEpisodeForCompleted = true,
            preferSeasons = false,
        )
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.single("1")),
            media(
                alliance = "字幕组2", episodeRange = EpisodeRange.range(1, 2),
            ).also { target = it },
            media(
                alliance = "字幕组6", episodeRange = EpisodeRange.season(1),
            ),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.single("4")),
        )
        assertEquals(2, selector.filteredCandidates.first().size)
        assertEquals(target, selector.filteredCandidates.first()[0])
    }

    @Test
    fun `do not hide single episode after finished if settings off`() = runTest {
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(true)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(
            hideSingleEpisodeForCompleted = false,
            preferSeasons = false,
        )
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.single("1")),
            media(alliance = "字幕组2", episodeRange = EpisodeRange.range(1, 2)),
            media(alliance = "字幕组6", episodeRange = EpisodeRange.season(1)),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.single("4")),
        )
        assertEquals(6, selector.filteredCandidates.first().size)
    }

    ///////////////////////////////////////////////////////////////////////////
    // 已完结后优先选择季度全集资源
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `prefer seasons after finished`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(true)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(
            hideSingleEpisodeForCompleted = false,
            preferSeasons = true,
        )
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE // 允许选择 1080P 等
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.single("1")),
            media(alliance = "字幕组2", episodeRange = EpisodeRange.season(1)).also { target = it },
            media(alliance = "字幕组3", episodeRange = EpisodeRange.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.single("4")),
        )
        assertEquals(5, selector.filteredCandidates.first().size)
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `do not prefer season if disabled`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(true)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(
            hideSingleEpisodeForCompleted = false,
            preferSeasons = false,
        )
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.single("1")).also { target = it },
            media(alliance = "字幕组2", episodeRange = EpisodeRange.season(1)),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.single("4")),
        )
        assertEquals(5, selector.filteredCandidates.first().size)
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `do not prefer season if not matched`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(true)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(
            hideSingleEpisodeForCompleted = false,
            preferSeasons = true,
        )
        savedUserPreference.value = MediaPreference.Empty
        savedDefaultPreference.value = MediaPreference.Empty // 啥都不要, 就一定会 fallback 成选第一个
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.single("1")).also { target = it },
            media(alliance = "字幕组2", episodeRange = EpisodeRange.season(1)),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.single("4")),
        )
        assertEquals(5, selector.filteredCandidates.first().size)
        assertEquals(target, selector.trySelectDefault())
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
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy()
        savedUserPreference.value = MediaPreference.Empty
        savedDefaultPreference.value = MediaPreference.Empty // 啥都不要, 就一定会 fallback 成选第一个
        addMedia(
            media(sourceId = "1", episodeRange = EpisodeRange.single("1")).also { userSelection = it },
            media(sourceId = "2", episodeRange = EpisodeRange.season(1)),
            media(sourceId = "3", episodeRange = EpisodeRange.single("2")),
            media(sourceId = "4", episodeRange = EpisodeRange.single("3")),
            media(sourceId = "5", episodeRange = EpisodeRange.single("4")),
        )
        selector.select(userSelection)
        assertEquals(1, selector.filteredCandidates.first().size) // 因为会自动设置 preference
        assertEquals(
            null,
            selector.trySelectFromMediaSources(
                listOf("2"),
                overrideUserSelection = false,
            ),
        )
    }

    @Test
    fun `trySelectFromMediaSources can override user selection`() = runTest {
        val userSelection: DefaultMedia
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy()
        savedUserPreference.value = MediaPreference.Empty
        savedDefaultPreference.value = MediaPreference.Empty // 啥都不要, 就一定会 fallback 成选第一个
        addMedia(
            media(sourceId = "1", episodeRange = EpisodeRange.single("1")).also { userSelection = it },
            media(sourceId = "2", episodeRange = EpisodeRange.season(1)).also { target = it },
            media(sourceId = "3", episodeRange = EpisodeRange.single("2")),
            media(sourceId = "4", episodeRange = EpisodeRange.single("3")),
            media(sourceId = "5", episodeRange = EpisodeRange.single("4")),
        )
        selector.select(userSelection)
        assertEquals(1, selector.filteredCandidates.first().size)
        assertEquals(
            target,
            selector.trySelectFromMediaSources(
                listOf("2"),
                overrideUserSelection = true,
            ),
        )
    }

    @Test
    fun `trySelectFromMediaSources does not update preferences`() = runTest {
        val userSelection: DefaultMedia
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy()
        savedUserPreference.value = MediaPreference.Empty
        savedDefaultPreference.value = MediaPreference.Empty // 啥都不要, 就一定会 fallback 成选第一个
        addMedia(
            media(sourceId = "1", episodeRange = EpisodeRange.single("1")).also { userSelection = it },
            media(sourceId = "2", episodeRange = EpisodeRange.season(1)).also { target = it },
            media(sourceId = "3", episodeRange = EpisodeRange.single("2")),
            media(sourceId = "4", episodeRange = EpisodeRange.single("3")),
            media(sourceId = "5", episodeRange = EpisodeRange.single("4")),
        )
        selector.select(userSelection)
        assertEquals(1, selector.filteredCandidates.first().size)
        assertEquals(
            target,
            selector.trySelectFromMediaSources(
                listOf("2"),
                overrideUserSelection = true,
            ),
        )
        assertEquals(
            OptionalPreference.prefer("1"),
            selector.mediaSourceId.userSelected.first(),
        )
    }

    @Test
    fun `trySelectFromMediaSources does not select from blacklisted`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy()
        savedUserPreference.value = MediaPreference.Empty
        savedDefaultPreference.value = MediaPreference.Empty // 啥都不要, 就一定会 fallback 成选第一个
        addMedia(
            media(sourceId = "1", episodeRange = EpisodeRange.single("1")),
            media(sourceId = "2", episodeRange = EpisodeRange.season(1)).also { target = it },
            media(sourceId = "3", episodeRange = EpisodeRange.single("2")),
            media(sourceId = "4", episodeRange = EpisodeRange.single("3")),
            media(sourceId = "5", episodeRange = EpisodeRange.single("4")),
        )
        assertEquals(5, selector.filteredCandidates.first().size)
        assertEquals(null, selector.trySelectFromMediaSources(listOf("2"), blacklistMediaIds = setOf(target.mediaId)))
    }

    @Test
    fun `trySelectFromMediaSources order source count smaller than actual count`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy()
        savedUserPreference.value = MediaPreference.Empty
        savedDefaultPreference.value = MediaPreference.Empty // 啥都不要, 就一定会 fallback 成选第一个
        addMedia(
            media(sourceId = "1", episodeRange = EpisodeRange.single("1")),
            media(sourceId = "2", episodeRange = EpisodeRange.season(1)).also { target = it },
            media(sourceId = "3", episodeRange = EpisodeRange.single("2")),
            media(sourceId = "4", episodeRange = EpisodeRange.single("3")),
            media(sourceId = "5", episodeRange = EpisodeRange.single("4")),
        )
        assertEquals(5, selector.filteredCandidates.first().size)
        assertEquals(target, selector.trySelectFromMediaSources(listOf("2")))
    }

    @Test
    fun `trySelectFromMediaSources order source count greater than actual count`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy()
        savedUserPreference.value = MediaPreference.Empty
        savedDefaultPreference.value = MediaPreference.Empty // 啥都不要, 就一定会 fallback 成选第一个
        addMedia(
            media(sourceId = "0", episodeRange = EpisodeRange.single("1")),
            media(sourceId = "2", episodeRange = EpisodeRange.season(1)).also { target = it },
        )
        assertEquals(2, selector.filteredCandidates.first().size)
        assertEquals(target, selector.trySelectFromMediaSources(listOf("1", "2", "3")))
    }

    @Test
    fun `trySelectFromMediaSources order source no intersection with actual count`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy()
        savedUserPreference.value = MediaPreference.Empty
        savedDefaultPreference.value = MediaPreference.Empty // 啥都不要, 就一定会 fallback 成选第一个
        addMedia(
            media(sourceId = "0", episodeRange = EpisodeRange.single("1")),
            media(sourceId = "4", episodeRange = EpisodeRange.season(1)).also { target = it },
        )
        assertEquals(2, selector.filteredCandidates.first().size)
        assertEquals(null, selector.trySelectFromMediaSources(listOf("1", "2", "3")))
    }

    ///////////////////////////////////////////////////////////////////////////
    // 优先选择在线数据源
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `prefer any sources`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(
            preferKind = null,
        )
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.single("1")).also { target = it },
            media(
                alliance = "字幕组2", episodeRange = EpisodeRange.single("2"), kind = MediaSourceKind.WEB,
            ),
            media(alliance = "字幕组6", episodeRange = EpisodeRange.single("1")),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.single("4")),
        )
        assertEquals(6, selector.filteredCandidates.first().size)
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `prefer bt sources`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(
            preferKind = MediaSourceKind.BitTorrent,
        )
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.single("1"), kind = MediaSourceKind.WEB),
            media(
                alliance = "字幕组2", episodeRange = EpisodeRange.single("2"), kind = MediaSourceKind.WEB,
            ),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.single("1")).also { target = it },
            media(alliance = "字幕组4", episodeRange = EpisodeRange.single("2")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.single("3")),
            media(alliance = "字幕组6", episodeRange = EpisodeRange.single("4")),
        )
        assertEquals(6, selector.filteredCandidates.first().size)
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `prefer web sources`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(false)
        mediaSelectorSettings.value = MediaSelectorSettings.Default.copy(
            preferKind = MediaSourceKind.WEB,
        )
        addMedia(
            media(alliance = "字幕组1", episodeRange = EpisodeRange.single("1")),
            media(
                alliance = "字幕组2", episodeRange = EpisodeRange.single("2"), kind = MediaSourceKind.WEB,
            ).also { target = it },
            media(alliance = "字幕组6", episodeRange = EpisodeRange.single("1")),
            media(alliance = "字幕组3", episodeRange = EpisodeRange.single("2")),
            media(alliance = "字幕组4", episodeRange = EpisodeRange.single("3")),
            media(alliance = "字幕组5", episodeRange = EpisodeRange.single("4")),
        )
        assertEquals(6, selector.filteredCandidates.first().size)
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
        assertEquals(listOf(m4, m3, m2, m1), selector.mediaList.first())
        assertEquals(listOf(m4, m3, m2, m1), selector.filteredCandidates.first())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Events
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `do not save subtitle language when it is ambiguous`() = runTest {
        savedUserPreference.value = MediaPreference.Empty
        savedDefaultPreference.value = MediaPreference.Empty // 方便后面比较
        val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS", "CHT"))
        addMedia(target)
        runCollectEvents {
            selector.select(target)
        }.run {
            assertEquals(1, onSelect.size)
            assertEquals(SelectEvent(target, null), onSelect.first())
            assertEquals(1, onChangePreference.size)
            assertEquals(
                // 
                MediaPreference.Empty.copy(
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
        savedUserPreference.value = MediaPreference.Empty
        savedDefaultPreference.value = MediaPreference.Empty // 方便后面比较
        val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS"))
        addMedia(target)
        runCollectEvents {
            selector.select(target)
        }.run {
            assertEquals(1, onSelect.size)
            assertEquals(SelectEvent(target, null), onSelect.first())
            assertEquals(1, onChangePreference.size)
            assertEquals(
                MediaPreference.Empty.copy(
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
    fun `event prefer`() = runTest {
        savedUserPreference.value = MediaPreference.Empty
        savedDefaultPreference.value = MediaPreference.Empty // 方便后面比较
        val target = media(alliance = "字幕组")
        addMedia(target)
        runCollectEvents {
            selector.alliance.prefer("字幕组")
        }.run {
            assertEquals(0, onSelect.size)
            assertEquals(1, onChangePreference.size)
            assertEquals(
                MediaPreference.Empty.copy(
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
