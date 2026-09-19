/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.utils.platform.Uuid


class SubjectRecommendation(
    val subjectId: Long?,
    val name: String,
    val nameCn: String?,
    val desc1: String,
    val desc2: String,
    val imageUrl: String,
    val uri: String?,
) {
    val uniqueId: String = Uuid.randomString()
}

/**
 * 根据用户偏好选择的显示名称, 与 [me.him188.ani.app.data.models.subject.preferredDisplayName] 同一约定.
 * @param useOriginalTitle 为 `true` 时优先显示原名 ([SubjectRecommendation.name]).
 */
fun SubjectRecommendation.preferredDisplayName(useOriginalTitle: Boolean): String =
    if (useOriginalTitle) {
        name.ifBlank { nameCn.orEmpty() }
    } else {
        nameCn.takeIf { !it.isNullOrBlank() } ?: name
    }

fun interface GetSubjectRecommendationUseCase : UseCase {
    suspend operator fun invoke(subjectId: Int): List<SubjectRecommendation>
}

class GetSubjectRecommendationUseCaseImpl(private val service: SubjectService) : GetSubjectRecommendationUseCase {
    override suspend fun invoke(subjectId: Int): List<SubjectRecommendation> {
        return service.getSubjectRecommendations(subjectId, 15).map {
            SubjectRecommendation(
                subjectId = it.subjectId,
                name = it.subjectName,
                nameCn = it.subjectNameCn,
                desc1 = it.desc1,
                desc2 = it.desc2,
                imageUrl = it.imageUrl,
                uri = it.uri,
            )
        }
    }
}