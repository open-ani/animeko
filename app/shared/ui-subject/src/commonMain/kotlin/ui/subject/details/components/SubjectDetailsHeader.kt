/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.him188.ani.app.data.models.subject.ContinueWatchingStatus
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectAiringKind
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.TestCoverImage
import me.him188.ani.app.data.models.subject.TestSubjectInfo
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paddingIfNotEmpty
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

const val COVER_WIDTH_TO_HEIGHT_RATIO = 849 / 1200f

// 图片和标题
@Composable
internal fun SubjectDetailsHeader(
    info: SubjectInfo?,
    coverImageUrl: String?,
    seasonTags: @Composable () -> Unit,
    collectionData: @Composable () -> Unit,
    collectionAction: @Composable () -> Unit,
    selectEpisodeButton: @Composable BoxScope.() -> Unit,
    rating: @Composable () -> Unit,
    onCoverImageSuccess: (AsyncImagePainter.State.Success) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (currentWindowAdaptiveInfo1().isWidthAtLeastMedium) {
        SubjectDetailsHeaderWide(
            info?.subjectId,
            coverImageUrl = coverImageUrl,
            title = {
                Text(
                    info?.displayName ?: "",
                    /*Modifier.useSharedTransitionScope { modifier, animatedVisibilityScope ->
                        modifier.sharedElement(
                            rememberSharedContentState(SharedTransitionKeys.subjectTitle(info.subjectId)),
                            animatedVisibilityScope,
                        )
                    },*/
                )
            },
            seasonTags = {
                seasonTags()
            },
            collectionData = collectionData,
            collectionAction = collectionAction,
            selectEpisodeButton = selectEpisodeButton,
            rating = rating,
            onCoverImageSuccess = onCoverImageSuccess,
            modifier = modifier,
        )
    } else {
        SubjectDetailsHeaderCompact(
            info?.subjectId,
            coverImageUrl = coverImageUrl,
            title = { Text(info?.displayName ?: "") },
            subtitle = { Text(info?.name ?: "") },
            seasonTags = { seasonTags() },
            collectionData = collectionData,
            collectionAction = collectionAction,
            selectEpisodeButton = selectEpisodeButton,
            rating = rating,
            onSuccess = onCoverImageSuccess,
            modifier = modifier,
        )
    }
}


// 适合手机, 窄
@Composable
fun SubjectDetailsHeaderCompact(
    subjectId: Int?,
    coverImageUrl: String?,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    seasonTags: @Composable () -> Unit,
    collectionData: @Composable () -> Unit,
    collectionAction: @Composable () -> Unit,
    selectEpisodeButton: @Composable BoxScope.() -> Unit,
    rating: @Composable () -> Unit,
    onSuccess: (AsyncImagePainter.State.Success) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
            val imageWidth = 140.dp

            Box(Modifier.clip(MaterialTheme.shapes.medium)) {
                AsyncImage(
                    ImageRequest.Builder(LocalPlatformContext.current)
                        .data(coverImageUrl)
                        .memoryCacheKey(subjectId?.toString())
                        .placeholderMemoryCacheKey(subjectId?.toString())
                        .crossfade(300)
                        .build(),
                    null,
                    Modifier
                        .width(imageWidth)
                        .height(imageWidth / COVER_WIDTH_TO_HEIGHT_RATIO),
                    contentScale = ContentScale.Crop,
                    onSuccess = onSuccess,
                )
            }

            Column(
                Modifier.weight(1f, fill = true)
                    .padding(horizontal = 12.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth(), // required by Rating
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    var showSubtitle by remember { mutableStateOf(false) }
                    Box(Modifier.clickable { showSubtitle = !showSubtitle }) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                            if (showSubtitle) {
                                subtitle()
                            } else {
                                title()
                            }
                        }
                    }

                    ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                        seasonTags()
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        Modifier.requiredHeight(IntrinsicSize.Max).align(Alignment.End),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        rating()
                    }
                }
            }
        }

        Row(
            Modifier.padding(top = 16.dp).align(Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom,
            ) {
                collectionData()
            }
            collectionAction()
        }

        Box(Modifier.paddingIfNotEmpty(top = 8.dp), contentAlignment = Alignment.CenterEnd) {
            selectEpisodeButton()
        }
    }
}

@Composable
fun SubjectDetailsHeaderWide(
    subjectId: Int?,
    coverImageUrl: String?,
    title: @Composable () -> Unit,
    seasonTags: @Composable RowScope.() -> Unit,
    collectionData: @Composable () -> Unit,
    collectionAction: @Composable () -> Unit,
    selectEpisodeButton: @Composable BoxScope.() -> Unit,
    rating: @Composable () -> Unit,
    onCoverImageSuccess: (AsyncImagePainter.State.Success) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            val imageWidth = 220.dp

            Box(Modifier.clip(MaterialTheme.shapes.medium)) {
                AsyncImage(
                    ImageRequest.Builder(LocalPlatformContext.current)
                        .data(coverImageUrl)
                        .memoryCacheKey(subjectId?.toString())
                        .placeholderMemoryCacheKey(subjectId?.toString())
                        .crossfade(300)
                        .build(),
                    null,
                    Modifier
                        .width(imageWidth)
                        .height(imageWidth / COVER_WIDTH_TO_HEIGHT_RATIO),
                    contentScale = ContentScale.Crop,
                    onSuccess = onCoverImageSuccess,
                )
            }

            Column(
                Modifier
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier.weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                            SelectionContainer {
                                title()
                            }
                        }
                    }
                    ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        ) {
                            seasonTags()
                        }
                    }
                    Spacer(Modifier.weight(1f)) // spacedBy applies
                    Row(Modifier) {
                        rating()
                    }
                }
                Row(Modifier.align(Alignment.Start)) {
                    collectionData()
                }
                Row(
                    Modifier.align(Alignment.Start),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    collectionAction()
                }
                Row(
                    Modifier.align(Alignment.Start),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(IntrinsicSize.Min)) {
                        selectEpisodeButton()
                    }
                }
            }
        }
    }
}

@OptIn(TestOnly::class)
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

@OptIn(TestOnly::class)
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

@OptIn(TestOnly::class)
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

@OptIn(TestOnly::class)
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
