/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.domain.episode.SubjectRecommendation
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.leanback.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.leanback.ui.foundation.widgets.tvOptionSurfaceColors

/** External/ad recommendations stay visible but have no navigation action on TV. */
internal val SubjectRecommendation.tvNavigationSubjectId: Int?
    get() = subjectId?.takeIf { uri == null && it in 1..Int.MAX_VALUE.toLong() }?.toInt()

/** The bottom layers slide/fade over a shared stationary scrim. */
@Composable
internal fun TvBottomControllerLayout(
    transition: Transition<Boolean>,
    recommendations: @Composable () -> Unit,
    controller: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Keep the shared scrim independent of both content transitions and panel height.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .4f to Color.Black.copy(alpha = .72f),
                        1f to Color.Black.copy(alpha = .96f),
                    ),
                ),
        )
        transition.AnimatedVisibility(
            visible = { !it },
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(220, delayMillis = 160)) +
                    slideInVertically(tween(220, delayMillis = 160)) { -it / 10 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 10 },
        ) {
            controller()
        }
        transition.AnimatedVisibility(
            visible = { it },
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(220, delayMillis = 160)) +
                    slideInVertically(tween(220, delayMillis = 160)) { it / 10 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 10 },
        ) {
            recommendations()
        }
    }
}

@Composable
internal fun TvPlayerRecommendationsRow(
    recommendations: List<SubjectRecommendation>,
    loading: Boolean,
    listState: LazyListState,
    entryModifier: Modifier,
    trapFocus: Boolean,
    onClick: (SubjectRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .testTag("tv-player-recommendations")
            .focusProperties { onExit = { if (trapFocus) cancelFocusChange() } }
            .focusGroup(),
    ) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 28.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TvPlayerControlsDefaults.HorizontalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("推荐条目", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    "按上或返回收起",
                    style = MaterialTheme.typography.labelMedium,
                    color = TvPlayerControlsDefaults.SecondaryContent,
                )
            }
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().testTag("tv-recommendations-row"),
                horizontalArrangement = Arrangement.spacedBy(TvPlayerEpisodeStripDefaults.CardSpacing),
                contentPadding = PaddingValues(
                    horizontal = TvPlayerControlsDefaults.HorizontalPadding,
                    vertical = 12.dp,
                ),
            ) {
                if (recommendations.isEmpty()) item(key = "empty") {
                    Surface(
                        onClick = {},
                        modifier = entryModifier
                            .width(TvPlayerEpisodeStripDefaults.CardWidth)
                            .aspectRatio(16f / 9f)
                            .testTag("tv-recommendations-empty"),
                        shape = ClickableSurfaceDefaults.shape(TvPlayerEpisodeStripDefaults.CardShape),
                        colors = tvOptionSurfaceColors(filled = true),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (loading) "正在加载推荐…" else "暂无推荐条目")
                        }
                    }
                }
                itemsIndexed(recommendations, key = { _, item -> item.uniqueId }) { index, recommendation ->
                    RecommendationStripCard(
                        recommendation,
                        onClick = { if (recommendation.tvNavigationSubjectId != null) onClick(recommendation) },
                        modifier = if (index == 0) entryModifier else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationStripCard(
    recommendation: SubjectRecommendation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selfFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(TvPlayerEpisodeStripDefaults.CardWidth)
            .aspectRatio(16f / 9f)
            .testTag("tv-recommendation-${recommendation.uniqueId}")
            .onFocusChanged { selfFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(TvPlayerEpisodeStripDefaults.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusDefaults.FocusedScale),
        border = TvFocusDefaults.clickableCardBorder(),
    ) {
        Box(
            Modifier.padding(TvFocusDefaults.RingInset).fillMaxSize()
                .clip(TvPlayerEpisodeStripDefaults.CardShape)
                .background(Color(0xFF181818)),
        ) {
            if (recommendation.imageUrl.isNotBlank()) AsyncImage(
                recommendation.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = .95f))),
            )
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)) {
                Text(
                    recommendation.nameCn?.takeIf { it.isNotBlank() } ?: recommendation.name,
                    Modifier
                        .fillMaxWidth()
                        .then(if (selfFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                for (description in listOf(recommendation.desc1, recommendation.desc2)) {
                    if (description.isBlank()) continue
                    Text(
                        description,
                        Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TvPlayerControlsDefaults.SecondaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
