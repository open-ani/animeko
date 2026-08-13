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
import kotlinx.coroutines.delay
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
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

    /**
     * 本媒体能否取到帧. 取帧失败置 false, 成功置 true, 换媒体时复位.
     *
     * 取帧能力是**整个媒体**的属性而不是某个位置的: 平台取帧器要么能解析这个容器, 要么完全不能
     * (最典型的是 HLS/m3u8 —— Android 的 MediaMetadataRetriever 走平台 extractor, 没有 HLS
     * 解复用器, 而在线源基本都是 m3u8). 唯一按位置失败的情况是 BT 源未下载区域, 那种情况
     * 调用方根本不会发起请求 (见 [MediaProgressSlider] 里的 `isPositionCached` 判断).
     *
     * 消费方据此决定**要不要给帧留位置**: 不看这个标志的话, 取不到帧的媒体上浮窗里会一直是
     * 一块 160x90 的占位黑底 (那正是"缩略图永远是黑的"的由来), 而正确的降级是只显示时间.
     *
     * 起播时的 [prewarm] 顺带就是一次能力探测: 用户第一次唤出进度条之前这个值就已经定下来了,
     * 所以不会出现"先给一块黑底, 过一会儿才发现取不到"的闪动.
     */
    var framesAvailable: Boolean by mutableStateOf(true)
        private set

    private var frameGridKey = Long.MIN_VALUE
    private val cache = androidx.collection.LruCache<Long, ImageBitmap>(cacheSize)

    private fun gridKeyOf(positionMillis: Long): Long =
        if (positionGridMillis > 0) positionMillis / positionGridMillis else positionMillis

    /**
     * 请求加载 [positionMillis] 处的帧. 预期在 `collectLatest` 中调用: 拖动到新位置时旧请求会被取消.
     * 缓存命中立即显示; 加载成功前保留上一帧, 避免闪烁.
     */
    internal suspend fun requestFrame(positionMillis: Long) {
        val key = gridKeyOf(positionMillis)
        if (key == frameGridKey && frame != null) return
        cache[key]?.let {
            frame = it
            frameGridKey = key
            return
        }
        delay(debounceMillis) // debounce: 快速滑动时, 更新的位置会取消本次请求
        val newFrame = fetchFrame(alignToGrid(key, positionMillis))
        if (newFrame == null) {
            // 底层实现把所有异常都吞了 (见 mediamp 的 ExoFramePreview), 这里至少留一行,
            // 否则"取不到帧"在日志里完全没有痕迹
            logger.warn { "Frame preview unavailable at $positionMillis ms (decoder returned null)" }
            framesAvailable = false
            return
        }
        cache.put(key, newFrame)
        frame = newFrame
        frameGridKey = key
        framesAvailable = true
    }

    /**
     * 后台预热: 解码 [positionMillis] 附近的一帧存入缓存, 不改变当前显示.
     * 用于播放开始时提前启动预览解码器.
     */
    suspend fun prewarm(positionMillis: Long) {
        val key = gridKeyOf(positionMillis)
        if (cache[key] != null) return
        val newFrame = fetchFrame(alignToGrid(key, positionMillis))
        if (newFrame == null) {
            // 预热位置一定是当前播放点 (数据必然可用), 这里失败就是取帧器打不开这个媒体
            logger.warn { "Frame preview prewarm failed at $positionMillis ms; frame preview disabled for this media" }
            framesAvailable = false
            return
        }
        logger.info { "Frame preview prewarmed at $positionMillis ms" }
        cache.put(key, newFrame)
        framesAvailable = true
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
        cache.evictAll()
        frame = null
        frameGridKey = Long.MIN_VALUE
        framesAvailable = true // 新媒体重新探测, 上一个不能取帧不代表这个也不能
    }
}

/**
 * 从 [player] 的 [FramePreview] feature 创建 [MediaProgressFramePreviewState].
 *
 * 当播放器后端不支持取帧时返回 `null`, 此时进度条浮窗只显示时间.
 */
@Composable
fun rememberMediaProgressFramePreviewState(
    player: MediampPlayer,
    maxWidth: Dp = 192.dp,
    maxHeight: Dp = 128.dp,
): MediaProgressFramePreviewState? {
    val framePreview = remember(player) { player.features[FramePreview] } ?: return null
    val density = LocalDensity.current
    val state = remember(framePreview, density, maxWidth, maxHeight) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val maxHeightPx = with(density) { maxHeight.roundToPx() }
        MediaProgressFramePreviewState(
            fetchFrame = { positionMillis ->
                framePreview.getPreviewFrame(positionMillis, maxWidthPx, maxHeightPx)?.toImageBitmap()
            },
        )
    }
    LaunchedEffect(state, player) {
        player.mediaData.collect { data ->
            state.onMediaChanged()
            if (data != null) {
                // 预热预览解码器 (第二个解码器实例启动需要秒级时间), 避免首次悬浮时长时间显示占位框.
                // 取当前播放位置附近的帧: 该区域一定已在缓冲, 不会抢占下载优先级.
                runCatching { state.prewarm(player.currentPositionMillis.value) }
            }
        }
    }
    return state
}

/**
 * 将 [PreviewFrame] 的 ARGB 像素转换为 [ImageBitmap].
 */
internal expect fun PreviewFrame.toImageBitmap(): ImageBitmap

private val logger = logger<MediaProgressFramePreviewState>()
