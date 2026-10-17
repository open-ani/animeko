/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CancellationException
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger

/** 将外部链接解析为导航目标. 条目链接查不到对应正片时打开条目详情. */
class ResolveEpisodeLinkUseCase(
    private val getEpisodes: suspend (subjectId: Int) -> List<EpisodeInfo>,
    private val getSubjectId: suspend (episodeId: Int) -> Int?,
) {
    private val logger = logger<ResolveEpisodeLinkUseCase>()

    suspend operator fun invoke(link: EpisodeLink): EpisodeLinkResolution {
        try {
            val destination = when (link) {
                is EpisodeLink.Subject -> {
                    val episode = link.ep?.let { ep ->
                        getEpisodes(link.subjectId).singleOrNull { it.type == EpisodeType.MainStory && it.ep == ep }
                    }
                    EpisodeLinkDestination(link.subjectId, episode?.episodeId)
                }
                is EpisodeLink.Episode -> getSubjectId(link.episodeId)?.let {
                    EpisodeLinkDestination(it, link.episodeId)
                }
            }
            return EpisodeLinkResolution(destination, if (destination == null) LoadError.NoResults else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to resolve episode link $link" }
            return EpisodeLinkResolution(
                destination = when (link) {
                    is EpisodeLink.Subject -> EpisodeLinkDestination(link.subjectId)
                    is EpisodeLink.Episode -> null
                },
                error = LoadError.fromException(RepositoryException.wrapOrThrowCancellation(e)),
            )
        }
    }
}

/** [destination] 是可导航的目标; [error] 由调用方展示, 包括回退到条目详情时的查询错误. */
data class EpisodeLinkResolution(
    val destination: EpisodeLinkDestination?,
    val error: LoadError? = null,
)
