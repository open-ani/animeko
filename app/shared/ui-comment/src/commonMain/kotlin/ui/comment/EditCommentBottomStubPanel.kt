/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.text.ProvideContentColor

/**
 * 显示在评论列表底部的面板，用于打开 [EditComment]
 */
@Composable
fun EditCommentBottomStubPanel(
    text: TextFieldValue,
    onClickEditText: () -> Unit,
    onClickEmoji: () -> Unit,
    modifier: Modifier = Modifier,
    textFieldColors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    placeholder: @Composable () -> Unit = { EditCommentDefaults.CommentTextFieldPlaceholder() },
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation),
        tonalElevation = NavigationBarDefaults.Elevation,
        modifier = modifier.clickable(onClick = onClickEditText),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                onClickEditText,
                Modifier
                    .minimumInteractiveComponentSize()
                    .fillMaxWidth()
                    .weight(1.0f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, textFieldColors.unfocusedIndicatorColor),
            ) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        val t = text.annotatedString
                        Box {
                            Text("a", Modifier.alpha(0f)) // 占高度
                            if (t.isNotEmpty()) {
                                ProvideContentColor(textFieldColors.unfocusedTextColor) {
                                    Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else {
                                ProvideContentColor(textFieldColors.unfocusedPlaceholderColor) {
                                    placeholder()
                                }
                            }
                        }
                    }
                }
            }
            EditCommentDefaults.ActionButton(
                imageVector = Icons.Outlined.SentimentSatisfied,
                onClick = onClickEmoji,
                enabled = true,
            )
        }
    }
}