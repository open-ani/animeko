/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding.navigation

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults

/**
 * A wrapper around [NavHost] that provides a wizard-like experience.
 * Which only provides linear and ordered navigation.
 *
 * WizardNavHost also provides a top bar and bottom bar for the wizard.
 */
@Composable
fun WizardNavHost(
    controller: WizardController,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    motionScheme: NavigationMotionScheme = LocalNavigationMotionScheme.current,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    content: WizardNavHostScope.() -> Unit,
) {
    val navController = rememberNavController()
    controller.setNavController(navController)

    DisposableEffect(controller, content) {
        val steps = WizardNavHostScope(controller).apply(content).build()
        controller.setupSteps(steps)
        onDispose { }
    }

    LaunchedEffect(controller) {
        controller.collectOnCompletedEvent(onCompleted)
    }

    val wizardState = controller.state.collectAsState(null).value ?: return
    val startDestination = controller.startDestinationAsState().value ?: return

    NavHost(
        navController,
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
                val topAppBarState = rememberTopAppBarState()

                val indicatorBarScrollable by remember(scrollState, topAppBarState) {
                    derivedStateOf {
                        (scrollState.canScrollForward && scrollState.canScrollBackward) ||
                                topAppBarState.collapsedFraction != 0f
                    }
                }

                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                    state = topAppBarState,
                    canScroll = { indicatorBarScrollable },
                )

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
                        topAppBarCollapsedFraction = topAppBarState.collapsedFraction,
                    )
                }
                // scroll to top when entering the step
                LaunchedEffect(Unit) {
                    launch { animateScrollTopAppBar(topAppBarState, 0f) }
                    launch { scrollState.animateScrollTo(0) }
                }

                Scaffold(
                    topBar = { 
                        step.indicatorBar(
                            indicatorState,
                            windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        ) 
                    },
                    bottomBar = {
                        Column {
                            if (scrollState.canScrollForward) {
                                HorizontalDivider(Modifier.fillMaxWidth())
                            }
                            step.controlBar(
                                windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentWindowInsets = windowInsets,
                ) { contentPadding ->
                    val scope = remember(scrollState, topAppBarState) {
                        object : WizardStepScope {
                            override val wizardScrollState: ScrollState = scrollState
                            override suspend fun scrollTopAppBarExpanded() {
                                animateScrollTopAppBar(topAppBarState, 0f)
                            }

                            override suspend fun scrollTopAppBarCollapsed() {
                                animateScrollTopAppBar(topAppBarState, topAppBarState.heightOffsetLimit)
                            }
                        }
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

private suspend fun animateScrollTopAppBar(topAppBarState: TopAppBarState, target: Float) {
    if (topAppBarState.heightOffset == target) return
    val animation = AnimationState(topAppBarState.heightOffset)
    animation.animateTo(target) {
        topAppBarState.heightOffset = value
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
        collapsedFraction: Float,
        modifier: Modifier = Modifier,
        navigationIcon: @Composable () -> Unit,
        actionButton: @Composable () -> Unit = { },
        windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
        indicatorStepTextTestTag: String = "indicatorText",
        scrollBehavior: TopAppBarScrollBehavior? = null,
        stepName: @Composable () -> Unit,
    ) {
        LargeTopAppBar(
            title = {
                val currentCollapsedFraction by rememberUpdatedState(collapsedFraction)
                val showStepText by remember {
                    derivedStateOf { currentCollapsedFraction < 0.5 }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (showStepText) {
                        ProvideTextStyleContentColor(
                            MaterialTheme.typography.titleMedium,
                            MaterialTheme.colorScheme.primary
                        ) {
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
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AniThemeDefaults.pageContentBackgroundColor,
            ),
            actions = { actionButton() },
            scrollBehavior = scrollBehavior,
            windowInsets = windowInsets,
        )
    }

    @Composable
    fun StepControlBar(
        forwardAction: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    ) {
        NavigationBar(
            modifier,
            containerColor = Color.Transparent,
            windowInsets = windowInsets
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                forwardAction()
            }
        }
    }

    @Composable
    fun GoForwardButton(
        onClick: () -> Unit,
        enabled: Boolean,
        modifier: Modifier = Modifier,
        colors: ButtonColors = ButtonDefaults.buttonColors(),
        text: String = "下一步"
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = colors
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
        text: String = "跳过",
        modifier: Modifier = Modifier
    ) {
        TextButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(text)
        }
    }
}