/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import kotlinx.io.files.Path
import me.him188.ani.app.ui.foundation.DragAndDropContent
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodeVideoDropHandlerTest {
    private val played = mutableListOf<SystemPath>()
    private val handler = EpisodeVideoDropHandler { played += it }

    private fun files(vararg names: String) = DragAndDropContent.FileList(names.map { Path("/videos/$it") })

    @Test
    fun `takes over only file lists with a video file or unknown content`() {
        assertNotNull(handler.onDragStarted(files("episode-01.ass", "episode-01.mkv")))
        assertNotNull(handler.onDragStarted(null))

        assertNull(handler.onDragStarted(files("Ani-4.12.0.dmg")))
        assertNull(handler.onDragStarted(files()))
        assertNull(handler.onDragStarted(DragAndDropContent.PlainText("/videos/episode-01.mkv")))
        assertNull(handler.onDragStarted(DragAndDropContent.Unsupported))
    }

    @Test
    fun `plays the first video file on drop`() {
        assertTrue(handler.onDrop(files("episode-01.ass", "episode-01.mkv", "episode-02.mkv")))
        assertEquals(listOf(Path("/videos/episode-01.mkv").inSystem), played)
    }

    @Test
    fun `ignores drops without a video file`() {
        assertFalse(handler.onDrop(files("Ani-4.12.0.dmg")))
        assertFalse(handler.onDrop(DragAndDropContent.PlainText("/videos/episode-01.mkv")))
        assertFalse(handler.onDrop(DragAndDropContent.Unsupported))
        assertTrue(played.isEmpty())
    }
}
