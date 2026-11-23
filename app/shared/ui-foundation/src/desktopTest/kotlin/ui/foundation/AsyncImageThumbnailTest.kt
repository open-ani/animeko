/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.github.panpf.sketch.AsyncImageState
import com.github.panpf.sketch.Image
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ImageOptions
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.ImageResult
import com.github.panpf.sketch.state.ColorPainterStateImage
import com.github.panpf.sketch.target.Target
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.ktor.asScopedHttpClient
import okio.Path.Companion.toPath
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 缩略图先于原图显示: [AsyncImage] 的 `thumbnailModel` 与 [CachedUriStateImage]. */
class AsyncImageThumbnailTest {
    private val thumbUrl = "https://example.com/thumb-${System.nanoTime()}.png"
    private val largeUrl = "https://example.com/large-${System.nanoTime()}.png"

    /** 缩略图 7x5 立即返回; 原图 70x50 先等 [beforeLarge] 返回. */
    private class Env(beforeLarge: suspend () -> Unit) {
        private val tempDirectory = SystemPaths.createTempDirectory("ani-sketch-thumbnail-test")
        val client = HttpClient(
            MockEngine { request ->
                val bytes = if (request.url.encodedPath.startsWith("/large")) {
                    beforeLarge()
                    encodedRaster(width = 70, height = 50)
                } else {
                    encodedRaster(width = 7, height = 5)
                }
                respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
            },
        )
        val sketch: Sketch = createDefaultSketch(
            PlatformContext.INSTANCE,
            client.asScopedHttpClient(),
            tempDirectory.absolutePath.toPath(),
        )

        fun close() {
            sketch.shutdown()
            client.close()
            tempDirectory.deleteRecursively()
        }
    }

    /** 模拟列表: 按卡片比例裁剪解码缩略图, 尺寸与详情页无关. */
    private suspend fun Sketch.decodeThumbnailLikeAList() {
        assertIs<ImageResult.Success>(
            execute(
                ImageRequest(PlatformContext.INSTANCE, thumbUrl) {
                    size(20, 20)
                    configureAniImageRequest(contentScale = ContentScale.Crop, alignment = Alignment.Center)
                },
            ),
        )
    }

    @Test
    fun `cached uri state image reuses any decode of the uri from the memory cache`() = runTest {
        val env = Env {}
        try {
            env.sketch.decodeThumbnailLikeAList()
            val request = ImageRequest(PlatformContext.INSTANCE, largeUrl)
            val cached = assertNotNull(CachedUriStateImage(thumbUrl).getImage(env.sketch, request, null))
            assertEquals(5, cached.width)
            assertEquals(5, cached.height)
            // 只认 Sketch 为该 uri 生成的 key: 其他 uri 和仅为前缀的 uri 都不匹配
            assertNull(CachedUriStateImage(largeUrl).getImage(env.sketch, request, null))
            assertNull(CachedUriStateImage(thumbUrl.dropLast(1)).getImage(env.sketch, request, null))
            val fallback = ColorPainterStateImage(Color.Red)
            assertEquals(
                fallback.getImage(env.sketch, request, null),
                CachedUriStateImage(largeUrl, fallback).getImage(env.sketch, request, null),
            )
        } finally {
            env.close()
        }
    }

    @Test
    fun `cached uri state image falls back to the global placeholder like previews rely on`() {
        val globalPlaceholder = ColorPainterStateImage(Color.Blue)
        val sketch = Sketch.Builder(PlatformContext.INSTANCE)
            .componentLoaderEnabled(false)
            .globalImageOptions(ImageOptions { placeholder(globalPlaceholder) })
            .build()
        try {
            val request = ImageRequest(PlatformContext.INSTANCE, largeUrl)
            assertEquals(
                globalPlaceholder.getImage(sketch, request, null),
                CachedUriStateImage(thumbUrl).getImage(sketch, request, null),
            )
        } finally {
            sketch.shutdown()
        }
    }

    @Test
    fun `thumbnail is displayed until the large image replaces it`() = runTest {
        val thumbnailDisplayed = CompletableDeferred<Unit>()
        val env = Env { withTimeoutOrNull(10_000) { thumbnailDisplayed.await() } }
        try {
            env.sketch.decodeThumbnailLikeAList()
            val target = RecordingTarget(onImage = { thumbnailDisplayed.complete(Unit) })
            val result = assertIs<ImageResult.Success>(
                env.sketch.execute(
                    ImageRequest(PlatformContext.INSTANCE, largeUrl) {
                        size(200, 200)
                        configureAniImageRequest(contentScale = ContentScale.Fit, alignment = Alignment.Center)
                        configureAniImagePlaceholder(placeholder = null, thumbnail = thumbUrl)
                        configureAniImageCrossfade(crossfade = false)
                        target(target)
                    },
                ),
            )
            assertEquals(70, result.image.width)
            assertEquals(50, result.image.height)
            // 列表解码过的缩略图立即占位, 然后是按本次尺寸解码的缩略图, 最后才是原图
            assertEquals(listOf("start:5x5", "success:7x5", "success:70x50"), target.events)
        } finally {
            env.close()
        }
    }

    @Test
    fun `async image keeps showing the thumbnail until the large image loads`() {
        val largeGate = CompletableDeferred<Unit>()
        val env = Env { largeGate.await() }
        val successes = CopyOnWriteArrayList<AniImageLoadSuccess>()
        var state: AsyncImageState? = null
        try {
            runAniComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalSketch provides env.sketch) {
                        val imageState = rememberAsyncImageState().also { state = it }
                        AniAsyncImage(
                            model = largeUrl,
                            contentDescription = null,
                            state = imageState,
                            modifier = Modifier.size(100.dp),
                            onSuccess = { successes += it },
                            contentScale = ContentScale.Fit,
                            crossfade = false,
                            thumbnailModel = thumbUrl,
                        )
                    }
                }
                waitUntil(timeoutMillis = 5_000) {
                    state?.painter?.intrinsicSize == Size(7f, 5f)
                }
                assertTrue(successes.isEmpty(), "原图未加载完成前不应回调 onSuccess")
                largeGate.complete(Unit)
                waitUntil(timeoutMillis = 5_000) {
                    state?.painter?.intrinsicSize == Size(70f, 50f)
                }
                waitUntil(timeoutMillis = 5_000) { successes.isNotEmpty() }
                assertEquals(listOf(70 to 50), successes.map { it.width to it.height })
            }
        } finally {
            env.close()
        }
    }
}

/** 记录 Sketch 交给显示目标的每张图: 占位图与 (缩略图、原图的) 成功结果. */
private class RecordingTarget(private val onImage: () -> Unit) : Target {
    val events = CopyOnWriteArrayList<String>()

    override fun onStart(sketch: Sketch, request: ImageRequest, placeholder: Image?) {
        events += "start:${placeholder?.let { "${it.width}x${it.height}" }}"
    }

    override fun onSuccess(sketch: Sketch, request: ImageRequest, result: ImageResult.Success, image: Image) {
        events += "success:${image.width}x${image.height}"
        onImage()
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
    override fun toString(): String = "RecordingTarget(events=$events)"
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
