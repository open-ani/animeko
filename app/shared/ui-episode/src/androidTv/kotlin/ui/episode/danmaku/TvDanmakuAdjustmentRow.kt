/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.danmaku

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.tv_player_adjusting
import me.him188.ani.leanback.ui.episode.components.tvStepKeys
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

/** Left closes the settings page until the user explicitly enters a value's adjustment. */
@Composable
internal fun TvDanmakuAdjustmentRow(
    title: String,
    value: String,
    adjusting: Boolean,
    onAdjustingChange: (Boolean) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
) {
    val adjustingText = stringResource(Lang.tv_player_adjusting)
    TvOptionRow(
        title,
        value,
        adjustable = adjusting,
        valueIcon = if (adjusting) null else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        modifier = modifier
            .onFocusChanged { if (!it.hasFocus && adjusting) onAdjustingChange(false) }
            .semantics { stateDescription = if (adjusting) adjustingText else "" }
            .then(if (adjusting) Modifier.tvStepKeys(onStep) else Modifier),
    ) {
        if (adjusting) onReset?.invoke()
        onAdjustingChange(!adjusting)
    }
}
