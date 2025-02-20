/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding.step

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.onboarding.ProxyTestCase
import me.him188.ani.app.ui.onboarding.ProxyTestCaseEnums
import me.him188.ani.app.ui.onboarding.WizardLayoutParams
import me.him188.ani.app.ui.onboarding.toDataSettings
import me.him188.ani.app.ui.onboarding.toUIConfig
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.tabs.network.CustomProxyConfig
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyConfig
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyPresentation

@Composable
internal fun ConfigureProxyStep(
    state: ConfigureProxyUIState,
    onUpdate: (config: ProxyUIConfig) -> Unit,
    onRequestReTest: () -> Unit,
    modifier: Modifier = Modifier,
    layoutParams: WizardLayoutParams = WizardLayoutParams.calculate(currentWindowAdaptiveInfo1().windowSizeClass),

    ) {
    val motionScheme = LocalAniMotionScheme.current
    var editingProxy by rememberSaveable { mutableStateOf(false) }

    SettingsTab(modifier = modifier) {
        Column {
            ProxyTestStatusGroup(
                state,
                onRequestReTest = onRequestReTest,
            ) {
                state.testState.items.forEach { item ->
                    ProxyTestItemView(item, modifier = Modifier)
                }
            }

            AnimatedContent(
                editingProxy,
                modifier = Modifier.animateContentSize(),
                transitionSpec = motionScheme.animatedContent.standard,
            ) { editing ->
                if (!editing) CurrentProxyTextModePresentation(
                    state,
                    onClickEdit = { editingProxy = true },
                ) else ProxyConfigGroup(
                    state,
                    onUpdate = { config ->
                        editingProxy = false
                        onUpdate(config)
                    },
                )
            }
        }
    }
}

@Composable
private fun renderOverallTestText(state: ProxyOverallTestState): String {
    return when (state) {
        ProxyOverallTestState.INIT -> "正在检测连接，请稍后"
        ProxyOverallTestState.RUNNING -> "正在检测连接，请稍后"
        ProxyOverallTestState.FAILED_NOT_PROXIED -> "部分服务连接失败，请考虑启用代理"
        ProxyOverallTestState.FAILED_PROXIED -> "部分服务连接失败，请更换代理模式或代理地址"
        ProxyOverallTestState.SUCCESS -> "所有服务连接正常"
    }
}

@Composable
private fun SettingsScope.ProxyTestStatusGroup(
    state: ConfigureProxyUIState,
    onRequestReTest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Group(
        title = {
            Text(
                text = renderOverallTestText(state.overallState),
                color = if (state.hasError)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        actions = if (state.hasError) {
            {
                TextButton(onRequestReTest) {
                    Text("重新测试")
                }
            }
        } else null,
        useThinHeader = true,
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun renderTestCaseName(case: ProxyTestCase): String {
    return when (case.name) {
        ProxyTestCaseEnums.ANI -> "Animeko"
        ProxyTestCaseEnums.BANGUMI -> "Bangumi"
        ProxyTestCaseEnums.BANGUMI_NEXT -> "Bangumi"
    }
}

@Composable
private fun renderTestCaseDescription(case: ProxyTestCase): String {
    return when (case.name) {
        ProxyTestCaseEnums.ANI -> "弹幕服务"
        ProxyTestCaseEnums.BANGUMI -> "收藏数据服务"
        ProxyTestCaseEnums.BANGUMI_NEXT -> "评论服务"
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
                tint = item.case.color,
                contentDescription = renderTestCaseDescription(item.case),
            )
        },
        action = { ProxyTestStatusIcon(item.state) },
    )
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
                    Icon(Icons.Default.Check, "使用 ${state.name} 连接成功")
                }

            ProxyTestCaseState.FAILED ->
                ProvideContentColor(MaterialTheme.colorScheme.error) {
                    Icon(Icons.Default.Close, "使用 ${state.name} 连接失败")
                }
        }
    }
}

@Composable
private fun renderProxyConfigModeName(mode: ProxyUIMode): String {
    return when (mode) {
        ProxyUIMode.DISABLED -> "不使用代理"
        ProxyUIMode.SYSTEM -> "系统代理"
        ProxyUIMode.CUSTOM -> "自定义代理"
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
private fun SettingsScope.CurrentProxyTextModePresentation(
    state: ConfigureProxyUIState,
    onClickEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextItem(
        modifier = modifier,
        title = {
            Text(
                if (state.config.mode == ProxyUIMode.DISABLED)
                    "未启用代理" else "正在使用${renderProxyConfigModeName(state.config.mode)}",
            )
        },
        description = when (state.config.mode) {
            ProxyUIMode.DISABLED -> null

            ProxyUIMode.SYSTEM -> {
                { Text(renderSystemProxyPresentation(state.systemProxy)) }
            }

            ProxyUIMode.CUSTOM -> {
                { Text(state.config.manualUrl) }
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
    state: ConfigureProxyUIState,
    onUpdate: (config: ProxyUIConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val motionScheme = LocalAniMotionScheme.current

    val currentConfig = remember(state.config) {
        mutableStateOf(state.config).let { config ->
            SettingsState(
                valueState = config,
                onUpdate = { config.value = it },
                placeholder = ProxyUIConfig.Default,
                backgroundScope = scope,
            )
        }
    }

    Group(
        title = { Text("代理设置") },
        actions = {
            TextButton({ onUpdate(currentConfig.value) }) {
                Text("保存并测试")
            }
        },
        useThinHeader = true,
        modifier = modifier,
    ) {
        ProxyUIMode.entries.forEach { mode ->
            val interactionSource = remember { MutableInteractionSource() }
            val updateCurrentMode = { currentConfig.update(currentConfig.value.copy(mode = mode)) }
            TextItem(
                title = { Text(renderProxyConfigModeName(mode)) },
                icon = {
                    RadioButton(
                        selected = currentConfig.value.mode == mode,
                        onClick = updateCurrentMode,
                        interactionSource = interactionSource,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = updateCurrentMode,
                    ),
            )
            AnimatedVisibility(
                visible = currentConfig.value.mode == mode,
                enter = motionScheme.animatedVisibility.columnEnter,
                exit = motionScheme.animatedVisibility.columnExit,
            ) {
                Column {
                    when (mode) {
                        ProxyUIMode.DISABLED -> {}
                        ProxyUIMode.SYSTEM -> {
                            SystemProxyConfig(state.systemProxy)
                        }

                        ProxyUIMode.CUSTOM -> {
                            // workaround for re-use CustomProxyConfig: Settings UI has no data layer of UI.
                            val workaroundDataConfig = remember(currentConfig.value) {
                                SettingsState(
                                    valueState = derivedStateOf { currentConfig.value.toDataSettings() },
                                    onUpdate = { currentConfig.update(it.toUIConfig()) },
                                    placeholder = ProxySettings.Default,
                                    backgroundScope = scope,
                                )
                            }
                            CustomProxyConfig(workaroundDataConfig.value, workaroundDataConfig)
                        }
                    }
                }
            }
        }
    }
}

@Immutable
enum class ProxyUIMode {
    DISABLED, SYSTEM, CUSTOM
}

@Immutable
data class ProxyUIConfig(
    val mode: ProxyUIMode,
    val manualUrl: String,
    val manualUsername: String?,
    val manualPassword: String?,
) {
    companion object {
        @Stable
        val Default = ProxyUIConfig(ProxyUIMode.DISABLED, "", "", "")
    }
}

@Immutable
enum class ProxyOverallTestState {
    INIT,
    RUNNING,
    FAILED_NOT_PROXIED,
    FAILED_PROXIED,
    SUCCESS
}

@Immutable
enum class ProxyTestCaseState {
    INIT,
    RUNNING,
    SUCCESS,
    FAILED
}

@Immutable
class ProxyTestItem(
    val case: ProxyTestCase,
    val state: ProxyTestCaseState,
)

@Immutable
class ProxyTestState(
    val testRunning: Boolean,
    val items: List<ProxyTestItem>
) {
    companion object {
        @Stable
        val Default = ProxyTestState(false, emptyList())
    }
}

@Immutable
class ConfigureProxyUIState(
    val config: ProxyUIConfig,
    val systemProxy: SystemProxyPresentation,
    val testState: ProxyTestState,
) {
    // when any of params in constructor changes, this will always be recalculated
    // since this class is immutable
    val overallState by derivedStateOf {
        if (testState.testRunning) {
            ProxyOverallTestState.RUNNING
        } else if (testState.items.any { it.state == ProxyTestCaseState.FAILED }) {
            if (config.mode == ProxyUIMode.DISABLED) {
                ProxyOverallTestState.FAILED_NOT_PROXIED
            } else {
                ProxyOverallTestState.FAILED_PROXIED
            }
        } else if (testState.items.all { it.state == ProxyTestCaseState.SUCCESS }) {
            ProxyOverallTestState.SUCCESS
        } else {
            ProxyOverallTestState.INIT
        }
    }

    val hasError by derivedStateOf {
        !testState.testRunning && testState.items.any { it.state == ProxyTestCaseState.FAILED }
    }

    companion object {
        @Stable
        val Placeholder = ConfigureProxyUIState(
            ProxyUIConfig.Default,
            SystemProxyPresentation.Detecting,
            ProxyTestState.Default,
        )
    }
}