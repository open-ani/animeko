/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WindowDropHostStateTest {
    private class FakeHandler(
        private val accepts: (DragAndDropContent?) -> Boolean,
        private val handles: Boolean = true,
    ) : WindowDropHandler {
        val dropped = mutableListOf<DragAndDropContent>()
        val preview = WindowDropPreview {}

        override fun onDragStarted(content: DragAndDropContent?): WindowDropPreview? =
            if (accepts(content)) preview else null

        override fun onDrop(content: DragAndDropContent): Boolean {
            dropped += content
            return handles
        }
    }

    private fun files(vararg names: String) = DragAndDropContent.FileList(names.map { Path("/downloads/$it") })

    @Test
    fun `first accepting handler takes over the session`() {
        val video = FakeHandler({ it is DragAndDropContent.FileList && it.files.any { f -> f.name.endsWith(".mp4") } })
        val installer = FakeHandler({ it is DragAndDropContent.FileList && it.files.any { f -> f.name.endsWith(".dmg") } })
        val state = WindowDropHostState()

        state.onDragStarted(files("ani.dmg"), listOf(video, installer))
        val session = assertIs<WindowDropSession.Accepted>(state.session)
        assertSame(installer, session.handler)
        assertSame(installer.preview, session.preview)

        assertTrue(state.onDrop(files("ani.dmg"), listOf(video, installer)))
        assertEquals(1, installer.dropped.size)
        assertEquals(0, video.dropped.size)

        state.onDragEnded()
        assertNull(state.session)
    }

    @Test
    fun `files nobody accepts are rejected with the first file name`() {
        val installer = FakeHandler({ false })
        val state = WindowDropHostState()

        state.onDragStarted(files("notes.txt", "ani.dmg"), listOf(installer))
        assertEquals("notes.txt", assertIs<WindowDropSession.Rejected>(state.session).fileName)

        assertFalse(state.onDrop(files("notes.txt"), listOf(installer)))
        assertEquals(0, installer.dropped.size)
    }

    @Test
    fun `non-file content and empty handler list show nothing`() {
        val state = WindowDropHostState()

        state.onDragStarted(DragAndDropContent.PlainText("hello"), listOf(FakeHandler({ false })))
        assertNull(state.session)

        state.onDragStarted(files("ani.dmg"), emptyList())
        assertNull(state.session)

        state.onDragStarted(null, listOf(FakeHandler({ false })))
        assertNull(state.session)
    }

    @Test
    fun `drop re-asks handlers when nothing took over during the drag`() {
        // 拖动阶段读不到内容 (null) 时该处理者不接管, 松手后按实际内容接管
        val installer = FakeHandler({ it is DragAndDropContent.FileList })
        val state = WindowDropHostState()

        state.onDragStarted(null, listOf(installer))
        assertNull(state.session)

        assertTrue(state.onDrop(files("ani.dmg"), listOf(installer)))
        assertEquals(1, installer.dropped.size)
    }

    @Test
    fun `a handler that took over without content yields to the one matching the dropped content`() {
        // 拖动阶段读不到内容, 两个处理者都愿意接管: 先展示第一个的预览, 松手后按实际内容交给安装包处理者
        val video = FakeHandler({ it == null || it is DragAndDropContent.FileList && it.files.any { f -> f.name.endsWith(".mp4") } })
        val installer = FakeHandler({ it == null || it is DragAndDropContent.FileList && it.files.any { f -> f.name.endsWith(".dmg") } })
        val state = WindowDropHostState()

        state.onDragStarted(null, listOf(video, installer))
        assertSame(video, assertIs<WindowDropSession.Accepted>(state.session).handler)

        assertTrue(state.onDrop(files("ani.dmg"), listOf(video, installer)))
        assertEquals(0, video.dropped.size)
        assertEquals(1, installer.dropped.size)
    }

    @Test
    fun `a handler that took over without content still receives content nobody matches`() {
        // 由展示了预览的处理者提示内容不受支持
        val installer = FakeHandler({ it == null }, handles = false)
        val state = WindowDropHostState()

        state.onDragStarted(null, listOf(installer))
        assertIs<WindowDropSession.Accepted>(state.session)

        assertFalse(state.onDrop(files("notes.txt"), listOf(installer)))
        assertEquals(1, installer.dropped.size)
    }

    @Test
    fun `a handler that took over by content keeps the session on drop`() {
        // 已按实际内容接管的会话不会在松手时改交给其他处理者
        val first = FakeHandler({ it is DragAndDropContent.FileList })
        val state = WindowDropHostState()

        state.onDragStarted(files("episode-01.mp4"), listOf(first))
        val later = FakeHandler({ true })
        assertTrue(state.onDrop(files("episode-01.mp4"), listOf(later, first)))
        assertEquals(1, first.dropped.size)
        assertEquals(0, later.dropped.size)
    }
}
