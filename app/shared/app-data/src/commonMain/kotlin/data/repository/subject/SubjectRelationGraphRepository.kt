/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.data.models.subject.SubjectRelationGraph
import me.him188.ani.app.data.models.subject.SubjectRelationGraphBranch
import me.him188.ani.app.data.models.subject.SubjectRelationGraphMainNode
import me.him188.ani.app.data.models.subject.SubjectRelationGraphPlatform
import me.him188.ani.app.data.models.subject.SubjectRelationGraphSubject
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionDao
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.models.AniSubjectRelationGraph
import me.him188.ani.client.models.AniSubjectRelationGraphNode
import me.him188.ani.client.models.AniSubjectRelationGraphNodeRole
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.platform.collections.mapToIntArray
import kotlin.coroutines.CoroutineContext

class SubjectRelationGraphRepository(
    private val subjectApi: ApiInvoker<SubjectsAniApi>,
    private val subjectCollectionDao: SubjectCollectionDao,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {
    /**
     * 获取 [subjectId] 所在系列的关系图. 图的结构由服务器计算, 只请求一次; 收藏状态来自本地缓存, 会随收藏变化更新.
     */
    fun subjectRelationGraphFlow(subjectId: Int): Flow<SubjectRelationGraph> = flow {
        val graph = try {
            subjectApi { getSubjectRelationGraph(subjectId.toLong()).body() }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
        emitAll(
            subjectCollectionDao.filterByIds(graph.nodes.mapToIntArray { it.id.toInt() }).map { collections ->
                graph.toSubjectRelationGraph(
                    collections.associate { it.subjectId to it.collectionType },
                )
            },
        )
    }.flowOn(defaultDispatcher)
}

internal fun AniSubjectRelationGraph.toSubjectRelationGraph(
    collectionTypes: Map<Int, UnifiedCollectionType>,
): SubjectRelationGraph {
    fun AniSubjectRelationGraphNode.toSubject() = SubjectRelationGraphSubject(
        subjectId = id.toInt(),
        name = name,
        nameCn = nameCn,
        image = imageLarge,
        airDate = if (airDate.isEmpty()) PackedDate.Invalid else PackedDate.parseFromDate(airDate),
        platform = when (platform) {
            1 -> SubjectRelationGraphPlatform.TV
            2 -> SubjectRelationGraphPlatform.OVA
            3 -> SubjectRelationGraphPlatform.MOVIE
            5 -> SubjectRelationGraphPlatform.WEB
            else -> null
        },
        episodeCount = episodeCount,
        collectionType = collectionTypes[id.toInt()] ?: UnifiedCollectionType.NOT_COLLECTED,
    )

    val nodesById = nodes.associateBy { it.id }
    val mainNodes = mainline.mapNotNull { nodesById[it] }
    val firstMajorIndex = mainNodes.indexOfFirst { it.role == AniSubjectRelationGraphNodeRole.MAIN }

    // 主线上只显示正片, 以及第一部正片之后的剧场版 (剧场版形式的总集篇除外).
    // 其他次要条目 (特别篇, 总集篇, 短篇, 以及排在第一部正片之前的条目) 列在它之前最近的正片下.
    fun isDisplayed(index: Int, node: AniSubjectRelationGraphNode): Boolean = when {
        firstMajorIndex == -1 -> true
        node.role == AniSubjectRelationGraphNodeRole.MAIN -> true
        else -> index > firstMajorIndex && node.platform == PLATFORM_MOVIE && !node.compilation
    }

    /** 未显示在主线上的条目 -> 它所属的正片 */
    val ownerOf = mutableMapOf<Long, Long>()
    val foldedUnder = mutableMapOf<Long, MutableList<SubjectRelationGraphBranch>>()
    var lastMajor: AniSubjectRelationGraphNode? = mainNodes.getOrNull(firstMajorIndex)
    mainNodes.forEachIndexed { index, node ->
        if (node.role == AniSubjectRelationGraphNodeRole.MAIN) lastMajor = node
        if (isDisplayed(index, node)) return@forEachIndexed
        val owner = lastMajor ?: return@forEachIndexed
        ownerOf[node.id] = owner.id
        foldedUnder.getOrPut(owner.id) { mutableListOf() }.add(
            SubjectRelationGraphBranch(
                node.toSubject(),
                when {
                    node.compilation -> SubjectRelation.COMPILATION
                    index < firstMajorIndex -> SubjectRelation.PREQUEL
                    else -> SubjectRelation.SEQUEL
                },
            ),
        )
    }

    // 服务器保证 nodes 中的分支已按挂载点和放送日期排序
    val sideNodes = nodes.filter { it.role == AniSubjectRelationGraphNodeRole.SIDE }
        .groupBy { node -> node.attachTo?.let { ownerOf[it] ?: it } }

    return SubjectRelationGraph(
        subjectId = subjectId.toInt(),
        mainline = mainNodes.filterIndexed { index, node -> isDisplayed(index, node) }.map { node ->
            SubjectRelationGraphMainNode(
                subject = node.toSubject(),
                isMovie = node.role != AniSubjectRelationGraphNodeRole.MAIN,
                branches = foldedUnder[node.id].orEmpty() + sideNodes[node.id].orEmpty().map { branch ->
                    SubjectRelationGraphBranch(
                        subject = branch.toSubject(),
                        relation = when (branch.relation) {
                            4 -> SubjectRelation.COMPILATION
                            6 -> SubjectRelation.SPECIAL
                            11 -> SubjectRelation.DERIVED
                            12 -> SubjectRelation.MAIN_STORY
                            else -> null
                        },
                    )
                },
            )
        },
        truncated = truncated,
    )
}

private const val PLATFORM_MOVIE = 3
