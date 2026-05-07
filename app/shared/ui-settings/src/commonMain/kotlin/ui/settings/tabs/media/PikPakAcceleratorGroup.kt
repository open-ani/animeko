/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.PikPakConfig
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_pikpak_description
import me.him188.ani.app.ui.lang.settings_pikpak_enabled
import me.him188.ani.app.ui.lang.settings_pikpak_password
import me.him188.ani.app.ui.lang.settings_pikpak_password_description
import me.him188.ani.app.ui.lang.settings_pikpak_password_hidden
import me.him188.ani.app.ui.lang.settings_pikpak_queue_description
import me.him188.ani.app.ui.lang.settings_pikpak_queue_title
import me.him188.ani.app.ui.lang.settings_pikpak_queue_unlimited
import me.him188.ani.app.ui.lang.settings_pikpak_recommend_apply
import me.him188.ani.app.ui.lang.settings_pikpak_recommend_dismiss
import me.him188.ani.app.ui.lang.settings_pikpak_recommend_message
import me.him188.ani.app.ui.lang.settings_pikpak_recommend_title
import me.him188.ani.app.ui.lang.settings_pikpak_test_connection
import me.him188.ani.app.ui.lang.settings_pikpak_username
import me.him188.ani.app.ui.lang.settings_pikpak_username_placeholder
import me.him188.ani.app.ui.settings.framework.ConnectionTester
import me.him188.ani.app.ui.settings.framework.ConnectionTesterResultIndicator
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SliderItem
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextFieldItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.datasources.api.source.MediaSourceKind
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScope.PikPakAcceleratorGroup(
    state: SettingsState<PikPakConfig>,
    mediaSelectorSettings: SettingsState<MediaSelectorSettings>,
    connectionTester: ConnectionTester,
) {
    val config by state
    var showRecommendDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Group(
        title = { Text("PikPak") },
        description = { Text(stringResource(Lang.settings_pikpak_description)) },
        useThinHeader = true,
    ) {
        SwitchItem(
            checked = config.enabled,
            onCheckedChange = { newValue ->
                val wasOff = !config.enabled
                state.update(config.copy(enabled = newValue))
                if (wasOff && newValue && !isSelectorAlignedForCloudOffline(mediaSelectorSettings.value)) {
                    showRecommendDialog = true
                }
            },
            title = { Text(stringResource(Lang.settings_pikpak_enabled)) },
        )

        AniAnimatedVisibility(visible = config.enabled) {
            Column {
                TextFieldItem(
                    value = config.username,
                    title = { Text(stringResource(Lang.settings_pikpak_username)) },
                    placeholder = { Text(stringResource(Lang.settings_pikpak_username_placeholder)) },
                    sanitizeValue = { it.trim() },
                    // Switching the account must invalidate the previously persisted
                    // refresh token and any plaintext password still in flight —
                    // otherwise the engine would happily sign into the old account
                    // using the old session because the credentials flow only checks
                    // "username non-empty && (password || refreshToken) non-empty".
                    onValueChangeCompleted = { newUsername ->
                        if (newUsername != config.username) {
                            state.update(
                                config.copy(
                                    username = newUsername,
                                    password = "",
                                    refreshToken = "",
                                ),
                            )
                        }
                    },
                )

                // The password persists in DataStore (see onSessionSaved TODO in
                // the platform modules), but the UI deliberately never echoes
                // the stored value back: the edit dialog opens empty every time
                // so neither shoulder-surfing nor the visibility toggle can
                // surface what's on disk. The collapsed row shows a masked
                // placeholder whenever a refresh token is live — that's the
                // "we're authenticated" signal — otherwise it renders empty.
                val hasLiveSession = config.refreshToken.isNotEmpty()
                TextFieldItem(
                    value = "",
                    title = { Text(stringResource(Lang.settings_pikpak_password)) },
                    description = { Text(stringResource(Lang.settings_pikpak_password_description)) },
                    exposedItem = {
                        Text(
                            if (hasLiveSession) stringResource(Lang.settings_pikpak_password_hidden)
                            else "",
                        )
                    },
                    sanitizeValue = { it },
                    visualTransformation = PasswordVisualTransformation(),
                    showVisibilityToggle = true,
                    // A non-empty confirm is treated as "user supplied a fresh
                    // password" and invalidates the stored refresh token so the
                    // next engine call does a full signin. An empty confirm is
                    // a no-op — we never let the UI clear a stored password,
                    // since the field is opened empty on every edit and an
                    // accidental confirm would otherwise destroy the only
                    // credential the engine has left.
                    onValueChangeCompleted = { newPassword ->
                        if (newPassword.isNotEmpty()) {
                            state.update(
                                config.copy(
                                    password = newPassword,
                                    refreshToken = "",
                                ),
                            )
                        }
                    },
                )

                val queueLength = config.slotQueueLength.coerceIn(1, PikPakConfig.SLOT_QUEUE_UNLIMITED)
                SliderItem(
                    value = queueLength.toFloat(),
                    onValueChange = { raw ->
                        val rounded = raw.toInt().coerceIn(1, PikPakConfig.SLOT_QUEUE_UNLIMITED)
                        if (rounded != config.slotQueueLength) {
                            state.update(config.copy(slotQueueLength = rounded))
                        }
                    },
                    title = { Text(stringResource(Lang.settings_pikpak_queue_title)) },
                    description = { Text(stringResource(Lang.settings_pikpak_queue_description)) },
                    valueRange = 1f..PikPakConfig.SLOT_QUEUE_UNLIMITED.toFloat(),
                    steps = PikPakConfig.SLOT_QUEUE_UNLIMITED - 2,
                    valueLabel = {
                        Text(
                            if (queueLength >= PikPakConfig.SLOT_QUEUE_UNLIMITED) stringResource(Lang.settings_pikpak_queue_unlimited)
                            else queueLength.toString(),
                        )
                    },
                )

                TextItem(
                    title = { Text(stringResource(Lang.settings_pikpak_test_connection)) },
                    action = {
                        ConnectionTesterResultIndicator(connectionTester, showTime = true)
                    },
                    onClick = {
                        scope.launch { connectionTester.test() }
                    },
                )
            }
        }
    }

    if (showRecommendDialog) {
        AlertDialog(
            onDismissRequest = { showRecommendDialog = false },
            title = { Text(stringResource(Lang.settings_pikpak_recommend_title)) },
            text = { Text(stringResource(Lang.settings_pikpak_recommend_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        mediaSelectorSettings.update(
                            mediaSelectorSettings.value.copy(preferKind = MediaSourceKind.BitTorrent),
                        )
                        showRecommendDialog = false
                    },
                ) {
                    Text(stringResource(Lang.settings_pikpak_recommend_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecommendDialog = false }) {
                    Text(stringResource(Lang.settings_pikpak_recommend_dismiss))
                }
            },
        )
    }
}

private fun isSelectorAlignedForCloudOffline(s: MediaSelectorSettings): Boolean =
    s.preferKind == MediaSourceKind.BitTorrent
