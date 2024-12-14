/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.test.web

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.app.domain.mediasource.test.MatchTag
import me.him188.ani.app.domain.mediasource.test.RefreshResult
import me.him188.ani.app.domain.mediasource.test.buildMatchTags
import me.him188.ani.app.domain.mediasource.web.SelectorSearchQuery
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo
import me.him188.ani.app.domain.mediasource.web.asCandidate
import me.him188.ani.app.domain.mediasource.web.toFilterContext
import me.him188.ani.utils.xml.Element

// For UI
@Immutable
sealed class SelectorTestSearchSubjectResult : RefreshResult {
    @Immutable
    data class Success(
        val encodedUrl: String,
        val subjects: List<SelectorTestSubjectPresentation>,
    ) : SelectorTestSearchSubjectResult(), RefreshResult.Success

    @Immutable
    data class ApiError(
        override val exception: RepositoryException,
    ) : SelectorTestSearchSubjectResult(), RefreshResult.ApiError

    @Immutable
    data object InvalidConfig : SelectorTestSearchSubjectResult(), RefreshResult.InvalidConfig

    @Immutable
    data class UnknownError(
        override val exception: Throwable,
    ) : SelectorTestSearchSubjectResult(), RefreshResult.UnknownError
}

@Immutable
data class SelectorTestSubjectPresentation(
    val name: String,
    val subjectDetailsPageUrl: String,
    val origin: Element?,
    val tags: List<MatchTag>,
) {
    companion object {
        fun compute(
            info: WebSearchSubjectInfo,
            query: SelectorSearchQuery,
            origin: Element?,
            filterBySubjectName: Boolean,
        ): SelectorTestSubjectPresentation {
            val tags = computeTags(info, query, filterBySubjectName)
            return SelectorTestSubjectPresentation(
                name = info.name,
                subjectDetailsPageUrl = info.fullUrl,
                origin = origin,
                tags = tags,
            )
        }

        private fun computeTags(
            info: WebSearchSubjectInfo,
            query: SelectorSearchQuery,
            filterBySubjectName: Boolean,
        ) = buildMatchTags {
            with(query.toFilterContext()) {
                val candidate = info.asCandidate()
                if (filterBySubjectName) {
                    if (!MediaListFilters.ContainsSubjectName.applyOn(candidate)) {
                        emit("标题", isMatch = false)
                    } else {
                        emit("标题", isMatch = true)
                    }
                }
                if (info.fullUrl.isBlank()) {
                    emit("链接", isMissing = true)
                } else {
                    emit(info.partialUrl, isMatch = true)
                }
            }
        }
    }
}
