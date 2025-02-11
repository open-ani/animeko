/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test

private const val TAG_INDICATOR_TEXT = "indicatorText"
private const val TAG_INDICATOR_TITLE = "indicatorTitle"
private const val TAG_BUTTON_NEXT_STEP = "buttonNextStep"
private const val TAG_BUTTON_PREV_STEP = "buttonPrevStep"
private const val TAG_STEP_CONTENT_TEXT = "stepContentText"

class OnboardingNavHostTest {
    private val SemanticsNodeInteractionsProvider.indicatorText
        get() = onAllNodesWithTag(TAG_INDICATOR_TEXT, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.indicatorTitle
        get() = onAllNodesWithTag(TAG_INDICATOR_TITLE, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.buttonNextStep
        get() = onNodeWithTag(TAG_BUTTON_NEXT_STEP, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.buttonPrevStep
        get() = onNodeWithTag(TAG_BUTTON_PREV_STEP, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.stepContentText
        get() = onNodeWithTag(TAG_STEP_CONTENT_TEXT, useUnmergedTree = true)


    @Composable
    private fun View(wizardController: WizardController) {
        WizardNavHost(
            wizardController,
            modifier = Modifier.fillMaxSize(),
        ) {
            step(
                "step_1",
                title = {
                    Text("Step 1", Modifier.testTag(TAG_INDICATOR_TITLE))
                },
            ) {
                Text("this is my first step", Modifier.testTag(TAG_STEP_CONTENT_TEXT))
            }
            step(
                "step_2",
                title = {
                    Text("Step 2", Modifier.testTag(TAG_INDICATOR_TITLE))
                },
            ) {
                Text("this is my second step", Modifier.testTag(TAG_STEP_CONTENT_TEXT))
            }
        }
    }

    @Test
    fun `step test`() = runAniComposeUiTest {
        val wizardController = WizardController()

        setContent {
            ProvideCompositionLocalsForPreview {
                View(wizardController)
            }
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(WizardDefaults.renderStepIndicatorText(1, 2)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 1"))
            stepContentText.assertTextEquals("this is my first step")
        }

        runOnIdle {
            buttonPrevStep.performClick() // 已经在第一步，所以不会到上一步
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(WizardDefaults.renderStepIndicatorText(1, 2)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 1"))
            stepContentText.assertTextEquals("this is my first step")
        }

        runOnIdle {
            buttonNextStep.performClick() // 到下一步
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(WizardDefaults.renderStepIndicatorText(2, 2)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 2"))
            stepContentText.assertTextEquals("this is my second step")
        }

        runOnIdle {
            buttonNextStep.performClick() // 已经是最后一步，所以不会到下一步
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(WizardDefaults.renderStepIndicatorText(2, 2)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 2"))
            stepContentText.assertTextEquals("this is my second step")
        }

        runOnIdle {
            buttonPrevStep.performClick() // 回到上一步
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(WizardDefaults.renderStepIndicatorText(1, 2)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 1"))
            stepContentText.assertTextEquals("this is my first step")
        }
    }
}