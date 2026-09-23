/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.episode.danmaku

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuEpisode
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.danmaku.api.provider.DanmakuFetchResult
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import me.him188.ani.danmaku.api.provider.DanmakuSubject
import me.him188.ani.danmaku.api.provider.MatchingDanmakuProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchingDanmakuPresenterTest {
    @Test
    fun cancellingSearchPreventsALateResultFromUpdatingTheState() = runTest {
        val pending = CompletableDeferred<Unit>()
        val presenter = MatchingDanmakuPresenter(TestProvider(
            subjects = {
                withContext(NonCancellable) { pending.await() }
                listOf(DanmakuSubject("late", "Late"))
            },
        ), backgroundScope)
        presenter.submitQuery("query")
        runCurrent()
        assertTrue(presenter.uiState.value.isLoadingSubjects)
        presenter.cancel()
        pending.complete(Unit)
        runCurrent()
        assertFalse(presenter.uiState.value.isLoadingSubjects)
        assertTrue(presenter.uiState.value.subjects.isEmpty())
        assertNull(presenter.uiState.value.subjectError)
    }

    @Test
    fun aNewQueryReplacesThePendingSearch() = runTest {
        val pending = CompletableDeferred<Unit>()
        val presenter = MatchingDanmakuPresenter(TestProvider(
            subjects = { query ->
                if (query == "first") pending.await()
                listOf(DanmakuSubject(query, query))
            },
        ), backgroundScope)
        presenter.submitQuery("first")
        runCurrent()
        presenter.submitQuery("second")
        runCurrent()
        assertEquals(listOf(DanmakuSubject("second", "second")), presenter.uiState.value.subjects)
        assertEquals("second", presenter.uiState.value.initialQuery)
        assertFalse(presenter.uiState.value.isLoadingSubjects)
        assertNull(presenter.uiState.value.subjectError)
    }

    @Test
    fun returningToSubjectsCancelsEpisodeLoadingWithoutShowingAnError() = runTest {
        val subject = DanmakuSubject("subject", "Subject")
        val pending = CompletableDeferred<Unit>()
        val presenter = MatchingDanmakuPresenter(TestProvider(
            subjects = { listOf(subject) },
            episodes = {
                withContext(NonCancellable) { pending.await() }
                listOf(DanmakuEpisode("episode", "Episode"))
            },
        ), backgroundScope)
        presenter.submitQuery("query")
        runCurrent()
        presenter.selectSubject(subject)
        runCurrent()
        assertTrue(presenter.uiState.value.isLoadingEpisodes)
        presenter.backToSubjects()
        pending.complete(Unit)
        runCurrent()
        assertEquals(listOf(subject), presenter.uiState.value.subjects)
        assertNull(presenter.uiState.value.selectedSubject)
        assertFalse(presenter.uiState.value.isLoadingEpisodes)
        assertTrue(presenter.uiState.value.episodes.isEmpty())
        assertNull(presenter.uiState.value.episodeError)
    }

    private class TestProvider(
        private val subjects: suspend (String) -> List<DanmakuSubject>,
        private val episodes: suspend (DanmakuSubject) -> List<DanmakuEpisode> = { emptyList() },
    ) : MatchingDanmakuProvider {
        override val providerId = DanmakuProviderId.Dandanplay
        override val mainServiceId = DanmakuServiceId.Dandanplay
        override suspend fun fetchSubjectList(name: String) = subjects(name)
        override suspend fun fetchEpisodeList(subject: DanmakuSubject) = episodes(subject)
        override suspend fun fetchDanmakuList(subject: DanmakuSubject, episode: DanmakuEpisode): List<DanmakuFetchResult> = emptyList()
        override suspend fun fetchAutomatic(request: DanmakuFetchRequest): List<DanmakuFetchResult> = emptyList()
    }
}
