/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
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
    indicatorBar: @Composable (WizardState) -> Unit = {
        WizardDefaults.StepTopAppBar(
            currentStep = it.currentStepIndex + 1,
            totalStep = it.stepCount,
        ) {
            it.currentStep.stepName.invoke()
        }
    },
    controlBar: @Composable (WizardState) -> Unit = {
        WizardDefaults.StepControlBar(
            forwardAction = { it.currentStep.forwardButton.invoke() },
            backwardAction = { it.currentStep.backwardButton.invoke() },
            tertiaryAction = { it.currentStep.skipButton.invoke() },
        )
    },
    motionScheme: NavigationMotionScheme = WizardDefaults.motionScheme,
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

    val wizardState = controller.state.collectAsState(null).value ?: return

    Scaffold(
        topBar = { indicatorBar(wizardState) },
        bottomBar = { controlBar(wizardState) },
        modifier = modifier,
        contentWindowInsets = windowInsets,
    ) { contentPadding ->
        val currentNavController =
            controller.navController.collectAsState().value ?: return@Scaffold
        val startDestination = controller.startDestinationAsState().value ?: return@Scaffold

        NavHost(
            currentNavController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize(),
            enterTransition = { motionScheme.enterTransition },
            exitTransition = { motionScheme.exitTransition },
            popEnterTransition = { motionScheme.popEnterTransition },
            popExitTransition = { motionScheme.popExitTransition },
        ) {
            wizardState.steps.forEach { step ->
                composable(step.key) {
                    Column(Modifier.fillMaxSize()) {
                        step.content.invoke()
                    }
                }
            }
        }
    }
}