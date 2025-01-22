/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.step

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.tabs.network.CustomProxyConfig
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyConfig
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyPresentation
import me.him188.ani.app.ui.wizard.ProxyTestCase
import me.him188.ani.app.ui.wizard.ProxyTestCaseEnums

@Immutable
enum class ProxyTestCaseState {
    INIT,
    RUNNING,
    SUCCESS,
    FAILED
}

@Stable
class ProxyTestItem(
    val case: ProxyTestCase,
    val state: ProxyTestCaseState,
)

@Stable
class ProxyTestState(
    val testRunning: State<Boolean>,
    val currentTestMode: State<ProxyMode>,
    val items: State<List<ProxyTestItem>>
)


@Composable
private fun SettingsScope.ProxyTestStatusGroup(
    testRunning: Boolean,
    currentTestMode: ProxyMode,
    items: List<ProxyTestItem>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val text = remember(testRunning, currentTestMode, items) {
        if (testRunning) {
            "正在检测连接，请稍后"
        } else if (items.any { it.state == ProxyTestCaseState.FAILED }) {
            if (currentTestMode == ProxyMode.DISABLED) {
                "部分服务连接失败，请考虑启用代理"
            } else {
                "部分服务连接失败，请更换代理模式或代理地址"
            }
        } else {
            "所有服务连接正常"
        }
    }
    val useErrorColor = remember(testRunning, items) {
        !testRunning && items.any { it.state == ProxyTestCaseState.FAILED }
    }

    Group(
        title = {
            Text(
                text = text,
                color = if (useErrorColor)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        useThinHeader = true,
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun renderTestCaseName(case: ProxyTestCase): String {
    return when (case.name) {
        ProxyTestCaseEnums.ANI_DANMAKU_API -> "Ani"
        ProxyTestCaseEnums.BANGUMI_V0 -> "Bangumi"
        ProxyTestCaseEnums.BANGUMI_P1 -> "Bangumi"
    }
}

@Composable
private fun renderTestCaseDescription(case: ProxyTestCase): String {
    return when (case.name) {
        ProxyTestCaseEnums.ANI_DANMAKU_API -> "弹幕服务"
        ProxyTestCaseEnums.BANGUMI_V0 -> "收藏数据服务"
        ProxyTestCaseEnums.BANGUMI_P1 -> "评论服务"
    }
}

@Composable
private fun ProxyTestStatusIcon(
    state: ProxyTestCaseState,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (state) {
            ProxyTestCaseState.INIT, ProxyTestCaseState.RUNNING ->
                CircularProgressIndicator(modifier = Modifier.size(24.dp))

            ProxyTestCaseState.SUCCESS ->
                ProvideContentColor(MaterialTheme.colorScheme.onSurfaceVariant) {
                    Icon(Icons.Default.Check, null)
                }

            ProxyTestCaseState.FAILED ->
                ProvideContentColor(MaterialTheme.colorScheme.error) {
                    Icon(Icons.Default.Close, null)
                }
        }
    }
}

@Composable
private fun SettingsScope.ProxyTestItemView(
    item: ProxyTestItem,
    modifier: Modifier = Modifier
) {
    TextItem(
        modifier = modifier,
        title = { Text(renderTestCaseName(item.case)) },
        description = { Text(renderTestCaseDescription(item.case)) },
        icon = {
            Icon(
                item.case.icon,
                contentDescription = renderTestCaseDescription(item.case),
            )
        },
        action = { ProxyTestStatusIcon(item.state) },
    )
}

@Composable
private fun renderProxyConfigModeName(mode: ProxyMode): String {
    return when (mode) {
        ProxyMode.DISABLED -> "不使用代理"
        ProxyMode.SYSTEM -> "系统代理"
        ProxyMode.CUSTOM -> "自定义代理"
    }
}

@Composable
private fun renderSystemProxyPresentation(systemProxy: SystemProxyPresentation): String {
    return when (systemProxy) {
        is SystemProxyPresentation.Detected -> systemProxy.proxyConfig.url
        SystemProxyPresentation.Detecting -> "正在检测"
        SystemProxyPresentation.NotDetected -> "未检测到系统代理"
    }
}

@Composable
private fun SettingsScope.ProxyConfigPresentation(
    config: ProxySettings,
    currentTestMode: ProxyMode,
    systemProxy: SystemProxyPresentation,
    onClickEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextItem(
        modifier = modifier,
        title = {
            Text(
                if (currentTestMode == ProxyMode.DISABLED)
                    "未启用代理" else "正在使用${renderProxyConfigModeName(currentTestMode)}",
            )
        },
        description = when (currentTestMode) {
            ProxyMode.DISABLED -> null
            ProxyMode.SYSTEM -> {
                { Text(renderSystemProxyPresentation(systemProxy)) }
            }

            ProxyMode.CUSTOM -> {
                { Text(config.default.customConfig.url) }
            }
        },
        action = {
            TextButton(
                onClick = onClickEdit,
                Modifier,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("修改", softWrap = false)
            }
        },
    )
}

@Composable
private fun SettingsScope.ProxyConfigGroup(
    initialConfig: ProxySettings,
    initialTestMode: ProxyMode,
    systemProxy: SystemProxyPresentation,
    onUpdate: (mode: ProxyMode, config: ProxySettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentConfig = remember { mutableStateOf(initialConfig) } // todo: rememberSavable
    var currentMode by remember { mutableStateOf(initialTestMode) }

    val scope = rememberCoroutineScope()
    val currentConfigState = remember {
        SettingsState(
            valueState = currentConfig,
            onUpdate = { currentConfig.value = it },
            placeholder = ProxySettings.Default,
            backgroundScope = scope,
        )
    }

    Group(
        title = { Text("代理设置") },
        useThinHeader = true,
    ) {
        ProxyMode.entries.forEach { mode ->
            val interactionSource = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = { Text(renderProxyConfigModeName(mode)) },
                leadingContent = {
                    RadioButton(
                        selected = currentMode == mode,
                        onClick = { currentMode = mode },
                        interactionSource = interactionSource,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = { currentMode = mode },
                    ),
            )
            AnimatedVisibility(visible = currentMode == mode) {
                Column {
                    when (mode) {
                        ProxyMode.DISABLED -> {}
                        ProxyMode.SYSTEM -> {
                            SystemProxyConfig(systemProxy)
                        }

                        ProxyMode.CUSTOM -> {
                            CustomProxyConfig(currentConfig.value, currentConfigState)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SelectProxy(
    config: ProxySettings,
    testRunning: Boolean,
    currentTestMode: ProxyMode,
    systemProxy: SystemProxyPresentation,
    testItems: List<ProxyTestItem>,
    onUpdate: (mode: ProxyMode, config: ProxySettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingProxy by rememberSaveable { mutableStateOf(false) }

    SettingsTab(modifier = modifier) {
        ProxyTestStatusGroup(testRunning, currentTestMode, testItems) {
            testItems.forEach { item ->
                ProxyTestItemView(item, modifier = Modifier)
            }
        }

        Crossfade(
            editingProxy,
            modifier = Modifier.animateContentSize(),
        ) {
            if (it) ProxyConfigGroup(
                config,
                currentTestMode,
                systemProxy,
                onUpdate = onUpdate,
            ) else ProxyConfigPresentation(
                config,
                currentTestMode,
                systemProxy,
                onClickEdit = { editingProxy = true },
            )
        }

    }
}