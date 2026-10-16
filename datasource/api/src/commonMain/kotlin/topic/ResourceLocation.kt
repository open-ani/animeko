/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.topic

import kotlinx.serialization.Serializable


@Serializable
sealed class ResourceLocation {
    abstract val uri: String

    /**
     * BT 磁力链, 需要使用 BT 引擎下载.
     *
     * `magnet:?xt=urn:btih:...`
     */
    @Serializable
    data class MagnetLink(override val uri: String) : ResourceLocation() {
        init {
            require(uri.startsWith("magnet:")) {
                "MagnetLink uri must start with magnet:"
            }
        }
    }

    /**
     * 需要通过 HTTP 下载的 BT 种子文件. 得到种子文件后还需要通过 BT 引擎下载.
     *
     * `https://example.com/a.torrent`.
     */
    @Serializable
    data class HttpTorrentFile(override val uri: String) : ResourceLocation() {
        init {
            require(uri.startsWith("https://") || uri.startsWith("http://")) {
                "HttpTorrentFile uri must start with http:// or https://"
            }
        }
    }

    /**
     * 流式传输视频文件, 例如 m3u8
     * `*.mkv`, `*.mp4` form `http://`, `https://`.
     */
    @Serializable
    data class HttpStreamingFile(override val uri: String) : ResourceLocation() {
        init {
            require(
                uri.startsWith("https://") ||
                        uri.startsWith("http://") ||
                        uri.startsWith("file://"),
            ) {
                "HttpStreamingFile uri must start with 'http://' or 'https://', but was $uri"
            }
        }
    }

    /**
     * 站点上的播放页面, 播放前需要解析出视频链接.
     *
     * 解析方式由 [apiParameters] 决定:
     * - `null`: 用 WebView 加载 [uri], 拦截页面发出的请求, 由 `WebVideoMatcher` 匹配出视频链接;
     * - 非 `null`: 不打开页面. 站点通过 API 按需签发有时效的视频地址 (例如几十分钟后过期), 所以查询资源时不解析,
     *   每次播放或开始下载前由查询出该资源的数据源 (实现 [me.him188.ani.datasources.api.matcher.ApiVideoResolver])
     *   向站点重新获取, [apiParameters] 是它需要的参数, 含义由数据源自己定义.
     *
     * 两种方式用同一个类而不是两个子类: 这个值会随下载记录持久化, 旧版本客户端读到不认识的子类会把整个下载列表当作损坏数据丢弃;
     * 多一个它不认识的字段则只是按 WebView 方式处理这一条.
     */
    @Serializable
    data class WebVideo(
        /**
         * Web 页面地址
         */
        override val uri: String,
        val apiParameters: Map<String, String>? = null,
    ) : ResourceLocation() {
        init {
            require(uri.startsWith("https://") || uri.startsWith("http://")) {
                "WebVideo uri must start with 'http://' or 'https://', but was $uri"
            }
        }

        /**
         * 是否由数据源通过 API 解析, 而不是用 WebView 加载页面.
         */
        val isResolvedByApi: Boolean get() = apiParameters != null
    }

    /**
     * 本地文件路径
     */
    @Serializable
    data class LocalFile(
        val filePath: String, // absolute
        /**
         * Hint to help the player to determine the file type.
         *
         * `null` for unknown.
         */
        val fileType: FileType? = null,
        /**
         * m3u8 原始地址.
         */
        val originalUri: String? = null,
    ) : ResourceLocation() {
        /**
         * `file://`
         */
        override val uri: String by lazy {
            "file://${filePath}"
        }

        @Serializable
        enum class FileType {
            /**
             *  MPEG Transport Stream
             */
            MPTS,

            /**
             * Contained in a container format, such as MKV, MP4, etc.
             */
            CONTAINED,
        }
    }
}

@Suppress("HttpUrlsUsage")
fun ResourceLocation.Companion.guessTorrentFromUrl(uri: String): ResourceLocation? {
    val isHttp = uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)
    return when {
        uri.startsWith("magnet:") -> ResourceLocation.MagnetLink(uri)
        isHttp && (uri.endsWith(".torrent") || uri.contains("uploadbt.com")) -> ResourceLocation.HttpTorrentFile(uri)
        else -> null
    }
}
