/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.leanback.ui.exploration

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_load_failed
import me.him188.ani.app.ui.lang.exploration_loading
import me.him188.ani.app.ui.lang.tv_exploration_more_details
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.subject_details_empty
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.rememberSubjectStatusStrings
import me.him188.ani.leanback.ui.foundation.focus.tvFocusMemorable
import me.him188.ani.leanback.ui.subject.details.TvDetailsMetadata
import me.him188.ani.leanback.ui.subject.details.TvDetailsActionVisual
import me.him188.ani.leanback.ui.subject.details.TvDetailsTitle
import org.jetbrains.compose.resources.stringResource

/** Featured pages share one translation for their title, metadata, summary and action. */
@Composable
internal fun TvExplorationHero(
    featured: TvHeroSubject?,
    featuredInfo: SubjectCollectionInfo?,
    preview: TvHeroSubject?,
    previewInfo: SubjectCollectionInfo?,
    followedSubject: FollowedSubjectInfo?,
    loadState: LoadState,
    expanded: Boolean,
    expandProgress: Float,
    collapsedHeight: Dp,
    slideDirection: Int,
    carouselSize: Int,
    carouselIndex: Int,
    previewVisible: Boolean,
    animateProgress: Boolean,
    onClickDetails: () -> Unit,
    onButtonFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
) {
    val featuredHeight = collapsedHeight + TvExplorationDefaults.HeroFeaturedExtraSpace
    val progress = expandProgress.coerceIn(0f, 1f)
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val actionText = stringResource(
        if (featured != null) Lang.tv_exploration_more_details
        else if (loadState is LoadState.Loading) Lang.exploration_loading
        else Lang.settings_mediasource_retry,
    )
    BoxWithConstraints(modifier.fillMaxWidth().height(collapsedHeight + TvExplorationDefaults.HeroFeaturedExtraSpace * progress)
        .testTag("tv-exploration-hero")) {
        val pageWidth = with(LocalDensity.current) { maxWidth.roundToPx() }
        Box(
            Modifier.fillMaxWidth().height(featuredHeight)
                .graphicsLayer {
                    translationY = -TvExplorationDefaults.HeroFeaturedExtraSpace.toPx() * (1f - progress)
                    alpha = progress
                }
                .then(if (expanded) Modifier else Modifier.clearAndSetSemantics {}),
        ) {
            AnimatedContent(
                targetState = featured to featuredInfo,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { explorationHeroContentTransform(slideDirection, pageWidth) },
                contentKey = { it.first?.subjectId },
                label = "home-featured-identity",
            ) { (subject, collection) ->
                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.align(Alignment.BottomStart)
                            .padding(start = TvExplorationDefaults.StartPadding, bottom = 96.dp, end = 40.dp)
                            .widthIn(max = 560.dp),
                    ) {
                        TvDetailsTitle(
                            collection?.subjectInfo?.displayName ?: subject?.title ?: stringResource(
                                when (loadState) {
                                    is LoadState.Loading -> Lang.exploration_loading
                                    is LoadState.Error -> Lang.exploration_load_failed
                                    else -> Lang.subject_details_empty
                                },
                            ),
                            Modifier.fillMaxWidth().testTag("tv-exploration-featured-title"),
                        )
                        HomeMetadata(collection, Modifier.testTag("tv-exploration-featured-metadata-${subject?.subjectId}"))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            collection?.subjectInfo?.summary?.replace(Regex("\\s+"), " ").orEmpty(),
                            Modifier.widthIn(max = 484.dp).testTag("tv-exploration-featured-summary"),
                            color = TvExplorationDefaults.SecondaryContent,
                            fontSize = 14.sp, lineHeight = 18.sp, minLines = 2, maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TvDetailsActionVisual(
                        actionText, focused && expanded,
                        Modifier.align(Alignment.BottomStart)
                            .padding(start = TvExplorationDefaults.StartPadding, bottom = 33.dp)
                            .testTag("tv-exploration-action-${subject?.subjectId}")
                            .clearAndSetSemantics {},
                        blurBackground = true, glowOnFocus = true,
                    )
                }
            }
            // The focus target stays in place while its visual belongs to the moving page.
            Box(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = TvExplorationDefaults.StartPadding, bottom = 33.dp)
                    .then(buttonModifier)
                    .testTag("tv-exploration-details")
                    .tvFocusMemorable("exploration-hero-details")
                    .focusProperties { canFocus = expanded }
                    .onFocusChanged { focused = it.isFocused; onButtonFocusChanged(it.isFocused) }
                    .semantics { contentDescription = actionText }
                    .clickable(interaction, indication = null, role = Role.Button, onClick = onClickDetails),
                contentAlignment = Alignment.Center,
            ) {
                TvDetailsActionVisual(actionText, false, Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {})
            }
            if (carouselSize > 1) HomeIndicators(
                carouselSize, carouselIndex,
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = TvExplorationDefaults.EndPadding, bottom = 33.dp),
            )
        }
        val previewVisibility = remember { MutableTransitionState(false) }
        previewVisibility.targetState = previewVisible && preview != null
        AnimatedVisibility(
            visibleState = previewVisibility,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .testTag("tv-exploration-preview-reveal")
                .then(if (previewVisible) Modifier else Modifier.clearAndSetSemantics {}),
            enter = expandVertically(
                tween(TvExplorationDefaults.HeroTransitionMillis, easing = ExplorationPanelEasing),
                expandFrom = Alignment.Bottom,
            ),
            exit = shrinkVertically(
                tween(TvExplorationDefaults.HeroTransitionMillis, easing = ExplorationPanelEasing),
                shrinkTowards = Alignment.Bottom,
            ),
        ) {
            val subject = preview
            val collection = previewInfo
            val info = collection?.subjectInfo
            val summary = info?.summary?.replace(Regex("\\s+"), " ").orEmpty()
            val strings = rememberSubjectStatusStrings()
            Box(Modifier.fillMaxWidth().height(collapsedHeight)) {
                Icon(
                    Icons.Outlined.KeyboardArrowUp, null,
                    Modifier.align(Alignment.TopCenter).padding(top = 16.dp).size(24.dp),
                    tint = TvExplorationDefaults.SecondaryContent.copy(alpha = .6f),
                )
                Column(
                    Modifier.fillMaxSize().padding(
                        start = TvExplorationDefaults.StartPadding, end = 40.dp,
                        bottom = 26.dp,
                    ),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    TvDetailsTitle(
                        collection?.subjectInfo?.displayName ?: subject?.title.orEmpty(),
                        Modifier.widthIn(max = 620.dp).fillMaxWidth().testTag("tv-exploration-preview-title"),
                    )
                    if (collection != null) {
                        HomeMetadata(
                            collection, Modifier.testTag("tv-exploration-preview-metadata"),
                            includeAiring = followedSubject == null,
                        )
                    }
                    if (summary.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            summary, Modifier.widthIn(max = TvExplorationDefaults.PreviewDescriptionWidth)
                                .testTag("tv-exploration-preview-summary"),
                            color = TvExplorationDefaults.SecondaryContent,
                            fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val progressInfo = rememberUpdatedState(collection?.progressInfo ?: followedSubject?.subjectProgressInfo)
                    val watching = remember { SubjectProgressState(progressInfo) }
                    if (followedSubject != null) {
                        Spacer(Modifier.height(14.dp))
                        HomeWatchingProgress(
                            watching.buttonText(strings), collection,
                            collection?.let { rememberHomeAiringState(it) },
                            Modifier.widthIn(max = TvExplorationDefaults.PreviewDescriptionWidth).fillMaxWidth(),
                            animate = animateProgress,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMetadata(
    collection: SubjectCollectionInfo?,
    modifier: Modifier = Modifier,
    includeAiring: Boolean = true,
) {
    if (collection == null) return
    Box(modifier) {
        TvDetailsMetadata(collection.subjectInfo, if (includeAiring) rememberHomeAiringState(collection) else null)
    }
}

@Composable
private fun rememberHomeAiringState(collection: SubjectCollectionInfo): AiringLabelState {
    val airing = rememberUpdatedState(collection.airingInfo)
    val progress = rememberUpdatedState(collection.progressInfo)
    return remember { AiringLabelState(airing, progress) }
}

@Composable
private fun HomeIndicators(count: Int, selectedIndex: Int, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val visibleCount = minOf(count, TvExplorationDefaults.CarouselVisibleIndicators)
    val viewportWidth = 8.dp + 16.dp * (visibleCount - 1)
    LaunchedEffect(selectedIndex, count) {
        state.animateScrollToItem(selectedIndex)
    }
    LazyRow(
        modifier.width(viewportWidth).testTag("tv-exploration-indicators"), state = state,
        contentPadding = PaddingValues(horizontal = (viewportWidth - 8.dp) / 2),
        horizontalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false,
    ) {
        items(count, key = { it }) { index ->
            Box(
                Modifier.size(8.dp).background(
                    TvExplorationDefaults.Content.copy(alpha = if (index == selectedIndex) 1f else .32f),
                    CircleShape,
                ).testTag("tv-exploration-dot-$index").semantics { selected = index == selectedIndex },
            )
        }
    }
}
