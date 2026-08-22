/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.subject

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.media.cache.requester.CacheRequestStage
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequesterImpl
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.fetch.CompletedConditions
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.cache.CacheManagementTestTags
import me.him188.ani.app.ui.cache.components.CacheEpisodePaused
import me.him188.ani.app.ui.cache.components.CacheEpisodeState
import me.him188.ani.app.ui.cache.components.CacheSelectionToolbarTestTags
import me.him188.ani.app.ui.cache.components.createTestCacheEpisode
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_management_episode_label
import me.him188.ani.app.ui.lang.cache_management_selected_count
import me.him188.ani.app.ui.lang.cache_management_select_all_action
import me.him188.ani.app.ui.lang.cache_subject_pause_all
import me.him188.ani.app.ui.mediafetch.createTestMediaSourceInfoProvider
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class SubjectCachePageTest {
    private val finished = createTestCacheEpisode(
        13,
        displayName = "已完成的剧集",
        episodeId = 13,
        initialState = CacheEpisodePaused.COMPLETED,
        progress = 1f.toProgress(),
    )
    private val inProgress = createTestCacheEpisode(
        16,
        displayName = "下载中的剧集",
        episodeId = 16,
        initialState = CacheEpisodePaused.IN_PROGRESS,
    )
    private val paused = createTestCacheEpisode(
        17,
        displayName = "已暂停的剧集",
        episodeId = 17,
        initialState = CacheEpisodePaused.PAUSED,
    )
    private val cachedEpisodes = listOf(finished, inProgress, paused)

    private fun createEpisodes(scope: CoroutineScope): List<EpisodeCacheState> = listOf(
        createEpisodeState(13, "已完成的剧集", scope),
        createEpisodeState(16, "下载中的剧集", scope),
        createEpisodeState(17, "已暂停的剧集", scope),
        createEpisodeState(18, "未缓存的剧集", scope),
        createEpisodeState(19, "看过的未缓存剧集", scope, watchStatus = UnifiedCollectionType.DONE),
    )

    @Test
    fun `shows cached and not cached rows with pause all`() = runAniComposeUiTest {
        val scope = CoroutineScope(Dispatchers.Main)
        var pauseAllCalled = false

        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectCachePage(
                    title = "葬送的芙莉莲",
                    cacheListState = FakeEpisodeCacheListState(createEpisodes(scope)),
                    cachedEpisodes = cachedEpisodes,
                    mediaSourceInfoProvider = createTestMediaSourceInfoProvider(),
                    mediaSelectorSettingsProvider = { flowOf(MediaSelectorSettings.Default) },
                    onPlay = {},
                    onResume = {},
                    onPause = {},
                    onDelete = {},
                    onViewDetail = {},
                    onPauseAll = { pauseAllCalled = true },
                    onResumeAll = {},
                )
            }
        }

        // 已缓存与未缓存的剧集都应显示
        onNodeWithText(episodeLabel(13, "已完成的剧集")).assertExists()
        onNodeWithText(episodeLabel(16, "下载中的剧集")).assertExists()
        onNodeWithText(episodeLabel(18, "未缓存的剧集")).assertExists()
        onNodeWithText(episodeLabel(19, "看过的未缓存剧集")).assertExists()

        // 有下载中的缓存时显示 "全部暂停"
        onNodeWithText(runBlocking { getString(Lang.cache_subject_pause_all) }).performClick()
        runOnIdle {
            assertEquals(true, pauseAllCalled)
        }
    }

    @Test
    fun `long press enters selection and batch operations work`() = runAniComposeUiTest {
        val scope = CoroutineScope(Dispatchers.Main)
        val resumedIds = mutableSetOf<String>()
        val pausedIds = mutableSetOf<String>()
        val deletedIds = mutableSetOf<String>()

        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectCachePage(
                    title = "葬送的芙莉莲",
                    cacheListState = FakeEpisodeCacheListState(createEpisodes(scope)),
                    cachedEpisodes = cachedEpisodes,
                    mediaSourceInfoProvider = createTestMediaSourceInfoProvider(),
                    mediaSelectorSettingsProvider = { flowOf(MediaSelectorSettings.Default) },
                    onPlay = {},
                    onResume = { resumedIds += it.cacheId },
                    onPause = { pausedIds += it.cacheId },
                    onDelete = { deletedIds += it.cacheId },
                    onViewDetail = {},
                    onPauseAll = {},
                    onResumeAll = {},
                )
            }
        }

        // 长按进入多选
        onNodeWithText(episodeLabel(13, "已完成的剧集")).performTouchInput { longClick() }
        onNodeWithText(runBlocking { getString(Lang.cache_management_selected_count, 1) }).assertExists()

        // 全选
        onNodeWithText(runBlocking { getString(Lang.cache_management_select_all_action) }).performClick()
        onNodeWithText(runBlocking { getString(Lang.cache_management_selected_count, 3) }).assertExists()

        // 批量继续: 只作用于已暂停的
        onNodeWithTag(CacheSelectionToolbarTestTags.RESUME).performClick()
        runOnIdle {
            assertEquals(setOf(paused.cacheId), resumedIds)
        }

        // 批量暂停: 只作用于下载中的
        onNodeWithTag(CacheSelectionToolbarTestTags.PAUSE).performClick()
        runOnIdle {
            assertEquals(setOf(inProgress.cacheId), pausedIds)
        }

        // 批量删除: 需要确认, 然后退出多选
        onNodeWithTag(CacheSelectionToolbarTestTags.DELETE).performClick()
        onNodeWithTag(CacheManagementTestTags.DELETE_CONFIRM_BUTTON).performClick()
        runOnIdle {
            assertEquals(cachedEpisodes.map { it.cacheId }.toSet(), deletedIds)
        }
        onNodeWithText(runBlocking { getString(Lang.cache_management_selected_count, 3) }).assertDoesNotExist()
    }

    private fun episodeLabel(sort: Int, title: String): String =
        runBlocking { getString(Lang.cache_management_episode_label, sort, title) }

    private fun createEpisodeState(
        episodeId: Int,
        title: String,
        backgroundScope: CoroutineScope,
        watchStatus: UnifiedCollectionType = UnifiedCollectionType.DOING,
    ): EpisodeCacheState {
        val requester = EpisodeCacheRequesterImpl(
            mediaFetcherLazy = flowOf(FakeMediaFetcher),
            mediaSelectorFactory = FakeMediaSelectorFactory,
            storagesLazy = flowOf(emptyList<MediaCacheStorage>()),
        )
        return EpisodeCacheState(
            episodeId = episodeId,
            cacheRequester = requester,
            currentStageState = mutableStateOf(CacheRequestStage.Idle),
            infoState = mutableStateOf(
                EpisodeCacheInfo(
                    sort = EpisodeSort(episodeId),
                    ep = null,
                    title = title,
                    watchStatus = watchStatus,
                    hasPublished = true,
                ),
            ),
            cacheStatusState = mutableStateOf(EpisodeCacheStatus.NotCached),
            backgroundScope = backgroundScope,
        )
    }
}

private class FakeEpisodeCacheListState(
    override val episodes: List<EpisodeCacheState>,
) : EpisodeCacheListState {
    override val currentEpisode: EpisodeCacheState? get() = null
    override val currentSelectMediaTask: SelectMediaTask? get() = null
    override fun selectMedia(media: Media) {}
    override fun cancelMediaSelector(task: SelectMediaTask) {}
    override val currentSelectStorageTask: SelectStorageTask? get() = null
    override fun selectStorage(storage: MediaCacheStorage) {}
    override fun cancelStorageSelector(task: SelectStorageTask) {}
    override fun cancelRequest() {}
    override fun requestCache(episode: EpisodeCacheState, autoSelectCached: Boolean) {}
    override fun deleteCache(episode: EpisodeCacheState) {}
}

private object FakeMediaFetcher : MediaFetcher {
    override fun newSession(
        requestLazy: Flow<MediaFetchRequest>,
        flowContext: CoroutineContext,
    ): MediaFetchSession {
        return FakeMediaFetchSession
    }
}

private object FakeMediaSelectorFactory : MediaSelectorFactory {
    override fun create(
        subjectId: Int,
        episodeId: Int,
        mediaList: Flow<List<Media>>,
        flowCoroutineContext: CoroutineContext,
    ): MediaSelector {
        return TestMediaSelector
    }
}

private object FakeMediaFetchSession : MediaFetchSession {
    override val request: Flow<MediaFetchRequest> = emptyFlow()
    override val mediaSourceResults: List<MediaSourceFetchResult> = emptyList()
    override val cumulativeResults: Flow<List<Media>> = flowOf(emptyList())
    override val hasCompleted: Flow<CompletedConditions> = flowOf(CompletedConditions.AllCompleted)

    override fun setFetchRequest(request: MediaFetchRequest) = Unit
}

private val TestMediaSelector = DefaultMediaSelector(
    mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
    mediaListNotCached = flowOf(emptyList()),
    savedUserPreference = flowOf(MediaPreference.Empty),
    savedDefaultPreference = flowOf(MediaPreference.Empty),
    mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
    enableCaching = false,
)
