/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.danmaku.ui.DanmakuPresentation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 浮出面板种类与内容宽度 (atv-architecture.md §8.3 功能药丸).
 */
enum class TvPlayerPanelPresentation { Popup, Sidebar }

enum class TvPlayerPanel(
    val title: String,
    val icon: ImageVector,
    val width: Dp,
    val presentation: TvPlayerPanelPresentation = TvPlayerPanelPresentation.Popup,
) {
    Collection("收藏状态", Icons.Rounded.Bookmark, 248.dp),
    Comments("评论", Icons.Rounded.Comment, 400.dp, TvPlayerPanelPresentation.Sidebar),
    DanmakuSettings("弹幕设置", Icons.Rounded.Tune, 400.dp, TvPlayerPanelPresentation.Sidebar),
    VideoSettings("画质增强", Icons.Rounded.AutoAwesome, 400.dp),
    Together("一起看", Icons.Rounded.Groups, 360.dp, TvPlayerPanelPresentation.Sidebar),
}

/**
 * Sidebar list content. The host supplies independent scroll state and owns navigation.
 */
@Composable
internal fun TvPlayerComments(
    comments: LazyPagingItems<EpisodeComment>?,
    entryAnchorModifier: Modifier,
    onClickComment: (EpisodeComment, Int) -> Unit,
    commentAnchor: (EpisodeComment) -> Modifier,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val listModifier = Modifier.fillMaxWidth()

    fun anchorFor(index: Int) = if (index == 0) entryAnchorModifier else Modifier

    Column(modifier) {
        if (comments != null) PanelList(
            listModifier, Modifier,
            empty = comments.itemCount == 0,
            emptyText = if (comments.loadState.refresh is LoadState.Loading) "正在加载评论…" else "暂无评论",
            state = listState,
        ) {
            items(comments.itemCount, key = { comments.peek(it)?.stableId ?: "placeholder-$it" }) { index ->
                comments[index]?.let { comment ->
                    CommentItem(comment, modifier = anchorFor(index).then(commentAnchor(comment))) {
                        onClickComment(comment, index)
                    }
                }
            }
            if (comments.loadState.append is LoadState.Error) item {
                TvOptionRow("加载评论失败，重试") { comments.retry() }
            }
        }
    }
}

@Composable
internal fun TvDanmakuListDialog(
    danmakuList: List<DanmakuPresentation>,
    entryModifier: Modifier,
) {
    if (danmakuList.isEmpty()) {
        TvOptionRow("还没有弹幕", modifier = entryModifier, onClick = {})
    } else {
        PanelList(Modifier.fillMaxWidth(), Modifier, empty = false, reverseLayout = true) {
            itemsIndexed(danmakuList, key = { index, it -> "${it.danmaku.id}-$index" }) { index, danmaku ->
                DanmakuItem(danmaku, modifier = if (index == 0) entryModifier else Modifier)
            }
        }
    }
}

@Composable
private fun PanelList(
    listModifier: Modifier,
    hostModifier: Modifier,
    empty: Boolean,
    emptyText: String = "暂无内容",
    reverseLayout: Boolean = false,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val colors = LocalTvPlayerSurfaceColors.current
    if (empty) {
        Text(
            emptyText,
            hostModifier
                .padding(horizontal = 16.dp, vertical = 20.dp),
            style = MaterialTheme.typography.labelLarge,
            color = colors.muted,
        )
    } else {
        LazyColumn(
            modifier = hostModifier.then(listModifier).tvPanelScrollEdges(state, colors.container),
            state = state,
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
            reverseLayout = reverseLayout,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** 内容条目: 聚焦后反色, 与交互面板共用视觉状态. */
@Composable
private fun PanelItemSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(TvPlayerSurfaceDefaults.ItemShape),
        colors = tvPlayerOptionColors(filled = true),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun CommentItem(comment: EpisodeComment, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(TvPlayerSurfaceDefaults.ItemShape),
        colors = tvPlayerOptionColors(filled = true),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.author?.nickname?.takeIf { it.isNotBlank() } ?: "匿名",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    remember(comment.createdAt) { formatCommentDate(comment.createdAt) },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                remember(comment.content) { cleanCommentText(comment.content) },
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Text("查看全文", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** A single reading focus target; vertical keys scroll the entire text without truncation. */
@Composable
internal fun TvCommentDetail(comment: EpisodeComment, modifier: Modifier = Modifier) {
    val colors = LocalTvPlayerSurfaceColors.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 96.dp.toPx() }
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionDown -> if (scroll.canScrollForward) 1 else 0
                    Key.DirectionUp -> if (scroll.canScrollBackward) -1 else 0
                    else -> 0
                }
                if (direction == 0) false else {
                    if (event.type == KeyEventType.KeyDown) scope.launch { scroll.scrollBy(step * direction) }
                    true
                }
            }
            .border(
                1.dp,
                if (focused) colors.focusedContainer else Color.Transparent,
                TvPlayerSurfaceDefaults.ItemShape
            )
            .focusable()
            .verticalScroll(scroll)
            .padding(16.dp)
            .testTag("tv-comment-full-text"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(comment.author?.nickname ?: "匿名", style = MaterialTheme.typography.titleMedium, color = colors.content)
        Text(
            formatCommentDate(comment.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted
        )
        Text(cleanCommentText(comment.content), style = MaterialTheme.typography.bodyLarge, color = colors.content)
    }
}

@Composable
private fun DanmakuItem(danmaku: DanmakuPresentation, modifier: Modifier = Modifier) {
    PanelItemSurface(onClick = {}, modifier) {
        Text(
            formatTime(danmaku.danmaku.playTimeMillis),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            danmaku.danmaku.text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 去掉 BBCode 标记 (评论只读简化展示; 富文本渲染留 M5 之后). */
private val BBCODE_TAG_REGEX = Regex("""\[/?[a-zA-Z][^\[\]]{0,64}?]""")

private fun cleanCommentText(raw: String): String = raw.replace(BBCODE_TAG_REGEX, "").trim()

private fun formatCommentDate(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMillis))
