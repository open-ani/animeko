/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate

data class LightSubjectAndEpisodes(
    val subject: LightSubjectInfo,
    val episodes: List<LightEpisodeInfo>,
) {
    val subjectId get() = subject.subjectId
}

data class LightSubjectInfo(
    val subjectId: Int,
    val name: String,
    val nameCn: String,
    val imageLarge: String,
)

val LightSubjectInfo.displayName get() = nameCn.takeIf { it.isNotBlank() } ?: name

data class LightEpisodeInfo(
    val episodeId: Int,
    val name: String,
    val nameCn: String,
    val airDate: PackedDate,
    val sort: EpisodeSort,
    val ep: EpisodeSort?,
)

val LightEpisodeInfo.displayName get() = nameCn.takeIf { it.isNotBlank() } ?: name
