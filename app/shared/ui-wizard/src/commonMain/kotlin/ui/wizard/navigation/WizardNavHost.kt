/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.navigation

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.theme.NavigationMotionScheme
import me.him188.ani.app.ui.wizard.WizardDefaults

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
    motionScheme: NavigationMotionScheme = WizardDefaults.motionScheme,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    content: WizardNavHostScope.() -> Unit,
) {
    val navController = rememberNavController()
    controller.setNavController(navController)

    val topAppBarState = rememberTopAppBarState()
    val lazyListState = rememberLazyListState()
    val indicatorBarScrollable by derivedStateOf {
        (lazyListState.canScrollForward && lazyListState.canScrollBackward) ||
                topAppBarState.collapsedFraction != 0f

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
            if (topAppBarState.heightOffset == 0f) return@subscribeNavDestChanges
            val animation = AnimationState(topAppBarState.heightOffset)
            animation.animateTo(0f) {
                topAppBarState.heightOffset = value
            }
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
                val indicatorState = key(
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

                Scaffold(
                    topBar = { step.indicatorBar(indicatorState) },
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = windowInsets,
                ) { contentPadding ->
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .padding(contentPadding)
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    ) {
                        item { step.content() }
                        item { HorizontalDivider(Modifier.fillMaxWidth()) }
                        item { step.controlBar() }
                    }
                }
            }
        }
    }
}