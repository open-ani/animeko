/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.wizard.WizardDefaults
import kotlin.test.Test

private const val TAG_INDICATOR_TEXT = "indicatorText"
private const val TAG_INDICATOR_TITLE = "indicatorTitle"
private const val TAG_BUTTON_NEXT_STEP = "buttonNextStep"
private const val TAG_BUTTON_PREV_STEP = "buttonPrevStep"

class WizardHostTest {
    private val SemanticsNodeInteractionsProvider.indicatorText
        get() = onNodeWithTag(TAG_INDICATOR_TEXT, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.indicatorTitle
        get() = onNodeWithTag(TAG_INDICATOR_TITLE, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.buttonNextStep
        get() = onNodeWithTag(TAG_BUTTON_NEXT_STEP, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.buttonPrevStep
        get() = onNodeWithTag(TAG_BUTTON_PREV_STEP, useUnmergedTree = true)

    @Composable
    private fun View(wizardController: WizardController) {
        WizardNavHost(
            wizardController,
            modifier = Modifier.fillMaxSize(),
            indicatorBar = {
                WizardDefaults.StepTopAppBar(
                    currentStep = it.currentStepIndex + 1,
                    totalStep = it.stepCount,
                    indicatorStepTextTestTag = TAG_INDICATOR_TEXT,
                ) {
                    it.currentStep.stepName.invoke()
                }
            },
            controlBar = {
                WizardDefaults.StepControlBar(
                    forwardAction = {
                        Box(Modifier.testTag(TAG_BUTTON_NEXT_STEP)) {
                            it.currentStep.forwardButton.invoke()
                        }
                    },
                    backwardAction = {
                        Box(Modifier.testTag(TAG_BUTTON_PREV_STEP)) {
                            it.currentStep.backwardButton.invoke()
                        }
                    },
                )
            },
        ) {
            step(
                "step_1",
                title = {
                    Text("Step 1", Modifier.testTag(TAG_INDICATOR_TITLE))
                },
            ) {
                Text("this is my first step")
            }
            step(
                "step_2",
                title = {
                    Text("Step 2", Modifier.testTag(TAG_INDICATOR_TITLE))
                },
            ) {
                Text("this is my second step")
            }
        }
    }

    @Test
    fun `step test`() = runAniComposeUiTest {
        val wizardController = WizardController()

        setContent {
            View(wizardController)
        }

        runOnIdle {
            //indicatorText.
        }
    }
}