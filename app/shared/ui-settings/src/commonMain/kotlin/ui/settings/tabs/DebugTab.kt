/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import me.him188.ani.app.data.models.preference.DebugSettings
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.supportsLimitUploadOnMeteredNetwork
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.platform.MeteredNetworkDetector
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import org.koin.mp.KoinPlatform

@Composable
fun DebugTab(
    debugSettingsState: SettingsState<DebugSettings>,
    uiSettingsState: SettingsState<UISettings>,
    modifier: Modifier = Modifier,
    onDisableDebugMode: () -> Unit = {}
) {
    val debugSettings by debugSettingsState
    val toaster = LocalToaster.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    SettingsTab(modifier) {
        Group(
            title = { Text("调试模式状态") },
            useThinHeader = true,
        ) {
            SwitchItem(
                checked = debugSettings.enabled,
                onCheckedChange = { checked ->
                    if (!checked) onDisableDebugMode()
                    debugSettingsState.update(debugSettings.copy(enabled = checked))
                },
                title = { Text("调试模式") },
                description = { Text("已开启调试模式，点击关闭") },
            )
        }
        Group(
            title = { Text("条目选集") },
            useThinHeader = true,
        ) {
            SwitchItem(
                checked = debugSettings.showAllEpisodes,
                onCheckedChange = { checked ->
                    debugSettingsState.update(debugSettings.copy(showAllEpisodes = checked))
                },
                title = { Text("显示所有剧集") },
                description = { Text("显示所有剧集，包括SP、OP、ED等，目前仅部分在线源支持，请谨慎启用") },
            )
        }
        Group(title = { Text("计费网络信息") }, useThinHeader = true) {
            TextItem {
                val networkDetector = LocalPlatform.current.supportsLimitUploadOnMeteredNetwork()
                Text("supportsLimitUploadOnMeteredNetwork: $networkDetector")
            }
            TextItem {
                val networkDetector = KoinPlatform.getKoin().get<MeteredNetworkDetector>()
                val isMetered by networkDetector.isMeteredNetworkFlow.collectAsStateWithLifecycle(false)
                Text("isMetered: $isMetered")
            }
        }
        Group(title = { Text("新手引导设置") }, useThinHeader = true) {
            TextItem(
                onClick = {
                    val navController = navigator.currentNavigator
                    val mainRouteFQN = NavRoutes.Main::class.qualifiedName!!
                    // 找到上一个 NavRoutes.Main 的路由
                    val lastMainRoute = navController.currentBackStack.value
                        .asReversed()
                        .firstOrNull { it.destination.route?.contains(mainRouteFQN) == true }
                        ?.toRoute<NavRoutes.Main>()
                    // 从 SettingsScreen 进入 onboarding, 最后 navigateMain 要 popUpTo Main
                    // 如果 back stack 没有 Main, 那就 popUpTo Settings, 这个一定有
                    navigator.navigateOnboarding(
                        lastMainRoute
                            ?: navController.currentBackStackEntry?.toRoute<NavRoutes.Settings>(),
                    )
                },
            ) {
                Text("进入新手向导")
            }
            TextItem(
                title = { Text("重置新手引导状态") },
                description = { Text("重置后，下次启动 APP 会进入新手引导") },
                onClick = {
                    uiSettingsState.update(uiSettingsState.value.copy(onboardingCompleted = false))
                    toaster.toast("已重置新手引导状态")
                },
            )
        }
    }
}