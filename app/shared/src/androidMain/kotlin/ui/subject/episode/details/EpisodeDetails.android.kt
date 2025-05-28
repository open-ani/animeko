/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.subject.episode.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.danmaku.DanmakuLoadingState
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.domain.session.TestAuthState
import me.him188.ani.app.ui.episode.share.MediaShareData
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.ViewKind
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediafetch.request.TestMediaFetchRequest
import me.him188.ani.app.ui.mediaselect.summary.createTestMediaSelectorSummaryAutoSelecting
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.components.rememberTestEditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.createTestAiringLabelState
import me.him188.ani.app.ui.subject.details.state.createTestSubjectDetailsLoader
import me.him188.ani.app.ui.subject.episode.statistics.DanmakuStatistics
import me.him188.ani.app.ui.subject.episode.statistics.createTestDanmakuStatistics
import me.him188.ani.app.ui.subject.episode.statistics.testPlayerStatisticsState
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly


@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsLongTitle() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState(
        remember {
            SubjectInfo.Empty.copy(
                nameCn = "中文条目名称啊中文条目名称中文条啊目名称中文条目名称中文条目名称中文",
            )
        },
    )
    PreviewEpisodeDetailsImpl(state)
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsShortTitle() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState(
        remember {
            SubjectInfo.Empty.copy(
                nameCn = "小市民系列",
            )
        },
    )
    PreviewEpisodeDetailsImpl(state)
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsScroll() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState(
        remember {
            SubjectInfo.Empty.copy(
                nameCn = "小市民系列",
            )
        },
    )
    Column(Modifier.height(300.dp)) {
        PreviewEpisodeDetailsImpl(state)
    }
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsDoing() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState(
        remember {
            SubjectInfo.Empty.copy(
                nameCn = "小市民系列",
            )
        },
    )
    PreviewEpisodeDetailsImpl(
        state,
        editableSubjectCollectionTypeState = rememberTestEditableSubjectCollectionTypeState(UnifiedCollectionType.DOING),
    )
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsDanmakuFailed() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState()
    PreviewEpisodeDetailsImpl(
        state,
        remember {
            createTestDanmakuStatistics(DanmakuLoadingState.Failed(IllegalStateException()), danmakuEnabled = true)
        },
    )
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsNotAuthorized() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState()
    PreviewEpisodeDetailsImpl(
        state,
        authState = TestAuthState,
    )
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsDanmakuLoading() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState()
    PreviewEpisodeDetailsImpl(
        state,
        remember {
            createTestDanmakuStatistics(DanmakuLoadingState.Loading)
        },
    )
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsNotSelected() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState()
    PreviewEpisodeDetailsImpl(
        state,
        playingMedia = null,
    )
}

@OptIn(TestOnly::class)
@Composable
private fun rememberTestEpisodeDetailsState(
    subjectInfo: SubjectInfo = SubjectInfo.Empty.copy(
        nameCn = "中文条目名称啊中文条目名称中文条啊目名称中文条目名称中文条目名称中文",
    ),
): EpisodeDetailsState {
    val scope = rememberCoroutineScope()
    return remember {
        EpisodeDetailsState(
            subjectInfo = mutableStateOf(subjectInfo),
            airingLabelState = createTestAiringLabelState(),
            subjectDetailsStateLoader = createTestSubjectDetailsLoader(scope),
        )
    }
}

@OptIn(TestOnly::class)
@Composable
private fun PreviewEpisodeDetailsImpl(
    state: EpisodeDetailsState,
    danmakuStatistics: DanmakuStatistics = remember {
        createTestDanmakuStatistics(
            DanmakuLoadingState.Success,
        )
    },
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState = rememberTestEditableSubjectCollectionTypeState(),
    mediaSelectorState: MediaSelectorState = rememberTestMediaSelectorState(),
    playingMedia: Media? = TestMediaList.first(),
    authState: AuthState = TestAuthState,
) {
    Scaffold {
        EpisodeDetails(
            mediaSelectorSummary = createTestMediaSelectorSummaryAutoSelecting(),
            state,
            initialMediaSelectorViewKind = ViewKind.WEB,
            TestMediaFetchRequest,
            { },
            episodeCarouselState = remember {
                EpisodeCarouselState(
                    mutableStateOf(TestEpisodeCollections),
                    mutableStateOf(TestEpisodeCollections[1]),
                    cacheStatus = { EpisodeCacheStatus.NotCached },
                    onSelect = {},
                    onChangeCollectionType = { _, _ -> },
                    backgroundScope = PreviewScope,
                )
            },
            editableSubjectCollectionTypeState = editableSubjectCollectionTypeState,
            danmakuStatistics = danmakuStatistics,
            videoStatisticsFlow = remember {
                MutableStateFlow(
                    testPlayerStatisticsState(
                        playingMedia = playingMedia,
                        playingFilename = "filename-filename-filename-filename-filename-filename-filename.mkv",
                        videoLoadingState = VideoLoadingState.Succeed(isBt = true),
                    ),
                )
            },
            mediaSelectorState = mediaSelectorState,
            mediaSourceResultListPresentation = { TestMediaSourceResultListPresentation },
            authState = authState,
            onSwitchEpisode = {},
            onRefreshMediaSources = {},
            onRestartSource = {},
            onSetDanmakuSourceEnabled = { _, _ -> },
            onClickLogin = { },
            onClickTag = {},
            onManualMatchDanmaku = {
            },
            onEpisodeCollectionUpdate = {},
            shareData = MediaShareData.from(null, null),
            null, {},
            Modifier
                .padding(bottom = 16.dp, top = 8.dp)
                .padding(it)
                .verticalScroll(rememberScrollState()),
        )
    }
}