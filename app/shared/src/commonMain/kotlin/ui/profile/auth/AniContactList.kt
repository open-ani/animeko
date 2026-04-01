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
import me.him188.ani.app.platform.navigation.rememberAsyncBrowserNavigator
import me.him188.ani.app.ui.foundation.icons.AniIcons
import me.him188.ani.app.ui.foundation.icons.GithubMark
import me.him188.ani.app.ui.foundation.icons.QqRoundedOutline
import me.him188.ani.app.ui.foundation.icons.Telegram
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_about_qq_group
import me.him188.ani.app.ui.lang.settings_about_website
import me.him188.ani.app.ui.settings.tabs.AniHelperDestination
import org.jetbrains.compose.resources.stringResource

private val ContactIconSize = 24.dp

@Composable
fun AniContactList(
    modifier: Modifier = Modifier
) {
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current
    val websiteText = stringResource(Lang.settings_about_website)
    val qqGroupText = stringResource(Lang.settings_about_qq_group)

    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.Start),
    ) {
        SuggestionChip(
            { browserNavigator.openBrowser(context, AniHelperDestination.GITHUB_HOME) },
            icon = {
                Icon(AniIcons.GithubMark, "Github", Modifier.size(ContactIconSize))
            },
            label = { Text("GitHub") },
        )

        SuggestionChip(
            { browserNavigator.openBrowser(context, AniHelperDestination.ANI_WEBSITE) },
            icon = {
                Icon(
                    Icons.Rounded.Public,
                    websiteText,
                    Modifier.size(ContactIconSize),
                )
            },
            label = { Text(websiteText) },
        )

        SuggestionChip(
            { browserNavigator.openJoinGroup(context) },
            icon = {
                Icon(
                    AniIcons.QqRoundedOutline,
                    qqGroupText,
                    Modifier.size(ContactIconSize),
                )
            },
            label = { Text(qqGroupText) },
        )

        SuggestionChip(
            { browserNavigator.openJoinTelegram(context) },
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
