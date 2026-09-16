/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.leanback.ui.exploration

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.kmpalette.color
import com.kmpalette.rememberPaletteState
import me.him188.ani.leanback.ui.subject.components.TvDetailsBackdrop

/** The backdrop stays fixed in the viewport; artwork and cover glow only change opacity. */
@Composable
internal fun TvExplorationBackdrop(
    hero: TvHeroSubject?,
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val currentUrl by rememberUpdatedState(hero?.imageUrl)
    var glowTarget by remember { mutableStateOf(TvExplorationDefaults.Background) }
    val glow by animateColorAsState(glowTarget, tween(450), label = "exploration-cover-glow")
    Box(modifier.fillMaxSize().background(TvExplorationDefaults.Background)) {
        Box(
            Modifier.fillMaxSize().testTag("tv-exploration-glow")
                .semantics { stateDescription = scrollProgress().toString() }
                .graphicsLayer { alpha = scrollProgress().coerceIn(0f, 1f) }
                .drawWithCache {
                    val upper = Brush.radialGradient(
                        listOf(glow.copy(alpha = .40f), Color.Transparent),
                        center = Offset(size.width * .65f, 0f), radius = size.width * .85f,
                    )
                    val lower = Brush.radialGradient(
                        listOf(glow.copy(alpha = .15f), Color.Transparent),
                        center = Offset(0f, size.height), radius = size.width * .7f,
                    )
                    onDrawBehind { drawRect(upper); drawRect(lower) }
                },
        )
        Crossfade(
            hero,
            Modifier.fillMaxSize().graphicsLayer { alpha = 1f - scrollProgress().coerceIn(0f, 1f) },
            animationSpec = tween(TvExplorationDefaults.BackdropFadeMillis), label = "exploration-backdrop",
        ) { subject ->
            val imageUrl = subject?.imageUrl.orEmpty()
            var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
            val palette = rememberPaletteState()
            LaunchedEffect(bitmap) { bitmap?.let { palette.generate(it) } }
            LaunchedEffect(palette.palette, imageUrl) {
                if (imageUrl == currentUrl) {
                    val colors = palette.palette
                    val color = colors?.vibrantSwatch?.color ?: colors?.dominantSwatch?.color
                    if (color != null) glowTarget = color
                }
            }
            TvDetailsBackdrop(
                imageUrl, blurProgress = { 0f }, crossfade = false,
                modifier = Modifier.testTag("tv-exploration-backdrop-${subject?.subjectId}"),
                onImageLoaded = { bitmap = it },
                onImageError = { if (imageUrl == currentUrl) glowTarget = TvExplorationDefaults.Background },
            )
        }
    }
}
