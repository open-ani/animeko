/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.domain.session.SessionStatus
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.rememberBackgroundScope
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.components.createTestAiringLabelState
import me.him188.ani.app.ui.subject.collection.components.rememberTestEditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.episode.EpisodePresentation
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorPresentation
import me.him188.ani.app.ui.subject.episode.mediaFetch.rememberTestMediaSelectorPresentation
import me.him188.ani.app.ui.subject.episode.mediaFetch.rememberTestMediaSourceResults
import me.him188.ani.app.ui.subject.episode.statistics.DanmakuLoadingState
import me.him188.ani.app.ui.subject.episode.statistics.VideoLoadingState
import me.him188.ani.app.ui.subject.episode.statistics.testPlayerStatisticsState
import me.him188.ani.app.ui.subject.episode.video.MutableDanmakuStatistics
import me.him188.ani.danmaku.api.DanmakuMatchInfo
import me.him188.ani.danmaku.api.DanmakuMatchMethod
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly


@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsLongTitle() = ProvideFoundationCompositionLocalsForPreview {
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
fun PreviewEpisodeDetailsShortTitle() = ProvideFoundationCompositionLocalsForPreview {
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
fun PreviewEpisodeDetailsScroll() = ProvideFoundationCompositionLocalsForPreview {
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
fun PreviewEpisodeDetailsDoing() = ProvideFoundationCompositionLocalsForPreview {
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
fun PreviewEpisodeDetailsDanmakuFailed() = ProvideFoundationCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState()
    PreviewEpisodeDetailsImpl(
        state,
        remember {
            MutableDanmakuStatistics().apply {
                danmakuLoadingState = DanmakuLoadingState.Failed(IllegalStateException())
            }
        },
    )
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsNotAuthorized() = ProvideFoundationCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState()
    PreviewEpisodeDetailsImpl(
        state,
        authState = rememberTestAuthState(SessionStatus.NoToken),
    )
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsDanmakuLoading() = ProvideFoundationCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState()
    PreviewEpisodeDetailsImpl(
        state,
        remember {
            MutableDanmakuStatistics().apply {
                danmakuLoadingState = DanmakuLoadingState.Loading
            }
        },
    )
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsNotSelected() = ProvideFoundationCompositionLocalsForPreview {
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
) = remember {
    EpisodeDetailsState(
        episodePresentation = mutableStateOf(
            EpisodePresentation.Placeholder.copy(
                title = "一个剧集",
                sort = "01",
                isPlaceholder = false,
            ),
        ),
        subjectInfo = mutableStateOf(subjectInfo),
        airingLabelState = createTestAiringLabelState(),
    )
}

@OptIn(TestOnly::class)
@Composable
private fun PreviewEpisodeDetailsImpl(
    state: EpisodeDetailsState,
    danmakuStatistics: MutableDanmakuStatistics = remember {
        MutableDanmakuStatistics().apply {
            danmakuLoadingState = DanmakuLoadingState.Success(
                listOf(
                    DanmakuMatchInfo(
                        "弹幕源弹幕源弹幕源 A", 100,
                        DanmakuMatchMethod.Fuzzy("条目标题", "剧集标题"),
                    ),
                    DanmakuMatchInfo(
                        "弹幕源 B", 100,
                        DanmakuMatchMethod.ExactId(123456, 222222),
                    ),
                ),
            )
        }
    },
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState = rememberTestEditableSubjectCollectionTypeState(),
    mediaSelectorPresentation: MediaSelectorPresentation = rememberTestMediaSelectorPresentation(),
    playingMedia: Media? = TestMediaList.first(),
    authState: AuthState = rememberTestAuthState(),
) {
    Scaffold {
        EpisodeDetails(
            state,
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
            videoStatistics = remember {
                testPlayerStatisticsState(
                    playingMedia = playingMedia,
                    playingFilename = "filename-filename-filename-filename-filename-filename-filename.mkv",
                    videoLoadingState = VideoLoadingState.Succeed(isBt = true),
                )
            },
            mediaSelectorPresentation = mediaSelectorPresentation,
            mediaSourceResultsPresentation = rememberTestMediaSourceResults(),
            authState = authState,
            Modifier
                .padding(bottom = 16.dp, top = 8.dp)
                .padding(it)
                .verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
fun rememberTestAuthState(
    state: SessionStatus = SessionStatus.Verified("", UserInfo.EMPTY),
): AuthState {
    val state1 = remember { mutableStateOf(state) }
    val scope = rememberBackgroundScope()
    return remember {
        AuthState(
            state1,
            launchAuthorize = { },
            retry = {},
            scope.backgroundScope,
        )
    }
}
