/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.EventListener
import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.NetworkFetcher
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.isDesktop
import me.him188.ani.utils.platform.isIos
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

val LocalImageLoader = androidx.compose.runtime.compositionLocalOf<ImageLoader> {
    error("No ImageLoader provided")
}

@Stable
inline val defaultFilterQuality get() = if (currentPlatform().isDesktop()) FilterQuality.High else FilterQuality.Low

@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalImageLoader.current,
    error: Painter? = null,
    fallback: Painter? = error,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = defaultFilterQuality,
    clipToBounds: Boolean = true,
) {
    return coil3.compose.AsyncImage(
        model = model,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        modifier = Modifier.then(modifier),
        placeholder = null,
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
    )
}


@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalImageLoader.current,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = defaultFilterQuality,
    clipToBounds: Boolean = true,
) {
    return coil3.compose.AsyncImage(
        model = model,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
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
    )
}

@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalImageLoader.current,
    transform: (AsyncImagePainter.State) -> AsyncImagePainter.State = AsyncImagePainter.DefaultTransform,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = defaultFilterQuality,
    clipToBounds: Boolean = true,
) {
    return coil3.compose.AsyncImage(
        model = model,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        modifier = modifier,
        transform = transform,
        onState = onState,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
        clipToBounds = clipToBounds,
    )
}

/**
 * 图片磁盘缓存大小上限.
 *
 * Coil 默认是 `clamp(可用空间 2%, 10MB, 250MB)`, 对 TV 用图 (w1280 backdrop + 分集剧照)
 * 偏挤; 显式固定上限, 超出由 Coil 的 LRU 自动淘汰 (不会无限膨胀, 无需手动清理).
 */
const val IMAGE_DISK_CACHE_MAX_SIZE_BYTES: Long = 300L * 1024 * 1024

// 进程级共享的 DiskCache (目录 -> 实例): Coil 明确要求同一目录同一时刻只能有一个
// DiskCache 实例 (两个 LRU journal 并发读写会损坏缓存). 主应用 ImageLoader 与
// TV 屏保 (AniDreamService, 同进程) 各建各的 loader, 磁盘缓存必须共享同一实例.
private val sharedDiskCachesLock = SynchronizedObject()
private val sharedDiskCaches = mutableMapOf<okio.Path, DiskCache>()

private fun sharedDiskCache(directory: okio.Path): DiskCache =
    synchronized(sharedDiskCachesLock) {
        sharedDiskCaches.getOrPut(directory) {
            DiskCache.Builder().apply {
                directory(directory)
                maxSizeBytes(IMAGE_DISK_CACHE_MAX_SIZE_BYTES)
            }.build()
        }
    }

private val imageLoadLogger = logger("ImageLoad")

/**
 * 异常慢的判定阈值. 只记超过它的加载 —— 正常加载 (内存/磁盘命中几十毫秒, 网络几百毫秒)
 * 一律不记, 否则滚动一次列表就是几百行日志.
 */
private val SLOW_IMAGE_LOAD_THRESHOLD = 3.seconds

/**
 * 只上报两种异常: 加载失败, 以及慢于 [SLOW_IMAGE_LOAD_THRESHOLD] 的加载.
 *
 * 慢加载附带 fetch 段耗时与数据来源: 总耗时远大于 fetch 段说明卡在派发队列
 * (并发图太多排队), fetch 段本身长则是网络/图床慢.
 */
internal class ImageLoadIssueTracker {
    private val start = TimeSource.Monotonic.markNow()
    private var fetchStartMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var fetchMillis: Long = -1

    fun fetchStart() {
        fetchStartMark = TimeSource.Monotonic.markNow()
    }

    fun fetchEnd() {
        fetchMillis = fetchStartMark?.elapsedNow()?.inWholeMilliseconds ?: -1
    }

    fun success(request: ImageRequest, result: SuccessResult) {
        val total = start.elapsedNow()
        if (total < SLOW_IMAGE_LOAD_THRESHOLD) return
        imageLoadLogger.info {
            "Image loaded slowly in ${total.inWholeMilliseconds}ms " +
                "(fetch=${if (fetchMillis >= 0) "${fetchMillis}ms" else "n/a"}, " +
                "source=${result.dataSource}): ${request.data}"
        }
    }

    fun error(request: ImageRequest, result: ErrorResult) {
        imageLoadLogger.warn {
            "Image load FAILED after ${start.elapsedNow().inWholeMilliseconds}ms: " +
                "${request.data} (${result.throwable})"
        }
    }
}

/**
 * 把 [ImageLoadIssueTracker] 接到 Coil 的事件回调上.
 *
 * 只有这层壳在平台侧: coil 的 `EventListener` 是 `expect abstract class` 且不声明构造器,
 * commonMain 里继承不了 (metadata 编译会报 "Expect class does not declare any constructors").
 */
internal expect fun imageLoadIssueEventListenerFactory(): EventListener.Factory

@OptIn(ExperimentalCoilApi::class)
fun createDefaultImageLoader(
    context: PlatformContext,
    client: ScopedHttpClient,
    /**
     * 磁盘缓存目录; null 用 Coil 默认 (系统临时目录, Android 上即 app cacheDir).
     * 传 app cacheDir 下的目录可保证桌面端也落在应用自己的缓存目录而非系统 temp.
     * 同一目录在进程内复用同一 DiskCache 实例 (见 [sharedDiskCache]).
     */
    diskCacheDirectory: okio.Path? = null,
    config: ImageLoader.Builder.() -> Unit = {}
): ImageLoader {
    return ImageLoader.Builder(context).apply {
        if (!currentPlatform().isIos()) {
            crossfade(true)
        }
        if (diskCacheDirectory != null) {
            diskCache(sharedDiskCache(diskCacheDirectory))
        }

        coroutineContext(Dispatchers.Default)

        eventListenerFactory(imageLoadIssueEventListenerFactory())

        diskCachePolicy(CachePolicy.ENABLED)
        memoryCachePolicy(CachePolicy.ENABLED)
        memoryCache {
            MemoryCache.Builder().apply {
                // 按可用堆比例 (Coil 默认 ~25%): 固定 10MB 在 TV 高密度下装不下
                // 一张全屏图, 网格滚动/hero 换图反复从磁盘重解码, 低端机可见卡顿
                maxSizePercent(context)
            }.build()
        }
        networkCachePolicy(CachePolicy.ENABLED)

        components {
            add(SvgDecoder.Factory())

            add(
                NetworkFetcher.Factory(
                    networkClient = {
                        ScopedHttpClientNetworkFetcher(client)
                    },
                ),
            )
        }

        config()
    }.build()
}

expect fun ImageBitmap.asCoilImage(): Image
