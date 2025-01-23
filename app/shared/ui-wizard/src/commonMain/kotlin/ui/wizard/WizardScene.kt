/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.foundation.theme.NavigationMotionScheme
import me.him188.ani.app.ui.wizard.navigation.WizardController
import me.him188.ani.app.ui.wizard.navigation.WizardNavHost
import me.him188.ani.app.ui.wizard.step.BitTorrentFeature
import me.him188.ani.app.ui.wizard.step.ConfigureProxy
import me.him188.ani.app.ui.wizard.step.ProxyTestCaseState
import me.him188.ani.app.ui.wizard.step.SelectTheme

@Composable
internal fun WizardScene(
    controller: WizardController,
    state: WizardPresentationState,
    modifier: Modifier = Modifier,
    useEnterAnim: Boolean = true,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    wizardLayoutParams: WizardLayoutParams = WizardLayoutParams.Default
) {
    val context = LocalContext.current
    var barVisible by rememberSaveable { mutableStateOf(!useEnterAnim) }

    DisposableEffect(Unit) {
        barVisible = true
        onDispose { }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        WizardNavHost(
            controller,
            modifier = Modifier
                .windowInsetsPadding(windowInsets)
                .fillMaxSize(),
            indicatorBar = {
                AnimatedVisibility(
                    barVisible,
                    enter = WizardDefaults.indicatorBarEnterAnim,
                ) {
                    WizardDefaults.StepTopAppBar(
                        currentStep = it.currentStepIndex + 1,
                        totalStep = it.stepCount,
                    ) {
                        it.currentStep.stepName.invoke()
                    }
                }
            },
            controlBar = {
                AnimatedVisibility(
                    barVisible,
                    enter = WizardDefaults.controlBarEnterAnim,
                ) {
                    WizardDefaults.StepControlBar(
                        forwardAction = { it.currentStep.forwardButton.invoke() },
                        backwardAction = { it.currentStep.backwardButton.invoke() },
                        tertiaryAction = { it.currentStep.skipButton.invoke() },
                    )
                }
            },
        ) {
            step("theme", { Text("选择主题") }) {
                SelectTheme(
                    config = state.selectThemeState.value,
                    onUpdate = { state.selectThemeState.update(it) },
                    modifier = Modifier.fillMaxSize(),
                    layoutParams = wizardLayoutParams,
                )
            }
            step(
                "proxy",
                title = { Text("设置代理") },
                forward = {
                    WizardDefaults.GoForwardButton(
                        { controller.goForward() },
                        enabled = state.configureProxyState.testState.items.value
                            .all { it.state == ProxyTestCaseState.SUCCESS },
                    )
                },
                skipButton = { WizardDefaults.SkipButton({ controller.goForward() }) },
            ) {
                val configureProxyState = state.configureProxyState
                ConfigureProxy(
                    config = configureProxyState.configState.value,
                    onUpdate = { config ->
                        configureProxyState.configState.update(config)
                    },
                    testRunning = configureProxyState.testState.testRunning.value,
                    systemProxy = configureProxyState.systemProxy.value,
                    testItems = configureProxyState.testState.items.value,
                    modifier = Modifier.fillMaxSize(),
                    layoutParams = wizardLayoutParams,
                )
            }
            step("bittorrent", { Text("BitTorrent 功能") }) {
                BitTorrentFeature(
                    bitTorrentEnabled = state.bitTorrentFeatureState.enabled.value,
                    grantedNotificationPermission =
                    state.bitTorrentFeatureState.notificationPermissionState.granted.value,
                    lastRequestNotificationPermissionResult =
                    state.bitTorrentFeatureState.notificationPermissionState.lastRequestResult.value,
                    onBitTorrentEnableChanged = { state.bitTorrentFeatureState.enabled.update(it) },
                    onRequestNotificationPermission = {
                        state.bitTorrentFeatureState.onRequestNotificationPermission(context)
                    },
                    modifier = Modifier.fillMaxSize(),
                    layoutParams = wizardLayoutParams,
                )
            }
        }
    }
}


object WizardDefaults {
    val controlBarEnterAnim = fadeIn(animationSpec = tween(200)) +
            slideIn(animationSpec = tween(200), initialOffset = { IntOffset(0, it.height) })

    val indicatorBarEnterAnim = fadeIn(animationSpec = tween(200)) +
            slideIn(animationSpec = tween(200), initialOffset = { IntOffset(0, -it.height) })
    
    val motionScheme = kotlin.run {
        val slideEnter = 350
        val fadeEnter = 350
        val slideExit = 200
        val fadeExit = 200

        NavigationMotionScheme(
            enterTransition = fadeIn(animationSpec = tween(fadeEnter, slideEnter - fadeEnter)) +
                    slideIn(tween(slideEnter)) {
                        IntOffset((it.width * 0.15).toInt(), 0)
                    },
            exitTransition = fadeOut(animationSpec = tween(fadeExit, slideExit - fadeExit)) +
                    slideOut(tween(slideExit)) {
                        IntOffset(-(it.width * 0.15).toInt(), 0)
                    },
            popEnterTransition = fadeIn(animationSpec = tween(fadeEnter, slideEnter - fadeEnter)) +
                    slideIn(tween(slideEnter)) {
                        IntOffset(-(it.width * 0.15).toInt(), 0)
                    },
            popExitTransition = fadeOut(animationSpec = tween(fadeExit, slideExit - fadeExit)) +
                    slideOut(tween(slideExit)) {
                        IntOffset((it.width * 0.15).toInt(), 0)
                    },
        )
    }

    fun renderStepIndicatorText(currentStep: Int, totalStep: Int): String {
        return "步骤 $currentStep / $totalStep"
    }
    
    @Composable
    fun StepTopAppBar(
        currentStep: Int,
        totalStep: Int,
        modifier: Modifier = Modifier,
        windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
        indicatorStepTextTestTag: String = "indicatorText",
        stepName: @Composable () -> Unit,
    ) {
        LargeTopAppBar(
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProvideTextStyleContentColor(
                        value = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = remember(currentStep, totalStep) {
                                renderStepIndicatorText(currentStep, totalStep)
                            },
                            modifier = Modifier.testTag(indicatorStepTextTestTag),
                        )
                    }
                    stepName()
                }
            },
            modifier = modifier,
            windowInsets = windowInsets,

            )
    }

    @Composable
    fun StepControlBar(
        forwardAction: @Composable () -> Unit,
        backwardAction: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        tertiaryAction: @Composable () -> Unit = {},
        windowInsets: WindowInsets = AniWindowInsets.forNavigationBar(),
    ) {
        Box(modifier = modifier) {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(
                        windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    )
                    .fillMaxWidth(),
            ) {
                HorizontalDivider(Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    backwardAction()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tertiaryAction()
                        forwardAction()
                    }
                }
            }
        }
    }

    @Composable
    fun GoForwardButton(
        onClick: () -> Unit,
        enabled: Boolean,
        text: String = "继续"
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
        ) {
            Text(text)
        }
    }

    @Composable
    fun GoBackwardButton(
        onClick: () -> Unit,
        text: String = "上一步"
    ) {
        TextButton(onClick = onClick) {
            Text(text)
        }
    }

    @Composable
    fun SkipButton(
        onClick: () -> Unit,
        text: String = "跳过"
    ) {
        TextButton(onClick = onClick) {
            Text(text)
        }
    }
}