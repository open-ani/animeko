/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.rss.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthCompact

@Immutable
internal class MediaSourceHeadlineStyle(
    val imageSize: DpSize,
    val titleTextStyle: TextStyle,
    val imageTitleSpacing: Dp,
)

@Composable
internal fun computeMediaSourceHeadlineStyle(): MediaSourceHeadlineStyle {
    val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
    return when {
        windowSizeClass.isWidthCompact -> {
            MediaSourceHeadlineStyle(
                imageSize = DpSize(96.dp, 96.dp),
                titleTextStyle = MaterialTheme.typography.headlineMedium,
                imageTitleSpacing = 12.dp,
            )
        }

        // MEDIUM, EXPANDED for now,
        // and LARGE in the future
        else -> {
            MediaSourceHeadlineStyle(
                imageSize = DpSize(128.dp, 128.dp),
                titleTextStyle = MaterialTheme.typography.displaySmall,
                imageTitleSpacing = 20.dp,
            )
        }
    }
}

@Composable
internal fun MediaSourceHeadline(
    iconUrl: String,
    name: String,
    modifier: Modifier = Modifier,
    headlineStyle: MediaSourceHeadlineStyle = computeMediaSourceHeadlineStyle(),
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            iconUrl,
            contentDescription = null,
            Modifier
                .padding(top = headlineStyle.imageTitleSpacing)
                .size(headlineStyle.imageSize)
                .clip(MaterialTheme.shapes.medium),
            error = if (LocalIsPreviewing.current) rememberVectorPainter(Icons.Outlined.DisplaySettings) else null,
        )

        Text(
            name,
            Modifier
                .padding(top = headlineStyle.imageTitleSpacing)
                .padding(bottom = headlineStyle.imageTitleSpacing),
            style = headlineStyle.titleTextStyle,
            textAlign = TextAlign.Center,
        )
    }
}
