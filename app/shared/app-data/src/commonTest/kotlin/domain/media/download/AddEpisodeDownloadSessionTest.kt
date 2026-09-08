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
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.fetch.CompletedConditions
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.datasources.api.source.MediaFetchRequest

class AddEpisodeDownloadSessionTest {
    private val media = TestMediaList.first()

    @Test
    fun `manual selections persist preferences before completion including subsequent episodes`() = runTest {
        val saved = mutableListOf<MediaPreference>()
        val firstSelector = selection(1).selector
        selectMediaAndSavePreference(firstSelector, media) { saved += it }
        selectMediaAndSavePreference(firstSelector, media) { saved += it }
        assertEquals(1, saved.size)
        selectMediaAndSavePreference(selection(2).selector, media) { saved += it }
        assertEquals(2, saved.size)
    }

    @Test
    fun `switch during metadata loading cancels old work and ignores late completion`() = runTest {
        val finishOldRequest = CompletableDeferred<Unit>()
        val session = session(prepare = { id, _ ->
            if (id == 1) withContext(NonCancellable) { finishOldRequest.await() }
            selection(id)
        })
        session.start(1)
        runCurrent()
        session.start(2)
        runCurrent()
        finishOldRequest.complete(Unit)
        runCurrent()
        assertEquals(2, assertIs<AddDownloadState.ChoosingMedia>(session.state.value).episodeId)
        session.close()
    }

    @Test
    fun `old picker callbacks cannot select media or cancel a new request`() = runTest {
        var submissions = 0
        val session = session(create = { submissions++ })
        session.start(1)
        runCurrent()
        val old = assertIs<AddDownloadState.ChoosingMedia>(session.state.value)
        session.start(2)
        runCurrent()
        session.selectMedia(old.requestId, media)
        session.cancel(old.requestId)
        runCurrent()
        assertEquals(0, submissions)
        assertEquals(2, assertIs<AddDownloadState.ChoosingMedia>(session.state.value).episodeId)
        session.close()
    }

    @Test
    fun `double selection submits once and cannot replace a submission`() = runTest {
        val finish = CompletableDeferred<Unit>()
        var submissions = 0
        val session = session(create = { submissions++; finish.await() })
        session.start(1)
        runCurrent()
        val choosing = assertIs<AddDownloadState.ChoosingMedia>(session.state.value)
        session.selectMedia(choosing.requestId, media)
        session.selectMedia(choosing.requestId, media)
        runCurrent()
        session.start(2)
        session.cancel(choosing.requestId)
        assertEquals(1, submissions)
        assertIs<AddDownloadState.Submitting>(session.state.value)
        finish.complete(Unit)
        runCurrent()
        assertEquals(AddDownloadState.Idle, session.state.value)
        session.close()
    }

    @Test
    fun `failed submission retries the same target without repeating search`() = runTest {
        var preparations = 0
        val submitted = mutableListOf<DownloadTarget>()
        val session = session(
            prepare = { id, _ -> preparations++; selection(id) },
            create = { submitted += it; if (submitted.size == 1) error("disk unavailable") },
        )
        session.start(1)
        runCurrent()
        session.selectMedia(assertIs<AddDownloadState.ChoosingMedia>(session.state.value).requestId, media)
        runCurrent()
        val failed = assertIs<AddDownloadState.Failed>(session.state.value)
        session.retry(failed.requestId)
        runCurrent()
        assertEquals(1, preparations)
        assertEquals(2, submitted.size)
        assertSame(submitted[0], submitted[1])
        assertEquals(AddDownloadState.Idle, session.state.value)
        session.close()
    }

    @Test
    fun `manual media selection submits directly after saving its preference`() = runTest {
        val events = mutableListOf<String>()
        val prepared = selection(1)
        val selection = DownloadMediaSelection(prepared.request, prepared.fetchSession, prepared.selector) {
            assertSame(media, it)
            events += "preference saved"
        }
        var submitted: DownloadTarget? = null
        val session = session(
            prepare = { _, _ -> selection },
            create = { submitted = it; events += "submitted" },
        )
        session.start(1)
        runCurrent()
        val choosing = assertIs<AddDownloadState.ChoosingMedia>(session.state.value)
        session.selectMedia(choosing.requestId, media)
        runCurrent()

        assertEquals(listOf("preference saved", "submitted"), events)
        assertSame(selection, submitted?.selection)
        assertSame(media, submitted?.media)
        assertEquals(AddDownloadState.Idle, session.state.value)
        session.close()
    }

    @Test
    fun `cancel releases query resources even after preparation finishes`() = runTest {
        var released = false
        val session = session(prepare = { id, scope ->
            scope.launch {
                try { awaitCancellation() } finally { released = true }
            }
            selection(id)
        })
        session.start(1)
        runCurrent()
        session.cancel(assertIs<AddDownloadState.ChoosingMedia>(session.state.value).requestId)
        runCurrent()
        assertTrue(released)
        assertEquals(AddDownloadState.Idle, session.state.value)
        session.close()
    }

    @Test
    fun `matching existing download submits without showing a picker`() = runTest {
        var submitted: DownloadTarget? = null
        val session = AddEpisodeDownloadSession(
            backgroundScope, { id, _ -> selection(id) },
            { media }, { submitted = it },
        )
        session.start(1)
        runCurrent()
        assertEquals(media, submitted?.media)
        assertEquals(AddDownloadState.Idle, session.state.value)
        session.close()
    }

    @Test
    fun `preparation failure is retryable`() = runTest {
        var attempts = 0
        val session = session(prepare = { id, _ -> if (++attempts == 1) error("offline"); selection(id) })
        session.start(1)
        runCurrent()
        session.retry(assertIs<AddDownloadState.Failed>(session.state.value).requestId)
        runCurrent()
        assertIs<AddDownloadState.ChoosingMedia>(session.state.value)
        session.close()
    }

    private fun TestScope.session(
        prepare: suspend (Int, CoroutineScope) -> DownloadMediaSelection = { id, _ -> selection(id) },
        create: suspend (DownloadTarget) -> Unit = {},
    ) = AddEpisodeDownloadSession(backgroundScope, prepare, { null }, create)

    private fun selection(id: Int): DownloadMediaSelection = DownloadMediaSelection(
        EpisodeDownloadRequest(SubjectInfo.Empty.copy(subjectId = 1), EpisodeInfo.Empty.copy(episodeId = id)),
        object : MediaFetchSession {
            override val request: Flow<MediaFetchRequest> = flowOf()
            override val mediaSourceResults: List<MediaSourceFetchResult> = emptyList()
            override val cumulativeResults = flowOf(TestMediaList)
            override val hasCompleted = flowOf(CompletedConditions.AllCompleted)
            override fun setFetchRequest(request: MediaFetchRequest) = Unit
        },
        DefaultMediaSelector(
            mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
            mediaListNotCached = flowOf(TestMediaList),
            savedUserPreference = flowOf(MediaPreference.Empty),
            savedDefaultPreference = flowOf(MediaPreference.Empty),
            mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
            enableCaching = false,
        ),
    )
}
