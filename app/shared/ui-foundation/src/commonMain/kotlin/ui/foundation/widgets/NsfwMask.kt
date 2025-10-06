/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.foundation.effects.blurEffect
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_search_nsfw_hint
import me.him188.ani.app.ui.lang.exploration_search_nsfw_show
import org.jetbrains.compose.resources.stringResource

/**
 * Nsfw 模糊遮罩加提示. 点击可以临时展示.
 */
@Composable
fun NsfwMask(
    mode: NsfwMode,
    onTemporarilyDisplay: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when (mode) {
            NsfwMode.BLUR -> {
                Box(
                    Modifier.clip(shape)
                        .blurEffect(radius = 12.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .graphicsLayer(alpha = 0.6f),
                ) {
                    content()
                }
                Box(
                    Modifier.matchParentSize().clickable(interactionSource = null, indication = null, onClick = {}),
                ) // 阻止传播点击事件
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.matchParentSize().defaultMinSize(minHeight = 30.dp).padding(top = 10.dp),
                ) {
                    Text(stringResource(Lang.exploration_search_nsfw_hint), textAlign = TextAlign.Center)
                    IconButton(onTemporarilyDisplay) {
                        Icon(
                            Icons.Rounded.RemoveRedEye,
                            contentDescription = stringResource(Lang.exploration_search_nsfw_show),
                        )
                    }
                }
            }

            NsfwMode.HIDE -> {
                Spacer(Modifier.height(Dp.Hairline)) // 有一个最小高度, 防止 lazy column bug
            }

            NsfwMode.DISPLAY -> {
                content()
            }
        }
    }
}
