/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.analytics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.player.analytics.PlayFlowDebugEvent
import me.him188.ani.app.domain.player.analytics.PlayFlowDebugLog
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.framework.exists
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayFlowTimelineTest {
    private fun event(seq: Int, attempt: Long = 1) = PlayFlowDebugEvent(
        mapOf(
            "step" to "step_$seq",
            "play_session_id" to "x",
            "episode_attempt" to attempt,
            "seq" to seq.toLong(),
            "t_ms" to seq * 100L,
            "subject_id" to 1L,
            "episode_id" to 1L,
            "outcome" to "ok",
        ),
    )

    @Test
    fun `follows new events at the bottom and stops after scrolling up`() = runAniComposeUiTest {
        val events = mutableStateListOf<PlayFlowDebugEvent>()
        repeat(30) { events += event(it) }
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.size(320.dp, 240.dp)) {
                    PlayFlowTimeline(events, autoFollow = true, Modifier.fillMaxSize())
                }
            }
        }

        // 初始停在底部
        runOnIdle { waitUntil { onNodeWithTag(PlayFlowDebugWindowTestTags.event(29)).exists() } }
        onNodeWithTag(PlayFlowDebugWindowTestTags.event(29)).assertIsDisplayed()
        onNodeWithTag(PlayFlowDebugWindowTestTags.FOLLOW_BUTTON).assertDoesNotExist()

        // 停在底部时跟随新事件
        runOnIdle { events += event(30) }
        runOnIdle { waitUntil { onNodeWithTag(PlayFlowDebugWindowTestTags.event(30)).exists() } }
        onNodeWithTag(PlayFlowDebugWindowTestTags.event(30)).assertIsDisplayed()
        onNodeWithTag(PlayFlowDebugWindowTestTags.FOLLOW_BUTTON).assertDoesNotExist()

        // 用户往上滚后不再跟随, 出现「跳到最新」
        onNodeWithTag(PlayFlowDebugWindowTestTags.TIMELINE).performTouchInput { swipeDown() }
        runOnIdle { waitUntil { onNodeWithTag(PlayFlowDebugWindowTestTags.FOLLOW_BUTTON).exists() } }
        runOnIdle { events += event(31) }
        waitForIdle()
        if (onNodeWithTag(PlayFlowDebugWindowTestTags.event(31)).exists()) {
            onNodeWithTag(PlayFlowDebugWindowTestTags.event(31)).assertIsNotDisplayed()
        }
        onNodeWithTag(PlayFlowDebugWindowTestTags.FOLLOW_BUTTON).assertIsDisplayed()

        // 点「跳到最新」回到底部并恢复跟随
        onNodeWithTag(PlayFlowDebugWindowTestTags.FOLLOW_BUTTON).performClick()
        runOnIdle { waitUntil { onNodeWithTag(PlayFlowDebugWindowTestTags.event(31)).exists() } }
        onNodeWithTag(PlayFlowDebugWindowTestTags.event(31)).assertIsDisplayed()
        onNodeWithTag(PlayFlowDebugWindowTestTags.FOLLOW_BUTTON).assertDoesNotExist()
        runOnIdle { events += event(32) }
        runOnIdle { waitUntil { onNodeWithTag(PlayFlowDebugWindowTestTags.event(32)).exists() } }
        onNodeWithTag(PlayFlowDebugWindowTestTags.event(32)).assertIsDisplayed()
    }

    @Test
    fun `history shows sessions newest first and opens a session timeline`() = runAniComposeUiTest {
        val log = PlayFlowDebugLog()
        log.record("first", 1, properties(seq = 1, playSessionId = "first"), timestampMillis = 1_000)
        log.record("second", 2, properties(seq = 1, playSessionId = "second"), timestampMillis = 2_000)
        log.record("second", 2, properties(seq = 2, playSessionId = "second"), timestampMillis = 2_000)
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.size(360.dp, 480.dp)) {
                    PlayFlowDebugWindow(log, onClose = {}, Modifier.fillMaxSize())
                }
            }
        }

        // 实时页显示最新会话
        runOnIdle { waitUntil { onNodeWithTag(PlayFlowDebugWindowTestTags.event(2)).exists() } }

        onNodeWithTag(PlayFlowDebugWindowTestTags.TAB_HISTORY).performClick()
        runOnIdle { waitUntil { onNodeWithTag(PlayFlowDebugWindowTestTags.session("first")).exists() } }
        onNodeWithTag(PlayFlowDebugWindowTestTags.session("second")).assertIsDisplayed()

        onNodeWithTag(PlayFlowDebugWindowTestTags.session("first")).performClick()
        runOnIdle { waitUntil { onNodeWithTag(PlayFlowDebugWindowTestTags.event(1)).exists() } }
        onNodeWithTag(PlayFlowDebugWindowTestTags.event(2)).assertDoesNotExist()
    }

    @Test
    fun `floating window moves when its title bar is dragged and stays inside the host`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.size(800.dp, 600.dp)) {
                    PlayFlowDebugFloatingWindow(onClose = {}, log = PlayFlowDebugLog())
                }
            }
        }
        runOnIdle { waitUntil { onNodeWithTag(PlayFlowDebugWindowTestTags.WINDOW).exists() } }
        val before = onNodeWithTag(PlayFlowDebugWindowTestTags.WINDOW).getUnclippedBoundsInRoot()

        onNodeWithTag(PlayFlowDebugWindowTestTags.TITLE_BAR).performTouchInput {
            swipe(center, center + Offset(-150f, 200f), durationMillis = 200)
        }
        waitForIdle()
        val after = onNodeWithTag(PlayFlowDebugWindowTestTags.WINDOW).getUnclippedBoundsInRoot()
        assertTrue(after.left < before.left, "expected window to move left: $before -> $after")
        assertTrue(after.top > before.top, "expected window to move down: $before -> $after")

        // 往右上拖出界: 停在右上角
        onNodeWithTag(PlayFlowDebugWindowTestTags.TITLE_BAR).performTouchInput {
            swipe(center, center + Offset(2000f, -2000f), durationMillis = 200)
        }
        waitForIdle()
        val clamped = onNodeWithTag(PlayFlowDebugWindowTestTags.WINDOW).getUnclippedBoundsInRoot()
        assertEquals(before, clamped)
    }

    @Test
    fun `minimizing collapses to a button and restoring keeps the selected tab`() = runAniComposeUiTest {
        val log = PlayFlowDebugLog()
        log.record("s", 1, properties(seq = 1, playSessionId = "s"))
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.size(800.dp, 600.dp)) {
                    PlayFlowDebugFloatingWindow(onClose = {}, log = log)
                }
            }
        }
        runOnIdle { waitUntil { onNodeWithTag(PlayFlowDebugWindowTestTags.WINDOW).exists() } }
        onNodeWithTag(PlayFlowDebugWindowTestTags.TAB_HISTORY).performClick()
        onNodeWithTag(PlayFlowDebugWindowTestTags.TAB_HISTORY).assertIsSelected()

        onNodeWithTag(PlayFlowDebugWindowTestTags.MINIMIZE_BUTTON).performClick()
        runOnIdle { waitUntil { onNodeWithTag(PlayFlowDebugWindowTestTags.MINIMIZED_BUTTON).exists() } }
        onNodeWithTag(PlayFlowDebugWindowTestTags.WINDOW).assertDoesNotExist()

        onNodeWithTag(PlayFlowDebugWindowTestTags.MINIMIZED_BUTTON).performClick()
        runOnIdle { waitUntil { onNodeWithTag(PlayFlowDebugWindowTestTags.WINDOW).exists() } }
        onNodeWithTag(PlayFlowDebugWindowTestTags.MINIMIZED_BUTTON).assertDoesNotExist()
        onNodeWithTag(PlayFlowDebugWindowTestTags.TAB_HISTORY).assertIsSelected()
    }

    @Test
    fun `copy button copies the viewed session as json`() = runAniComposeUiTest {
        val log = PlayFlowDebugLog()
        log.record("first", 1, properties(seq = 1, playSessionId = "first"), timestampMillis = 1_000)
        log.record("second", 2, properties(seq = 1, playSessionId = "second"), timestampMillis = 2_000)
        val clipboard = RecordingClipboard()
        setContent {
            ProvideCompositionLocalsForPreview {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    Box(Modifier.size(360.dp, 480.dp)) {
                        PlayFlowDebugWindow(log, onClose = {}, Modifier.fillMaxSize())
                    }
                }
            }
        }

        // 实时页复制最新会话
        onNodeWithTag(PlayFlowDebugWindowTestTags.COPY_BUTTON).assertIsEnabled().performClick()
        runOnIdle { waitUntil { clipboard.text() != null } }
        val live = clipboard.text()!!
        assertTrue(live.trimStart().startsWith("["), live)
        assertTrue("\"play_session_id\": \"second\"" in live, live)
        assertTrue("\"step\": \"enter\"" in live, live)

        // 历史列表没有打开会话时不能复制; 打开后复制那条
        onNodeWithTag(PlayFlowDebugWindowTestTags.TAB_HISTORY).performClick()
        onNodeWithTag(PlayFlowDebugWindowTestTags.COPY_BUTTON).assertIsNotEnabled()
        onNodeWithTag(PlayFlowDebugWindowTestTags.session("first")).performClick()
        clipboard.entry = null
        onNodeWithTag(PlayFlowDebugWindowTestTags.COPY_BUTTON).assertIsEnabled().performClick()
        runOnIdle { waitUntil { clipboard.text() != null } }
        assertTrue("\"play_session_id\": \"first\"" in clipboard.text()!!, clipboard.text()!!)
    }

    private class RecordingClipboard : Clipboard {
        var entry: ClipEntry? = null
        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            entry = clipEntry
        }

        override val nativeClipboard: NativeClipboard get() = error("not used")

        fun text(): String? = runBlocking { getClipEntryText() }
    }

    private fun properties(seq: Long, playSessionId: String): Map<String, Any?> = mapOf(
        "step" to "enter",
        "play_session_id" to playSessionId,
        "episode_attempt" to 1L,
        "seq" to seq,
        "t_ms" to 0L,
        "subject_id" to 1L,
        "episode_id" to 1L,
    )
}
