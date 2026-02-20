/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.TestRatingInfo
import me.him188.ani.app.data.models.subject.TestSelfRatingInfo
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeButton
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

@Immutable
data class PlayingEpisodeSummary(
    val episodeSort: EpisodeSort,
    val episodeName: String,
    val subjectName: String,
    val subjectTags: List<String>,
    val subjectCoverUrl: String,
    val rating: RatingInfo,
    val selfRatingInfo: SelfRatingInfo,
)

/**
 * https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Animeko?node-id=898-7207&t=BTaIuPfq1Qm7cTfJ-0
 */
@Composable
fun PlayingEpisodeSummaryRow(
    expanded: Boolean,
    summary: PlayingEpisodeSummary,
    onClickShare: () -> Unit,
    onClickDownload: () -> Unit,
    collectionButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
) {
    Surface(color = containerColor) {
        Column(modifier) {
            if (expanded) {
                // title
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "${summary.episodeSort}  ${summary.episodeName}",
                        softWrap = false,
                        style = MaterialTheme.typography.headlineSmall,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // subject info
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AsyncImage(
                        summary.subjectCoverUrl,
                        contentDescription = summary.subjectName,
                        Modifier.clip(MaterialTheme.shapes.medium).width(158.dp)
                            .height(233.dp), //.aspectRatio(9f / 16f),
                        contentScale = ContentScale.Crop,
                    )

                    Column(Modifier.weight(1f).height(IntrinsicSize.Min)) {
                        Row {
                            Column {
                                Text(
                                    summary.subjectName,
                                    softWrap = false,
                                    style = MaterialTheme.typography.titleLarge,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Text(
                                    summary.subjectTags.joinToString(" · "),
                                    style = MaterialTheme.typography.labelLarge,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Spacer(Modifier.weight(1f))
                            }

                            Spacer(Modifier.width(16.dp))

                            collectionButton()
                        }
                    }

                    Spacer(Modifier.width(24.dp))

                    Column {
                        // actions
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp, alignment = Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(16.dp, alignment = Alignment.CenterVertically),
                        ) {
                            TonalButtonWithIcon("分享", Icons.Rounded.Share, onClickShare)
                            TonalButtonWithIcon("下载", Icons.Rounded.Download, onClickDownload)
                        }
                    }
                }
            } else {
                // title
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        summary.subjectName,
                        softWrap = true,
                        maxLines = 2,
                        style = MaterialTheme.typography.titleMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FlowRow(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(0.dp, alignment = Alignment.Start),
                            verticalArrangement = Arrangement.spacedBy(0.dp, alignment = Alignment.CenterVertically),
                        ) {
                            TonalButtonWithIcon("分享", Icons.Rounded.Share, onClickShare)
                            TonalButtonWithIcon("下载", Icons.Rounded.Download, onClickDownload)
                        }

                        Box(Modifier.width(IntrinsicSize.Max)) {
                            collectionButton()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TonalButtonWithIcon(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick,
        modifier,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, softWrap = false)
    }
}

@TestOnly
val TestPlayingEpisodeSummary
    get() = PlayingEpisodeSummary(
        episodeSort = EpisodeSort(1),
        episodeName = "Episode 1",
        subjectName = "Subject Name",
        subjectTags = listOf("Tag1", "Tag2", "Tag3"),
        subjectCoverUrl = "https://example.com/image.jpg",
        rating = TestRatingInfo,
        selfRatingInfo = TestSelfRatingInfo,
    )

@OptIn(TestOnly::class)
@Preview(device = "spec:width=800dp,height=800dp,dpi=240")
@Composable
private fun PreviewPlayingEpisodeSummaryRow() {
    val scope = rememberCoroutineScope()
    PlayingEpisodeSummaryRow(
        expanded = true,
        summary = TestPlayingEpisodeSummary,
        {}, {},
        collectionButton = {
            EditableSubjectCollectionTypeButton(
                remember {
                    EditableSubjectCollectionTypeState(
                        flowOf(UnifiedCollectionType.NOT_COLLECTED),
                        { false },
                        {},
                        {},
                        scope,
                    )
                },
            )
        },
    )
}
