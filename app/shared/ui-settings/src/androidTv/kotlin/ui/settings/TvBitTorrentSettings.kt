/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.compose.runtime.Composable
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_media_source_bt
import me.him188.ani.app.ui.lang.settings_media_source_web
import me.him188.ani.app.ui.lang.settings_media_torrent_download_rate_limit
import me.him188.ani.app.ui.lang.settings_media_torrent_extra_trackers
import me.him188.ani.app.ui.lang.settings_media_torrent_extra_trackers_description
import me.him188.ani.app.ui.lang.settings_media_torrent_extra_trackers_dialog_description
import me.him188.ani.app.ui.lang.settings_media_torrent_limit_upload_on_metered
import me.him188.ani.app.ui.lang.settings_media_torrent_limit_upload_on_metered_description
import me.him188.ani.app.ui.lang.settings_media_torrent_share_ratio_description
import me.him188.ani.app.ui.lang.settings_media_torrent_share_ratio_limit
import me.him188.ani.app.ui.lang.settings_media_torrent_unlimited
import me.him188.ani.app.ui.lang.settings_media_torrent_upload_rate_limit
import me.him188.ani.app.ui.lang.tv_downloads_preferred_source
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvSettingsItems.bitTorrent() {
    val config = state.torrent
    val unlimited = stringResource(Lang.settings_media_torrent_unlimited)
    val rates = listOf(.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0).map { it.megaBytes } + FileSize.Unspecified
    fun rateLabel(rate: FileSize) = if (rate.isUnspecified) unlimited else "$rate/s"
    choice(
        "torrent-download", stringResource(Lang.settings_media_torrent_download_rate_limit),
        config.downloadRateLimit, rates, rates.map(::rateLabel),
    ) { value -> onIntent(TvSettingsIntent.Torrent { copy(downloadRateLimit = value) }) }
    choice(
        "torrent-upload", stringResource(Lang.settings_media_torrent_upload_rate_limit),
        config.uploadRateLimit, rates, rates.map(::rateLabel),
    ) { value -> onIntent(TvSettingsIntent.Torrent { copy(uploadRateLimit = value) }) }
    val ratios = (1..10).map(Int::toFloat)
    choice(
        "torrent-ratio", stringResource(Lang.settings_media_torrent_share_ratio_limit),
        config.shareRatioLimit, ratios,
        ratios.map { if (it == AnitorrentConfig.SHARE_RATIO_LIMIT_INFINITE) unlimited else it.toString() },
        description = stringResource(Lang.settings_media_torrent_share_ratio_description),
    ) { value -> onIntent(TvSettingsIntent.Torrent { copy(shareRatioLimit = value) }) }
    toggle(
        "torrent-metered", stringResource(Lang.settings_media_torrent_limit_upload_on_metered),
        config.limitUploadOnMeteredNetwork,
        stringResource(Lang.settings_media_torrent_limit_upload_on_metered_description),
    ) { value -> onIntent(TvSettingsIntent.Torrent { copy(limitUploadOnMeteredNetwork = value) }) }
    val title = stringResource(Lang.settings_media_torrent_extra_trackers)
    val description = stringResource(Lang.settings_media_torrent_extra_trackers_dialog_description)
    action("torrent-trackers", title, description = stringResource(Lang.settings_media_torrent_extra_trackers_description)) {
        open(TvSettingsDialog.Input("torrent-trackers", title, config.extraTrackers, description, multiline = true) { value ->
            onIntent(TvSettingsIntent.Torrent { copy(extraTrackers = value.trim()) })
        })
    }
    choice(
        "torrent-prefer", stringResource(Lang.tv_downloads_preferred_source), state.selector.preferKind,
        listOf(MediaSourceKind.WEB, MediaSourceKind.BitTorrent),
        listOf(stringResource(Lang.settings_media_source_web), stringResource(Lang.settings_media_source_bt)),
    ) { value -> onIntent(TvSettingsIntent.Selector { copy(preferKind = value) }) }
}
