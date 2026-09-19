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
    /** 条目原名 (通常为日文), 供"显示原名"设置开启时使用. */
    val name: String,
    val imageLarge: String,
) : RecommendedItemInfo()

/**
 * 根据用户偏好选择的显示名称, 与 [me.him188.ani.app.data.models.subject.preferredDisplayName] 同一约定.
 */
fun RecommendedSubjectInfo.preferredDisplayName(useOriginalTitle: Boolean): String =
    if (useOriginalTitle) name.ifBlank { nameCn } else nameCn.ifBlank { name }

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
            name = it.subjectInfo.name,
            imageLarge = it.subjectInfo.imageLarge,
        )
    }
