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
                Text("此操作可能需要数分钟时间，请耐心等待。在同步过程中，其他功能不可使用")
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onDismissRequest) {
                Text("在后台继续")
            }
        },
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}

private fun renderBangumiSyncState(state: BangumiSyncState?): String {
    return when (state) {
        null -> "准备中"
        BangumiSyncState.Preparing -> "正在获取元数据"
        is BangumiSyncState.FetchingSubjects -> "(已完成 ${state.fetchedCount} 条) 正在获取更多收藏列表"
        is BangumiSyncState.FetchingEpisodes -> "(已完成 ${state.fetchedCount} 条) 正在获取观看进度"
        is BangumiSyncState.Inserting -> "(已完成 ${state.savedCount} 条) 正在保存"
        is BangumiSyncState.Finishing -> "(已完成 ${state.savedCount} 条) 正在完成"
        is BangumiSyncState.Finished -> {
            if (state.error != null) {
                "(已完成 ${state.savedCount} 条) 同步失败, 错误信息如下: \n$state"
            } else {
                "(已完成 ${state.savedCount} 条) 同步成功"
            }
        }

        BangumiSyncState.Unsupported -> "进行中"
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