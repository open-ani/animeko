/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate

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
    /**
     * 此条目是 Bangumi 拆分的一季中的一个部分时的分部信息, 否则为 `null`.
     */
    val seasonPart: SeasonPartInfo? = null,
) {
    companion object {
        /**
         * @param seriesSubjects 系列里的其他条目 (主线与续作), 用于识别分部. 缺少主线上的条目时不识别分部.
         */
        fun compute(
            requestingSubject: SubjectCollectionInfo,
            seriesSubjects: List<SubjectCollectionInfo> = emptyList(),
        ): SubjectSeriesInfo {
            val sequelSubjectNames = requestingSubject.relations.sequelSubjectNames.toMutableSet().apply {
                removeAll { sequelName ->
                    // 如果续集名称存在于当前名称中, 则删除, 否则可能导致过滤掉当前季度的条目
                    requestingSubject.subjectInfo.allNames.any {
                        MediaListFilters.specialContains(it, sequelName)
                    }
                }
            }
            val seriesSubjectNamesWithoutSelf: Set<String> =
                requestingSubject.relations.seriesMainSubjectNames.toMutableSet().apply {
                    removeAll { seriesName ->
                        requestingSubject.subjectInfo.allNames.any { subjectName ->
                            MediaListFilters.specialEquals(subjectName, seriesName)
                        }
                    }
                }

            val seasonSort = requestingSubject.relations.seriesMainSubjectIds
                .indexOfFirst { it == requestingSubject.subjectId }
                .let { if (it == -1) 1 else it + 1 }
            return SubjectSeriesInfo(
//                subjectId,
//                seriesSubjects,
//                sequelSubjects,
                seasonSort = seasonSort,
                sequelSubjectNames,
                seriesSubjectNamesWithoutSelf,
                seasonPart = computeSeasonPart(requestingSubject, seriesSubjects),
            )
        }

        private fun computeSeasonPart(
            requestingSubject: SubjectCollectionInfo,
            seriesSubjects: List<SubjectCollectionInfo>,
        ): SeasonPartInfo? {
            val byId = (seriesSubjects + requestingSubject).associateBy { it.subjectId }
            val today = PackedDate.now()
            val mainline = requestingSubject.relations.seriesMainSubjectIds.map { id ->
                val subject = byId[id] ?: return null
                val mainEpisodes = subject.episodes.filter { it.episodeInfo.type == EpisodeType.MainStory }
                SeriesMainSubject(
                    subjectId = id,
                    names = subject.subjectInfo.allNames,
                    mainEpisodeCount = mainEpisodes.size,
                    airedEpisodeCount = mainEpisodes.count { !it.episodeInfo.airDate.isValid || it.episodeInfo.airDate <= today },
                )
            }
            return SeasonPartInfo.compute(mainline, requestingSubject.subjectId)
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

fun SubjectSeriesInfo.toBuilder() = SubjectSeriesInfoBuilder(seasonSort).apply {
    sequel(*sequelSubjectNames.toTypedArray())
    series(*seriesSubjectNamesWithoutSelf.toTypedArray())
    seasonPart = this@toBuilder.seasonPart
}

// mainly designed for tests
class SubjectSeriesInfoBuilder(
    /**
     * 此条目是系列中的第几季
     * @see SubjectSeriesInfo.seasonSort
     */
    var seasonSort: Int,
) {
    private val sequelNames = mutableListOf<String>()
    private val seriesNames = mutableListOf<String>()

    /**
     * @see SubjectSeriesInfo.seasonPart
     */
    var seasonPart: SeasonPartInfo? = null

    /**
     * 添加一个续集番名.
     * @see SubjectSeriesInfo.sequelSubjectNames
     */
    fun sequel(vararg subjectName: String) {
        sequelNames.addAll(subjectName)
        seriesNames.addAll(subjectName)
    }

    /**
     * 添加一个同系列的番名
     * @see SubjectSeriesInfo.seriesSubjectNamesWithoutSelf
     */
    fun series(vararg subjectName: String) {
        seriesNames.addAll(subjectName)
    }

    fun build() = SubjectSeriesInfo(
        seasonSort,
        sequelSubjectNames = sequelNames.toSet(),
        seriesSubjectNamesWithoutSelf = seriesNames.toSet(),
        seasonPart = seasonPart,
    )
}

inline fun buildSubjectSeriesInfo(
    seasonSort: Int,
    block: SubjectSeriesInfoBuilder.() -> Unit
) =
    SubjectSeriesInfoBuilder(seasonSort).apply(block).build()
