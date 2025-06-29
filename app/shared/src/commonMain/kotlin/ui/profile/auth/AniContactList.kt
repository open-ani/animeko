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
import me.him188.ani.app.platform.navigation.rememberBrowserNavigator
import me.him188.ani.app.ui.foundation.icons.AniIcons
import me.him188.ani.app.ui.foundation.icons.GithubMark
import me.him188.ani.app.ui.foundation.icons.QqRoundedOutline
import me.him188.ani.app.ui.foundation.icons.Telegram
import me.him188.ani.app.ui.settings.tabs.AniHelperDestination

private val ContactIconSize = 24.dp

@Composable
fun AniContactList(
    modifier: Modifier = Modifier
) {
    val browserNavigator = rememberBrowserNavigator()
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.Start),
    ) {
        SuggestionChip(
            { browserNavigator.openBrowser(AniHelperDestination.GITHUB_HOME) },
            icon = {
                Icon(AniIcons.GithubMark, "Github", Modifier.size(ContactIconSize))
            },
            label = { Text("GitHub") },
        )

        SuggestionChip(
            { browserNavigator.openBrowser(AniHelperDestination.ANI_WEBSITE) },
            icon = {
                Icon(
                    Icons.Rounded.Public, "官网",
                    Modifier.size(ContactIconSize),
                )
            },
            label = { Text("官网") },
        )

        SuggestionChip(
            { browserNavigator.openJoinGroup() },
            icon = {
                Icon(
                    AniIcons.QqRoundedOutline, "QQ 群",
                    Modifier.size(ContactIconSize),
                )
            },
            label = { Text("QQ 群") },
        )

        SuggestionChip(
            { browserNavigator.openJoinTelegram() },
            icon = {
                Image(
                    AniIcons.Telegram, "Telegram",
                    Modifier.size(ContactIconSize),
                )
            },
            label = { Text("Telegram") },
        )
    }
}
