/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.settings_update_check
import me.him188.ani.app.ui.lang.settings_update_check_failed
import me.him188.ani.app.ui.lang.settings_update_checking
import me.him188.ani.app.ui.lang.settings_update_new_version
import me.him188.ani.app.ui.lang.settings_update_popup_close
import me.him188.ani.app.ui.lang.settings_update_popup_download_complete
import me.him188.ani.app.ui.lang.settings_update_popup_installing
import me.him188.ani.app.ui.lang.settings_update_popup_new_version
import me.him188.ani.app.ui.lang.settings_update_up_to_date
import me.him188.ani.app.ui.lang.settings_update_version_expired_title
import me.him188.ani.app.ui.lang.tv_update_cancel_download
import me.him188.ani.app.ui.lang.tv_update_download
import me.him188.ani.app.ui.lang.tv_update_download_failed
import me.him188.ani.app.ui.lang.tv_update_downloading
import me.him188.ani.app.ui.lang.tv_update_expired_message
import me.him188.ani.app.ui.lang.tv_update_expired_message_with_latest
import me.him188.ani.app.ui.lang.tv_update_install
import me.him188.ani.app.ui.lang.tv_update_install_hint
import me.him188.ani.app.ui.lang.tv_update_later
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.layout.TvModalOverlay
import me.him188.ani.tv.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvOptionModal
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private enum class TvUpdateFocus : TvFocusKey { Primary }

private class TvUpdateAction(val id: String, val title: String, val intent: TvUpdateIntent)

@Composable
fun TvUpdateDialog(
    state: TvUpdateUiState,
    onIntent: (TvUpdateIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvUpdateFocus.Primary)
    LaunchedEffect(state.phase) { focus.request(TvUpdateFocus.Primary) }

    val status = when (state.phase) {
        TvUpdatePhase.Checking -> stringResource(Lang.settings_update_checking)
        TvUpdatePhase.CheckFailed -> stringResource(Lang.settings_update_check_failed)
        TvUpdatePhase.UpToDate -> stringResource(Lang.settings_update_up_to_date)
        TvUpdatePhase.Available -> stringResource(Lang.settings_update_new_version, state.newVersion.orEmpty())
        TvUpdatePhase.Downloading ->
            stringResource(Lang.tv_update_downloading, "${(state.downloadProgress * 100).roundToInt()}%")
        TvUpdatePhase.DownloadFailed -> stringResource(Lang.tv_update_download_failed)
        TvUpdatePhase.Downloaded -> stringResource(Lang.settings_update_popup_download_complete)
        TvUpdatePhase.Installing -> stringResource(Lang.settings_update_popup_installing)
    }
    val title = when {
        state.expired -> stringResource(Lang.settings_update_version_expired_title)
        state.newVersion != null -> stringResource(Lang.settings_update_popup_new_version)
        else -> stringResource(Lang.settings_update_check)
    }
    val expiredMessage = state.expiredLatestVersion?.let { stringResource(Lang.tv_update_expired_message_with_latest, it) }
        ?: stringResource(Lang.tv_update_expired_message)

    val context = LocalContext.current
    val retry = stringResource(Lang.settings_mediasource_retry)
    val dismiss = TvUpdateAction(
        "dismiss",
        stringResource(if (state.newVersion != null) Lang.tv_update_later else Lang.settings_update_popup_close),
        TvUpdateIntent.Dismiss,
    )
    val primary = when (state.phase) {
        TvUpdatePhase.Checking, TvUpdatePhase.Installing -> null
        TvUpdatePhase.CheckFailed -> TvUpdateAction("retry-check", retry, TvUpdateIntent.Check)
        TvUpdatePhase.UpToDate -> if (state.expired) TvUpdateAction("retry-check", retry, TvUpdateIntent.Check) else null
        TvUpdatePhase.Available ->
            TvUpdateAction("download", stringResource(Lang.tv_update_download), TvUpdateIntent.Download)
        TvUpdatePhase.Downloading ->
            TvUpdateAction("cancel-download", stringResource(Lang.tv_update_cancel_download), TvUpdateIntent.CancelDownload)
        TvUpdatePhase.DownloadFailed -> TvUpdateAction("retry-download", retry, TvUpdateIntent.Download)
        TvUpdatePhase.Downloaded ->
            TvUpdateAction("install", stringResource(Lang.tv_update_install), TvUpdateIntent.Install(context))
    }
    val actions = listOfNotNull(primary, dismiss.takeUnless { state.expired })

    TvModalOverlay(
        onClose = { if (!state.expired) onIntent(TvUpdateIntent.Dismiss) },
        modifier = modifier.tvFocusNavSignal(focus),
        background = {},
    ) {
        TvOptionModal(
            title = title,
            subtitle = if (state.expired) expiredMessage else status,
            modifier = Modifier.testTag("tv-update-dialog"),
            width = 560.dp,
            footer = if (actions.isEmpty()) null else {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions.forEachIndexed { index, action ->
                            TvOptionRow(
                                title = action.title,
                                modifier = Modifier.weight(1f).testTag("tv-update-${action.id}").then(
                                    if (index == 0) Modifier.tvFocusAnchor(focus, TvUpdateFocus.Primary) else Modifier,
                                ),
                                onClick = { onIntent(action.intent) },
                            )
                        }
                    }
                }
            },
        ) {
            if (state.expired) {
                Text(status, style = MaterialTheme.typography.bodyMedium, color = TvOptionDefaults.Content)
            }
            if (state.phase != TvUpdatePhase.UpToDate) {
                state.changes.forEach { change ->
                    Text("• $change", style = MaterialTheme.typography.bodyMedium, color = TvOptionDefaults.Content, maxLines = 2)
                }
            }
            if (state.phase == TvUpdatePhase.Downloaded) {
                Text(
                    stringResource(Lang.tv_update_install_hint),
                    style = MaterialTheme.typography.bodySmall, color = TvOptionDefaults.Muted,
                )
            }
        }
    }
}
