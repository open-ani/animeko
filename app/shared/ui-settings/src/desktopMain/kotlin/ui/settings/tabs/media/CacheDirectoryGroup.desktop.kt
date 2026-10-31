/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import me.him188.ani.app.platform.LocalDesktopContext
import me.him188.ani.app.platform.files
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_storage_bt_cache_location
import me.him188.ani.app.ui.lang.settings_storage_bt_cache_location_description
import me.him188.ani.app.ui.lang.settings_storage_choose_directory
import me.him188.ani.app.ui.lang.settings_storage_directory_create_failed
import me.him188.ani.app.ui.lang.settings_storage_directory_not_exist
import me.him188.ani.app.ui.lang.settings_storage_local_video_library_choose
import me.him188.ani.app.ui.lang.settings_storage_local_video_library_clear
import me.him188.ani.app.ui.lang.settings_storage_local_video_library_description
import me.him188.ani.app.ui.lang.settings_storage_local_video_library_not_set
import me.him188.ani.app.ui.lang.settings_storage_local_video_library_path
import me.him188.ani.app.ui.lang.settings_storage_local_video_library_title
import me.him188.ani.app.ui.lang.settings_storage_open_bt_cache_directory
import me.him188.ani.app.ui.lang.settings_storage_open_directory_chooser
import me.him188.ani.app.ui.lang.settings_storage_path_is_invalid
import me.him188.ani.app.ui.lang.settings_storage_title
import me.him188.ani.app.ui.settings.framework.components.RowButtonItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextFieldItem
import me.him188.ani.utils.io.absolutePath
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File

@Composable
actual fun SettingsScope.CacheDirectoryGroup(state: CacheDirectoryGroupState) {
    LocalVideoLibraryGroup(state)

    Group({ Text(stringResource(Lang.settings_storage_title)) }) {
        val mediaCacheSettings by state.mediaCacheSettingsState

        val context = LocalDesktopContext.current

        val defaultSaveDir = remember { context.files.defaultMediaCacheBaseDir.absolutePath }
        val currentSaveDir: String by remember {
            derivedStateOf {
                mediaCacheSettings.saveDir ?: defaultSaveDir
            }
        }

        val toaster = LocalToaster.current

        val directoryNotExistMessage = stringResource(Lang.settings_storage_directory_not_exist)
        val pathIsInvalidMessage = stringResource(Lang.settings_storage_path_is_invalid)
        val directoryCreateFailed = stringResource(Lang.settings_storage_directory_create_failed)

        TextFieldItem(
            currentSaveDir,
            title = { Text(stringResource(Lang.settings_storage_bt_cache_location)) },
            onValueChangeCompleted = {
                val dir = try {
                    File(it).canonicalFile
                } catch (e: Exception) {
                    toaster.toast("$pathIsInvalidMessage: ${e.message}")
                    return@TextFieldItem
                }

                if (!dir.exists() && !dir.mkdirs()) {
                    toaster.toast("$directoryCreateFailed: ${dir.path}")
                    return@TextFieldItem
                }

                if (!dir.isDirectory) {
                    toaster.toast(pathIsInvalidMessage)
                    return@TextFieldItem
                }

                state.mediaCacheSettingsState.update(
                    mediaCacheSettings.copy(saveDir = dir.path),
                )
            },
            textFieldDescription = {
                Text(stringResource(Lang.settings_storage_bt_cache_location_description))
            },
            extra = { textFieldValue ->
                val directoryPicker = rememberDirectoryPickerLauncher(
                    directory = PlatformFile(currentSaveDir),
                    dialogSettings = FileKitDialogSettings(
                        title = stringResource(Lang.settings_storage_choose_directory),
                    ),
                ) {
                    it?.let {
                        textFieldValue.value = it.file.absolutePath
                    }
                }
                OutlinedButton({ directoryPicker.launch() }) {
                    Text(stringResource(Lang.settings_storage_open_directory_chooser))
                }
            },
        )
        RowButtonItem(
            title = { Text(stringResource(Lang.settings_storage_open_bt_cache_directory)) },
            icon = { Icon(Icons.Rounded.ArrowOutward, null) },
            onClick = {
                val file = File(mediaCacheSettings.saveDir ?: defaultSaveDir)
                if (file.exists()) {
                    Desktop.getDesktop().open(file)
                } else {
                    toaster.toast(directoryNotExistMessage)
                }
            },
        )
    }
    DanmakuCacheSettings(state)
}

@Composable
private fun SettingsScope.LocalVideoLibraryGroup(state: CacheDirectoryGroupState) {
    val settings by state.localVideoLibrarySettingsState
    val currentDirectory = settings.directory.orEmpty()
    val toaster = LocalToaster.current

    val chooseText = stringResource(Lang.settings_storage_local_video_library_choose)
    val pathInvalidText = stringResource(Lang.settings_storage_path_is_invalid)

    val directoryPicker = rememberDirectoryPickerLauncher(
        directory = currentDirectory.takeIf { it.isNotBlank() && File(it).isDirectory }?.let(::PlatformFile),
        dialogSettings = FileKitDialogSettings(
            title = chooseText,
        ),
    ) { file ->
        file?.let {
            state.localVideoLibrarySettingsState.update(
                settings.copy(directory = it.file.absolutePath),
            )
        }
    }

    Group(
        title = { Text(stringResource(Lang.settings_storage_local_video_library_title)) },
        description = { Text(stringResource(Lang.settings_storage_local_video_library_description)) },
    ) {
        TextFieldItem(
            value = currentDirectory,
            title = { Text(stringResource(Lang.settings_storage_local_video_library_path)) },
            placeholder = { Text(stringResource(Lang.settings_storage_local_video_library_not_set)) },
            textFieldDescription = {
                Text(stringResource(Lang.settings_storage_local_video_library_description))
            },
            onValueChangeCompleted = { value ->
                if (value.isBlank()) {
                    state.localVideoLibrarySettingsState.update(settings.copy(directory = null))
                    return@TextFieldItem
                }
                val directory = try {
                    File(value).canonicalFile
                } catch (e: Exception) {
                    toaster.toast("$pathInvalidText: ${e.message}")
                    return@TextFieldItem
                }
                if (!directory.exists() || !directory.isDirectory) {
                    toaster.toast(pathInvalidText)
                    return@TextFieldItem
                }
                state.localVideoLibrarySettingsState.update(
                    settings.copy(directory = directory.path),
                )
            },
        )
        RowButtonItem(
            onClick = { directoryPicker.launch() },
            title = { Text(chooseText) },
        )
        if (currentDirectory.isNotBlank()) {
            RowButtonItem(
                onClick = {
                    state.localVideoLibrarySettingsState.update(settings.copy(directory = null))
                },
                title = { Text(stringResource(Lang.settings_storage_local_video_library_clear)) },
            )
        }
    }
}
