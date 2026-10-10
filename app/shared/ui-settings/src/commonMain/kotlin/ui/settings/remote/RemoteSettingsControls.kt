/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.ui.settings.remote

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.him188.ani.app.data.models.preference.DanmakuCacheStrategy
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.remote_settings_restore_warning
import me.him188.ani.app.ui.lang.remote_settings_scan
import me.him188.ani.app.ui.lang.settings_media_source_disable
import me.him188.ani.app.ui.lang.settings_media_source_edit
import me.him188.ani.app.ui.lang.settings_media_source_enable
import me.him188.ani.app.ui.lang.settings_mediasource_refresh
import me.him188.ani.app.ui.lang.settings_player_exoplayer_preinit_effect_graph
import me.him188.ani.app.ui.lang.settings_player_exoplayer_preinit_effect_graph_desc
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.tabs.app.PlayerGroup
import me.him188.ani.app.ui.settings.tabs.media.BackupSettings
import me.him188.ani.app.ui.settings.tabs.media.DanmakuCacheSettings
import me.him188.ani.app.ui.settings.tabs.media.MediaSelectionGroup
import me.him188.ani.datasources.api.source.MediaSourceKind
import org.jetbrains.compose.resources.stringResource

@Composable
fun RemoteSettingsScanButton(onClick: () -> Unit) {
    IconButton(onClick) {
        Icon(Icons.Outlined.QrCodeScanner, stringResource(Lang.remote_settings_scan))
    }
}

/** TV 支持的设置项在远程入口组合，共享 Group 保持当前设备的默认行为。 */
@Composable
internal fun SettingsScope.RemotePlayerGroup(form: RemoteSettingsFormState) {
    PlayerGroup(
        form.video,
        form.kernel,
        form.filter,
        form.regexFilters,
        showDebug = false,
        showFullscreenOnLandscape = false,
        showAudioTimeStretch = true,
        showBackgroundBehavior = false,
        platformSettings = {
            HorizontalDividerItem()
            SwitchItem(
                checked = form.kernel.value.exoPlayerInitEffectGraphInAdvance,
                onCheckedChange = {
                    form.kernel.update(form.kernel.value.copy(exoPlayerInitEffectGraphInAdvance = it))
                },
                title = { Text(stringResource(Lang.settings_player_exoplayer_preinit_effect_graph)) },
                description = { Text(stringResource(Lang.settings_player_exoplayer_preinit_effect_graph_desc)) },
            )
        },
    )
}

@Composable
internal fun SettingsScope.RemoteMediaSelectionGroup(form: RemoteSettingsFormState) {
    MediaSelectionGroup(
        form.selection,
        preferredKinds = listOf(MediaSourceKind.WEB),
        showImageCaptchaAutoSolve = true,
    )
}

@Composable
internal fun SettingsScope.RemoteStorageGroup(form: RemoteSettingsFormState) {
    DanmakuCacheSettings(
        form.storage,
        strategies = listOf(
            DanmakuCacheStrategy.DON_NOT_CACHE,
            DanmakuCacheStrategy.CACHE_ON_COLLECTION_DOING_MEDIA_PLAY,
        ),
    )
}

@Composable
internal fun SettingsScope.RemoteBackupGroup(form: RemoteSettingsFormState) {
    BackupSettings(form::exportBackup, form::restoreBackup, Lang.remote_settings_restore_warning)
}

@Composable
internal fun ColumnScope.RemoteSubscriptionActions(
    vm: RemoteSettingsViewModel,
    subscription: MediaSourceSubscription,
    onDismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(Lang.settings_media_source_edit)) },
        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
        onClick = {
            onDismiss()
            vm.editSubscription(subscription)
        },
    )
    DropdownMenuItem(
        text = { Text(stringResource(Lang.settings_mediasource_refresh)) },
        leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
        onClick = {
            onDismiss()
            vm.refreshSubscription(subscription)
        },
        enabled = subscription.enabled,
    )
    DropdownMenuItem(
        text = {
            Text(stringResource(if (subscription.enabled) Lang.settings_media_source_disable else Lang.settings_media_source_enable))
        },
        leadingIcon = {
            Icon(if (subscription.enabled) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
        },
        onClick = {
            onDismiss()
            vm.toggleSubscription(subscription)
        },
    )
}
