/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.source

import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.isFailedOrAbandoned
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.datasources.api.Media
import me.him188.ani.leanback.ui.episode.TvPlayerError

data class TvSourceItem(val media: Media, val excludedReason: MediaExclusionReason? = null)

data class TvSourceGroup(
    val instanceId: String,
    val sourceId: String,
    val name: String,
    val iconUrl: String?,
    val state: MediaSourceFetchState,
    val items: List<TvSourceItem>,
    val isCaptchaSupported: Boolean = true,
    val isResolvingCaptcha: Boolean = false,
) {
    val loading: Boolean get() = state == MediaSourceFetchState.Idle || state == MediaSourceFetchState.Working
    val failed: Boolean get() = state.isFailedOrAbandoned
    val showInSimpleMode: Boolean
        get() = state != MediaSourceFetchState.Disabled &&
                (state !is MediaSourceFetchState.Succeed || items.any { it.excludedReason == null })
}

data class TvSourceSelectionState(
    val groups: List<TvSourceGroup> = emptyList(),
    val loading: Boolean = true,
    val error: TvPlayerError? = null,
)
