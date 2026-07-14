/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import android.content.ContentValues
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.videoplayer.ui.findAndroidVideoSurface
import org.koin.mp.KoinPlatform
import org.openani.mediamp.MediampPlayer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal actual suspend fun takeAndroidPlayerScreenshot(
    context: Context,
    player: MediampPlayer,
    filename: String,
): Boolean {
    if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
        !KoinPlatform.getKoin().get<PermissionManager>().requestWriteExternalStoragePermission(context)
    ) {
        return false
    }
    val bitmap = capturePlayerSurface(player) ?: return false
    return try {
        savePlayerScreenshot(context, filename, bitmap)
    } finally {
        bitmap.recycle()
    }
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

private suspend fun savePlayerScreenshot(
    context: Context,
    filename: String,
    bitmap: Bitmap,
): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        @Suppress("DEPRECATION")
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            .resolve("Animeko")
        if (!directory.exists() && !directory.mkdirs()) return@withContext false

        val file = directory.resolve(filename)
        val saved = file.outputStream().buffered().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        if (!saved) return@withContext false
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        return@withContext true
    }

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Animeko")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return@withContext false

    try {
        val saved = resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } == true
        if (!saved) {
            resolver.delete(uri, null, null)
            return@withContext false
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
}
