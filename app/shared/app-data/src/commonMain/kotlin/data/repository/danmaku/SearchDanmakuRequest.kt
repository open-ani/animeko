/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.danmaku

import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// Stable equals is required by DanmakuLoaderImpl
data class SearchDanmakuRequest(
    val subjectInfo: SubjectInfo,
    val episodeInfo: EpisodeInfo,
    val episodeId: Int,
    val filename: String? = null,
    val fileLength: Long? = null,
    val fileHash: String? = "aa".repeat(16),
    val videoDuration: Duration = 0.milliseconds,
)