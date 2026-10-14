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
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.xml.Element

/**
 * 编辑器 "测试数据源" 里一个条目的剧集列表.
 */
@Immutable
sealed class TestEpisodeListResult : RefreshResult {
    @Immutable
    data class Success(
        /**
         * 线路名称, 按站点顺序. 没有线路概念时为 `null`.
         */
        val channels: List<String>?,
        /**
         * must distinct by [TestEpisodePresentation.playUrl]
         */
        val episodes: List<TestEpisodePresentation>,
    ) : TestEpisodeListResult(), RefreshResult.Success

    @Immutable
    data class ApiError(
        override val exception: RepositoryException
    ) : TestEpisodeListResult(), RefreshResult.ApiError

    /**
     * 站点要求先处理验证码. 只有抓取网页的数据源会产生.
     */
    @Immutable
    data class CaptchaRequired(
        val request: SolveRequest,
    ) : TestEpisodeListResult()

    @Immutable
    data object InvalidConfig : TestEpisodeListResult(), RefreshResult.InvalidConfig

    @Immutable
    data class UnknownError(
        override val exception: Throwable
    ) : TestEpisodeListResult(), RefreshResult.UnknownError
}

/**
 * 测试页里的一个剧集.
 */
@Immutable
class TestEpisodePresentation(
    val channel: String?,
    val name: String,
    val episodeSort: EpisodeSort?,
    val playUrl: String,
    val tags: List<MatchTag>,
    /**
     * 剧集在条目页面里对应的元素. 不是网页的数据源为 `null`.
     */
    val origin: Element?,
) {
    val id: Uuid = Uuid.random()

    companion object
}
