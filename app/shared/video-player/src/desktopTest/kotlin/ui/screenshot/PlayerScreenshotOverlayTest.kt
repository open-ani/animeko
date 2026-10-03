/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.videoplayer.screenshot.SavedPlayerScreenshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class PlayerScreenshotOverlayTest {
    private val autoDismissDelay = 3.seconds

    private fun screenshot(width: Int = 160, height: Int = 90) =
        SavedPlayerScreenshot(ImageBitmap(width, height), "shot.png", "shot")

    private fun AniComposeUiTest.setOverlay(
        playerWidth: Dp,
        playerHeight: Dp,
        state: PlayerScreenshotPanelState,
        bottomOffset: Dp = 0.dp,
        onShare: (SavedPlayerScreenshot) -> Unit = {},
        onOpen: (SavedPlayerScreenshot) -> Unit = {},
    ) {
        mainClock.autoAdvance = false
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.requiredSize(playerWidth, playerHeight)) {
                    PlayerScreenshotOverlay(
                        state,
                        onShare = onShare,
                        onOpen = onOpen,
                        Modifier.fillMaxSize(),
                        bottomOffset = bottomOffset,
                        autoDismissDelay = autoDismissDelay,
                    )
                }
            }
        }
    }

    /** 走完原位停留、收进角落和外壳淡入. */
    private fun AniComposeUiTest.advanceUntilDocked() = mainClock.advanceTimeBy(2_000)

    @Test
    fun `flash appears on present and fades out`() = runAniComposeUiTest {
        val state = PlayerScreenshotPanelState()
        setOverlay(800.dp, 450.dp, state)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_FLASH).assertDoesNotExist()

        state.present(screenshot())
        mainClock.advanceTimeBy(50)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_FLASH).assertExists()

        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_FLASH).assertDoesNotExist()
    }

    @Test
    fun `landscape screenshot starts full size and docks at bottom left above the bottom bar`() = runAniComposeUiTest {
        val state = PlayerScreenshotPanelState()
        setOverlay(800.dp, 450.dp, state, bottomOffset = 60.dp)
        state.present(screenshot())
        mainClock.advanceTimeBy(50)

        val start = onNodeWithTag(TAG_PLAYER_SCREENSHOT_THUMBNAIL).getBoundsInRoot()
        assertEquals(800f, start.width.value, 1f)
        assertEquals(450f, start.height.value, 1f)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_SHARE).assertIsNotEnabled()

        advanceUntilDocked()
        val docked = onNodeWithTag(TAG_PLAYER_SCREENSHOT_THUMBNAIL).getBoundsInRoot()
        assertEquals(16f, docked.left.value, 1f)
        assertEquals(450f - 60f - 16f, docked.bottom.value, 1f)
        // 播放器高度不足 480dp, 用紧凑的 88dp 缩略图
        assertEquals(88f, docked.height.value, 1f)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_SHARE).assertIsDisplayed().assertIsEnabled()
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_DISMISS).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun `portrait screenshot docks at bottom right`() = runAniComposeUiTest {
        val state = PlayerScreenshotPanelState()
        // 测试窗口 768dp 高, 播放器区域必须放得下, 否则会被居中裁切
        setOverlay(400.dp, 700.dp, state)
        state.present(screenshot())
        advanceUntilDocked()

        val docked = onNodeWithTag(TAG_PLAYER_SCREENSHOT_THUMBNAIL).getBoundsInRoot()
        assertEquals(400f - 16f, docked.right.value, 1f)
        assertEquals(700f - 16f, docked.bottom.value, 1f)
        // 112dp 高的 16:9 缩略图宽 199 超过上限 400 * 0.4 = 160, 按上限缩小
        assertEquals(160f, docked.width.value, 1f)
    }

    @Test
    fun `share button reports the screenshot and dismiss removes the panel`() = runAniComposeUiTest {
        val state = PlayerScreenshotPanelState()
        var shared: SavedPlayerScreenshot? = null
        setOverlay(800.dp, 450.dp, state, onShare = { shared = it })
        val screenshot = screenshot()
        state.present(screenshot)
        advanceUntilDocked()

        onNodeWithTag(TAG_PLAYER_SCREENSHOT_SHARE).performClick()
        assertSame(screenshot, shared)

        onNodeWithTag(TAG_PLAYER_SCREENSHOT_DISMISS).performClick()
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_PANEL).assertDoesNotExist()
        assertNull(state.current)
    }

    @Test
    fun `panel dismisses itself after the delay`() = runAniComposeUiTest {
        val state = PlayerScreenshotPanelState()
        setOverlay(800.dp, 450.dp, state)
        state.present(screenshot())
        advanceUntilDocked()
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_PANEL).assertExists()

        // 落定于 0.8s, 倒计时 3s, 退场 0.2s
        mainClock.advanceTimeBy(1_500)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_PANEL).assertExists()
        mainClock.advanceTimeBy(1_500)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_PANEL).assertDoesNotExist()
        assertNull(state.current)
    }

    @Test
    fun `a new screenshot replaces the docked panel and flashes again`() = runAniComposeUiTest {
        val state = PlayerScreenshotPanelState()
        setOverlay(800.dp, 450.dp, state)
        state.present(screenshot())
        advanceUntilDocked()

        val second = screenshot(320, 180)
        state.present(second)
        mainClock.advanceTimeBy(50)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_FLASH).assertExists()
        assertSame(second, state.current?.screenshot)

        advanceUntilDocked()
        val docked = onNodeWithTag(TAG_PLAYER_SCREENSHOT_THUMBNAIL).getBoundsInRoot()
        assertEquals(16f, docked.left.value, 1f)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_SHARE).assertIsEnabled()
    }
}
