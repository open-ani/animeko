/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboard
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.preference.DanmakuCacheStrategy
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.settings.compressBackup
import me.him188.ani.app.ui.settings.decompressBackup
import me.him188.ani.tracking.api.TrackingBackupValidationException
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_storage_backup_op_backup_error
import me.him188.ani.app.ui.lang.settings_storage_backup_op_restore
import me.him188.ani.app.ui.lang.settings_storage_backup_op_restore_error
import me.him188.ani.app.ui.lang.settings_storage_backup_op_restore_succees
import me.him188.ani.app.ui.lang.settings_storage_backup_op_restore_warning
import me.him188.ani.app.ui.lang.settings_storage_backup_title
import me.him188.ani.app.ui.lang.settings_storage_danmaku_cache_strategy_description_cache_on_collection_doing_media_play
import me.him188.ani.app.ui.lang.settings_storage_danmaku_cache_strategy_description_cache_on_media_cache
import me.him188.ani.app.ui.lang.settings_storage_danmaku_cache_strategy_description_do_not_cache
import me.him188.ani.app.ui.lang.settings_storage_danmaku_cache_strategy_title
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.DropdownItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Stable
class CacheDirectoryGroupState(
    val mediaCacheSettingsState: SettingsState<MediaCacheSettings>,
    val permissionManager: PermissionManager,
    val trackingBindingsAvailable: Boolean,
    val onGetBackupData: suspend (BackupSelection) -> String,
    val onRestoreSettings: suspend (String) -> Boolean,
)

data class BackupSelection(val settings: Boolean = true, val trackingBindings: Boolean = true) {
    val hasContent get() = settings || trackingBindings
}

@Composable
fun SettingsScope.BackupSettings(state: CacheDirectoryGroupState) {
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var backupSettings by remember { mutableStateOf(true) }
    var backupTracking by remember { mutableStateOf(state.trackingBindingsAvailable) }

    val scope = rememberAsyncHandler()
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val backupErrorText = stringResource(Lang.settings_storage_backup_op_backup_error)

    Group({ Text(stringResource(Lang.settings_storage_backup_title)) }) {
        TextItem(
            onClick = { showBackupDialog = true },
            title = { Text("Create backup") },
            description = { Text("Choose what to save in an Animeko backup file") },
        )
        TextItem(
            onClick = { restoreError = null; showRestoreDialog = true },
            title = { Text("Restore backup") },
            description = { Text("Import settings and tracking matches from a backup file") },
        )
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Choose backup contents") },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { backupSettings = !backupSettings }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(backupSettings, onCheckedChange = { backupSettings = it })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text("App settings")
                            Text("Preferences and Animeko sign-in data", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (state.trackingBindingsAvailable) Row(
                        Modifier.fillMaxWidth().clickable { backupTracking = !backupTracking }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(backupTracking, onCheckedChange = { backupTracking = it })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text("Tracking matches")
                            Text("Saved title links for tracking services; reconnect accounts after restore", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("This backup does not include downloaded videos or tracking account tokens.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                    Text("Keep the backup file or copied text private if app settings are selected.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                            val date = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')}"
                            val time = "${now.hour.toString().padStart(2, '0')}-${now.minute.toString().padStart(2, '0')}"
                            val filename = "animeko_${date}_$time"
                            val target = FileKit.openFileSaver(suggestedName = filename, extension = "animekobk")
                                ?: return@launch
                            val data = state.onGetBackupData(BackupSelection(backupSettings, backupTracking))
                            target.write(compressBackup(data.encodeToByteArray()))
                            showBackupDialog = false
                            toaster.toast("Backup saved")
                        } catch (_: Exception) {
                            toaster.toast(backupErrorText)
                        }
                    }
                }, enabled = backupSettings || backupTracking) { Text("Save backup") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val data = state.onGetBackupData(BackupSelection(backupSettings, backupTracking))
                            clipboard.setClipEntryText(data)
                            showBackupDialog = false
                            toaster.toast("Backup copied")
                        } catch (_: Exception) {
                            toaster.toast(backupErrorText)
                        }
                    }
                }, enabled = backupSettings || backupTracking) { Text("Copy") }
            },
        )
    }

    if (showRestoreDialog) {
        val restoreSuccess = stringResource(Lang.settings_storage_backup_op_restore_succees)
        val restoreFailed = stringResource(Lang.settings_storage_backup_op_restore_error)

        AlertDialog(
            { showRestoreDialog = false },
            icon = { Icon(Icons.Rounded.Restore, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Restore backup") },
            text = {
                Column {
                    Text(stringResource(Lang.settings_storage_backup_op_restore_warning))
                    restoreError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                TextButton(
                    {
                        scope.launch {
                            try {
                                restoreError = null
                                val source = FileKit.openFilePicker() ?: return@launch
                                val bytes = source.readBytes()
                                val content = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                                    decompressBackup(bytes).decodeToString()
                                } else {
                                    bytes.decodeToString()
                                }
                                val result = state.onRestoreSettings(content)
                                toaster.toast(if (result) restoreSuccess else restoreFailed)
                                if (result) showRestoreDialog = false else restoreError = restoreFailed
                            } catch (failure: TrackingBackupValidationException) {
                                restoreError = failure.message ?: restoreFailed
                            } catch (_: Exception) {
                                restoreError = restoreFailed
                            }
                        }
                    },
                ) {
                    Text("Choose file", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton({
                    scope.launch {
                        try {
                            restoreError = null
                            val content = clipboard.getClipEntryText()
                            val result = content?.let { state.onRestoreSettings(it) } == true
                            toaster.toast(if (result) restoreSuccess else restoreFailed)
                            if (result) showRestoreDialog = false else restoreError = restoreFailed
                        } catch (failure: TrackingBackupValidationException) {
                            restoreError = failure.message ?: restoreFailed
                        } catch (_: Exception) {
                            restoreError = restoreFailed
                        }
                    }
                }) { Text("Paste") }
            },
        )
    }
}


@Composable
fun SettingsScope.DanmakuCacheSettings(state: CacheDirectoryGroupState) {
    val mediaCacheSettings by state.mediaCacheSettingsState
    val tasker = rememberAsyncHandler()

    DropdownItem(
        title = { Text(stringResource(Lang.settings_storage_danmaku_cache_strategy_title)) },
        selected = { mediaCacheSettings.danmakuCacheStrategy },
        values = { DanmakuCacheStrategy.entries },
        description = {
            Text(
                when (mediaCacheSettings.danmakuCacheStrategy) {
                    DanmakuCacheStrategy.DON_NOT_CACHE ->
                        stringResource(Lang.settings_storage_danmaku_cache_strategy_description_do_not_cache)

                    DanmakuCacheStrategy.CACHE_ON_COLLECTION_DOING_MEDIA_PLAY ->
                        stringResource(Lang.settings_storage_danmaku_cache_strategy_description_cache_on_collection_doing_media_play)

                    DanmakuCacheStrategy.CACHE_ON_MEDIA_CACHE ->
                        stringResource(Lang.settings_storage_danmaku_cache_strategy_description_cache_on_media_cache)
                },
            )
        },
        itemText = { strategy ->
            Text(
                when (strategy) {
                    DanmakuCacheStrategy.DON_NOT_CACHE -> "NONE"
                    DanmakuCacheStrategy.CACHE_ON_MEDIA_CACHE -> "MEDIA"
                    DanmakuCacheStrategy.CACHE_ON_COLLECTION_DOING_MEDIA_PLAY -> "COLLECT"
                },
            )
        },
        onSelect = { newStrategy ->
            tasker.launch {
                state.mediaCacheSettingsState.updateSuspended(
                    mediaCacheSettings.copy(danmakuCacheStrategy = newStrategy),
                )
            }
        },
    )
}

@Composable
expect fun SettingsScope.CacheDirectoryGroup(
    state: CacheDirectoryGroupState,
)
