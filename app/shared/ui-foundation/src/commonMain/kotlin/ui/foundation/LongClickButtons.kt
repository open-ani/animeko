/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("ERROR_SUPPRESSION")

package me.him188.ani.app.ui.foundation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor

/**
 * <a href="https://m3.material.io/components/buttons/overview" class="external" target="_blank">Material Design filled tonal button</a>.
 *
 * Buttons help people initiate actions, from sending an email, to sharing a document, to liking a
 * post.
 *
 * ![Filled tonal button image](https://developer.android.com/images/reference/androidx/compose/material3/filled-tonal-button.png)
 *
 * Filled tonal buttons are medium-emphasis buttons that is an alternative middle ground between
 * default [Button]s (filled) and [OutlinedButton]s. They can be used in contexts where
 * lower-priority button requires slightly more emphasis than an outline would give, such as "Next"
 * in an onboarding flow. Tonal buttons use the secondary color mapping.
 *
 * @sample androidx.compose.material3.samples.FilledTonalButtonSample
 *
 * Choose the best button for an action based on the amount of emphasis it needs. The more important
 * an action is, the higher emphasis its button should be.
 *
 * - See [Button] for a high-emphasis button without a shadow, also known as a filled button.
 * - See [ElevatedButton] for a [FilledTonalCombinedClickButton] with a shadow.
 * - See [OutlinedButton] for a medium-emphasis button with a border.
 * - See [TextButton] for a low-emphasis button with no border.
 *
 * The default text style for internal [Text] components will be set to [Typography.labelLarge].
 *
 * @param onClick called when this button is clicked
 * @param modifier the [Modifier] to be applied to this button
 * @param enabled controls the enabled state of this button. When `false`, this component will not
 * respond to user input, and it will appear visually disabled and disabled to accessibility
 * services.
 * @param shape defines the shape of this button's container, border (when [border] is not null),
 * and shadow (when using [elevation])
 * @param colors [ButtonColors] that will be used to resolve the colors for this button in different
 * states. See [ButtonDefaults.filledTonalButtonColors].
 * @param elevation [ButtonElevation] used to resolve the elevation for this button in different
 * states. This controls the size of the shadow below the button. Additionally, when the container
 * color is [ColorScheme.surface], this controls the amount of primary color applied as an overlay.
 * @param border the border to draw around the container of this button
 * @param contentPadding the spacing values to apply internally between the container and the
 * content
 * @param interactionSource the [MutableInteractionSource] representing the stream of [Interaction]s
 * for this button. You can create and pass in your own `remember`ed instance to observe
 * [Interaction]s and customize the appearance / behavior of this button in different states.
 */
@Composable
fun FilledTonalCombinedClickButton(
    onClick: () -> Unit,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onDoubleClick: (() -> Unit)? = null,
    indication: Indication = LocalIndication.current,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) =
    CombinedClickButton(
        onClick = onClick,
        onClickLabel = onClickLabel,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        onDoubleClick = onDoubleClick,
        indication = indication,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )


/**
 * <a href="https://m3.material.io/components/buttons/overview" class="external" target="_blank">Material Design button</a>.
 *
 * Buttons help people initiate actions, from sending an email, to sharing a document, to liking a
 * post.
 *
 * ![Filled button image](https://developer.android.com/images/reference/androidx/compose/material3/filled-button.png)
 *
 * Filled buttons are high-emphasis buttons. Filled buttons have the most visual impact after the
 * [FloatingActionButton], and should be used for important, final actions that complete a flow,
 * like "Save", "Join now", or "Confirm".
 *
 * @sample androidx.compose.material3.samples.ButtonSample
 * @sample androidx.compose.material3.samples.ButtonWithIconSample
 *
 * Choose the best button for an action based on the amount of emphasis it needs. The more important
 * an action is, the higher emphasis its button should be.
 *
 * - See [OutlinedButton] for a medium-emphasis button with a border.
 * - See [ElevatedButton] for an [OutlinedButton] with a shadow.
 * - See [TextButton] for a low-emphasis button with no border.
 * - See [FilledTonalCombinedClickButton] for a middle ground between [OutlinedButton] and [Button].
 *
 * The default text style for internal [Text] components will be set to [Typography.labelLarge].
 *
 * @param onClick called when this button is clicked
 * @param modifier the [Modifier] to be applied to this button
 * @param enabled controls the enabled state of this button. When `false`, this component will not
 * respond to user input, and it will appear visually disabled and disabled to accessibility
 * services.
 * @param shape defines the shape of this button's container, border (when [border] is not null),
 * and shadow (when using [elevation])
 * @param colors [ButtonColors] that will be used to resolve the colors for this button in different
 * states. See [ButtonDefaults.buttonColors].
 * @param elevation [ButtonElevation] used to resolve the elevation for this button in different
 * states. This controls the size of the shadow below the button. Additionally, when the container
 * color is [ColorScheme.surface], this controls the amount of primary color applied as an overlay.
 * See [ButtonElevation.shadowElevation] and [ButtonElevation.tonalElevation].
 * @param border the border to draw around the container of this button
 * @param contentPadding the spacing values to apply internally between the container and the
 * content
 * @param interactionSource the [MutableInteractionSource] representing the stream of [Interaction]s
 * for this button. You can create and pass in your own `remember`ed instance to observe
 * [Interaction]s and customize the appearance / behavior of this button in different states.
 */
@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@Composable
fun CombinedClickButton(
    onClick: () -> Unit,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onDoubleClick: (() -> Unit)? = null,
    indication: Indication = LocalIndication.current,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val containerColor = colors.containerColor(enabled)
    val contentColor = colors.contentColor(enabled)
    val shadowElevation = elevation?.shadowElevation(enabled, interactionSource)?.value ?: 0.dp
    Surface(
        modifier = modifier.semantics { role = Role.Button }.combinedClickable(
            interactionSource = interactionSource,
            indication = indication,
            role = Role.Button,
            enabled = enabled,
            onClick = onClick,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
        ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        border = border,
    ) {
        ProvideTextStyleContentColor(
            MaterialTheme.typography.labelLarge,
            contentColor,
        ) {
            Box {
                LongClickProgressFill(
                    interactionSource = interactionSource,
                    enabled = enabled && onLongClick != null,
                    color = contentColor.copy(alpha = 0.12f),
                    modifier = Modifier.matchParentSize(),
                )
                Row(
                    Modifier.defaultMinSize(
                        minWidth = ButtonDefaults.MinWidth,
                        minHeight = ButtonDefaults.MinHeight,
                    )
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

@Composable
fun LongClickProgressFill(
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
) {
    val progress by animateLongClickProgressAsState(interactionSource, enabled)
    Box(
        modifier.drawBehind {
            if (progress > 0f) {
                drawRect(
                    color = color,
                    size = Size(width = size.width * progress, height = size.height),
                )
            }
        },
    )
}

@Composable
private fun animateLongClickProgressAsState(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
): State<Float> {
    val longPressTimeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis.toInt()
    val progress = remember { Animatable(0f) }
    val progressState = remember { mutableStateOf(0f) }

    suspend fun animatePressProgress() {
        progress.snapTo(0f)
        progressState.value = 0f

        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = longPressTimeoutMillis, easing = LinearEasing),
        ) {
            progressState.value = value
        }
        progressState.value = progress.value
    }

    suspend fun resetProgress() {
        progress.snapTo(0f)
        progressState.value = 0f
    }

    LaunchedEffect(interactionSource, enabled, longPressTimeoutMillis) {
        if (!enabled) {
            resetProgress()
            return@LaunchedEffect
        }

        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> animatePressProgress()
                is PressInteraction.Release -> resetProgress()
                is PressInteraction.Cancel -> resetProgress()
            }
        }
    }

    return progressState
}
