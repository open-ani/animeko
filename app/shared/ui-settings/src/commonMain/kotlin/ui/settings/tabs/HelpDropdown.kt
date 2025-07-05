/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.navigation.rememberAsyncBrowserNavigator
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_help_feedback
import me.him188.ani.app.ui.lang.settings_help_github
import me.him188.ani.app.ui.lang.settings_help_qq
import me.him188.ani.app.ui.lang.settings_help_telegram
import me.him188.ani.app.ui.lang.settings_help_website
import org.jetbrains.compose.resources.stringResource

object AniHelperDestination {
    const val GITHUB_HOME = "https://github.com/open-ani/animeko"
    const val GITHUB_CONTRIBUTORS = "https://github.com/open-ani/animeko/graphs/contributors"
    const val ANI_WEBSITE = "https://myani.org"
    const val ISSUE_TRACKER = "https://github.com/open-ani/animeko/issues"
    const val RELEASE_PREFIX = "https://github.com/open-ani/animeko/releases/tag/v"

    const val GITHUB_REPO = "https://github.com/him188/ani"
    const val BANGUMI = "https://bangumi.tv"
    const val DANDANPLAY = "https://www.dandanplay.com/"
    const val DMHY = "https://dmhy.org/"
    const val ACG_RIP = "https://acg.rip/"
    const val MIKAN = "https://mikanime.tv/"
}

@Composable
fun HelpDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current

    DropdownMenu(expanded, onDismissRequest, modifier) {
        DropdownMenuItem(
            text = { Text(stringResource(Lang.settings_help_qq)) },
            onClick = { browserNavigator.openJoinGroup(context) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Lang.settings_help_telegram)) },
            onClick = { browserNavigator.openJoinTelegram(context) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Lang.settings_help_github)) },
            onClick = { browserNavigator.openBrowser(context, AniHelperDestination.GITHUB_HOME) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Lang.settings_help_feedback)) },
            onClick = { browserNavigator.openBrowser(context, AniHelperDestination.ISSUE_TRACKER) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Lang.settings_help_website)) },
            onClick = { browserNavigator.openBrowser(context, AniHelperDestination.ANI_WEBSITE) },
        )
    }
}
