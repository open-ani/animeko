/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.DebugSettings
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.supportsLimitUploadOnMeteredNetwork
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.navigation.findLast
import me.him188.ani.app.platform.MeteredNetworkDetector
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_debug_copied
import me.him188.ani.app.ui.lang.settings_debug_enter_onboarding
import me.him188.ani.app.ui.lang.settings_debug_episodes
import me.him188.ani.app.ui.lang.settings_debug_get_ani_token
import me.him188.ani.app.ui.lang.settings_debug_logged_out
import me.him188.ani.app.ui.lang.settings_debug_logout
import me.him188.ani.app.ui.lang.settings_debug_metered_network
import me.him188.ani.app.ui.lang.settings_debug_mode
import me.him188.ani.app.ui.lang.settings_debug_mode_description
import me.him188.ani.app.ui.lang.settings_debug_onboarding
import me.him188.ani.app.ui.lang.settings_debug_others
import me.him188.ani.app.ui.lang.settings_debug_reset_onboarding
import me.him188.ani.app.ui.lang.settings_debug_reset_onboarding_description
import me.him188.ani.app.ui.lang.settings_debug_reset_onboarding_toast
import me.him188.ani.app.ui.lang.settings_debug_show_all_episodes
import me.him188.ani.app.ui.lang.settings_debug_show_all_episodes_description
import me.him188.ani.app.ui.lang.settings_debug_status
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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
    val clipboard = LocalClipboardManager.current

    SettingsTab(modifier) {
        Group(
            title = { Text(stringResource(Lang.settings_debug_status)) },
            useThinHeader = true,
        ) {
            SwitchItem(
                checked = debugSettings.enabled,
                onCheckedChange = { checked ->
                    if (!checked) onDisableDebugMode()
                    debugSettingsState.update(debugSettings.copy(enabled = checked))
                },
                title = { Text(stringResource(Lang.settings_debug_mode)) },
                description = { Text(stringResource(Lang.settings_debug_mode_description)) },
            )
        }
        Group(
            title = { Text(stringResource(Lang.settings_debug_episodes)) },
            useThinHeader = true,
        ) {
            SwitchItem(
                checked = debugSettings.showAllEpisodes,
                onCheckedChange = { checked ->
                    debugSettingsState.update(debugSettings.copy(showAllEpisodes = checked))
                },
                title = { Text(stringResource(Lang.settings_debug_show_all_episodes)) },
                description = { Text(stringResource(Lang.settings_debug_show_all_episodes_description)) },
            )
        }
        Group(title = { Text(stringResource(Lang.settings_debug_metered_network)) }, useThinHeader = true) {
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
        Group(title = { Text(stringResource(Lang.settings_debug_onboarding)) }, useThinHeader = true) {
            TextItem(
                onClick = {
                    val navController = navigator.currentNavigator
                    // 从 SettingsScreen 进入 onboarding, 最后 navigateMain 要 popUpTo Main
                    // 如果 back stack 没有 Main, 那就 popUpTo Settings, 这个一定有
                    navigator.navigateOnboarding(
                        navController.findLast<NavRoutes.Main>()
                            ?: navController.currentBackStackEntry?.toRoute<NavRoutes.Settings>(),
                    )
                },
            ) {
                Text(stringResource(Lang.settings_debug_enter_onboarding))
            }
            TextItem(
                title = { Text(stringResource(Lang.settings_debug_reset_onboarding)) },
                description = { Text(stringResource(Lang.settings_debug_reset_onboarding_description)) },
                onClick = {
                    uiSettingsState.update(uiSettingsState.value.copy(onboardingCompleted = false))
                    scope.launch {
                        toaster.toast(getString(Lang.settings_debug_reset_onboarding_toast))
                    }
                },
            )
        }
        Group(title = { Text(stringResource(Lang.settings_debug_others)) }, useThinHeader = true) {
            TextItem(
                title = { Text(stringResource(Lang.settings_debug_logout)) },
                onClick = {
                    scope.launch {
                        GlobalKoin.get<UserRepository>().clearSelfInfo()
                        toaster.toast(getString(Lang.settings_debug_logged_out))
                    }
                },
            )
            TextItem(
                title = { Text(stringResource(Lang.settings_debug_get_ani_token)) },
                onClick = {
                    scope.launch {
                        val value =
                            (GlobalKoin.get<SessionManager>().sessionFlow.value as? AccessTokenSession)?.tokens?.aniAccessToken
                        toaster.toast(getString(Lang.settings_debug_copied, value.toString()))
                        clipboard.setText(AnnotatedString(value.toString()))
                    }
                },
            )
            TextItem(
                title = { Text("Crash") },
                onClick = {
                    throw ManualCrashException()
                },
            )
        }
    }
}

private class ManualCrashException : Throwable("Manual crash for testing")
