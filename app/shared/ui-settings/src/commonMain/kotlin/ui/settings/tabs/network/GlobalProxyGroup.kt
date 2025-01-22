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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextFieldItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.utils.ktor.ClientProxyConfigValidator

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
        title = { Text("全局代理设置") },
        description = {
            Text("应用于所有数据源以及 Bangumi")
        },
    ) {
        val selectedMode = proxySettings.default.mode

        Item(
            headlineContent = { Text("代理类型") },
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
                                    ProxyMode.DISABLED -> "禁用"
                                    ProxyMode.SYSTEM -> "系统代理"
                                    ProxyMode.CUSTOM -> "自定义"
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
            transitionSpec = AniThemeDefaults.standardAnimatedContentTransition,
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
        title = { Text("自动检测结果") },
        description = {
            when (proxyConfig) {
                is SystemProxyPresentation.Detected -> Text(proxyConfig.proxyConfig.url)
                SystemProxyPresentation.Detecting -> Text("正在检测")
                SystemProxyPresentation.NotDetected -> Text("未检测到系统代理")
            }
        },
    )
}

@Composable
fun SettingsScope.CustomProxyConfig(
    proxySettings: ProxySettings,
    proxySettingsState: SettingsState<ProxySettings>
) {
    TextFieldItem(
        proxySettings.default.customConfig.url,
        title = { Text("代理地址") },
        description = {
            Text(
                "示例: http://127.0.0.1:7890 或 socks5://127.0.0.1:1080",
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

    val username by remember {
        derivedStateOf {
            proxySettings.default.customConfig.authorization?.username ?: ""
        }
    }

    val password by remember {
        derivedStateOf {
            proxySettings.default.customConfig.authorization?.password ?: ""
        }
    }

    TextFieldItem(
        username,
        title = { Text("用户名") },
        description = { Text("可选") },
        placeholder = { Text("无") },
        onValueChangeCompleted = {
            proxySettingsState.update(
                proxySettings.copy(
                    default = proxySettings.default.copy(
                        customConfig = proxySettings.default.customConfig.copy(
                            authorization = proxySettings.default.customConfig.authorization?.copy(
                                username = it,
                            ),
                        ),
                    ),
                ),
            )
        },
        sanitizeValue = { it },
    )

    HorizontalDividerItem()

    TextFieldItem(
        password,
        title = { Text("密码") },
        description = { Text("可选") },
        placeholder = { Text("无") },
        onValueChangeCompleted = {
            proxySettingsState.update(
                proxySettings.copy(
                    default = proxySettings.default.copy(
                        customConfig = proxySettings.default.customConfig.copy(
                            authorization = proxySettings.default.customConfig.authorization?.copy(
                                password = password,
                            ),
                        ),
                    ),
                ),
            )
        },
        sanitizeValue = { it },
    )
}

