/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import me.him188.ani.app.domain.mediasource.MediaListFilter.Candidate
import me.him188.ani.datasources.api.EpisodeSort

/**
 * 包含条目和剧集等查询信息, 用于将 filter 执行.
 *
 * @see MediaListFilter
 */
open class MediaListFilterContext(
    val subjectNames: Set<String>, // faster
    val episodeSort: EpisodeSort,
    val episodeEp: EpisodeSort?,
    val episodeName: String?,
) {
    val subjectNamesWithoutSpecial: Set<String> by lazy {
        subjectNames.mapTo(HashSet(subjectNames.size)) {
            MediaListFilters.removeSpecials(it, removeWhitespace = true)
        }
    }
    val episodeNameWithoutSpecial: String? by lazy {
        episodeName?.let {
            MediaListFilters.removeSpecials(it, removeWhitespace = true)
        }
    }

    /**
     * 返回 `true` 表示该 [Candidate] 符合过滤条件.
     */
    fun BasicMediaListFilter.applyOn(candidate: Candidate): Boolean =
        this@MediaListFilterContext.applyOn(candidate) // calls MediaListFilter's member function

    /**
     * 返回 `true` 表示该 [Candidate] 符合所有过滤条件.
     */
    fun Iterable<BasicMediaListFilter>.applyOn(candidate: Candidate): Boolean =
        this.all { it.applyOn(candidate) }

    /**
     * 返回 `true` 表示该 [Candidate] 符合所有过滤条件.
     */
    fun Sequence<BasicMediaListFilter>.applyOn(candidate: Candidate): Boolean =
        this.all { it.applyOn(candidate) }
}
