/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes

/**
 * 供 [MediaCacheEngine] 与 [MediaCacheStorage] 使用的统计数据.
 */
data class MediaStats(
    /**
     * 总上传量.
     *
     * 实现约束: flow 一定要有至少一个元素.
     *
     * - 若 emit [FileSize.Unspecified], 表示上传速度未知. 这只会在该缓存正在上传, 但无法知道具体速度时出现.
     * - 若 emit [FileSize.Zero], 表示上传速度真的是零.
     */
    val uploaded: FileSize,
    /**
     * 总下载量.
     *
     * 实现约束: flow 一定要有至少一个元素.
     */
    val downloaded: FileSize,
    /**
     * 上传速度每秒. 对于不支持上传的缓存, 该值为 [FileSize.Zero].
     *
     * 实现约束: flow 一定要有至少一个元素.
     *
     * - 若 emit [FileSize.Unspecified], 表示上传速度未知. 这只会在该缓存正在上传, 但无法知道具体速度时出现.
     * - 若 emit [FileSize.Zero], 表示上传速度真的是零.
     */
    val uploadSpeed: FileSize,
    /**
     * 下载速度, 每秒. 对于不支持下载的缓存, 该值为 [FileSize.Zero]. flow 一定 emit 至少一个元素.
     *
     * - 若 emit [FileSize.Unspecified], 表示上传速度未知. 这只会在该缓存正在上传, 但无法知道具体速度时出现.
     * - 若 emit [FileSize.Zero], 表示上传速度真的是零.
     */
    val downloadSpeed: FileSize,
) {
    companion object {
        val Zero =
            MediaStats(FileSize.Zero, FileSize.Zero, FileSize.Zero, FileSize.Zero)
        val Unspecified =
            MediaStats(FileSize.Unspecified, FileSize.Unspecified, FileSize.Unspecified, FileSize.Unspecified)
    }
}

fun Iterable<Flow<MediaStats>>.sum(): Flow<MediaStats> = combine(this) { array -> array.sum() }

fun Array<MediaStats>.sum() = MediaStats(
    uploaded = sumOf { it.uploaded.inBytes }.bytes,
    downloaded = sumOf { it.downloaded.inBytes }.bytes,
    uploadSpeed = sumOf { it.uploadSpeed.inBytes }.bytes,
    downloadSpeed = sumOf { it.downloadSpeed.inBytes }.bytes,
)
