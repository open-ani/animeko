/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.tv.ui.exploration

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.CompositeShader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import com.kmpalette.color
import com.kmpalette.rememberPaletteState
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.tv.ui.foundation.semantics.tvVisualProgress

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
    val imageFadeSpec = remember {
        tween<Float>(TvExplorationDefaults.BackdropFadeMillis, easing = ExplorationFocusEasing)
    }
    Box(modifier.fillMaxSize().background(TvExplorationDefaults.Background)) {
        Box(
            Modifier.fillMaxSize().testTag("tv-exploration-glow")
                .semantics { tvVisualProgress = scrollProgress() }
                .drawWithCache {
                    val wash = Brush.radialGradient(
                        listOf(glow.copy(alpha = .12f), Color.Transparent),
                        center = Offset(size.width * .70f, size.height * .25f), radius = size.width * .85f,
                    )
                    onDrawBehind { drawRect(wash) }
                },
        )
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = 1f - scrollProgress().coerceIn(0f, 1f) },
        ) {
            val transition = updateTransition(hero, label = "home-backdrop")
            val visibleSubjects = remember { mutableListOf(transition.currentState) }
            if (transition.currentState == transition.targetState) {
                visibleSubjects.removeAll { it != transition.targetState }
            }
            if (hero !in visibleSubjects) visibleSubjects.add(hero)
            for (index in 0 until visibleSubjects.size) {
                val subject = visibleSubjects[index]
                key(subject) {
                    val imageAlpha by transition.animateFloat(
                        transitionSpec = { imageFadeSpec },
                        label = "image-alpha",
                    ) { if (it == subject) 1f else 0f }
                    TvExplorationBackdropImage(
                        url = subject?.imageUrl.orEmpty(),
                        scale = { scale },
                        onPalette = { url, color -> if (url == currentUrl) glowTarget = color },
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            alpha = imageAlpha
                            // Retain the complete masked image only while it can contribute to the backdrop.
                            compositingStrategy = if (scrollProgress() >= 1f || (alpha == 0f && subject != hero)) {
                                CompositingStrategy.Auto
                            } else {
                                CompositingStrategy.Offscreen
                            }
                        }.testTag("tv-exploration-backdrop-${subject?.subjectId}"),
                    )
                }
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

@Composable
private fun TvExplorationBackdropImage(
    url: String,
    scale: () -> Float,
    onPalette: (String, Color) -> Unit,
    modifier: Modifier,
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    val palette = rememberPaletteState()
    LaunchedEffect(bitmap) { bitmap?.let { palette.generate(it) } }
    LaunchedEffect(palette.palette, url) {
        (palette.palette?.darkVibrantSwatch?.color ?: palette.palette?.dominantSwatch?.color)
            ?.let { onPalette(url, it) }
    }
    Box(modifier) {
        AsyncImage(
            url, null,
            Modifier.fillMaxSize().graphicsLayer {
                val currentScale = scale()
                scaleX = currentScale
                scaleY = currentScale
            }.testTag("tv-exploration-backdrop-image"),
            contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -.5f), crossfade = false,
            onSuccess = { bitmap = it.bitmap },
        )
        Box(Modifier.fillMaxSize().drawWithCache {
            val horizontal = LinearGradientShader(
                Offset.Zero, Offset(size.width, 0f),
                listOf(TvExplorationDefaults.Background, TvExplorationDefaults.Background.copy(alpha = .95f),
                    TvExplorationDefaults.Background.copy(alpha = .60f), Color.Transparent),
                listOf(0f, .18f, .45f, .72f), TileMode.Clamp,
            )
            val vertical = LinearGradientShader(
                Offset.Zero, Offset(0f, size.height),
                listOf(TvExplorationDefaults.Background.copy(alpha = .1f), Color.Transparent,
                    TvExplorationDefaults.Background.copy(alpha = .8f), TvExplorationDefaults.Background),
                listOf(0f, .4f, .78f, 1f), TileMode.Clamp,
            )
            val mask = ShaderBrush(CompositeShader(horizontal, vertical, BlendMode.SrcOver))
            onDrawBehind { drawRect(mask) }
        })
    }
}
