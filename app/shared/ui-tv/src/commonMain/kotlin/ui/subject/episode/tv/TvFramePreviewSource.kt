/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.tv

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.transformer.ExperimentalFrameExtractor
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressFramePreviewState
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.FramePreview
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/*
 * TV 进度条缩略图的取帧源.
 *
 * 为什么不直接用 mediamp 的 FramePreview (即 rememberMediaProgressFramePreviewState):
 * Android 侧那个实现是 MediaMetadataRetriever, 走平台 extractor, **没有 HLS 解复用器** ——
 * 而在线源基本都是 m3u8, 于是每个位置都返回 null, 表现为浮窗里永远一块黑底. 桌面端正常是因为
 * mpv 后端另起一个精简 mpv 实例取帧, ffmpeg 有 HLS 解复用器.
 *
 * 这里改用 media3 的 ExperimentalFrameExtractor: 它内部起一个 ExoPlayer 把帧解到 GL 纹理再读回,
 * 所以**凡是 ExoPlayer 能播的都能取**, 与 mpv 那套是同一思路.
 *
 * 实测 (Shield, m3u8 在线源, 边播边取, 输出 640x360):
 * - 硬解一次成功, 第二路解码器开得起来 (软解退路没触发过)
 * - 帧精确 seek 典型 1.0~1.7 秒, 最快 0.5 秒, 偶发 9 秒 (网络抖动, 靠超时兜)
 * - 关键帧 seek (CLOSEST_SYNC) 只快约三成, 但漂移最大 -1.9 秒: 该源 GOP 约 5.5 秒, 和遥控器
 *   5 秒一步的步长撞车 —— 连按两步会落回同一个关键帧, 缩略图不动. 所以用 [SeekParameters.EXACT],
 *   多花的三成买的是"图和圆点对得上".
 *
 * BT 源走不了这条路 ([ExperimentalFrameExtractor] 只收 MediaItem, 没法注入 DataSource), 仍退回
 * mediamp 的实现 —— 那条路在 BT 已下载区域本来就能用.
 */

private val logger = logger("TvFramePreviewSource")

/** 单次取帧上限: 实测偶发 9 秒 (网络抖动). 到点放弃并弃掉会话, 不让它拖累后面每一次请求. */
private val FRAME_TIMEOUT: Duration = 6.seconds

/** 超过这个耗时记一行: 帮助事后判断是网络还是解码的问题. */
private val FRAME_SLOW_THRESHOLD: Duration = 2.seconds

/**
 * 请求防抖. 比手机端默认的 50ms 长: 取一帧要一秒以上, 而遥控器按住不放是 50ms 一发 ——
 * 防抖窗口必须盖住连发间隔, 否则每一发都会启动一次注定被丢弃的解码.
 *
 * [MediaProgressFramePreviewState.requestFrame] 里 `delay` 在 `fetchFrame` **之前**,
 * 所以窗口内被取消的请求根本不会碰到解码器.
 */
private const val FRAME_DEBOUNCE_MILLIS = 200L

/**
 * 关闭位置网格对齐 (0 = 按请求位置原样取帧).
 *
 * 上游默认 2000ms: 请求位置先向下取整到 2 秒的倍数, 用来给鼠标悬浮那种连续位置做缓存命中和
 * 去重. 遥控器场景下它**只带来误差不带来收益** —— 圆点位置本来就是"进入时的播放位置 + k×5 秒"
 * 的离散值, 不需要再去重, 而对齐会让画面比时间标签**最多晚 2 秒** (平均 1 秒).
 *
 * 关掉后缓存键变成精确位置: 同一次拖拽里左右来回扫仍然命中 (位置可复现), 而误差降到 seek 本身
 * 的精度, 即不到一帧 (实测 1~29ms).
 */
private const val FRAME_POSITION_GRID_MILLIS = 0L

/**
 * TV 版进度条缩略图状态: 与 `rememberMediaProgressFramePreviewState` 同形, 只是取帧源换成
 * [ExperimentalFrameExtractor] (见文件头). 返回的 state 由 `MediaProgressSlider` 消费.
 */
@Composable
internal fun rememberTvFramePreviewState(
    player: MediampPlayer,
    maxWidth: Dp,
    maxHeight: Dp,
): MediaProgressFramePreviewState {
    val context = LocalContext.current
    val density = LocalDensity.current
    val source = remember(context, player) { TvFramePreviewSource(context, player) }
    // 会话持有一个 ExoPlayer 实例, 离屏必须释放
    DisposableEffect(source) { onDispose { source.release() } }
    val state = remember(source, density, maxWidth, maxHeight) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val maxHeightPx = with(density) { maxHeight.roundToPx() }
        MediaProgressFramePreviewState(
            fetchFrame = { source.getFrame(it, maxWidthPx, maxHeightPx) },
            debounceMillis = FRAME_DEBOUNCE_MILLIS,
            positionGridMillis = FRAME_POSITION_GRID_MILLIS,
        )
    }
    LaunchedEffect(state, source, player) {
        player.mediaData.collect { data ->
            state.onMediaChanged()
            source.onMediaChanged(data)
            if (data != null) {
                // 预热: 建会话 (含 ExoPlayer 启动 + 容器/播放列表解析) 是这条链路最贵的一步,
                // 提前在起播时做掉, 首次拖拽就不用等它. 同时也是一次能力探测 ——
                // 失败会把 MediaProgressFramePreviewState.framesAvailable 置 false,
                // 浮窗直接退化成只显示时间, 而不是先给一块黑底
                runCatching { state.prewarm(player.getCurrentPositionMillis()) }
            }
        }
    }
    return state
}

@OptIn(UnstableApi::class, ExperimentalMediampApi::class)
private class TvFramePreviewSource(
    private val context: Context,
    private val player: MediampPlayer,
) {
    /** 串行化: 取帧会话内部是单个 ExoPlayer, 并发 seek 没有意义也不安全. */
    private val mutex = Mutex()

    private var extractor: ExperimentalFrameExtractor? = null

    /** 会话对应的媒体与输出尺寸: 任一变化都要重建 (缩放是 setMediaItem 时定的). */
    private var sessionMedia: MediaData? = null
    private var sessionWidth = 0
    private var sessionHeight = 0

    private var currentMedia: MediaData? = null

    /** BT 源的退路: mediamp 自带的 MediaMetadataRetriever 实现. */
    private val fallback: FramePreview? by lazy { player.features[FramePreview] }

    fun onMediaChanged(data: MediaData?) {
        currentMedia = data
        // 会话的失效在 [obtainLocked] 里按身份判断, 这里不动 extractor:
        // 本方法在主线程的流收集里调用, 拿不到 mutex
    }

    suspend fun getFrame(positionMillis: Long, maxWidthPx: Int, maxHeightPx: Int): ImageBitmap? {
        if (maxWidthPx <= 0 || maxHeightPx <= 0) return null
        val data = currentMedia ?: return null
        return when (data) {
            is UriMediaData -> extractFrame(data, positionMillis, maxWidthPx, maxHeightPx)
            else -> fallbackFrame(positionMillis, maxWidthPx, maxHeightPx)
        }
    }

    fun release() {
        // 主线程同步释放: DisposableEffect 的 onDispose 就在主线程上, 不能挂起等 mutex.
        // 此刻若有取帧在飞, 它持有的是同一个 extractor —— 但界面已经离屏, 结果无人消费,
        // 而 ExperimentalFrameExtractor.release 内部会等自己的播放器停掉
        releaseLocked()
    }

    private suspend fun extractFrame(
        data: UriMediaData,
        positionMillis: Long,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): ImageBitmap? = withContext(Dispatchers.Main) {
        mutex.withLock {
            // NonCancellable: 取消只是让上层丢弃结果, 而 getFrame 已经推动了内部播放器去 seek ——
            // 半路撤掉会让会话状态和我们的认知不一致, 下一次请求反而更慢. 靠防抖控制启动次数即可
            withContext(NonCancellable) {
                val extractor = obtainLocked(data, maxWidthPx, maxHeightPx) ?: return@withContext null
                val mark = TimeSource.Monotonic.markNow()
                val frame = try {
                    withTimeoutOrNull(FRAME_TIMEOUT) {
                        extractor.getFrame(positionMillis.coerceAtLeast(0L)).await()
                    }
                } catch (e: Throwable) {
                    logger.warn(e) { "Frame extraction failed at $positionMillis ms" }
                    releaseLocked() // 失败的会话通常不会自愈, 下次重建
                    return@withContext null
                }
                if (frame == null) {
                    logger.warn { "Frame extraction timed out at $positionMillis ms after $FRAME_TIMEOUT" }
                    releaseLocked()
                    return@withContext null
                }
                val elapsed = mark.elapsedNow()
                if (elapsed > FRAME_SLOW_THRESHOLD) {
                    logger.info { "Slow frame extraction at $positionMillis ms: $elapsed" }
                }
                // 位图已经在 GPU 侧缩到了目标尺寸, 直接包成 ImageBitmap (不能 recycle, 交给上层持有)
                frame.bitmap.asImageBitmap()
            }
        }
    }

    /** 复用现有会话, 媒体或尺寸变了则重建. 必须在主线程且持有 [mutex] 时调用. */
    private fun obtainLocked(data: UriMediaData, maxWidthPx: Int, maxHeightPx: Int): ExperimentalFrameExtractor? {
        extractor?.let { existing ->
            if (sessionMedia === data && sessionWidth == maxWidthPx && sessionHeight == maxHeightPx) {
                return existing
            }
            releaseLocked()
        }
        return try {
            ExperimentalFrameExtractor(
                context,
                ExperimentalFrameExtractor.Configuration.Builder()
                    // 帧精确: 关键帧 seek 会漂移到几秒外, 与 5 秒的步长撞车 (见文件头实测)
                    .setSeekParameters(SeekParameters.EXACT)
                    .build(),
            ).also { created ->
                created.setMediaItem(
                    MediaItem.fromUri(data.uri),
                    // GPU 侧先缩到目标尺寸再读回: 不缩的话每帧是一整张 1080p/4K ARGB (8MB/33MB)
                    listOf(
                        Presentation.createForWidthAndHeight(
                            maxWidthPx, maxHeightPx, Presentation.LAYOUT_SCALE_TO_FIT,
                        ),
                    ),
                )
                extractor = created
                sessionMedia = data
                sessionWidth = maxWidthPx
                sessionHeight = maxHeightPx
                logger.info { "Frame extractor session created (${maxWidthPx}x$maxHeightPx)" }
            }
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to create frame extractor session" }
            releaseLocked()
            null
        }
    }

    private fun releaseLocked() {
        val existing = extractor ?: return
        extractor = null
        sessionMedia = null
        sessionWidth = 0
        sessionHeight = 0
        runCatching { existing.release() }
    }

    private suspend fun fallbackFrame(positionMillis: Long, maxWidthPx: Int, maxHeightPx: Int): ImageBitmap? {
        val frame = fallback?.getPreviewFrame(positionMillis, maxWidthPx, maxHeightPx) ?: return null
        return Bitmap
            .createBitmap(frame.pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (e: CancellationException) {
                continuation.cancel()
            } catch (e: Throwable) {
                // ExecutionException 包着真正的原因, 抛外壳看不出是什么问题
                continuation.resumeWithException(e.cause ?: e)
            }
        },
        Executor { it.run() }, // 只是取一个已完成的结果, 不需要额外线程
    )
    continuation.invokeOnCancellation { cancel(false) }
}
