/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.leanback.ui.exploration

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import me.him188.ani.app.data.models.subject.SubjectAiringKind
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_progress_episode_counts
import me.him188.ani.app.ui.lang.subject_progress_episode_counts_on_air
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.subject.details.TvDetailsAiringInfo
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class TvWatchingProgress(
    val watched: Int,
    val aired: Int?,
    val total: Int?,
    val onAir: Boolean,
) {
    val watchedFraction: Float get() = fraction(watched)
    val airedFraction: Float get() = fraction(maxOf(aired ?: 0, watched))

    private fun fraction(count: Int): Float = total?.let { (count.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
}

/** Counts main-story episodes; episode sort numbers may continue across seasons. */
internal fun SubjectCollectionInfo.watchingProgress(): TvWatchingProgress {
    val mainEpisodes = episodes.filter { it.episodeInfo.type == EpisodeType.MainStory }
    val total = maxOf(airingInfo.mainEpisodeCount, mainEpisodes.size).takeIf { it > 0 }
    val aired = when (airingInfo.kind) {
        SubjectAiringKind.COMPLETED -> total
        SubjectAiringKind.UPCOMING -> 0
        SubjectAiringKind.ON_AIR -> airingInfo.latestSort?.let { latest ->
            mainEpisodes.takeIf { it.isNotEmpty() }?.count { it.episodeInfo.sort <= latest }
        }
    }
    return TvWatchingProgress(
        watched = mainEpisodes.count { it.collectionType == UnifiedCollectionType.DONE },
        aired = aired,
        total = total,
        onAir = airingInfo.kind == SubjectAiringKind.ON_AIR,
    )
}

@Composable
internal fun HomeWatchingProgress(
    caption: String,
    collection: SubjectCollectionInfo?,
    airing: AiringLabelState?,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val progress = remember(collection) { collection?.watchingProgress() }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().testTag("tv-exploration-preview-status"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                caption, Modifier.testTag("tv-exploration-preview-caption"),
                color = TvExplorationDefaults.Content,
                fontSize = 16.sp, lineHeight = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (airing != null) {
                Text("·", color = TvExplorationDefaults.SecondaryContent, fontSize = 16.sp)
                TvDetailsAiringInfo(airing, Modifier.weight(1f).testTag("tv-exploration-preview-airing"))
            }
        }
        if (progress != null) {
            val counts = if (progress.onAir) stringResource(
                Lang.subject_progress_episode_counts_on_air,
                progress.watched.toString(), progress.aired?.toString() ?: "–", progress.total?.toString() ?: "–",
            ) else stringResource(
                Lang.subject_progress_episode_counts, progress.watched.toString(), progress.total?.toString() ?: "–",
            )
            Spacer(Modifier.height(8.dp))
            ChargingProgressBar(progress, counts, animate)
        }
    }
}

@Composable
private fun ChargingProgressBar(progress: TvWatchingProgress, counts: String, animate: Boolean) {
    val colors = MaterialTheme.colorScheme
    val watchedStart = lerp(colors.primary, colors.primaryContainer, .45f)
    val watchedEnd = colors.primary
    val animationSpec = if (animate) tween<Float>(
        TvExplorationDefaults.WatchingProgressTransitionMillis, easing = FastOutSlowInEasing,
    ) else snap()
    val watchedFraction by animateFloatAsState(progress.watchedFraction, animationSpec, label = "home-watched-progress")
    val airedFraction by animateFloatAsState(
        if (progress.onAir) progress.airedFraction else progress.watchedFraction,
        animationSpec, label = "home-aired-progress",
    )
    val phase = rememberChargingPhase(animate && watchedFraction > 0f)
    Box(
        Modifier.fillMaxWidth().height(TvExplorationDefaults.WatchingProgressHeight)
            .testTag("tv-exploration-watching-progress").semantics {
                stateDescription = counts
                progress.total?.let { progressBarRangeInfo = ProgressBarRangeInfo(progress.watched.toFloat(), 0f..it.toFloat()) }
            },
    ) {
        if (watchedFraction > 0f) Box(
            Modifier.fillMaxWidth(watchedFraction).fillMaxHeight().padding(vertical = 2.dp)
                .blur(11.dp, BlurredEdgeTreatment.Unbounded)
                .background(colors.primary.copy(alpha = .22f), CircleShape),
        )
        Box(
            Modifier.matchParentSize().clip(CircleShape).background(colors.surfaceContainerHighest)
                .border(1.dp, colors.primary.copy(alpha = .6f), CircleShape),
        ) {
            if (progress.onAir || airedFraction > watchedFraction) Box(
                Modifier.fillMaxWidth(airedFraction).fillMaxHeight()
                    .background(colors.outlineVariant, CircleShape)
                    .testTag("tv-exploration-watching-aired"),
            )
            Box(
                Modifier.fillMaxWidth(watchedFraction).fillMaxHeight().clip(CircleShape)
                    .testTag("tv-exploration-watching-watched")
                    .drawWithCache {
                        val fill = Brush.horizontalGradient(
                            listOf(watchedStart, watchedEnd),
                        )
                        val sheen = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = .22f), .46f to Color.Transparent,
                            1f to colors.onPrimary.copy(alpha = .1f),
                        )
                        val capWidth = minOf(16.dp.toPx(), size.width)
                        val cap = Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 1f / 6f)),
                            startX = size.width - capWidth, endX = size.width,
                        )
                        val count = (size.width / 22.dp.toPx()).roundToInt().coerceIn(3, 18)
                        val diamond = Path()
                        onDrawBehind {
                            if (size.width <= 0f) return@onDrawBehind
                            drawRect(fill)
                            drawRect(sheen)
                            drawRect(cap, topLeft = Offset(size.width - capWidth, 0f), size = Size(capWidth, size.height))
                            drawChargingParticles(phase.value, count, diamond)
                        }
                    },
            )
        }
    }
}

/** The frame clock invalidates only the watched segment's drawing. */
@Composable
private fun rememberChargingPhase(animate: Boolean): State<Double> {
    val phase = remember { mutableDoubleStateOf(0.0) }
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val active = animate && LocalWindowInfo.current.isWindowFocused && !LocalInspectionMode.current && durationScale > 0f
    LaunchedEffect(active, durationScale) {
        if (!active) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive) withFrameNanos { now ->
            phase.doubleValue += ((now - previous) / 1_000_000_000.0).coerceIn(0.0, .06) * .33 / durationScale
            previous = now
        }
    }
    return phase
}

private fun DrawScope.drawChargingParticles(phase: Double, count: Int, diamond: Path) {
    for (index in 0 until count) {
        val particle = ChargingProgressStyle.Particles[index]
        val position = ((particle.start + phase * particle.speed) % 1.0).toFloat()
        val trailLength = (12f + particle.speed * 6f) * density
        // The entire trail leaves the right edge before this particle loops back to the left.
        val travelWidth = size.width + trailLength + 12f * density
        for (trailStep in ChargingProgressStyle.TrailSteps downTo 0) {
            val distance = trailLength * trailStep / ChargingProgressStyle.TrailSteps
            val samplePosition = position - distance / travelWidth
            val x = samplePosition * travelWidth - 6f * density
            val y = particle.lane * size.height + sin(samplePosition * 2 * PI + particle.start * 6).toFloat() * .6f * density
            val edgeFade = minOf(
                1f, (x / (8f * density)).coerceAtLeast(0f), ((size.width - x) / (8f * density)).coerceAtLeast(0f),
            )
            val trailAlpha = if (trailStep == 0) 1f else .32f * (1f - trailStep / (ChargingProgressStyle.TrailSteps + 1f))
            val alpha = edgeFade * particle.opacity * trailAlpha
            if (alpha <= 0f) continue
            val radius = minOf(particle.radius * density, size.height * .18f) * (1f - trailStep * .1f)
            val color = Color.White.copy(alpha = alpha)
            when (index % 3) {
                0 -> drawCircle(color, radius * .85f, Offset(x, y))
                1 -> {
                    diamond.rewind()
                    diamond.moveTo(x, y - radius)
                    diamond.lineTo(x + radius, y)
                    diamond.lineTo(x, y + radius)
                    diamond.lineTo(x - radius, y)
                    diamond.close()
                    drawPath(diamond, color)
                }
                else -> drawRect(
                    color, Offset(x - radius * .75f, y - radius * .75f), Size(radius * 1.5f, radius * 1.5f),
                )
            }
        }
    }
}

private data class ChargingParticle(
    val start: Double,
    val lane: Float,
    val speed: Float,
    val radius: Float,
    val opacity: Float,
)

private object ChargingProgressStyle {
    const val TrailSteps = 4
    val Particles = List(18) { index ->
        fun noise(salt: Double): Float {
            val value = sin((index + 1) * salt) * 43758.5453
            return (value - floor(value)).toFloat()
        }
        ChargingParticle(
            start = (index * .61803398875) % 1.0,
            lane = .18f + noise(12.9898) * .64f,
            speed = .75f + noise(47.17) * .75f,
            radius = 1.8f + noise(5.381) * 1.4f,
            opacity = .38f + noise(26.72) * .32f,
        )
    }
}
