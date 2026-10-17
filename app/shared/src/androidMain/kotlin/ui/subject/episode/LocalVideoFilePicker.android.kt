/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_local_file_import_failed
import me.him188.ani.app.ui.lang.media_selector_local_file_importing
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.jetbrains.compose.resources.getString
import java.io.File

private val logger = logger("LocalVideoFilePicker")

private const val LOCAL_VIDEO_CACHE_DIR_NAME = "local-video"
private const val LOCAL_VIDEO_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

/**
 * 系统文件选择器返回的是 `content://` Uri, 播放器无法按文件路径读取,
 * 因此先把文件复制到缓存目录, 再把缓存文件路径交给 [onPicked].
 */
@Composable
actual fun rememberLocalVideoFilePicker(
    onPicked: (SystemPath) -> Unit,
    initialDirectory: String?,
): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val onPickedUpdated by rememberUpdatedState(onPicked)
    val initialUri = remember(context, initialDirectory) { initialDocumentUri(context, initialDirectory) }

    val launcher = rememberLauncherForActivityResult(OpenVideoDocumentContract()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            toaster.toast(getString(Lang.media_selector_local_file_importing))
            val file = withContext(Dispatchers.IO) { copyToCache(context, uri) }
            if (file == null) {
                toaster.toast(getString(Lang.media_selector_local_file_import_failed))
            } else {
                onPickedUpdated(Path(file.absolutePath).inSystem)
            }
        }
    }
    return { launcher.launch(OpenVideoRequest(listOf("video/*"), initialUri)) }
}

private data class OpenVideoRequest(
    val mimeTypes: List<String>,
    val initialUri: Uri?,
)

/**
 * 与 [androidx.activity.result.contract.ActivityResultContracts.OpenDocument] 相同, 但支持指定初始目录.
 */
private class OpenVideoDocumentContract : ActivityResultContract<OpenVideoRequest, Uri?>() {
    override fun createIntent(context: Context, input: OpenVideoRequest): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            putExtra(Intent.EXTRA_MIME_TYPES, input.mimeTypes.toTypedArray())
            input.initialUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}

/**
 * 本地视频库保存的是 SAF tree URI, 而 `ACTION_OPEN_DOCUMENT` 的初始位置需要 document URI.
 */
private fun initialDocumentUri(context: Context, directory: String?): Uri? {
    if (directory.isNullOrBlank()) return null
    val treeUri = runCatching { Uri.parse(directory) }.getOrNull() ?: return null
    if (treeUri.scheme != "content" || !DocumentsContract.isTreeUri(treeUri)) return null
    // 设置可能由备份恢复, 而对应的 SAF 授权只存在于原设备上.
    if (context.contentResolver.persistedUriPermissions.none { it.uri == treeUri && it.isReadPermission }) {
        return null
    }
    return runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    }.getOrNull()
}

private fun copyToCache(context: Context, uri: Uri): File? {
    val cacheDir = File(context.cacheDir, LOCAL_VIDEO_CACHE_DIR_NAME).apply { mkdirs() }
    deleteExpiredFiles(cacheDir)
    val target = File(cacheDir, createCacheFileName(context, uri))
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target
    }.getOrElse { throwable ->
        logger.warn(throwable) { "Failed to copy local video file: $uri" }
        target.delete()
        null
    }
}

private fun createCacheFileName(context: Context, uri: Uri): String {
    val name = queryDisplayName(context, uri)
        ?.let { File(it).name }
        ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
        ?: "video${guessExtension(context, uri)}"
    // 用 Uri 的哈希前缀区分同名文件, 重复选择同一个文件时则复用同一个副本.
    return "${uri.hashCode()}-$name"
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull()

private fun guessExtension(context: Context, uri: Uri): String {
    val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: return ""
    return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let { ".$it" }.orEmpty()
}

private fun deleteExpiredFiles(dir: File) {
    val expiredBefore = System.currentTimeMillis() - LOCAL_VIDEO_CACHE_MAX_AGE_MS
    dir.listFiles()?.forEach { file ->
        if (file.isFile && file.lastModified() < expiredBefore) {
            if (!file.delete()) {
                logger.warn { "Failed to delete expired local video cache file: $file" }
            }
        }
    }
}
