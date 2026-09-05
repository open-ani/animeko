/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Recommend
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.data.models.subject.nameCn
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.danmaku.ui.DanmakuPresentation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 浮出面板种类 (atv-architecture.md §8.3 面板 ×5). [wide] 区分文本面板 420dp / 卡片面板 240dp.
 */
internal enum class TvPlayerPanel(val title: String, val icon: ImageVector, val wide: Boolean) {
    Recommendations("相关推荐", Icons.Rounded.Recommend, false),
    Staff("制作人员", Icons.Rounded.Groups, false),
    Characters("角色", Icons.Rounded.Face, false),
    Comments("评论", Icons.Rounded.Comment, true),
    DanmakuList("弹幕列表", Icons.Rounded.FormatListBulleted, true),
}

/** 浮出面板样式 (附录 A: 面板 420/240dp 宽 max 300dp; 玻璃条目 black 55%/80%). */
internal object TvPlayerPanelDefaults {
    /** 文本面板宽 (评论/弹幕列表). */
    val WideWidth: Dp = 420.dp

    /** 卡片面板宽 (推荐/Staff/角色). */
    val NarrowWidth: Dp = 240.dp

    /** 面板最大高度. */
    val MaxHeight: Dp = 300.dp

    /** 玻璃条目底色. */
    val ItemContainer: Color = Color.Black.copy(alpha = 0.55f)

    /** 空态/提示条底色 (较深档). */
    val PlaceholderContainer: Color = Color.Black.copy(alpha = 0.80f)

    val ItemShape = RoundedCornerShape(10.dp)
}

/**
 * 浮出面板宿主 (§8.3): 胶囊行上方透明宿主, 玻璃条目列表吸底.
 *
 * 纯视图组件: 焦点接线由 Screen 注入 —— [panelModifier] 挂列表容器 (锚点 + 向下退出回胶囊),
 * [entryAnchorModifier] 挂入口条目 (第一条; 弹幕列表吸底, 入口即最新一条).
 * 数据为空时展示不可聚焦的占位条, 焦点留在胶囊行.
 */
@Composable
internal fun TvPlayerPanelHost(
    panel: TvPlayerPanel,
    relatedSubjects: List<RelatedSubjectInfo>,
    staff: List<RelatedPersonInfo>,
    characters: List<RelatedCharacterInfo>,
    comments: LazyPagingItems<EpisodeComment>,
    danmakuList: List<DanmakuPresentation>,
    panelModifier: Modifier,
    entryAnchorModifier: Modifier,
    onClickSubject: (RelatedSubjectInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val width = if (panel.wide) TvPlayerPanelDefaults.WideWidth else TvPlayerPanelDefaults.NarrowWidth
    val listModifier = panelModifier
        .width(width)
        .heightIn(max = TvPlayerPanelDefaults.MaxHeight)

    fun anchorFor(index: Int) = if (index == 0) entryAnchorModifier else Modifier

    when (panel) {
        TvPlayerPanel.Recommendations -> PanelList(listModifier, modifier, empty = relatedSubjects.isEmpty()) {
            itemsIndexed(relatedSubjects, key = { _, it -> it.subjectId }) { index, subject ->
                RelatedSubjectItem(subject, onClick = { onClickSubject(subject) }, modifier = anchorFor(index))
            }
        }

        TvPlayerPanel.Staff -> PanelList(listModifier, modifier, empty = staff.isEmpty()) {
            itemsIndexed(staff, key = { _, it -> "${it.personInfo.id}-${it.position.id}" }) { index, person ->
                StaffItem(person, modifier = anchorFor(index))
            }
        }

        TvPlayerPanel.Characters -> PanelList(listModifier, modifier, empty = characters.isEmpty()) {
            itemsIndexed(characters, key = { _, it -> it.character.id }) { index, character ->
                CharacterItem(character, modifier = anchorFor(index))
            }
        }

        TvPlayerPanel.Comments -> PanelList(
            listModifier, modifier,
            empty = comments.itemCount == 0,
            emptyText = if (comments.loadState.refresh is LoadState.Loading) "正在加载评论…" else "暂无评论",
        ) {
            items(comments.itemCount) { index ->
                comments[index]?.let { comment ->
                    CommentItem(comment, modifier = anchorFor(index))
                }
            }
        }

        TvPlayerPanel.DanmakuList -> PanelList(
            listModifier, modifier,
            empty = danmakuList.isEmpty(),
            emptyText = "还没有弹幕",
            reverseLayout = true, // 吸底: index 0 (最新) 画在底部
        ) {
            itemsIndexed(danmakuList, key = { index, it -> "${it.danmaku.id}-$index" }) { index, danmaku ->
                DanmakuItem(danmaku, modifier = anchorFor(index))
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
                .padding(bottom = 12.dp)
                .background(TvPlayerPanelDefaults.PlaceholderContainer, TvPlayerPanelDefaults.ItemShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f),
        )
    } else {
        LazyColumn(
            modifier = hostModifier.padding(bottom = 12.dp).then(listModifier),
            reverseLayout = reverseLayout,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

/** 玻璃条目 (black 55% 底, 聚焦白底黑内容反色, 与播放器系一致). */
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
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)),
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
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StaffItem(person: RelatedPersonInfo, modifier: Modifier = Modifier) {
    PanelItemSurface(onClick = {}, modifier) {
        AsyncImage(
            model = person.personInfo.imageMedium,
            contentDescription = person.personInfo.displayName,
            modifier = Modifier.size(32.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(
                person.personInfo.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            person.position.nameCn?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CharacterItem(related: RelatedCharacterInfo, modifier: Modifier = Modifier) {
    PanelItemSurface(onClick = {}, modifier) {
        AsyncImage(
            model = related.character.imageMedium,
            contentDescription = related.character.displayName,
            modifier = Modifier.size(32.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    related.character.displayName,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    related.role.nameCn,
                    Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            related.character.actors.firstOrNull()?.let { actor ->
                Text(
                    "CV ${actor.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                style = MaterialTheme.typography.bodySmall,
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
            style = MaterialTheme.typography.bodySmall,
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
