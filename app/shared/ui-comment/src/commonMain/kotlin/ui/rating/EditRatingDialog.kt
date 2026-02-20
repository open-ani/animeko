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
    var showConfirmCancelDialog by remember { mutableStateOf(false) }
    if (showConfirmCancelDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmCancelDialog = false },
            title = { Text("舍弃编辑") },
            text = { Text("评价尚未保存，确定要舍弃吗？") },
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
                    Text("舍弃")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmCancelDialog = false },
                ) {
                    Text("继续编辑")
                }
            },
        )
    }
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.EditSquare, null) },
        title = { Text("修改评分") },
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
                    Text("确定")
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
                Text("取消")
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
                        remember(score) { renderScoreClass(score.toFloat()) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = scoreColor(score.toFloat()),
                    )
                }
            }

            Row {
                TenRatingStars(
                    score,
                    onScoreChange = onScoreChange,
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
                            Text("评价")
                        } else {
                            Text("说点什么...")
                        }
                    },
                    interactionSource = interactionSource,
                    placeholder = { Text("可留空") },
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
            Text("仅自己可见")
        }
    }
}

@Composable
fun TenRatingStars(
    score: Int, // range 1..10
    onScoreChange: (Int) -> Unit,
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
                    contentDescription = renderScoreClass(index.toFloat()),
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

@Stable
fun renderScoreClass(score: Float): String {
    return when (score) {
        in 0f..1f -> "不忍直视（请谨慎评价）"
        in 1f..2f -> "很差"
        in 2f..3f -> "差"
        in 3f..4f -> "较差"
        in 4f..5f -> "不过不失"
        in 5f..6f -> "还行"
        in 6f..7f -> "推荐"
        in 7f..8f -> "力荐"
        in 8f..9f -> "神作"
        in 9f..10f -> "超神作（请谨慎评价）"
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
