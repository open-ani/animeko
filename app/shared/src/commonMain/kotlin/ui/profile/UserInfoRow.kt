/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.avatar.AvatarImage


@Composable
fun UserInfoRow(
    self: UserInfo?,
    onClickEditNickname: () -> Unit,
    onClickSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    Row(
        modifier
            .padding(contentPadding)
            .height(IntrinsicSize.Min)
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AvatarImage(
                url = self?.avatarUrl,
                Modifier
                    .placeholder(self == null)
                    .size(64.dp),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp)
                .fillMaxHeight(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = self?.nickname ?: "Loading...",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.placeholder(self?.nickname == null),
                )

//                Box(
//                    modifier = Modifier
//                        .padding(start = 12.dp)
//                        .size(20.dp)
//                        .clickable(onClick = onClickEditNickname)
//                ) {
//                    Icon(
//                        Icons.Default.Edit,
//                        contentDescription = "Edit",
//                        tint = MaterialTheme.colorScheme.primary,
//                    )
//                }
            }

            Row(
                Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
//                Icon(
//                    Icons.Default.SimCard, null,
//                    Modifier
//                        .padding(end = 4.dp)
//                        .size(iconHeight),
//                )
                val density = LocalDensity.current
                Text(
                    text = self?.username ?: "Loading...",
                    Modifier
                        .placeholder(self?.username == null),
                    style = MaterialTheme.typography.labelLarge,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(Modifier.align(Alignment.Top)) {
            IconButton(onClickSettings) {
                Icon(Icons.Rounded.Settings, "设置")
            }
        }
    }
}


private val sampleUser = UserInfo(
    username = "username",
    avatarUrl = "https://example.com/avatar.jpg",
    id = 1,
    nickname = "Nickname",
    sign = "Sign ".repeat(3),
)

@Preview
@Composable
private fun PreviewUserInfoRow() {
    ProvideCompositionLocalsForPreview {
        UserInfoRow(
            self = sampleUser,
            onClickEditNickname = {},
            onClickSettings = {},
        )
    }
}