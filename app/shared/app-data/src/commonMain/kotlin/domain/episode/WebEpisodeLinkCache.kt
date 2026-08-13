/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.him188.ani.app.domain.mediasource.web.WebSearchEpisodeInfo
import me.him188.ani.app.domain.mediasource.web.findMatchingEpisodeOrNull
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.topic.ResourceLocation

/**
 * 播放页内跨 episode 共享的 Web 源剧集链接缓存.
 *
 * 当用户 (或自动选择) 选中一个 Web 源线路后, 该线路上搜索到的所有剧集链接会被记录到这里.
 * 切换 episode 时, 若缓存中已有目标剧集的链接, 则直接用它合成 [DefaultMedia] 进入
 * 「匹配视频」阶段, 跳过「搜索条目 - 搜索剧集」两步的等待.
 *
 * 生命周期与 [EpisodeFetchSelectPlayState] 相同 (即播放页), 随播放页销毁.
 */
class WebEpisodeLinkCache {
    data class CachedWebChannel(
        /**
         * 选中的线路聚合 media, 作为合成新 media 的模板.
         */
        val media: DefaultMedia,
        /**
         * 该线路上搜索到的所有剧集.
         */
        val episodes: List<WebSearchEpisodeInfo>,
    )

    private val _state = MutableStateFlow<CachedWebChannel?>(null)
    val state: StateFlow<CachedWebChannel?> = _state.asStateFlow()

    fun update(media: DefaultMedia, episodes: List<WebSearchEpisodeInfo>) {
        _state.value = CachedWebChannel(media, episodes)
    }

    fun invalidate() {
        _state.value = null
    }

    /**
     * 若缓存的线路上有匹配目标剧集的链接, 以缓存的 media 为模板合成一个指向该剧集播放页的 [DefaultMedia].
     */
    fun createMediaFor(
        episodeSort: EpisodeSort,
        episodeEp: EpisodeSort?,
        episodeName: String?,
    ): DefaultMedia? {
        val cached = _state.value ?: return null
        val episode = cached.episodes.findMatchingEpisodeOrNull(episodeSort, episodeEp, episodeName)
            ?: return null
        val template = cached.media
        return DefaultMedia(
            mediaId = template.mediaId,
            mediaSourceId = template.mediaSourceId,
            originalUrl = episode.playUrl,
            download = ResourceLocation.WebVideo(episode.playUrl),
            originalTitle = template.originalTitle,
            publishedTime = template.publishedTime,
            properties = template.properties.run {
                MediaProperties(
                    subjectName = subjectName,
                    episodeName = episode.name,
                    subtitleLanguageIds = subtitleLanguageIds,
                    resolution = resolution,
                    alliance = alliance,
                    size = size,
                    subtitleKind = subtitleKind,
                )
            },
            episodeRange = template.episodeRange,
            extraFiles = template.extraFiles,
            location = template.location,
            kind = template.kind,
        )
    }
}
