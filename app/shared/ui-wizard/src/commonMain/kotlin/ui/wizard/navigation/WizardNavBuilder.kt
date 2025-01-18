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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.wizard.WizardDefaults

@DslMarker
annotation class WizardStepDsl

class WizardBuilder(
    private val controller: WizardController,
) {
    private val steps: LinkedHashMap<String, WizardStep<Any>> = linkedMapOf()

    @WizardStepDsl
    fun <T : Any> step(
        key: String,
        title: @Composable (T) -> Unit,
        defaultConfig: T,
        canForward: (T) -> Boolean = { true },
        skippable: Boolean = false,
        forward: @Composable (canForward: Boolean) -> Unit = {
            WizardDefaults.GoForwardButton({ controller.goForward() }, it)
        },
        backward: @Composable () -> Unit = {
            WizardDefaults.GoBackwardButton({ controller.goBackward() })
        },
        skipButton: @Composable () -> Unit = if (skippable) {
            {
                val scope = rememberCoroutineScope()
                WizardDefaults.SkipButton(
                    {
                        scope.launch {
                            controller.requestSkip()
                        }
                    },
                )
            }
        } else {
            { }
        },
        content: @Composable WizardStepScope<T>.() -> Unit,
    ) {
        if (steps[key] != null) {
            throw IllegalArgumentException("Duplicate step key: $key")
        }
        @Suppress("UNCHECKED_CAST")
        steps[key] = WizardStep(
            key,
            title,
            defaultConfig,
            canForward,
            skippable,
            forward,
            backward,
            skipButton,
            content,
        ) as WizardStep<Any>
    }

    fun build(): Map<String, WizardStep<Any>> {
        return steps
    }
}