/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.persistent.createTestPreferencesDataStore
import me.him188.ani.app.data.persistent.database.dao.createMemoryPreferredWebMediaSourceDao
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepositoryImpl
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_DMHY
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_MIKAN
import me.him188.ani.app.domain.media.selector.testFramework.SimpleMediaSelectorTestSuite
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.test.TestContainer
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestContainer
class MediaPreferenceCrossSessionTest {
    private val globalDefault = MutableStateFlow(MediaSelectorTestSuite.DEFAULT_PREFERENCE)

    private fun createRepository(store: DataStore<Preferences>): EpisodePreferencesRepositoryImpl {
        return EpisodePreferencesRepositoryImpl(
            store = store,
            preferredWebMediaSourceDao = createMemoryPreferredWebMediaSourceDao(),
            defaultMediaPreference = globalDefault,
        )
    }

    private fun TestScope.createSessionSelector(
        suite: SimpleMediaSelectorTestSuite,
        repository: EpisodePreferencesRepository,
        mediaList: Flow<List<Media>>,
        enableCaching: Boolean = false,
        cachingScope: CoroutineScope? = null,
    ): DefaultMediaSelector = DefaultMediaSelector(
        mediaSelectorContextNotCached = suite.preferenceApi.mediaSelectorContext,
        mediaListNotCached = mediaList,
        savedUserPreference = repository.mediaPreferenceFlow(SUBJECT_ID),
        savedDefaultPreference = globalDefault,
        mediaSelectorSettings = suite.preferenceApi.mediaSelectorSettings,
        flowCoroutineContext = coroutineContext[ContinuationInterceptor]!!,
        enableCaching = enableCaching,
        cachingScope = cachingScope,
    )

    /**
     * 会话 A: 挂载 savePreferenceOnSelect, 手动选择 [target] 后推进 debounce, 使偏好落到 store 上.
     */
    private suspend fun TestScope.runSessionA(
        suite: SimpleMediaSelectorTestSuite,
        repository: EpisodePreferencesRepository,
        target: Media,
        mediaList: List<Media>,
    ) {
        val selectorA = createSessionSelector(suite, repository, MutableStateFlow(mediaList))
        val mountA: Job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            selectorA.eventHandling.savePreferenceOnSelect { repository.setMediaPreference(SUBJECT_ID, it) }
        }
        selectorA.events.onChangePreference.subscriptionCount.first { it > 0 }

        assertTrue(selectorA.select(target))
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()

        mountA.cancel()
    }

    private fun createTargetMedia(suite: SimpleMediaSelectorTestSuite) = suite.media(
        sourceId = SOURCE_MIKAN,
        alliance = "桜都字幕组",
        resolution = "720P",
        subtitleLanguages = listOf("CHT"),
        kind = MediaSourceKind.WEB,
    )

    private fun createCompetitorMedia(suite: SimpleMediaSelectorTestSuite) = suite.media(
        sourceId = SOURCE_DMHY,
        alliance = "北宇治字幕组",
        resolution = "1080P",
        subtitleLanguages = listOf("CHS", "CHT"),
        kind = MediaSourceKind.WEB,
    )

    @Test
    fun `A11 会话A手动选择debounce落库 会话B读回存档过滤偏好并选回同源同字幕组`() = runTest {
        val store = createTestPreferencesDataStore()
        val suite = SimpleMediaSelectorTestSuite(this)
        suite.initSubject("孤独摇滚")
        val target = createTargetMedia(suite)
        val competitor = createCompetitorMedia(suite)

        val repositoryA = createRepository(store)
        val selectorA = createSessionSelector(suite, repositoryA, MutableStateFlow(listOf(competitor, target)))
        val mountA = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            selectorA.eventHandling.savePreferenceOnSelect { repositoryA.setMediaPreference(SUBJECT_ID, it) }
        }
        selectorA.events.onChangePreference.subscriptionCount.first { it > 0 }

        assertTrue(selectorA.select(target))
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()

        val savedPreference = repositoryA.mediaPreferenceFlow(SUBJECT_ID).first()
        assertEquals(
            MediaSelectorTestSuite.DEFAULT_PREFERENCE.copy(
                alliance = "桜都字幕组",
                resolution = "720P",
                subtitleLanguageId = "CHT",
                mediaSourceId = SOURCE_MIKAN,
            ),
            savedPreference,
        )

        mountA.cancel()

        val repositoryB = createRepository(store)
        val selectorB = createSessionSelector(suite, repositoryB, MutableStateFlow(listOf(competitor, target)))

        assertEquals(setOf(competitor, target), selectorB.filteredCandidatesMedia.first().toSet())
        assertEquals(listOf(target), selectorB.preferredCandidatesMedia.first())
        // 四个字段逐一断言: preferredCandidatesMedia 只需任一维度生效就能滤掉 competitor,
        // 逐字段断言才能把 "只有部分字段被持久化" 的回归归因到具体字段.
        assertEquals(SOURCE_MIKAN, selectorB.mediaSourceId.finalSelected.first())
        assertEquals("桜都字幕组", selectorB.alliance.finalSelected.first())
        assertEquals("720P", selectorB.resolution.finalSelected.first())
        assertEquals("CHT", selectorB.subtitleLanguageId.finalSelected.first())
        assertEquals(target, selectorB.trySelectDefault())
        assertEquals(target, selectorB.selected.value)
    }

    /**
     * 生产两处接线点 (CreateMediaFetchSelectBundleFlowUseCaseImpl / MediaSelectorFactory) 都不传 `enableCaching`,
     * 即生产默认走 `enableCaching = true` 的缓存路径. 这里把会话 B 在生产默认配置下再跑一遍.
     */
    @Test
    fun `A11 会话B在生产默认 enableCaching=true 下同样读回存档并按四字段过滤`() = runTest {
        val store = createTestPreferencesDataStore()
        val suite = SimpleMediaSelectorTestSuite(this)
        suite.initSubject("孤独摇滚")
        val target = createTargetMedia(suite)
        val competitor = createCompetitorMedia(suite)

        val repositoryA = createRepository(store)
        runSessionA(suite, repositoryA, target, listOf(competitor, target))

        val repositoryB = createRepository(store)
        val selectorB = createSessionSelector(
            suite, repositoryB, MutableStateFlow(listOf(competitor, target)),
            enableCaching = true,
            cachingScope = backgroundScope,
        )
        advanceUntilIdle()

        assertEquals(setOf(competitor, target), selectorB.filteredCandidatesMedia.first().toSet())
        assertEquals(listOf(target), selectorB.preferredCandidatesMedia.first())
        assertEquals(SOURCE_MIKAN, selectorB.mediaSourceId.finalSelected.first())
        assertEquals("桜都字幕组", selectorB.alliance.finalSelected.first())
        assertEquals("720P", selectorB.resolution.finalSelected.first())
        assertEquals("CHT", selectorB.subtitleLanguageId.finalSelected.first())
        assertEquals(target, selectorB.trySelectDefault())
        assertEquals(target, selectorB.selected.value)
    }

    @Test
    fun `A11 对照 空存档时会话B不按偏好过滤且 mediaSourceId finalSelected 为空`() = runTest {
        val store = createTestPreferencesDataStore()
        val suite = SimpleMediaSelectorTestSuite(this)
        suite.initSubject("孤独摇滚")
        val target = createTargetMedia(suite)
        val competitor = createCompetitorMedia(suite)

        val repository = createRepository(store)
        val selector = createSessionSelector(suite, repository, MutableStateFlow(listOf(competitor, target)))

        assertEquals(setOf(competitor, target), selector.preferredCandidatesMedia.first().toSet())
        assertNull(selector.mediaSourceId.finalSelected.first())
        assertNull(selector.alliance.finalSelected.first())
    }

    private companion object {
        private const val SUBJECT_ID = 100
    }
}
