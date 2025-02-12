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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import me.him188.ani.utils.coroutines.update


@Composable
fun rememberWizardController(): WizardController {
    return remember { WizardController() }
}

/**
 * 向导界面 [WizardNavHost] 的控制器, 存储向导的状态和步骤
 */
@Stable
class WizardController() {
    private val navController = MutableStateFlow<NavHostController?>(null)

    private val steps = MutableStateFlow(emptyMap<String, WizardStep>())
    private val currentStepKey = combine(
        navController.filterNotNull().flatMapLatest { it.currentBackStackEntryFlow }, steps,
    ) { currentBackEntry, steps -> steps[currentBackEntry.destination.route]?.key }

    val state: Flow<WizardState?> = steps.map { step ->
        WizardState(
            steps = step.map { it.value },
        )
    }

    fun setNavController(controller: NavHostController) {
        navController.update { controller }
    }

    fun setupSteps(steps: Map<String, WizardStep>) {
        this.steps.update { steps }
    }

    @Composable
    fun startDestinationAsState(): State<String?> {
        val stepLine by steps.collectAsState()
        return remember {
            derivedStateOf { stepLine.entries.firstOrNull()?.key }
        }
    }

    suspend fun goForward() {
        move(forward = true)
    }

    suspend fun goBackward() {
        move(forward = false)
    }

    private suspend fun move(forward: Boolean) {
        val navController = navController.value ?: return
        val currentStepKey = currentStepKey.filterNotNull().first()
        val stepEntries = steps.value.entries.toList()
        val currentStepIndex = stepEntries.indexOfFirst { it.key == currentStepKey }

        val targetStepKey = stepEntries
            .getOrNull(currentStepIndex + (if (forward) 1 else -1))?.value?.key ?: return
        val targetStep = steps.value[targetStepKey] ?: return

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
    val steps: List<WizardStep>
)