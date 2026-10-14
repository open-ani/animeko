/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.io.IOException
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.matcher.MediaSourceApiVideoResolverLoader
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.cancellation.CancellationException

/**
 * 解析由数据源 API 签发地址的网页视频 ([ResourceLocation.WebVideo.isResolvedByApi]):
 * 找到查询出该资源的数据源, 让它向站点重新获取当前有效的视频地址. 不打开页面, 所以各平台共用.
 *
 * 每次播放与每次开始下载都重新解析, 不缓存结果: 这类地址有时效, 缓存反而会播到过期地址.
 * 数据源不存在 (已被删除) 时无法解析, 换任何同源资源都会同样失败.
 */
class ApiVideoMediaResolver(
    private val resolverLoader: MediaSourceApiVideoResolverLoader,
) : MediaResolver {
    private companion object {
        private val logger = logger<ApiVideoMediaResolver>()
    }

    override fun supports(media: Media): Boolean =
        (media.download as? ResourceLocation.WebVideo)?.isResolvedByApi == true

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        if (!supports(media)) throw UnsupportedMediaException(media)

        val resolver = resolverLoader.loadResolver(media.mediaSourceId)
            ?: throw MediaResolutionException(
                ResolutionFailures.ENGINE_ERROR,
                IllegalStateException("MediaSource '${media.mediaSourceId}' is not available or cannot resolve ApiVideo"),
            )

        val video = try {
            resolver.resolveApiVideo(media)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MediaResolutionException) {
            throw e
        } catch (e: RepositoryException) {
            throw MediaResolutionException(e.toResolutionFailure(), e)
        } catch (e: IOException) {
            throw MediaResolutionException(ResolutionFailures.NETWORK_ERROR, e)
        } catch (e: Exception) {
            throw MediaResolutionException(ResolutionFailures.ENGINE_ERROR, e)
        } ?: throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)

        logger.info { "Resolved ApiVideo of ${media.mediaId} to ${video.m3u8Url}" }
        return HttpStreamingMediaDataProvider(
            video.m3u8Url,
            media.originalTitle,
            video.headers,
            media.extraFiles.toMediampMediaExtraFiles(),
        )
    }

    private fun RepositoryException.toResolutionFailure(): ResolutionFailures = when (this) {
        is RepositoryNetworkException,
        is RepositoryServiceUnavailableException,
        is RepositoryRateLimitedException,
            -> ResolutionFailures.NETWORK_ERROR

        is RepositoryAuthorizationException,
        is RepositoryRequestError,
        is RepositoryUnknownException,
            -> ResolutionFailures.ENGINE_ERROR
    }
}
