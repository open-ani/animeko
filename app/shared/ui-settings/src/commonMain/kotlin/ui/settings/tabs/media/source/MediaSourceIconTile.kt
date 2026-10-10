/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.settings.rendering.getIconResourceOrNull
import me.him188.ani.datasources.api.source.MediaSourceInfo

/**
 * 列表中的数据源图标.
 *
 * 数据源图标的形状各不相同: 有铺满的方图, 有透明背景的 logo, 也有横向的文字 logo.
 * 统一放在圆角底板上完整显示, 不裁切. 没有图标或加载失败时显示名称的首字.
 */
@Composable
internal fun MediaSourceIconTile(
    info: MediaSourceInfo,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    small: Boolean = false,
) {
    val resource = info.getIconResourceOrNull()
    val iconUrl = info.iconUrl?.takeIf { it.isNotBlank() }
    var failed by remember(iconUrl) { mutableStateOf(false) }
    Box(
        modifier
            .size(if (small) 16.dp else 40.dp)
            .clip(RoundedCornerShape(if (small) 4.dp else 10.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            resource != null -> Image(resource, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)

            iconUrl != null && !failed -> AsyncImage(
                iconUrl,
                contentDescription = null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onError = { failed = true },
            )

            else -> Text(
                info.displayName.trim().take(1).uppercase(),
                style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 订阅没有提供图标时, 用前四个数据源的图标拼成的图标.
 * 只有一个数据源时直接显示它的图标; 两个时放在对角, 使图标保持平衡.
 */
@Composable
internal fun MediaSourceIconMosaic(
    infos: List<MediaSourceInfo>,
    modifier: Modifier = Modifier,
) {
    if (infos.size == 1) {
        MediaSourceIconTile(infos.single(), modifier)
        return
    }
    val cells: List<MediaSourceInfo?> = when (infos.size) {
        2 -> listOf(infos[0], null, null, infos[1])
        else -> infos.take(4)
    }
    Column(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (column in 0 until 2) {
                    val info = cells.getOrNull(row * 2 + column)
                    if (info != null) {
                        MediaSourceIconTile(
                            info,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            small = true,
                        )
                    } else {
                        Spacer(Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
