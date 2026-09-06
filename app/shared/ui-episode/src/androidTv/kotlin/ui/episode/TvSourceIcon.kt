/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import me.him188.ani.app.ui.foundation.AsyncImage

private object TvSourceIconDefaults {
    val Size = 24.dp
    val Shape = RoundedCornerShape(6.dp)
    val Scrim = Color.Black.copy(alpha = .65f)
    val LoadingScrim = Color.Black.copy(alpha = .4f)
    val ProgressInset = 3.dp
    val ProgressWidth = 2.dp
}

/** Shared source identity for the controller and source tabs; the parent owns focus and selection. */
@Composable
internal fun TvSourceIcon(
    iconUrl: String?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    dimmed: Boolean = false,
) {
    Box(
        modifier.size(TvSourceIconDefaults.Size).clip(TvSourceIconDefaults.Shape),
        contentAlignment = Alignment.Center,
    ) {
        if (iconUrl.isNullOrBlank()) {
            Icon(Icons.Rounded.DisplaySettings, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(
                iconUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        if (loading || dimmed) Box(
            Modifier.fillMaxSize().background(
                if (loading) TvSourceIconDefaults.LoadingScrim else TvSourceIconDefaults.Scrim,
            ),
        )
        if (loading) CircularProgressIndicator(
            modifier = Modifier.fillMaxSize().padding(TvSourceIconDefaults.ProgressInset),
            color = Color.White,
            strokeWidth = TvSourceIconDefaults.ProgressWidth,
        )
    }
}
