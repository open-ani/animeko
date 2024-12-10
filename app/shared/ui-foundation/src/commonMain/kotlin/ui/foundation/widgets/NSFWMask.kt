/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.effects.blurEffect

@Composable
fun NSFWMask(
    state: NSFWMaskState,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit
) {
    if (state.maskEnabled) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
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
                modifier = Modifier.matchParentSize().offset(y = 10.dp),
            ) {
                Text("此内容不适合展示", textAlign = TextAlign.Center)
                IconButton({ state.toggle() }) {
                    Icon(Icons.Rounded.RemoveRedEye, contentDescription = "临时展示")
                }
            }
        }
    } else {
        content()
    }
}

class NSFWMaskState(
    initEnabled: Boolean,
    val blurEnabled: Boolean,
) {
    var enabled by mutableStateOf(initEnabled)

    val maskEnabled by derivedStateOf {
        blurEnabled && enabled
    }
    fun toggle() {
        enabled = !enabled
    }

    companion object {
        val Default = NSFWMaskState(initEnabled = false, blurEnabled = false)
    }
}
