/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.adaptive

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarColors
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarDefaults.InputFieldHeight
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.navigation.BackHandler

/**
 * 本身相当于是一个 [inputField], 可以以 popup 形式展开 [content]. 有动画效果.
 */
@ExperimentalMaterial3Api
@Composable
fun PopupSearchBar(
    inputField: @Composable () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    content: @Composable (ColumnScope.() -> Unit),
) {
    val cornerSizeDp by animateDpAsState(
        if (expanded) 0.dp else 28.dp,
        animationSpec =
        if (expanded) {
            // 现在要展开
            tween(
                durationMillis = 300,
                delayMillis = 0,
                easing = AnimationEnterEasing,
            )
        } else {
            // 现在要收起来
            tween(
                durationMillis = AnimationExitDurationMillis,
                delayMillis = AnimationExitDurationMillis, // 先等 list 收起来
                easing = AnimationExitEasing,
            )
        },
    )
    Surface(
        shape = MaterialTheme.shapes.extraLarge.copy(
            bottomStart = CornerSize(cornerSizeDp),
            bottomEnd = CornerSize(cornerSizeDp),
        ),
        color = colors.containerColor,
        contentColor = contentColorFor(colors.containerColor),
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        modifier = modifier.zIndex(1f).widthIn(min = SearchBarMinWidth),
    ) {
        Column {
            var size by remember { mutableStateOf(IntSize.Zero) }
            Box(Modifier.onSizeChanged { size = it }) {
                inputField()
            }
            Popup(
                offset = IntOffset(0, size.height),
                onDismissRequest = { onExpandedChange(false) },
            ) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = cornerSizeDp,
                        topEnd = cornerSizeDp,
                    ),
                    color = colors.containerColor,
                    contentColor = contentColorFor(colors.containerColor),
                    tonalElevation = tonalElevation,
                    shadowElevation = shadowElevation,
                    modifier = Modifier.zIndex(1f).width(LocalDensity.current.run { size.width.toDp() }),
                ) {
                    AniAnimatedVisibility(
                        visible = expanded,
                        enter = DockedEnterTransition,
                        exit = DockedExitTransition,
                    ) {
                        val screenHeight = getScreenHeight()
                        val maxHeight =
                            remember(screenHeight) {
                                screenHeight * DockedExpandedTableMaxHeightScreenRatio
                            }
                        val minHeight =
                            remember(maxHeight) { DockedExpandedTableMinHeight.coerceAtMost(maxHeight) }

                        Column(
                            Modifier
                                .heightIn(min = minHeight, max = maxHeight),
                        ) {
                            HorizontalDivider(color = colors.dividerColor)
                            content()
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = expanded) { onExpandedChange(false) }
}

@Composable
internal expect fun getScreenHeight(): Dp

///////////////////////////////////////////////////////////////////////////
// 以下全部复制自 CMP
///////////////////////////////////////////////////////////////////////////


// Measurement specs
@OptIn(ExperimentalMaterial3Api::class)
private val SearchBarCornerRadius: Dp = InputFieldHeight / 2
internal val DockedExpandedTableMinHeight: Dp = 240.dp
private const val DockedExpandedTableMaxHeightScreenRatio: Float = 2f / 3f
internal val SearchBarMinWidth: Dp = 360.dp
private val SearchBarMaxWidth: Dp = 720.dp
internal val SearchBarVerticalPadding: Dp = 8.dp

// Search bar has 16dp padding between icons and start/end, while by default text field has 12dp.
private val SearchBarIconOffsetX: Dp = 4.dp
private const val SearchBarPredictiveBackMinScale: Float = 9f / 10f
private val SearchBarPredictiveBackMinMargin: Dp = 8.dp
private const val SearchBarPredictiveBackMaxOffsetXRatio: Float = 1f / 20f
private val SearchBarPredictiveBackMaxOffsetY: Dp = 24.dp

// Animation specs
private const val AnimationEnterDurationMillis: Int = 600
private const val AnimationExitDurationMillis: Int = 350
private const val AnimationDelayMillis: Int = 100
private val AnimationEnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val AnimationExitEasing = CubicBezierEasing(0.0f, 1.0f, 0.0f, 1.0f)
private val AnimationEnterFloatSpec: FiniteAnimationSpec<Float> =
    tween(
        durationMillis = AnimationEnterDurationMillis,
        delayMillis = AnimationDelayMillis,
        easing = AnimationEnterEasing,
    )
private val AnimationExitFloatSpec: FiniteAnimationSpec<Float> =
    tween(
        durationMillis = AnimationExitDurationMillis,
        delayMillis = AnimationDelayMillis,
        easing = AnimationExitEasing,
    )
private val AnimationPredictiveBackExitFloatSpec: FiniteAnimationSpec<Float> =
    tween(
        durationMillis = AnimationExitDurationMillis,
        easing = AnimationExitEasing,
    )
private val AnimationEnterSizeSpec: FiniteAnimationSpec<IntSize> =
    tween(
        durationMillis = AnimationEnterDurationMillis,
        delayMillis = AnimationDelayMillis,
        easing = AnimationEnterEasing,
    )
private val AnimationExitSizeSpec: FiniteAnimationSpec<IntSize> =
    tween(
        durationMillis = AnimationExitDurationMillis,
        delayMillis = AnimationDelayMillis,
        easing = AnimationExitEasing,
    )
private val DockedEnterTransition: EnterTransition =
    fadeIn(AnimationEnterFloatSpec) + expandVertically(AnimationEnterSizeSpec)
private val DockedExitTransition: ExitTransition =
    fadeOut(AnimationExitFloatSpec) + shrinkVertically(AnimationExitSizeSpec)
