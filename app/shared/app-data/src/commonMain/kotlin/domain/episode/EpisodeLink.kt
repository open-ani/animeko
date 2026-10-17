/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import io.ktor.http.Url
import me.him188.ani.datasources.api.EpisodeSort

/** 外部链接中的 Bangumi 条目或剧集定位信息. */
sealed interface EpisodeLink {
    data class Subject(val subjectId: Int, val ep: EpisodeSort? = null) : EpisodeLink
    data class Episode(val episodeId: Int) : EpisodeLink

    companion object {
        fun parse(value: String): EpisodeLink? {
            val url = runCatching { Url(value) }.getOrNull() ?: return null
            if (url.protocol.name != "ani") return null
            val path = Regex("/([0-9]+)/?").matchEntire(url.encodedPath) ?: return null
            val id = path.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            return when (url.host) {
                "subjects" -> {
                    val ep = url.parameters["ep"]?.takeIf { Regex("[0-9]+(?:\\.[0-9]+)?").matches(it) }
                        ?.let { EpisodeSort(it) }?.takeIf { it is EpisodeSort.Normal && it.number > 0 }
                    Subject(id, ep)
                }
                "episodes" -> Episode(id)
                else -> null
            }
        }
    }
}

/** [episodeId] 为空时打开条目详情, 否则打开剧集播放页. */
data class EpisodeLinkDestination(val subjectId: Int, val episodeId: Int? = null)
