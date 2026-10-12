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
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.videoplayer.screenshot.SavedPlayerScreenshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class PlayerScreenshotOverlayTest {
    private val autoDismissDelay = 3.seconds

    /** 画面到播放器区域边缘的距离: 面板边距 16dp 加 A 区域内留白 8dp. B 区域与 A 底边对齐, 不占画面下方的空间. */
    private val imageMargin = 24f

    private fun screenshot(width: Int = 160, height: Int = 90) =
        SavedPlayerScreenshot(ImageBitmap(width, height), "shot.png", "shot")

    /** 组合覆盖层并返回它的状态. */
    private fun AniComposeUiTest.setOverlay(
        playerWidth: Dp,
        playerHeight: Dp,
        bottomOffset: Dp = 0.dp,
        onShare: (SavedPlayerScreenshot, DpRect?) -> Unit = { _, _ -> },
        onCopy: (SavedPlayerScreenshot) -> Unit = {},
        onOpen: (SavedPlayerScreenshot) -> Unit = {},
    ): PlayerScreenshotPanelState {
        val state = PlayerScreenshotPanelState()
        mainClock.autoAdvance = false
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.requiredSize(playerWidth, playerHeight)) {
                    PlayerScreenshotOverlay(
                        state,
                        onShare = onShare,
                        onCopy = onCopy,
                        onOpen = onOpen,
                        Modifier.fillMaxSize(),
                        bottomOffset = bottomOffset,
                        autoDismissDelay = autoDismissDelay,
                    )
                }
            }
        }
        return state
    }

    /** 走完原位停留、收进角落和外壳淡入. */
    private fun AniComposeUiTest.advanceUntilDocked() = mainClock.advanceTimeBy(2_000)

    @Test
    fun `flash appears on present and fades out`() = runAniComposeUiTest {
        val state = setOverlay(800.dp, 450.dp)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_FLASH).assertDoesNotExist()

        state.present(screenshot())
        mainClock.advanceTimeBy(50)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_FLASH).assertExists()

        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_FLASH).assertDoesNotExist()
    }

    @Test
    fun `landscape screenshot starts full size and docks at bottom left above the bottom bar`() = runAniComposeUiTest {
        val state = setOverlay(800.dp, 450.dp, bottomOffset = 60.dp)
        state.present(screenshot())
        mainClock.advanceTimeBy(50)

        val start = onNodeWithTag(TAG_PLAYER_SCREENSHOT_THUMBNAIL).getBoundsInRoot()
        assertEquals(800f, start.width.value, 1f)
        assertEquals(450f, start.height.value, 1f)
        // 入场期间面板外壳还没有组合
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_SHARE).assertDoesNotExist()

        advanceUntilDocked()
        val docked = onNodeWithTag(TAG_PLAYER_SCREENSHOT_THUMBNAIL).getBoundsInRoot()
        assertEquals(imageMargin, docked.left.value, 1f)
        assertEquals(450f - 60f - imageMargin, docked.bottom.value, 1f)
        // 播放器高度不足 480dp, 用紧凑的 88dp 缩略图
        assertEquals(88f, docked.height.value, 1f)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_SHARE).assertIsDisplayed().assertIsEnabled()
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_COPY).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun `portrait screenshot docks at bottom right`() = runAniComposeUiTest {
        // 测试窗口 768dp 高, 播放器区域必须放得下, 否则会被居中裁切
        val state = setOverlay(400.dp, 700.dp)
        state.present(screenshot())
        advanceUntilDocked()

        val docked = onNodeWithTag(TAG_PLAYER_SCREENSHOT_THUMBNAIL).getBoundsInRoot()
        assertEquals(400f - imageMargin, docked.right.value, 1f)
        assertEquals(700f - imageMargin, docked.bottom.value, 1f)
        // 112dp 高的 16:9 缩略图宽 199 超过上限 400 * 0.4 = 160, 按上限缩小
        assertEquals(160f, docked.width.value, 1f)
    }

    @Test
    fun `share and copy buttons report the screenshot and keep the panel`() = runAniComposeUiTest {
        var shared: SavedPlayerScreenshot? = null
        var sharedAnchor: DpRect? = null
        var copied: SavedPlayerScreenshot? = null
        val state = setOverlay(
            800.dp, 450.dp,
            onShare = { screenshot, anchor ->
                shared = screenshot
                sharedAnchor = anchor
            },
            onCopy = { copied = it },
        )
        val screenshot = screenshot()
        state.present(screenshot)
        advanceUntilDocked()

        onNodeWithTag(TAG_PLAYER_SCREENSHOT_SHARE).performClick()
        assertSame(screenshot, shared)
        // 分享面板从分享按钮旁边弹出: 锚点就是按钮在窗口中的位置
        val shareButton = onNodeWithTag(TAG_PLAYER_SCREENSHOT_SHARE).getBoundsInRoot()
        val anchor = assertNotNull(sharedAnchor)
        assertEquals(shareButton.left.value, anchor.left.value, 1f)
        assertEquals(shareButton.top.value, anchor.top.value, 1f)
        assertEquals(shareButton.right.value, anchor.right.value, 1f)
        assertEquals(shareButton.bottom.value, anchor.bottom.value, 1f)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_COPY).performClick()
        assertSame(screenshot, copied)
        mainClock.advanceTimeBy(500)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_PANEL).assertExists()
    }

    @Test
    fun `clicking the screenshot opens it and dismisses the panel`() = runAniComposeUiTest {
        var opened: SavedPlayerScreenshot? = null
        val state = setOverlay(800.dp, 450.dp, onOpen = { opened = it })
        val screenshot = screenshot()
        state.present(screenshot)
        advanceUntilDocked()

        onNodeWithTag(TAG_PLAYER_SCREENSHOT_THUMBNAIL).performClick()
        assertSame(screenshot, opened)
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_PANEL).assertDoesNotExist()
        assertNull(state.current)
    }

    @Test
    fun `panel dismisses itself after the delay`() = runAniComposeUiTest {
        val state = setOverlay(800.dp, 450.dp)
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
        val state = setOverlay(800.dp, 450.dp)
        state.present(screenshot())
        advanceUntilDocked()

        val second = screenshot(320, 180)
        state.present(second)
        mainClock.advanceTimeBy(50)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_FLASH).assertExists()
        assertSame(second, state.current?.screenshot)

        advanceUntilDocked()
        val docked = onNodeWithTag(TAG_PLAYER_SCREENSHOT_THUMBNAIL).getBoundsInRoot()
        assertEquals(imageMargin, docked.left.value, 1f)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_SHARE).assertIsEnabled()
    }

    @Test
    fun `hovering a panel button postpones the auto dismiss until the pointer leaves`() = runAniComposeUiTest {
        val state = setOverlay(800.dp, 450.dp)
        state.present(screenshot())
        advanceUntilDocked()

        // 悬停在按钮上也算悬停在面板上: 倒计时 3s 内不收起
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_SHARE).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(4_000)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_PANEL).assertExists()

        // 移出面板后重新计时
        onRoot().performMouseInput { moveTo(topLeft) }
        mainClock.advanceTimeBy(1_500)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_PANEL).assertExists()
        mainClock.advanceTimeBy(2_000)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_PANEL).assertDoesNotExist()
    }

    @Test
    fun `holding a panel button down postpones the auto dismiss until release`() = runAniComposeUiTest {
        var shared = 0
        val state = setOverlay(800.dp, 450.dp, onShare = { _, _ -> shared++ })
        state.present(screenshot())
        advanceUntilDocked()

        // 触摸没有悬停, 只有按住
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_SHARE).performTouchInput { down(center) }
        mainClock.advanceTimeBy(4_000)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_PANEL).assertExists()

        onNodeWithTag(TAG_PLAYER_SCREENSHOT_SHARE).performTouchInput { up() }
        assertEquals(1, shared)
        mainClock.advanceTimeBy(1_500)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_PANEL).assertExists()
        mainClock.advanceTimeBy(2_000)
        onNodeWithTag(TAG_PLAYER_SCREENSHOT_PANEL).assertDoesNotExist()
    }
}
