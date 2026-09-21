/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.leanback.ui.exploration

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

internal enum class TvExplorationArea { Featured, ContinueWatching, Recommendations }

internal val ExplorationPanelEasing = Easing { ((1.0 - cos(PI * it)) / 2.0).toFloat() }
internal val ExplorationFocusEasing = CubicBezierEasing(.2f, .1f, 0f, 1f)
internal val ExplorationScrollEasing = Easing {
    val remaining = it - 1f
    remaining * remaining * remaining * remaining * remaining + 1f
}

internal fun explorationHeroContentTransform(direction: Int, pageWidth: Int): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(tween(500, easing = ExplorationScrollEasing)) { direction * pageWidth },
    initialContentExit = slideOutHorizontally(tween(500, easing = ExplorationScrollEasing)) { -direction * pageWidth },
    sizeTransform = null,
)

/** Keep the selected business identity when a new page or ordering arrives. */
internal fun nextFeaturedSubjectId(ids: List<Int>, currentId: Int?, direction: Int): Int? {
    if (ids.isEmpty()) return null
    val current = ids.indexOf(currentId).coerceAtLeast(0)
    return ids[(current + direction.mod(ids.size)).mod(ids.size)]
}

/** Resource and card-model values from launcherx 1.0.877433387; see google-tv-home-reference.md. */
internal object TvExplorationDefaults {
    val Background = Color(0xFF0E0E0F)
    val Content = Color(0xFFE8EAED)
    val SecondaryContent = Color(0xFFBDC1C6)
    // The existing shell reserves 48dp; content aligns with Google's 58dp screen keyline.
    val StartPadding = 10.dp
    val EndPadding = 56.dp
    val HeroFeaturedExtraSpace = 147.dp
    val PreviewDescriptionWidth = 432.dp
    val WatchingProgressHeight = 20.dp
    val ImmersiveCardWidth = 153.dp
    val ImmersiveCardHeight = 86.dp
    val CardSpacing = 20.dp
    val RowAnchorInset = 64.dp
    val RowGap = 12.dp
    val FadingEdgeHeight = 24.dp
    const val HeroTransitionMillis = 250
    const val WatchingProgressTransitionMillis = 400
    const val BackdropFadeMillis = 750
    const val CarouselVisibleIndicators = 5
    const val CarouselAutoAdvanceMillis = 12000
}
