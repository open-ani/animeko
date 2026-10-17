/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.io.files.Path
import me.him188.ani.app.domain.media.LocalFileMedia
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_select_local_file
import me.him188.ani.utils.io.inSystem
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
actual fun rememberLocalVideoFilePicker(
    onPicked: (LocalVideoFile) -> Unit,
    initialDirectory: String?,
): (() -> Unit)? {
    val onPickedUpdated by rememberUpdatedState(onPicked)
    val title = stringResource(Lang.media_selector_select_local_file)
    val directory = initialDirectory
        ?.takeIf { it.isNotBlank() && File(it).isDirectory }
        ?.let(::PlatformFile)
    val launcher = rememberFilePickerLauncher(
        type = FileKitType.File(LocalFileMedia.VIDEO_EXTENSIONS),
        directory = directory,
        dialogSettings = FileKitDialogSettings(
            title = title,
            parentWindow = LocalPlatformWindow.current.windowScope?.window,
        ),
    ) { file ->
        file?.let { onPickedUpdated(LocalVideoFile.File(Path(it.absolutePath()).inSystem)) }
    }
    return launcher::launch
}
