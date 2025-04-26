/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.utils.platform.annotations.SerializationOnly
import kotlin.jvm.JvmInline

/**
 * 一个 `MediaCache` 的元数据, 包含来源的条目和剧集信息, 以及 `MediaCacheEngine` 自行添加的额外信息 [extra].
 *
 * [MediaCacheMetadata] 通常在创建缓存时, 根据 [MediaFetchRequest] 中的条目和剧集信息创建.
 *
 * [MediaCacheMetadata] 可被持久化, 用于下次启动时恢复缓存任务. 恢复过程详见 `MediaCacheStorage`.
 *
 * 在播放时查询数据源时, [MediaCacheMetadata] 也被用于与 [MediaFetchRequest] 匹配缓存. 查询过程详见 `MediaCacheEngine`.
 */
@Serializable
data class MediaCacheMetadata
/**
 * This constructor is only for serialization
 */
@SerializationOnly
constructor(
    /**
     * @see MediaFetchRequest.subjectId
     */
    @SerialName("subjectId")
    private val _subjectId: String? = null,
    /**
     * @see MediaFetchRequest.episodeId
     */
    @SerialName("episodeId")
    private val _episodeId: String? = null,
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
    /**
     * `MediaCacheEngine` 自行添加的额外信息. 例如来源 BT 磁力链.
     */
    val extra: Map<MetadataKey, String> = emptyMap(),
    @Transient @Suppress("unused") private val _primaryConstructorMarker: Byte = 0, // avoid compiler error
) {
    val subjectId get() = _subjectId ?: "0" // 为了兼容旧版 (< 3.8) 的缓存, 但实际上这些缓存也并不会是 null
    val episodeId get() = _episodeId ?: "0"

    @OptIn(SerializationOnly::class)
    constructor(
//    /**
//     * Id of the [MediaSource] that cached this media.
//     */
//    val cacheMediaSourceId: String, // e.g. "localfs" for the local file system
        /**
         * @see MediaFetchRequest.subjectId
         */
        subjectId: String,
        /**
         * @see MediaFetchRequest.episodeId
         */
        episodeId: String,
        subjectNameCN: String?,
        subjectNames: List<String>,
        episodeSort: EpisodeSort,
        episodeEp: EpisodeSort?,
        episodeName: String,
        extra: Map<MetadataKey, String> = emptyMap(),
    ) : this(
        subjectId, episodeId, subjectNameCN, subjectNames, episodeSort, episodeEp, episodeName, extra,
        _primaryConstructorMarker = 0,
    )

    /**
     * Appends [other] to the existing [extra].
     */
    fun withExtra(other: Map<MetadataKey, String>): MediaCacheMetadata {
        return MediaCacheMetadata(
            subjectId = subjectId,
            episodeId = episodeId,
            subjectNameCN = subjectNameCN,
            subjectNames = subjectNames,
            episodeSort = episodeSort,
            episodeEp = episodeEp,
            episodeName = episodeName,
            extra = extra + other,
        )
    }

    companion object {
        /**
         * 该缓存的创建时间. 重启 APP 之后不会变化. 每个缓存都应当有这个属性.
         * @since 3.8
         */
        val KEY_CREATION_TIME = MetadataKey("creationTime")
    }
}

/**
 * 旧缓存没有这个
 */
val MediaCacheMetadata.creationTimeOrNull: Long?
    get() = extra[MediaCacheMetadata.KEY_CREATION_TIME]?.toLongOrNull()

@Serializable
@JvmInline
value class MetadataKey(val key: String)

val MediaCacheMetadata.subjectIdInt: Int
    get() = subjectId.toIntOrNull() ?: error("subjectId is not int: $subjectId")

val MediaCacheMetadata.episodeIdInt: Int
    get() = episodeId.toIntOrNull() ?: error("episodeId is not int: $episodeId")

fun MediaCacheMetadata(
    request: MediaFetchRequest,
    extra: Map<MetadataKey, String> = emptyMap(),
): MediaCacheMetadata {
    return MediaCacheMetadata(
        subjectId = request.subjectId,
        episodeId = request.episodeId,
        subjectNameCN = request.subjectNameCN,
        subjectNames = request.subjectNames,
        episodeSort = request.episodeSort,
        episodeEp = request.episodeEp,
        episodeName = request.episodeName,
        extra = extra,
    )
}
