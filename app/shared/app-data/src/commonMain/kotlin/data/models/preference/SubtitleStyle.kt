/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.Serializable

/** 文本字幕的共享外观；颜色使用 ARGB，位置从顶部 0 到底部 1，描边宽度以 720 高度为基准。 */
@Serializable
data class SubtitleStyle(
    val verticalPosition: Float = 1f,
    val textColor: Long = 0xFFFFFFFF,
    val outlineColor: Long = 0xFF000000,
    val outlineWidth: Float = 2f,
    val backgroundEnabled: Boolean = false,
    val backgroundColor: Long = 0xFF000000,
    val backgroundOpacity: Float = 0.6f,
    val overrideEmbeddedStyle: Boolean = false,
) {
    fun normalized(): SubtitleStyle = copy(
        verticalPosition = verticalPosition.finiteOr(1f).coerceIn(0f, 1f),
        textColor = (textColor and 0xFFFFFFL) or 0xFF000000L,
        outlineColor = (outlineColor and 0xFFFFFFL) or 0xFF000000L,
        outlineWidth = outlineWidth.finiteOr(2f).coerceIn(0f, 8f),
        backgroundColor = (backgroundColor and 0xFFFFFFL) or 0xFF000000L,
        backgroundOpacity = backgroundOpacity.finiteOr(0.6f).coerceIn(0f, 1f),
    )

    companion object {
        val Default = SubtitleStyle()
    }
}

private fun Float.finiteOr(default: Float): Float = if (isFinite()) this else default
