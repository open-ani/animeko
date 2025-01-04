/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import androidx.compose.runtime.Composable
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import org.openani.mediamp.source.MediaData
import kotlin.coroutines.cancellation.CancellationException

/**
 * 根据 [EpisodeMetadata] 中的集数信息和 [Media.location] 中的下载方式,
 * 解析一个 [Media] 为可以播放的 [MediaData].
 *
 * 实际操作可涉及创建种子下载任务, 寻找文件等.
 *
 * 由于 [Media] 可能包含多个剧集视频, 在[解析][resolve]时需要提供所需剧集的信息 [EpisodeMetadata].
 */
interface MediaResolver {
    /**
     * 判断是否支持解析这个 [Media] 的 [Media.download].
     *
     * 当且仅当返回 `true` 时, 才可以调用 [resolve] 方法.
     */
    suspend fun supports(media: Media): Boolean

    /**
     * "挂载" 到 composable 中, 以便进行需要虚拟 UI 的操作, 例如 WebView
     */
    @Composable
    fun ComposeContent() {
    }

    /**
     * 根据 [EpisodeMetadata] 中的集数信息和 [Media.location] 中的下载方式,
     * 解析一个 [Media] 为 [MediaDataProvider].
     *
     * @param episode Target episode to resolve, because a media can have multiple episodes.
     *
     * @throws UnsupportedMediaException if the media cannot be resolved.
     * Use [supports] to check if the media can be resolved.
     * @throws VideoSourceResolutionException 当遇到已知原因的解析错误时抛出
     * @throws CancellationException 当协程被取消时抛出
     * @throws Exception 所有抛出的其他异常都属于 bug
     */
    @Throws(VideoSourceResolutionException::class, CancellationException::class)
    suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*>

    companion object {
        fun from(vararg resolvers: MediaResolver): MediaResolver {
            return ChainedMediaResolver(resolvers.toList())
        }

        fun from(resolvers: Iterable<MediaResolver>): MediaResolver {
            return ChainedMediaResolver(resolvers.toList())
        }
    }
}

/**
 * @see MediaResolver.resolve
 */
data class EpisodeMetadata(
    val title: String,
    val ep: EpisodeSort?,
    val sort: EpisodeSort,
)

fun EpisodeInfo.toEpisodeMetadata(): EpisodeMetadata {
    return EpisodeMetadata(nameCn, ep, sort)
}

class UnsupportedMediaException(
    val media: Media,
) : UnsupportedOperationException("Media is not supported: $media")

/**
 * 已知的解析错误
 * @see VideoSourceResolutionException
 */
enum class ResolutionFailures {
    /**
     * 下载种子文件超时或者解析失败
     */
    FETCH_TIMEOUT,

    NETWORK_ERROR,

    /**
     * Web 没有匹配到资源
     */
    NO_MATCHING_RESOURCE,

    /**
     * 引擎自身错误 (bug)
     */
    ENGINE_ERROR,
}

/**
 * 解析资源失败时抛出的异常.
 * @see MediaResolver.resolve
 */
class VideoSourceResolutionException(
    val reason: ResolutionFailures,
    override val cause: Throwable? = null,
) : Exception("Failed to resolve video source: $reason", cause)


/**
 * 用于将多个 [MediaResolver] 链接在一起的 [MediaResolver].
 */
private class ChainedMediaResolver(
    private val resolvers: List<MediaResolver>
) : MediaResolver {
    override suspend fun supports(media: Media): Boolean {
        return resolvers.any { it.supports(media) }
    }

    @Composable
    override fun ComposeContent() {
        this.resolvers.forEach {
            it.ComposeContent()
        }
    }

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        return resolvers.firstOrNull { it.supports(media) }?.resolve(media, episode)
            ?: throw UnsupportedMediaException(media)
    }
}
