/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** VM-owned playback interaction, read synchronously by remote input and frame preview. */
@Stable
class TvPlaybackInteractionState(initialPreviewMillis: Long? = null) {
    var scrubMillis by mutableStateOf(initialPreviewMillis)
        private set
    var speedHolding by mutableStateOf(false)
        private set

    internal fun setPreview(positionMillis: Long?) {
        scrubMillis = positionMillis
    }

    internal fun setSpeedHolding(engaged: Boolean) {
        speedHolding = engaged
    }
}
