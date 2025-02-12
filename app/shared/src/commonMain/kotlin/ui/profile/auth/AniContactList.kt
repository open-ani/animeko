/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.profile.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.icons.AniIcons
import me.him188.ani.app.ui.foundation.icons.GithubMark
import me.him188.ani.app.ui.foundation.icons.QqRoundedOutline
import me.him188.ani.app.ui.foundation.icons.Telegram
import me.him188.ani.app.ui.settings.tabs.AniHelpNavigator

private val ContactIconSize = 24.dp

@Composable
fun AniContactList(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.Start),
    ) {
        SuggestionChip(
            { AniHelpNavigator.openGitHubHome(context) },
            icon = {
                Icon(AniIcons.GithubMark, null, Modifier.size(ContactIconSize))
            },
            label = { Text("GitHub") },
        )

        SuggestionChip(
            { AniHelpNavigator.openAniWebsite(context) },
            icon = {
                Icon(
                    Icons.Rounded.Public, null,
                    Modifier.size(ContactIconSize),
                )
            },
            label = { Text("官网") },
        )

        SuggestionChip(
            { AniHelpNavigator.openJoinQQGroup(context) },
            icon = {
                Icon(
                    AniIcons.QqRoundedOutline, null,
                    Modifier.size(ContactIconSize),
                )
            },
            label = { Text("QQ 群") },
        )

        SuggestionChip(
            { AniHelpNavigator.openTelegram(context) },
            icon = {
                Image(
                    AniIcons.Telegram, null,
                    Modifier.size(ContactIconSize),
                )
            },
            label = { Text("Telegram") },
        )
    }
}
