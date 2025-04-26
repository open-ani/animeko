/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.network

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.preference.ProxyAuthorization
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_network_proxy_address
import me.him188.ani.app.ui.lang.settings_network_proxy_address_example
import me.him188.ani.app.ui.lang.settings_network_proxy_custom
import me.him188.ani.app.ui.lang.settings_network_proxy_description
import me.him188.ani.app.ui.lang.settings_network_proxy_detecting
import me.him188.ani.app.ui.lang.settings_network_proxy_detection_result
import me.him188.ani.app.ui.lang.settings_network_proxy_disabled
import me.him188.ani.app.ui.lang.settings_network_proxy_none
import me.him188.ani.app.ui.lang.settings_network_proxy_not_detected
import me.him188.ani.app.ui.lang.settings_network_proxy_optional
import me.him188.ani.app.ui.lang.settings_network_proxy_password
import me.him188.ani.app.ui.lang.settings_network_proxy_system
import me.him188.ani.app.ui.lang.settings_network_proxy_title
import me.him188.ani.app.ui.lang.settings_network_proxy_type
import me.him188.ani.app.ui.lang.settings_network_proxy_username
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextFieldItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.utils.ktor.ClientProxyConfigValidator
import org.jetbrains.compose.resources.stringResource

@Immutable
sealed class SystemProxyPresentation {
    @Immutable
    data object Detecting : SystemProxyPresentation()

    @Immutable
    data class Detected(val proxyConfig: ProxyConfig) : SystemProxyPresentation()

    @Immutable
    data object NotDetected : SystemProxyPresentation()
}

@Composable
internal fun SettingsScope.GlobalProxyGroup(
    proxySettingsState: SettingsState<ProxySettings>,
    detectedProxyFlow: Flow<SystemProxyPresentation>
) {
    val proxySettings: ProxySettings by proxySettingsState
    Group(
        title = { Text(stringResource(Lang.settings_network_proxy_title)) },
        description = {
            Text(stringResource(Lang.settings_network_proxy_description))
        },
    ) {
        val selectedMode = proxySettings.default.mode

        Item(
            headlineContent = { Text(stringResource(Lang.settings_network_proxy_type)) },
            supportingContent = {
                val selectedIndex = ProxyMode.entries.indexOf(selectedMode)
                val options = ProxyMode.entries
                SingleChoiceSegmentedButtonRow {
                    options.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = options.size,
                            ),
                            onClick = {
                                proxySettingsState.update(
                                    proxySettings.copy(
                                        default = proxySettings.default.copy(
                                            mode = mode,
                                        ),
                                    ),
                                )
                            },
                            selected = index == selectedIndex,
                            modifier = Modifier.width(IntrinsicSize.Max),
                        ) {
                            Text(
                                when (mode) {
                                    ProxyMode.DISABLED -> stringResource(Lang.settings_network_proxy_disabled)
                                    ProxyMode.SYSTEM -> stringResource(Lang.settings_network_proxy_system)
                                    ProxyMode.CUSTOM -> stringResource(Lang.settings_network_proxy_custom)
                                },
                                softWrap = false,
                            )
                        }
                    }
                }
            },
        )

        AnimatedContent(
            selectedMode,
            transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
        ) { mode ->
            Column {
                when (mode) {
                    ProxyMode.DISABLED -> {}
                    ProxyMode.SYSTEM -> {
                        val detectedProxy by detectedProxyFlow.collectAsStateWithLifecycle(SystemProxyPresentation.Detecting)
                        SystemProxyConfig(detectedProxy)
                    }

                    ProxyMode.CUSTOM -> {
                        CustomProxyConfig(proxySettings, proxySettingsState)
                    }
                }
            }
        }
    }
}


@Composable
fun SettingsScope.SystemProxyConfig(
    proxyConfig: SystemProxyPresentation,
) {
    TextItem(
        title = { Text(stringResource(Lang.settings_network_proxy_detection_result)) },
        description = {
            Text(renderSystemProxyPresentation(proxyConfig))
        },
    )
}

@Composable
fun renderSystemProxyPresentation(systemProxy: SystemProxyPresentation): String {
    return when (systemProxy) {
        is SystemProxyPresentation.Detected -> systemProxy.proxyConfig.url
        SystemProxyPresentation.Detecting -> stringResource(Lang.settings_network_proxy_detecting)
        SystemProxyPresentation.NotDetected -> stringResource(Lang.settings_network_proxy_not_detected)
    }
}


@Composable
fun SettingsScope.CustomProxyConfig(
    proxySettings: ProxySettings,
    proxySettingsState: SettingsState<ProxySettings>
) {
    TextFieldItem(
        proxySettings.default.customConfig.url,
        title = { Text(stringResource(Lang.settings_network_proxy_address)) },
        description = {
            Text(
                stringResource(Lang.settings_network_proxy_address_example),
            )
        },
        onValueChangeCompleted = {
            proxySettingsState.update(
                proxySettings.copy(
                    default = proxySettings.default.copy(
                        customConfig = proxySettings.default.customConfig.copy(
                            url = it,
                        ),
                    ),
                ),
            )
        },
        isErrorProvider = {
            !ClientProxyConfigValidator.isValidProxy(it)
        },
        sanitizeValue = { it.trim() },
    )

    HorizontalDividerItem()

    TextFieldItem(
        proxySettings.default.customConfig.authorization?.username ?: "",
        title = { Text(stringResource(Lang.settings_network_proxy_username)) },
        description = { Text(stringResource(Lang.settings_network_proxy_optional)) },
        placeholder = { Text(stringResource(Lang.settings_network_proxy_none)) },
        onValueChangeCompleted = {
            val newAuth = proxySettings.default.customConfig.authorization?.copy(username = it)
                ?: ProxyAuthorization(username = it, password = "")
            proxySettingsState.update(
                proxySettings.copy(
                    default = proxySettings.default.copy(
                        customConfig = proxySettings.default.customConfig.copy(
                            authorization = newAuth,
                        ),
                    ),
                ),
            )
        },
        sanitizeValue = { it },
    )

    HorizontalDividerItem()

    TextFieldItem(
        proxySettings.default.customConfig.authorization?.password ?: "",
        title = { Text(stringResource(Lang.settings_network_proxy_password)) },
        description = { Text(stringResource(Lang.settings_network_proxy_optional)) },
        placeholder = { Text(stringResource(Lang.settings_network_proxy_none)) },
        onValueChangeCompleted = {
            val newAuth = proxySettings.default.customConfig.authorization?.copy(password = it)
                ?: ProxyAuthorization(username = "", password = it)
            proxySettingsState.update(
                proxySettings.copy(
                    default = proxySettings.default.copy(
                        customConfig = proxySettings.default.customConfig.copy(
                            authorization = newAuth,
                        ),
                    ),
                ),
            )
        },
        sanitizeValue = { it },
    )
}
