/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.datasources.api.source.MediaFetchRequest
import kotlin.time.Clock

/**
 * 一个 `MediaCache` 的元数据, 包含来源的条目和剧集信息.
 *
 * [MediaCacheMetadata] 通常在创建缓存时, 根据 [MediaFetchRequest] 中的条目和剧集信息创建.
 *
 * [MediaCacheMetadata] 可被持久化, 用于下次启动时恢复缓存任务. 恢复过程详见 `MediaCacheStorage`.
 *
 * 在播放时查询数据源时, [MediaCacheMetadata] 也被用于与 [MediaFetchRequest] 匹配缓存. 查询过程详见 `MediaCacheEngine`.
 */
@Serializable
data class MediaCacheMetadata(
    /**
     * @see MediaFetchRequest.subjectId
     */
    val subjectId: String,
    /**
     * @see MediaFetchRequest.episodeId
     */
    val episodeId: String,
    /**
     * 在创建缓存时的条目名称, 仅应当在无法获取最新的名称时, 才使用这个
     */
    val subjectNameCN: String? = null,
    /**
     * @see MediaFetchRequest.subjectNames
     */
    val subjectNames: List<String>,
    /**
     * @see MediaFetchRequest.episodeSort
     */
    val episodeSort: EpisodeSort,
    /**
     * @see MediaFetchRequest.episodeEp
     */
    val episodeEp: EpisodeSort? = episodeSort,
    /**
     * @see MediaFetchRequest.episodeName
     */
    val episodeName: String,

    val creationTime: Long = Clock.System.now().toEpochMilliseconds(),

    /**
     * @see [CacheOnBtPlayExtension]
     */
    val autoCached: Boolean = false,

    @Transient @Suppress("unused") private val _primaryConstructorMarker: Byte = 0, // avoid compiler error
) {
    constructor(
        request: MediaFetchRequest,
        autoCached: Boolean = false
    ) : this(
        subjectId = request.subjectId,
        episodeId = request.episodeId,
        subjectNameCN = request.subjectNameCN,
        subjectNames = request.subjectNames,
        episodeSort = request.episodeSort,
        episodeEp = request.episodeEp,
        episodeName = request.episodeName,
        creationTime = Clock.System.now().toEpochMilliseconds(),
        autoCached = autoCached,
    )
}