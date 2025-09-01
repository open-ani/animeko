/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun BangumiFullSyncStateDialog(
    state: BangumiSyncState?,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit = {
        TextButton(onDismissRequest) {
            Text("关闭")
        }
    },
) {
    AlertDialog(
        title = { Text("正在同步 Bangumi 收藏") },
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
                Text("此操作可能需要数分钟时间，请耐心等待")
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}

private fun renderBangumiSyncState(state: BangumiSyncState?): String {
    return when (state) {
        null -> "准备中"
        BangumiSyncState.Started -> "正在开始"
        is BangumiSyncState.Fetched -> "已获取 ${state.fetchedCount} 条收藏条目"
        is BangumiSyncState.Saved -> "已存储 ${state.savedCount} 条收藏条目"
        BangumiSyncState.SyncingTimeline -> "正在同步时间线"
        BangumiSyncState.Finished -> "同步完成"
    }
}

@Composable
@Preview
private fun PreviewBangumiFullSyncDialogSaved() {
    ProvideCompositionLocalsForPreview {
        BangumiFullSyncStateDialog(
            state = BangumiSyncState.Saved(123),
            onDismissRequest = {},
        )
    }
}


@Composable
@Preview
private fun PreviewBangumiFullSyncDialogSyncTimeline() {
    ProvideCompositionLocalsForPreview {
        BangumiFullSyncStateDialog(
            state = BangumiSyncState.SyncingTimeline,
            onDismissRequest = {},
        )
    }
}