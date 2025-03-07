/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details

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
import me.him188.ani.app.ui.mediaselect.summary.MediaSelectorSummary
import me.him188.ani.app.ui.mediaselect.summary.MediaSelectorSummaryCard
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.collection.SubjectCollectionTypeSuggestions
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeDialogsHost
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.details.SubjectDetailsScene
import me.him188.ani.app.ui.subject.details.SubjectDetailsUIState
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.subject.episode.details.components.DanmakuMatchInfoGrid
import me.him188.ani.app.ui.subject.episode.details.components.DanmakuSourceCard
import me.him188.ani.app.ui.subject.episode.details.components.DanmakuSourceSettingsDropdown
import me.him188.ani.app.ui.subject.episode.details.components.PlayingEpisodeItemDefaults
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorState
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultPresentation
import me.him188.ani.app.ui.subject.episode.statistics.DanmakuMatchInfoSummaryRow
import me.him188.ani.app.ui.subject.episode.statistics.VideoStatistics
import me.him188.ani.app.ui.subject.episode.video.DanmakuStatistics
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.isDesktop

@Stable
class EpisodeDetailsState(
    val subjectInfo: State<SubjectInfo>,
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
    mediaSelectorSummary: MediaSelectorSummary,
    state: EpisodeDetailsState,
    episodeCarouselState: EpisodeCarouselState,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    danmakuStatistics: DanmakuStatistics,
    videoStatisticsFlow: Flow<VideoStatistics>,
    mediaSelectorState: MediaSelectorState,
    mediaSourceResultListPresentation: () -> MediaSourceResultListPresentation,
    authState: AuthState,
    onSwitchEpisode: (episodeId: Int) -> Unit,
    onRefreshMediaSources: () -> Unit,
    onRestartSource: (instanceId: String) -> Unit,
    onSetDanmakuSourceEnabled: (providerId: String, enabled: Boolean) -> Unit,
    onClickLogin: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
) {
    var showSubjectDetails by rememberSaveable {
        mutableStateOf(false)
    }

    if (state.subjectId != 0) {
        val subjectDetailsState by state.subjectDetailsStateLoader.state
            .collectAsStateWithLifecycle(SubjectDetailsUIState.Placeholder(state.subjectId))
        if (showSubjectDetails) {
            ModalBottomSheet(
                { showSubjectDetails = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = LocalPlatform.current.isDesktop()),
                modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
                contentWindowInsets = { BottomSheetDefaults.windowInsets.add(WindowInsets.desktopTitleBar()) },
            ) {
                SubjectDetailsScene(
                    subjectDetailsState,
                    authState,
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
    val navigator = LocalNavigator.current
    EpisodeDetailsScaffold(
        subjectTitle = { Text(state.subjectTitle) },
        episodeInfo = {
            episodeCarouselState.playingEpisode?.let {
                Row {
                    Text(
                        "${it.episodeInfo.sort}  ${it.episodeInfo.displayName}",
                        Modifier.weight(1f).align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    val mediaSelectorPresentation by mediaSelectorState.presentationFlow.collectAsStateWithLifecycle()

                    PlayingEpisodeItemDefaults.ActionShare(mediaSelectorPresentation.selected)
                    PlayingEpisodeItemDefaults.ActionCache({ navigator.navigateSubjectCaches(state.subjectId) })
                }
            }
        },
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
            var showMediaSelector by rememberSaveable { mutableStateOf(false) }
            if (showMediaSelector) {
                ModalBottomSheet(
                    { showMediaSelector = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = LocalPlatform.current.isDesktop()),
                    modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
                    contentWindowInsets = { BottomSheetDefaults.windowInsets.add(WindowInsets.desktopTitleBar()) },
                ) {
                    EpisodePlayMediaSelector(
                        mediaSelectorState,
                        mediaSourceResultListPresentation,
                        onDismissRequest = { showMediaSelector = false },
                        onRefresh = onRefreshMediaSources,
                        onRestartSource = onRestartSource,
                        onSelected = { showMediaSelector = false },
                        stickyHeaderBackgroundColor = BottomSheetDefaults.ContainerColor,
                    )
                }
            }

            MediaSelectorSummaryCard(
                mediaSelectorSummary,
                onClickManualSelect = { showMediaSelector = true },
                Modifier.fillMaxWidth().padding(innerPadding),
            )
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
                val colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer),
                )
                DanmakuMatchInfoGrid(
                    danmakuStatistics.fetchResults,
                    Modifier.padding(innerPadding),
                    itemSpacing = 16.dp,
                ) { source ->
                    var showDropdown by rememberSaveable { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        DanmakuSourceCard(
                            source.matchInfo,
                            enabled = source.config.enabled,
                            expandDanmakuStatistics,
                            onClickSettings = {
                                showDropdown = true
                            },
                            Modifier.fillMaxWidth(),
                            colors = colors,
                            dropdown = {
                                DanmakuSourceSettingsDropdown(
                                    showDropdown,
                                    onDismissRequest = { showDropdown = false },
                                    enabled = source.config.enabled,
                                    onSetEnabled = { enabled ->
                                        onSetDanmakuSourceEnabled(source.matchInfo.providerId, enabled)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        },
        onShowEpisodes = {
            state.showEpisodes = true
        },
        onExpandSubject = {
            showSubjectDetails = true
            state.subjectDetailsStateLoader.load(state.subjectId, state.subjectInfo.value)
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
    episodeInfo: @Composable () -> Unit,
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

        Row(Modifier.padding(horizontalPaddingValues).paddingIfNotEmpty(top = 12.dp)) {
            episodeInfo()
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
