/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlinx.io.files.Path
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test

@OptIn(TestOnly::class)
class WindowDropHostTest {
    private class PlayHandler : WindowDropHandler {
        override fun onDragStarted(content: DragAndDropContent?): WindowDropPreview? {
            val file = (content as? DragAndDropContent.FileList)?.files?.firstOrNull { it.name.endsWith(".mp4") }
                ?: return null
            return WindowDropPreview {
                WindowDropCardContent(icon = Icons.Rounded.PlayArrow, title = "Release to play", subtitle = file.name)
            }
        }

        override fun onDrop(content: DragAndDropContent): Boolean = true

        @Composable
        override fun supportedHint(): String = "video files"
    }

    @Test
    fun `shows the handler preview while dragging and hides it afterwards`() = runAniComposeUiTest {
        val state = WindowDropHostState()
        val handlers = listOf(PlayHandler())
        setContent {
            ProvideCompositionLocalsForPreview {
                WindowDropHost(handlers, Modifier.fillMaxSize(), state) {
                    Text("content")
                }
            }
        }
        onNodeWithTag(WindowDropTestTags.OVERLAY).assertDoesNotExist()

        state.onDragStarted(DragAndDropContent.FileList(listOf(Path("/videos/episode-01.mp4"))), handlers)
        waitForIdle()
        onNodeWithTag(WindowDropTestTags.OVERLAY).assertIsDisplayed()
        onNodeWithText("Release to play").assertIsDisplayed()
        onNodeWithText("episode-01.mp4").assertIsDisplayed()
        onNodeWithText("content").assertIsDisplayed()

        state.onDragEnded()
        waitForIdle()
        onNodeWithTag(WindowDropTestTags.OVERLAY).assertDoesNotExist()
    }

    @Test
    fun `rejected files show the file name and the handlers' supported hints`() = runAniComposeUiTest {
        val state = WindowDropHostState()
        val handlers = listOf(PlayHandler())
        setContent {
            ProvideCompositionLocalsForPreview {
                WindowDropHost(handlers, Modifier.fillMaxSize(), state) {
                    Text("content")
                }
            }
        }

        state.onDragStarted(DragAndDropContent.FileList(listOf(Path("/downloads/notes.txt"))), handlers)
        waitForIdle()
        onNodeWithTag(WindowDropTestTags.OVERLAY).assertIsDisplayed()
        onNodeWithText("notes.txt").assertIsDisplayed()
        onNodeWithText("video files", substring = true).assertIsDisplayed()
    }
}
