/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.rss.test

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.rss.RssChannel
import me.him188.ani.app.ui.settings.mediasource.RefreshResult
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.xml.Element

@Immutable
sealed class RssTestResult : RefreshResult { // for ui
    @Immutable
    data object EmptyResult : RssTestResult()

    @Immutable
    data class Success(
        val encodedUrl: String,
        val channel: RssChannel,
        val rssItems: List<RssItemPresentation>,
        val mediaList: List<Media>,
        val origin: Element?,
    ) : RssTestResult(), RefreshResult.Success {
        val originString = origin.toString()
    }

    sealed class Failed : RssTestResult()

    @Immutable
    data class ApiError(
        override val exception: RepositoryException,
    ) : Failed(), RefreshResult.ApiError

    @Immutable
    data class UnknownError(
        override val exception: Throwable,
    ) : Failed(), RefreshResult.UnknownError
}
