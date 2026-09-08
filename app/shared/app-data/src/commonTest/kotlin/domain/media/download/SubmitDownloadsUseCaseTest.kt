/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SubmitDownloadsUseCaseTest {
    @Test
    fun `batch returns created existing and failed outcomes and continues after failure`() = runTest {
        val storage = DownloadTestStorage()
        storage.listFlow.value = listOf(testDownload(1))
        val attempted = mutableListOf<Int>()
        val useCase = SubmitDownloadsUseCase(MediaDownloadManager(listOf(storage), backgroundScope)) {
            attempted += it.episode.episodeId
            if (it.episode.episodeId == 2) error("disk unavailable")
            downloadFor(it).also { download -> storage.listFlow.value += download }
        }
        val result = useCase.submit(DownloadPlan((1..6).map { testDownloadSpec(it) })).await()
        assertEquals(listOf(2, 3, 4, 5, 6), attempted)
        assertIs<DownloadSubmissionOutcome.AlreadyExists>(result.items[0].outcome)
        assertIs<DownloadSubmissionOutcome.Failed>(result.items[1].outcome)
        assertTrue(result.items.drop(2).all { it.outcome is DownloadSubmissionOutcome.Created })
        assertEquals(listOf(2), result.retryPlan()!!.items.map { it.episode.episodeId })
    }

    @Test
    fun `overlapping submitted plans create each target once even while creation is suspended`() = runTest {
        val storage = DownloadTestStorage()
        val finish = CompletableDeferred<Unit>()
        val calls = mutableListOf<Int>()
        val useCase = SubmitDownloadsUseCase(MediaDownloadManager(listOf(storage), backgroundScope)) {
            calls += it.episode.episodeId
            finish.await()
            downloadFor(it).also { download -> storage.listFlow.value += download }
        }
        val first = useCase.submit(DownloadPlan(listOf(testDownloadSpec(1))))
        val second = useCase.submit(DownloadPlan(listOf(testDownloadSpec(1), testDownloadSpec(2))))
        runCurrent()
        assertEquals(listOf(1), calls)
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        finish.complete(Unit)
        assertIs<DownloadSubmissionOutcome.Created>(first.await().items.single().outcome)
        assertIs<DownloadSubmissionOutcome.AlreadyExists>(second.await().items[0].outcome)
        assertIs<DownloadSubmissionOutcome.Created>(second.await().items[1].outcome)
        assertEquals(listOf(1, 2), calls)
    }

    @Test
    fun `same season media creates independent episode records`() = runTest {
        val storage = DownloadTestStorage()
        val useCase = SubmitDownloadsUseCase(MediaDownloadManager(listOf(storage), backgroundScope)) {
            downloadFor(it).also { download -> storage.listFlow.value += download }
        }
        val season = testDownload(1).origin
        val result = useCase.submit(DownloadPlan((1..6).map { testDownloadSpec(it, season) })).await()
        assertTrue(result.items.all { it.outcome is DownloadSubmissionOutcome.Created })
        assertEquals(6, storage.listFlow.value.map { it.cacheId }.distinct().size)
    }

    @Test
    fun `cancelling an observer leaves the whole accepted plan running`() = runTest {
        val finish = CompletableDeferred<Unit>()
        val created = mutableListOf<Int>()
        val useCase = SubmitDownloadsUseCase(MediaDownloadManager(emptyList(), backgroundScope)) {
            finish.await()
            created += it.episode.episodeId
            downloadFor(it)
        }
        val result = useCase.submit(DownloadPlan((1..3).map { testDownloadSpec(it) }))
        val observer = backgroundScope.launch { result.await() }
        runCurrent()
        observer.cancel()
        finish.complete(Unit)
        assertTrue(result.await().failures.isEmpty())
        assertEquals(listOf(1, 2, 3), created)
    }

    @Test
    fun `shutdown cancels active queued and later submission results`() = runTest {
        val application = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        val useCase = SubmitDownloadsUseCase(MediaDownloadManager(emptyList(), application)) { awaitCancellation() }
        val plan = DownloadPlan(listOf(testDownloadSpec(1)))
        val active = useCase.submit(plan)
        val queued = useCase.submit(plan)
        runCurrent()
        application.cancel()
        runCurrent()
        assertTrue(active.isCancelled)
        assertTrue(queued.isCancelled)
        assertTrue(useCase.submit(plan).isCancelled)
    }

    @Test
    fun `shutdown before worker starts cancels accepted submissions`() = runTest {
        val application = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        val useCase = SubmitDownloadsUseCase(MediaDownloadManager(emptyList(), application)) { error("must not create") }
        val result = useCase.submit(DownloadPlan(listOf(testDownloadSpec(1))))
        application.cancel()
        runCurrent()
        assertTrue(result.isCancelled)
    }

    @Test
    fun `plan captures its input collection and rejects empty or duplicate targets`() {
        val inputs = mutableListOf(testDownloadSpec(1))
        val plan = DownloadPlan(inputs)
        inputs.clear()
        assertEquals(1, plan.items.size)
        assertFailsWith<IllegalArgumentException> { DownloadPlan(emptyList()) }
        assertFailsWith<IllegalArgumentException> { DownloadPlan(listOf(plan.items.single(), plan.items.single())) }
    }

    @Test
    fun `episode specification rejects metadata belonging to another episode`() {
        val spec = testDownloadSpec(1)
        assertFailsWith<IllegalArgumentException> { spec.copy(metadata = testDownloadSpec(2).metadata) }
    }
}
