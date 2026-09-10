/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata

data class EpisodeDownloadRequest(val subject: SubjectInfo, val episode: EpisodeInfo)

/** Query resources belong only to the editable session, never to a submitted plan. */
class DownloadMediaSelection(
    val request: EpisodeDownloadRequest,
    val fetchSession: MediaFetchSession,
    val selector: MediaSelector,
    val selectMedia: suspend (Media) -> Unit = { selector.select(it) },
)

sealed interface EpisodeDownloadState {
    data object Preparing : EpisodeDownloadState
    data class ChoosingMedia(val selection: DownloadMediaSelection) : EpisodeDownloadState
    data class SelectingMedia(val selection: DownloadMediaSelection, val media: Media) : EpisodeDownloadState
    data class Ready(val selection: DownloadMediaSelection, val spec: EpisodeDownloadSpec) : EpisodeDownloadState
    sealed interface Failed : EpisodeDownloadState {
        val cause: Throwable

        data class Preparation(override val cause: Throwable) : Failed
        data class Selection(
            override val cause: Throwable,
            val selection: DownloadMediaSelection,
            val media: Media,
        ) : Failed
    }
}

sealed interface AddDownloadsState {
    data object Idle : AddDownloadsState

    sealed interface Active : AddDownloadsState {
        val requestId: Long
        val episodeIds: Set<Int>
    }

    data class Editing(
        override val requestId: Long,
        val episodes: PersistentMap<Int, EpisodeDownloadState>,
    ) : Active {
        override val episodeIds get() = episodes.keys
        val canSubmit get() = episodes.isNotEmpty() && episodes.values.all { it is EpisodeDownloadState.Ready }
    }

    data class Submitting(override val requestId: Long, val plan: DownloadPlan) : Active {
        override val episodeIds get() = plan.items.map { it.episode.episodeId }.toPersistentSet()
    }

    data class Completed(override val requestId: Long, val result: DownloadSubmissionResult) : Active {
        override val episodeIds get() = result.items.map { it.spec.episode.episodeId }.toPersistentSet()
    }
}

/**
 * One subject's editable download session. Commands change state synchronously under a short lock;
 * query work runs outside it, with a separate scope per episode. Retained episodes keep their queries.
 * Submission transfers a frozen plan to the application before releasing all query resources.
 */
class AddDownloadsSession(
    parentScope: CoroutineScope,
    private val prepare: suspend (episodeId: Int, scope: CoroutineScope) -> DownloadMediaSelection,
    private val findReusableMedia: suspend (DownloadMediaSelection) -> Media?,
    private val submitDownloads: (DownloadPlan) -> Deferred<DownloadSubmissionResult>,
    private val submitWhenReady: Boolean = false,
) : AutoCloseable {
    private val lock = SynchronizedObject()
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val preparationPermits = Semaphore(3)
    private val mutableState = MutableStateFlow<AddDownloadsState>(AddDownloadsState.Idle)
    val state = mutableState.asStateFlow()
    private var nextRequestId = 0L
    private val episodes = mutableMapOf<Int, EpisodeWork>()

    private class EpisodeWork(val scope: CoroutineScope)

    fun start(episodeIds: Set<Int>) = synchronized(lock) {
        if (!scope.isActive || mutableState.value is AddDownloadsState.Submitting) return
        require(episodeIds.isNotEmpty())
        releaseQueriesLocked()
        val requestId = ++nextRequestId
        mutableState.value = AddDownloadsState.Editing(requestId, persistentMapOf())
        setEpisodesLocked(requestId, episodeIds.toSet())
    }

    fun setEpisodes(requestId: Long, episodeIds: Set<Int>) = synchronized(lock) {
        val current = mutableState.value as? AddDownloadsState.Editing ?: return
        if (!scope.isActive || current.requestId != requestId) return
        require(episodeIds.isNotEmpty())
        setEpisodesLocked(requestId, episodeIds.toSet())
    }

    private fun setEpisodesLocked(requestId: Long, episodeIds: Set<Int>) {
        val current = mutableState.value as AddDownloadsState.Editing
        val removed = episodes.keys - episodeIds
        removed.forEach { episodes.remove(it)?.scope?.cancel() }
        val added = episodeIds - current.episodeIds
        mutableState.value = current.copy(episodes = episodeIds.associateWith {
            current.episodes[it] ?: EpisodeDownloadState.Preparing
        }.toPersistentMap())
        added.forEach { episodeId ->
            val work = EpisodeWork(CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])))
            episodes[episodeId] = work
            prepareLocked(requestId, episodeId, work)
        }
        submitIfReadyLocked()
    }

    private fun prepareLocked(requestId: Long, episodeId: Int, work: EpisodeWork) {
        updateEpisodeLocked(requestId, episodeId, work, EpisodeDownloadState.Preparing)
        work.scope.launch {
            try {
                val (selection, reusable) = preparationPermits.withPermit {
                    val selection = prepare(episodeId, work.scope)
                    selection to findReusableMedia(selection)
                }
                val ready = reusable?.let { EpisodeDownloadState.Ready(selection, selection.snapshot(it)) }
                synchronized(lock) {
                    updateEpisodeLocked(requestId, episodeId, work, ready ?: EpisodeDownloadState.ChoosingMedia(selection))
                    submitIfReadyLocked()
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                synchronized(lock) { updateEpisodeLocked(requestId, episodeId, work, EpisodeDownloadState.Failed.Preparation(e)) }
            }
        }
    }

    fun selectMedia(requestId: Long, episodeId: Int, media: Media) = synchronized(lock) {
        val current = mutableState.value as? AddDownloadsState.Editing ?: return
        if (!scope.isActive || current.requestId != requestId) return
        val selection = when (val entry = current.episodes[episodeId]) {
            is EpisodeDownloadState.ChoosingMedia -> entry.selection
            is EpisodeDownloadState.Ready -> entry.selection
            else -> return
        }
        selectMediaLocked(requestId, episodeId, checkNotNull(episodes[episodeId]), selection, media)
    }

    private fun selectMediaLocked(
        requestId: Long,
        episodeId: Int,
        work: EpisodeWork,
        selection: DownloadMediaSelection,
        media: Media,
    ) {
        updateEpisodeLocked(requestId, episodeId, work, EpisodeDownloadState.SelectingMedia(selection, media))
        work.scope.launch {
            try {
                selection.selectMedia(media)
                val spec = selection.snapshot(media)
                synchronized(lock) {
                    updateEpisodeLocked(requestId, episodeId, work, EpisodeDownloadState.Ready(selection, spec))
                    submitIfReadyLocked()
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                synchronized(lock) {
                    updateEpisodeLocked(requestId, episodeId, work, EpisodeDownloadState.Failed.Selection(e, selection, media))
                }
            }
        }
    }

    fun submit(requestId: Long) = synchronized(lock) {
        val current = mutableState.value as? AddDownloadsState.Editing ?: return
        if (!scope.isActive || current.requestId != requestId || !current.canSubmit) return
        submitLocked(requestId, DownloadPlan(current.episodes.values.map { (it as EpisodeDownloadState.Ready).spec }))
    }

    private fun submitIfReadyLocked() {
        val current = mutableState.value as? AddDownloadsState.Editing ?: return
        if (scope.isActive && submitWhenReady && current.canSubmit) {
            submitLocked(current.requestId, DownloadPlan(current.episodes.values.map { (it as EpisodeDownloadState.Ready).spec }))
        }
    }

    private fun submitLocked(requestId: Long, plan: DownloadPlan, previous: DownloadSubmissionResult? = null) {
        val pending = submitDownloads(plan)
        mutableState.value = AddDownloadsState.Submitting(requestId, plan)
        releaseQueriesLocked()
        scope.launch {
            val result = try {
                pending.await()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                DownloadSubmissionResult(plan.items.map { DownloadSubmissionItem(it, DownloadSubmissionOutcome.Failed(e)) })
            }
            synchronized(lock) {
                if (scope.isActive && (mutableState.value as? AddDownloadsState.Submitting)?.requestId == requestId) {
                    mutableState.value = AddDownloadsState.Completed(requestId, previous?.withRetry(result) ?: result)
                }
            }
        }
    }

    fun retry(requestId: Long): Unit = synchronized(lock) {
        if (!scope.isActive) return
        when (val current = mutableState.value) {
            is AddDownloadsState.Completed -> {
                if (current.requestId != requestId) return
                current.result.retryPlan()?.let { submitLocked(requestId, it, current.result) }
            }
            is AddDownloadsState.Editing -> {
                if (current.requestId != requestId) return
                current.episodes.forEach { (episodeId, state) ->
                    if (state !is EpisodeDownloadState.Failed) return@forEach
                    val work = checkNotNull(episodes[episodeId])
                    if (state is EpisodeDownloadState.Failed.Selection) {
                        selectMediaLocked(requestId, episodeId, work, state.selection, state.media)
                    } else {
                        work.scope.cancel()
                        val replacement = EpisodeWork(CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])))
                        episodes[episodeId] = replacement
                        prepareLocked(requestId, episodeId, replacement)
                    }
                }
            }
            else -> Unit
        }
    }

    fun cancel(requestId: Long) = synchronized(lock) {
        val current = mutableState.value as? AddDownloadsState.Active ?: return
        if (current.requestId != requestId || current is AddDownloadsState.Submitting) return
        releaseQueriesLocked()
        mutableState.value = AddDownloadsState.Idle
    }

    private fun updateEpisodeLocked(requestId: Long, episodeId: Int, work: EpisodeWork, state: EpisodeDownloadState) {
        val current = mutableState.value as? AddDownloadsState.Editing ?: return
        if (scope.isActive && current.requestId == requestId && episodes[episodeId] === work) {
            mutableState.value = current.copy(episodes = current.episodes.put(episodeId, state))
        }
    }

    private fun releaseQueriesLocked() {
        episodes.values.forEach { it.scope.cancel() }
        episodes.clear()
    }

    override fun close() = synchronized(lock) {
        releaseQueriesLocked()
        scope.cancel()
    }
}

private suspend fun DownloadMediaSelection.snapshot(media: Media): EpisodeDownloadSpec = EpisodeDownloadSpec(
    request.subject,
    request.episode,
    media,
    MediaCacheMetadata(fetchSession.request.first()),
)
