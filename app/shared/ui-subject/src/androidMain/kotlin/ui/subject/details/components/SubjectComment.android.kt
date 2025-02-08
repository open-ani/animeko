/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.comment.generateUiComment
import me.him188.ani.app.ui.comment.rememberTestCommentState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.rememberConnectedScrollState
import me.him188.ani.utils.platform.annotations.TestOnly

@Preview
@Composable
private fun PreviewSubjectComment() {
    ProvideCompositionLocalsForPreview {
        SubjectComment(
            comment = remember { generateUiComment(1).single() },
            modifier = Modifier.fillMaxWidth(),
            onClickImage = { },
            onClickUrl = { },
            onClickReaction = { _, _ -> },
        )

    }
}

@Preview
@Composable
private fun PreviewSubjectCommentColumn() {
    ProvideCompositionLocalsForPreview {
        SubjectDetailsDefaults.SubjectCommentColumn(
            state = rememberTestCommentState(generateUiComment(4)),
            onClickUrl = { },
            onClickImage = {},
            connectedScrollState = rememberConnectedScrollState(),
        )
    }
}
