/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

data class SubjectSeriesInfo(
//    val subjectId: Int,
//    /**
//     * 系列作品列表. 这可能会包含它自己
//     */
//    val seriesSubjects: List<SubjectCollectionInfo>,
//    /**
//     * 续集列表, 不包含自己
//     */
//    val sequelSubjects: List<SubjectCollectionInfo>,
    /**
     * 此条目是系列中的第几季. 从 1 开始计数.
     */
    val seasonSort: Int,
    /**
     * 所有续集的名称, 用于过滤掉非当前季度的条目
     */
    val sequelSubjectNames: Set<String>,
    /**
     * 系列作品名称, 不包含自己的名称
     */
    val seriesSubjectNamesWithoutSelf: Set<String>,
) {
    companion object {
        fun compute(
            requestingSubject: SubjectCollectionInfo,
            seriesSubjects: List<SubjectCollectionInfo>,
            sequelSubjects: List<SubjectCollectionInfo>,
        ): SubjectSeriesInfo {
            val sequelSubjectNames = sequelSubjects.flatMapTo(mutableSetOf()) { it.subjectInfo.allNames }.apply {
                removeAll { sequelName ->
                    // 如果续集名称存在于当前名称中, 则删除, 否则可能导致过滤掉当前季度的条目
                    requestingSubject.subjectInfo.allNames.any { it.contains(sequelName, ignoreCase = true) }
                }
            }
            val seriesSubjectNamesWithoutSelf: Set<String> =
                seriesSubjects.flatMapTo(mutableSetOf()) { it.subjectInfo.allNames }
                    .minus(requestingSubject.subjectInfo.allNames)

            val seasonSort = seriesSubjects.indexOfFirst { it.subjectId == requestingSubject.subjectId }
                .let { if (it == -1) 1 else it + 1 }
            return SubjectSeriesInfo(
//                subjectId,
//                seriesSubjects,
//                sequelSubjects,
                seasonSort = seasonSort,
                sequelSubjectNames,
                seriesSubjectNamesWithoutSelf,
            )
        }

        val Fallback = SubjectSeriesInfo(
//            emptyList(),
//            emptyList(),
            seasonSort = 1,
            sequelSubjectNames = emptySet(),
            seriesSubjectNamesWithoutSelf = emptySet(),
        )
    }
}