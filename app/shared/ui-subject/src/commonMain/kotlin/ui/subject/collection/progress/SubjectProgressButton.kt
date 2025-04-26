/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection.progress

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.subject.SubjectProgressState


/**
 * 显示条目的当前观看进度或推荐观看下一集.
 *
 * 在追番中的每个卡片的右下角.
 */
@Composable
fun SubjectProgressButton(
    state: SubjectProgressState,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requiredWidth = Modifier.requiredWidth(IntrinsicSize.Max)
    Crossfade(state.buttonIsPrimary) { isPrimary ->
        if (isPrimary) {
            Button(onClick = onPlay, modifier) {
                Text(state.buttonText, requiredWidth, softWrap = false)
            }
        } else {
            FilledTonalButton(onClick = onPlay, modifier) {
                Text(state.buttonText, requiredWidth, softWrap = false)
            }
        }
    }
}
