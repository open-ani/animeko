/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.domain.media.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.utils.platform.annotations.TestOnly

class DownloadRequestSessionTest {
    private val media = TestMediaList.first()

    @Test
    fun `construction does no work until start is called`() = withFixture {
        val session = create(listOf(1))
        testScope.runCurrent()
        assertEquals(0, subjectLoads)
        assertTrue(queried.isEmpty())
        assertEquals(DownloadRequestState.Preparing(1, listOf(1)), session.state.value)

        session.start()
        testScope.runCurrent()
        assertEquals(1, subjectLoads)
        assertEquals(listOf(1), queried)
        assertEquals(1, assertIs<DownloadRequestState.AwaitingSelection>(session.state.value).episodeId)
    }

    @Test
    fun `start can only be called once`() = withFixture {
        val session = create(listOf(1))
        session.start()
        assertFailsWith<IllegalStateException> { session.start() }
        testScope.runCurrent()
        assertEquals(1, subjectLoads)
    }

    @Test
    fun `episodes are processed in input order with a shrinking pending list`() = withFixture {
        val ids = listOf(3, 1, 4, 2, 6, 5)
        val session = create(ids)
        val states = recordStates(session)
        session.start()
        for ((index, id) in ids.withIndex()) {
            testScope.runCurrent()
            val awaiting = assertIs<DownloadRequestState.AwaitingSelection>(session.state.value)
            assertEquals(id, awaiting.episodeId)
            assertEquals(ids.drop(index), awaiting.pendingEpisodeIds)
            assertEquals(ids.take(index), created.map { it.episodeId })
            assertTrue(session.select(id, media))
        }
        testScope.runCurrent()

        assertEquals(DownloadRequestState.Finished(), session.state.value)
        assertEquals(ids, created.map { it.episodeId })
        assertEquals(ids, queried)
        assertEquals(ids, released)
        assertEquals(ids.size, savedPreferences.size)
        for (creation in created) {
            assertSame(media, creation.media)
            assertEquals(subject.subjectInfo, creation.subject)
            assertEquals(requestTestEpisode(creation.episodeId), creation.episode)
            assertEquals(subject.subjectId.toString(), creation.metadata.subjectId)
            assertEquals(creation.episodeId.toString(), creation.metadata.episodeId)
            assertEquals(EpisodeSort(creation.episodeId), creation.metadata.episodeSort)
        }
        val expected = buildList {
            for ((index, id) in ids.withIndex()) {
                val pending = ids.drop(index)
                add("Preparing($id, $pending)")
                add("AwaitingSelection($id, $pending)")
                add("Creating($id, $pending)")
            }
            add("Finished(null)")
        }
        assertEquals(expected, states.map { it.describe() })
    }

    @Test
    fun `preparing state is visible while subject information loads`() = withFixture {
        prepareGate = CompletableDeferred()
        val session = create(listOf(2, 5))
        session.start()
        testScope.runCurrent()
        assertEquals(DownloadRequestState.Preparing(2, listOf(2, 5)), session.state.value)
        assertTrue(queried.isEmpty())

        prepareGate!!.complete(Unit)
        testScope.runCurrent()
        assertEquals(2, assertIs<DownloadRequestState.AwaitingSelection>(session.state.value).episodeId)
    }

    @Test
    fun `select is rejected for other episodes and for repeated choices`() = withFixture {
        val session = create(listOf(1, 2))
        session.start()
        testScope.runCurrent()
        val awaiting = assertIs<DownloadRequestState.AwaitingSelection>(session.state.value)

        assertFalse(session.select(2, media))
        testScope.runCurrent()
        assertSame(awaiting, session.state.value)
        assertTrue(created.isEmpty())

        assertTrue(session.select(1, media))
        assertFalse(session.select(1, media))
        testScope.runCurrent()
        assertFalse(session.select(1, media))
        assertEquals(listOf(1), created.map { it.episodeId })
        assertEquals(2, assertIs<DownloadRequestState.AwaitingSelection>(session.state.value).episodeId)
    }

    @Test
    fun `existing season download is reused without querying`() = withFixture {
        val season = requestTestMedia(100, EpisodeRange.range(1, 6))
        storage.listFlow.value = listOf(requestTestCache(season, subjectId = subject.subjectId, episodeId = 1))
        testScope.runCurrent()

        val session = create(listOf(3, 1, 2))
        val states = recordStates(session)
        session.start()
        testScope.runCurrent()

        assertEquals(DownloadRequestState.Finished(), session.state.value)
        assertEquals(listOf(3, 1, 2), created.map { it.episodeId })
        assertTrue(created.all { it.media === season })
        assertTrue(queried.isEmpty())
        assertTrue(savedPreferences.isEmpty())
        assertEquals(
            listOf(
                "Preparing(3, [3, 1, 2])", "Creating(3, [3, 1, 2])",
                "Preparing(1, [1, 2])", "Creating(1, [1, 2])",
                "Preparing(2, [2])", "Creating(2, [2])",
                "Finished(null)",
            ),
            states.map { it.describe() },
        )
    }

    @Test
    fun `season media chosen for the first episode is reused for the rest`() = withFixture {
        val season = requestTestMedia(100, EpisodeRange.range(1, 6))
        val session = create(listOf(1, 2, 3))
        session.start()
        testScope.runCurrent()
        assertTrue(session.select(1, season))
        testScope.runCurrent()

        assertEquals(DownloadRequestState.Finished(), session.state.value)
        assertEquals(listOf(1, 2, 3), created.map { it.episodeId })
        assertTrue(created.all { it.media === season })
        assertEquals(listOf(1), queried)
        assertEquals(1, savedPreferences.size)
    }

    @Test
    fun `media created in this session is reused before the manager lists it`() = withFixture {
        listCreated = false
        val season = requestTestMedia(100, EpisodeRange.range(1, 6))
        val session = create(listOf(1, 2, 3))
        session.start()
        testScope.runCurrent()
        assertTrue(session.select(1, season))
        testScope.runCurrent()

        assertEquals(DownloadRequestState.Finished(), session.state.value)
        assertEquals(listOf(1, 2, 3), created.map { it.episodeId })
        assertTrue(created.all { it.media === season })
        assertEquals(listOf(1), queried)
        assertTrue(storage.listFlow.value.isEmpty())
    }

    @Test
    fun `single episode media chosen for the first episode is not reused`() = withFixture {
        val session = create(listOf(1, 2))
        session.start()
        testScope.runCurrent()
        assertTrue(session.select(1, requestTestMedia(100, EpisodeRange.single(EpisodeSort(1)))))
        testScope.runCurrent()
        assertEquals(2, assertIs<DownloadRequestState.AwaitingSelection>(session.state.value).episodeId)
        assertEquals(listOf(1, 2), queried)
    }

    @Test
    fun `subject loading failure finishes with the error before any query`() = withFixture {
        val failure = IllegalStateException("subject failed")
        subjectFailure = failure
        val session = create(listOf(1, 2))
        session.start()
        testScope.runCurrent()

        assertSame(failure, assertIs<DownloadRequestState.Finished>(session.state.value).error)
        assertEquals(1, subjectLoads)
        assertTrue(queried.isEmpty())
        assertTrue(created.isEmpty())
    }

    @Test
    fun `missing episode finishes with the error and keeps earlier downloads`() = withFixture {
        val session = create(listOf(1, 99, 2))
        session.start()
        testScope.runCurrent()
        assertTrue(session.select(1, media))
        testScope.runCurrent()

        assertIs<NoSuchElementException>(assertIs<DownloadRequestState.Finished>(session.state.value).error)
        assertEquals(listOf(1), created.map { it.episodeId })
        assertEquals(listOf(1), queried)
        assertEquals(2, subjectLoads)
    }

    @Test
    fun `preference failure stops before creating that episode`() = withFixture {
        preferenceFailure = 2
        val session = create(listOf(1, 2, 3))
        session.start()
        testScope.runCurrent()
        assertTrue(session.select(1, media))
        testScope.runCurrent()
        assertTrue(session.select(2, media))
        testScope.runCurrent()

        val finished = assertIs<DownloadRequestState.Finished>(session.state.value)
        assertEquals("save failed", assertIs<IllegalStateException>(finished.error).message)
        assertEquals(listOf(1), created.map { it.episodeId })
        assertEquals(listOf(1), creationStarted)
        assertEquals(listOf(1, 2), queried)
        assertEquals(listOf(1, 2), released)
        assertEquals(1, savedPreferences.size)
        assertFalse(session.select(2, media))
    }

    @Test
    fun `creation failure keeps earlier downloads and stops`() = withFixture(supervisedApplication = false) {
        creationFailure = 2
        val session = create(listOf(1, 2, 3))
        session.start()
        testScope.runCurrent()
        assertTrue(session.select(1, media))
        testScope.runCurrent()
        assertTrue(session.select(2, media))
        testScope.runCurrent()

        val finished = assertIs<DownloadRequestState.Finished>(session.state.value)
        assertEquals("create failed", assertIs<IllegalStateException>(finished.error).message)
        assertEquals(listOf(1), created.map { it.episodeId })
        assertEquals(listOf(1, 2), creationStarted)
        assertEquals(listOf(1, 2), queried)
        assertEquals(2, savedPreferences.size)
        assertTrue(applicationScope.isActive)
    }

    @Test
    fun `cancel during selection releases the query and rejects later choices`() = withFixture {
        val session = create(listOf(1, 2))
        session.start()
        testScope.runCurrent()
        assertEquals(listOf(1), queried)
        assertTrue(released.isEmpty())

        session.cancel()
        assertEquals(DownloadRequestState.Finished(), session.state.value)
        testScope.runCurrent()
        assertEquals(listOf(1), released)
        assertFalse(session.select(1, media))
        testScope.runCurrent()
        assertTrue(created.isEmpty())
        assertEquals(1, subjectLoads)
    }

    @Test
    fun `cancel during creation lets the accepted download finish and skips the rest`() = withFixture {
        createGate = CompletableDeferred()
        val session = create(listOf(1, 2))
        session.start()
        testScope.runCurrent()
        assertTrue(session.select(1, media))
        testScope.runCurrent()
        assertEquals(DownloadRequestState.Creating(1, listOf(1, 2)), session.state.value)
        assertEquals(listOf(1), creationStarted)
        assertTrue(created.isEmpty())

        session.cancel()
        testScope.runCurrent()
        assertEquals(DownloadRequestState.Finished(), session.state.value)
        assertTrue(created.isEmpty())

        createGate!!.complete(Unit)
        testScope.runCurrent()
        assertEquals(listOf(1), created.map { it.episodeId })
        assertEquals(listOf(1), queried)
        assertEquals(1, subjectLoads)
    }

    @Test
    fun `preference is saved after selection and before creation`() = withFixture {
        val session = create(listOf(1))
        session.start()
        testScope.runCurrent()
        assertTrue(session.select(1, media))
        testScope.runCurrent()

        assertEquals(DownloadRequestState.Finished(), session.state.value)
        assertEquals(listOf("save:1", "create:1"), log)
        val saved = savedPreferences.single()
        assertEquals(media.properties.alliance, saved.alliance)
        assertEquals(media.properties.resolution, saved.resolution)
        assertEquals(media.mediaSourceId, saved.mediaSourceId)
    }

    @Test
    fun `latest dialog preference is saved when the selector already holds the media`() = withFixture {
        val session = create(listOf(1))
        session.start()
        testScope.runCurrent()
        val selector = assertIs<DownloadRequestState.AwaitingSelection>(session.state.value).selector
        assertTrue(selector.select(media))
        testScope.runCurrent()
        selector.alliance.prefer("Dialog Group")
        testScope.runCurrent()
        assertTrue(savedPreferences.isEmpty())

        assertTrue(session.select(1, media))
        testScope.runCurrent()
        assertEquals(DownloadRequestState.Finished(), session.state.value)
        assertEquals("Dialog Group", savedPreferences.single().alliance)
        assertEquals(listOf("save:1", "create:1"), log)
    }

    @Test
    fun `parent scope cancellation finishes the session`() = withFixture {
        val parent = CoroutineScope(testScope.backgroundScope.coroutineContext + Job(sessionScope.coroutineContext[Job]))
        val session = create(listOf(1, 2), parentScope = parent)
        session.start()
        testScope.runCurrent()
        assertIs<DownloadRequestState.AwaitingSelection>(session.state.value)

        parent.cancel()
        testScope.runCurrent()
        assertEquals(DownloadRequestState.Finished(), session.state.value)
        assertEquals(listOf(1), released)
        assertFalse(session.select(1, media))
        assertTrue(created.isEmpty())
    }

    @Test
    fun `parent scope cancelled before start finishes without work`() = withFixture {
        val parent = CoroutineScope(testScope.backgroundScope.coroutineContext + Job(sessionScope.coroutineContext[Job]))
        val session = create(listOf(1), parentScope = parent)
        parent.cancel()
        testScope.runCurrent()
        assertEquals(DownloadRequestState.Finished(), session.state.value)

        session.start()
        testScope.runCurrent()
        assertEquals(DownloadRequestState.Finished(), session.state.value)
        assertEquals(0, subjectLoads)
    }

    private fun DownloadRequestState.describe(): String = when (this) {
        is DownloadRequestState.Preparing -> "Preparing($episodeId, $pendingEpisodeIds)"
        is DownloadRequestState.AwaitingSelection -> "AwaitingSelection($episodeId, $pendingEpisodeIds)"
        is DownloadRequestState.Creating -> "Creating($episodeId, $pendingEpisodeIds)"
        is DownloadRequestState.Finished -> "Finished(${error?.message})"
    }

    private fun withFixture(
        supervisedApplication: Boolean = true,
        block: suspend DownloadRequestFixture.() -> Unit,
    ) = runTest {
        val fixture = DownloadRequestFixture(this, supervisedApplication)
        try {
            fixture.block()
        } finally {
            fixture.close()
        }
    }
}
