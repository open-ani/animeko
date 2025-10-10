/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage

@Composable
fun SelectMediaStorageDialog(
    options: List<MediaCacheStorage>,
    onSelect: (MediaCacheStorage) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("选择储存位置") },
        icon = { Icon(Icons.Rounded.Save, null) },
        confirmButton = {
            TextButton(onDismissRequest) {
                Text("取消")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier)
                for (storage in options) {
                    Column(Modifier.padding()) {
                        // TODO: 很丑, 但反正只在 debug 用
                        OutlinedButton(
                            { onSelect(storage) },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(storage.cacheMediaSource.info.displayName) // TODO: 本地的全都叫 "本地" 无法区分 
                        }
                    }
                }
                Spacer(Modifier)
            }
        },
        modifier = modifier,
    )
}
