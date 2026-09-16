/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.leanback.ui.exploration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import dev.chrisbanes.haze.HazeState
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_load_failed
import me.him188.ani.app.ui.lang.exploration_loading
import me.him188.ani.app.ui.lang.exploration_subject_details
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.subject_details_empty
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.app.ui.subject.rememberSubjectStatusStrings
import me.him188.ani.leanback.ui.foundation.focus.tvFocusMemorable
import me.him188.ani.leanback.ui.subject.components.LocalTvDetailsActionBackdrop
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.details.TvDetailsAction
import me.him188.ani.leanback.ui.subject.details.TvDetailsHero
import org.jetbrains.compose.resources.stringResource

/** Exploration supplies the details hero's action and progress slots, without a description card. */
@Composable
internal fun TvExplorationHero(
    hero: TvHeroSubject?,
    info: SubjectCollectionInfo?,
    followedSubject: FollowedSubjectInfo?,
    loadState: LoadState,
    expanded: Boolean,
    expandProgress: Float,
    minimumHeight: Dp,
    slideDirection: Int,
    carouselSize: Int,
    carouselIndex: Int,
    hazeState: HazeState,
    onClickDetails: () -> Unit,
    onButtonFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
) {
    val progress = expandProgress.coerceIn(0f, 1f)
    val loading = hero == null && loadState is LoadState.Loading
    val missingTitle = stringResource(
        when (loadState) {
            is LoadState.Loading -> Lang.exploration_loading
            is LoadState.Error -> Lang.exploration_load_failed
            is LoadState.NotLoading -> Lang.subject_details_empty
        },
    )
    val subject = info?.subjectInfo ?: SubjectInfo.Empty.copy(
        subjectId = hero?.subjectId ?: 0, nameCn = hero?.title ?: missingTitle,
    )
    val airingInfo = rememberUpdatedState(info?.airingInfo)
    val progressInfo = rememberUpdatedState(info?.progressInfo)
    val airing = remember(hero?.subjectId) { AiringLabelState(airingInfo, progressInfo) }
    Box(modifier.testTag("tv-exploration-hero")) {
        CompositionLocalProvider(LocalTvDetailsActionBackdrop provides hazeState) {
            TvDetailsHero(
                subject, airing, height = minimumHeight,
                titleModifier = Modifier.testTag("tv-exploration-hero-title"),
                titleSize = lerp(TvExplorationDefaults.CompactTitleSize, TvSubjectDetailsDefaults.TitleSize, progress),
                titleLineHeight = lerp(42.sp, TvSubjectDetailsDefaults.TitleLineHeight, progress),
                titleMinLines = 2,
                contentPadding = PaddingValues(
                    start = TvExplorationDefaults.StartPadding, end = TvExplorationDefaults.EndPadding,
                    top = lerp(
                        TvExplorationDefaults.HeroCompactTopPadding,
                        TvExplorationDefaults.HeroTopPadding,
                        progress,
                    ),
                    bottom = TvSubjectDetailsDefaults.OverviewBottomPadding,
                ),
                actionSpacing = 32.dp * progress,
                identityTransition = explorationHeroContentTransform(slideDirection),
                loading = loading,
                supportingContent = { displayedSubject, contentModifier ->
                    Column(contentModifier) {
                        Text(
                            displayedSubject.summary.trim(),
                            Modifier.fillMaxWidth().padding(top = 8.dp).testTag("tv-exploration-hero-summary"),
                            color = TvSubjectDetailsDefaults.SecondaryContent,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp, lineHeight = 21.sp,
                                lineHeightStyle = LineHeightStyle(
                                    LineHeightStyle.Alignment.Center,
                                    LineHeightStyle.Trim.None,
                                ),
                            ),
                            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        followedSubject?.takeIf { it.subjectCollectionInfo.subjectId == displayedSubject.subjectId }
                            ?.let { item ->
                                val state = rememberUpdatedState(item.subjectProgressInfo)
                                val status =
                                    remember(item.subjectCollectionInfo.subjectId) { SubjectProgressState(state) }
                                val watched = watchedEpisodeProgress(item.subjectCollectionInfo)
                                Column(
                                    Modifier.width(320.dp).padding(top = 12.dp)
                                        .testTag("tv-exploration-hero-progress-${displayedSubject.subjectId}"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            status.buttonText(rememberSubjectStatusStrings()),
                                            Modifier.testTag("tv-exploration-hero-status-${displayedSubject.subjectId}"),
                                            color = TvSubjectDetailsDefaults.Content,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            "${watched.watched} / ${watched.total}",
                                            color = TvSubjectDetailsDefaults.SecondaryContent,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { watched.fraction },
                                        modifier = Modifier.fillMaxWidth().height(3.dp)
                                            .testTag("tv-exploration-watched-progress-${displayedSubject.subjectId}"),
                                        color = TvSubjectDetailsDefaults.Content,
                                        trackColor = TvSubjectDetailsDefaults.Content.copy(alpha = .22f),
                                        gapSize = 0.dp, drawStopIndicator = {},
                                    )
                                }
                            }
                    }
                },
                actions = { compact ->
                    Box(
                        Modifier.height(48.dp * progress).graphicsLayer {
                            alpha = progress
                            translationY = (1f - progress) * 12.dp.toPx()
                        },
                    ) {
                        TvDetailsAction(
                            label = stringResource(
                                if (hero != null) Lang.exploration_subject_details
                                else if (loading) Lang.exploration_loading else Lang.settings_mediasource_retry,
                            ),
                            icon = Icons.Outlined.Info, onClick = onClickDetails, compact = compact,
                            blurBackground = true, loading = loading, available = !loading,
                            modifier = buttonModifier.testTag("tv-exploration-details")
                                .tvFocusMemorable("exploration-hero-details")
                                .focusProperties { canFocus = expanded }
                                .onFocusChanged { onButtonFocusChanged(it.isFocused) },
                        )
                    }
                },
            )
        }
        if (carouselSize > 1) {
            TvExplorationCarouselIndicator(
                carouselSize, carouselIndex,
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = TvExplorationDefaults.EndPadding, bottom = 22.dp)
                    .graphicsLayer { alpha = progress },
            )
        }
    }
}

/** Position only; automatic advance has no countdown track. */
@Composable
private fun TvExplorationCarouselIndicator(
    size: Int, selectedIndex: Int, modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(size) { index ->
            Box(
                Modifier.size(6.dp).clip(CircleShape)
                    .background(TvExplorationDefaults.Content.copy(alpha = if (index == selectedIndex) 1f else .32f))
                    .testTag("tv-exploration-dot-$index").semantics { selected = index == selectedIndex },
            )
        }
    }
}
