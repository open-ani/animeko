/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.matcher

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.topic.ResourceLocation

/**
 * 能在播放前通过站点 API 重新解析视频地址的数据源, 用于 [ResourceLocation.WebVideo.isResolvedByApi] 的资源.
 *
 * 由查询出这类资源的 [MediaSource] 实现. 播放器与下载器通过 [MediaSourceApiVideoResolverLoader]
 * 找到查询出该资源的数据源实例, 每次播放或开始下载前调用 [resolveApiVideo] 取当前有效的地址.
 */
interface ApiVideoResolver {
    /**
     * 向站点重新获取 [media] 当前有效的视频地址与播放器请求它时需要的请求头.
     *
     * @return `null` 表示站点没有给出可用的视频.
     * @throws kotlinx.io.IOException 网络错误
     * @throws kotlin.coroutines.cancellation.CancellationException
     */
    suspend fun resolveApiVideo(media: Media): WebVideo?
}

/**
 * 按 [MediaSource.mediaSourceId] 找到实现了 [ApiVideoResolver] 的数据源实例. 数据源实例是动态创建的, 所以要每次查找.
 */
class MediaSourceApiVideoResolverLoader(
    private val mediaSources: Flow<List<MediaSource>>,
) {
    suspend fun loadResolver(mediaSourceId: String): ApiVideoResolver? {
        return mediaSources.first().asSequence()
            .filter { it.mediaSourceId == mediaSourceId }
            .filterIsInstance<ApiVideoResolver>()
            .firstOrNull()
    }
}
