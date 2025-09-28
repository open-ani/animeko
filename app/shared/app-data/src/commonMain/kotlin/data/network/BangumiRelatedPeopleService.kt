/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.models.AniRelatedSubject
import me.him188.ani.utils.ktor.ApiInvoker

class BangumiRelatedPeopleService(
    private val subjectApi: ApiInvoker<SubjectsAniApi>,
) {
    fun relatedSubjectsFlow(subjectId: Int): Flow<List<RelatedSubjectInfo>> = flow {
        val list: List<AniRelatedSubject> = subjectApi {
            getRelatedSubjects(subjectId.toLong())
                .body()
        }
        emit(
            list.map { subject ->
                RelatedSubjectInfo(
                    subjectId = subject.id.toInt(),
                    relation = when (subject.relation) {
                        2 -> SubjectRelation.PREQUEL
                        3 -> SubjectRelation.SEQUEL
                        6 -> SubjectRelation.SPECIAL
                        11 -> SubjectRelation.DERIVED
                        else -> null
                    },
                    name = subject.name,
                    nameCn = subject.nameCn,
                    image = subject.imageLarge,
                )
            }.let(RelatedSubjectInfo::sortList),
        )
    }
}
