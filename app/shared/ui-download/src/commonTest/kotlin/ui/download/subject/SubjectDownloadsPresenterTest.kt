/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.download.DownloadOperations
import me.him188.ani.app.domain.media.download.DownloadRequestSessionFactory
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.download.FakeAddDownloadUseCase
import me.him188.ani.app.ui.download.FakeDeleteCacheUseCase
import me.him188.ani.app.ui.download.FakeDownloadStorage
import me.him188.ani.app.ui.download.FakeEpisodePlayHistoryRepository
import me.him188.ani.app.ui.download.FakeEpisodePreferencesRepository
import me.him188.ani.app.ui.download.FakeMediaFetcher
import me.him188.ani.app.ui.download.FakeMediaSourceManager
import me.him188.ani.app.ui.download.FakeSettingsRepository
import me.him188.ani.app.ui.download.FakeSubjectCollectionRepository
import me.him188.ani.app.ui.download.components.DownloadItem
import me.him188.ani.app.ui.download.components.DownloadStatus
import me.him188.ani.app.ui.download.fakeMediaSelectorFactory
import me.him188.ani.app.ui.download.testDownloadCache
import me.him188.ani.app.ui.download.testSubjectCollection
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
class SubjectDownloadsPresenterTest {
    private val media = TestMediaList.first()

    @Test
    fun `ui state maps episodes downloads histories and loading flags`() = withFixture {
        val cache = testDownloadCache(1)
        storage.listFlow.value = listOf(cache)
        histories.flow.value = listOf(EpisodeHistory(episodeId = 1, positionMillis = 500, durationMillis = 1000))
        assertEquals(SubjectDownloadsUiState(), presenter.uiState.value)

        val state = awaitState { !it.episodesLoading && !it.downloadsLoading && it.downloads.isNotEmpty() }
        assertEquals("中文条目名称", state.title)
        assertEquals(3, state.totalEpisodes)
        assertFalse(state.episodesFailed)
        assertFalse(state.downloadsFailed)
        assertEquals(listOf("download-${cache.cacheId}", "episode-2", "episode-3"), state.items.map { it.key })
        assertEquals(DownloadRequestUiState(), state.request)

        val item = state.downloads.single()
        assertEquals(1, item.subjectId)
        assertEquals(1, item.episodeId)
        assertEquals("Subject 1", item.subjectName)
        assertEquals("Episode 1", item.displayName)
        assertEquals(100L, item.creationTime)
        assertEquals(DownloadStatus.IN_PROGRESS, item.status)
        assertEquals(UnifiedCollectionType.DOING, item.subjectCollectionType)
        assertEquals(0.5f.toProgress(), item.playbackProgress)
        assertEquals(DownloadItem.Playability.PLAYABLE, item.playability)
        assertEquals(DummyMediaCacheEngine.engineKey, item.engineKey)
        assertEquals(media.mediaSourceId, item.mediaSourceId)
        assertFalse(item.isBusy)
    }

    @Test
    fun `subject failure marks episodes failed and reload recovers`() = withFixture {
        subjects.collectionFailure = IllegalStateException("subject failed")
        val failed = awaitState { it.episodesFailed && !it.downloadsLoading }
        assertFalse(failed.episodesLoading)
        assertFalse(failed.downloadsFailed)
        assertNull(failed.title)
        assertTrue(failed.items.isEmpty())

        subjects.collectionFailure = null
        presenter.reload()
        val recovered = awaitState { !it.episodesFailed && !it.episodesLoading }
        assertEquals("中文条目名称", recovered.title)
        assertEquals(listOf("episode-1", "episode-2", "episode-3"), recovered.items.map { it.key })
    }

    @Test
    fun `request states map to busy cancellable and dialogs`() = withFixture {
        val collection = subjects.collection.value
        subjects.collection.value = null

        presenter.requestDownload(2)
        val preparing = awaitState { it.request.busy }
        assertEquals(DownloadRequestUiState(episodeIds = setOf(2), busy = true, canCancel = true), preparing.request)
        assertEquals(DownloadRequestDialogState(), awaitDialogs { it != null })

        subjects.collection.value = collection
        val picker = assertNotNull(awaitDialogs { it?.selection != null }?.selection)
        assertEquals(2, picker.episodeId)
        val awaiting = awaitState { !it.request.busy }
        assertEquals(DownloadRequestUiState(episodeIds = setOf(2), busy = false, canCancel = true), awaiting.request)
        runCurrent()
        assertSame(picker, presenter.requestDialogs.value?.selection)

        addDownload.gate = CompletableDeferred()
        presenter.selectMedia(2, media)
        val creating = awaitState { it.request.busy }
        assertEquals(DownloadRequestUiState(episodeIds = setOf(2), busy = true, canCancel = true), creating.request)
        assertEquals(DownloadRequestDialogState(), awaitDialogs { it?.selection == null })

        addDownload.gate!!.complete(Unit)
        val finished = awaitState { state -> !state.request.canCancel && state.downloads.any { it.episodeId == 2 } }
        assertEquals(DownloadRequestUiState(), finished.request)
        assertNull(awaitDialogs { it == null })
        assertEquals(listOf(2), addDownload.createdEpisodeIds)
        assertIs<SubjectDownloadListItem.Download>(finished.items[1])
    }

    @Test
    fun `failed request shows the failure dialog until cancelled`() = withFixture {
        addDownload.failure = IllegalStateException("create failed")
        presenter.requestDownload(1)
        awaitDialogs { it?.selection != null }
        presenter.selectMedia(1, media)

        val dialogs = awaitDialogs { it?.failed == true }
        assertEquals(DownloadRequestDialogState(failed = true), dialogs)
        val state = awaitState { !it.request.canCancel }
        assertEquals(DownloadRequestUiState(), state.request)
        assertTrue(addDownload.createdEpisodeIds.isEmpty())

        presenter.cancelRequest()
        assertNull(awaitDialogs { it == null })
    }

    @Test
    fun `requesting another episode while awaiting selection cancels the previous session`() = withFixture {
        assertTrue(presenter.requestDownload(1))
        awaitDialogs { it?.selection?.episodeId == 1 }
        assertTrue(fetcher.releasedEpisodeIds.isEmpty())

        assertTrue(presenter.requestDownload(2))
        val picker = assertNotNull(awaitDialogs { it?.selection?.episodeId == 2 }?.selection)
        runCurrent()
        assertEquals(setOf(1), fetcher.releasedEpisodeIds)
        assertEquals(setOf(2), presenter.uiState.value.request.episodeIds)

        // 再次请求正在等待选源的同一集不会重建会话, 也不算开启了新会话.
        assertFalse(presenter.requestDownload(2))
        runCurrent()
        assertSame(picker, presenter.requestDialogs.value?.selection)

        presenter.cancelRequest()
        assertNull(awaitDialogs { it == null })
        runCurrent()
        assertEquals(setOf(1, 2), fetcher.releasedEpisodeIds)
        assertEquals(DownloadRequestUiState(), presenter.uiState.value.request)
        assertTrue(addDownload.createdEpisodeIds.isEmpty())
    }

    @Test
    fun `operation failures are counted and skipped submissions are not`() = withFixture {
        val failing = object : MediaCache by testDownloadCache(1) {
            override suspend fun pause() = throw IllegalStateException("pause failed")
        }
        val gate = CompletableDeferred<Unit>()
        var pauseCalls = 0
        val blocked = object : MediaCache by testDownloadCache(2) {
            override suspend fun pause() {
                pauseCalls++
                gate.await()
            }
        }
        val normal = testDownloadCache(3)
        storage.listFlow.value = listOf(failing, blocked, normal)
        awaitState { it.downloads.size == 3 }

        presenter.pauseDownloads(setOf(failing.cacheId, normal.cacheId))
        presenter.operationFailures.first { it == 1 }
        assertEquals(MediaCacheState.PAUSED, normal.state.value)
        presenter.dismissOperationFailures()
        assertEquals(0, presenter.operationFailures.value)

        // 第一次暂停排队后, 第二次提交被跳过: 不会再调用 pause, 也不计入失败.
        presenter.pauseDownloads(setOf(blocked.cacheId))
        runCurrent()
        presenter.pauseDownloads(setOf(blocked.cacheId))
        runCurrent()
        gate.complete(Unit)
        awaitState { state -> state.downloads.none { it.isBusy } }
        assertEquals(1, pauseCalls)
        assertEquals(0, presenter.operationFailures.value)

        deleteCache.failure = IllegalStateException("delete failed")
        presenter.deleteDownloads(setOf(normal.cacheId))
        presenter.operationFailures.first { it == 1 }
        assertEquals(3, storage.listFlow.value.size)
    }

    @Test
    fun `pause all and resume all target every download`() = withFixture {
        storage.listFlow.value = listOf(testDownloadCache(1), testDownloadCache(2))
        awaitState { it.downloads.size == 2 }

        presenter.pauseAll()
        awaitState { state -> state.downloads.all { it.isPaused } }
        presenter.resumeAll()
        awaitState { state -> state.downloads.all { it.status == DownloadStatus.IN_PROGRESS } }
        assertEquals(0, presenter.operationFailures.value)
    }

    @Test
    fun `pause all and resume all without downloads submit nothing`() = withFixture {
        val state = awaitState { !it.downloadsLoading }
        assertTrue(state.downloads.isEmpty())

        presenter.pauseAll()
        presenter.resumeAll()
        assertEquals(0, pendingOperationCount)
        runCurrent()
        assertEquals(0, presenter.operationFailures.value)
    }

    @Test
    fun `requests during preparing and creating are ignored and keep the session`() = withFixture {
        val collection = subjects.collection.value
        subjects.collection.value = null

        assertTrue(presenter.requestDownload(2))
        awaitState { it.request.busy }
        assertFalse(presenter.requestDownload(1))
        runCurrent()
        assertEquals(DownloadRequestUiState(episodeIds = setOf(2), busy = true, canCancel = true), presenter.uiState.value.request)

        subjects.collection.value = collection
        awaitDialogs { it?.selection?.episodeId == 2 }
        addDownload.gate = CompletableDeferred()
        presenter.selectMedia(2, media)
        awaitState { it.request.busy }
        assertFalse(presenter.requestDownload(3))
        runCurrent()
        assertEquals(DownloadRequestUiState(episodeIds = setOf(2), busy = true, canCancel = true), presenter.uiState.value.request)

        addDownload.gate!!.complete(Unit)
        awaitState { !it.request.canCancel }
        assertEquals(listOf(2), addDownload.createdEpisodeIds)
    }

    @Test
    fun `closing the presenter releases the pending session`() = withFixture {
        assertTrue(presenter.requestDownload(1))
        awaitDialogs { it?.selection?.episodeId == 1 }
        runCurrent()
        assertTrue(fetcher.releasedEpisodeIds.isEmpty())
        assertFalse(presenter.isClosed)

        presenter.close()
        runCurrent()
        assertTrue(presenter.isClosed)
        assertEquals(setOf(1), fetcher.releasedEpisodeIds)
        assertTrue(addDownload.createdEpisodeIds.isEmpty())
    }

    @Test
    fun `resubscribing after the sharing timeout starts from the loaded state`() = withFixture(subscribe = false) {
        val collection = subjects.collection.value
        val first = subscribeUiState()
        awaitState { !it.episodesLoading && !it.downloadsLoading }
        first.cancel()
        advanceTimeBy(6_000)

        // 条目流挂起: 重新订阅后若回到空的加载状态, 页面会一直停留在那一帧.
        subjects.collection.value = null
        val observed = mutableListOf<SubjectDownloadsUiState>()
        subscribeUiState(observed)
        runCurrent()
        assertTrue(observed.isNotEmpty())
        assertTrue(observed.all { !it.episodesLoading && it.title == "中文条目名称" }, observed.toString())
        assertEquals(listOf("episode-1", "episode-2", "episode-3"), presenter.uiState.value.items.map { it.key })

        presenter.reload()
        val reloading = awaitState { it.episodesLoading }
        assertEquals("中文条目名称", reloading.title)
        assertEquals(listOf("episode-1", "episode-2", "episode-3"), reloading.items.map { it.key })
        assertFalse(reloading.episodesFailed)

        subjects.collection.value = collection
        val reloaded = awaitState { !it.episodesLoading && !it.downloadsLoading }
        assertEquals("中文条目名称", reloaded.title)
    }

    /**
     * @param subscribe 是否在整个测试期间保持订阅 [SubjectDownloadsPresenter.uiState] 与 [SubjectDownloadsPresenter.requestDialogs].
     */
    private class Fixture(private val testScope: TestScope, subscribe: Boolean) {
        val storage = FakeDownloadStorage()
        val downloadManager = MediaDownloadManager(listOf(storage), testScope.backgroundScope)
        val deleteCache = FakeDeleteCacheUseCase(downloadManager)

        /**
         * 操作的执行作用域. 提交操作会立即在其中创建协程, 因此在 [runCurrent] 之前检查 [pendingOperationCount] 即可判断是否提交了操作.
         */
        private val operationJob = SupervisorJob(testScope.backgroundScope.coroutineContext.job)
        val operations = DownloadOperations(
            downloadManager, deleteCache,
            CoroutineScope(testScope.backgroundScope.coroutineContext + operationJob),
        )
        val pendingOperationCount: Int get() = operationJob.children.count()

        val subjects = FakeSubjectCollectionRepository().apply { collection.value = testSubjectCollection(subjectId = 1, episodeCount = 3) }
        val histories = FakeEpisodePlayHistoryRepository()
        val preferences = FakeEpisodePreferencesRepository()
        val fetcher = FakeMediaFetcher()
        val sources = FakeMediaSourceManager(fetcher)
        val addDownload = FakeAddDownloadUseCase(storage)
        val sessionFactory = DownloadRequestSessionFactory(
            subjects, preferences, sources, fakeMediaSelectorFactory(), downloadManager, addDownload,
        )
        val presenter = SubjectDownloadsPresenter(
            subjectId = 1,
            parentScope = testScope.backgroundScope,
            subjects = subjects,
            histories = histories,
            settings = FakeSettingsRepository(),
            sources = sources,
            downloadManager = downloadManager,
            sessionFactory = sessionFactory,
            operations = operations,
        )

        init {
            if (subscribe) {
                // 保持订阅, 让两个状态流在整个测试期间持续更新.
                subscribeUiState()
                testScope.backgroundScope.launch { presenter.requestDialogs.collect() }
            }
        }

        /**
         * 在后台订阅 [SubjectDownloadsPresenter.uiState], 收到的每个值记入 [into]. 取消返回的 [Job] 即取消订阅.
         */
        fun subscribeUiState(into: MutableList<SubjectDownloadsUiState> = mutableListOf()): Job =
            testScope.backgroundScope.launch { presenter.uiState.collect { into += it } }

        suspend fun awaitState(predicate: (SubjectDownloadsUiState) -> Boolean): SubjectDownloadsUiState =
            presenter.uiState.first(predicate)

        suspend fun awaitDialogs(predicate: (DownloadRequestDialogState?) -> Boolean): DownloadRequestDialogState? =
            presenter.requestDialogs.first(predicate)

        fun runCurrent() = testScope.runCurrent()

        fun advanceTimeBy(millis: Long) = testScope.advanceTimeBy(millis)

        fun close() {
            presenter.close()
        }
    }

    private fun withFixture(subscribe: Boolean = true, block: suspend Fixture.() -> Unit) = runTest {
        val fixture = Fixture(this, subscribe)
        try {
            fixture.block()
        } finally {
            fixture.close()
        }
    }
}
