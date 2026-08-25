/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.decode.SvgDecoder
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.ImageResult
import com.github.panpf.sketch.request.LoadState
import com.github.panpf.sketch.source.DataFrom
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.ktor.asScopedHttpClient
import okio.Path.Companion.toPath
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AniImageLoaderConfigurationTest {
    @Test
    fun `default Sketch uses bounded caches and explicit components`() {
        val tempDirectory = SystemPaths.createTempDirectory("ani-sketch-cache-test")
        val cacheDirectory = tempDirectory.absolutePath.toPath()
        val client = HttpClient(MockEngine { error("Network must not be used by this test") })
        val sketch = createDefaultSketch(
            PlatformContext.INSTANCE,
            client.asScopedHttpClient(),
            cacheDirectory,
        )

        try {
            assertEquals(64L * 1024L * 1024L, sketch.memoryCache.maxSize)
            assertEquals(0L, sketch.memoryCache.size)
            assertEquals(100L * 1024L * 1024L, sketch.downloadCache.maxSize)
            assertEquals(cacheDirectory.resolve("download"), sketch.downloadCache.directory)
            assertEquals(cacheDirectory.resolve("result"), sketch.resultCache.directory)

            val options = requireNotNull(sketch.globalImageOptions)
            assertEquals(CachePolicy.ENABLED, options.downloadCachePolicy)
            assertEquals(CachePolicy.ENABLED, options.memoryCachePolicy)
            assertEquals(CachePolicy.DISABLED, options.resultCachePolicy)
            assertNull(options.transitionFactory)

            assertTrue(
                sketch.components.registry.fetchers.any { it is ScopedHttpClientHttpUriFetcherFactory },
                "Ani's scoped HTTP fetcher must be registered",
            )
            assertTrue(
                sketch.components.registry.decoders.any { it is SvgDecoder.Factory },
                "SVG decoding must be registered explicitly",
            )
        } finally {
            sketch.shutdown()
            client.close()
            tempDirectory.deleteRecursively()
        }
    }

    @Test
    fun `default Sketch reuses decoded images from memory cache`() = runTest {
        val bytes = encodedRaster(EncodedImageFormat.PNG)
        var calls = 0
        val client = HttpClient(
            MockEngine {
                calls++
                respond(
                    content = bytes,
                    headers = headersOf(HttpHeaders.ContentType, "image/png"),
                )
            },
        )
        val sketch = createDefaultSketch(PlatformContext.INSTANCE, client.asScopedHttpClient())
        val url = "https://example.com/memory-${System.nanoTime()}.png"

        try {
            fun request() = ImageRequest(PlatformContext.INSTANCE, url) {
                size(7, 5)
                downloadCachePolicy(CachePolicy.DISABLED)
                resultCachePolicy(CachePolicy.DISABLED)
            }

            val first = assertIs<ImageResult.Success>(sketch.execute(request()))
            val second = assertIs<ImageResult.Success>(sketch.execute(request()))

            assertEquals(DataFrom.NETWORK, first.dataFrom)
            assertEquals(DataFrom.MEMORY_CACHE, second.dataFrom)
            assertEquals(1, calls)
            assertTrue(sketch.memoryCache.size > 0L)
        } finally {
            sketch.shutdown()
            client.close()
        }
    }

    @Test
    fun `custom HTTP pipeline decodes raster formats and SVG and reports HTTP errors`() = runTest {
        val rasterResponses = mapOf(
            "/image.jpg" to (encodedRaster(EncodedImageFormat.JPEG) to "image/jpeg"),
            "/image.png" to (encodedRaster(EncodedImageFormat.PNG) to "image/png"),
            "/image.webp" to (encodedRaster(EncodedImageFormat.WEBP) to "image/webp"),
        )
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="11" height="6">
              <rect width="11" height="6" fill="#6750A4"/>
            </svg>
        """.trimIndent()
        val client = HttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                when {
                    path == "/image.svg" -> respond(
                        content = svg,
                        headers = headersOf(HttpHeaders.ContentType, "image/svg+xml"),
                    )

                    path == "/missing" -> respond("missing", HttpStatusCode.NotFound)
                    else -> {
                        val (bytes, contentType) = requireNotNull(rasterResponses[path])
                        respond(
                            content = bytes,
                            headers = headersOf(HttpHeaders.ContentType, contentType),
                        )
                    }
                }
            },
        )
        val sketch = createDefaultSketch(PlatformContext.INSTANCE, client.asScopedHttpClient())

        try {
            rasterResponses.keys.forEach { path ->
                val success = assertIs<ImageResult.Success>(
                    sketch.execute(uncachedRequest("https://example.com$path")),
                )
                assertEquals(7, success.image.width, path)
                assertEquals(5, success.image.height, path)
                val publicResult = success.toAniImageLoadSuccess()
                assertNotNull(publicResult.bitmap, path)
                assertEquals(7, publicResult.width, path)
                assertEquals(5, publicResult.height, path)
            }

            val svgSuccess = assertIs<ImageResult.Success>(
                sketch.execute(uncachedRequest("https://example.com/image.svg", width = 11, height = 6)),
            )
            assertEquals(11, svgSuccess.image.width)
            assertEquals(6, svgSuccess.image.height)

            assertIs<ImageResult.Error>(
                sketch.execute(uncachedRequest("https://example.com/missing")),
            )
        } finally {
            sketch.shutdown()
            client.close()
        }
    }

    @Test
    fun `crop decodes both dimensions at the target size`() = runTest {
        val bytes = encodedRaster(EncodedImageFormat.PNG, width = 600, height = 900)
        val client = HttpClient(
            MockEngine {
                respond(
                    content = bytes,
                    headers = headersOf(HttpHeaders.ContentType, "image/png"),
                )
            },
        )
        val sketch = createDefaultSketch(PlatformContext.INSTANCE, client.asScopedHttpClient())

        try {
            val success = assertIs<ImageResult.Success>(
                sketch.execute(
                    ImageRequest(PlatformContext.INSTANCE, "https://example.com/portrait.png") {
                        size(300, 100)
                        configureAniImageRequest(
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                        )
                        downloadCachePolicy(CachePolicy.DISABLED)
                        resultCachePolicy(CachePolicy.DISABLED)
                        memoryCachePolicy(CachePolicy.DISABLED)
                    },
                ),
            )

            assertEquals(300, success.image.width)
            assertEquals(100, success.image.height)
            assertEquals(600, success.imageInfo.width)
            assertEquals(900, success.imageInfo.height)
            assertEquals(600, success.toAniImageLoadSuccess().width)
            assertEquals(900, success.toAniImageLoadSuccess().height)
        } finally {
            sketch.shutdown()
            client.close()
        }
    }

    @Test
    fun `disk cache re-decodes the original at each requested size without retaining images`() = runTest {
        val bytes = encodedRaster(EncodedImageFormat.PNG, width = 600, height = 900)
        var calls = 0
        val client = HttpClient(
            MockEngine {
                calls++
                respond(
                    content = bytes,
                    headers = headersOf(HttpHeaders.ContentType, "image/png"),
                )
            },
        )
        val sketch = createDefaultSketch(PlatformContext.INSTANCE, client.asScopedHttpClient())
        val url = "https://example.com/cover-${System.nanoTime()}.png"

        try {
            fun request(width: Int, height: Int) = ImageRequest(PlatformContext.INSTANCE, url) {
                size(width, height)
                configureAniImageRequest(
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                )
                downloadCachePolicy(CachePolicy.ENABLED)
                resultCachePolicy(CachePolicy.DISABLED)
                memoryCachePolicy(CachePolicy.DISABLED)
            }

            val small = assertIs<ImageResult.Success>(sketch.execute(request(100, 150)))
            val large = assertIs<ImageResult.Success>(sketch.execute(request(300, 450)))

            assertEquals(DataFrom.NETWORK, small.dataFrom)
            assertEquals(DataFrom.DOWNLOAD_CACHE, large.dataFrom)
            assertEquals(1, calls)
            assertTrue(large.image.width > small.image.width)
            assertTrue(large.image.height > small.image.height)
            assertEquals(0L, sketch.memoryCache.size)
            assertTrue(sketch.memoryCache.keys().isEmpty())
        } finally {
            sketch.shutdown()
            client.close()
        }
    }

    @Test
    fun `download cache survives Sketch replacement without network`() = runTest {
        val tempDirectory = SystemPaths.createTempDirectory("ani-sketch-persistent-cache-test")
        val cacheDirectory = tempDirectory.absolutePath.toPath()
        val bytes = encodedRaster(EncodedImageFormat.PNG)
        var calls = 0
        val client = HttpClient(
            MockEngine {
                calls++
                respond(
                    content = bytes,
                    headers = headersOf(HttpHeaders.ContentType, "image/png"),
                )
            },
        )
        val scopedClient = client.asScopedHttpClient()
        var sketch = createDefaultSketch(PlatformContext.INSTANCE, scopedClient, cacheDirectory)
        val downloadKey = "ani-sketch-migration-download-test"

        try {
            fun downloadRequest() = ImageRequest(PlatformContext.INSTANCE, "https://example.com/download.png") {
                downloadCachePolicy(CachePolicy.ENABLED)
                downloadCacheKey(downloadKey)
                resultCachePolicy(CachePolicy.DISABLED)
                memoryCachePolicy(CachePolicy.DISABLED)
            }

            assertEquals(DataFrom.NETWORK, assertIs<ImageResult.Success>(sketch.execute(downloadRequest())).dataFrom)
            assertEquals(1, calls)

            sketch.shutdown()
            sketch = createDefaultSketch(PlatformContext.INSTANCE, scopedClient, cacheDirectory)

            assertEquals(
                DataFrom.DOWNLOAD_CACHE,
                assertIs<ImageResult.Success>(sketch.execute(downloadRequest())).dataFrom,
            )
            assertEquals(1, calls)
            assertEquals(0L, sketch.memoryCache.size)
        } finally {
            sketch.shutdown()
            client.close()
            tempDirectory.deleteRecursively()
        }
    }

    @Test
    fun `load state callbacks map started success and error and ignore canceled`() = runTest {
        val bytes = encodedRaster(EncodedImageFormat.PNG)
        val client = HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath == "/success.png") {
                    respond(
                        content = bytes,
                        headers = headersOf(HttpHeaders.ContentType, "image/png"),
                    )
                } else {
                    respond("missing", HttpStatusCode.NotFound)
                }
            },
        )
        val sketch = createDefaultSketch(PlatformContext.INSTANCE, client.asScopedHttpClient())

        try {
            val success = assertIs<ImageResult.Success>(
                sketch.execute(uncachedRequest("https://example.com/success.png")),
            )
            val error = assertIs<ImageResult.Error>(
                sketch.execute(uncachedRequest("https://example.com/missing")),
            )
            val callbacks = mutableListOf<String>()

            fun dispatch(loadState: LoadState?) {
                dispatchImageLoadState(
                    loadState = loadState,
                    onLoading = { callbacks += "loading" },
                    onSuccess = { callbacks += "success:${it.width}x${it.height}:${it.bitmap != null}" },
                    onError = { callbacks += "error:${it === error.throwable}" },
                )
            }

            dispatch(LoadState.Started(success.request))
            dispatch(LoadState.Success(success.request, success))
            dispatch(LoadState.Error(error.request, error))
            dispatch(LoadState.Canceled(success.request))
            dispatch(null)

            assertEquals(
                listOf("loading", "success:7x5:true", "error:true"),
                callbacks,
            )
        } finally {
            sketch.shutdown()
            client.close()
        }
    }

    private fun uncachedRequest(
        url: String,
        width: Int? = null,
        height: Int? = null,
    ): ImageRequest = ImageRequest(PlatformContext.INSTANCE, url) {
        if (width != null && height != null) size(width, height)
        downloadCachePolicy(CachePolicy.DISABLED)
        resultCachePolicy(CachePolicy.DISABLED)
        memoryCachePolicy(CachePolicy.DISABLED)
    }

    private fun encodedRaster(
        format: EncodedImageFormat,
        width: Int = 7,
        height: Int = 5,
    ): ByteArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        try {
            surface.canvas.clear(0xFF6750A4.toInt())
            return requireNotNull(surface.makeImageSnapshot().encodeToData(format, 100)).bytes
        } finally {
            surface.close()
        }
    }
}
