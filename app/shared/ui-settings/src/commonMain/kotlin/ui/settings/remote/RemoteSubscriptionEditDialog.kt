/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.remote

import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_media_source_cancel
import me.him188.ani.app.ui.lang.settings_media_source_enable
import me.him188.ani.app.ui.lang.settings_media_source_save_button
import me.him188.ani.app.ui.lang.settings_media_source_subscription_edit
import me.him188.ani.app.ui.lang.settings_media_source_subscription_period
import me.him188.ani.app.ui.lang.settings_media_source_subscription_url
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.minutes
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription

@Composable
internal fun RemoteSubscriptionEditDialog(
    initial: MediaSourceSubscription,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (MediaSourceSubscription) -> Unit,
) {
    var url by remember { mutableStateOf(initial.url) }
    var period by remember { mutableStateOf(initial.updatePeriod.inWholeMinutes.toString()) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Lang.settings_media_source_subscription_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    url,
                    { url = it },
                    label = { Text(stringResource(Lang.settings_media_source_subscription_url)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(period, { period = it }, label = { Text(stringResource(Lang.settings_media_source_subscription_period)) })
                Row {
                    Text(stringResource(Lang.settings_media_source_enable), Modifier.weight(1f))
                    Switch(enabled, { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                {
                    onSave(
                        initial.copy(
                            url = url.trim(),
                            updatePeriod = period.toLong().minutes,
                            enabled = enabled,
                        )
                    )
                },
                enabled = !busy && url.isNotBlank() && (period.toLongOrNull() ?: 0) >= 1,
            ) {
                Text(stringResource(Lang.settings_media_source_save_button))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(Lang.settings_media_source_cancel)) } },
    )
}
