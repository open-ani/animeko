/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.ui.download.components.DownloadItem
import me.him188.ani.app.ui.download.components.DownloadStatus
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_episode_pause_download
import me.him188.ani.app.ui.lang.cache_episode_resume_download
import me.him188.ani.app.ui.lang.cache_management_invalid_cache_info
import me.him188.ani.app.ui.lang.cache_management_more_actions
import me.him188.ani.app.ui.lang.cache_management_play
import me.him188.ani.app.ui.lang.cache_management_streaming_not_supported
import me.him188.ani.app.ui.lang.cache_subject_cancel
import me.him188.ani.app.ui.lang.cache_subject_delete
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.layout.TvModalOverlay
import me.him188.ani.tv.ui.foundation.widgets.TvOptionModal
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

/** Cancel is the stable entry, including while asynchronously loaded results change. */
@Composable
internal fun TvDownloadModal(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    key(title) {
        val focus = rememberTvFocusScope()
        val cancel = TvFocusKey("download-modal-cancel")
        focus.Resolver()
        focus.InitialFocus(cancel)
        TvModalOverlay(onClose, Modifier.tvFocusNavSignal(focus), background = {}) {
            TvOptionModal(title, Modifier.testTag("tv-download-modal")) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvOptionRow(stringResource(Lang.cache_subject_cancel),
                        modifier = Modifier.tvFocusAnchor(focus, cancel).testTag("tv-download-modal-cancel"), onClick = onClose)
                    content()
                }
            }
        }
    }
}

@Composable
internal fun TvDownloadActionsModal(
    items: List<DownloadItem>,
    onClose: () -> Unit,
    onAction: (TvDownloadIntent) -> Unit,
) {
    val available = items.filterNot { it.isBusy }
    TvDownloadModal(stringResource(Lang.cache_management_more_actions), onClose) {
        items.singleOrNull()?.let { item ->
            Text(downloadDescription(item), style = MaterialTheme.typography.bodyMedium)
            TvOptionRow(
                stringResource(Lang.cache_management_play),
                enabled = !item.isBusy && item.playability == DownloadItem.Playability.PLAYABLE,
                modifier = Modifier.testTag("tv-download-play"),
                supportingText = when (item.playability) {
                    DownloadItem.Playability.STREAMING_NOT_SUPPORTED -> stringResource(Lang.cache_management_streaming_not_supported)
                    DownloadItem.Playability.INVALID_SUBJECT_EPISODE_ID -> stringResource(Lang.cache_management_invalid_cache_info)
                    DownloadItem.Playability.PLAYABLE -> null
                },
            ) { onAction(TvDownloadIntent.Play(item)) }
        }
        val pause = available.filter { it.status == DownloadStatus.IN_PROGRESS }.mapTo(mutableSetOf()) { it.id }
        val resume = available.filter { it.isPaused }.mapTo(mutableSetOf()) { it.id }
        TvOptionRow(stringResource(Lang.cache_episode_pause_download), enabled = pause.isNotEmpty(),
            modifier = Modifier.testTag("tv-download-pause")) { onAction(TvDownloadIntent.Pause(pause)) }
        TvOptionRow(stringResource(Lang.cache_episode_resume_download), enabled = resume.isNotEmpty(),
            modifier = Modifier.testTag("tv-download-resume")) { onAction(TvDownloadIntent.Resume(resume)) }
        TvOptionRow(stringResource(Lang.cache_subject_delete), enabled = available.isNotEmpty(),
            modifier = Modifier.testTag("tv-download-delete")) {
            onAction(TvDownloadIntent.Delete(available.mapTo(mutableSetOf()) { it.id }))
        }
    }
}
