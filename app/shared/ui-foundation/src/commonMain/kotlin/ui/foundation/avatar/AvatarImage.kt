/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.avatar

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import me.him188.ani.app.ui.foundation.AsyncImage

@Composable
fun AvatarImage(
    url: String?,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    if (url == null) {
        Image(Icons.Rounded.Person, null, modifier)
    } else {
        AsyncImage(
            model = url,
            contentDescription = "Avatar",
            modifier = modifier,
            error = rememberVectorPainter(Icons.Rounded.Person),
            fallback = rememberVectorPainter(Icons.Rounded.Person),
            alignment = alignment,
            contentScale = contentScale,
            colorFilter = colorFilter,
        )
    }
}
