/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.navigation

import androidx.annotation.UiThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import me.him188.ani.utils.coroutines.update


@Composable
fun rememberWizardController(
    onFinish: (Map<String, WizardStepData>) -> Unit
): WizardController {
    return remember { WizardController(onFinish) }
}

class WizardController(
    private val onFinish: (Map<String, WizardStepData>) -> Unit = {},
) {
    private val _controller = MutableStateFlow<NavHostController?>(null)

    private var _steps = MutableStateFlow(emptyMap<String, WizardStep<Any>>())
    private var stepLine: MutableStateFlow<List<String>> = MutableStateFlow(listOf())

    private var _currentStep: MutableStateFlow<WizardStep<Any>?> = MutableStateFlow(null)
    private val skippedSteps = mutableSetOf<String>()

    val navController: StateFlow<NavHostController?> = _controller
    val skipState = SkipState()

    val steps: StateFlow<Map<String, WizardStep<Any>>> = _steps
    val currentStep: StateFlow<WizardStep<Any>?> = _currentStep

    // step index 仅使用 stepLine 计算
    val totalStepIndex: Flow<Int?> = stepLine.map { it.size }
    val currentStepIndex: Flow<Int?> = _currentStep.combine(stepLine) { currentStep, stepLine ->
        stepLine.indexOfFirst { it == currentStep?.key } + 1
    }

    @UiThread
    fun setNavController(controller: NavHostController) {
        _controller.update { controller }
    }

    @UiThread
    fun setupSteps(
        steps: MutableMap<String, WizardStep<Any>>,
        stepLine: List<String>,
    ) {
        this._steps.update { steps }
        this.stepLine.update { stepLine }

        _currentStep.update {
            steps[stepLine.firstOrNull() ?: return@update null]
        }
    }

    @Composable
    fun startDestination(): String? {
        val stepLine by stepLine.collectAsState()
        return remember { stepLine.getOrNull(0) }
    }

    fun updateCurrentStepData(data: Any) {
        val currentStep = _currentStep.value ?: return
        if (currentStep.data::class != data::class) {

            return
        }
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
        _steps.value = _steps.value.toMutableMap()
            .apply { put(currentStep.key, currentStep) }
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
        val stepLine = stepLine.value
        val currentStepIndex = stepLine.indexOfFirst { it == currentStepKey }

        saveCurrentStep()

        if (currentStepIndex == 0 && !forward) return
        if (currentStepIndex == stepLine.lastIndex && forward) {
            onFinish(buildResultMap())
            return
        }

        val targetStepKey = stepLine.getOrNull(currentStepIndex + (if (forward) 1 else -1))
            ?: return
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