/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.recommend

import me.him188.ani.app.data.models.subject.TestFollowedSubjectInfos
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.utils.platform.annotations.TestOnly

sealed class RecommendedItemInfo

data class RecommendedSubjectInfo(
    val bangumiId: Int,
    val nameCn: String,
    val imageLarge: String,
) : RecommendedItemInfo()


val RecommendedItemInfo.id: Any
    get() = when (this) {
        is RecommendedSubjectInfo -> bangumiId
    }

val RecommendedItemInfo.type: Int
    get() = when (this) {
        is RecommendedSubjectInfo -> 0
    }

@TestOnly
val TestRecommendedItemInfos: List<RecommendedItemInfo>
    get() = TestFollowedSubjectInfos.map {
        RecommendedSubjectInfo(
            bangumiId = it.subjectInfo.subjectId,
            nameCn = it.subjectInfo.nameCn,
            imageLarge = it.subjectInfo.imageLarge,
        )
    }
