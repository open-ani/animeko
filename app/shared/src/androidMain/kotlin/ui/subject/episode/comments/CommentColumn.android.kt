/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.subject.episode.comments

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.comment.UIRichText
import me.him188.ani.app.ui.comment.generateUiComment
import me.him188.ani.app.ui.comment.rememberTestCommentState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.utils.platform.annotations.TestOnly

@Preview
@Composable
private fun PreviewEpisodeComment() {
    ProvideCompositionLocalsForPreview {
        EpisodeComment(
            comment = remember {
                generateUiComment(
                    size = 1,
                    content = UIRichText(
                        listOf(
                            UIRichElement.AnnotatedText(
                                listOf(
                                    UIRichElement.Annotated.Text("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer nec odio. Praesent libero. Sed cursus ante dapibus diam. Sed nisi. Nulla quis sem at nibh elementum imperdiet."),
                                    UIRichElement.Annotated.Text("masked text", mask = true),
                                ),
                            ),
                        ),
                    ),
                ).single()
            },
            modifier = Modifier.fillMaxWidth(),
            onActionReply = { },
            onClickImage = { },
            onClickUrl = { },
        )

    }
}

@Preview
@Composable
private fun PreviewEpisodeCommentColumn() {
    ProvideCompositionLocalsForPreview {
        EpisodeCommentColumn(
            state = rememberTestCommentState(commentList = generateUiComment(4)),
            editCommentStubText = TextFieldValue("this is my new pending comment"),
            onClickReply = { },
            onClickEditCommentStub = { },
            onClickEditCommentStubEmoji = { },
            onClickUrl = { },
        )
    }
}