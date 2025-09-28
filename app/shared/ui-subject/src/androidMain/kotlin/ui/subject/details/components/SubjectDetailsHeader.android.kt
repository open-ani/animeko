/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.data.models.subject.ContinueWatchingStatus
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectAiringKind
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.TestCoverImage
import me.him188.ani.app.data.models.subject.TestSubjectInfo
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.rating.EditableRating
import me.him188.ani.app.ui.rating.rememberTestEditableRatingState
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.TestSubjectAiringInfo
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeButton
import me.him188.ani.app.ui.subject.collection.components.rememberTestEditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.progress.rememberTestSubjectProgressState
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.utils.platform.annotations.TestOnly


@Composable
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
fun PreviewSubjectDetailsHeaderCompleted() {
    PreviewSubjectDetailsHeader(
        airingInfo = TestSubjectAiringInfo,
        progressInfo = SubjectProgressInfo(
            continueWatchingStatus = ContinueWatchingStatus.Done,
            nextEpisodeIdToPlay = null,
        ),
        subjectInfo = TestSubjectInfo,
    )
}

@Composable
@Preview
fun PreviewSubjectDetailsHeaderCompletedLong() {
    PreviewSubjectDetailsHeader(
        airingInfo = TestSubjectAiringInfo,
        progressInfo = SubjectProgressInfo(
            continueWatchingStatus = ContinueWatchingStatus.Done,
            nextEpisodeIdToPlay = null,
        ),
        subjectInfo = TestSubjectInfo.copy(
            nameCn = "孤独摇滚".repeat(20),
        ),
    )
}

@Composable
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
fun PreviewSubjectDetailsHeaderOnAirWatched() {
    PreviewSubjectDetailsHeader(
        airingInfo = TestSubjectAiringInfo.copy(
            kind = SubjectAiringKind.ON_AIR,
            mainEpisodeCount = 24,
            latestSort = EpisodeSort(20),
        ),
        progressInfo = SubjectProgressInfo(
            continueWatchingStatus = ContinueWatchingStatus.Watched(
                episodeEp = EpisodeSort(20 - 12),
                episodeSort = EpisodeSort(20),
                PackedDate.Invalid,
            ),
            nextEpisodeIdToPlay = null,
        ),
    )
}

@Composable
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
fun PreviewSubjectDetailsHeaderOnAirContinue() {
    PreviewSubjectDetailsHeader(
        airingInfo = TestSubjectAiringInfo.copy(
            kind = SubjectAiringKind.ON_AIR,
            mainEpisodeCount = 24,
            latestSort = EpisodeSort(20),
        ),
        progressInfo = SubjectProgressInfo(
            continueWatchingStatus = ContinueWatchingStatus.Continue(
                episodeEp = EpisodeSort(20 - 12),
                episodeSort = EpisodeSort(20),
                watchedEpisodeEp = EpisodeSort(19 - 12),
                watchedEpisodeSort = EpisodeSort(19),
            ),
            nextEpisodeIdToPlay = null,
        ),
    )
}

@OptIn(TestOnly::class)
@Composable
fun PreviewSubjectDetailsHeader(
    airingInfo: SubjectAiringInfo,
    progressInfo: SubjectProgressInfo,
    subjectInfo: SubjectInfo = TestSubjectInfo,
) = ProvideCompositionLocalsForPreview {
    SubjectDetailsHeader(
        subjectInfo,
        TestCoverImage,
        seasonTags = {
            SubjectDetailsDefaults.SeasonTag(
                subjectInfo.airDate,
                AiringLabelState(
                    remember { stateOf(airingInfo) },
                    remember { stateOf(progressInfo) },
                ),
            )
        },
        collectionData = {
            SubjectDetailsDefaults.CollectionData(
                collectionStats = subjectInfo.collectionStats,
            )
        },
        collectionAction = {
            EditableSubjectCollectionTypeButton(
                rememberTestEditableSubjectCollectionTypeState(),
            )
        },
        selectEpisodeButton = {
            SubjectDetailsDefaults.SelectEpisodeButtons(rememberTestSubjectProgressState(), {}, {})
        },
        rating = {
            EditableRating(
                state = rememberTestEditableRatingState(),
            )
        },
    )
}
