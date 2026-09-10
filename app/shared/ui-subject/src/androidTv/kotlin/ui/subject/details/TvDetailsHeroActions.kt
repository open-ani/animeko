/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.rating_self_score
import me.him188.ani.app.ui.lang.rating_requires_collection
import me.him188.ani.app.ui.lang.subject_details_rate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvPlaceholderBlock
import me.him188.ani.leanback.ui.subject.components.LocalTvDetailsActionBackdrop
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.collection.tvCollectionLabel
import org.jetbrains.compose.resources.stringResource

/** A neutral, inverse-focus pill. Busy actions keep the same focus node. */
@Composable
internal fun TvDetailsAction(
    label: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
    busy: Boolean = false,
    available: Boolean = true,
    active: Boolean = false,
    boundsModifier: Modifier = Modifier,
    compact: Boolean = false,
    blurBackground: Boolean = false,
    glowOnFocus: Boolean = false,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val content = if (focused) Color(0xFF282629) else TvSubjectDetailsDefaults.Content
    val hazeState = LocalTvDetailsActionBackdrop.current.takeIf { blurBackground }
    val backdropModifier = if (hazeState != null) Modifier.hazeEffect(hazeState) {
        backgroundColor = TvSubjectDetailsDefaults.Background
        blurRadius = TvSubjectDetailsDefaults.ActionBlurRadius
        tints = listOf(HazeTint(TvSubjectDetailsDefaults.ActionTint))
        noiseFactor = 0f
        alpha = if (focused) 0f else 1f
    } else Modifier
    val glowAlpha by animateFloatAsState(
        if (glowOnFocus && focused && available) TvSubjectDetailsDefaults.PlayGlowAlpha else 0f,
        animationSpec = tween(180),
        label = "play-focus-glow",
    )
    val glowModifier = if (glowOnFocus) Modifier.dropShadow(
        TvSubjectDetailsDefaults.ActionShape,
        Shadow(
            radius = TvSubjectDetailsDefaults.PlayGlowRadius,
            spread = TvSubjectDetailsDefaults.PlayGlowSpread,
            color = TvSubjectDetailsDefaults.Content,
            alpha = glowAlpha,
        ),
    ) else Modifier
    Surface(
        onClick = { if (available && !busy) onClick() },
        modifier = modifier.heightIn(min = TvSubjectDetailsDefaults.ActionHeight)
            .then(if (loading) Modifier.progressSemantics() else Modifier)
            .semantics { contentDescription = label; selected = active }
            .then(glowModifier),
        shape = TvSubjectDetailsDefaults.ActionShape,
        color = when {
            focused -> TvSubjectDetailsDefaults.Content
            hazeState != null -> Color.Transparent
            else -> TvSubjectDetailsDefaults.ActionTint
        },
        border = if (active && !focused) BorderStroke(1.dp, Color.White.copy(alpha = .65f)) else null,
        contentColor = content.copy(alpha = if (available) 1f else .55f),
        interactionSource = interaction,
    ) {
        Row(
            boundsModifier.then(backdropModifier)
                .padding(horizontal = if (compact) 14.dp else 22.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconSize = if (compact) 18.dp else 22.dp
            if (busy) CircularProgressIndicator(Modifier.size(iconSize), color = content, strokeWidth = 2.dp)
            else if (icon != null) Icon(icon, null, Modifier.size(iconSize))
            if (loading && !iconOnly) TvPlaceholderBlock(Modifier.width(100.dp).height(16.dp), color = content)
            else if (!iconOnly) Text(label, style = MaterialTheme.typography.titleMedium.copy(fontSize = if (compact) 14.sp else 16.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun TvDetailsCollectionAction(
    type: UnifiedCollectionType, onClick: () -> Unit, modifier: Modifier, compact: Boolean, active: Boolean = false,
    boundsModifier: Modifier = Modifier,
) {
    TvDetailsAction(type.tvCollectionLabel(), Icons.Rounded.Bookmark, onClick, modifier,
        iconOnly = compact, active = active, boundsModifier = boundsModifier, blurBackground = true)
}

@Composable
internal fun TvDetailsRatingAction(
    score: Int, onClick: () -> Unit, modifier: Modifier, compact: Boolean, active: Boolean = false,
    boundsModifier: Modifier = Modifier,
    available: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val hint = stringResource(Lang.rating_requires_collection)
    val tooltipVisible = remember { MutableTransitionState(false) }
    tooltipVisible.targetState = focused && !available
    val tooltipSlide = with(LocalDensity.current) { TvSubjectDetailsDefaults.TooltipSlide.toPx().toInt() }
    Box {
        TvDetailsAction(
            if (score > 0) stringResource(Lang.rating_self_score, score) else stringResource(Lang.subject_details_rate),
            Icons.Rounded.Star, onClick,
            modifier.onFocusChanged { focused = it.isFocused }.semantics {
                if (!available) {
                    disabled()
                    stateDescription = hint
                }
            },
            iconOnly = compact, active = active, available = available, boundsModifier = boundsModifier,
            blurBackground = true,
        )
        if (tooltipVisible.currentState || tooltipVisible.targetState) Popup(
            popupPositionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above,
                spacingBetweenTooltipAndAnchor = TvSubjectDetailsDefaults.TooltipSpacing,
            ),
            properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            AnimatedVisibility(
                visibleState = tooltipVisible,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { tooltipSlide },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { tooltipSlide },
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = TvSubjectDetailsDefaults.TooltipMaxWidth)
                        .testTag("tv-rating-collection-tooltip"),
                    shape = TvOptionDefaults.ItemShape,
                    color = TvOptionDefaults.Container,
                    contentColor = TvOptionDefaults.Content,
                    border = BorderStroke(1.dp, TvOptionDefaults.Outline),
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = TvOptionDefaults.Muted)
                        Text(hint, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
