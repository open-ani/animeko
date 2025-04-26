/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.domain.media.selector.MediaSelectorContext.Companion.Initial
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.utils.coroutines.retryWithBackoffDelay
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

data class MediaSelectorContext(
    /**
     * 该条目已经完结了一段时间了. `null` 表示该信息还正在查询中
     */
    val subjectFinished: Boolean?,
    /**
     * 在执行自动选择时, 需要按此顺序使用数据源.
     * 为 `null` 表示无偏好, 可以按任意顺序选择.
     *
     * 当使用完所有偏好的数据源后都没有筛选到资源时, 将会 fallback 为选择任意数据源的资源
     */
    val mediaSourcePrecedence: List<String>?,
    /**
     * 用于针对各个平台的播放器缺陷，调整选择资源的优先级
     */
    val subtitlePreferences: MediaSelectorSubtitlePreferences?,
    val subjectSeriesInfo: SubjectSeriesInfo?,
    val subjectInfo: SubjectInfo?,
    val episodeInfo: EpisodeInfo?,
    val mediaSourceTiers: MediaSelectorSourceTiers?,
) {
    fun allFieldsLoaded() = subjectFinished != null
            && mediaSourcePrecedence != null
            && subtitlePreferences != null
            && subjectSeriesInfo != null
            && subjectInfo != null
            && episodeInfo != null
            && mediaSourceTiers != null

    companion object {
        /**
         * 刚开始查询时的默认值
         */
        val Initial = MediaSelectorContext(null, null, null, null, null, null, null)

        val EmptyForPreview
            get() = MediaSelectorContext(
                false,
                emptyList(),
                MediaSelectorSubtitlePreferences.AllNormal,
                SubjectSeriesInfo.Fallback,
                SubjectInfo.Empty,
                EpisodeInfo.Empty,
                mediaSourceTiers = MediaSelectorSourceTiers.Empty,
            )


        internal val logger = logger<MediaSelectorContext>()
    }
}

class MediaSelectorContextFlowProducer(
    subjectCompleted: Flow<Boolean>,
    mediaSourcePrecedence: Flow<List<String>>,
    subjectSeriesInfo: Flow<SubjectSeriesInfo>,
    subjectInfoFlow: Flow<SubjectInfo>,
    episodeInfoFlow: Flow<EpisodeInfo>,
    mediaSourceTiersFlow: Flow<MediaSelectorSourceTiers>,
    subtitleKindFilters: Flow<MediaSelectorSubtitlePreferences> = flowOf(MediaSelectorSubtitlePreferences.CurrentPlatform),
) {
    val flow = me.him188.ani.utils.coroutines.flows.combine(
        // 都 emit null, debug 时能知道是谁没 emit
        subjectCompleted.onStart<Boolean?> { emit(null) },
        mediaSourcePrecedence.onStart<List<String>?> { emit(null) },
        subtitleKindFilters.onStart<MediaSelectorSubtitlePreferences?> { emit(null) },
        subjectSeriesInfo.retryWithBackoffDelay { e, _ ->
            val wrapped = RepositoryException.wrapOrThrowCancellation(e)
            if (wrapped is RepositoryUnknownException) {
                MediaSelectorContext.Companion.logger.warn { "Failed to load related subject names due to $wrapped" }
            } else {
                MediaSelectorContext.Companion.logger.error(wrapped) { "Failed to load related subject names" }
            }
            emit(SubjectSeriesInfo.Fallback)
            true
        }.onStart<SubjectSeriesInfo?> { emit(null) },
        subjectInfoFlow.onStart<SubjectInfo?> { emit(null) },
        episodeInfoFlow.onStart<EpisodeInfo?> { emit(null) },
        mediaSourceTiersFlow.onStart<MediaSelectorSourceTiers?> { emit(null) },
    ) { completed, instances, filters, seriesInfo, subjectInfo, episodeInfo, mediaSourceTiers ->
        MediaSelectorContext(
            subjectFinished = completed,
            mediaSourcePrecedence = instances,
            subtitlePreferences = filters,
            subjectSeriesInfo = seriesInfo,
            subjectInfo = subjectInfo,
            episodeInfo = episodeInfo,
            mediaSourceTiers = mediaSourceTiers,
        )
    }.onStart {
        emit(Initial) // 否则如果一直没获取到剧集信息, 就无法选集, #385
    }
}


/**
 * 所有已知数据源的 tiers.
 *
 * @since 4.7
 */
data class MediaSelectorSourceTiers(
    /**
     * Key is [me.him188.ani.datasources.api.source.MediaSource.mediaSourceId]
     */
    val tiers: Map<String, MediaSourceTier>,
    val fallback: (mediaSourceId: String) -> MediaSourceTier = { MediaSourceTier.Fallback },
) {
    fun get(mediaSourceId: String): MediaSourceTier {
        return tiers[mediaSourceId] ?: fallback(mediaSourceId)
    }

    companion object {
        val Empty = MediaSelectorSourceTiers(emptyMap())
    }
}
