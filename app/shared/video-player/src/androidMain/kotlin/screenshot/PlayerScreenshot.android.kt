/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.screenshot

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.DpRect
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.platform.findActivity
import me.him188.ani.app.videoplayer.ui.findAndroidVideoSurface
import me.him188.ani.utils.coroutines.runCatchingCancellable
import org.koin.mp.KoinPlatform
import org.openani.mediamp.MediampPlayer
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
actual fun rememberPlayerScreenshotCapturer(): PlayerScreenshotCapturer {
    val context = LocalContext.current
    return remember(context) { AndroidPlayerScreenshotCapturer(context) }
}

@Composable
actual fun rememberPlayerScreenshotSharer(): PlayerScreenshotSharer {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    return remember(context, clipboard) { AndroidPlayerScreenshotSharer(context, clipboard) }
}

private const val GALLERY_DIRECTORY = "Animeko"
private const val MIME_PNG = "image/png"

/**
 * 用 [PixelCopy] 从 ExoPlayer 的 SurfaceView 抓取当前帧, 保存到系统相册的 `Pictures/Animeko`.
 *
 * Android 10 起经 MediaStore 写入, 不需要权限; Android 9 及以下直接写公共图片目录,
 * 需要先取得 WRITE_EXTERNAL_STORAGE, 文件经 FileProvider 以 content URI 共享.
 */
private class AndroidPlayerScreenshotCapturer(
    private val context: Context,
) : PlayerScreenshotCapturer {
    override fun isSupported(player: MediampPlayer): Boolean = true

    override suspend fun capture(player: MediampPlayer, fileName: String): PlayerScreenshotResult {
        if (!ensureWritePermission()) {
            return PlayerScreenshotResult.Failure(PlayerScreenshotFailure.PermissionDenied)
        }
        val bitmap = capturePlayerSurface(player)
            ?: return PlayerScreenshotResult.Failure(PlayerScreenshotFailure.NoFrame)
        return runCatchingCancellable {
            val uri = saveToGallery(fileName, bitmap)
            val full = bitmap.asImageBitmap()
            val preview = full.limitedToPreviewSize()
            // 预览另有缩小的副本时整帧不再需要
            if (preview !== full) bitmap.recycle()
            PlayerScreenshotResult.Success(SavedPlayerScreenshot(preview, fileName, uri.toString()))
        }.getOrElse { PlayerScreenshotResult.Failure(PlayerScreenshotFailure.SaveFailed(it)) }
    }

    private suspend fun ensureWritePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return KoinPlatform.getKoin().get<PermissionManager>().requestWriteExternalStoragePermission(context)
    }

    private suspend fun capturePlayerSurface(player: MediampPlayer): Bitmap? = withContext(Dispatchers.Main.immediate) {
        val surfaceView = player.findAndroidVideoSurface() ?: return@withContext null
        if (!surfaceView.holder.surface.isValid || surfaceView.width <= 0 || surfaceView.height <= 0) {
            return@withContext null
        }

        val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
        val result = suspendCoroutine { continuation ->
            PixelCopy.request(
                surfaceView,
                bitmap,
                { copyResult -> continuation.resume(copyResult) },
                Handler(Looper.getMainLooper()),
            )
        }
        if (result == PixelCopy.SUCCESS) {
            bitmap
        } else {
            bitmap.recycle()
            null
        }
    }

    /** @return 保存后的 content URI. 相册拒绝写入时抛出 [IOException]. */
    private suspend fun saveToGallery(fileName: String, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .resolve(GALLERY_DIRECTORY)
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Cannot create $directory")
            }

            val file = directory.resolve(fileName)
            val encoded = file.outputStream().buffered().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (!encoded) {
                file.delete()
                throw IOException("Cannot encode screenshot to $file")
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(MIME_PNG), null)
            return@withContext FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_PNG)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$GALLERY_DIRECTORY")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore rejected the screenshot")

        try {
            val encoded = resolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (encoded != true) throw IOException("Cannot write screenshot to $uri")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}

/** 「分享」交给系统分享面板; 「复制」把截图的 content URI 放进剪贴板, 接收方据此读取图片. */
private class AndroidPlayerScreenshotSharer(
    private val context: Context,
    private val clipboard: Clipboard,
) : PlayerScreenshotSharer {
    override suspend fun share(screenshot: SavedPlayerScreenshot, anchor: DpRect?): Boolean {
        val uri = Uri.parse(screenshot.location)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_PNG
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(screenshot.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null)
        if (context.findActivity() == null) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    override suspend fun copy(screenshot: SavedPlayerScreenshot): Boolean = runCatchingCancellable {
        val uri = Uri.parse(screenshot.location)
        clipboard.setClipEntry(ClipEntry(ClipData.newUri(context.contentResolver, screenshot.fileName, uri)))
    }.isSuccess
}
