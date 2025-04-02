/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.RichDialogLayout
import me.him188.ani.utils.io.absolutePath
import org.koin.mp.KoinPlatform

@Composable
fun FailedToInstallDialog(
    message: String,
    onDismissRequest: () -> Unit,
    logoState: () -> UpdateLogoState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    BasicAlertDialog(onDismissRequest) {
        RichDialogLayout(
            title = { Text("自动安装失败") },
            description = { Text(message) },
            buttons = {
                TextButton(onDismissRequest) { Text("取消更新") }
                Button(
                    onClick = {
                        scope.launch {
                            val file = (logoState() as? UpdateLogoState.Downloaded)?.file
                            if (file == null) {
                                toaster.toast("未找到安装包")
                                return@launch
                            }
                            val success =
                                KoinPlatform.getKoin().get<UpdateInstaller>().openForManualInstallation(file, context)

                            if (!success) {
                                toaster.toast("打开文件失败，请手动安装 ${file.absolutePath}")
                            }
                        }
                    },
                ) { Text("查看安装包") }
            },
        ) {
            Text("自动安装失败, 请手动安装")
        }
    }
}
