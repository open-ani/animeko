/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.client.models.AniSubjectType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.time.Duration.Companion.seconds

/** 搜索候选的输入识别、查询调度和结果合并。 */
internal object SubjectSearchCompletions {
    private val subjectIdPattern = Regex("subject/([0-9]+)")
    private val episodeIdPattern = Regex("ep/([0-9]+)")

    /** 提取首个 subject/ID，或将完整整数输入解析为正 Int 条目 ID。 */
    private fun parseSearchSubjectId(query: String): Int? {
        val value = subjectIdPattern.find(query)?.groupValues?.get(1) ?: query.trim()
        return value.toIntOrNull()?.takeIf { it > 0 }
    }

    /** 从 ep/ID 中提取首个正 Int 剧集 ID；完整整数输入属于条目 ID。 */
    private fun parseSearchEpisodeId(query: String): Int? =
        episodeIdPattern.find(query)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

    /**
     * ID 查询超过 3 秒时并行搜索关键词。普通候选先到先显示，ID 候选成功后置顶并取消尚未完成的关键词查询。
     * 查询与收集协程共享生命周期；ID 查询失败或被过滤时使用普通候选。
     */
    fun flow(
        query: String,
        nsfwMode: NsfwMode,
        excludedCollectionTypes: Set<UnifiedCollectionType>,
        getSubject: suspend (Int) -> AniSubjectCollection?,
        getEpisodeSubjectId: suspend (Int) -> Int? = { null },
        searchKeywords: suspend (String) -> List<String>,
    ): Flow<List<String>> = flow {
        coroutineScope {
            val specific = async {
                try {
                    val subjectId = parseSearchSubjectId(query)
                        ?: parseSearchEpisodeId(query)?.let { getEpisodeSubjectId(it) }
                        ?: return@async null
                    if (subjectId <= 0) return@async null
                    val subject = getSubject(subjectId) ?: return@async null
                    if (subject.collectionType.toUnifiedCollectionType() in excludedCollectionTypes) return@async null
                    if (subject.type != AniSubjectType.ANIME ||
                        (subject.nsfw && nsfwMode != NsfwMode.DISPLAY)
                    ) return@async null
                    val name = subject.nameCn.ifBlank { subject.name }
                    if (name.isBlank()) return@async null
                    listOf(name, subjectId.toString(), query).distinct()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }

            // 只限制独占等待时间，specific 的协程属于外层作用域。
            val completedEarly = withTimeoutOrNull(3.seconds) {
                specific.await()
                true
            } == true
            if (completedEarly || specific.isCompleted) {
                emit(specific.await() ?: searchKeywords(query.trim()))
                return@coroutineScope
            }

            val ordinary = async {
                try {
                    Result.success(searchKeywords(query.trim()))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            select {
                specific.onAwait { candidates ->
                    if (candidates != null) {
                        val existing = if (ordinary.isCompleted) {
                            ordinary.await().getOrNull().orEmpty()
                        } else {
                            ordinary.cancel()
                            emptyList()
                        }
                        emit((candidates + existing).distinct())
                    } else {
                        emit(ordinary.await().getOrThrow())
                    }
                }
                ordinary.onAwait { result ->
                    result.getOrNull()?.let { emit(it) }
                    val candidates = specific.await()
                    if (candidates != null) {
                        emit((candidates + result.getOrNull().orEmpty()).distinct())
                    } else {
                        result.getOrThrow()
                    }
                }
            }
        }
    }.distinctUntilChanged()
}
