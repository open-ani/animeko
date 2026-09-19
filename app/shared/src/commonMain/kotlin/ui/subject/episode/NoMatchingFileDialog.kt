/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_no_matching_file_dialog_cancel
import me.him188.ani.app.ui.lang.subject_episode_no_matching_file_dialog_description
import me.him188.ani.app.ui.lang.subject_episode_no_matching_file_dialog_title
import org.jetbrains.compose.resources.stringResource

/**
 * 自动匹配不到本集的文件时, 把资源里的文件清单摆出来让用户挑一个.
 *
 * @param files 每一项是种子内的相对路径 (`pathInTorrent`)
 */
@Composable
fun NoMatchingFileDialog(
    files: List<String>,
    onSelect: (pathInTorrent: String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = { Text(stringResource(Lang.subject_episode_no_matching_file_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(Lang.subject_episode_no_matching_file_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyColumn(Modifier.padding(top = 12.dp)) {
                    items(files) { path ->
                        Text(
                            path,
                            Modifier.fillMaxWidth()
                                .clickable { onSelect(path) }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onDismissRequest) {
                Text(stringResource(Lang.subject_episode_no_matching_file_dialog_cancel))
            }
        },
    )
}
