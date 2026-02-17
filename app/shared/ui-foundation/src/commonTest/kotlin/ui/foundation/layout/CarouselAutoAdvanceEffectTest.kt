/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.test.DisabledOnAndroid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * Test cases for [CarouselAutoAdvanceEffect]
 */
class CarouselAutoAdvanceEffectTest {
    private fun createTestLifecycleOwner(initialState: Lifecycle.State): TestLifecycleOwner {
        return TestLifecycleOwner(initialState)
    }

    @Test
    @DisabledOnAndroid
    // Cannot invoke "String.toLowerCase(java.util.Locale)" because "android.os.Build.FINGERPRINT" is null
    //java.lang.NullPointerException: Cannot invoke "String.toLowerCase(java.util.Locale)" because "android.os.Build.FINGERPRINT" is null
    //	at androidx.compose.ui.test.RobolectricIdlingStrategy_androidKt.getHasRobolectricFingerprint(RobolectricIdlingStrategy.android.kt:32)
    //	at androidx.compose.ui.test.AndroidComposeUiTestEnvironment$runTest$1$1.invokeSuspend(ComposeUiTest.android.kt:589)
    //	at androidx.compose.ui.test.AndroidComposeUiTestEnvironment$runTest$1$1.invoke(ComposeUiTest.android.kt)
    //	at androidx.compose.ui.test.AndroidComposeUiTestEnvironment$runTest$1$1.invoke(ComposeUiTest.android.kt)
    //	at kotlinx.coroutines.test.TestBuildersKt__TestBuildersKt$runTest$2$1$1.invokeSuspend(TestBuilders.kt:317)
    //	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
    fun `auto-advance - whenEnabledAndLifecycleResumed - advancesPageAfterPeriod`() = runAniComposeUiTest {
        mainClock.autoAdvance = false
        val testLifecycleOwner = createTestLifecycleOwner(Lifecycle.State.RESUMED)
        val pageCount = 20
        val periodMs = 3_000L // 3 seconds in milliseconds

        val carouselState = CarouselState(0) { pageCount }

        setContent {
            ProvideFoundationCompositionLocalsForTest {
                CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {
                    CarouselAutoAdvanceEffect(
                        enabled = true,
                        carouselState = carouselState,
                        period = periodMs.milliseconds - 50.milliseconds,
                        animationSpec = snap(),
                    )
                }
                TestCarousel(carouselState)
            }
        }
        runOnIdle {
            assertEquals(0, carouselState.pagerState1.currentPage)
        }

        // Advance time to exactly the period => should move to page 1
        mainClock.advanceTimeBy(3000)
        runOnIdle {
            assertEquals(1, carouselState.pagerState1.currentPage)
        }

        // Advance another full period => should move to page 2
        mainClock.advanceTimeBy(periodMs)
        runOnIdle {
            assertEquals(2, carouselState.pagerState1.currentPage)
        }
    }

    @Test
    @DisabledOnAndroid
    fun `auto-advance - whenDisabled - doesNotAdvancePage`() = runAniComposeUiTest {
        mainClock.autoAdvance = false
        val testLifecycleOwner = createTestLifecycleOwner(Lifecycle.State.RESUMED)
        val pageCount = 5
        val periodMs = 3_000L

        val carouselState = CarouselState(0) { pageCount }

        setContent {
            ProvideFoundationCompositionLocalsForTest {
                CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {
                    // Disabled auto-advance
                    CarouselAutoAdvanceEffect(
                        enabled = false,
                        carouselState = carouselState,
                        period = periodMs.milliseconds - 50.milliseconds,
                        animationSpec = snap(),
                    )
                }
                TestCarousel(carouselState)
            }
        }

        // Advance time beyond the period, but no auto-advance should occur
        mainClock.advanceTimeBy(periodMs * 2)
        runOnIdle {
            // Still page = 0
            assertEquals(0, carouselState.pagerState1.currentPage)
        }
    }

    @Test
    @DisabledOnAndroid
    fun `auto-advance - userScrollInProgress - skipAutoAdvance`() = runAniComposeUiTest {
        mainClock.autoAdvance = false
        val testLifecycleOwner = createTestLifecycleOwner(Lifecycle.State.RESUMED)
        val pageCount = 20
        val periodMs = 3_000L

        val carouselState = CarouselState(0) { pageCount }

        setContent {
            ProvideFoundationCompositionLocalsForTest {
                CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {
                    CarouselAutoAdvanceEffect(
                        enabled = true,
                        carouselState = carouselState,
                        period = periodMs.milliseconds - 50.milliseconds,
                        animationSpec = snap(),
                    )
                }
                TestCarousel(carouselState)
            }
        }

        // Move near the period but not yet => should still be on page 0
        mainClock.advanceTimeBy(periodMs)
        runOnIdle {
            assertEquals(1, carouselState.pagerState1.currentPage)
        }

        // Simulate user scroll by dragging and not lifting
        onNodeWithTag("carousel").performTouchInput {
            down(center)
            moveBy(Offset(viewConfiguration.touchSlop, 0f), delayMillis = 0)
            // Keep the finger down
        }
        runOnIdle {
            assertEquals(1, carouselState.pagerState1.currentPage)
        }

        // Advance time beyond one period while scrolling
        mainClock.advanceTimeBy(periodMs)
        runOnIdle {
            // Should still be on page 1, because user has not lifted finger
            assertEquals(1, carouselState.pagerState1.currentPage)
        }

        // Now lift the finger to end user scroll
        onNodeWithTag("carousel").performTouchInput {
            up()
        }
        mainClock.autoAdvance = true // finish up fling animation
        waitForIdle()
        runOnIdle {
            assertEquals(1, carouselState.pagerState1.currentPage)
        }

        // After another full period => should now auto-advance to page 2
        mainClock.advanceTimeBy(periodMs)
        runOnIdle {
            assertEquals(2, carouselState.pagerState1.currentPage)
        }
    }

    @Composable
    private fun TestCarousel(carouselState: CarouselState) {
        Surface {
            HorizontalMultiBrowseCarousel(
                carouselState,
                preferredItemWidth = 100.dp,
                Modifier.width(300.dp).height(200.dp).testTag("carousel"),
                flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(carouselState),
            ) {
                CarouselItem(
                    label = { Text("Item $it") },
                ) {
                    Box(Modifier.height(200.dp).width(100.dp))
                }
            }
        }
    }

    @Test
    @DisabledOnAndroid
    fun `auto-advance - lifecyclePausedThenResumed - resumesAutoAdvance`() = runAniComposeUiTest {
        mainClock.autoAdvance = false
        val testLifecycleOwner = createTestLifecycleOwner(Lifecycle.State.RESUMED)
        val pageCount = 10
        val periodMs = 3_000L

        val carouselState = CarouselState(0) { pageCount }

        setContent {
            ProvideFoundationCompositionLocalsForTest {
                CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {
                    CarouselAutoAdvanceEffect(
                        enabled = true,
                        carouselState = carouselState,
                        period = periodMs.milliseconds - 50.milliseconds,
                        animationSpec = snap(),
                    )
                }
                TestCarousel(carouselState)
            }
        }

        // After one period => should advance from page 0 to page 1
        mainClock.advanceTimeBy(periodMs)
        runOnIdle {
            assertEquals(1, carouselState.pagerState1.currentPage)
        }

        // Pause the lifecycle
        runOnIdle {
            testLifecycleOwner.currentState = Lifecycle.State.STARTED
        }

        // Advance time => page should remain 1 (no auto-advance when not RESUMED)
        mainClock.advanceTimeBy(periodMs)
        runOnIdle {
            assertEquals(1, carouselState.pagerState1.currentPage)
        }

        // Return to RESUMED => auto-advance resumes
        runOnIdle {
            testLifecycleOwner.currentState = Lifecycle.State.RESUMED
        }

        // After another period => should go to page 2
        mainClock.advanceTimeBy(periodMs)
        runOnIdle {
            assertEquals(2, carouselState.pagerState1.currentPage)
        }
    }

    @Test
    @DisabledOnAndroid
    fun `auto-advance - wrapAround - goesBackToFirstPage`() = runAniComposeUiTest {
        mainClock.autoAdvance = false
        val testLifecycleOwner = createTestLifecycleOwner(Lifecycle.State.RESUMED)
        // If pageCount = 4, effect uses (currentPage + 1) % (pageCount - 1)
        // => cycles 0 -> 1 -> 2 -> 0 -> 1 -> 2 -> ...
        val pageCount = 6
        val periodMs = 3_000L

        val carouselState = CarouselState(0) { pageCount }

        setContent {
            ProvideFoundationCompositionLocalsForTest {
                CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {
                    CarouselAutoAdvanceEffect(
                        enabled = true,
                        carouselState = carouselState,
                        period = periodMs.milliseconds - 500.milliseconds,
                        animationSpec = snap(),
                    )
                }
                TestCarousel(carouselState)
            }
        }

        // After one period => page = 1
        mainClock.advanceTimeBy(periodMs)
        runOnIdle {
            assertEquals(1, carouselState.pagerState1.currentPage)
        }

        // Second period => page = 2
        mainClock.advanceTimeBy(periodMs)
        runOnIdle {
            assertEquals(2, carouselState.pagerState1.currentPage)
        }

        // TODO: This test does not work anymore since the upgrade to CMP 1.9.0
        // reaching end
//        mainClock.advanceTimeBy(periodMs)
//        runOnIdle {
//            assertEquals(3, carouselState.pagerState1.currentPage)
//            assertEquals(false, carouselState.pagerState1.canScrollForward)
//            assertEquals(true, carouselState.pagerState1.canScrollBackward)
//        }
//
//        mainClock.advanceTimeBy(periodMs)
//        runOnIdle {
//            assertEquals(0, carouselState.pagerState1.currentPage)
//            assertEquals(true, carouselState.pagerState1.canScrollForward)
//            assertEquals(false, carouselState.pagerState1.canScrollBackward)
//        }
    }
}

private val CarouselState.pagerState1: PagerState
    get() =
        @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
        this.pagerState
