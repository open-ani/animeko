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

/** 提取首个 subject/ID，或将完整整数输入解析为正 Int 条目 ID。 */
internal fun parseSearchSubjectId(query: String): Int? {
    val value = subjectIdPattern.find(query)?.groupValues?.get(1) ?: query.trim()
    return value.toIntOrNull()?.takeIf { it > 0 }
}

/** ID 候选遵守搜索过滤设置；查询失败时使用原输入执行普通关键词补全。 */
internal suspend fun loadSubjectSearchCompletions(
    query: String,
    nsfwMode: NsfwMode,
    excludedIds: Set<Int>,
    getSubject: suspend (Int) -> AniSubjectCollection?,
    searchKeywords: suspend (String) -> List<String>,
): List<String> {
    val subjectId = parseSearchSubjectId(query)
    if (subjectId != null && subjectId !in excludedIds) {
        val subject = try {
            withTimeoutOrNull(5.seconds) { getSubject(subjectId) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (subject != null && subject.type == AniSubjectType.ANIME &&
            (!subject.nsfw || nsfwMode == NsfwMode.DISPLAY)
        ) {
            val name = subject.nameCn.ifBlank { subject.name }
            if (name.isNotBlank()) {
                return listOf(name, subjectId.toString(), query).distinct()
            }
        }
    }
    return searchKeywords(query.trim())
}
