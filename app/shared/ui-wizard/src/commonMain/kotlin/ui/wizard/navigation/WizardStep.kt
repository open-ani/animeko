/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.navigation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

class WizardStep<T : Any>(
    val key: String,
    val stepName: String,
    val data: T,
    val canForward: (T) -> Boolean,
    val skippable: Boolean,
    val forwardButton: @Composable (Boolean) -> Unit,
    val backwardButton: @Composable () -> Unit,
    val skipButton: @Composable () -> Unit,
    val content: @Composable ColumnScope.(WizardConfigState<T>) -> Unit,
)

fun <T : Any> WizardStep<T>.copy(
    key: String = this.key,
    stepName: String = this.stepName,
    data: T = this.data,
    canForward: (T) -> Boolean = this.canForward,
    skippable: Boolean = this.skippable,
    forwardButton: @Composable (Boolean) -> Unit = this.forwardButton,
    backwardButton: @Composable () -> Unit = this.backwardButton,
    skipButton: @Composable () -> Unit = this.skipButton,
    content: @Composable ColumnScope.(WizardConfigState<T>) -> Unit = this.content,
): WizardStep<T> {
    return WizardStep(
        key = key,
        stepName = stepName,
        data = data,
        canForward = canForward,
        skippable = skippable,
        forwardButton = forwardButton,
        backwardButton = backwardButton,
        skipButton = skipButton,
        content = content,
    )
}

data class WizardStepData(
    val data: Any,
    val skipped: Boolean
)