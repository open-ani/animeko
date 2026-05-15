/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_bangumi_sync_continue_background
import me.him188.ani.app.ui.lang.foundation_bangumi_sync_description
import me.him188.ani.app.ui.lang.foundation_bangumi_sync_failed
import me.him188.ani.app.ui.lang.foundation_bangumi_sync_fetching_episodes
import me.him188.ani.app.ui.lang.foundation_bangumi_sync_fetching_metadata
import me.him188.ani.app.ui.lang.foundation_bangumi_sync_fetching_subjects
import me.him188.ani.app.ui.lang.foundation_bangumi_sync_finishing
import me.him188.ani.app.ui.lang.foundation_bangumi_sync_in_progress
import me.him188.ani.app.ui.lang.foundation_bangumi_sync_inserting
import me.him188.ani.app.ui.lang.foundation_bangumi_sync_preparing
import me.him188.ani.app.ui.lang.foundation_bangumi_sync_success
import me.him188.ani.app.ui.lang.foundation_bangumi_sync_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun BangumiFullSyncStateDialog(
    state: BangumiSyncState?,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = { Text(stringResource(Lang.foundation_bangumi_sync_title)) },
        text = {
            Column {
                Text(renderBangumiSyncState(state))
                Spacer(modifier = Modifier.height(24.dp))
                if (state?.finished == false) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator({ 1f }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(Lang.foundation_bangumi_sync_description))
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onDismissRequest) {
                Text(stringResource(Lang.foundation_bangumi_sync_continue_background))
            }
        },
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}

@Composable
private fun renderBangumiSyncState(state: BangumiSyncState?): String {
    return when (state) {
        null -> stringResource(Lang.foundation_bangumi_sync_preparing)
        BangumiSyncState.Preparing -> stringResource(Lang.foundation_bangumi_sync_fetching_metadata)
        is BangumiSyncState.FetchingSubjects -> stringResource(
            Lang.foundation_bangumi_sync_fetching_subjects,
            state.fetchedCount,
        )

        is BangumiSyncState.FetchingEpisodes -> stringResource(
            Lang.foundation_bangumi_sync_fetching_episodes,
            state.fetchedCount,
        )

        is BangumiSyncState.Inserting -> stringResource(
            Lang.foundation_bangumi_sync_inserting,
            state.savedCount,
        )

        is BangumiSyncState.Finishing -> stringResource(
            Lang.foundation_bangumi_sync_finishing,
            state.savedCount,
        )
        is BangumiSyncState.Finished -> {
            if (state.error != null) {
                stringResource(
                    Lang.foundation_bangumi_sync_failed,
                    state.savedCount,
                    state.toString(),
                )
            } else {
                stringResource(Lang.foundation_bangumi_sync_success, state.savedCount)
            }
        }

        BangumiSyncState.Unsupported -> stringResource(Lang.foundation_bangumi_sync_in_progress)
    }
}

@Composable
@Preview
private fun PreviewBangumiFullSyncDialogSaved() {
    ProvideCompositionLocalsForPreview {
        BangumiFullSyncStateDialog(
            state = BangumiSyncState.Inserting(123),
            onDismissRequest = {},
        )
    }
}


@Composable
@Preview
private fun PreviewBangumiFullSyncDialogSyncTimeline() {
    ProvideCompositionLocalsForPreview {
        BangumiFullSyncStateDialog(
            state = BangumiSyncState.Finishing(100),
            onDismissRequest = {},
        )
    }
}
