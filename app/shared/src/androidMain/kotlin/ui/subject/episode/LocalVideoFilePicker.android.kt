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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * 系统文件选择器返回 `content://` Uri. 直接把 Uri 交给播放器, 由 ExoPlayer 通过
 * ContentResolver 读取, 避免把大文件完整复制到应用缓存.
 */
@Composable
actual fun rememberLocalVideoFilePicker(
    onPicked: (LocalVideoFile) -> Unit,
    initialDirectory: String?,
): (() -> Unit)? {
    val context = LocalContext.current
    val onPickedUpdated by rememberUpdatedState(onPicked)
    val initialUri = remember(context, initialDirectory) { initialDocumentUri(context, initialDirectory) }

    val launcher = rememberLauncherForActivityResult(OpenVideoDocumentContract()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onPickedUpdated(
            LocalVideoFile.Document(
                uri = uri.toString(),
                displayName = queryDisplayName(context, uri),
            ),
        )
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

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull()
