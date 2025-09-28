/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.followed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.hasNewEpisodeToPlay
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.layout.*
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.widgets.NsfwMask
import me.him188.ani.app.ui.search.*
import me.him188.ani.app.ui.subject.SubjectProgressState

// https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Animeko?node-id=62-4581&node-type=frame&t=Evw0PwXZHXQNgEm3-0
@Composable
fun FollowedSubjectsLazyRow(
    items: LazyPagingItems<FollowedSubjectInfo>, // null means placeholder
//    items: List<FollowedSubjectInfo?>, // null means placeholder
    onClick: (FollowedSubjectInfo) -> Unit,
    onPlay: (FollowedSubjectInfo) -> Unit,
    modifier: Modifier = Modifier,
    layoutParameters: FollowedSubjectsLayoutParameters = FollowedSubjectsDefaults.layoutParameters(
        currentWindowAdaptiveInfo1(),
    ),
    lazyListState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalAlignment: Alignment.Vertical = Alignment.Top,
) {
    LazyRow(
        modifier,
        lazyListState,
        contentPadding,
        horizontalArrangement = layoutParameters.horizontalArrangement,
        verticalAlignment = verticalAlignment,
    ) {
//        itemsIndexed(
//            items,
//            key = { index, item -> item?.subjectInfo?.subjectId ?: -index },
//            contentType = { _, item -> item?.subjectProgressInfo?.hasNewEpisodeToPlay == true },
//        ) { _, item ->
        when {
            items.isLoadingFirstPage -> {
                // placeholders
                items(8) {
                    FollowedSubjectItem(
                        null,
                        onClick = { },
                        onPlay = { },
                        layoutParameters.imageSize,
                        layoutParameters.shape,
                    )
                }
            }

            items.loadState.hasError -> {
                item {
                    Box(Modifier.minimumHairlineSize()) {
                        val problem by items.rememberLoadErrorState()
                        LoadErrorCard(problem, { items.retry() })
                    }
                }
            }

            items.isFinishedAndEmpty -> {
                item {
                    Box(Modifier.minimumHairlineSize()) {
                        LoadErrorCardLayout(
                            LoadErrorCardRole.Unimportant,
                            content = {
                                ListItem(
                                    headlineContent = { Text("将番剧收藏为 \"在看\" 后将在这里显示") },
                                    colors = listItemColors,
                                )
                            },
                        )
                    }
                }
            }
        }
        items(
            items.itemCount,
            key = items.itemKey { "FollowedSubjectsLazyRow-" + it.subjectInfo.subjectId },
            contentType = items.itemContentType { it.subjectProgressInfo.hasNewEpisodeToPlay },
        ) { index ->
            val item = items[index]
            var subjectNsfwType: NsfwMode by rememberSaveable(item) {
                mutableStateOf(
                    item?.nsfwMode ?: NsfwMode.DISPLAY, // null 表示 placeholder, 不应该 blur
                )
            }

            NsfwMask(
                subjectNsfwType,
                onTemporarilyDisplay = { subjectNsfwType = NsfwMode.DISPLAY },
                shape = layoutParameters.shape,
            ) {
                FollowedSubjectItem(
                    item,
                    onClick = { item?.let { onClick(it) } },
                    onPlay = { item?.let { onPlay(it) } },
                    layoutParameters.imageSize,
                    layoutParameters.shape,
                )
            }
        }
    }
}

@Composable
private fun FollowedSubjectItem(
    item: FollowedSubjectInfo?, // null for placeholder
    onClick: () -> Unit,
    onPlay: () -> Unit,
    imageSize: DpSize,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    BasicCarouselItem(
        label = { CarouselItemDefaults.Text(item?.subjectInfo?.displayName ?: "") },
        modifier.placeholder(item == null, shape = shape),
        supportingText = {
            if (item != null) {
                val airingState = remember(item) {
                    SubjectProgressState(stateOf(item.subjectProgressInfo))
                }
                Text(airingState.buttonText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        maskShape = shape,
        overlay = {
            if (item?.subjectProgressInfo?.hasNewEpisodeToPlay == true) {
                FilledTonalIconButton(
                    onClick = { onPlay() },
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp))
                }
            }
        },
    ) {
        if (item != null) {
            val image = @Composable {
                AsyncImage(
                    item.subjectInfo.imageLarge,
                    modifier = Modifier.size(imageSize),
                    contentDescription = item.subjectInfo.displayName,
                    contentScale = ContentScale.Crop,
                )
            }
            Surface({ onClick() }, content = image)
        } else {
            Box(Modifier.size(imageSize))
        }
    }
}

@Immutable
data class FollowedSubjectsLayoutParameters(
    val imageSize: DpSize,
    val horizontalArrangement: Arrangement.Horizontal,
    val shape: Shape,
)

@Stable
object FollowedSubjectsDefaults {
    @Composable
    fun layoutParameters(windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo1()): FollowedSubjectsLayoutParameters {
        val windowSizeClass = windowAdaptiveInfo.windowSizeClass
        return FollowedSubjectsLayoutParameters(
            imageSize = imageSize(windowAdaptiveInfo),
            horizontalArrangement = when {
                windowSizeClass.isWidthAtLeastExpanded -> Arrangement.spacedBy(16.dp)
                windowSizeClass.isWidthAtLeastMedium -> Arrangement.spacedBy(12.dp)
                else -> Arrangement.spacedBy(8.dp)
            },
            shape = MaterialTheme.shapes.large,
        )
    }

    private fun imageSize(windowAdaptiveInfo: WindowAdaptiveInfo): DpSize {
        val windowSizeClass = windowAdaptiveInfo.windowSizeClass
        val baseSize = when {
            windowSizeClass.isHeightAtLeastMedium && windowSizeClass.isWidthAtLeastExpanded -> 160.dp
            windowSizeClass.isHeightAtLeastMedium && windowSizeClass.isWidthAtLeastMedium -> 140.dp
            else -> 120.dp
        }
        return DpSize(baseSize, (baseSize) / 9 * 16)
    }
}
