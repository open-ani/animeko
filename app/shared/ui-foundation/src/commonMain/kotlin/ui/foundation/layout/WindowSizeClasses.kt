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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

private object PanePaddings {
    private val compactCompact = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    private val compactExpanded = PaddingValues(horizontal = 16.dp, vertical = 24.dp)
    private val expandedCompact = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    private val expandedExpanded = PaddingValues(horizontal = 24.dp, vertical = 24.dp)

    @Stable
    fun get(windowSizeClass: WindowSizeClass): PaddingValues {
        val widthIsCompact = windowSizeClass.isWidthCompact
        val heightIsCompact = windowSizeClass.isHeightCompact
        return when {
            widthIsCompact && heightIsCompact -> compactCompact
            widthIsCompact && !heightIsCompact -> compactExpanded
            !widthIsCompact && heightIsCompact -> expandedCompact
            !widthIsCompact && !heightIsCompact -> expandedExpanded
            else -> error("unreachable")
        }
    }
}

@Stable
inline val WindowAdaptiveInfo.isWidthCompact: Boolean
    get() = windowSizeClass.isWidthCompact

@Stable
inline val WindowAdaptiveInfo.isWidthAtLeastMedium: Boolean
    get() = windowSizeClass.isWidthAtLeastMedium

@Stable
inline val WindowAdaptiveInfo.isWidthAtLeastExpanded: Boolean
    get() = windowSizeClass.isWidthAtLeastExpanded

@Stable
inline val WindowAdaptiveInfo.isHeightCompact: Boolean
    get() = windowSizeClass.isHeightCompact

@Stable
inline val WindowAdaptiveInfo.isHeightAtLeastMedium: Boolean
    get() = windowSizeClass.isHeightAtLeastMedium


@Stable
inline val WindowSizeClass.isWidthCompact: Boolean
    get() = !isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

@Stable
inline val WindowSizeClass.isWidthAtLeastMedium: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

@Stable
inline val WindowSizeClass.isWidthAtLeastExpanded: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

@Stable
inline val WindowSizeClass.isHeightCompact: Boolean
    get() = !isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

@Stable
inline val WindowSizeClass.isHeightAtLeastMedium: Boolean
    get() = isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

@Stable
inline val WindowSizeClass.isHeightAtLeastExpanded: Boolean
    get() = isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND)


@Stable
val WindowSizeClass.panePadding
    get() = PanePaddings.get(this)

@Stable
val WindowSizeClass.paneHorizontalPadding
    get() = if (isWidthCompact) 16.dp else 24.dp

@Stable
val WindowSizeClass.paneVerticalPadding
    get() = if (isHeightCompact) 16.dp else 24.dp

/**
 * 在一个主要的滚动列表中卡片的间距
 */
@Stable
val WindowSizeClass.cardHorizontalPadding
    get() = if (isWidthCompact) 16.dp else 20.dp

/**
 * 在一个主要的滚动列表中卡片的间距
 */
@Stable
val WindowSizeClass.cardVerticalPadding
    get() = if (isHeightCompact) 16.dp else 20.dp

private val zeroInsets = WindowInsets(0.dp) // single instance to be shared

@Stable
val WindowInsets.Companion.Zero: WindowInsets
    get() = zeroInsets

