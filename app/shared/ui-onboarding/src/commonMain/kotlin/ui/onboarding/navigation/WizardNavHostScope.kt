/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton

@DslMarker
annotation class WizardStepDsl

class WizardNavHostScope(
    private val controller: WizardController,
) {
    private val steps: LinkedHashMap<String, WizardStep> = linkedMapOf()

    /**
     * Add a step to the wizard.
     *
     * @param autoSkip If true, the step will be skipped from last step by [WizardController.goForward].
     *   Not that the the first step cannot be skipped.
     */
    @WizardStepDsl
    fun step(
        key: String,
        title: @Composable () -> Unit,
        autoSkip: () -> Boolean = { false },
        forwardButton: @Composable () -> Unit = {
            val scope = rememberCoroutineScope()
            WizardDefaults.GoForwardButton(
                {
                    scope.launch {
                        controller.goForward()
                    }
                },
                enabled = true,
                modifier = Modifier.testTag("buttonNextStep"),
            )
        },
        skipButton: @Composable () -> Unit = {
            val scope = rememberCoroutineScope()
            WizardDefaults.SkipButton(
                {
                    scope.launch {
                        controller.goForward()
                    }
                },
                modifier = Modifier.testTag("buttonSkipStep"),
            )
        },
        navigationIcon: @Composable () -> Unit = {
            val scope = rememberCoroutineScope()
            BackNavigationIconButton(
                onNavigateBack = {
                    scope.launch {
                        controller.goBackward()
                    }
                },
                modifier = Modifier.testTag("buttonPrevStep"),
            )
        },
        indicatorBar: @Composable (WizardIndicatorState, WindowInsets) -> Unit = { state, insets ->
            WizardDefaults.StepTopAppBar(
                currentStep = state.currentStep,
                totalStep = state.totalStep,
                scrollBehavior = state.scrollBehavior,
                navigationIcon = navigationIcon,
                actionButton = skipButton,
                collapsedFraction = state.topAppBarCollapsedFraction,
                windowInsets = insets
            ) {
                title()
            }
        },
        controlBar: @Composable (WindowInsets) -> Unit = { insets ->
            WizardDefaults.StepControlBar(
                forwardAction = forwardButton,
                windowInsets = insets
            )
        },
        content: @Composable WizardStepScope.() -> Unit,
    ) {
        if (steps[key] != null) {
            throw IllegalArgumentException("Duplicate step key: $key")
        }
        steps[key] = WizardStep(
            key = key,
            stepName = title,
            autoSkip = autoSkip,
            backwardButton = navigationIcon,
            skipButton = skipButton,
            indicatorBar = indicatorBar,
            controlBar = controlBar,
            content = content,
        )
    }

    fun build(): Map<String, WizardStep> {
        return steps
    }
}