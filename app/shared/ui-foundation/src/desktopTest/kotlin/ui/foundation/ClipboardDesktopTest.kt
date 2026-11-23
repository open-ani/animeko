/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.platform.awtClipboard
import kotlinx.coroutines.runBlocking
import java.awt.datatransfer.Clipboard as AwtClipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ClipboardDesktopTest {
    private class FakeClipboard : Clipboard {
        val awt = AwtClipboard("test")
        var entry: ClipEntry? = null

        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            entry = clipEntry
        }

        @Deprecated("Use platform-specific extension to get platform reference")
        override val nativeClipboard: NativeClipboard get() = awt
    }

    @Test
    fun `read text from a text entry`() = runBlocking {
        assertEquals("hello", textClipEntryOf("hello").readText())
    }

    @Test
    fun `read text from an entry without text`() = runBlocking {
        val noText = object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = emptyArray()
            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = false
            override fun getTransferData(flavor: DataFlavor): Any = error("unsupported")
        }
        assertNull(ClipEntry(noText).readText())
    }

    @Test
    fun `written text is transformed`() = runBlocking {
        val delegate = FakeClipboard()
        val clipboard = delegate.transformTextOnWrite { it.uppercase() }
        clipboard.setClipEntryText("abc")
        assertEquals("ABC", delegate.getClipEntryText())
        assertEquals("ABC", clipboard.getClipEntryText())
    }

    @Test
    fun `entry is written as is when the transform returns null`() = runBlocking {
        val delegate = FakeClipboard()
        val clipboard = delegate.transformTextOnWrite { null }
        val entry = ClipEntry(StringSelection("abc"))
        clipboard.setClipEntry(entry)
        assertSame(entry, delegate.entry)
        clipboard.setClipEntry(null)
        assertNull(delegate.entry)
    }

    @Test
    fun `native clipboard is the delegate clipboard`() {
        val delegate = FakeClipboard()
        assertSame(delegate.awt, delegate.transformTextOnWrite { it }.awtClipboard)
    }

    @Test
    fun `entry without text is written as is`() = runBlocking {
        val delegate = FakeClipboard()
        val clipboard = delegate.transformTextOnWrite { error("must not transform") }
        val image = object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
            override fun getTransferData(flavor: DataFlavor): Any = error("not needed")
        }
        val entry = ClipEntry(image)
        clipboard.setClipEntry(entry)
        assertSame(image, delegate.entry?.asAwtTransferable)
    }
}
