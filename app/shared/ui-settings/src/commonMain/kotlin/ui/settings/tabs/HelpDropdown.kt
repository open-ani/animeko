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
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.LocalContext
import org.koin.mp.KoinPlatform

object AniHelpNavigator {
    private const val GITHUB_HOME = "https://github.com/open-ani/animeko"
    private const val GITHUB_CONTRIBUTORS = "https://github.com/open-ani/animeko/graphs/contributors"
    private const val ANI_WEBSITE = "https://myani.org"
    private const val ISSUE_TRACKER = "https://github.com/open-ani/animeko/issues"

    private const val GITHUB_REPO = "https://github.com/him188/ani"
    private const val BANGUMI = "https://bangumi.tv"
    private const val DANDANPLAY = "https://www.dandanplay.com/"
    private const val DMHY = "https://dmhy.org/"
    private const val ACG_RIP = "https://acg.rip/"
    private const val MIKAN = "https://mikanime.tv/"


    fun openJoinQQGroup(context: ContextMP) {
        KoinPlatform.getKoin().get<BrowserNavigator>().openJoinGroup(context)
    }

    fun openTelegram(context: ContextMP) {
        KoinPlatform.getKoin().get<BrowserNavigator>().openJoinTelegram(context)
    }

    fun openIssueTracker(context: ContextMP) {
        KoinPlatform.getKoin().get<BrowserNavigator>().openBrowser(context, ISSUE_TRACKER)
    }

    fun openGitHubContributors(context: ContextMP) {
        KoinPlatform.getKoin().get<BrowserNavigator>().openBrowser(context, GITHUB_CONTRIBUTORS)
    }

    fun openGitHubRelease(context: ContextMP, version: String) {
        KoinPlatform.getKoin().get<BrowserNavigator>()
            .openBrowser(context, "https://github.com/open-ani/animeko/releases/tag/v$version")
    }

    fun openGitHubHome(context: ContextMP) {
        KoinPlatform.getKoin().get<BrowserNavigator>().openBrowser(context, GITHUB_HOME)
    }

    fun openAniWebsite(context: ContextMP) {
        KoinPlatform.getKoin().get<BrowserNavigator>().openBrowser(context, ANI_WEBSITE)
    }
}

@Composable
fun HelpDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    DropdownMenu(expanded, onDismissRequest, modifier) {
        DropdownMenuItem(
            text = { Text("QQ") },
            onClick = {
                AniHelpNavigator.openJoinQQGroup(context)
            },
        )
        DropdownMenuItem(
            text = { Text("Telegram") },
            onClick = {
                AniHelpNavigator.openTelegram(context)
            },
        )
        DropdownMenuItem(
            text = { Text("GitHub") },
            onClick = { AniHelpNavigator.openGitHubHome(context) },
        )
        DropdownMenuItem(
            text = { Text("反馈问题") },
            onClick = { AniHelpNavigator.openIssueTracker(context) },
        )
        DropdownMenuItem(
            text = { Text("Ani 官网") },
            onClick = { AniHelpNavigator.openAniWebsite(context) },
        )
    }
}
