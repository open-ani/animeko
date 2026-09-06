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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.danmaku.ui.DanmakuPresentation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 浮出面板种类与内容宽度 (atv-architecture.md §8.3 功能药丸).
 */
enum class TvPlayerPanel(val title: String, val icon: ImageVector, val width: Dp) {
    Collection("收藏状态", Icons.Rounded.Bookmark, 248.dp),
    Recommendations("相关推荐", Icons.Rounded.Recommend, 360.dp),
    Comments("评论", Icons.Rounded.Comment, 400.dp),
    DanmakuSettings("弹幕设置", Icons.Rounded.Tune, 400.dp),
    VideoSettings("画质增强", Icons.Rounded.HighQuality, 400.dp),
    Together("一起看", Icons.Rounded.Groups, 360.dp),
}

/** 内容面板与交互面板共用实色底、圆角和焦点状态. */
internal object TvPlayerPanelDefaults {
    /** 面板最大高度. */
    val MaxHeight: Dp = TvPlayerSurfaceDefaults.PanelMaxHeight

    /** 内容条目底色. */
    val ItemContainer: Color = TvPlayerSurfaceDefaults.Raised

    val ItemShape = TvPlayerSurfaceDefaults.ItemShape
}

/**
 * 浮出面板宿主 (§8.3): 锚定对应药丸, 标题固定, 内容独立滚动.
 *
 * 纯视图组件: 焦点接线由 Screen 注入 —— [panelModifier] 挂列表容器 (锚点 + 向下退出回胶囊),
 * [entryAnchorModifier] 挂入口条目 (第一条).
 * 数据为空时展示不可聚焦的占位条, 焦点留在胶囊行.
 */
@Composable
internal fun TvPlayerPanelHost(
    panel: TvPlayerPanel,
    relatedSubjects: List<RelatedSubjectInfo>,
    comments: LazyPagingItems<EpisodeComment>?,
    panelModifier: Modifier,
    entryAnchorModifier: Modifier,
    onClickSubject: (RelatedSubjectInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listModifier = Modifier.fillMaxWidth()

    fun anchorFor(index: Int) = if (index == 0) entryAnchorModifier else Modifier

    TvPlayerPanelSurface(
        panel.title, panel.icon,
        modifier
            .then(panelModifier)
            .width(panel.width)
            .heightIn(max = TvPlayerPanelDefaults.MaxHeight),
    ) {
        when (panel) {
            TvPlayerPanel.Recommendations -> PanelList(listModifier, Modifier, empty = relatedSubjects.isEmpty()) {
                itemsIndexed(relatedSubjects, key = { _, it -> it.subjectId }) { index, subject ->
                    RelatedSubjectItem(subject, onClick = { onClickSubject(subject) }, modifier = anchorFor(index))
                }
            }

            TvPlayerPanel.Comments -> if (comments != null) PanelList(
                listModifier, Modifier,
                empty = comments.itemCount == 0,
                emptyText = if (comments.loadState.refresh is LoadState.Loading) "正在加载评论…" else "暂无评论",
            ) {
                items(comments.itemCount) { index ->
                    comments[index]?.let { comment ->
                        CommentItem(comment, modifier = anchorFor(index))
                    }
                }
            }

            else -> Unit // Interactive option panels have their own state/intent-only renderer.
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
    content: LazyListScope.() -> Unit,
) {
    if (empty) {
        Text(
            emptyText,
            hostModifier
                .padding(horizontal = 16.dp, vertical = 20.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f),
        )
    } else {
        LazyColumn(
            modifier = hostModifier.then(listModifier),
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
        shape = ClickableSurfaceDefaults.shape(TvPlayerPanelDefaults.ItemShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvPlayerPanelDefaults.ItemContainer,
            focusedContainerColor = TvPlayerControlsDefaults.FocusedContainer,
            contentColor = TvPlayerControlsDefaults.Content,
            focusedContentColor = TvPlayerControlsDefaults.FocusedContent,
        ),
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
private fun RelatedSubjectItem(
    subject: RelatedSubjectInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelItemSurface(onClick, modifier) {
        AsyncImage(
            model = subject.image,
            contentDescription = subject.displayName,
            modifier = Modifier
                .size(width = 40.dp, height = 56.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(
                subject.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subject.relation?.let {
                Text(
                    when (it) {
                        SubjectRelation.SEQUEL -> "续集"
                        SubjectRelation.PREQUEL -> "前传"
                        SubjectRelation.DERIVED -> "衍生"
                        SubjectRelation.SPECIAL -> "番外"
                    },
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = .7f),
                )
            }
        }
    }
}

@Composable
private fun CommentItem(comment: EpisodeComment, modifier: Modifier = Modifier) {
    Surface(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(TvPlayerPanelDefaults.ItemShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvPlayerPanelDefaults.ItemContainer,
            focusedContainerColor = TvPlayerControlsDefaults.FocusedContainer,
            contentColor = TvPlayerControlsDefaults.Content,
            focusedContentColor = TvPlayerControlsDefaults.FocusedContent,
        ),
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
        }
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 去掉 BBCode 标记 (评论只读简化展示; 富文本渲染留 M5 之后). */
private val BBCODE_TAG_REGEX = Regex("""\[/?[a-zA-Z][^\[\]]{0,64}?]""")

private fun cleanCommentText(raw: String): String = raw.replace(BBCODE_TAG_REGEX, "").trim()

private fun formatCommentDate(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMillis))
