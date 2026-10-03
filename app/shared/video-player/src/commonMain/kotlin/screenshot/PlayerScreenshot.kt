/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntSize
import me.him188.ani.app.ui.foundation.imageviewer.sanitizedForFileName
import org.openani.mediamp.MediampPlayer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 已保存到系统相册 (Android) 或图片目录 (桌面) 的一张播放器截图.
 *
 * @property image 预览用的画面, 最长边不超过 [PREVIEW_MAX_LONG_SIDE]; 完整文件在 [location].
 * @property fileName 含扩展名的文件名.
 * @property location 平台定位符: Android 为 `content://` URI, 桌面为文件的绝对路径. 也是图片查看器可以直接加载的模型.
 */
@Immutable
class SavedPlayerScreenshot(
    val image: ImageBitmap,
    val fileName: String,
    val location: String,
)

/** 截图失败的原因, 用于向用户说明. */
sealed class PlayerScreenshotFailure {
    /** 用户拒绝了写入相册所需的权限. */
    data object PermissionDenied : PlayerScreenshotFailure()

    /** 当前平台或播放器后端不支持截图. */
    data object Unsupported : PlayerScreenshotFailure()

    /** 当前没有可截取的视频画面, 例如视频尚未开始渲染. */
    data object NoFrame : PlayerScreenshotFailure()

    /** 画面已截取但保存失败. */
    data class SaveFailed(val cause: Throwable) : PlayerScreenshotFailure()
}

sealed class PlayerScreenshotResult {
    class Success(val screenshot: SavedPlayerScreenshot) : PlayerScreenshotResult()
    class Failure(val reason: PlayerScreenshotFailure) : PlayerScreenshotResult()
}

/**
 * 平台截图器: 截取 [MediampPlayer] 当前渲染的视频帧 (不含弹幕与字幕叠层) 并保存到相册或图片目录.
 */
interface PlayerScreenshotCapturer {
    /** 当前平台与播放器后端是否支持截图. 为 `false` 时播放器不显示截图按钮. */
    fun isSupported(player: MediampPlayer): Boolean

    /**
     * 先确保拥有保存所需的权限 (没有则向用户申请), 然后截图并保存为 [fileName].
     *
     * 不抛出异常: 任何失败都以 [PlayerScreenshotResult.Failure] 返回.
     */
    suspend fun capture(player: MediampPlayer, fileName: String): PlayerScreenshotResult
}

/** 当前平台的截图器. */
@Composable
expect fun rememberPlayerScreenshotCapturer(): PlayerScreenshotCapturer

/**
 * 截图文件名: `条目ID-剧集序号-视频时间点.png`, 例如 `12345-01-23m45s678ms.png`.
 * 剧集序号中不能出现在文件名里的字符替换为 `_`.
 */
fun playerScreenshotFileName(subjectId: Int, episodeSort: String, positionMillis: Long): String {
    val position = positionMillis.coerceAtLeast(0)
    val minutes = position / 60_000
    val seconds = position % 60_000 / 1000
    val millis = position % 1000
    val sort = episodeSort.sanitizedForFileName().ifEmpty { "0" }
    return "$subjectId-$sort-${minutes}m${seconds}s${millis}ms.png"
}

/**
 * 预览图最长边的上限. 入场时画面会短暂铺满播放器, 1920 足够清晰, 又不至于让 4K 整帧在面板存活期间常驻内存和纹理.
 */
internal const val PREVIEW_MAX_LONG_SIDE = 1920

/** 最长边超过 [PREVIEW_MAX_LONG_SIDE] 时按比例缩小成一张新图, 否则返回自身. */
internal fun ImageBitmap.limitedToPreviewSize(): ImageBitmap {
    val longSide = max(width, height)
    if (longSide <= PREVIEW_MAX_LONG_SIDE) return this
    val scale = PREVIEW_MAX_LONG_SIDE.toFloat() / longSide
    val size = IntSize(
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
    )
    val scaled = ImageBitmap(size.width, size.height)
    Canvas(scaled).drawImageRect(
        image = this,
        dstSize = size,
        paint = Paint().apply { filterQuality = FilterQuality.Medium },
    )
    return scaled
}
