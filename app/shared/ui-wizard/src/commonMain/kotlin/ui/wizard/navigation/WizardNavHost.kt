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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.wizard.WizardDefaults

@Composable
fun WizardNavHost(
    controller: WizardController,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    builder: WizardBuilder.() -> Unit,
) {
    val navController = rememberNavController()
    controller.setNavController(navController)

    DisposableEffect(Unit) {
        val steps = WizardBuilder(controller).apply(builder).build()
        controller.setupSteps(steps)
        onDispose { }
    }

    val totalStepIndex = controller.totalStepIndex.collectAsState(null).value ?: return
    val currentStep = controller.currentStep.collectAsState(null).value ?: return
    val currentStepIndex = controller.currentStepIndex.collectAsState(null).value ?: return

    Scaffold(
        topBar = {
            WizardDefaults.StepTopAppBar(
                currentStep = currentStepIndex,
                totalStep = totalStepIndex,
            ) {
                val currentStepName by rememberUpdatedState(currentStep.stepName)
                currentStepName(currentStep.data)
            }
        },
        bottomBar = {
            WizardDefaults.StepControlBar(
                forwardAction = {
                    val canForward = remember(currentStep.data) {
                        currentStep.canForward(currentStep.data)
                    }
                    currentStep.forwardButton.invoke(canForward)
                },
                backwardAction = {
                    currentStep.backwardButton.invoke()
                },
                tertiaryAction = {
                    currentStep.skipButton.invoke()
                },
            )
        },
        modifier = modifier,
        contentWindowInsets = windowInsets,
    ) { contentPadding ->
        val currentNavController =
            controller.navController.collectAsState().value ?: return@Scaffold
        val startDestination = controller.startDestinationAsState().value ?: return@Scaffold
        val steps by controller.steps.collectAsState()

        NavHost(
            currentNavController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize(),
        ) {
            steps.forEach { (key, step) ->
                composable(key) { backStackEntry ->
                    val currentKey = backStackEntry.destination.route ?: return@composable

                    val indexedStep = remember(steps, currentKey) {
                        steps[currentKey]
                    }
                    val currentOrSnapshotStep by derivedStateOf {
                        if (indexedStep?.key == currentStep.key) currentStep else indexedStep
                    }
                    val finalStep = currentOrSnapshotStep ?: return@composable
                    val requestedSkip by controller.skipState.waitingConfirmation
                        .collectAsState(false)

                    val configState = remember(finalStep, requestedSkip) {
                        WizardStepScope(
                            finalStep.data,
                            requestedSkip,
                            onUpdate = { controller.updateCurrentStepData(it) },
                            onConfirmSkip = { controller.skipState.confirm(it) },
                        )
                    }

                    Column(Modifier.fillMaxSize()) {
                        step.content(configState)
                    }
                }
            }
        }
    }
}