/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.him188.ani.app.ui.foundation.crop
import me.him188.ani.app.ui.foundation.decodeImageBitmap
import me.him188.ani.datasources.api.MediaPreviewThumbnails
import me.him188.ani.utils.ktor.ScopedHttpClient
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.FramePreview
import org.openani.mediamp.features.PreviewFrame

/**
 * 进度条预览帧的状态: 悬浮 (桌面) 或拖动 (触摸) 进度条时, 加载并展示目标位置的视频帧.
 *
 * 为了降低延迟:
 * - 请求位置对齐到 [positionGridMillis] 网格, 拖动时只在跨格时才真正解码;
 * - 最近解码的帧按格子做 LRU 缓存, 回扫时立即命中;
 * - [prewarm] 可在播放开始时后台预热解码器, 避免首次悬浮时等待秒级的解码器启动.
 *
 * @see MediaProgressSlider
 */
@Stable
class MediaProgressFramePreviewState(
    /**
     * 加载 [positionMillis] 处的预览帧. 返回 `null` 表示暂不可用 (不会清空已显示的帧).
     */
    private val fetchFrame: suspend (positionMillis: Long) -> ImageBitmap?,
    private val debounceMillis: Long = 50,
    /**
     * 预览位置的对齐粒度. 视频关键帧间隔通常为数秒, 更细的粒度并不会带来更准确的画面.
     */
    private val positionGridMillis: Long = 2_000,
    /**
     * 帧缓存容量. 每帧约 80 KB (192x108 ARGB), 默认 8 帧约 650 KB;
     * 命中场景主要是"刚扫过又扫回来", 缓存最近一小段轨迹即可.
     */
    cacheSize: Int = 8,
) {
    /**
     * 当前要展示的预览帧. `null` 表示无帧可展示 (浮窗显示占位背景).
     */
    var frame: ImageBitmap? by mutableStateOf(null)
        private set

    private var frameGridKey = Long.MIN_VALUE
    private var mediaGeneration = 0L
    private val cache = androidx.collection.LruCache<Long, ImageBitmap>(cacheSize)

    private fun gridKeyOf(positionMillis: Long): Long =
        if (positionGridMillis > 0) positionMillis / positionGridMillis else positionMillis

    /**
     * 请求加载 [positionMillis] 处的帧. 预期在 `collectLatest` 中调用: 拖动到新位置时旧请求会被取消.
     * 缓存命中立即显示; 加载成功前保留上一帧, 避免闪烁.
     */
    internal suspend fun requestFrame(positionMillis: Long) {
        val generation = mediaGeneration
        val key = gridKeyOf(positionMillis)
        if (key == frameGridKey && frame != null) return
        cache[key]?.let {
            frame = it
            frameGridKey = key
            return
        }
        delay(debounceMillis) // debounce: 快速滑动时, 更新的位置会取消本次请求
        val newFrame = fetchFrame(alignToGrid(key, positionMillis)) ?: return
        if (generation != mediaGeneration) return
        cache.put(key, newFrame)
        frame = newFrame
        frameGridKey = key
    }

    /**
     * 后台预热: 解码 [positionMillis] 附近的一帧存入缓存, 不改变当前显示.
     * 用于播放开始时提前启动预览解码器.
     */
    suspend fun prewarm(positionMillis: Long) {
        val generation = mediaGeneration
        val key = gridKeyOf(positionMillis)
        if (cache[key] != null) return
        val newFrame = fetchFrame(alignToGrid(key, positionMillis)) ?: return
        if (generation != mediaGeneration) return
        cache.put(key, newFrame)
    }

    private fun alignToGrid(key: Long, positionMillis: Long): Long =
        if (positionGridMillis > 0) key * positionGridMillis else positionMillis

    /**
     * 预览结束 (浮窗隐藏) 时清空当前帧, 避免下次悬浮时先显示过期位置的帧. 缓存保留.
     */
    internal fun onPreviewFinished() {
        frame = null
        frameGridKey = Long.MIN_VALUE
    }

    /**
     * 媒体切换时清空缓存, 避免展示上一个视频的帧.
     */
    fun onMediaChanged() {
        mediaGeneration++
        cache.evictAll()
        frame = null
        frameGridKey = Long.MIN_VALUE
    }
}

/**
 * 从 [player] 的 [FramePreview] feature 或 [previewThumbnails] 创建 [MediaProgressFramePreviewState].
 *
 * 优先使用数据源提供的 [previewThumbnails]（如 Jellyfin/Emby 的 Trickplay 拼图大图），
 * 避免在 HTTP 播放时进行二次视频解码；若无预生成缩略图，则回退到播放器解码器 [FramePreview]。
 */
@Composable
fun rememberMediaProgressFramePreviewState(
    player: MediampPlayer,
    httpClient: ScopedHttpClient,
    previewThumbnails: MediaPreviewThumbnails? = null,
    requestThumbnail: (suspend (mediaSourceId: String, url: String) -> ByteArray?)? = null,
    maxWidth: Dp = 192.dp,
    maxHeight: Dp = 128.dp,
): MediaProgressFramePreviewState? {
    val density = LocalDensity.current
    val framePreviewFeature = remember(player) { player.features[FramePreview] }
    val tileFetcher = remember(previewThumbnails, httpClient, requestThumbnail) {
        previewThumbnails?.let { MediaPreviewThumbnailsTileFetcher(it, httpClient, requestThumbnail) }
    }

    if (tileFetcher == null && framePreviewFeature == null) return null

    val state = remember(tileFetcher, framePreviewFeature, density, maxWidth, maxHeight) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val maxHeightPx = with(density) { maxHeight.roundToPx() }
        MediaProgressFramePreviewState(
            fetchFrame = { positionMillis ->
                tileFetcher?.fetchFrame(positionMillis)
                    ?: framePreviewFeature?.getPreviewFrame(positionMillis, maxWidthPx, maxHeightPx)?.toImageBitmap()
            },
        )
    }
    LaunchedEffect(state, player) {
        player.mediaData.collect { data ->
            state.onMediaChanged()
            if (data != null && tileFetcher == null) {
                // 预热预览解码器 (仅在无预生成缩略图时), 避免首次悬浮时长时间显示占位框.
                runCatching { state.prewarm(player.getCurrentPositionMillis()) }
            }
        }
    }
    return state
}

/**
 * 负责拉取和裁剪 [MediaPreviewThumbnails.Layout.SpriteTile] 精灵图网格缩略图。
 */
class MediaPreviewThumbnailsTileFetcher(
    private val previewThumbnails: MediaPreviewThumbnails,
    private val httpClient: ScopedHttpClient,
    private val requestThumbnail: (suspend (mediaSourceId: String, url: String) -> ByteArray?)? = null,
) {
    private val tileCache = androidx.collection.LruCache<Int, ImageBitmap>(1)

    suspend fun fetchFrame(positionMillis: Long): ImageBitmap? {
        val layout = previewThumbnails.layout as? MediaPreviewThumbnails.Layout.SpriteTile ?: return null
        val frame = calculateSpriteTileFrame(previewThumbnails, layout, positionMillis) ?: return null

        val tileImage = getOrFetchTileImage(layout.urlPattern, frame.tileIndex) ?: return null

        if (frame.cropX.toLong() + previewThumbnails.width > tileImage.width.toLong() ||
            frame.cropY.toLong() + previewThumbnails.height > tileImage.height.toLong()
        ) {
            return null
        }

        return try {
            withContext(Dispatchers.Default) {
                tileImage.crop(frame.cropX, frame.cropY, previewThumbnails.width, previewThumbnails.height)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun getOrFetchTileImage(
        urlPattern: String,
        tileIndex: Int,
    ): ImageBitmap? {
        tileCache[tileIndex]?.let { return it }

        val url = urlPattern.replace("{tileIndex}", tileIndex.toString())
        val bytes = try {
            val requesterMediaSourceId = previewThumbnails.requesterMediaSourceId
            if (requesterMediaSourceId == null) {
                httpClient.use {
                    prepareGet(url) {
                        previewThumbnails.headers.forEach { (key, value) ->
                            header(key, value)
                        }
                    }.execute { response ->
                        response.body<ByteArray>()
                    }
                }
            } else {
                requestThumbnail?.invoke(requesterMediaSourceId, url) ?: return null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }

        val bitmap = try {
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        tileCache.put(tileIndex, bitmap)
        return bitmap
    }
}

internal data class SpriteTileFrame(
    val tileIndex: Int,
    val cropX: Int,
    val cropY: Int,
)

internal fun calculateSpriteTileFrame(
    previewThumbnails: MediaPreviewThumbnails,
    layout: MediaPreviewThumbnails.Layout.SpriteTile,
    positionMillis: Long,
): SpriteTileFrame? {
    if (previewThumbnails.width <= 0 || previewThumbnails.height <= 0 ||
        previewThumbnails.intervalMillis <= 0 || previewThumbnails.totalCount <= 0 ||
        layout.columns <= 0 || layout.rows <= 0
    ) {
        return null
    }

    val frameIndex = (positionMillis.coerceAtLeast(0) / previewThumbnails.intervalMillis)
        .coerceAtMost(previewThumbnails.totalCount.toLong() - 1)
    val tilesPerSheet = layout.columns.toLong() * layout.rows
    val indexInTile = frameIndex % tilesPerSheet
    val tileIndex = frameIndex / tilesPerSheet
    val cropX = (indexInTile % layout.columns) * previewThumbnails.width
    val cropY = (indexInTile / layout.columns) * previewThumbnails.height
    if (tileIndex > Int.MAX_VALUE || cropX > Int.MAX_VALUE || cropY > Int.MAX_VALUE) return null

    return SpriteTileFrame(tileIndex.toInt(), cropX.toInt(), cropY.toInt())
}

/**
 * 将 [PreviewFrame] 的 ARGB 像素转换为 [ImageBitmap].
 */
internal expect fun PreviewFrame.toImageBitmap(): ImageBitmap
