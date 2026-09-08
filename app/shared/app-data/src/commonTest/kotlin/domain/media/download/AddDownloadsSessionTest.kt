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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.fetch.create
import me.him188.ani.datasources.api.source.MediaFetchRequest

class AddDownloadsSessionTest {
    private val media = TestMediaList.first()

    @Test
    fun `manual selections persist preferences before completion including subsequent episodes`() = runTest {
        val saved = mutableListOf<MediaPreference>()
        val first = testDownloadSelection(1).selector
        selectMediaAndSavePreference(first, media) { saved += it }
        selectMediaAndSavePreference(first, media) { saved += it }
        assertEquals(1, saved.size)
        selectMediaAndSavePreference(testDownloadSelection(2).selector, media) { saved += it }
        assertEquals(2, saved.size)
    }

    @Test
    fun `six episodes build one frozen plan only after explicit submission`() = runTest {
        val plans = mutableListOf<DownloadPlan>()
        val session = session(submit = { plans += it; successful(it) })
        session.start((1..6).toSet())
        runCurrent()
        val requestId = editing(session).requestId
        session.submit(requestId)
        assertTrue(plans.isEmpty())
        for (id in 1..6) session.selectMedia(requestId, id, testDownload(id).origin)
        runCurrent()
        assertTrue(editing(session).canSubmit)
        assertTrue(plans.isEmpty())
        session.submit(requestId)
        session.submit(requestId)
        runCurrent()

        val plan = plans.single()
        assertEquals((1..6).toSet(), plan.items.map { it.episode.episodeId }.toSet())
        plan.items.forEach {
            assertEquals(it.episode.episodeId.toString(), it.metadata.episodeId)
            assertEquals("media-${it.episode.episodeId}", it.media.mediaId)
        }
        assertIs<AddDownloadsState.Completed>(session.state.value)
        session.close()
    }

    @Test
    fun `changing selected episodes retains queries and selections and cancels removed work`() = runTest {
        val prepared = mutableListOf<Int>()
        val released = mutableSetOf<Int>()
        val session = session(prepare = { id, scope ->
            prepared += id
            scope.launch { try { awaitCancellation() } finally { released += id } }
            testDownloadSelection(id)
        })
        session.start(setOf(1, 2))
        runCurrent()
        val current = editing(session)
        val second = assertIs<EpisodeDownloadState.ChoosingMedia>(current.episodes[2]).selection
        session.selectMedia(current.requestId, 2, media)
        runCurrent()
        session.setEpisodes(current.requestId, setOf(2, 3))
        runCurrent()

        assertEquals(listOf(1, 2, 3), prepared)
        assertEquals(setOf(1), released)
        assertSame(second, assertIs<EpisodeDownloadState.Ready>(editing(session).episodes[2]).selection)
        session.close()
        runCurrent()
        assertEquals(setOf(1, 2, 3), released)
    }

    @Test
    fun `removed and readded episode ignores late query from its old scope`() = runTest {
        val finishOld = CompletableDeferred<Unit>()
        var attempts = 0
        val newer = testDownloadSelection(1)
        val session = session(prepare = { id, _ ->
            if (id == 1 && ++attempts == 1) {
                withContext(NonCancellable) { finishOld.await() }
                testDownloadSelection(1)
            } else if (id == 1) newer else testDownloadSelection(id)
        })
        session.start(setOf(1, 2))
        runCurrent()
        val requestId = editing(session).requestId
        session.setEpisodes(requestId, setOf(2))
        session.setEpisodes(requestId, setOf(1, 2))
        runCurrent()
        finishOld.complete(Unit)
        runCurrent()
        assertSame(newer, assertIs<EpisodeDownloadState.ChoosingMedia>(editing(session).episodes[1]).selection)
        session.close()
    }

    @Test
    fun `synchronous request replacement rejects stale picker and cancellation callbacks`() = runTest {
        val session = session()
        session.start(setOf(1))
        val oldId = editing(session).requestId
        session.start(setOf(2))
        runCurrent()
        session.selectMedia(oldId, 1, media)
        session.cancel(oldId)
        assertEquals(setOf(2), editing(session).episodeIds)
        assertFalse(editing(session).canSubmit)
        session.close()
    }

    @Test
    fun `single episode auto submission uses a plan and saves preferences first`() = runTest {
        val events = mutableListOf<String>()
        val prepared = testDownloadSelection(1)
        val selection = DownloadMediaSelection(prepared.request, prepared.fetchSession, prepared.selector) {
            events += "preference saved"
        }
        val finish = CompletableDeferred<DownloadSubmissionResult>()
        var plan: DownloadPlan? = null
        val session = session(
            prepare = { _, _ -> selection },
            submit = { plan = it; events += "submitted"; finish },
            autoSubmit = true,
        )
        session.start(setOf(1))
        runCurrent()
        val requestId = editing(session).requestId
        session.selectMedia(requestId, 1, media)
        session.selectMedia(requestId, 1, media)
        runCurrent()
        session.start(setOf(2))
        session.cancel(requestId)
        session.submit(requestId)
        assertIs<AddDownloadsState.Submitting>(session.state.value)
        assertEquals(listOf("preference saved", "submitted"), events)
        assertSame(media, plan!!.items.single().media)
        finish.complete(successfulResult(plan!!))
        runCurrent()
        assertIs<AddDownloadsState.Completed>(session.state.value)
        session.close()
    }

    @Test
    fun `partial submission retry contains only failed episodes and merges all outcomes`() = runTest {
        val plans = mutableListOf<DownloadPlan>()
        val session = session(submit = { plan ->
            plans += plan
            CompletableDeferred(DownloadSubmissionResult(plan.items.map {
                DownloadSubmissionItem(it, if (plans.size == 1 && it.episode.episodeId == 2) {
                    DownloadSubmissionOutcome.Failed(IllegalStateException("disk unavailable"))
                } else DownloadSubmissionOutcome.Created("download-${it.episode.episodeId}"))
            }))
        })
        session.start(setOf(1, 2, 3))
        runCurrent()
        val requestId = editing(session).requestId
        for (id in 1..3) session.selectMedia(requestId, id, media)
        runCurrent()
        session.submit(requestId)
        runCurrent()
        assertEquals(listOf(2), assertIs<AddDownloadsState.Completed>(session.state.value).result.failures.map { it.spec.episode.episodeId })
        session.retry(requestId)
        runCurrent()
        assertEquals(listOf(2), plans[1].items.map { it.episode.episodeId })
        val result = assertIs<AddDownloadsState.Completed>(session.state.value).result
        assertEquals(listOf(1, 2, 3), result.items.map { it.spec.episode.episodeId })
        assertTrue(result.failures.isEmpty())
        session.close()
    }

    @Test
    fun `closing an unsubmitted session releases all queries without creating downloads`() = runTest {
        val released = mutableSetOf<Int>()
        var submissions = 0
        val session = session(
            prepare = { id, scope ->
                scope.launch { try { awaitCancellation() } finally { released += id } }
                testDownloadSelection(id)
            },
            submit = { submissions++; successful(it) },
        )
        session.start(setOf(1, 2))
        runCurrent()
        session.close()
        session.submit(editing(session).requestId)
        runCurrent()
        assertEquals(setOf(1, 2), released)
        assertEquals(0, submissions)
    }

    @Test
    fun `submission owns snapshots and survives closing the originating session`() = runTest {
        val storage = DownloadTestStorage()
        val manager = MediaDownloadManager(listOf(storage), backgroundScope)
        val finish = CompletableDeferred<Unit>()
        val created = mutableListOf<EpisodeDownloadSpec>()
        val submit = SubmitDownloadsUseCase(manager) {
            finish.await()
            created += it
            downloadFor(it).also { download -> storage.listFlow.value += download }
        }
        val original = testDownloadSpec(1)
        val request = MutableStateFlow(MediaFetchRequest.create(original.subject, original.episode))
        val session = session(prepare = { _, _ -> testDownloadSelection(1, request) }, submit = submit::submit)
        session.start(setOf(1))
        runCurrent()
        val requestId = editing(session).requestId
        session.selectMedia(requestId, 1, media)
        runCurrent()
        session.submit(requestId)
        request.value = request.value.copy(episodeName = "Changed after confirmation")
        session.close()
        finish.complete(Unit)
        runCurrent()
        assertEquals(original.episode.name, created.single().metadata.episodeName)
    }

    @Test
    fun `preparation retry keeps successful episodes and their selected media`() = runTest {
        val counts = mutableMapOf<Int, Int>()
        val session = session(prepare = { id, _ ->
            counts[id] = counts.getOrElse(id) { 0 } + 1
            if (id == 2 && counts[id] == 1) error("offline")
            testDownloadSelection(id)
        })
        session.start(setOf(1, 2))
        runCurrent()
        val requestId = editing(session).requestId
        session.selectMedia(requestId, 1, media)
        runCurrent()
        session.retry(requestId)
        runCurrent()
        assertEquals(mapOf(1 to 1, 2 to 2), counts)
        assertIs<EpisodeDownloadState.Ready>(editing(session).episodes[1])
        assertIs<EpisodeDownloadState.ChoosingMedia>(editing(session).episodes[2])
        session.close()
    }

    @Test
    fun `preparing many episodes limits concurrent preparation`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var started = 0
        val session = session(prepare = { id, _ -> started++; gate.await(); testDownloadSelection(id) })
        session.start((1..6).toSet())
        runCurrent()
        assertEquals(3, started)
        gate.complete(Unit)
        runCurrent()
        assertEquals(6, started)
        session.close()
    }

    @Test
    fun `reused season media creates separate episode specifications without auto submitting a batch`() = runTest {
        val plans = mutableListOf<DownloadPlan>()
        val session = AddDownloadsSession(backgroundScope, { id, _ -> testDownloadSelection(id) }, { media }, {
            plans += it
            successful(it)
        })
        session.start(setOf(1, 2))
        runCurrent()
        assertTrue(editing(session).canSubmit)
        assertTrue(plans.isEmpty())
        session.submit(editing(session).requestId)
        runCurrent()
        val items = plans.single().items
        assertEquals(setOf("1", "2"), items.map { it.metadata.episodeId }.toSet())
        assertTrue(items.all { it.media === media })
        session.close()
    }

    private fun editing(session: AddDownloadsSession) = assertIs<AddDownloadsState.Editing>(session.state.value)

    private fun successfulResult(plan: DownloadPlan) = DownloadSubmissionResult(plan.items.map {
        DownloadSubmissionItem(it, DownloadSubmissionOutcome.Created("download-${it.episode.episodeId}"))
    })

    private fun successful(plan: DownloadPlan) = CompletableDeferred(successfulResult(plan))

    private fun TestScope.session(
        prepare: suspend (Int, CoroutineScope) -> DownloadMediaSelection = { id, _ -> testDownloadSelection(id) },
        submit: (DownloadPlan) -> Deferred<DownloadSubmissionResult> = ::successful,
        autoSubmit: Boolean = false,
    ) = AddDownloadsSession(backgroundScope, prepare, { null }, submit, autoSubmit)
}
