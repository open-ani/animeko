/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_empty_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

object CommentDefaults {
    @Composable
    fun Avatar(url: String?, modifier: Modifier = Modifier) {
        AvatarImage(
            url = url,
            modifier = modifier.size(36.dp),
        )
    }

    @Composable
    fun ReactionPicker(
        onClickItem: (reactionValue: String) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val previewing = LocalIsPreviewing.current
        FlowRow(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BangumiCommentSticker.map { (id, drawableRes) -> id to drawableRes }.forEach { (id, drawableRes) ->
                Surface(
                    onClick = { onClickItem("bgm$id") },
                    shape = CircleShape,
                    color = Color.Transparent,
                ) {
                    if (previewing) {
                        Icon(
                            imageVector = Icons.Rounded.Face,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp).size(22.dp),
                        )
                    } else {
                        Image(
                            painter = painterResource(drawableRes),
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp).size(22.dp),
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun EmptyPlaceholder(modifier: Modifier = Modifier) {
        Text(
            stringResource(Lang.comment_empty_title),
            modifier = modifier.padding(16.dp),
        )
    }
}
