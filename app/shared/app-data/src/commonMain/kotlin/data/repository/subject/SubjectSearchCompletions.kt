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
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.client.models.AniSubjectType
import kotlin.time.Duration.Companion.seconds

private val subjectIdPattern = Regex("subject/([0-9]+)")
private val episodeIdPattern = Regex("ep/([0-9]+)")

/** 提取首个 subject/ID，或将完整整数输入解析为正 Int 条目 ID。 */
internal fun parseSearchSubjectId(query: String): Int? {
    val value = subjectIdPattern.find(query)?.groupValues?.get(1) ?: query.trim()
    return value.toIntOrNull()?.takeIf { it > 0 }
}

/** 从 ep/ID 中提取首个正 Int 剧集 ID；完整整数输入属于条目 ID。 */
internal fun parseSearchEpisodeId(query: String): Int? =
    episodeIdPattern.find(query)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

/** ID 候选遵守搜索过滤设置；查询失败时使用原输入执行普通关键词补全。 */
internal suspend fun loadSubjectSearchCompletions(
    query: String,
    nsfwMode: NsfwMode,
    excludedIds: Set<Int>,
    getSubject: suspend (Int) -> AniSubjectCollection?,
    getEpisodeSubjectId: suspend (Int) -> Int? = { null },
    searchKeywords: suspend (String) -> List<String>,
): List<String> {
    val completions = try {
        withTimeoutOrNull(5.seconds) {
            val subjectId = parseSearchSubjectId(query)
                ?: parseSearchEpisodeId(query)?.let { getEpisodeSubjectId(it) }
                ?: return@withTimeoutOrNull null
            if (subjectId <= 0 || subjectId in excludedIds) return@withTimeoutOrNull null
            val subject = getSubject(subjectId) ?: return@withTimeoutOrNull null
            if (subject.type != AniSubjectType.ANIME ||
                (subject.nsfw && nsfwMode != NsfwMode.DISPLAY)
            ) return@withTimeoutOrNull null
            val name = subject.nameCn.ifBlank { subject.name }
            if (name.isBlank()) return@withTimeoutOrNull null
            listOf(name, subjectId.toString(), query).distinct()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    return completions ?: searchKeywords(query.trim())
}
