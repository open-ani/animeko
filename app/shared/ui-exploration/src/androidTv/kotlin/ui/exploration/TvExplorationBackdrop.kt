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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.kmpalette.color
import com.kmpalette.rememberPaletteState
import me.him188.ani.app.ui.foundation.AsyncImage

@Composable
internal fun TvExplorationBackdrop(
    hero: TvHeroSubject?,
    scrollProgress: () -> Float,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentUrl by rememberUpdatedState(hero?.imageUrl)
    var glowTarget by remember { mutableStateOf(Color(0xFF35455B)) }
    val glow by animateColorAsState(glowTarget, tween(400, easing = ExplorationFocusEasing), label = "home-backdrop-color")
    val scale by animateFloatAsState(
        if (expanded) 1f else 1.1f, tween(400, easing = ExplorationFocusEasing), label = "home-backdrop-scale",
    )
    Box(modifier.fillMaxSize().background(TvExplorationDefaults.Background)) {
        Box(
            Modifier.fillMaxSize().testTag("tv-exploration-glow")
                .semantics { stateDescription = scrollProgress().toString() }
                .drawWithCache {
                    val wash = Brush.radialGradient(
                        listOf(glow.copy(alpha = .12f), Color.Transparent),
                        center = Offset(size.width * .70f, size.height * .25f), radius = size.width * .85f,
                    )
                    onDrawBehind { drawRect(wash) }
                },
        )
        Crossfade(
            hero,
            Modifier.fillMaxSize().graphicsLayer { alpha = 1f - scrollProgress().coerceIn(0f, 1f) },
            animationSpec = tween(TvExplorationDefaults.BackdropFadeMillis, easing = ExplorationFocusEasing),
            label = "home-backdrop",
        ) { subject ->
            val url = subject?.imageUrl.orEmpty()
            var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
            val palette = rememberPaletteState()
            LaunchedEffect(bitmap) { bitmap?.let { palette.generate(it) } }
            LaunchedEffect(palette.palette, url) {
                if (url == currentUrl) {
                    (palette.palette?.darkVibrantSwatch?.color ?: palette.palette?.dominantSwatch?.color)
                        ?.let { glowTarget = it }
                }
            }
            Box(Modifier.fillMaxSize().testTag("tv-exploration-backdrop-${subject?.subjectId}")) {
                AsyncImage(
                    url, null, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale },
                    contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -.5f), crossfade = false,
                    onSuccess = { bitmap = it.bitmap },
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to TvExplorationDefaults.Background,
                            .18f to TvExplorationDefaults.Background.copy(alpha = .95f),
                            .45f to TvExplorationDefaults.Background.copy(alpha = .60f),
                            .72f to Color.Transparent,
                        ),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to TvExplorationDefaults.Background.copy(alpha = .1f),
                            .4f to Color.Transparent,
                            .78f to TvExplorationDefaults.Background.copy(alpha = .8f),
                            1f to TvExplorationDefaults.Background,
                        ),
                    ),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val halo = Brush.radialGradient(
                    listOf(glow.copy(alpha = .10f), Color.Transparent),
                    center = Offset(size.width * .12f, size.height * .6f), radius = size.width * .32f,
                )
                onDrawBehind { drawRect(halo) }
            },
        )
    }
}
