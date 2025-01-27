/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import androidx.collection.IntList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.datasources.bangumi.BangumiRateLimitedException
import me.him188.ani.datasources.bangumi.BangumiSearchSubjectNewApi
import me.him188.ani.datasources.bangumi.client.BangumiSearchApi
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.subjects.BangumiLegacySubject
import me.him188.ani.datasources.bangumi.models.subjects.BangumiSubjectImageSize
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.platform.collections.mapToIntList
import kotlin.coroutines.CoroutineContext

class BangumiSubjectSearchService(
    private val searchApi: ApiInvoker<BangumiSearchApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    suspend fun searchSubjectIds(
        keyword: String,
        useNewApi: Boolean,
        offset: Int? = null,
        limit: Int? = null,
    ): IntList = withContext(ioDispatcher) {
        searchImpl(sanitizeKeyword(keyword), useNewApi, offset, limit).fold(
            left = { list ->
                list.orEmpty().mapToIntList {
                    it.id
                }
            },
            right = { list ->
                list.mapToIntList {
                    it.id
                }
            },
        )
    }

    suspend fun searchSubjectNames(
        keyword: String,
        useNewApi: Boolean,
        includeNsfw: Boolean,
//        offset: Int? = null, // 无法支持 offset, 因为过滤掉 NSFW 后可能会导致返回的结果数量与 offset 不匹配
        limit: Int? = null,
    ): List<String> = withContext(ioDispatcher) {
        searchImpl(keyword, useNewApi, 0, limit).fold(
            left = { list ->
                list.orEmpty()
                    .filter { includeNsfw || !it.nsfw }
                    .map { subject ->
                        subject.nameCn.takeIf { it.isNotEmpty() } ?: subject.name
                    }
            },
            right = { list ->
                list
                    // 不支持 nsfw 过滤
                    .map { subject ->
                        subject.chineseName.takeIf { it.isNotEmpty() } ?: subject.originalName
                    }
            },
        )
    }

    private suspend fun searchImpl(
        keyword: String,
        useNewApi: Boolean,
        offset: Int? = null,
        limit: Int? = null,
    ): Either<List<BangumiSearchSubjectNewApi>?, List<BangumiLegacySubject>> = searchApi {
        if (useNewApi) {
            Either.Left(
                searchSubjectByKeywords(
                    keyword,
                    offset = offset,
                    limit = limit,
                    types = listOf(BangumiSubjectType.Anime),
                ),
            )
        } else {
            try {
                Either.Right(
                    searchSubjectsByKeywordsWithOldApi(
                        keyword,
                        type = BangumiSubjectType.Anime,
                        responseGroup = BangumiSubjectImageSize.SMALL,
                        start = offset,
                        maxResults = limit,
                    ).page,
                )
            } catch (e: BangumiRateLimitedException) {
                throw RepositoryRateLimitedException(cause = e)
            }
        }
    }

    companion object {
        fun sanitizeKeyword(keyword: String): String {
            return buildString(keyword.length) {
                for (c in keyword) {
                    if (MediaListFilters.charsToDeleteForSearch.contains(c.code)) {
                        append(' ')
                    } else {
                        append(c)
                    }
                }
            }
        }
    }
}

private sealed class Either<out A, out B> {
    data class Left<A>(val value: A) : Either<A, Nothing>()
    data class Right<B>(val value: B) : Either<Nothing, B>()
}

private inline fun <A, B, C> Either<A, B>.fold(
    left: (A) -> C,
    right: (B) -> C,
): C = when (this) {
    is Either.Left -> left(value)
    is Either.Right -> right(value)
}
