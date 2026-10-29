/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.imageviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.Sketch
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.ui.foundation.IMAGE_VIEWER_TEST_TAG
import me.him188.ani.app.ui.foundation.ImageViewerOverlay
import me.him188.ani.app.ui.foundation.LocalSketch
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.createDefaultSketch
import me.him188.ani.app.ui.foundation.rememberImageViewerHandler
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.framework.runOnSwingEdt
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.image_viewer_copied
import me.him188.ani.app.ui.lang.image_viewer_save_failed
import me.him188.ani.app.ui.lang.image_viewer_saved
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.asScopedHttpClient
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 交互测试: 查看器工具栏的缩放, 保存, 关闭.
 */
class ImageViewerContentTest {
    private class Env(
        val mainScheduler: TestCoroutineScheduler,
        val sketch: Sketch,
        val url: String,
        val exportDirectory: SystemPath,
        val closeCount: AtomicInteger,
        val toasts: CopyOnWriteArrayList<String>,
    )

    /** 等待 [condition] 成立, 期间在当前线程 (EDT) 上执行 Dispatchers.Main 的排队任务. */
    private fun Env.waitUntilPumpingMain(
        test: AniComposeUiTest,
        timeoutMillis: Long = 5_000,
        condition: () -> Boolean,
    ) {
        test.waitUntil(timeoutMillis = timeoutMillis) {
            mainScheduler.advanceUntilIdle()
            condition()
        }
    }

    private fun runViewerTest(
        fileSaver: ImageFileSaver = ImageFileSaver { true },
        imageClipboard: ImageClipboard? = null,
        size: DpSize = DpSize(800.dp, 600.dp),
        dismissOnSwipe: Boolean = true,
        useOverlay: Boolean = false,
        block: AniComposeUiTest.(Env) -> Unit,
    ) {
        val tempDirectory = SystemPaths.createTempDirectory("ani-image-viewer-content-test")
        val bytes = encodedRaster(width = 400, height = 300)
        val client = HttpClient(
            MockEngine {
                respond(content = bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
            },
        )
        val sketch = createDefaultSketch(
            PlatformContext.INSTANCE,
            client.asScopedHttpClient(),
            tempDirectory.resolve("sketch").absolutePath.toPath(),
        )
        val mainScheduler = TestCoroutineScheduler()
        val mainDispatcher = StandardTestDispatcher(mainScheduler)
        val env = Env(
            mainScheduler = mainScheduler,
            sketch = sketch,
            url = "https://example.com/viewer-${System.nanoTime()}.png",
            exportDirectory = tempDirectory.resolve("export"),
            closeCount = AtomicInteger(),
            toasts = CopyOnWriteArrayList(),
        )
        val toaster = object : Toaster {
            override fun toast(text: String) {
                env.toasts.add(text)
            }
        }
        try {
            // Sketch 和 zoomimage 都要求在 Swing EDT 上执行 (真实 App 里 Compose 就跑在 EDT 上), 因此整个测试在 EDT 上跑.
            // 测试线程占着 EDT 时 Dispatchers.Main (=Swing) 无法调度, 而 Sketch 用 Main 跑请求管线,
            // 所以把 Main 换成测试调度器, 在等待条件时由测试线程 (即 EDT) 手动泵.
            runOnSwingEdt {
                runAniComposeUiTest {
                    Dispatchers.setMain(mainDispatcher)
                    setContent {
                        ProvideCompositionLocalsForPreview {
                            CompositionLocalProvider(
                                LocalSketch provides sketch,
                                LocalToaster provides toaster,
                            ) {
                                if (useOverlay) {
                                    val handler = rememberImageViewerHandler()
                                    LaunchedEffect(handler) { handler.viewImage(env.url) }
                                    Box(
                                        Modifier.requiredSize(size.width, size.height)
                                            .background(Color.Magenta).testTag("ViewerHost"),
                                    ) {
                                        ImageViewerOverlay(handler) {
                                            handler.clear()
                                            env.closeCount.incrementAndGet()
                                        }
                                    }
                                } else {
                                    ImageViewerContent(
                                        model = env.url,
                                        onClose = { env.closeCount.incrementAndGet() },
                                        modifier = Modifier.requiredSize(size.width, size.height),
                                        fileSaver = fileSaver,
                                        imageClipboard = imageClipboard,
                                        dismissOnSwipe = dismissOnSwipe,
                                        exportDirectory = env.exportDirectory,
                                    )
                                }
                            }
                        }
                    }
                    // 图片加载完成后缩放控件才可用
                    env.waitUntilPumpingMain(this, timeoutMillis = 10_000) {
                        runCatching {
                            onNodeWithTag(ImageViewerTestTags.ZOOM_IN).assertIsEnabled()
                        }.isSuccess
                    }
                    block(env)
                }
            }
        } finally {
            Dispatchers.resetMain()
            sketch.shutdown()
            client.close()
            tempDirectory.deleteRecursively()
        }
    }

    @Test
    fun `close retains image content throughout the overlay exit`() = runViewerTest(useOverlay = true) { env ->
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = false
        onNodeWithTag(ImageViewerTestTags.CLOSE).performClick()
        env.mainScheduler.advanceUntilIdle()
        mainClock.advanceTimeByFrame()
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertExists()
        assertEquals(1, env.closeCount.get())
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `swipe exit keeps the revealed page visible on every frame`() = runViewerTest(useOverlay = true) { env ->
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = false
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f))
            up()
        }
        repeat(60) {
            mainClock.advanceTimeByFrame()
            env.mainScheduler.advanceUntilIdle()
            waitForIdle()
            if (env.closeCount.get() == 1) {
                val pixels = onNodeWithTag("ViewerHost").captureToImage().toPixelMap()
                assertEquals(Color.Magenta, pixels[pixels.width / 2, pixels.height / 2], "exit frame $it")
                onNodeWithTag(ImageViewerTestTags.CLOSE).assertDoesNotExist()
            }
        }
        assertEquals(1, env.closeCount.get())
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `zoom in and reset change scale`() = runViewerTest { env ->
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertIsDisplayed()
        val initial = scalePercent()
        assertTrue(initial > 0, "initial scale should be positive, was $initial")
        // 适应窗口时不能再缩小
        onNodeWithTag(ImageViewerTestTags.ZOOM_OUT).assertIsNotEnabled()
        onNodeWithTag(ImageViewerTestTags.RESET_ZOOM).assertIsNotEnabled()

        onNodeWithTag(ImageViewerTestTags.ZOOM_IN).performClick()
        env.waitUntilPumpingMain(this) { scalePercent() > initial }
        val zoomed = scalePercent()
        assertTrue(zoomed > initial, "zoomed=$zoomed initial=$initial")
        onNodeWithTag(ImageViewerTestTags.ZOOM_OUT).assertIsEnabled()

        onNodeWithTag(ImageViewerTestTags.RESET_ZOOM).performClick()
        env.waitUntilPumpingMain(this) { scalePercent() == initial }
        onNodeWithTag(ImageViewerTestTags.ZOOM_OUT).assertIsNotEnabled()
    }

    @Test
    fun `tap toggles controls and drag does not`() = runViewerTest { env ->
        // 拖动保持控件可见, 单击切换控件显隐.
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput {
            down(center)
            moveBy(Offset(200f, 0f))
            up()
        }
        // 让 detectTapGestures 的双击等待超时后再判断
        mainClock.advanceTimeBy(1_000)
        env.mainScheduler.advanceUntilIdle()
        waitForIdle()
        assertEquals(0, env.closeCount.get())
        onNodeWithTag(ImageViewerTestTags.CLOSE).assertIsDisplayed()

        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput { down(center); up() }
        env.waitUntilPumpingMain(this) {
            runCatching { onNodeWithTag(ImageViewerTestTags.CLOSE).assertDoesNotExist() }.isSuccess
        }
        assertEquals(0, env.closeCount.get())
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput { down(center); up() }
        env.waitUntilPumpingMain(this) {
            runCatching { onNodeWithTag(ImageViewerTestTags.CLOSE).assertIsDisplayed() }.isSuccess
        }
        assertEquals(0, env.closeCount.get())
    }

    @Test
    fun `double tap zooms without hiding controls`() = runViewerTest { env ->
        val initial = scalePercent()
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput { doubleClick(center) }
        env.waitUntilPumpingMain(this) { scalePercent() > initial }
        onNodeWithTag(ImageViewerTestTags.CLOSE).assertIsDisplayed()
        onNodeWithTag(ImageViewerTestTags.TOOLBAR).assertIsDisplayed()
        assertEquals(0, env.closeCount.get())
    }

    @Test
    fun `vertical drag follows the finger and short drag springs back`() = runViewerTest { env ->
        val image = onNodeWithTag(IMAGE_VIEWER_TEST_TAG)
        val initial = image.fetchSemanticsNode().boundsInRoot
        image.performTouchInput {
            down(center)
            moveBy(Offset(20f, 70f))
        }
        val dragged = image.fetchSemanticsNode().boundsInRoot
        assertTrue(dragged.center.y > initial.center.y)
        assertTrue(dragged.center.x > initial.center.x)
        assertTrue(dragged.width < initial.width)
        onNodeWithTag(ImageViewerTestTags.CLOSE).assertDoesNotExist()
        image.performTouchInput {
            advanceEventTime(500)
            up()
        }
        env.waitUntilPumpingMain(this) {
            image.fetchSemanticsNode().boundsInRoot == initial
        }
        assertEquals(0, env.closeCount.get())
        onNodeWithTag(ImageViewerTestTags.CLOSE).assertIsDisplayed()
    }

    @Test
    fun `swipe down dismisses the image`() = runViewerTest { env ->
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f))
            up()
        }
        env.waitUntilPumpingMain(this) { env.closeCount.get() == 1 }
    }

    @Test
    fun `swipe up dismisses the image`() = runViewerTest { env ->
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, -200f))
            up()
        }
        env.waitUntilPumpingMain(this) { env.closeCount.get() == 1 }
    }

    @Test
    fun `fast short swipe dismisses the image`() = runViewerTest { env ->
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput {
            down(center)
            repeat(4) { moveBy(Offset(0f, 20f), delayMillis = 8) }
            up()
        }
        env.waitUntilPumpingMain(this) { env.closeCount.get() == 1 }
    }

    @Test
    fun `second finger cancels swipe dismissal`() = runViewerTest { env ->
        val image = onNodeWithTag(IMAGE_VIEWER_TEST_TAG)
        val initial = image.fetchSemanticsNode().boundsInRoot
        image.performTouchInput {
            down(0, center)
            moveBy(Offset(0f, 200f))
            down(1, center + Offset(80f, 0f))
            up(1)
            up(0)
        }
        env.waitUntilPumpingMain(this) { image.fetchSemanticsNode().boundsInRoot == initial }
        assertEquals(0, env.closeCount.get())
        onNodeWithTag(ImageViewerTestTags.CLOSE).assertIsDisplayed()
    }

    @Test
    fun `independent window does not dismiss on swipe`() = runViewerTest(dismissOnSwipe = false) { env ->
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f))
            up()
        }
        mainClock.advanceTimeBy(1_000)
        env.mainScheduler.advanceUntilIdle()
        assertEquals(0, env.closeCount.get())
        onNodeWithTag(ImageViewerTestTags.CLOSE).assertIsDisplayed()
    }

    @Test
    fun `vertical drag pans a zoomed image without dismissing`() = runViewerTest { env ->
        val initial = scalePercent()
        onNodeWithTag(ImageViewerTestTags.ZOOM_IN).performClick()
        env.waitUntilPumpingMain(this) { scalePercent() > initial }
        val image = onNodeWithTag(IMAGE_VIEWER_TEST_TAG)
        val bounds = image.fetchSemanticsNode().boundsInRoot
        image.performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f))
            up()
        }
        mainClock.advanceTimeBy(1_000)
        env.mainScheduler.advanceUntilIdle()
        assertEquals(0, env.closeCount.get())
        assertEquals(bounds, image.fetchSemanticsNode().boundsInRoot)
        onNodeWithTag(ImageViewerTestTags.CLOSE).assertIsDisplayed()
    }

    @Test
    fun `portrait controls fit a narrow viewport`() = runViewerTest(size = DpSize(280.dp, 600.dp)) {
        onNodeWithTag(ImageViewerTestTags.CLOSE).assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        val image = onNodeWithTag(IMAGE_VIEWER_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val toolbar = onNodeWithTag(ImageViewerTestTags.TOOLBAR).fetchSemanticsNode().boundsInRoot
        assertTrue(toolbar.width > toolbar.height)
        assertTrue(toolbar.left >= image.left && toolbar.right <= image.right)
        assertTrue(toolbar.top > image.center.y)
        onNodeWithTag(ImageViewerTestTags.SAVE).assertWidthIsEqualTo(48.dp).assertIsDisplayed()
        onNodeWithTag(ImageViewerTestTags.MORE).assertDoesNotExist()
    }

    @Test
    fun `landscape controls occupy the right side`() = runViewerTest(size = DpSize(600.dp, 280.dp)) {
        val image = onNodeWithTag(IMAGE_VIEWER_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val toolbar = onNodeWithTag(ImageViewerTestTags.TOOLBAR).fetchSemanticsNode().boundsInRoot
        val close = onNodeWithTag(ImageViewerTestTags.CLOSE).fetchSemanticsNode().boundsInRoot
        assertTrue(toolbar.height > toolbar.width)
        assertTrue(toolbar.left > image.center.x)
        assertTrue(toolbar.top >= close.bottom)
        assertTrue(toolbar.bottom <= image.bottom)
        onNodeWithTag(ImageViewerTestTags.CLOSE).assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        onNodeWithTag(ImageViewerTestTags.MORE).assertIsDisplayed()
        onNodeWithTag(ImageViewerTestTags.MORE).performClick()
        onNodeWithTag(ImageViewerTestTags.SAVE).assertIsDisplayed()
    }

    @Test
    fun `all actions are visible without a menu when space permits`() = runViewerTest(imageClipboard = {}) {
        listOf(
            ImageViewerTestTags.ZOOM_IN, ImageViewerTestTags.ZOOM_OUT, ImageViewerTestTags.RESET_ZOOM,
            ImageViewerTestTags.SAVE, ImageViewerTestTags.COPY,
        ).forEach { tag ->
            onNodeWithTag(tag).assertIsDisplayed().assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        }
        onNodeWithTag(ImageViewerTestTags.MORE).assertDoesNotExist()
    }

    @Test
    fun `only overflowing actions appear in the menu`() = runViewerTest(
        imageClipboard = {},
        size = DpSize(280.dp, 600.dp),
    ) {
        onNodeWithTag(ImageViewerTestTags.RESET_ZOOM).assertIsDisplayed()
        onNodeWithTag(ImageViewerTestTags.SAVE).assertDoesNotExist()
        onNodeWithTag(ImageViewerTestTags.COPY).assertDoesNotExist()
        onNodeWithTag(ImageViewerTestTags.MORE).performClick()
        onNodeWithTag(ImageViewerTestTags.SAVE).assertIsDisplayed()
        onNodeWithTag(ImageViewerTestTags.COPY).assertIsDisplayed()
    }

    @Test
    fun `close button calls onClose`() = runViewerTest { env ->
        onNodeWithTag(ImageViewerTestTags.CLOSE).performClick()
        waitForIdle()
        assertEquals(1, env.closeCount.get())
    }

    @Test
    fun `save exports the image and reports success`() {
        val saved = CopyOnWriteArrayList<ImageViewerExportedFile>()
        runViewerTest(fileSaver = { saved.add(it); true }, size = DpSize(220.dp, 600.dp)) { env ->
            onNodeWithTag(ImageViewerTestTags.MORE).performClick()
            env.waitUntilPumpingMain(this, timeoutMillis = 10_000) {
                runCatching { onNodeWithTag(ImageViewerTestTags.SAVE).assertIsEnabled() }.isSuccess
            }
            onNodeWithTag(ImageViewerTestTags.SAVE).performClick()
            env.waitUntilPumpingMain(this) { env.toasts.isNotEmpty() }
            assertEquals(1, saved.size)
            assertEquals("png", saved.single().extension)
            assertTrue(saved.single().path.absolutePath.startsWith(env.exportDirectory.absolutePath))
            assertEquals(listOf(runBlocking { getString(Lang.image_viewer_saved) }), env.toasts)
        }
    }

    @Test
    fun `copy button and shortcut copy the exported image`() {
        val copied = CopyOnWriteArrayList<ImageViewerExportedFile>()
        runViewerTest(imageClipboard = { copied.add(it) }, size = DpSize(280.dp, 600.dp)) { env ->
            onNodeWithTag(ImageViewerTestTags.MORE).performClick()
            env.waitUntilPumpingMain(this, timeoutMillis = 10_000) {
                runCatching { onNodeWithTag(ImageViewerTestTags.COPY).assertIsEnabled() }.isSuccess
            }
            onNodeWithTag(ImageViewerTestTags.COPY).performClick()
            env.waitUntilPumpingMain(this) { env.toasts.size == 1 }
            assertEquals(1, copied.size)
            assertEquals("png", copied.single().extension)

            // 图片加载后自动获得焦点, Ctrl+C 直接可用
            onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performKeyInput {
                withKeyDown(Key.CtrlLeft) { pressKey(Key.C) }
            }
            env.waitUntilPumpingMain(this) { env.toasts.size == 2 }
            assertEquals(2, copied.size)
            assertEquals(List(2) { runBlocking { getString(Lang.image_viewer_copied) } }, env.toasts)
        }
    }

    @Test
    fun `no copy button when the platform has no image clipboard`() = runViewerTest(imageClipboard = null) {
        onNodeWithTag(ImageViewerTestTags.COPY).assertDoesNotExist()
        onNodeWithTag(ImageViewerTestTags.SAVE).assertIsDisplayed()
        onNodeWithTag(ImageViewerTestTags.MORE).assertDoesNotExist()
    }

    @Test
    fun `cancelled save shows nothing, failed save reports error`() {
        val attempts = AtomicInteger()
        runViewerTest(
            size = DpSize(220.dp, 600.dp),
            fileSaver = {
                if (attempts.getAndIncrement() == 0) false else error("disk full")
            },
        ) { env ->
            onNodeWithTag(ImageViewerTestTags.MORE).performClick()
            env.waitUntilPumpingMain(this, timeoutMillis = 10_000) {
                runCatching { onNodeWithTag(ImageViewerTestTags.SAVE).assertIsEnabled() }.isSuccess
            }
            onNodeWithTag(ImageViewerTestTags.SAVE).performClick()
            env.waitUntilPumpingMain(this) { attempts.get() == 1 }
            waitForIdle()
            assertTrue(env.toasts.isEmpty(), "cancel should not toast, got ${env.toasts}")

            onNodeWithTag(ImageViewerTestTags.MORE).performClick()
            onNodeWithTag(ImageViewerTestTags.SAVE).performClick()
            env.waitUntilPumpingMain(this) { env.toasts.isNotEmpty() }
            assertEquals(listOf(runBlocking { getString(Lang.image_viewer_save_failed) }), env.toasts)
        }
    }

    private fun AniComposeUiTest.scalePercent(): Int {
        val node = onNodeWithTag(IMAGE_VIEWER_TEST_TAG).fetchSemanticsNode()
        val text = node.config.getOrNull(SemanticsProperties.StateDescription) ?: return -1
        return text.removeSuffix("%").toIntOrNull() ?: -1
    }

    private fun encodedRaster(width: Int, height: Int): ByteArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        try {
            surface.canvas.clear(0xFF6750A4.toInt())
            return requireNotNull(
                surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG, 100),
            ).bytes
        } finally {
            surface.close()
        }
    }
}
