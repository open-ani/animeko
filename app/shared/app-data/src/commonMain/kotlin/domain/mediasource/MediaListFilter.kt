/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.EpisodeRange

/**
 * 一个通用的过滤器, 可被多个数据源实现共享.
 *
 * [MediaListFilters] 中包含了一些常用的过滤器.
 *
 * @sample me.him188.ani.app.domain.mediasource.applyingFilters
 *
 * @param Ctx 所需上下文类型.
 * 最基础的为 [MediaListFilterContext]. 一个 [MediaListFilter] 使用的上下文类型越基础, 就越通用.
 * 例如 [MediaListFilters.ContainsSubjectName] 就使用最基础的上下文, 因此可以用于所有类型数据源.
 *
 * 使用 [BasicMediaListFilter] 可简化类型声明.
 *
 * @see me.him188.ani.app.domain.mediasource.rss.DefaultRssMediaSourceEngine
 */
fun interface MediaListFilter<in Ctx : MediaListFilterContext> {
    /**
     * 一个待被过滤的物品. 通常由你需要过滤的物品转换而来.
     *
     * [asCandidate] 可将 [Media] 转换为 [Candidate].
     */
    interface Candidate {
        val originalTitle: String
        val subjectName: String get() = originalTitle
        val episodeRange: EpisodeRange?
    }

    /**
     * 过滤一个 [Candidate].
     *
     * 返回 `true` 表示该 [Candidate] 符合过滤条件.
     * 返回 `false` 表示不符合, 将会导致该 [Candidate] 被完全从结果中剔除. Media Selector 中的计数也不会包含它.
     */
    fun Ctx.applyOn(media: Candidate): Boolean
}

infix fun <C : MediaListFilterContext> MediaListFilter<C>.or(cond: MediaListFilter<C>): MediaListFilter<C> {
    val self = this
    return MediaListFilter<C> { candidate ->
        with(self) { applyOn(candidate) } || with(cond) { applyOn(candidate) }
    }
}

typealias BasicMediaListFilter = MediaListFilter<MediaListFilterContext>

// TODO: require context MediaListFilterContext to limit scope
fun Media.asCandidate(): MediaListFilter.Candidate {
    val media = this
    return object : MediaListFilter.Candidate {
        override val originalTitle: String get() = media.originalTitle
        override val episodeRange: EpisodeRange? get() = media.episodeRange
        override fun toString(): String {
            return "Candidate(media=$media)"
        }
    }
}
