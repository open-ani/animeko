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
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import me.him188.ani.utils.coroutines.mapAsStateFlow
import me.him188.ani.utils.coroutines.update


@Composable
fun rememberWizardController(
    onFinish: (Map<String, WizardStepData>) -> Unit
): WizardController {
    val onFinishState = rememberUpdatedState(onFinish)
    return remember(onFinishState) { WizardController(onFinishState.value) }
}

/**
 * 向导界面 [WizardNavHost] 的控制器, 存储向导的状态和步骤
 *
 * @param onFinish 所有步骤完成后的回调, 会传入所有设置的步骤数据. 需要结束向导页
 */
@Stable
class WizardController(
    private val onFinish: (Map<String, WizardStepData>) -> Unit = {},
) {
    private val _controller = MutableStateFlow<NavHostController?>(null)

    private val _steps = MutableStateFlow(emptyMap<String, WizardStep<Any>>())
    private val stepEntries = _steps.mapAsStateFlow(Dispatchers.Main) { it.entries.toList() }

    private val _currentStep: MutableStateFlow<WizardStep<Any>?> = MutableStateFlow(null)
    private val skippedSteps = mutableSetOf<String>()

    val navController: StateFlow<NavHostController?> = _controller
    val skipState = SkipState()

    val steps: StateFlow<Map<String, WizardStep<Any>>> = _steps
    val currentStep: StateFlow<WizardStep<Any>?> = _currentStep

    val totalStepIndex: Flow<Int?> = stepEntries.map { it.size }
    val currentStepIndex: Flow<Int?> = _currentStep
        .combine(stepEntries) { currentStep, stepEntries ->
            stepEntries.indexOfFirst { it.value.key == currentStep?.key } + 1
        }

    fun setNavController(controller: NavHostController) {
        _controller.update { controller }
    }

    fun setupSteps(steps: Map<String, WizardStep<Any>>) {
        _steps.update { steps }
        _currentStep.update { steps.entries.firstOrNull()?.value }
    }

    @Composable
    fun startDestinationAsState(): State<String?> {
        val stepLine by stepEntries.collectAsState()
        return derivedStateOf { stepLine.firstOrNull()?.key }
    }

    fun updateCurrentStepData(data: Any) {
        val currentStep = _currentStep.value ?: return
        if (currentStep.data::class != data::class) return
        
        val newStep = currentStep.copy(data = data)
        _currentStep.update { newStep }
    }

    fun goForward() {
        val currentStep = _currentStep.value ?: return
        if (currentStep.canForward(currentStep.data)) {
            skippedSteps.remove(currentStep.key)
            move(forward = true)
        }
    }

    fun goBackward() {
        move(forward = false)
    }

    suspend fun requestSkip() {
        val confirm = skipState.awaitConfirmation()
        if (confirm) {
            val currentStep = _currentStep.value ?: return
            skippedSteps.add(currentStep.key)
            move(forward = true)
        }
    }

    private fun saveCurrentStep() {
        val currentStep = _currentStep.value ?: return
        _steps.update {
            toMutableMap()
                .apply { put(currentStep.key, currentStep) }
        }
    }

    private fun buildResultMap(): Map<String, WizardStepData> {
        return buildMap {
            steps.value.forEach { (key, step) ->
                put(key, WizardStepData(step.data, skippedSteps.contains(key)))
            }
        }
    }

    private fun move(forward: Boolean) {
        val navController = navController.value ?: return
        val currentStepKey = _currentStep.value?.key ?: return
        val stepEntries = stepEntries.value
        val currentStepIndex = stepEntries.indexOfFirst { it.value.key == currentStepKey }

        saveCurrentStep()

        if (currentStepIndex == 0 && !forward) return
        if (currentStepIndex == stepEntries.size - 1 && forward) {
            onFinish(buildResultMap())
            return
        }

        val targetStepKey = stepEntries
            .getOrNull(currentStepIndex + (if (forward) 1 else -1))?.value?.key ?: return
        val targetStep = _steps.value[targetStepKey] ?: return

        _currentStep.update { targetStep }

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

class SkipState() {
    private val deferred = MutableStateFlow(CompletableDeferred(false))
    private val _waitingConfirmation = MutableStateFlow(false)
    val waitingConfirmation: Flow<Boolean> = _waitingConfirmation

    suspend fun awaitConfirmation(): Boolean {
        val currentDeferred = deferred.value
        return if (currentDeferred.isCompleted) {
            val newDeferred = CompletableDeferred<Boolean>()
            deferred.update { newDeferred }
            _waitingConfirmation.update { true }
            newDeferred.await()
        } else {
            currentDeferred.await()
        }
    }

    fun confirm(skip: Boolean) {
        val currentDeferred = deferred.value
        if (currentDeferred.isActive) {
            _waitingConfirmation.update { false }
            currentDeferred.complete(skip)
        }
    }
}