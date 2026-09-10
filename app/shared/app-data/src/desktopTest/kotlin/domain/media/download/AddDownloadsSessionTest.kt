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
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.EpisodeRange

class AddDownloadsSessionTest {
    private val media = TestMediaList.first()

    @Test
    fun `construction does no work until run is called`() = withFixture {
        val session = factory.create(1, listOf(1))
        testScope.runCurrent()
        assertTrue(prepared.isEmpty())
        assertTrue(created.isEmpty())
        start(session)
        session.awaitSelection(1)
        assertEquals(listOf(1), prepared)
    }

    @Test
    fun `six episodes are selected and created in input order`() = withFixture {
        val session = factory.create(1, (1..6).toList())
        val job = start(session)
        createGate = CompletableDeferred()
        session.awaitSelection(1)
        session.selectMedia(1, media)
        assertTrue(session.state.value.isBusy)
        session.selectMedia(1, TestMediaList.last())
        creationStarted.await()
        assertEquals(listOf(1), prepared)
        assertTrue(created.isEmpty())
        createGate!!.complete(Unit)
        for (id in 2..6) {
            session.awaitSelection(id)
            assertEquals((1 until id).toList(), created)
            assertTrue((1 until id).all { it in saved })
            session.selectMedia(id - 1, media)
            assertFalse(session.state.value.isBusy)
            session.selectMedia(id, media)
        }
        job.join()
        assertEquals((1..6).toList(), prepared)
        assertEquals((1..6).toList(), created)
        assertEquals((1..6).toSet(), released)
        assertEquals(AddDownloadsState(), session.state.value)
    }

    @Test
    fun `immediate completion keeps the finished state after selecting media`() = withFixture {
        val session = factory.create(1, listOf(1))
        val job = start(session, UnconfinedTestDispatcher(testScope.testScheduler))
        session.awaitSelection(1)
        session.selectMedia(1, media)
        job.join()
        assertEquals(AddDownloadsState(), session.state.value)
    }

    @Test
    fun `preparation failure stops before later episodes`() = failureStopsAtSecond("prepare")

    @Test
    fun `preference failure stops before creating that episode`() = failureStopsAtSecond("save")

    @Test
    fun `creation failure preserves earlier downloads and stops the flow`() = failureStopsAtSecond("create")

    private fun failureStopsAtSecond(stage: String) = withFixture {
        when (stage) {
            "prepare" -> failPreparation = 2
            "save" -> failPreference = 2
            "create" -> failCreation = 2
        }
        val session = factory.create(1, listOf(1, 2, 3))
        val job = start(session)
        session.awaitSelection(1)
        session.selectMedia(1, media)
        if (stage != "prepare") {
            session.awaitSelection(2)
            session.selectMedia(2, media)
        }
        job.join()
        assertEquals(listOf(1, 2), prepared)
        assertEquals(listOf(1), created)
        assertEquals(setOf(1, 2), released)
        assertEquals("$stage failed", assertIs<IllegalStateException>(session.state.value.error).message)
        assertEquals(2, session.state.value.episodeId)
        assertTrue(session.state.value.isFinished)
        assertFalse(session.state.value.isBusy)
        session.selectMedia(2, media)
        testScope.runCurrent()
        assertEquals(listOf(1), created)
    }

    @Test
    fun `cancelling a picker releases its query and disables its callbacks`() = withFixture {
        val old = factory.create(1, listOf(1))
        val oldJob = start(old)
        old.awaitSelection(1)
        oldJob.cancelAndJoin()
        val current = factory.create(1, listOf(1))
        start(current)
        current.awaitSelection(1)
        old.selectMedia(1, media)
        testScope.runCurrent()
        assertEquals(setOf(1), released)
        assertTrue(created.isEmpty())
        assertTrue(testScope.backgroundScope.isActive)
        assertEquals(1, current.state.value.selection!!.request.episode.episodeId)
    }

    @Test
    fun `cancelling during creation lets the accepted download finish and skips remaining episodes`() = withFixture {
        createGate = CompletableDeferred()
        val session = factory.create(1, listOf(1, 2))
        val job = start(session)
        session.awaitSelection(1)
        session.selectMedia(1, media)
        creationStarted.await()
        job.cancelAndJoin()
        createGate!!.complete(Unit)
        testScope.runCurrent()
        assertEquals(listOf(1), created)
        assertEquals(listOf(1), prepared)
        assertEquals(setOf(1), released)
    }

    @Test
    fun `reusable media creates episodes in input order without showing the picker`() = withFixture {
        storage.listFlow.value = listOf(testDownload(6, range = EpisodeRange.range(EpisodeSort(1), EpisodeSort(6))))
        val session = factory.create(1, listOf(3, 1, 2))
        start(session).join()
        assertEquals(listOf(3, 1, 2), created)
        assertTrue(saved.isEmpty())
        assertTrue(session.state.value.isFinished)
    }

    @Test
    fun `cancelling before run starts does no work`() = withFixture {
        val session = factory.create(1, listOf(1))
        start(session).cancelAndJoin()
        assertTrue(prepared.isEmpty())
        assertTrue(created.isEmpty())
    }

    private suspend fun AddDownloadsSession.awaitSelection(episodeId: Int) {
        state.first { it.selection?.request?.episode?.episodeId == episodeId }
    }

    private fun withFixture(block: suspend AddDownloadsFixture.() -> Unit) = runTest {
        val fixture = AddDownloadsFixture(this)
        try {
            fixture.seed()
            fixture.block()
        } finally {
            fixture.close()
        }
    }
}
