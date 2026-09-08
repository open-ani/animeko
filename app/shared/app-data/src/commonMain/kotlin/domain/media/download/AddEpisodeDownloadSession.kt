/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.logging.logger

data class EpisodeDownloadRequest(val subject: SubjectInfo, val episode: EpisodeInfo)

/** Query and selector resources owned by one request, also used by the media picker. */
class DownloadMediaSelection(
    val request: EpisodeDownloadRequest,
    val fetchSession: MediaFetchSession,
    val selector: MediaSelector,
    val selectMedia: suspend (Media) -> Unit = { selector.select(it) },
)

data class DownloadTarget(
    val selection: DownloadMediaSelection,
    val media: Media,
)

sealed interface AddDownloadState {
    data object Idle : AddDownloadState

    sealed interface Active : AddDownloadState {
        val requestId: Long
        val episodeId: Int
    }

    data class Preparing(override val requestId: Long, override val episodeId: Int) : Active

    data class ChoosingMedia(
        override val requestId: Long,
        val selection: DownloadMediaSelection,
    ) : Active {
        override val episodeId get() = selection.request.episode.episodeId
    }

    data class Submitting(override val requestId: Long, val target: DownloadTarget) : Active {
        override val episodeId get() = target.selection.request.episode.episodeId
    }

    data class Failed(
        override val requestId: Long,
        override val episodeId: Int,
        val cause: Throwable,
        val target: DownloadTarget? = null,
    ) : Active
}

/**
 * Owns the current add-download interaction. All transitions are serialized; slow work runs
 * outside the lock. Replacing a request cancels its query scope, and late callbacks carry an ID.
 * The submitted download itself is owned by [CreateEpisodeDownloadUseCase].
 */
class AddEpisodeDownloadSession(
    parentScope: CoroutineScope,
    private val prepare: suspend (episodeId: Int, scope: CoroutineScope) -> DownloadMediaSelection,
    private val findReusableMedia: suspend (DownloadMediaSelection) -> Media?,
    private val createDownload: suspend (DownloadTarget) -> Unit,
) : AutoCloseable {
    private val logger = logger<AddEpisodeDownloadSession>()
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<AddDownloadState>(AddDownloadState.Idle)
    val state = mutableState.asStateFlow()
    private var nextRequestId = 0L
    private var requestScope: CoroutineScope? = null
    private var action: Job? = null

    suspend fun start(episodeId: Int) = mutex.withLock { startLocked(episodeId) }

    private fun startLocked(episodeId: Int) {
        if (mutableState.value is AddDownloadState.Submitting) return
        requestScope?.cancel()
        val requestId = ++nextRequestId
        val child = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        requestScope = child
        mutableState.value = AddDownloadState.Preparing(requestId, episodeId)
        action = child.launch {
            runRequest(requestId, episodeId) {
                val selection = prepare(episodeId, child)
                val existing = findReusableMedia(selection)
                mutex.withLock {
                    if (!isCurrent(requestId)) return@withLock
                    if (existing == null) {
                        mutableState.value = AddDownloadState.ChoosingMedia(requestId, selection)
                    } else {
                        submitLocked(requestId, DownloadTarget(selection, existing))
                    }
                }
            }
        }
    }

    suspend fun selectMedia(requestId: Long, media: Media) = mutex.withLock {
        val current = mutableState.value as? AddDownloadState.ChoosingMedia ?: return@withLock
        if (current.requestId != requestId || action?.isActive == true) return@withLock
        action = checkNotNull(requestScope).launch {
            runRequest(requestId, current.episodeId) {
                current.selection.selectMedia(media)
                mutex.withLock {
                    if (!isCurrent(requestId)) return@withLock
                    submitLocked(requestId, DownloadTarget(current.selection, media))
                }
            }
        }
    }

    suspend fun cancel(requestId: Long) = mutex.withLock {
        if (!isCurrent(requestId) || mutableState.value is AddDownloadState.Submitting) return@withLock
        requestScope?.cancel()
        requestScope = null
        mutableState.value = AddDownloadState.Idle
    }

    suspend fun retry(requestId: Long) = mutex.withLock {
        val current = mutableState.value as? AddDownloadState.Failed ?: return@withLock
        if (current.requestId != requestId) return@withLock
        if (current.target != null) submitLocked(requestId, current.target)
        else startLocked(current.episodeId)
    }

    private fun submitLocked(requestId: Long, target: DownloadTarget) {
        mutableState.value = AddDownloadState.Submitting(requestId, target)
        action = checkNotNull(requestScope).launch {
            runRequest(requestId, target.selection.request.episode.episodeId, target) {
                createDownload(target)
                mutex.withLock {
                    if (isCurrent(requestId)) {
                        mutableState.value = AddDownloadState.Idle
                        requestScope?.cancel()
                        requestScope = null
                    }
                }
            }
        }
    }

    private suspend fun runRequest(
        requestId: Long,
        episodeId: Int,
        target: DownloadTarget? = null,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to add download for episode $episodeId", e)
            mutex.withLock {
                if (isCurrent(requestId)) mutableState.value = AddDownloadState.Failed(requestId, episodeId, e, target)
            }
        }
    }

    private fun isCurrent(requestId: Long) = (mutableState.value as? AddDownloadState.Active)?.requestId == requestId

    override fun close() = scope.cancel()
}
