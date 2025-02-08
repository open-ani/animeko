/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.rating

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

@Composable
@Preview
fun PreviewEditMyRatingDialog() {
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
fun PreviewEditMyRatingDialogLoading() {
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
fun PreviewEditMyRating() {
    ProvideCompositionLocalsForPreview {
        val state = remember {
            RatingEditorState(
                initialScore = 4,
                initialComment = "",
                initialIsPrivate = false,
            )
        }
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

@Composable
@Preview
fun PreviewEditMyRatingDisabled() {
    ProvideCompositionLocalsForPreview {
        val state = remember {
            RatingEditorState(
                initialScore = 0,
                initialComment = "",
                initialIsPrivate = false,
            )
        }
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
