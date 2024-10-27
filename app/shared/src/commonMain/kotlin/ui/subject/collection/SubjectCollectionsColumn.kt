/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.data.models.episode.EpisodeCollection
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectCollection
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.subject.collection.components.AiringLabel
import me.him188.ani.app.ui.subject.collection.components.AiringLabelState
import me.him188.ani.app.ui.subject.collection.components.EditCollectionTypeDropDown
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.details.components.COVER_WIDTH_TO_HEIGHT_RATIO
import me.him188.ani.app.ui.subject.rating.TestSelfRatingInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

@Stable
class SubjectCollectionColumnState(
    cachedData: State<List<SubjectCollection>>,
    hasMore: State<Boolean>,
    isKnownAuthorizedAndEmpty: State<Boolean>,
    private val onRequestMore: suspend () -> Unit,
    private val onAutoRefresh: suspend () -> Unit,
    private val onManualRefresh: suspend () -> Unit,
    backgroundScope: CoroutineScope,
) {
    private val requestMoreTasker = MonoTasker(backgroundScope)
    private val refreshTasker = MonoTasker(backgroundScope)

    val cachedData: List<SubjectCollection> by cachedData
    val hasMore by hasMore

    /**
     * 如果未登录, 此属性会一直未 false
     */
    val isKnownAuthorizedAndEmpty by isKnownAuthorizedAndEmpty

    internal val gridState = LazyGridState()

    fun requestMore() {
        if (requestMoreTasker.isRunning) return
        requestMoreTasker.launch {
            onRequestMore()
        }
    }

    val isRefreshing get() = refreshTasker.isRunning

    fun startAutoRefresh() {
        requestMoreTasker.cancel()
        refreshTasker.launch {
            onAutoRefresh()
        }
    }

    suspend fun manualRefresh() {
        requestMoreTasker.cancel()
        refreshTasker.launch {
            onManualRefresh()
        }.join()
    }
}

/**
 * Lazy column of [item]s, designed for My Collections.
 *
 * 自带一圈 padding
 *
 * @param item composes each item. See [SubjectCollection]
 */
@Composable
fun SubjectCollectionsColumn(
    state: SubjectCollectionColumnState,
    item: @Composable (item: SubjectCollection) -> Unit,
    modifier: Modifier = Modifier,
    enableAnimation: Boolean = true,
    allowProgressIndicator: Boolean = true,
) {
    val isCompact = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
    val spacedBy = if (isCompact) 16.dp else 24.dp

    LazyVerticalGrid(
        GridCells.Adaptive(360.dp),
        modifier,
        state.gridState,
        verticalArrangement = Arrangement.spacedBy(spacedBy),
        horizontalArrangement = Arrangement.spacedBy(spacedBy),
        contentPadding = PaddingValues(horizontal = spacedBy),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(1.dp)) } // 添加新 item 时保持到顶部

        items(state.cachedData, key = { it.subjectId }) { collection ->
            Box(Modifier.ifThen(enableAnimation) { animateItem() }) {
                item(collection)
            }
        }

        if (state.hasMore) {
            item("dummy loader", span = { GridItemSpan(maxLineSpan) }) {
                if (allowProgressIndicator) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Spacer(Modifier.height(1.dp))
                }
                state.requestMore()
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(1.dp)) }
    }
}

/**
 * 追番列表的一个条目卡片
 *
 * @param onClick on clicking this card (background)
 */
@Composable
fun SubjectCollectionItem(
    item: SubjectCollection,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    onClick: () -> Unit,
    onShowEpisodeList: () -> Unit,
    playButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 148.dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    colors: CardColors = CardDefaults.cardColors(),
) {
    Card(
        onClick,
        modifier.clip(shape).fillMaxWidth().height(height),
        shape = shape,
        colors = colors,
    ) {
        Row(Modifier.weight(1f, fill = false)) {
            AsyncImage(
                item.info.imageCommon,
                contentDescription = null,
                modifier = Modifier
                    .height(height).width(height * COVER_WIDTH_TO_HEIGHT_RATIO),
                contentScale = ContentScale.Crop,
            )

            Box(Modifier.weight(1f)) {
                SubjectCollectionItemContent(
                    item = item,
                    editableSubjectCollectionTypeState = editableSubjectCollectionTypeState,
                    onShowEpisodeList = onShowEpisodeList,
                    playButton = playButton,
                    Modifier.padding(start = 12.dp).fillMaxSize(),
                )
            }
        }
    }
}

/**
 * 追番列表的一个条目卡片的内容
 */
@Composable
private fun SubjectCollectionItemContent(
    item: SubjectCollection,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    onShowEpisodeList: () -> Unit,
    playButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // 标题和右上角菜单
        Row(
            Modifier.fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.displayName,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            Box {
                IconButton(
                    { editableSubjectCollectionTypeState.showDropdown = true },
                    Modifier.fillMaxHeight().padding(),
                ) {
                    Icon(Icons.Outlined.MoreVert, null, Modifier.size(24.dp))
                }

                EditCollectionTypeDropDown(editableSubjectCollectionTypeState)
            }
        }

        Row(
            Modifier.padding(top = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 连载至第 28 话 · 全 34 话
            AiringLabel(
                remember(item) {
                    AiringLabelState(stateOf(item.airingInfo), stateOf(item.progressInfo))
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(Modifier.weight(1f))


        Row(
            Modifier
                .padding(vertical = 12.dp)
                .padding(horizontal = 12.dp)
                .align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onShowEpisodeList) {
                Text("选集")
            }

            Box(Modifier.width(IntrinsicSize.Min)) { playButton() }
        }
    }
}


@TestOnly
val TestSubjectCollections
    get() = buildList {
        var id = 0
        val eps = listOf(
            EpisodeCollection(
                episodeInfo = EpisodeInfo(
                    episodeId = 6385,
                    name = "Diana Houston",
                    nameCn = "Nita O'Donnell",
                    comment = 5931,
                    duration = "",
                    desc = "gubergren",
                    disc = 2272,
                    sort = EpisodeSort(1),
                    ep = EpisodeSort(1),
                ),
                collectionType = UnifiedCollectionType.DONE,
            ),
            EpisodeCollection(
                episodeInfo = EpisodeInfo(
                    episodeId = 6386,
                    name = "Diana Houston",
                    nameCn = "Nita O'Donnell",
                    sort = EpisodeSort(2),
                    comment = 5931,
                    duration = "",
                    desc = "gubergren",
                    disc = 2272,
                    ep = EpisodeSort(2),
                ),
                collectionType = UnifiedCollectionType.DONE,
            ),

            )
        add(
            testSubjectCollection(++id, eps, UnifiedCollectionType.DOING),
        )
        add(
            testSubjectCollection(++id, eps, UnifiedCollectionType.DOING),
        )
        add(
            testSubjectCollection(++id, eps, UnifiedCollectionType.DOING),
        )
        add(
            testSubjectCollection(++id, eps, collectionType = UnifiedCollectionType.WISH),
        )
        repeat(10) {
            add(
                testSubjectCollection(
                    ++id,
                    episodes = eps + EpisodeCollection(
                        episodeInfo = EpisodeInfo(
                            episodeId = 6386,
                            name = "Diana Houston",
                            nameCn = "Nita O'Donnell",
                            sort = EpisodeSort(2),
                            comment = 5931,
                            duration = "",
                            desc = "gubergren",
                            disc = 2272,
                            ep = EpisodeSort(2),
                        ),
                        collectionType = UnifiedCollectionType.DONE,
                    ),
                    collectionType = UnifiedCollectionType.WISH,
                ),
            )
        }
    }

@TestOnly
@Composable
internal fun rememberTestSubjectCollectionColumnState(
    cachedData: List<SubjectCollection> = TestSubjectCollections,
    hasMore: Boolean = false,
    isKnownEmpty: Boolean = false,
): SubjectCollectionColumnState {
    val scope = rememberCoroutineScope()
    return remember {
        SubjectCollectionColumnState(
            cachedData = mutableStateOf(cachedData),
            hasMore = mutableStateOf(hasMore),
            isKnownAuthorizedAndEmpty = mutableStateOf(isKnownEmpty),
            onRequestMore = {},
            onAutoRefresh = {},
            onManualRefresh = {},
            backgroundScope = scope,
        )
    }
}

@TestOnly
private fun testSubjectCollection(
    id: Int,
    episodes: List<EpisodeCollection>,
    collectionType: UnifiedCollectionType,
) = SubjectCollection(
    info = SubjectInfo.Empty.copy(
        id,
        nameCn = "中文条目名称",
        name = "Subject Name",
    ),
    episodes = episodes,
    collectionType = collectionType,
    selfRatingInfo = TestSelfRatingInfo,
)
