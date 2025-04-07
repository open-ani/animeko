/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.layout.BasicCarouselItem
import me.him188.ani.app.ui.foundation.layout.CarouselItemDefaults

@Composable
fun SubjectCoverCard(
    name: String?,
    image: String?,
    isPlaceholder: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CarouselItemDefaults.shape,
    imageModifier: Modifier = Modifier,
    brushLayerModifier: Modifier = Modifier,
) {
    BasicCarouselItem(
        label = { CarouselItemDefaults.Text(name ?: "", maxLines = 2) },
        modifier.placeholder(isPlaceholder, shape = shape),
        maskShape = shape,
        brushLayerModifier = brushLayerModifier,
    ) {
        if (!isPlaceholder) {
            val image = @Composable {
                AsyncImage(
                    image,
                    modifier = imageModifier.aspectRatio(9f / 16).fillMaxWidth(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                )
            }

            Surface({ onClick() }, content = image)
        } else {
            Box(Modifier.aspectRatio(9f / 16).fillMaxWidth())
        }
    }
}
