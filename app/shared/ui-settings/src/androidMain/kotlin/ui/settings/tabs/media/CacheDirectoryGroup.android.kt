/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_storage_local_video_library_choose
import me.him188.ani.app.ui.lang.settings_storage_local_video_library_clear
import me.him188.ani.app.ui.lang.settings_storage_local_video_library_description
import me.him188.ani.app.ui.lang.settings_storage_local_video_library_not_set
import me.him188.ani.app.ui.lang.settings_storage_local_video_library_title
import me.him188.ani.app.ui.settings.framework.components.RowButtonItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun SettingsScope.CacheDirectoryGroup(state: CacheDirectoryGroupState) {
    LocalVideoLibraryGroup(state)
    DanmakuCacheSettings(state)
}

@Composable
private fun SettingsScope.LocalVideoLibraryGroup(state: CacheDirectoryGroupState) {
    val settings by state.localVideoLibrarySettingsState
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val chooseDirectory = {
        scope.launch {
            val newDirectory = state.permissionManager.requestExternalDocumentTree(context)
                ?: return@launch
            val oldDirectory = settings.directory
            if (oldDirectory != null && oldDirectory != newDirectory) {
                releasePersistedPermission(context, oldDirectory)
            }
            state.localVideoLibrarySettingsState.update(
                settings.copy(directory = newDirectory),
            )
        }
        Unit
    }

    Group(
        title = { Text(stringResource(Lang.settings_storage_local_video_library_title)) },
        description = { Text(stringResource(Lang.settings_storage_local_video_library_description)) },
    ) {
        TextItem(
            title = {
                Text(
                    settings.directory?.let { displayDirectoryName(context, it) }
                        ?: stringResource(Lang.settings_storage_local_video_library_not_set),
                )
            },
            icon = { Icon(Icons.Rounded.FolderOpen, null) },
            onClick = chooseDirectory,
        )
        RowButtonItem(
            onClick = chooseDirectory,
            title = { Text(stringResource(Lang.settings_storage_local_video_library_choose)) },
        )
        if (settings.directory != null) {
            RowButtonItem(
                onClick = {
                    settings.directory?.let { releasePersistedPermission(context, it) }
                    state.localVideoLibrarySettingsState.update(
                        settings.copy(directory = null),
                    )
                },
                title = { Text(stringResource(Lang.settings_storage_local_video_library_clear)) },
            )
        }
    }
}

private fun displayDirectoryName(context: Context, directory: String): String {
    val uri = runCatching { Uri.parse(directory) }.getOrNull() ?: return directory
    if (uri.scheme != "content") return directory

    return runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
        context.contentResolver.query(
            documentUri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: Uri.decode(uri.lastPathSegment ?: directory)
}

private fun releasePersistedPermission(context: Context, directory: String) {
    val uri = runCatching { Uri.parse(directory) }.getOrNull() ?: return
    runCatching {
        context.contentResolver.releasePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
