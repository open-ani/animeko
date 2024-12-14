/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.episode

import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.mediasource.test.RefreshResult

sealed class SelectorEpisodeResult : RefreshResult {
    data class InProgress(
        val flow: StateFlow<PersistentList<SelectorTestWebUrl>>,
    ) : SelectorEpisodeResult(), RefreshResult.InProgress

    data class Success(
        val flow: StateFlow<PersistentList<SelectorTestWebUrl>>,
    ) : SelectorEpisodeResult(), RefreshResult.Success

    object InvalidConfig : SelectorEpisodeResult(), RefreshResult.InvalidConfig
    data class ApiError(override val exception: RepositoryException) : SelectorEpisodeResult(), RefreshResult.ApiError
    data class UnknownError(override val exception: Throwable) : SelectorEpisodeResult(), RefreshResult.UnknownError
}

data class SelectorTestWebUrl(
    val url: String,
    val didLoadNestedPage: Boolean,
)
