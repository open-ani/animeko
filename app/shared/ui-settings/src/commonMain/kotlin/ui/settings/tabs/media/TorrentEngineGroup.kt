/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.AnitorrentConfig.Companion.SHARE_RATIO_LIMIT_INFINITE
import me.him188.ani.app.data.models.preference.supportsLimitUploadOnMeteredNetwork
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_media_torrent_download_rate_limit
import me.him188.ani.app.ui.lang.settings_media_torrent_limit_upload_on_metered
import me.him188.ani.app.ui.lang.settings_media_torrent_limit_upload_on_metered_description
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_description
import me.him188.ani.app.ui.lang.settings_media_torrent_share_ratio_description
import me.him188.ani.app.ui.lang.settings_media_torrent_share_ratio_limit
import me.him188.ani.app.ui.lang.settings_media_torrent_sharing_description
import me.him188.ani.app.ui.lang.settings_media_torrent_sharing_settings
import me.him188.ani.app.ui.lang.settings_media_torrent_speed_format
import me.him188.ani.app.ui.lang.settings_media_torrent_title
import me.him188.ani.app.ui.lang.settings_media_torrent_unlimited
import me.him188.ani.app.ui.lang.settings_media_torrent_upload_rate_limit
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SliderItem
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.Unspecified
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.utils.platform.format1f
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScope.TorrentEngineGroup(
    torrentSettingsState: SettingsState<AnitorrentConfig>
) {
    Group({ Text(stringResource(Lang.settings_media_torrent_title)) }) {
        val torrentSettings by torrentSettingsState

        RateSliderItem(
            torrentSettings.downloadRateLimit,
            onValueChangeFinished = {
                torrentSettingsState.update(torrentSettings.copy(downloadRateLimit = it))
            },
            title = { Text(stringResource(Lang.settings_media_torrent_download_rate_limit)) },
        )

        Group(
            title = { Text(stringResource(Lang.settings_media_torrent_sharing_settings)) },
            description = { Text(stringResource(Lang.settings_media_torrent_sharing_description)) },
            useThinHeader = true,
        ) {
//            val allowUpload by remember {
//                derivedStateOf {
//                    torrentSettings.uploadRateLimit != FileSize.Zero
//                }
//            }
//            SwitchItem(
//                checked = allowUpload,
//                onCheckedChange = {
//                    vm.torrentSettings.update(
//                        torrentSettings.copy(
//                            uploadRateLimit = if (it) 1.megaBytes else FileSize.Zero,
//                        ),
//                    )
//                },
//                title = { Text("允许上传") },
//                description = if (!allowUpload) {
//                    {
//                        Text(
//                            "BT 网络依赖用户间分享，你所看的视频均来自其他用户的分享。" +
//                                    "除特殊情况外，建议允许上传，共同维护健康的 BT 分享环境。" +
//                                    "禁用上传会导致许多用户不再分享视频给你。",
//                            color = MaterialTheme.colorScheme.error,
//                        )
//                    }
//                } else null,
//            )
//
//            AnimatedVisibility(allowUpload) {
//            RateTextFieldItem(
//                torrentSettings.uploadRateLimit,
//                title = { Text("上传速度限制") },
//                onValueChangeCompleted = { vm.torrentSettings.update(torrentSettings.copy(uploadRateLimit = it)) },
//            )

            RateSliderItem(
                torrentSettings.uploadRateLimit,
                onValueChangeFinished = {
                    torrentSettingsState.update(torrentSettings.copy(uploadRateLimit = it))
                },
                title = { Text(stringResource(Lang.settings_media_torrent_upload_rate_limit)) },
            )
            var shareRatioLimit by remember {
                mutableStateOf(torrentSettings.shareRatioLimit)
            }
            SliderItem(
                shareRatioLimit,
                onValueChange = { shareRatioLimit = it },
                valueRange = 1f..10f,
                onValueChangeFinished = {
                    torrentSettingsState.update(torrentSettings.copy(shareRatioLimit = shareRatioLimit))
                },
                title = { Text(stringResource(Lang.settings_media_torrent_share_ratio_limit)) },
                description = { Text(stringResource(Lang.settings_media_torrent_share_ratio_description)) },
                valueLabel = {
                    Text(
                        if (shareRatioLimit == SHARE_RATIO_LIMIT_INFINITE) {
                            stringResource(Lang.settings_media_torrent_unlimited)
                        } else {
                            String.format1f(shareRatioLimit)
                        },
                    )
                },
            )
            if (LocalPlatform.current.supportsLimitUploadOnMeteredNetwork()) {
                SwitchItem(
                    checked = torrentSettings.limitUploadOnMeteredNetwork,
                    onCheckedChange = { torrentSettingsState.update(torrentSettings.copy(limitUploadOnMeteredNetwork = it)) },
                    title = { Text(stringResource(Lang.settings_media_torrent_limit_upload_on_metered)) },
                    description = { Text(stringResource(Lang.settings_media_torrent_limit_upload_on_metered_description)) },
                )
            }
        }
        val navigator by rememberUpdatedState(LocalNavigator.current)
        TextItem(
            title = { Text(stringResource(Lang.settings_media_torrent_peer_filter)) },
            description = { Text(stringResource(Lang.settings_media_torrent_peer_filter_description)) },
            action = {
                IconButton({ navigator.navigateTorrentPeerSettings() }) {
                    Icon(Icons.Rounded.ArrowOutward, null)
                }
            },
            onClick = { navigator.navigateTorrentPeerSettings() },
        )
    }
}

@Composable
private fun SettingsScope.RateSliderItem(
    value: FileSize,
    onValueChangeFinished: (value: FileSize) -> Unit,
    title: @Composable RowScope.() -> Unit,
    description: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var editingValue by remember(value) {
        mutableFloatStateOf(if (value == Unspecified) 10f else value.inMegaBytes)
    }
    SliderItem(
        if (editingValue == -1f) 10f else editingValue,
        onValueChange = { editingValue = it },
        title = title,
        description = description,
        valueRange = 1f..10f,
        steps = 0,
        onValueChangeFinished = {
            onValueChangeFinished(
                if (editingValue == 10f) Unspecified
                else editingValue.megaBytes,
            )
        },
        modifier = modifier,
        valueLabel = {
            Text(
                if (editingValue == 10f) stringResource(Lang.settings_media_torrent_unlimited)
                else stringResource(Lang.settings_media_torrent_speed_format, String.format1f(editingValue)),
            )
        },
    )
}

//@Composable
//private fun SettingsScope.RateTextFieldItem(
//    value: FileSize,
//    title: @Composable () -> Unit,
//    onValueChangeCompleted: (value: FileSize) -> Unit,
//    minValue: FileSize = FileSize.Zero,
//) {
//    TextFieldItem(
//        value.inKiloBytes.toString(),
//        title = title,
//        onValueChangeCompleted = { onValueChangeCompleted(it.toDoubleOrNull()?.kiloBytes ?: Unspecified) },
//        isErrorProvider = {
//            val double = it.toDoubleOrNull()
//            double == null || double.kiloBytes.inBytes < minValue.inBytes
//        },
//        sanitizeValue = { it.trim() },
//        textFieldDescription = { Text("单位为 KB/s，最低 1024 KB/s，-1 表示无限制\n\n当前设置: ${renderRateValue(it)}") },
//        exposedItem = { text ->
//            Text(renderRateValue(text))
//        },
//    )
//}
