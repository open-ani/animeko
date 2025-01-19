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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
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
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    content: WizardNavHostScope.() -> Unit,
) {
    val navController = rememberNavController()
    controller.setNavController(navController)

    DisposableEffect(Unit) {
        val steps = WizardNavHostScope(controller).apply(content).build()
        controller.setupSteps(steps)
        onDispose { }
    }

    val wizardState = controller.state.collectAsState(null).value ?: return

    Scaffold(
        topBar = {
            WizardDefaults.StepTopAppBar(
                currentStep = wizardState.currentStepIndex,
                totalStep = wizardState.totalStepIndex,
            ) {
                wizardState.currentStep.stepName.invoke()
            }
        },
        bottomBar = {
            WizardDefaults.StepControlBar(
                forwardAction = { wizardState.currentStep.forwardButton.invoke() },
                backwardAction = { wizardState.currentStep.backwardButton.invoke() },
                tertiaryAction = { wizardState.currentStep.skipButton.invoke() },
            )
        },
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
        ) {
            wizardState.steps.forEach { step ->
                composable(step.key) { backStackEntry ->
                    val currentKey = backStackEntry.destination.route ?: return@composable

                    val indexedStep = remember(wizardState.steps, currentKey) {
                        wizardState.steps.find { currentKey == it.key }
                    }
                    val currentOrSnapshotStep by remember {
                        derivedStateOf {
                            if (indexedStep?.key == wizardState.currentStep.key)
                                wizardState.currentStep else indexedStep
                        }
                    }
                    val finalStep = currentOrSnapshotStep ?: return@composable
                    val requestedSkip by controller.skipState.waitingConfirmation
                        .collectAsState(false)

                    val configState = remember(finalStep, requestedSkip) {
                        WizardStepScope(finalStep.defaultData)
                    }

                    Column(Modifier.fillMaxSize()) {
                        step.content(configState)
                    }
                }
            }
        }
    }
}