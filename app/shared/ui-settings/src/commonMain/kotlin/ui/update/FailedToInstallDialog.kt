/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_update_manual_install_cancel
import me.him188.ani.app.ui.lang.settings_update_manual_install_open_failed
import me.him188.ani.app.ui.lang.settings_update_manual_install_package_not_found
import me.him188.ani.app.ui.lang.settings_update_manual_install_title
import me.him188.ani.app.ui.lang.settings_update_manual_install_view_package
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

@Composable
fun FailedToInstallDialog(
    message: String,
    onDismissRequest: () -> Unit,
    state: AppUpdateState,
) {
    FailedToInstallDialog(
        message,
        onDismissRequest,
        file = (state as? AppUpdateState.Downloaded)?.file,
    )
}

/**
 * 自动安装失败的提示. [file] 为安装失败的安装包, 用户可以打开它手动安装; 为 `null` 表示安装包已不存在.
 */
@Composable
fun FailedToInstallDialog(
    message: String,
    onDismissRequest: () -> Unit,
    file: SystemPath?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    AlertDialog(
        onDismissRequest,
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        if (file == null) {
                            toaster.toast(getString(Lang.settings_update_manual_install_package_not_found))
                            return@launch
                        }
                        val success =
                            KoinPlatform.getKoin().get<UpdateInstaller>().openForManualInstallation(file, context)

                        if (!success) {
                            toaster.toast(
                                getString(
                                    Lang.settings_update_manual_install_open_failed,
                                    file.absolutePath,
                                ),
                            )
                        }
                    }
                },
            ) { Text(stringResource(Lang.settings_update_manual_install_view_package)) }
        },
        dismissButton = {
            TextButton(
                onDismissRequest,
                modifier = Modifier.testTag(FailedToInstallDialogTestTags.DISMISS_BUTTON),
            ) { Text(stringResource(Lang.settings_update_manual_install_cancel)) }
        },
        title = { Text(stringResource(Lang.settings_update_manual_install_title)) },
        text = { Text(message) },
    )
}

object FailedToInstallDialogTestTags {
    const val DISMISS_BUTTON = "failed_to_install_dialog_dismiss"
}
