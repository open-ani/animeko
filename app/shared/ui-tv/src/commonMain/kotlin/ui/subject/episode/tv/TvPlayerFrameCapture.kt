/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.tv

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.videoplayer.ui.findAndroidVideoSurface
import org.openani.mediamp.MediampPlayer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** 降采样目标宽度: 缓存页背景会再压半透明遮罩, 720p 足够, 避免持有 4K 位图 (~33MB). */
private const val CAPTURE_MAX_WIDTH = 1280

/**
 * 抓取播放器当前画面 (跳转缓存页前用作背景).
 *
 * 本模块只有 android 一个 target, 因此不需要 expect/actual —— 直接用 Android 的
 * [PixelCopy] 从 SurfaceView 取帧 (SurfaceView 的内容在独立的显示层上,
 * 常规的 Compose 截图/View.draw 取不到).
 */
internal suspend fun captureTvPlayerFrame(player: MediampPlayer): ImageBitmap? =
    withContext(Dispatchers.Main.immediate) {
        val surfaceView = player.findAndroidVideoSurface() ?: return@withContext null
        if (!surfaceView.holder.surface.isValid || surfaceView.width <= 0 || surfaceView.height <= 0) {
            return@withContext null
        }
        // PixelCopy 会把 Surface 内容缩放进目标位图
        val scale = (CAPTURE_MAX_WIDTH.toFloat() / surfaceView.width).coerceAtMost(1f)
        val width = (surfaceView.width * scale).toInt().coerceAtLeast(1)
        val height = (surfaceView.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val result = runCatching {
            suspendCoroutine { continuation ->
                PixelCopy.request(
                    surfaceView,
                    bitmap,
                    { copyResult -> continuation.resume(copyResult) },
                    Handler(Looper.getMainLooper()),
                )
            }
        }.getOrElse { PixelCopy.ERROR_UNKNOWN }
        if (result == PixelCopy.SUCCESS) {
            bitmap.asImageBitmap()
        } else {
            bitmap.recycle()
            null
        }
    }
