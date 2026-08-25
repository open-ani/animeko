/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.github.panpf.sketch.AsyncImageState
import com.github.panpf.sketch.LocalPlatformContext
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.asBitmapOrNull
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.cache.DiskCache
import com.github.panpf.sketch.cache.MemoryCache
import com.github.panpf.sketch.decode.supportSvg
import com.github.panpf.sketch.painter.asEquitable
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ComposableImageRequest
import com.github.panpf.sketch.request.ImageOptions
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.ImageResult
import com.github.panpf.sketch.request.LoadState
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.sketch.state.PainterStateImage
import com.github.panpf.sketch.state.StateImage
import com.github.panpf.sketch.util.asComposeImageBitmap
import com.github.panpf.sketch.util.toScale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.files
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.isDesktop
import okio.Path
import okio.Path.Companion.toPath
import com.github.panpf.sketch.AsyncImage as SketchAsyncImage

private const val MEBIBYTE = 1024L * 1024L
private const val IMAGE_MEMORY_CACHE_SIZE = 64L * MEBIBYTE
private const val IMAGE_DOWNLOAD_CACHE_SIZE = 128L * MEBIBYTE
private const val ANI_IMAGE_CACHE_DIRECTORY = "image-cache"

val LocalSketch = staticCompositionLocalOf<Sketch> {
    error("No Ani image loader provided")
}

/** A library-neutral successful image load result exposed to feature UI modules. */
@Immutable
data class AniImageLoadSuccess(
    val bitmap: ImageBitmap?,
    val width: Int,
    val height: Int,
)

internal fun ImageResult.Success.toAniImageLoadSuccess(): AniImageLoadSuccess = AniImageLoadSuccess(
    bitmap = image.asBitmapOrNull()?.asComposeImageBitmap(),
    width = imageInfo.width,
    height = imageInfo.height,
)

@Stable
inline val defaultFilterQuality: FilterQuality
    get() = if (currentPlatform().isDesktop()) FilterQuality.High else FilterQuality.Low

/** Owns the application Sketch instance and keeps Sketch types inside ui-foundation. */
@Composable
fun rememberAniSketchInstance(client: ScopedHttpClient): Sketch {
    val context = LocalPlatformContext.current
    val imageCacheDirectory = LocalContext.current.files.cacheDir
        .resolve(ANI_IMAGE_CACHE_DIRECTORY)
        .absolutePath
        .toPath()
    val sketch = remember(context, client, imageCacheDirectory) {
        createDefaultSketch(context, client, imageCacheDirectory)
    }
    DisposableEffect(sketch) {
        onDispose(sketch::shutdown)
    }
    return sketch
}

@Composable
fun AsyncImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error,
    onLoading: (() -> Unit)? = null,
    onSuccess: ((AniImageLoadSuccess) -> Unit)? = null,
    onError: ((Throwable?) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = defaultFilterQuality,
    clipToBounds: Boolean = true,
    crossfade: Boolean? = null,
    crossfadeDurationMillis: Int? = null,
) {
    val state = rememberAsyncImageState()
    AniAsyncImage(
        model = model,
        contentDescription = contentDescription,
        state = state,
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        fallback = fallback,
        onLoading = onLoading,
        onSuccess = onSuccess,
        onError = onError,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
        clipToBounds = clipToBounds,
        crossfade = crossfade,
        crossfadeDurationMillis = crossfadeDurationMillis,
    )
}

@Composable
internal fun AniAsyncImage(
    model: String?,
    contentDescription: String?,
    state: AsyncImageState,
    modifier: Modifier = Modifier,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error,
    onLoading: (() -> Unit)? = null,
    onSuccess: ((AniImageLoadSuccess) -> Unit)? = null,
    onError: ((Throwable?) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = defaultFilterQuality,
    clipToBounds: Boolean = true,
    crossfade: Boolean? = null,
    crossfadeDurationMillis: Int? = null,
) {
    var requestSize by remember { mutableStateOf<IntSize?>(null) }

    val placeholderStateImage = rememberStateImage(placeholder, "placeholder")
    val errorStateImage = rememberStateImage(error, "error")
    val fallbackStateImage = rememberStateImage(fallback, "fallback")

    val request = ComposableImageRequest(model) {
        if (placeholderStateImage != null) placeholder(placeholderStateImage)
        if (errorStateImage != null) error(errorStateImage)
        if (fallbackStateImage != null) fallback(fallbackStateImage)

        configureAniImageRequest(
            contentScale = contentScale,
            alignment = alignment,
            requestSize = requestSize,
        )

        when {
            crossfade == false -> crossfade(false)
            crossfadeDurationMillis != null -> crossfade(crossfadeDurationMillis)
            crossfade == true -> crossfade(true)
        }
    }

    ImageLoadStateEffect(state, onLoading, onSuccess, onError)
    SketchAsyncImage(
        request = request,
        sketch = LocalSketch.current,
        contentDescription = contentDescription,
        modifier = modifier.onSizeChanged { size ->
            val roundedSize = size.toAniImageRequestSize()
            if (requestSize != roundedSize) requestSize = roundedSize
        },
        state = state,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
        clipToBounds = clipToBounds,
    )
}

@Composable
private fun ImageLoadStateEffect(
    state: AsyncImageState,
    onLoading: (() -> Unit)?,
    onSuccess: ((AniImageLoadSuccess) -> Unit)?,
    onError: ((Throwable?) -> Unit)?,
) {
    val currentOnLoading by rememberUpdatedState(onLoading)
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnError by rememberUpdatedState(onError)

    val loadState = state.loadState

    LaunchedEffect(loadState) {
        dispatchImageLoadState(
            loadState = loadState,
            onLoading = currentOnLoading,
            onSuccess = currentOnSuccess,
            onError = currentOnError,
        )
    }
}

internal fun dispatchImageLoadState(
    loadState: LoadState?,
    onLoading: (() -> Unit)?,
    onSuccess: ((AniImageLoadSuccess) -> Unit)?,
    onError: ((Throwable?) -> Unit)?,
) {
    if (loadState == null) return

    when (loadState) {
        is LoadState.Started -> onLoading?.invoke()
        is LoadState.Success -> onSuccess?.invoke(loadState.result.toAniImageLoadSuccess())
        is LoadState.Error -> onError?.invoke(loadState.result.throwable)
        is LoadState.Canceled -> Unit
    }
}

@Composable
private fun rememberStateImage(painter: Painter?, role: String): StateImage? =
    remember(painter, role) {
        painter?.let {
            PainterStateImage(it.asEquitable(role to it))
        }
    }

@Composable
internal fun rememberAniAsyncImagePainter(
    model: String?,
    contentScale: ContentScale,
    requestSize: IntSize?,
    filterQuality: FilterQuality = defaultFilterQuality,
    state: AsyncImageState? = null,
): Painter {
    val rememberedState = rememberAsyncImageState()
    val finalState = state ?: rememberedState
    val request = ComposableImageRequest(model) {
        configureAniImageRequest(
            contentScale = contentScale,
            alignment = Alignment.Center,
            requestSize = requestSize,
        )
    }
    return rememberAsyncImagePainter(
        request = request,
        sketch = LocalSketch.current,
        state = finalState,
        contentScale = contentScale,
        filterQuality = filterQuality,
    )
}

/**
 * Sketch's default [Precision.LESS_PIXELS] only matches the target's total pixel count. When a
 * portrait source is drawn into a landscape crop (or vice versa), that can decode one dimension
 * below the target and force Compose to upscale it. Crop to the target aspect while decoding so
 * both displayed dimensions have enough pixels.
 */
internal fun ImageRequest.Builder.configureAniImageRequest(
    contentScale: ContentScale,
    alignment: Alignment,
    requestSize: IntSize? = null,
) {
    if (requestSize != null && requestSize.width > 0 && requestSize.height > 0) {
        size(requestSize.width * 2, requestSize.height * 2)
    }
    scale(toScale(contentScale, alignment))
    when (contentScale) {
        ContentScale.Crop -> precision(Precision.SAME_ASPECT_RATIO)
        ContentScale.FillBounds -> precision(Precision.EXACTLY)
    }
}

/**
 * Keep decoded dimensions at or above the layout size while avoiding a new request for every pixel
 * of a desktop window resize.
 */
internal fun IntSize.toAniImageRequestSize(): IntSize = IntSize(
    width = width.roundUpImageRequestDimension(),
    height = height.roundUpImageRequestDimension(),
)

private fun Int.roundUpImageRequestDimension(): Int {
    if (this <= 0) return this
    val step = when {
        this <= 64 -> 8
        this <= 256 -> 16
        else -> 64
    }
    return ((this + step - 1) / step) * step
}

internal fun createDefaultSketch(
    context: PlatformContext,
    client: ScopedHttpClient,
    cacheDirectory: Path? = null,
): Sketch = Sketch.Builder(context).apply {
    componentLoaderEnabled(false)
    memoryCache(
        MemoryCache.Builder(context)
            .maxSizeBytes(IMAGE_MEMORY_CACHE_SIZE)
            .build(),
    )
    downloadCacheOptions(
        DiskCache.Options(
            directory = cacheDirectory?.resolve("download"),
            maxSize = IMAGE_DOWNLOAD_CACHE_SIZE,
        ),
    )
    resultCacheOptions(
        DiskCache.Options(
            directory = cacheDirectory?.resolve("result"),
        ),
    )
    globalImageOptions(
        ImageOptions {
            memoryCachePolicy(CachePolicy.ENABLED)
            downloadCachePolicy(CachePolicy.ENABLED)
            // Result cache re-encodes transformed images. Keep the original bytes in the LRU
            // download cache instead so disk caching cannot reduce image quality.
            resultCachePolicy(CachePolicy.DISABLED)
            crossfade(false)
        },
    )
    addComponents {
        add(ScopedHttpClientHttpUriFetcherFactory(ScopedHttpClientHttpStack(client)))
        supportSvg()
    }
}.build()

/** Keeps previews deterministic without retaining their synthetic images. */
private data object DisabledMemoryCache : MemoryCache {
    private val mutex = Mutex()

    override val maxSize: Long = 0L
    override val size: Long = 0L

    override fun put(key: String, value: MemoryCache.Value): Int = -3

    override fun remove(key: String): MemoryCache.Value? = null

    override fun get(key: String): MemoryCache.Value? = null

    override fun exist(key: String): Boolean = false

    override fun trim(targetSize: Long) = Unit

    override fun keys(): Set<String> = emptySet()

    override fun entries(): Set<Map.Entry<String, MemoryCache.Value>> = emptySet()

    override fun clear() = Unit

    override suspend fun <R> withLock(
        key: String,
        action: suspend MemoryCache.() -> R,
    ): R = mutex.withLock { action(this) }
}

/** Provides a deterministic, network-free image loader for previews and screenshot tests. */
@PublishedApi
@Composable
internal fun rememberAniPreviewSketch(previewPainter: Painter): Sketch {
    val context = LocalPlatformContext.current
    val sketch = remember(context, previewPainter) {
        val previewStateImage = PainterStateImage(
            previewPainter.asEquitable("ani-preview-image"),
        )
        Sketch.Builder(context)
            .componentLoaderEnabled(false)
            .memoryCache(DisabledMemoryCache)
            .globalImageOptions(
                ImageOptions {
                    placeholder(previewStateImage)
                    error(previewStateImage)
                    fallback(previewStateImage)
                    downloadCachePolicy(CachePolicy.DISABLED)
                    memoryCachePolicy(CachePolicy.DISABLED)
                    resultCachePolicy(CachePolicy.DISABLED)
                    crossfade(false)
                },
            )
            .build()
    }
    DisposableEffect(sketch) {
        onDispose(sketch::shutdown)
    }
    return sketch
}
