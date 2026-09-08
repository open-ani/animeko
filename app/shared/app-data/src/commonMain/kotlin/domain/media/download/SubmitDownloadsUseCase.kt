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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.cache.MediaCache

/**
 * Accepts immutable plans synchronously. One application-owned worker serializes creation,
 * including duplicate checks across overlapping plans. Cancelling an observer cannot cancel a plan.
 */
class SubmitDownloadsUseCase(
    private val downloadManager: MediaDownloadManager,
    private val createDownload: suspend (EpisodeDownloadSpec) -> MediaCache,
) {
    private class Submission(val plan: DownloadPlan, val result: CompletableDeferred<DownloadSubmissionResult>)

    private val submissions = Channel<Submission>(
        Channel.UNLIMITED,
        onUndeliveredElement = { it.result.cancel() },
    )

    init {
        downloadManager.backgroundScope.launch {
            for (submission in submissions) {
                try {
                    submission.result.complete(execute(submission.plan))
                } catch (e: Throwable) {
                    submission.result.completeExceptionally(e)
                    throw e
                }
            }
        }.invokeOnCompletion { submissions.cancel() }
    }

    fun submit(plan: DownloadPlan): Deferred<DownloadSubmissionResult> {
        val result = CompletableDeferred<DownloadSubmissionResult>()
        if (submissions.trySend(Submission(plan, result)).isFailure) {
            result.cancel(CancellationException("Download submissions have stopped"))
        }
        return result
    }

    private suspend fun execute(plan: DownloadPlan): DownloadSubmissionResult {
        val results = plan.items.map { spec ->
            currentCoroutineContext().ensureActive()
            val outcome = try {
                val existing = downloadManager.findFirstDownload {
                    it.metadata.subjectId == spec.metadata.subjectId &&
                            it.metadata.episodeId == spec.metadata.episodeId &&
                            it.origin.mediaId == spec.media.mediaId
                }
                if (existing == null) {
                    DownloadSubmissionOutcome.Created(createDownload(spec).cacheId)
                } else {
                    DownloadSubmissionOutcome.AlreadyExists(existing.cacheId)
                }
            } catch (e: Exception) {
                // A cancelled download operation is a per-item failure unless the application is stopping.
                currentCoroutineContext().ensureActive()
                DownloadSubmissionOutcome.Failed(e)
            }
            DownloadSubmissionItem(spec, outcome)
        }
        return DownloadSubmissionResult(results)
    }
}
