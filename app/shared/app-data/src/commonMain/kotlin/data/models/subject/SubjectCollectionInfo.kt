/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.repository.EpisodeCollectionInfo
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/**
 * 用户对一个条目的收藏情况
 */
@Immutable
data class SubjectCollectionInfo(
    val collectionType: UnifiedCollectionType,
    val subjectInfo: SubjectInfo,
    val selfRatingInfo: SelfRatingInfo,
    val episodes: List<EpisodeCollectionInfo>,
    val airingInfo: SubjectAiringInfo,
    val progressInfo: SubjectProgressInfo,
) {
    val subjectId: Int get() = subjectInfo.subjectId
}