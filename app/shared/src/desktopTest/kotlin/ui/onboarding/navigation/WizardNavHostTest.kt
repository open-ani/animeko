/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding.navigation

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
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.onboarding_navigation_step_indicator
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TAG_INDICATOR_TEXT = "indicatorText"
private const val TAG_INDICATOR_TITLE = "indicatorTitle"
private const val TAG_BUTTON_NEXT_STEP = "buttonNextStep"
private const val TAG_BUTTON_PREV_STEP = "buttonPrevStep"
private const val TAG_STEP_CONTENT_TEXT = "stepContentText"

private fun expectedStepIndicatorText(currentStep: Int, totalStep: Int): String = runBlocking {
    getString(Lang.onboarding_navigation_step_indicator, currentStep, totalStep)
}

class WizardNavHostTest {
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
    private fun View(
        wizardController: WizardController,
        skipSecond: Boolean = false,
        skipThird: Boolean = false,
        onCompleted: () -> Unit = { },
    ) {
        WizardNavHost(
            wizardController,
            modifier = Modifier.fillMaxSize(),
            onCompleted = onCompleted,
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
                autoSkip = { skipSecond },
                title = {
                    Text("Step 2", Modifier.testTag(TAG_INDICATOR_TITLE))
                },
            ) {
                Text("this is my second step", Modifier.testTag(TAG_STEP_CONTENT_TEXT))
            }
            step(
                "step_3",
                autoSkip = { skipThird },
                title = {
                    Text("Step 3", Modifier.testTag(TAG_INDICATOR_TITLE))
                },
            ) {
                Text("this is my third step", Modifier.testTag(TAG_STEP_CONTENT_TEXT))
            }
        }
    }

    @Test
    fun `step test`() = runAniComposeUiTest {
        val wizardController = WizardController()
        var completedCount = 0

        setContent {
            ProvideCompositionLocalsForPreview {
                View(wizardController, onCompleted = { completedCount++ })
            }
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(1, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 1"))
            stepContentText.assertTextEquals("this is my first step")
        }

        runOnIdle {
            buttonPrevStep.performClick() // 已经在第一步，所以不会到上一步
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(1, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 1"))
            stepContentText.assertTextEquals("this is my first step")
        }

        runOnIdle {
            buttonNextStep.performClick() // 到下一步
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(2, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 2"))
            stepContentText.assertTextEquals("this is my second step")
        }

        runOnIdle {
            buttonNextStep.performClick()
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(3, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 3"))
            stepContentText.assertTextEquals("this is my third step")
        }

        runOnIdle {
            buttonNextStep.performClick() // 已经到最后一步了，不会进行下一步
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(3, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 3"))
            stepContentText.assertTextEquals("this is my third step")

            assertEquals(1, completedCount, "Expected trigger onCompleted once")
        }
    }

    @Test
    fun `skip middle steps test`() = runAniComposeUiTest {
        val wizardController = WizardController()
        var completedCount = 0

        setContent {
            ProvideCompositionLocalsForPreview {
                View(
                    wizardController,
                    skipSecond = true,
                    onCompleted = { completedCount++ },
                )
            }
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(1, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 1"))
            stepContentText.assertTextEquals("this is my first step")
        }

        runOnIdle {
            buttonNextStep.performClick() // 第二步会跳过, 应该直接到第三步
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(3, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 3"))
            stepContentText.assertTextEquals("this is my third step")
        }

        runOnIdle {
            buttonNextStep.performClick()
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(3, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 3"))
            stepContentText.assertTextEquals("this is my third step")

            assertEquals(1, completedCount, "Expected trigger onCompleted once")
        }

        runOnIdle {
            buttonNextStep.performClick()
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(3, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 3"))
            stepContentText.assertTextEquals("this is my third step")

            assertEquals(2, completedCount, "Expected trigger onCompleted once")
        }
    }

    @Test
    fun `skip last step test`() = runAniComposeUiTest {
        val wizardController = WizardController()
        var completedCount = 0

        setContent {
            ProvideCompositionLocalsForPreview {
                View(
                    wizardController,
                    skipThird = true,
                    onCompleted = { completedCount++ },
                )
            }
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(1, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 1"))
            stepContentText.assertTextEquals("this is my first step")
        }

        runOnIdle {
            buttonNextStep.performClick() // 第二步
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(2, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 2"))
            stepContentText.assertTextEquals("this is my second step")
        }

        runOnIdle {
            buttonNextStep.performClick() // 最后一步会跳过
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(2, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 2"))
            stepContentText.assertTextEquals("this is my second step")

            assertEquals(1, completedCount, "Expected trigger onCompleted once")
        }

        runOnIdle {
            buttonNextStep.performClick() // 再点一次也是一样
        }

        runOnIdle {
            indicatorText.assertAll(
                hasTextExactly(expectedStepIndicatorText(2, 3)),
            )
            indicatorTitle.assertAll(hasTextExactly("Step 2"))
            stepContentText.assertTextEquals("this is my second step")

            assertEquals(2, completedCount, "Expected trigger onCompleted once")
        }
    }
}
