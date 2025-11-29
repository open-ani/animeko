/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api.provider

import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import kotlin.jvm.JvmInline
import kotlin.time.Duration

/**
 * 弹幕查询器
 *
 * @see SimpleDanmakuProvider
 * @see MatchingDanmakuProvider
 */
sealed interface DanmakuProvider {
    val providerId: DanmakuProviderId

    /**
     * 主要的 [DanmakuServiceId].
     *
     * 一个 [DanmakuProvider] 的查询可以返回多个 service id 的结果,
     * 但是当该 [DanmakuProvider] 查询没有任何结果时, UI 必须显示该来源无结果, 届时会使用此 id.
     */
    val mainServiceId: DanmakuServiceId

    suspend fun fetchAutomatic(
        request: DanmakuFetchRequest,
    ): List<DanmakuFetchResult>
}

@JvmInline
value class DanmakuProviderId(
    val id: String,
) {
    companion object {
        val Animeko = DanmakuProviderId("animeko")
        val Dandanplay = DanmakuProviderId("dandanplay")
        val Local = DanmakuProviderId("local")
    }
}


class DanmakuFetchRequest(
    val subjectId: Int,
    val subjectPrimaryName: String,
    val subjectNames: List<String>,
    /**
     * Cane be [me.him188.ani.datasources.api.PackedDate.Companion.Invalid]
     */
    val subjectPublishDate: PackedDate,
    val episodeId: Int,
    val episodeSort: EpisodeSort,
    val episodeEp: EpisodeSort?,
    val episodeName: String,

    val filename: String?,
    val fileHash: String?,
    val fileSize: Long?,
    val videoDuration: Duration,
)
