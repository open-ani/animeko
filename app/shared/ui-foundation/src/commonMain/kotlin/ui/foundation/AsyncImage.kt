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
import com.github.panpf.sketch.Image
import com.github.panpf.sketch.LocalPlatformContext
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.asBitmapOrNull
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.cache.DiskCache
import com.github.panpf.sketch.cache.MemoryCache
import com.github.panpf.sketch.cache.internal.LruMemoryCache
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
import com.github.panpf.sketch.resize.Scale
import com.github.panpf.sketch.resize.ScaleDecider
import com.github.panpf.sketch.state.PainterStateImage
import com.github.panpf.sketch.state.StateImage
import com.github.panpf.sketch.target.Target
import com.github.panpf.sketch.transition.Transition
import com.github.panpf.sketch.util.Size
import com.github.panpf.sketch.util.asComposeImageBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.files
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.isDesktop
import me.him188.ani.utils.platform.isIos
import okio.Path
import okio.Path.Companion.toPath
import com.github.panpf.sketch.AsyncImage as SketchAsyncImage

private const val MEBIBYTE = 1024L * 1024L
private const val IMAGE_DOWNLOAD_CACHE_SIZE = 100L * MEBIBYTE
/**
 * 解码后位图的 LRU 上限. 列表封面按布局尺寸的 2 倍解码, 手机上一张约 1.3 MB, 一屏 9 到 12 张;
 * 放下四五屏, 来回滚动和页面返回时不必重新读盘解码. iOS 上 Skia 没有采样解码, 重新解码尤其昂贵.
 */
private const val IMAGE_MEMORY_CACHE_SIZE = 64L * MEBIBYTE
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

/**
 * @param thumbnailModel [model] 的低分辨率版本 (例如列表用的缩略图). 内存里已有它的解码结果就立即显示, 否则后台加载它;
 * [model] 加载完成后再替换, 中途不会空白. 用于从列表进入详情这类 [model] 尚未缓存而缩略图刚显示过的场景.
 */
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
    thumbnailModel: String? = null,
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
        thumbnailModel = thumbnailModel,
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
    thumbnailModel: String? = null,
) {
    var requestSize by remember { mutableStateOf<IntSize?>(null) }

    val placeholderStateImage = rememberStateImage(placeholder, "placeholder")
    val errorStateImage = rememberStateImage(error, "error")
    val fallbackStateImage = rememberStateImage(fallback, "fallback")

    val thumbnail = rememberAniImageThumbnail(model, thumbnailModel)
    val request = ComposableImageRequest(model) {
        configureAniImagePlaceholder(placeholderStateImage, thumbnail)
        if (errorStateImage != null) error(errorStateImage)
        if (fallbackStateImage != null) fallback(fallbackStateImage)

        configureAniImageRequest(
            contentScale = contentScale,
            alignment = alignment,
            requestSize = requestSize,
        )

        configureAniImageCrossfade(crossfade, crossfadeDurationMillis)
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

internal fun ImageRequest.Builder.configureAniImageCrossfade(
    crossfade: Boolean?,
    crossfadeDurationMillis: Int? = null,
) {
    when {
        // Sketch's crossfade(false) sets null, which inherits the global crossfade again.
        crossfade == false -> transitionFactory(NoImageTransitionFactory)
        crossfadeDurationMillis != null -> crossfade(crossfadeDurationMillis)
        crossfade == true -> crossfade(true)
    }
}

private data object NoImageTransitionFactory : Transition.Factory {
    override val key: String = "AniNoImageTransition"

    override fun create(sketch: Sketch, request: ImageRequest, target: Target, result: ImageResult): Transition? = null
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

/**
 * 缩略图只在 [model] 换值时取一次. Sketch 把缩略图放在请求的 extras 里而不参与请求 key,
 * 同一 key 下换缩略图会被 [AsyncImageState] 判定为非法请求.
 */
@Composable
private fun rememberAniImageThumbnail(model: String?, thumbnailModel: String?): String? =
    remember(model) { thumbnailModel?.takeIf { it.isNotBlank() && it != model } }

/**
 * 有 [thumbnail] 时先显示它: 内存里已有它的解码结果 (列表刚显示过) 就立即占位, 同时在后台加载它;
 * 主图加载完成后替换. [placeholder] 退为找不到缩略图时的占位.
 */
internal fun ImageRequest.Builder.configureAniImagePlaceholder(
    placeholder: StateImage?,
    thumbnail: String?,
) {
    if (thumbnail == null) {
        if (placeholder != null) placeholder(placeholder)
        return
    }
    thumbnail(thumbnail)
    placeholder(CachedUriStateImage(thumbnail, placeholder))
}

/**
 * 内存缓存里 [uri] 的解码结果, 不限解码时的尺寸和裁剪; 有多份时取最大的.
 * 没有时退回 [defaultImage], 再退回全局占位图 (预览和截图测试靠它显示测试图片).
 *
 * Sketch 自带的 [com.github.panpf.sketch.state.ThumbnailMemoryCacheStateImage] 要求缓存图与原图等比,
 * 而列表按卡片比例裁剪解码, 常常匹配不上.
 */
internal data class CachedUriStateImage(
    val uri: String,
    val defaultImage: StateImage? = null,
) : StateImage {
    override val key: String = "AniCachedUri('$uri',${defaultImage?.key})"

    override fun getImage(sketch: Sketch, request: ImageRequest, throwable: Throwable?): Image? {
        val cached = sketch.memoryCache.entries()
            .filter { (cacheKey, _) -> isCacheKeyOf(cacheKey, uri) }
            .maxByOrNull { (_, value) -> value.image.width.toLong() * value.image.height }
        return cached?.value?.image
            ?: (defaultImage ?: sketch.globalImageOptions?.placeholder)?.getImage(sketch, request, throwable)
    }

    override fun toString(): String = "CachedUriStateImage(uri='$uri', defaultImage=$defaultImage)"

    private companion object {
        /** Sketch 的缓存 key 是 uri 后接 `?_xxx=...` 或 `&_xxx=...` 形式的参数. */
        fun isCacheKeyOf(cacheKey: String, uri: String): Boolean {
            if (!cacheKey.startsWith(uri)) return false
            if (cacheKey.length == uri.length) return true
            return cacheKey.length > uri.length + 1 &&
                    (cacheKey[uri.length] == '?' || cacheKey[uri.length] == '&') &&
                    cacheKey[uri.length + 1] == '_'
        }
    }
}

@Composable
internal fun rememberAniAsyncImagePainter(
    model: String?,
    contentScale: ContentScale,
    requestSize: IntSize?,
    filterQuality: FilterQuality = defaultFilterQuality,
    state: AsyncImageState? = null,
    thumbnailModel: String? = null,
): Painter {
    val rememberedState = rememberAsyncImageState()
    val finalState = state ?: rememberedState
    val thumbnail = rememberAniImageThumbnail(model, thumbnailModel)
    val request = ComposableImageRequest(model) {
        configureAniImageRequest(
            contentScale = contentScale,
            alignment = Alignment.Center,
            requestSize = requestSize,
        )
        configureAniImagePlaceholder(placeholder = null, thumbnail = thumbnail)
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
    scale(aniScaleDecider(contentScale, alignment))
    when (contentScale) {
        ContentScale.Crop -> precision(
            // Sketch only understands start/center/end crop. Preserve the source aspect
            // for a custom alignment so Compose can position the crop without losing pixels.
            if (alignment in listOf(
                    Alignment.TopStart, Alignment.TopCenter, Alignment.TopEnd,
                    Alignment.CenterStart, Alignment.Center, Alignment.CenterEnd,
                    Alignment.BottomStart, Alignment.BottomCenter, Alignment.BottomEnd,
                )) Precision.SAME_ASPECT_RATIO else Precision.LESS_PIXELS,
        )
        ContentScale.FillBounds -> precision(Precision.EXACTLY)
    }
}

/**
 * Sketch's built-in alignment conversion only considers the horizontal component. For example,
 * [Alignment.TopCenter] becomes [Scale.CENTER_CROP], which discards the top before Compose draws
 * the image. Select the relevant alignment component after Sketch knows the source aspect ratio.
 */
private fun aniScaleDecider(contentScale: ContentScale, alignment: Alignment): ScaleDecider {
    if (
        contentScale == ContentScale.FillBounds ||
        contentScale == ContentScale.FillWidth ||
        contentScale == ContentScale.FillHeight
    ) {
        return ScaleDecider(Scale.FILL)
    }

    val (horizontalScale, verticalScale) = when (alignment) {
        Alignment.TopStart -> Scale.START_CROP to Scale.START_CROP
        Alignment.TopCenter -> Scale.CENTER_CROP to Scale.START_CROP
        Alignment.TopEnd -> Scale.END_CROP to Scale.START_CROP
        Alignment.CenterStart -> Scale.START_CROP to Scale.CENTER_CROP
        Alignment.Center -> Scale.CENTER_CROP to Scale.CENTER_CROP
        Alignment.CenterEnd -> Scale.END_CROP to Scale.CENTER_CROP
        Alignment.BottomStart -> Scale.START_CROP to Scale.END_CROP
        Alignment.BottomCenter -> Scale.CENTER_CROP to Scale.END_CROP
        Alignment.BottomEnd -> Scale.END_CROP to Scale.END_CROP
        else -> Scale.CENTER_CROP to Scale.CENTER_CROP
    }
    return AniAlignmentScaleDecider(horizontalScale, verticalScale)
}

private data class AniAlignmentScaleDecider(
    val horizontalScale: Scale,
    val verticalScale: Scale,
) : ScaleDecider {
    override val key: String = "AniAlignment($horizontalScale,$verticalScale)"

    override fun get(imageSize: Size, targetSize: Size): Scale {
        if (
            imageSize.width <= 0 || imageSize.height <= 0 ||
            targetSize.width <= 0 || targetSize.height <= 0
        ) {
            return Scale.CENTER_CROP
        }

        val imageAspectProduct = imageSize.width.toLong() * targetSize.height
        val targetAspectProduct = targetSize.width.toLong() * imageSize.height
        return when {
            imageAspectProduct > targetAspectProduct -> horizontalScale
            imageAspectProduct < targetAspectProduct -> verticalScale
            else -> Scale.CENTER_CROP
        }
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
    // 让刚显示过的图片 (翻页、列表滚回、页面返回) 无需重新读盘解码即可立即显示.
    memoryCache(LruMemoryCache(IMAGE_MEMORY_CACHE_SIZE))
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
            downloadCachePolicy(CachePolicy.ENABLED)
            memoryCachePolicy(CachePolicy.ENABLED)
            // Result cache re-encodes transformed images. Keep the original bytes in the LRU
            // download cache instead so disk caching cannot reduce image quality.
            resultCachePolicy(CachePolicy.DISABLED)
            crossfade(!currentPlatform().isIos())
        },
    )
    addComponents {
        add(ScopedHttpClientHttpUriFetcherFactory(ScopedHttpClientHttpStack(client)))
        supportSvg()
    }
}.build()

/** Keeps the network-free preview loader from retaining decoded images. */
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
