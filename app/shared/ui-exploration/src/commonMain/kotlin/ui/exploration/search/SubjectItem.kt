/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium

/**
 * Design: [SubjectItem on Figma](https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Main?node-id=101-877&t=gmFJS6LFQudIIXfK-4)
 *
 * @param image see [SubjectItemDefaults.Image]
 * @param title remember to use `maxLines`
 * @param actions see [SubjectItemDefaults.ActionPlay]
 */
@Composable
fun SubjectItemLayout(
    selected: Boolean,
    onClick: () -> Unit,
    image: @Composable () -> Unit,
    title: @Composable (maxLines: Int) -> Unit,
    tags: @Composable () -> Unit,
    extraInfo: @Composable () -> Unit,
    rating: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    layout: SubjectItemLayoutParameters = SubjectItemLayoutParameters.calculate(currentWindowAdaptiveInfo1().windowSizeClass),
    typography: SubjectItemTypography = SubjectItemTypography.calculate(currentWindowAdaptiveInfo1().windowSizeClass),
    colors: SubjectItemColors = SubjectItemDefaults.colors()
) {
    val shape = layout.shape
    Surface(
        onClick,
        modifier
            .clip(shape)
            .height(IntrinsicSize.Min)
            .defaultMinSize(minWidth = layout.minWidth)
            .width(IntrinsicSize.Min),
        color = colors.containerColorFor(selected),
        shape = shape,
    ) {
        Row {
            Box(
                Modifier.clip(shape).size(layout.imageSize),
            ) {
                image()
            }
            Column(
                Modifier.padding(layout.bodyPaddings),
                verticalArrangement = layout.bodyVerticalArrangement,
            ) {
                Column(
                    Modifier.weight(1f).padding(layout.textsPaddings),
                    verticalArrangement = layout.bodyVerticalArrangement,
                ) {
                    Row(Modifier.width(IntrinsicSize.Max)) {
                        ProvideTextStyle(typography.titleStyle) {
                            title(typography.titleMaxLines)
                        }
                    }
                    ProvideTextStyle(typography.tagsStyle) {
                        tags()
                    }
                    Spacer(Modifier.weight(1f))
                    Column(verticalArrangement = layout.extraInfoVerticalArrangement) {
                        ProvideTextStyle(typography.extraInfoStyle) {
                            extraInfo()
                        }
                    }
                }
                Row(
                    Modifier.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(Modifier.weight(1f).padding(bottom = 8.dp)) {
                        rating()
                    }
                    actions()
                }
            }
        }
    }
}

@Stable
object SubjectItemDefaults {
    @Composable
    fun Image(
        model: Any?,
        modifier: Modifier = Modifier,
        contentDescription: String? = null,
    ) {
        AsyncImage(
            model,
            contentDescription,
            modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )
    }

    @Composable
    fun ActionPlay(
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        FilledTonalIconButton(onClick, modifier) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "播放", Modifier.size(28.dp))
        }
    }

    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
        selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    ) = SubjectItemColors(containerColor, selectedContainerColor)
}

@Immutable
class SubjectItemTypography(
    val titleStyle: TextStyle,
    val tagsStyle: TextStyle,
    val extraInfoStyle: TextStyle,
    val titleMaxLines: Int,
) {
    @Stable
    companion object {
        @Composable
        fun calculate(windowSizeClass: WindowSizeClass): SubjectItemTypography {
            if (windowSizeClass.isWidthAtLeastMedium && windowSizeClass.isHeightAtLeastMedium) {
                // medium
                return SubjectItemTypography(
                    titleStyle = MaterialTheme.typography.titleLarge,
                    tagsStyle = MaterialTheme.typography.labelLarge,
                    extraInfoStyle = MaterialTheme.typography.bodyMedium,
                    titleMaxLines = 2,
                )
            }
            // compact
            return SubjectItemTypography(
                titleStyle = MaterialTheme.typography.titleMedium,
                tagsStyle = MaterialTheme.typography.labelMedium,
                extraInfoStyle = MaterialTheme.typography.bodySmall,
                titleMaxLines = 1,
            )
        }
    }
}

@Immutable
class SubjectItemLayoutParameters(
    val minWidth: Dp,
    val shape: Shape,
    val imageSize: DpSize,
    val bodyPaddings: PaddingValues,
    val textsPaddings: PaddingValues,
    val bodyVerticalArrangement: Arrangement.Vertical,
    val extraInfoVerticalArrangement: Arrangement.Vertical,
) {
    @Stable
    companion object {
        private val MEDIUM
            @Composable
            get() = SubjectItemLayoutParameters(
                minWidth = 410.dp,
                shape = MaterialTheme.shapes.extraLarge,
                imageSize = DpSize(158.dp, 223.dp),
                bodyPaddings = PaddingValues(start = 16.dp, top = 12.dp),
                textsPaddings = PaddingValues(end = 16.dp),
                bodyVerticalArrangement = Arrangement.spacedBy(4.dp),
                extraInfoVerticalArrangement = Arrangement.spacedBy(8.dp),
            )

        private val COMPACT
            @Composable
            get() = SubjectItemLayoutParameters(
                minWidth = Dp.Unspecified,
                shape = MaterialTheme.shapes.large,
                imageSize = DpSize(114.dp, 161.dp),
                bodyPaddings = PaddingValues(start = 12.dp, top = 8.dp),
                textsPaddings = PaddingValues(end = 12.dp),
                bodyVerticalArrangement = Arrangement.spacedBy(2.dp),
                extraInfoVerticalArrangement = Arrangement.spacedBy(4.dp),
            )

        @Composable
        @Stable
        fun calculate(windowSizeClass: WindowSizeClass): SubjectItemLayoutParameters {
            if (windowSizeClass.isWidthAtLeastMedium && windowSizeClass.isHeightAtLeastMedium) {
                return MEDIUM
            }
            return COMPACT
        }
    }
}

@Immutable
data class SubjectItemColors(
    private val containerColor: Color,
    private val selectedContainerColor: Color,
) {
    @Stable
    fun containerColorFor(selected: Boolean) =
        if (selected) selectedContainerColor else containerColor
}
