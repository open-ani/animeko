/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import me.him188.ani.utils.coroutines.update


@Composable
fun rememberWizardController(): WizardController {
    return remember { WizardController() }
}

/**
 * 向导界面 [WizardNavHost] 的控制器, 存储向导的状态和步骤
 *
 * @param onFinish 所有步骤完成后的回调, 会传入所有设置的步骤数据. 需要结束向导页
 */
@Stable
class WizardController() {
    private val _controller = MutableStateFlow<NavHostController?>(null)

    private val steps = MutableStateFlow(emptyMap<String, WizardStep>())
    private val currentStepKey: MutableStateFlow<String?> = MutableStateFlow(null)

    val navController: StateFlow<NavHostController?> = _controller

    val state: Flow<WizardState?> = combine(
        steps,
        currentStepKey,
    ) { steps, currentStepKey ->
        val currentStep = steps[currentStepKey] ?: return@combine null
        WizardState(
            steps = steps.map { it.value },
            currentStep = currentStep,
            currentStepIndex = steps.entries.indexOfFirst { it.key == currentStepKey },
            stepCount = steps.size,
        )
    }

    fun setNavController(controller: NavHostController) {
        _controller.update { controller }
    }

    fun setupSteps(steps: Map<String, WizardStep>) {
        this.steps.update { steps }
        if (currentStepKey.value == null || steps[currentStepKey.value] == null) {
            currentStepKey.update { steps.entries.firstOrNull()?.key }
        }
    }

    @Composable
    fun startDestinationAsState(): State<String?> {
        val stepLine by steps.collectAsState()
        return remember {
            derivedStateOf { stepLine.entries.firstOrNull()?.key }
        }
    }

    fun goForward() {
        move(forward = true)
    }

    fun goBackward() {
        move(forward = false)
    }

    private fun move(forward: Boolean) {
        val navController = navController.value ?: return
        val currentStepKey = currentStepKey.value ?: return
        val stepEntries = steps.value.entries.toList()
        val currentStepIndex = stepEntries.indexOfFirst { it.key == currentStepKey }

        if (currentStepIndex == 0 && !forward) return
        if (currentStepIndex == stepEntries.size - 1 && forward) return

        val targetStepKey = stepEntries
            .getOrNull(currentStepIndex + (if (forward) 1 else -1))?.value?.key ?: return
        val targetStep = steps.value[targetStepKey] ?: return

        this.currentStepKey.update { targetStepKey }

        val prevBackEntry = navController.previousBackStackEntry
        if (!forward
            && prevBackEntry != null
            && prevBackEntry.destination.route == targetStepKey
        ) {
            navController.popBackStack(targetStepKey, false)
        } else {
            navController.navigate(targetStep.key)
        }
    }
}

@Stable
class WizardState(
    val steps: List<WizardStep>,
    val currentStep: WizardStep,
    val currentStepIndex: Int,
    val stepCount: Int,
)