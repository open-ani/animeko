/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.analytics

import me.him188.ani.app.domain.media.DroppedFileMedia
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.selector.MatchMetadata.SubjectMatchKind
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.SelectTrigger
import me.him188.ani.app.domain.mediasource.web.normalizedSessionHost
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.isLocalCache
import me.him188.ani.datasources.api.source.MediaSourceKind
import org.openani.mediamp.PlaybackException

/**
 * `play_flow` 事件里资源与数据源的 `kind` 取值.
 */
internal fun MediaSourceKind.toPlayFlowKind(): String = when (this) {
    MediaSourceKind.WEB -> "web"
    MediaSourceKind.BitTorrent -> "bt"
    MediaSourceKind.LocalCache -> "local_cache"
}

internal fun Media.toPlayFlowKind(): String = when {
    DroppedFileMedia.isDroppedFile(this) -> "dropped_file"
    isLocalCache() -> "local_cache"
    else -> kind.toPlayFlowKind()
}

/**
 * `user_select.entry` 取值. 自动选择的入口不在此列, 它们由 `auto_select_done` 与 `auto_switch` 覆盖.
 */
internal fun SelectTrigger.toPlayFlowEntry(): String = when (this) {
    SelectTrigger.MANUAL_AUTO_PAGE -> "auto_page"
    SelectTrigger.MANUAL_BT_PAGE -> "bt_page"
    SelectTrigger.MANUAL_BROWSE -> "manual_search"
    SelectTrigger.MANUAL_TV -> "tv"
    SelectTrigger.DROPPED_FILE -> "dropped_file"
    SelectTrigger.MANUAL_OTHER -> "other"
    SelectTrigger.AUTO_STARTUP, SelectTrigger.AUTO_ERROR_SWITCH, SelectTrigger.AUTO_OTHER -> "auto"
    SelectTrigger.BROWSE_MEMORY_REPLAY -> "browse_memory"
}

internal fun SubjectMatchKind.toPlayFlowMatch(): String = when (this) {
    SubjectMatchKind.EXACT -> "exact"
    SubjectMatchKind.FUZZY -> "fuzzy"
}

/**
 * 资源的有效阶级. 本地缓存与拖入的文件没有阶级.
 */
internal fun MediaSelectorSourceTiers.tierOf(media: Media): Long? {
    if (media.isLocalCache() || DroppedFileMedia.isDroppedFile(media)) return null
    return get(media.mediaSourceId, media.properties.alliance).value.toLong()
}

/**
 * 播放器错误在 `video_load_done.reason` 与 `play_error.reason` 里的取值, 如 `player_io`.
 */
internal fun PlaybackException.toPlayFlowReason(): String = "player_" + code.name.lowercase()

/**
 * 数据源网址的主机名 (同 [normalizedSessionHost]: 小写, 去掉 `www.`), 作为 `source_failed.sources_*` 里的源标识;
 * 没有网址或解析不出主机名时为 `null`.
 */
internal fun playFlowHost(websiteUrl: String?): String? {
    if (websiteUrl.isNullOrBlank()) return null
    // 没有协议的网址会被解析成相对路径 (主机名为 localhost)
    val withScheme = if ("://" in websiteUrl) websiteUrl else "https://$websiteUrl"
    return normalizedSessionHost(withScheme)?.takeIf { it != "localhost" }
}

/**
 * 把 [tokens] 用逗号连接, 切成每段不超过 [maxLength] 字符的若干段. 单个超长的记号独占一段并被截断.
 */
internal fun packPlayFlowTokens(
    tokens: List<String>,
    maxLength: Int = PlayFlowRecorder.MAX_STRING_LENGTH,
): List<String> {
    val chunks = ArrayList<String>()
    val current = StringBuilder()
    for (token in tokens) {
        if (current.isNotEmpty() && current.length + 1 + token.length > maxLength) {
            chunks += current.toString()
            current.clear()
        }
        if (current.isNotEmpty()) current.append(',')
        current.append(token.take(maxLength))
    }
    if (current.isNotEmpty()) chunks += current.toString()
    return chunks
}

/**
 * `video_load_done.engine` 取值: 由解析器类名与种子引擎推断.
 */
internal fun playFlowEngine(resolverName: String?, engineKey: MediaCacheEngineKey?): String = when {
    resolverName == null -> "none"
    engineKey != null -> if (engineKey.isCloud) "cloud_bt" else "local_bt"
    resolverName.contains("Torrent", ignoreCase = true) -> "local_bt"
    resolverName.contains("LocalFile", ignoreCase = true) -> "local_file"
    resolverName.contains("HttpStreaming", ignoreCase = true) -> "direct"
    resolverName.contains("Web", ignoreCase = true) -> "web"
    else -> "other"
}
