/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

@PreviewLightDark
@Composable
fun PreviewAnimatedLinearProgressIndicatorIndefiniteLonger() = ProvideCompositionLocalsForPreview {
    var visible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(key1 = true) {
        while (isActive) {
            visible = !visible
            delay(2000)
        }
    }
    Surface {
        Box(
            Modifier
                .height(64.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            FastLinearProgressIndicator(visible)
        }
    }
}

@PreviewLightDark
@Composable
fun PreviewAnimatedLinearProgressIndicatorIndefiniteShorter() = ProvideCompositionLocalsForPreview {
    var visible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(key1 = true) {
        while (isActive) {
            visible = !visible
            delay(1000)
        }
    }
    Surface {
        Box(
            Modifier
                .height(64.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            FastLinearProgressIndicator(visible)
        }
    }
}

//@PreviewLightDark
//@Composable
//fun PreviewAnimatedLinearProgressIndicatorDefinite() = ProvideCompositionLocalsForPreview {
//    var visible by remember {
//        mutableStateOf(false)
//    }
//
//    LaunchedEffect(key1 = true) {
//        while (isActive) {
//            visible = !visible
//            delay(2000)
//        }
//    }
//    Surface {
//        Box(
//            Modifier
//                .height(64.dp)
//                .fillMaxWidth(), contentAlignment = Alignment.Center
//        ) {
//            AnimatedLinearProgressIndicator(visible, progress = { 1f })
//        }
//    }
//}
