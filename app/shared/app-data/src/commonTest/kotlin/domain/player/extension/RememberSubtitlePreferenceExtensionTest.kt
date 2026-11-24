/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(
    UnsafeEpisodeSessionApi::class,
    InternalMediampApi::class,
    InternalForInheritanceMediampApi::class,
)

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.utils.coroutines.childScope
import org.koin.core.Koin
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.metadata.SubtitleTrack
import org.openani.mediamp.metadata.TrackGroup
import org.openani.mediamp.metadata.TrackLabel
import org.openani.mediamp.source.UriMediaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * @see RememberSubtitlePreferenceExtension
 */
class RememberSubtitlePreferenceExtensionTest : AbstractPlayerExtensionTest() {
    private class TestSubtitleTrackGroup : TrackGroup<SubtitleTrack> {
        private val _selected = MutableStateFlow<SubtitleTrack?>(null)
        private val _candidates = MutableStateFlow<List<SubtitleTrack>>(emptyList())

        override val selected: StateFlow<SubtitleTrack?> = _selected
        override val candidates: Flow<List<SubtitleTrack>> = _candidates

        override fun select(track: SubtitleTrack?): Boolean {
            _selected.value = track
            return true
        }

        fun addMpvLikeTrack(track: SubtitleTrack) {
            _candidates.value = _candidates.value + track
            // 模拟 mpv sub-add auto: 当前未选中任何字幕时自动选中第一条
            if (_selected.value == null) {
                _selected.value = track
            }
        }

        fun resetForNewMedia() {
            _candidates.value = emptyList()
            _selected.value = null
        }
    }

    private val chsTrack = SubtitleTrack(
        id = "1",
        internalId = "1",
        language = "chi",
        labels = listOf(TrackLabel("chi", "简体中文")),
    )
    private val chtTrack = SubtitleTrack(
        id = "2",
        internalId = "2",
        language = "chi",
        labels = listOf(TrackLabel("chi", "繁体中文")),
    )
    private val engTrack = SubtitleTrack(
        id = "3",
        internalId = "3",
        language = "eng",
        labels = listOf(TrackLabel("eng", "英语")),
    )

    private class InMemoryEpisodePreferencesRepository(
        private val koin: Koin,
    ) : EpisodePreferencesRepository {
        private val preferences = MutableStateFlow<Map<Int, MediaPreference>>(emptyMap())
        private val webSources = MutableStateFlow<Map<Int, String>>(emptyMap())

        override fun getKoin(): Koin = koin

        override fun mediaPreferenceFlow(subjectId: Int): Flow<MediaPreference> {
            return preferences.map { it[subjectId] ?: MediaPreference.Empty }
        }

        override suspend fun setMediaPreference(subjectId: Int, mediaPreference: MediaPreference) {
            preferences.value = preferences.value + (subjectId to mediaPreference)
        }

        override suspend fun setPreferredWebMediaSource(subjectId: Int, webSourceId: String) {
            webSources.value = webSources.value + (subjectId to webSourceId)
        }

        override fun getPreferredWebMediaSource(subjectId: Int): Flow<String?> {
            return webSources.map { it[subjectId] }
        }

        override suspend fun removePreferredWebMediaSource(subjectId: Int) {
            webSources.value = webSources.value - subjectId
        }
    }

    private fun TestScope.createSuite(): Triple<CoroutineScope, EpisodePlayerTestSuite, TestSubtitleTrackGroup> {
        val scope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, scope)
        suite.registerComponent<EpisodePreferencesRepository> {
            InMemoryEpisodePreferencesRepository(suite.koin)
        }
        val trackGroup = TestSubtitleTrackGroup()
        val factory = EpisodePlayerExtensionFactory { context: PlayerExtensionContext, koin: Koin ->
            RememberSubtitlePreferenceExtension(context, koin) { trackGroup }
        }
        val state = suite.createState(listOf(factory))
        state.onUIReady()
        advanceUntilIdle()
        return Triple(scope, suite, trackGroup)
    }

    @Test
    fun `first play prefers simplified chinese over other tracks`() = runTest {
        val (scope, suite, trackGroup) = createSuite()

        suite.player.setMediaData(UriMediaData("http://example.com/ep1.mp4"))
        advanceUntilIdle()

        // 模拟 eng 先于 chs 到达
        trackGroup.addMpvLikeTrack(engTrack)
        advanceUntilIdle()
        trackGroup.addMpvLikeTrack(chsTrack)
        advanceUntilIdle()

        assertEquals(chsTrack.id, trackGroup.selected.value?.id)
        scope.cancel()
    }

    @Test
    fun `manual selection survives subsequent candidate additions and restores on next episode`() = runTest {
        val (scope, suite, trackGroup) = createSuite()
        val repo = suite.koin.get<EpisodePreferencesRepository>()

        // 第 1 集: 模拟 mpv 分批 sub-add
        suite.player.setMediaData(UriMediaData("http://example.com/ep1.mp4"))
        advanceUntilIdle()
        trackGroup.addMpvLikeTrack(chsTrack)
        advanceUntilIdle()
        trackGroup.addMpvLikeTrack(chtTrack)
        advanceUntilIdle()
        trackGroup.addMpvLikeTrack(engTrack)
        advanceUntilIdle()

        // 用户手动选择英语
        trackGroup.select(engTrack)
        advanceUntilIdle()

        // 后续又有新轨道到达也不会丢失偏好
        val jpTrack = SubtitleTrack("4", "4", "jpn", listOf(TrackLabel("jpn", "日语")))
        trackGroup.addMpvLikeTrack(jpTrack)
        advanceUntilIdle()

        val savedPref = repo.mediaPreferenceFlow(subjectId).first()
        assertEquals("英语", savedPref.preferredSubtitleLabel)
        assertEquals("eng", savedPref.preferredSubtitleLanguage)
        assertFalse(savedPref.subtitleDisabled)

        // 切换到第 2 集: 先触发切集加载，再重置旧轨道并模拟新集字幕分批到达
        suite.player.setMediaData(UriMediaData("http://example.com/ep2.mp4"))
        trackGroup.resetForNewMedia()
        advanceUntilIdle()

        trackGroup.addMpvLikeTrack(chsTrack)
        advanceUntilIdle()
        trackGroup.addMpvLikeTrack(chtTrack)
        advanceUntilIdle()
        trackGroup.addMpvLikeTrack(engTrack)
        advanceUntilIdle()

        assertEquals(engTrack.id, trackGroup.selected.value?.id)
        val prefAfterEp2 = repo.mediaPreferenceFlow(subjectId).first()
        assertEquals("英语", prefAfterEp2.preferredSubtitleLabel)
        scope.cancel()
    }

    @Test
    fun `disabling subtitles persists across episodes even when mpv auto-selects first track`() = runTest {
        val (scope, suite, trackGroup) = createSuite()
        val repo = suite.koin.get<EpisodePreferencesRepository>()

        suite.player.setMediaData(UriMediaData("http://example.com/ep1.mp4"))
        advanceUntilIdle()
        trackGroup.addMpvLikeTrack(chsTrack)
        advanceUntilIdle()

        // 用户手动关闭字幕
        trackGroup.select(null)
        advanceUntilIdle()
        assertTrue(repo.mediaPreferenceFlow(subjectId).first().subtitleDisabled)

        // 切换到第 2 集
        suite.player.setMediaData(UriMediaData("http://example.com/ep2.mp4"))
        trackGroup.resetForNewMedia()
        advanceUntilIdle()
        trackGroup.addMpvLikeTrack(chsTrack)
        advanceUntilIdle()

        assertNull(trackGroup.selected.value)
        assertTrue(repo.mediaPreferenceFlow(subjectId).first().subtitleDisabled)
        scope.cancel()
    }
}
