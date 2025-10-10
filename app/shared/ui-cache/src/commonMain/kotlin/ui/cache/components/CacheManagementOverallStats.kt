/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.datasources.api.topic.FileSize


@Composable
fun CacheManagementOverallStats(
    stats: () -> MediaStats,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Stat(
            title = {
                Icon(Icons.Rounded.Upload, null)
                Text("总上传", style = MaterialTheme.typography.titleMedium)
            },
            speedText = {
                Text(renderSpeed(remember(stats) { derivedStateOf { stats().uploadSpeed } }.value))
            },
            totalText = {
                Text(renderFileSize(remember(stats) { derivedStateOf { stats().uploaded } }.value))
            },
        )

        Stat(
            title = {
                Icon(Icons.Rounded.Download, null)
                Text("总下载", style = MaterialTheme.typography.titleMedium)
            },
            speedText = {
                Text(renderSpeed(remember(stats) { derivedStateOf { stats().downloadSpeed } }.value))
            },
            totalText = {
                Text(renderFileSize(remember(stats) { derivedStateOf { stats().downloaded } }.value))
            },
        )
    }
}

@Composable
private fun Stat(
    title: @Composable () -> Unit,
    speedText: @Composable () -> Unit,
    totalText: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            title()
        }

        Row(
            Modifier.weight(1f).padding(start = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(textAlign = TextAlign.Center)) {
                Row(
                    Modifier.widthIn(min = 100.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Speed, null)
                    speedText()
                }
                Row(
                    Modifier.widthIn(min = 100.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.DownloadDone, null)
                    totalText()
                }
            }
        }
    }
}


@Stable
private fun renderFileSize(size: FileSize): String {
    if (size == FileSize.Unspecified) {
        return ""
    }
    return "$size"
}

@Stable
private fun renderSpeed(speed: FileSize): String {
    if (speed == FileSize.Unspecified) {
        return ""
    }
    return "$speed/s"
}
