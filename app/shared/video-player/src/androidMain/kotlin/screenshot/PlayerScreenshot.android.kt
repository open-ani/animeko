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
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.platform.findActivity
import me.him188.ani.app.videoplayer.ui.findAndroidVideoSurface
import org.koin.mp.KoinPlatform
import org.openani.mediamp.MediampPlayer
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
    return remember(context) { AndroidPlayerScreenshotSharer(context) }
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
        return try {
            val uri = saveToGallery(fileName, bitmap)
                ?: return PlayerScreenshotResult.Failure(PlayerScreenshotFailure.SaveFailed(null))
            PlayerScreenshotResult.Success(SavedPlayerScreenshot(bitmap.asImageBitmap(), fileName, uri.toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PlayerScreenshotResult.Failure(PlayerScreenshotFailure.SaveFailed(e))
        }
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

    /** @return 保存后的 content URI; 相册拒绝写入时为 null */
    private suspend fun saveToGallery(fileName: String, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .resolve(GALLERY_DIRECTORY)
            if (!directory.isDirectory && !directory.mkdirs()) return@withContext null

            val file = directory.resolve(fileName)
            val saved = file.outputStream().buffered().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (!saved) {
                file.delete()
                return@withContext null
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
            ?: return@withContext null

        try {
            val saved = resolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } == true
            if (!saved) {
                resolver.delete(uri, null, null)
                return@withContext null
            }

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

/** 「分享」交给系统分享面板, 「打开」交给系统图片查看器; 两者都通过 content URI 授予读权限. */
private class AndroidPlayerScreenshotSharer(
    private val context: Context,
) : PlayerScreenshotSharer {
    override suspend fun share(screenshot: SavedPlayerScreenshot): PlayerScreenshotShareOutcome {
        val uri = Uri.parse(screenshot.location)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_PNG
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(screenshot.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return if (startActivity(Intent.createChooser(send, null))) {
            PlayerScreenshotShareOutcome.Shared
        } else {
            PlayerScreenshotShareOutcome.Failed
        }
    }

    override suspend fun open(screenshot: SavedPlayerScreenshot): Boolean {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(screenshot.location), MIME_PNG)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startActivity(view)
    }

    private fun startActivity(intent: Intent): Boolean {
        if (context.findActivity() == null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
