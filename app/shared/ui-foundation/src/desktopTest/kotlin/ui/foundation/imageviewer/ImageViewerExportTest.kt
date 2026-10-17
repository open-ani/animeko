/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.imageviewer

import com.github.panpf.sketch.PlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.ui.foundation.createDefaultSketch
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import me.him188.ani.utils.ktor.asScopedHttpClient
import okio.Path.Companion.toPath
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageViewerExportTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 9)

    @Test
    fun `file name from url, extension from content`() {
        assertEquals("cover" to "png", deriveImageFileName("https://a.com/pics/cover.jpg?x=1#frag", png))
        assertEquals("cover" to "jpg", deriveImageFileName("https://a.com/pics/cover.jpg", jpeg))
    }

    @Test
    fun `unknown content falls back to url extension then jpg`() {
        val unknown = byteArrayOf(1, 2, 3)
        assertEquals("cover" to "webp", deriveImageFileName("https://a.com/cover.webp", unknown))
        assertEquals("image" to "jpg", deriveImageFileName("https://a.com/", unknown))
        // 非图片扩展名当作文件名的一部分
        assertEquals("abc.php" to "jpg", deriveImageFileName("https://a.com/abc.php", unknown))
    }

    @Test
    fun `generic size segment is prefixed with the previous segment`() {
        assertEquals("277554_large" to "jpg", deriveImageFileName("https://static.myani.org/bangumi/subjects/277554/large", jpeg))
        assertEquals("large" to "jpg", deriveImageFileName("https://a.com/large", jpeg))
        assertEquals("large" to "png", deriveImageFileName("https://a.com/x/large.png", png))
    }

    @Test
    fun `invalid characters are sanitized`() {
        assertEquals("a_b_c" to "png", deriveImageFileName("https://a.com/a:b*c.png", png))
        assertEquals("image" to "png", deriveImageFileName("https://a.com/....png", png))
    }

    @Test
    fun `export writes bytes once and reuses the file`() = runTest {
        val tempDirectory = SystemPaths.createTempDirectory("ani-image-viewer-export-test")
        val requests = AtomicInteger()
        val client = HttpClient(
            MockEngine {
                requests.incrementAndGet()
                respond(content = png, headers = headersOf(HttpHeaders.ContentType, "image/png"))
            },
        )
        val sketch = createDefaultSketch(
            PlatformContext.INSTANCE,
            client.asScopedHttpClient(),
            tempDirectory.resolve("sketch").absolutePath.toPath(),
        )
        val exportDirectory = tempDirectory.resolve("export")
        try {
            val url = "https://example.com/images/cover.jpg?size=large"
            val first = sketch.exportImageForViewer(url, exportDirectory)
            assertEquals("cover.png", first.fileName)
            assertTrue(first.path.exists())
            assertContentEquals(png, first.path.readBytes())
            assertTrue(first.path.absolutePath.startsWith(exportDirectory.absolutePath))

            // 第二次导出同一 URL: 复用文件, 且命中 Sketch 下载缓存, 不再请求网络
            first.path.writeBytes(byteArrayOf(42))
            val second = sketch.exportImageForViewer(url, exportDirectory)
            assertEquals(first.path, second.path)
            assertContentEquals(byteArrayOf(42), second.path.readBytes())
            assertEquals(1, requests.get())

            // 不同 URL 但同名文件互不覆盖
            val other = sketch.exportImageForViewer("https://example.com/other/cover.jpg", exportDirectory)
            assertEquals("cover.png", other.fileName)
            assertFalse(other.path == first.path)
        } finally {
            sketch.shutdown()
            client.close()
            tempDirectory.deleteRecursively()
        }
    }
}
