/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_account_profile_delete_account
import me.him188.ani.app.ui.lang.settings_account_profile_delete_account_confirm
import me.him188.ani.app.ui.lang.settings_account_profile_delete_account_input_label
import me.him188.ani.app.ui.lang.settings_account_profile_delete_account_warning
import me.him188.ani.app.ui.lang.settings_account_profile_deleting_account
import me.him188.ani.app.ui.lang.subject_collection_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * 用户需要输入的确认文字. 不随语言变化.
 */
internal const val DELETE_ACCOUNT_CONFIRMATION_TEXT = "yes"

/**
 * 注销账号的确认弹窗. 告知用户注销会永久删除全部数据且不可撤回, 输入 [DELETE_ACCOUNT_CONFIRMATION_TEXT] 后才能确认.
 *
 * @param isDeleting 正在注销. 期间弹窗不能关闭, 用户等待注销完成
 */
@Composable
internal fun DeleteAccountDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    // 手机键盘常会自动把首字母大写
    val confirmed = input.trim().equals(DELETE_ACCOUNT_CONFIRMATION_TEXT, ignoreCase = true)

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onCancel() },
        icon = { Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Lang.settings_account_profile_delete_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Lang.settings_account_profile_delete_account_warning))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = {
                        Text(
                            stringResource(
                                Lang.settings_account_profile_delete_account_input_label,
                                DELETE_ACCOUNT_CONFIRMATION_TEXT,
                            ),
                        )
                    },
                    enabled = !isDeleting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("deleteAccountInput"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onConfirm,
                enabled = confirmed && !isDeleting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("deleteAccountConfirm"),
            ) {
                if (isDeleting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("deleteAccountProgress"),
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(Lang.settings_account_profile_deleting_account))
                    }
                } else {
                    Text(stringResource(Lang.settings_account_profile_delete_account_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onCancel, enabled = !isDeleting, modifier = Modifier.testTag("deleteAccountCancel")) {
                Text(stringResource(Lang.subject_collection_cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = !isDeleting, dismissOnClickOutside = !isDeleting),
    )
}

@Preview
@Composable
private fun PreviewDeleteAccountDialog() {
    ProvideCompositionLocalsForPreview {
        DeleteAccountDialog(isDeleting = false, onConfirm = {}, onCancel = {})
    }
}

@Preview
@Composable
private fun PreviewDeleteAccountDialogDeleting() {
    ProvideCompositionLocalsForPreview {
        DeleteAccountDialog(isDeleting = true, onConfirm = {}, onCancel = {})
    }
}
