/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material.icons.outlined.ExpandCircleDown
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.danmaku.DanmakuLoadingState
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.foundation.layout.paddingIfNotEmpty
import me.him188.ani.app.ui.media.renderProperties
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.collection.SubjectCollectionTypeSuggestions
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeDialogsHost
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.details.SubjectDetailsPage
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.subject.episode.details.components.DanmakuMatchInfoGrid
import me.him188.ani.app.ui.subject.episode.details.components.EpisodeWatchStatusButton
import me.him188.ani.app.ui.subject.episode.details.components.PlayingEpisodeItem
import me.him188.ani.app.ui.subject.episode.details.components.PlayingEpisodeItemDefaults
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorState
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultsPresentation
import me.him188.ani.app.ui.subject.episode.statistics.DanmakuMatchInfoSummaryRow
import me.him188.ani.app.ui.subject.episode.statistics.VideoLoadingSummary
import me.him188.ani.app.ui.subject.episode.statistics.VideoStatistics
import me.him188.ani.app.ui.subject.episode.video.DanmakuStatistics
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.datasources.api.unwrapCached
import me.him188.ani.utils.platform.isDesktop

@Stable
class EpisodeDetailsState(
    subjectInfo: State<SubjectInfo>,
    val airingLabelState: AiringLabelState,
    val subjectDetailsStateLoader: SubjectDetailsStateLoader,
) {
    private val subject by subjectInfo

    val subjectId by derivedStateOf { subject.subjectId }
//    var subjectDetailsState by mutableStateOf<SubjectDetailsState?>(null)
//    val subjectDetailsStateError: SearchProblem

    val subjectTitle by derivedStateOf { subject.displayName }

    var showEpisodes: Boolean by mutableStateOf(false)
}

/**
 * 番剧详情内容, 包含条目的基本信息, 选集, 评分.
 *
 * has inner top padding 8.dp
 */
@Composable
fun EpisodeDetails(
    state: EpisodeDetailsState,
    episodeCarouselState: EpisodeCarouselState,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    danmakuStatistics: DanmakuStatistics,
    videoStatisticsFlow: Flow<VideoStatistics>,
    mediaSelectorState: MediaSelectorState,
    mediaSourceResultsPresentation: MediaSourceResultsPresentation,
    authState: AuthState,
    onSwitchEpisode: (episodeId: Int) -> Unit,
    onRefreshMediaSources: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
) {
    var showSubjectDetails by rememberSaveable {
        mutableStateOf(false)
    }

    if (state.subjectId != 0) {
        val subjectDetailsState by state.subjectDetailsStateLoader.result
        if (showSubjectDetails) {
            ModalBottomSheet(
                { showSubjectDetails = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = LocalPlatform.current.isDesktop()),
                modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
                contentWindowInsets = { BottomSheetDefaults.windowInsets.add(WindowInsets.desktopTitleBar()) },
            ) {
                SubjectDetailsPage(
                    subjectDetailsState,
                    onPlay = onSwitchEpisode,
                    onLoadErrorRetry = { state.subjectDetailsStateLoader.reload(state.subjectId) },
                    showTopBar = false,
                    showBlurredBackground = false,
                    navigationIcon = {},
                )
            }
        }
    }

    var expandDanmakuStatistics by rememberSaveable { mutableStateOf(false) }

    if (state.showEpisodes) {
        ModalBottomSheet(
            { state.showEpisodes = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = LocalPlatform.current.isDesktop()),
            modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
            contentWindowInsets = { BottomSheetDefaults.windowInsets.add(WindowInsets.desktopTitleBar()) },
        ) {
            EpisodeCarousel(
                episodeCarouselState,
                contentPadding = PaddingValues(all = 16.dp),
            )
        }
    }

    EditableSubjectCollectionTypeDialogsHost(editableSubjectCollectionTypeState)

    val videoStatistics by videoStatisticsFlow.collectAsStateWithLifecycle(VideoStatistics.Placeholder)

    EpisodeDetailsScaffold(
        subjectTitle = { Text(state.subjectTitle) },
        airingStatus = {
            AiringLabel(
                state.airingLabelState,
                Modifier.align(Alignment.CenterVertically),
                style = LocalTextStyle.current,
                progressColor = LocalContentColor.current,
            )
        },
        subjectSuggestions = {
            // 推荐一些状态修改操作

            if (authState.isKnownLoggedIn) {
                val editableSubjectCollectionTypePresentation by editableSubjectCollectionTypeState.presentationFlow.collectAsStateWithLifecycle()
                when (editableSubjectCollectionTypePresentation.selfCollectionType) {
                    UnifiedCollectionType.NOT_COLLECTED -> {
                        SubjectCollectionTypeSuggestions.Collect(editableSubjectCollectionTypeState)
                    }

                    UnifiedCollectionType.WISH, UnifiedCollectionType.ON_HOLD -> {
                        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                            Text(
                                "已想看，可更改为：", Modifier.align(Alignment.CenterVertically),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { // 一起换行
                            SubjectCollectionTypeSuggestions.MarkAsDoing(editableSubjectCollectionTypeState)
                            SubjectCollectionTypeSuggestions.MarkAsDropped(editableSubjectCollectionTypeState)
                        }
                    }

                    else -> {}
                }
            }
        },
        exposedEpisodeItem = { innerPadding ->
            val originalMedia by remember {
                derivedStateOf {
                    videoStatistics.playingMedia?.unwrapCached() // 显示原始来源
                }
            }
            val mediaSelected by remember {
                derivedStateOf {
                    originalMedia != null
                }
            }
            episodeCarouselState.playingEpisode?.let { episode ->
                Card(
                    Modifier.padding(innerPadding).animateContentSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer),
                    ),
                ) {
                    PlayingEpisodeItem(
                        episodeSort = { Text(episode.episodeInfo.sort.toString()) },
                        title = { Text(episode.episodeInfo.displayName) },
                        watchStatus = {
                            if (authState.isKnownLoggedIn) {
                                EpisodeWatchStatusButton(
                                    episode.collectionType.isDoneOrDropped(),
                                    onUnmark = {
                                        episodeCarouselState.setCollectionType(
                                            episode,
                                            UnifiedCollectionType.NOT_COLLECTED,
                                        )
                                    },
                                    onMarkAsDone = {
                                        episodeCarouselState.setCollectionType(
                                            episode,
                                            UnifiedCollectionType.DONE,
                                        )
                                    },
                                    enabled = !episodeCarouselState.isSettingCollectionType.collectAsStateWithLifecycle().value,
                                )
                            }
                        },
                        mediaSelected = mediaSelected,
                        mediaLabels = {
                            val mediaPropertiesText by remember {
                                derivedStateOf {
                                    originalMedia?.renderProperties()
                                }
                            }
                            SelectionContainer { Text(mediaPropertiesText ?: "") }
                        },
                        filename = {
                            videoStatistics.playingFilename?.let {
                                SelectionContainer {
                                    Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        },
                        videoLoadingSummary = {
                            VideoLoadingSummary(videoStatistics.videoLoadingState)
                        },
                        mediaSource = {
                            var showMediaSelector by rememberSaveable { mutableStateOf(false) }
                            PlayingEpisodeItemDefaults.MediaSource(
                                media = originalMedia,
                                mediaSourceInfo = videoStatistics.playingMediaSourceInfo,
                                isLoading = videoStatistics.mediaSourceLoading,
                                onClick = { showMediaSelector = !showMediaSelector },
                            )
                            if (showMediaSelector) {
                                ModalBottomSheet(
                                    { showMediaSelector = false },
                                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = LocalPlatform.current.isDesktop()),
                                    modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
                                    contentWindowInsets = { BottomSheetDefaults.windowInsets.add(WindowInsets.desktopTitleBar()) },
                                ) {
                                    EpisodePlayMediaSelector(
                                        mediaSelectorState,
                                        mediaSourceResultsPresentation,
                                        onDismissRequest = { showMediaSelector = false },
                                        onRefresh = onRefreshMediaSources,
                                        onSelected = { showMediaSelector = false },
                                        stickyHeaderBackgroundColor = BottomSheetDefaults.ContainerColor,
                                    )
                                }
                            }
                        },
                        actions = {
                            val navigator = LocalNavigator.current
                            PlayingEpisodeItemDefaults.ActionShare(videoStatistics.playingMedia)
                            PlayingEpisodeItemDefaults.ActionCache({ navigator.navigateSubjectCaches(state.subjectId) })
                        },
                    )
                }
            }
        },
        danmakuStatisticsSummary = {
            DanmakuMatchInfoSummaryRow(
                danmakuStatistics,
                expanded = expandDanmakuStatistics,
                { expandDanmakuStatistics = !expandDanmakuStatistics },
            )
        },
        danmakuStatistics = { innerPadding ->
            val danmakuLoadingState = danmakuStatistics.danmakuLoadingState
            if (danmakuLoadingState is DanmakuLoadingState.Success) {
                DanmakuMatchInfoGrid(
                    danmakuLoadingState.matchInfos,
                    expanded = expandDanmakuStatistics,
                    Modifier.padding(innerPadding),
                    itemSpacing = 16.dp,
                    cardColors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer),
                    ),
                )
            }
        },
        onShowEpisodes = {
            state.showEpisodes = true
        },
        onExpandSubject = {
            showSubjectDetails = true
            state.subjectDetailsStateLoader.load(state.subjectId)
        },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun SectionTitle(
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Row(
        modifier.heightIn(min = 40.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            Row(Modifier.weight(1f)) {
                content()
            }
            Row(Modifier.padding(start = 16.dp)) {
                actions()
            }
        }
    }
}

@Composable
fun EpisodeDetailsScaffold(
    subjectTitle: @Composable () -> Unit,
    airingStatus: @Composable (FlowRowScope.() -> Unit),
    subjectSuggestions: @Composable (FlowRowScope.() -> Unit),
    exposedEpisodeItem: @Composable (contentPadding: PaddingValues) -> Unit,
    danmakuStatisticsSummary: @Composable () -> Unit,
    danmakuStatistics: @Composable (contentPadding: PaddingValues) -> Unit,
    onShowEpisodes: () -> Unit,
    onExpandSubject: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(all = 16.dp),
) {
    val contentPaddingState by rememberUpdatedState(contentPadding)
    val layoutDirection by rememberUpdatedState(LocalLayoutDirection.current)
    val horizontalPaddingValues by remember {
        derivedStateOf {
            PaddingValues(
                start = contentPaddingState.calculateStartPadding(layoutDirection),
                end = contentPaddingState.calculateStartPadding(layoutDirection),
            )
        }
    }
    val topPadding by remember {
        derivedStateOf {
            (contentPaddingState.calculateTopPadding() - 8.dp).coerceAtLeast(0.dp)
        }
    }
    val bottomPadding by remember {
        derivedStateOf {
            contentPaddingState.calculateBottomPadding()
        }
    }

    Column(
        modifier.padding(top = topPadding, bottom = bottomPadding).background(MaterialTheme.colorScheme.background),
    ) {
        // header
        Column(
            Modifier.padding(horizontalPaddingValues),
        ) {
            Row(Modifier.clickable(onClick = onExpandSubject)) {
                Box(
                    Modifier.padding(top = 8.dp) // icon button semantics padding
                        .weight(1f),
                ) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                        SelectionContainer { subjectTitle() }
                    }
                }

                Column(Modifier.padding(start = 24.dp)) {
                    Row {
                        IconButton(onExpandSubject) {
                            Icon(Icons.Outlined.ExpandCircleDown, null)
                        }
                    }
                }
            }
        }

        FlowRow(
            Modifier.padding(horizontalPaddingValues).paddingIfNotEmpty(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            subjectSuggestions()
        }

        SectionTitle(
            Modifier.padding(top = 12.dp, bottom = 8.dp),
            actions = {
                IconButton(onShowEpisodes) {
                    Icon(Icons.Outlined.Dataset, null)
                }
            },
        ) {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                airingStatus()
            }
        }

        Row {
            exposedEpisodeItem(horizontalPaddingValues)
        }

        SectionTitle(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            danmakuStatisticsSummary()
        }

        Row(Modifier.fillMaxWidth()) {
            danmakuStatistics(horizontalPaddingValues)
        }
    }
}
