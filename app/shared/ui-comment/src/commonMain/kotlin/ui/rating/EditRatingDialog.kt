/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.rating

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.icons.EditSquare
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.rating_comment_hint
import me.him188.ani.app.ui.lang.rating_comment_label
import me.him188.ani.app.ui.lang.rating_comment_optional
import me.him188.ani.app.ui.lang.rating_discard
import me.him188.ani.app.ui.lang.rating_discard_edit_message
import me.him188.ani.app.ui.lang.rating_discard_edit_title
import me.him188.ani.app.ui.lang.rating_edit_title
import me.him188.ani.app.ui.lang.rating_private_only
import me.him188.ani.app.ui.lang.rating_score_class_average
import me.him188.ani.app.ui.lang.rating_score_class_bad
import me.him188.ani.app.ui.lang.rating_score_class_highly_recommended
import me.him188.ani.app.ui.lang.rating_score_class_legendary_caution
import me.him188.ani.app.ui.lang.rating_score_class_masterpiece
import me.him188.ani.app.ui.lang.rating_score_class_okay
import me.him188.ani.app.ui.lang.rating_score_class_poor
import me.him188.ani.app.ui.lang.rating_score_class_recommended
import me.him188.ani.app.ui.lang.rating_score_class_terrible_caution
import me.him188.ani.app.ui.lang.rating_score_class_very_bad
import me.him188.ani.app.ui.lang.settings_danmaku_confirm
import me.him188.ani.app.ui.lang.settings_media_source_continue_editing
import me.him188.ani.app.ui.lang.settings_mediasource_cancel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max

@Stable
class RatingEditorState(
    initialScore: Int, // 0 if not rated
    initialComment: String,
    initialIsPrivate: Boolean,
) {
    var score by mutableIntStateOf(initialScore)
    var comment by mutableStateOf(initialComment)
    var isPrivate by mutableStateOf(initialIsPrivate)

    val hasModified by derivedStateOf {
        score != initialScore || comment != initialComment
    }
    val hasModifiedComment by derivedStateOf {
        comment != initialComment
    }
}

class RateRequest(
    val score: Int,
    val comment: String,
    val isPrivate: Boolean,
)

@Composable
fun RatingEditorDialog(
    state: RatingEditorState,
    onDismissRequest: () -> Unit,
    onRate: (RateRequest) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    val discardEditTitle = stringResource(Lang.rating_discard_edit_title)
    val discardEditMessage = stringResource(Lang.rating_discard_edit_message)
    val discardText = stringResource(Lang.rating_discard)
    val continueEditingText = stringResource(Lang.settings_media_source_continue_editing)
    val editRatingText = stringResource(Lang.rating_edit_title)
    val confirmText = stringResource(Lang.settings_danmaku_confirm)
    val cancelText = stringResource(Lang.settings_mediasource_cancel)
    var showConfirmCancelDialog by remember { mutableStateOf(false) }
    if (showConfirmCancelDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmCancelDialog = false },
            title = { Text(discardEditTitle) },
            text = { Text(discardEditMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmCancelDialog = false
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(discardText)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmCancelDialog = false },
                ) {
                    Text(continueEditingText)
                }
            },
        )
    }
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.EditSquare, null) },
        title = { Text(editRatingText) },
        text = {
            RatingEditor(
                state.score, { state.score = it },
                state.comment, { state.comment = it },
                state.isPrivate, { state.isPrivate = it },
                enabled = !isLoading,
            )
        },
        confirmButton = {
            if (isLoading) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            } else {
                TextButton(
                    onClick = {
                        onRate(RateRequest(state.score, state.comment, state.isPrivate))
                    },
                ) {
                    Text(confirmText)
                }
            }
        },
        dismissButton = {
            TextButton(
                {
                    if (state.hasModifiedComment) {
                        showConfirmCancelDialog = true
                    } else {
                        onDismissRequest()
                    }
                },
            ) {
                Text(cancelText)
            }
        },
        properties = DialogProperties(
            // 当有修改之后必须点击 "取消" 才能关闭
            dismissOnBackPress = !state.hasModified,
            dismissOnClickOutside = !state.hasModified,
        ),
        modifier = modifier
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                focusManager.clearFocus() // 点击编辑框外面关闭键盘
            },
    )
}

@Composable
fun RatingEditor(
    score: Int,
    onScoreChange: (Int) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    isPrivate: Boolean,
    onIsPrivateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scoreLabels = rememberRatingScoreLabels()
    val commentLabelText = stringResource(Lang.rating_comment_label)
    val commentHintText = stringResource(Lang.rating_comment_hint)
    val commentOptionalText = stringResource(Lang.rating_comment_optional)
    val privateOnlyText = stringResource(Lang.rating_private_only)
    Column(modifier) {
        Column(
            Modifier.align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (score == 0) {
                    RatingScoreText(
                        "",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    RatingScoreText(
                        score.toString(),
                        color = scoreColor(score.toFloat()),
                    )
                    RatingScoreText(
                        remember(score, scoreLabels) { renderScoreClass(score.toFloat(), scoreLabels) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = scoreColor(score.toFloat()),
                    )
                }
            }

            Row {
                TenRatingStars(
                    score,
                    onScoreChange = onScoreChange,
                    scoreLabels = scoreLabels,
                    enabled = enabled,
                )
            }
        }

        Column(
            Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row {
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                OutlinedTextField(
                    comment,
                    onCommentChange,
                    Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    singleLine = false,
                    shape = MaterialTheme.shapes.medium,
                    label = {
                        if (isFocused || comment.isNotEmpty()) {
                            Text(commentLabelText)
                        } else {
                            Text(commentHintText)
                        }
                    },
                    interactionSource = interactionSource,
                    placeholder = { Text(commentOptionalText) },
                    readOnly = !enabled,
                )
            }
        }

        Row(
            Modifier.clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = { onIsPrivateChange(!isPrivate) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isPrivate,
                onCheckedChange = onIsPrivateChange,
                enabled = enabled,
            )
            Text(privateOnlyText)
        }
    }
}

@Composable
private fun TenRatingStars(
    score: Int, // range 1..10
    onScoreChange: (Int) -> Unit,
    scoreLabels: RatingScoreLabels,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            val icon = @Composable { index: Int ->
                Icon(
                    if (score >= index) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = renderScoreClass(index.toFloat(), scoreLabels),
                    Modifier
                        .clip(CircleShape)
                        .clickable(
                            remember { MutableInteractionSource() },
                            enabled = enabled,
                            indication = ripple(),
                        ) { onScoreChange(index) }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val size = max(placeable.width, placeable.height)
                            layout(size, size) {
                                placeable.place((size - placeable.width) / 2, (size - placeable.height) / 2)
                            }
                        }
                        .height(32.dp)
                        .weight(1f),
                )
            }

            repeat(10) {
                icon(it + 1)
            }
        }
    }
}

private data class RatingScoreLabels(
    val terribleCaution: String,
    val veryBad: String,
    val bad: String,
    val poor: String,
    val average: String,
    val okay: String,
    val recommended: String,
    val highlyRecommended: String,
    val masterpiece: String,
    val legendaryCaution: String,
)

@Composable
private fun rememberRatingScoreLabels(): RatingScoreLabels = RatingScoreLabels(
    terribleCaution = stringResource(Lang.rating_score_class_terrible_caution),
    veryBad = stringResource(Lang.rating_score_class_very_bad),
    bad = stringResource(Lang.rating_score_class_bad),
    poor = stringResource(Lang.rating_score_class_poor),
    average = stringResource(Lang.rating_score_class_average),
    okay = stringResource(Lang.rating_score_class_okay),
    recommended = stringResource(Lang.rating_score_class_recommended),
    highlyRecommended = stringResource(Lang.rating_score_class_highly_recommended),
    masterpiece = stringResource(Lang.rating_score_class_masterpiece),
    legendaryCaution = stringResource(Lang.rating_score_class_legendary_caution),
)

@Stable
private fun renderScoreClass(score: Float, labels: RatingScoreLabels): String {
    return when (score) {
        in 0f..1f -> labels.terribleCaution
        in 1f..2f -> labels.veryBad
        in 2f..3f -> labels.bad
        in 3f..4f -> labels.poor
        in 4f..5f -> labels.average
        in 5f..6f -> labels.okay
        in 6f..7f -> labels.recommended
        in 7f..8f -> labels.highlyRecommended
        in 8f..9f -> labels.masterpiece
        in 9f..10f -> labels.legendaryCaution
        else -> ""
    }
}

@Composable
fun scoreColor(score: Float): Color {
    return when (score) {
        in 0f..1f -> MaterialTheme.colorScheme.error
        in 1f..4f -> MaterialTheme.colorScheme.onSurface
        in 4f..6f -> MaterialTheme.colorScheme.onSurface
        in 6f..9f -> MaterialTheme.colorScheme.onSurface
        in 9f..10f -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
}


@Composable
@Preview
private fun PreviewEditRatingDialog() {
    ProvideCompositionLocalsForPreview {
        RatingEditorDialog(
            remember {
                RatingEditorState(
                    initialScore = 0,
                    initialComment = "",
                    initialIsPrivate = false,
                )
            },
            onDismissRequest = {},
            onRate = {},
        )
    }
}

@Composable
@Preview
private fun PreviewEditRatingDialogLoading() {
    ProvideCompositionLocalsForPreview {
        RatingEditorDialog(
            remember {
                RatingEditorState(
                    initialScore = 0,
                    initialComment = "",
                    initialIsPrivate = false,
                )
            },
            onDismissRequest = {},
            onRate = {},
            isLoading = true,
        )
    }
}

@Composable
@Preview
private fun PreviewEditRating() {
    ProvideCompositionLocalsForPreview {
        val state = remember {
            RatingEditorState(
                initialScore = 4,
                initialComment = "",
                initialIsPrivate = false,
            )
        }
        Surface {
            RatingEditor(
                score = state.score,
                onScoreChange = { state.score = it },
                comment = state.comment,
                onCommentChange = { state.comment = it },
                isPrivate = state.isPrivate,
                onIsPrivateChange = { state.isPrivate = it },
            )
        }
    }
}

@Composable
@Preview
private fun PreviewEditRatingDisabled() {
    ProvideCompositionLocalsForPreview {
        val state = remember {
            RatingEditorState(
                initialScore = 0,
                initialComment = "",
                initialIsPrivate = false,
            )
        }
        Surface {
            RatingEditor(
                score = state.score,
                onScoreChange = { state.score = it },
                comment = state.comment,
                onCommentChange = { state.comment = it },
                isPrivate = state.isPrivate,
                onIsPrivateChange = { state.isPrivate = it },
                enabled = false,
            )
        }
    }
}
