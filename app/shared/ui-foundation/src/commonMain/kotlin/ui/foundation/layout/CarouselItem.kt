/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.foundation.theme.appColorScheme

@Stable
private val carouselBrush = Brush.verticalGradient(
    listOf(
        Color.Transparent,
        Color.Transparent,
        Color.Black.copy(alpha = 0.612f),
    ),
)

/**
 * @param label see [CarouselItemDefaults.Text]
 * @param supportingText see [CarouselItemDefaults.Text]
 *
 * @see CarouselItemDefaults.itemSize
 */
@Composable // Preview: PreviewTrendingSubjectsCarousel
fun CarouselItemScope.CarouselItem(
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: @Composable () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    colors: CarouselItemColors = CarouselItemDefaults.colors(),
    shape: Shape = CarouselItemDefaults.shape,
    image: @Composable () -> Unit,
) {
    val maskShape = rememberMaskShape(shape)
    BasicCarouselItem(
        label,
        modifier,
        supportingText,
        overlay,
        colors,
        maskShape,
        image,
    )
}

/**
 * @param label see [CarouselItemDefaults.Text]
 * @param supportingText see [CarouselItemDefaults.Text]
 *
 * @see CarouselItemDefaults.itemSize
 */
@Composable // Preview: PreviewTrendingSubjectsCarousel
fun BasicCarouselItem(
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: @Composable () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    colors: CarouselItemColors = CarouselItemDefaults.colors(),
    maskShape: Shape = RectangleShape,
    image: @Composable () -> Unit,
) {
    Box(modifier) {
        Box(Modifier.clip(maskShape)) {
            image()
        }
        Box(Modifier.matchParentSize().background(carouselBrush, maskShape)) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(all = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    colors.titleColor,
                ) {
                    label()
                }
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.labelSmall,
                    colors.supportingTextColor,
                ) {
                    supportingText()
                }
            }
        }
        Box(Modifier.matchParentSize()) {
            overlay()
        }
    }
}


@Immutable
data class CarouselItemColors(
    val titleColor: Color,
    val supportingTextColor: Color,
)

@Stable
object CarouselItemDefaults {
    val shape: Shape
        @Composable
        get() = MaterialTheme.shapes.extraLarge

    @Composable
    fun colors(): CarouselItemColors = appColorScheme(isDark = true).run {
        CarouselItemColors(
            titleColor = onSurface,
            supportingTextColor = onSurface,
        )
    }

    @Composable
    fun Text(value: String) {
        androidx.compose.material3.Text(
            value,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    @Composable
    fun itemSize(): CarouselItemSize {
        val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
        val preferredWidth =
            if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            300.dp
        } else {
            240.dp
        }
        return CarouselItemSize(
            preferredWidth = preferredWidth,
            imageHeight = 213.dp, // 120.dp / 9 * 16
        )
    }
}

@Immutable
data class CarouselItemSize(
    val preferredWidth: Dp,
    val imageHeight: Dp,
)