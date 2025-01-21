/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.dp

/**
 * 每个窗口 (页面) 距离窗口边缘的间距.
 *
 * 根据当前设备大小决定.
 */
fun Modifier.materialWindowMarginPadding(): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "materialWindowMarginPadding"
    },
) {
    // https://m3.material.io/foundations/layout/applying-layout
    val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
    val values = when {
        windowSizeClass.isWidthCompact -> {
            // https://m3.material.io/foundations/layout/applying-layout/compact#5a83ddd7-137f-4657-ba2d-eb08cac065e7
            MaterialWindowMarginPaddings.COMPACT
        }

        // medium, expanded, large 都是 24
        else -> {
            MaterialWindowMarginPaddings.EXPANDED
        }
    }
    padding(values)
}


private object MaterialWindowMarginPaddings {
    // single instance
    val COMPACT = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
    val EXPANDED = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)
}
