/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertNotNull
import me.him188.ani.app.ui.download.subject.SubjectDownloadsPresenterFactory
import me.him188.ani.app.domain.media.download.DownloadRequestSessionFactory
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.subject.OfflineSubjectDisplayInfo
import me.him188.ani.app.data.repository.subject.staticSubjectImageLargeUrl
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.download.DownloadOperations
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.ui.download.components.DownloadStatus
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

class DownloadManagementViewModelTest {
    @Test
    fun `downloads are grouped by subject with offline info and fallbacks`() = withFixture {
        storage.listFlow.value = listOf(
            testDownloadCache(1, subjectId = 1, creationTime = 100),
            testDownloadCache(2, subjectId = 1, creationTime = 200),
            testDownloadCache(5, subjectId = 2, creationTime = 50),
        )
        subjects.collectionTypes[1] = UnifiedCollectionType.DOING
        subjects.displayInfos[1] = OfflineSubjectDisplayInfo(1, "Subject One", "https://img/1", 12)

        assertTrue(vm.uiState.value.isLoading)
        val state = awaitState { state -> state.groups.any { it.subjectName == "Subject One" } }
        assertFalse(state.isLoading)
        assertEquals(listOf(1, 2), state.groups.map { it.subjectId })

        val known = state.groups.first { it.subjectId == 1 }
        assertEquals(UnifiedCollectionType.DOING, known.collectionType)
        assertEquals("https://img/1", known.imageUrl)
        assertEquals(12, known.totalEpisodeCount)
        assertEquals(listOf(1, 2), known.entries.map { it.episodeId })
        assertTrue(known.entries.all { it.subjectCollectionType == UnifiedCollectionType.DOING })

        val unknown = state.groups.first { it.subjectId == 2 }
        assertEquals("Subject 2", unknown.subjectName)
        assertNull(unknown.collectionType)
        assertEquals(staticSubjectImageLargeUrl(2), unknown.imageUrl)
        assertNull(unknown.totalEpisodeCount)
        assertEquals(listOf(5), unknown.entries.map { it.episodeId })
    }

    @Test
    fun `groups with unfinished downloads come first and then newest creation time`() = withFixture {
        val downloading = testDownloadCache(5, subjectId = 2, creationTime = 50)
        storage.listFlow.value = listOf(
            testDownloadCache(1, subjectId = 1, creationTime = 100, state = MediaCacheState.COMPLETED),
            testDownloadCache(2, subjectId = 1, creationTime = 200, state = MediaCacheState.COMPLETED),
            downloading,
            testDownloadCache(7, subjectId = 3, creationTime = 300, state = MediaCacheState.COMPLETED),
        )
        val initial = awaitState { it.groups.size == 3 }
        assertEquals(listOf(2, 3, 1), initial.groups.map { it.subjectId })

        downloading.state.value = MediaCacheState.COMPLETED
        val finished = awaitState { state -> state.groups.all { !it.hasUnfinished } }
        assertEquals(listOf(3, 1, 2), finished.groups.map { it.subjectId })
    }

    @Test
    fun `overall stats sum storage stats and follow updates`() = runTest {
        val first = FakeDownloadStorage(MediaStats(uploaded = 300L.bytes, downloaded = 1200L.bytes, uploadSpeed = 10L.bytes, downloadSpeed = 50L.bytes))
        val second = FakeDownloadStorage(MediaStats(uploaded = 100L.bytes, downloaded = 800L.bytes, uploadSpeed = 5L.bytes, downloadSpeed = 20L.bytes))
        val fixture = Fixture(this, listOf(first, second))
        try {
            val initial = fixture.awaitState { it.overallStats.downloaded == 2000L.bytes }
            assertEquals(400L.bytes, initial.overallStats.uploaded)
            assertEquals(70L.bytes, initial.overallStats.downloadSpeed)
            assertEquals(15L.bytes, initial.overallStats.uploadSpeed)
            assertTrue(initial.groups.isEmpty())
            assertFalse(initial.isLoading)

            first.stats.value = MediaStats(uploaded = 300L.bytes, downloaded = 1500L.bytes, uploadSpeed = 10L.bytes, downloadSpeed = 50L.bytes)
            fixture.awaitState { it.overallStats.downloaded == 2300L.bytes }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `download is busy while its operation runs and is idle afterwards`() = withFixture {
        val gate = CompletableDeferred<Unit>()
        storage.listFlow.value = listOf(
            object : MediaCache by testDownloadCache(1) {
                override suspend fun pause() = gate.await()
            },
        )
        val item = awaitState { it.entries.size == 1 }.entries.single()
        assertFalse(item.isBusy)

        vm.pauseDownload(item)
        awaitState { it.entries.single().isBusy }
        gate.complete(Unit)
        awaitState { !it.entries.single().isBusy }
        assertEquals(0, vm.operationFailures.value)
    }

    @Test
    fun `pause resume and delete are applied to the download`() = withFixture {
        val cache = testDownloadCache(1)
        storage.listFlow.value = listOf(cache)
        val item = awaitState { it.entries.size == 1 }.entries.single()

        vm.pauseDownload(item)
        awaitState { it.entries.single().status == DownloadStatus.PAUSED }
        vm.resumeDownload(item)
        awaitState { it.entries.single().status == DownloadStatus.IN_PROGRESS }
        vm.deleteDownload(item)
        awaitState { it.groups.isEmpty() }
        assertTrue(storage.listFlow.value.isEmpty())
    }

    @Test
    fun `failed operations are counted and can be dismissed`() = withFixture {
        storage.listFlow.value = listOf(
            object : MediaCache by testDownloadCache(1) {
                override suspend fun pause() = throw IllegalStateException("pause failed")
            },
        )
        val item = awaitState { it.entries.size == 1 }.entries.single()

        vm.pauseDownload(item)
        vm.operationFailures.first { it == 1 }
        vm.dismissOperationFailures()
        assertEquals(0, vm.operationFailures.value)

        deleteCache.failure = IllegalStateException("delete failed")
        vm.deleteDownload(item)
        vm.operationFailures.first { it == 1 }
        assertEquals(1, storage.listFlow.value.size)
    }

    @Test
    fun `selecting a subject keeps one presenter and closes the previous one`() = withFixture {
        assertNull(vm.subjectPresenter.value)

        vm.selectSubject(1)
        val first = assertNotNull(vm.subjectPresenter.value)
        assertEquals(1, first.subjectId)
        assertFalse(first.isClosed)

        // 相同条目不重建.
        vm.selectSubject(1)
        assertSame(first, vm.subjectPresenter.value)

        // 切换条目关闭上一个实例.
        vm.selectSubject(2)
        val second = assertNotNull(vm.subjectPresenter.value)
        assertEquals(2, second.subjectId)
        assertTrue(first.isClosed)
        assertFalse(second.isClosed)

        vm.selectSubject(null)
        assertNull(vm.subjectPresenter.value)
        assertTrue(second.isClosed)
    }

    private class Fixture(testScope: TestScope, storages: List<FakeDownloadStorage>) {
        val storage: FakeDownloadStorage = storages.first()
        val downloadManager = MediaDownloadManager(storages, testScope.backgroundScope)
        val deleteCache = FakeDeleteCacheUseCase(downloadManager)
        val operations = DownloadOperations(downloadManager, deleteCache, testScope.backgroundScope)
        val subjects = FakeSubjectCollectionRepository()
        val histories = FakeEpisodePlayHistoryRepository()
        val presenters = SubjectDownloadsPresenterFactory(
            subjects, histories, FakeSettingsRepository(), FakeMediaSourceManager(), downloadManager,
            DownloadRequestSessionFactory(
                subjects, FakeEpisodePreferencesRepository(), FakeMediaSourceManager(), fakeMediaSelectorFactory(),
                downloadManager, FakeAddDownloadUseCase(storage),
            ),
            operations,
        )
        val vm = DownloadManagementViewModel(
            downloadManager, subjects, histories, operations, presenters,
            StandardTestDispatcher(testScope.testScheduler),
        )

        init {
            // 保持订阅, 让 uiState 在整个测试期间持续更新.
            testScope.backgroundScope.launch { vm.uiState.collect() }
        }

        suspend fun awaitState(predicate: (DownloadManagementUiState) -> Boolean): DownloadManagementUiState =
            vm.uiState.first(predicate)

        fun close() {
            vm.backgroundScope.cancel()
        }
    }

    private fun withFixture(block: suspend Fixture.() -> Unit) = runTest {
        val fixture = Fixture(this, listOf(FakeDownloadStorage()))
        try {
            fixture.block()
        } finally {
            fixture.close()
        }
    }
}
