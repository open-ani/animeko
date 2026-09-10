/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.collections.immutable.toPersistentList
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata

/** A frozen per-episode command. It owns no query, selector, flow, or coroutine. */
data class EpisodeDownloadSpec(
    val subject: SubjectInfo,
    val episode: EpisodeInfo,
    val media: Media,
    val metadata: MediaCacheMetadata,
) {
    init {
        require(metadata.subjectId == subject.subjectId.toString())
        require(metadata.episodeId == episode.episodeId.toString())
    }

    val key get() = DownloadSpecKey(subject.subjectId, episode.episodeId, media.mediaId)
}

data class DownloadSpecKey(val subjectId: Int, val episodeId: Int, val mediaId: String)

class DownloadPlan(items: Collection<EpisodeDownloadSpec>) {
    val items = items.toPersistentList()

    init {
        require(this.items.isNotEmpty()) { "A download plan must contain at least one episode" }
        require(this.items.map { it.key }.distinct().size == this.items.size) { "Duplicate download targets" }
    }
}

sealed interface DownloadSubmissionOutcome {
    data class Created(val downloadId: String) : DownloadSubmissionOutcome
    data class AlreadyExists(val downloadId: String) : DownloadSubmissionOutcome
    data class Failed(val cause: Throwable) : DownloadSubmissionOutcome
}

data class DownloadSubmissionItem(val spec: EpisodeDownloadSpec, val outcome: DownloadSubmissionOutcome)

class DownloadSubmissionResult(items: Collection<DownloadSubmissionItem>) {
    val items = items.toPersistentList()
    val failures get() = items.filter { it.outcome is DownloadSubmissionOutcome.Failed }

    fun retryPlan(): DownloadPlan? = failures.takeIf { it.isNotEmpty() }?.let { DownloadPlan(it.map { it.spec }) }

    fun withRetry(result: DownloadSubmissionResult): DownloadSubmissionResult {
        val replacements = result.items.associateBy { it.spec.key }
        return DownloadSubmissionResult(items.map { replacements[it.spec.key] ?: it })
    }
}
