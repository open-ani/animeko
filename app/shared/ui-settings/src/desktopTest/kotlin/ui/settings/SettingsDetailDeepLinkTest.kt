/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test

/**
 * 深链接的二级页 (如 `"TimeZone"`) 在详情区首次组合时直接入栈, 用户不需要再点一次.
 */
class SettingsDetailDeepLinkTest {
    @Test
    fun `pending detail route is opened on first composition`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Host(pendingDetailRoute = DetailPaneRoutes.TimeZone)
            }
        }

        onNodeWithText("timezone-page").assertIsDisplayed()
    }

    @Test
    fun `without a pending detail route the tab content stays in front`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Host(pendingDetailRoute = null)
            }
        }

        onNodeWithText("timezone-page").assertDoesNotExist()
        onNodeWithText("main-page").assertIsDisplayed()
    }

    /**
     * 桌面宽窗格: 详情区一开始就在目标页上.
     */
    @Composable
    private fun Host(pendingDetailRoute: DetailPaneRoutes?) {
        val navigator = rememberListDetailPaneScaffoldNavigator<Nothing?>(
            initialDestinationHistory = listOf(
                ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
                ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail),
            ),
        )
        Box(Modifier.size(1000.dp, 800.dp)) {
            SettingsPageLayout(
                navigator = navigator,
                currentTab = { SettingsTab.APPEARANCE },
                onSelectedTab = {},
                onClickBackOnListPage = {},
                onClickBackOnDetailPage = {},
                navItems = {},
                tabContent = { Text("main-page") },
                timeZonePageContent = { Text("timezone-page") },
                loadOpenSourceLibrariesJsons = { emptyList() },
                pendingDetailRoute = pendingDetailRoute,
                onPendingDetailRouteHandled = {},
            )
        }
    }
}
