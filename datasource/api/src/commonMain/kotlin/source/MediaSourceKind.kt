package me.him188.ani.datasources.api.source

import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation

/**
 * 数据源类型.
 *
 * 不同类型的资源在缓冲速度上可能有本质上的区别.
 */ // 在数据源选择器中, 每个数据源会以 [MediaSourceKind] 分类展示.
@Serializable
enum class MediaSourceKind {
    /**
     * 在线视频网站. 资源为 [ResourceLocation.WebVideo] 或 [ResourceLocation.HttpStreamingFile].
     *
     * 对于完结番, [WEB] 的单集资源**不会**被过滤掉.
     * @see BitTorrent
     */
    WEB,

    /**
     * P2P BitTorrent 网络. 资源为 [ResourceLocation.HttpTorrentFile] 或 [ResourceLocation.MagnetLink].
     *
     * 如果 [Media.episodeRange] 剧集为单集 [EpisodeRange.single], 且类型为 [MediaSourceKind.BitTorrent], 则很有可能会因为默认启用的"完结番隐藏单集资源"功能而被过滤掉.
     * @see WEB
     * @see Media.episodeRange
     */
    BitTorrent,

    /**
     * 本地视频下载. 只表示那些由 `MediaDownloadManager` 管理的视频.
     *
     * 该类型的资源总是会显示, 忽略一切过滤条件.
     */
    LocalCache;

    companion object {
        /**
         * 除本地缓存这种特殊类型外, 用户可以选择的数据源类型.
         */
        val selectableEntries = listOf(WEB, BitTorrent)
    }
}
