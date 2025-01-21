/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.framework

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.MainTestClock
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density

/**
 * 相对于 [runComposeUiTest], 有一些修改:
 * - [ComposeUiTest.waitUntil] 的超时时间更长
 */
expect fun runAniComposeUiTest(
    testBody: AniComposeUiTest.() -> Unit
)

internal abstract class AbstractAniComposeUiTest(override val composeUiTest: ComposeUiTest) : AniComposeUiTest {
    override val density: Density
        get() = composeUiTest.density
    override val mainClock: MainTestClock
        get() = composeUiTest.mainClock

    override fun <T> runOnUiThread(action: () -> T): T = composeUiTest.runOnUiThread(action)
    override fun <T> runOnIdle(action: () -> T): T = composeUiTest.runOnIdle(action)

    override fun waitForIdle() = composeUiTest.waitForIdle()
    override suspend fun awaitIdle() = composeUiTest.awaitIdle()

    // 安卓没有这个, 所以得在具体平台实现
    abstract override fun waitUntil(conditionDescription: String?, timeoutMillis: Long, condition: () -> Boolean)

    override fun registerIdlingResource(idlingResource: IdlingResource) =
        composeUiTest.registerIdlingResource(idlingResource)

    override fun unregisterIdlingResource(idlingResource: IdlingResource) =
        composeUiTest.unregisterIdlingResource(idlingResource)

    override fun setContent(composable: @Composable () -> Unit) = composeUiTest.setContent(composable)

    override fun onAllNodes(matcher: SemanticsMatcher, useUnmergedTree: Boolean): SemanticsNodeInteractionCollection =
        composeUiTest.onAllNodes(matcher, useUnmergedTree)

    override fun onNode(matcher: SemanticsMatcher, useUnmergedTree: Boolean): SemanticsNodeInteraction =
        composeUiTest.onNode(matcher, useUnmergedTree)
}

// body copied from ComposeUiTest
/**
 * @see ComposeUiTest
 */
interface AniComposeUiTest : SemanticsNodeInteractionsProvider {
    val composeUiTest: ComposeUiTest

    val density: Density
    val mainClock: MainTestClock
    fun <T> runOnUiThread(action: () -> T): T
    fun <T> runOnIdle(action: () -> T): T
    fun waitForIdle()
    suspend fun awaitIdle()


    /**
     * Blocks until the given [condition] is satisfied.
     *
     * If [auto advancement][MainTestClock.autoAdvance] is enabled on the [mainClock], this method
     * will actively advance the clock to process any pending composition, invalidation and
     * animation. If auto advancement is not enabled, the clock will not be advanced actively
     * which usually means that the Compose UI appears to be frozen. This is ideal for testing
     * animations in a deterministic way. In either case, this method will wait for all
     * [IdlingResource]s to become idle.
     *
     * Note that some processes are driven by the host operating system and will therefore still
     * execute when auto advancement is disabled. For example, on Android measure, layout and draw
     * can still happen if the host view is invalidated by other parts of the View hierarchy.
     *
     * Compared to [MainTestClock.advanceTimeUntil], [waitUntil] sleeps after every iteration to
     * give the host operating system the opportunity to do measure/layout/draw passes. This gives
     * [waitUntil] a better integration with the host, but it is less preferred from a performance
     * viewpoint. Therefore, we recommend that you try using [MainTestClock.advanceTimeUntil]
     * before resorting to [waitUntil].
     *
     * @param timeoutMillis The time after which this method throws an exception if the given
     * condition is not satisfied. This observes wall clock time, not [frame time][mainClock].
     * @param conditionDescription An optional human-readable description of [condition] that will
     * be included in the timeout exception if thrown.
     * @param condition Condition that must be satisfied in order for this method to successfully
     * finish.
     *
     * @throws androidx.compose.ui.test.ComposeTimeoutException If the condition is not satisfied
     * after [timeoutMillis] (in wall clock time).
     */
    fun waitUntil(
        conditionDescription: String? = null,
        timeoutMillis: Long = 5000L,
        condition: () -> Boolean
    )

    // 默认 timeoutMillis 更长, 否则 CI 上有概率失败
    fun waitUntil(timeoutMillis: Long = 5000L, condition: () -> Boolean) {
        waitUntil(null, timeoutMillis, condition)
    }

    fun registerIdlingResource(idlingResource: IdlingResource)
    fun unregisterIdlingResource(idlingResource: IdlingResource)
    fun setContent(composable: @Composable () -> Unit)
}

/**
 * 截图当前的 UI 并与 resources 目录下的图片 [expectedResource] 进行比较.
 */
expect fun AniComposeUiTest.assertScreenshot(expectedResource: String)

expect fun ImageBitmap.assertScreenshot(expectedResource: String)

