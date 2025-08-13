/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.episode.DanmakuFetchResultWithConfig
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuProviderId

@Composable
fun DesktopDanmakuSourceSection(
    fetchResults: List<DanmakuFetchResultWithConfig>,
    onSetEnabled: (DanmakuServiceId, Boolean) -> Unit,
    onManualMatch: (DanmakuProviderId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        fetchResults.forEach { result ->
            DesktopDanmakuSourceItem(
                result = result,
                onSetEnabled = { enabled -> onSetEnabled(result.serviceId, enabled) },
                onManualMatch = { onManualMatch(result.providerId) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DesktopDanmakuSourceItem(
    result: DanmakuFetchResultWithConfig,
    onSetEnabled: (Boolean) -> Unit,
    onManualMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Subtitles,
                contentDescription = null,
                tint = if (result.config.enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = renderDanmakuServiceId(result.serviceId),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (result.config.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                
                if (result.config.enabled) {
                    Text(
                        text = "${result.matchInfo.count} 条弹幕",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "已禁用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            
            if (result.config.enabled) {
                TextButton(
                    onClick = onManualMatch,
                ) {
                    Text("重新匹配")
                }
            }
            
            Switch(
                checked = result.config.enabled,
                onCheckedChange = onSetEnabled,
            )
        }
    }
}

private fun renderDanmakuServiceId(serviceId: DanmakuServiceId): String = when (serviceId) {
    DanmakuServiceId.Animeko -> "Animeko"
    DanmakuServiceId.AcFun -> "AcFun"
    DanmakuServiceId.Baha -> "Baha"
    DanmakuServiceId.Bilibili -> "哔哩哔哩"
    DanmakuServiceId.Dandanplay -> "弹弹play"
    DanmakuServiceId.Tucao -> "Tucao"
    else -> serviceId.value
}