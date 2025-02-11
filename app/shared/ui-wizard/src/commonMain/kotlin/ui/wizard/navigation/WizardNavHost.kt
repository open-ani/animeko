/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.navigation

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.animation.LocalNavigationMotionScheme
import me.him188.ani.app.ui.foundation.animation.NavigationMotionScheme
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor

/**
 * A wrapper around [NavHost] that provides a wizard-like experience.
 * Which only provides linear and ordered navigation.
 *
 * WizardNavHost also provides a top bar and bottom bar for the wizard.
 */
@Composable
fun WizardNavHost(
    controller: WizardController,
    modifier: Modifier = Modifier,
    motionScheme: NavigationMotionScheme = LocalNavigationMotionScheme.current,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    content: WizardNavHostScope.() -> Unit,
) {
    val navController = rememberNavController()
    controller.setNavController(navController)

    val topAppBarState = rememberTopAppBarState()
    var contentCanScrollForward by rememberSaveable { mutableStateOf(false) }
    var contentCanScrollBackward by rememberSaveable { mutableStateOf(false) }
    val indicatorBarScrollable by derivedStateOf {
        (contentCanScrollForward && contentCanScrollBackward) || topAppBarState.collapsedFraction != 0f
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = topAppBarState,
        canScroll = { indicatorBarScrollable },
    )

    DisposableEffect(controller, content) {
        val steps = WizardNavHostScope(controller).apply(content).build()
        controller.setupSteps(steps)
        onDispose { }
    }

    LaunchedEffect(Unit) {
        controller.subscribeNavDestChanges {
            launch { controller.animateScrollUpTopAppBar(topAppBarState) }
        }
    }

    val wizardState = controller.state.collectAsState(null).value ?: return
    val currentNavController = controller.navController.collectAsState().value ?: return
    val startDestination = controller.startDestinationAsState().value ?: return

    NavHost(
        currentNavController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { motionScheme.enterTransition },
        exitTransition = { motionScheme.exitTransition },
        popEnterTransition = { motionScheme.popEnterTransition },
        popExitTransition = { motionScheme.popExitTransition },
    ) {
        val stepCount = wizardState.steps.size
        wizardState.steps.forEachIndexed { index, step ->
            composable(step.key) {
                val scrollState = rememberScrollState()

                val indicatorState = remember(
                    stepCount,
                    index,
                    scrollBehavior,
                    topAppBarState.collapsedFraction,
                ) {
                    WizardIndicatorState(
                        currentStep = index + 1,
                        totalStep = stepCount,
                        scrollBehavior = scrollBehavior,
                        scrollCollapsedFraction = topAppBarState.collapsedFraction,
                    )
                }

                key(scrollState.canScrollForward) {
                    contentCanScrollForward = scrollState.canScrollForward
                }
                key(scrollState.canScrollBackward) {
                    contentCanScrollBackward = scrollState.canScrollBackward
                }

                Scaffold(
                    topBar = { step.indicatorBar(indicatorState) },
                    bottomBar = {
                        if (scrollState.canScrollForward) {
                            HorizontalDivider(Modifier.fillMaxWidth())
                        }
                        step.controlBar()
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = windowInsets,
                ) { contentPadding ->
                    val scope = remember(scrollState, topAppBarState) {
                        WizardStepScope(
                            scrollState,
                            scrollUpTopAppBar = { controller.animateScrollUpTopAppBar(topAppBarState) },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .verticalScroll(scrollState),
                    ) {
                        step.content(scope)
                    }
                }
            }
        }
    }
}

object WizardDefaults {
    fun renderStepIndicatorText(currentStep: Int, totalStep: Int): String {
        return "步骤 $currentStep / $totalStep"
    }

    @Composable
    fun StepTopAppBar(
        currentStep: Int,
        totalStep: Int,
        modifier: Modifier = Modifier,
        navigationIcon: @Composable () -> Unit,
        windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
        indicatorStepTextTestTag: String = "indicatorText",
        scrollBehavior: TopAppBarScrollBehavior? = null,
        scrollCollapsedFraction: Float = 0f,
        stepName: @Composable () -> Unit,
    ) {
        LargeTopAppBar(
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProvideTextStyleContentColor(
                        value = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        if (scrollCollapsedFraction <= 0.5) {
                            Text(
                                text = remember(currentStep, totalStep) {
                                    renderStepIndicatorText(currentStep, totalStep)
                                },
                                modifier = Modifier.testTag(indicatorStepTextTestTag),
                            )
                        }
                    }
                    stepName()
                }
            },
            modifier = modifier,
            navigationIcon = navigationIcon,
            scrollBehavior = scrollBehavior,
            windowInsets = windowInsets,
        )
    }

    @Composable
    fun StepControlBar(
        forwardAction: @Composable () -> Unit,
        backwardAction: @Composable () -> Unit,
        skipAction: @Composable () -> Unit,
        modifier: Modifier = Modifier,
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
                Row(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    backwardAction()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        skipAction()
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
        modifier: Modifier = Modifier,
        text: String = "下一步"
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(text)
        }
    }

    @Composable
    fun GoBackwardButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        text: String = "上一步"
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
        ) {
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