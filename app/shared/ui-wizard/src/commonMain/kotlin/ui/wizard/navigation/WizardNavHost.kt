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
import androidx.compose.material3.Text
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

@Suppress("LocalVariableName")
@Composable
fun WizardNavHost(
    controller: WizardController,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    builder: WizardBuilder.() -> Unit,
) {
    val navController = rememberNavController()
    controller.setNavController(navController)

    DisposableEffect(controller, builder) {
        WizardBuilder(controller).apply(builder).build()
        onDispose {}
    }

    val _currentStepIndex by controller.currentStepIndex.collectAsState(null)
    val _totalStepIndex by controller.totalStepIndex.collectAsState(null)
    val _currentStep by controller.currentStep.collectAsState(null)

    val currentStepIndex = _currentStepIndex ?: return
    val totalStepIndex = _totalStepIndex ?: return
    val currentStep = _currentStep ?: return

    Scaffold(
        topBar = {
            WizardDefaults.StepTopAppBar(
                currentStep = currentStepIndex,
                totalStep = totalStepIndex,
            ) {
                val currentStepName by rememberUpdatedState(currentStep.stepName)
                Text(currentStepName)
            }
        },
        bottomBar = {
            WizardDefaults.StepControlBar(
                forwardAction = {
                    val canForward = remember(currentStep.data) {
                        currentStep.canForward(currentStep.data)
                    }
                    val currentForwardButton by rememberUpdatedState(currentStep.forwardButton)
                    currentForwardButton(canForward)
                },
                backwardAction = {
                    val currentBackwardButton by rememberUpdatedState(currentStep.backwardButton)
                    currentBackwardButton()
                },
                tertiaryAction = {
                    val currentSkippable by rememberUpdatedState(currentStep.skippable)
                    val currentSkipButton by rememberUpdatedState(currentStep.skipButton)
                    if (currentSkippable) {
                        currentSkipButton()
                    }
                },
            )
        },
        modifier = modifier,
        contentWindowInsets = windowInsets,
    ) { contentPadding ->
        val _navController by controller.navController.collectAsState()
        val currentNavController = _navController ?: return@Scaffold
        val startDestination = controller.startDestination() ?: return@Scaffold
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
                        WizardConfigState(
                            finalStep.data,
                            requestedSkip,
                            onUpdate = { controller.updateCurrentStepData(it) },
                            onConfirmSkip = { controller.skipState.confirm(it) },
                        )
                    }

                    Column(Modifier.fillMaxSize()) {
                        step.content(this, configState)
                    }
                }
            }
        }
    }
}