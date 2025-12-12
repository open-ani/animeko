/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
data class MediaCacheSettings(
    val enabled: Boolean = false,
    val maxCountPerSubject: Int = 1,

    val mostRecentOnly: Boolean = false,
    val mostRecentCount: Int = 8,

    /**
     * Use system default if `null`.
     * @since 3.4.0
     */
    val saveDir: String? = null,

    /**
     * 弹幕缓存策略
     * @since 5.3.0
     */
    val danmakuCacheStrategy: DanmakuCacheStrategy = DanmakuCacheStrategy.CACHE_ON_MEDIA_CACHE,

    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {
        val Default = MediaCacheSettings()
    }
}

/**
 * 弹幕缓存策略
 */
enum class DanmakuCacheStrategy {
    /**
     * 不缓存弹幕, 每次进入播放页都从网络获取弹幕
     */
    DON_NOT_CACHE,

    /**
     * 播放正在追番的剧集时缓存, 已看完或者取消追番删除缓存
     */
    CACHE_ON_COLLECTION_DOING_MEDIA_PLAY,

    /**
     * 在缓存媒体时缓存弹幕, 删除媒体缓存时一并删除弹幕缓存
     */
    CACHE_ON_MEDIA_CACHE
}