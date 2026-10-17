/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.test

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.utils.xml.Element

/**
 * 编辑器 "测试数据源" 里一次条目搜索的结果. 各类数据源的测试器都产生这个类型, 测试页据此展示.
 */
@Immutable
sealed class TestSubjectSearchResult : RefreshResult {
    @Immutable
    data class Success(
        /**
         * 实际请求的地址, 供用户核对关键字的处理方式.
         */
        val encodedUrl: String,
        val subjects: List<TestSubjectPresentation>,
    ) : TestSubjectSearchResult(), RefreshResult.Success

    @Immutable
    data class ApiError(
        override val exception: RepositoryException,
    ) : TestSubjectSearchResult(), RefreshResult.ApiError

    /**
     * 站点要求先处理验证码. 只有抓取网页的数据源会产生.
     */
    @Immutable
    data class CaptchaRequired(
        val request: SolveRequest,
    ) : TestSubjectSearchResult()

    @Immutable
    data object InvalidConfig : TestSubjectSearchResult(), RefreshResult.InvalidConfig

    @Immutable
    data class UnknownError(
        override val exception: Throwable,
    ) : TestSubjectSearchResult(), RefreshResult.UnknownError
}

/**
 * 测试页里的一个条目.
 */
@Immutable
data class TestSubjectPresentation(
    val name: String,
    val subjectDetailsPageUrl: String,
    /**
     * 条目在搜索结果页面里对应的元素. 不是网页的数据源为 `null`.
     */
    val origin: Element?,
    val tags: List<MatchTag>,
) {
    companion object
}
