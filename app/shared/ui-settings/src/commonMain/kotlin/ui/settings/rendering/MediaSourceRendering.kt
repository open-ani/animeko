/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.rendering

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.Res
import me.him188.ani.app.ui.foundation.dmhy
import me.him188.ani.app.ui.foundation.mikan
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceTier
import org.jetbrains.compose.resources.painterResource

@Composable
fun MediaSourceInfo.getIconResourceOrNull(): Painter? {
    val res = when (this.iconResourceId) {
        "mikan.png" -> Res.drawable.mikan
        "dmhy.png" -> Res.drawable.dmhy
        else -> null
    }
    return res?.let { painterResource(it) }
}

@Composable
fun MediaSourceIcon(
    sourceInfo: MediaSourceInfo?,
    modifier: Modifier = Modifier,
) {
    val url = sourceInfo?.getIconResourceOrNull()
    when {
        url != null && !LocalIsPreviewing.current -> { // TODO: 升级到 CMP 1.7 后, 可以去掉这里的 LocalIsPreviewing
            Image(
                url,
                null,
                modifier,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                colorFilter = null,
            )
        }

        sourceInfo != null -> {
            AsyncImage(
                sourceInfo.iconUrl?.takeIf { it.isNotEmpty() } ?: MediaSourceIcons.getDefaultIconUrl(sourceInfo),
                null,
                modifier,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                colorFilter = null,
            )
        }

        else -> {
            Image(
                rememberVectorPainter(Icons.Rounded.DisplaySettings), null,
                modifier,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

/**
 * 宽度不固定
 */
@Composable
fun SmallMediaSourceIcon(
    info: MediaSourceInfo,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(MaterialTheme.shapes.extraSmall).height(24.dp)) {
        val image = info.getIconResourceOrNull()
        when {
            image != null -> {
                Image(
                    image,
                    null, Modifier.size(24.dp),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    colorFilter = null,
                )
            }

            else -> {
                AsyncImage(
                    info.iconUrl?.takeIf { it.isNotEmpty() } ?: MediaSourceIcons.getDefaultIconUrl(info),
                    null, Modifier.size(24.dp),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    colorFilter = null,
                )
            }
        }
    }
}

/**
 * 「T0」「T1」样式的数据源等级标签.
 *
 * @param containerColor 标签所在的行本身是 secondaryContainer 底色 (例如选中的菜单项) 时换一个底色, 否则标签与行融为一体.
 */
@Composable
fun MediaSourceTierTag(
    tier: MediaSourceTier,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = "T${tier.value}",
            modifier = Modifier.wrapContentSize().padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            softWrap = false,
        )
    }
}
