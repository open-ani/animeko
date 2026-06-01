/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.rating.FiveRatingStars

/**
 * "查看全部"类内容的 TV 大弹窗: 标题 (+可选右侧动作) + 自适应卡片网格 (方向键导航,
 * 默认 BringIntoView 滚动, 返回键关闭). 替代移动端的 ModalBottomSheet.
 *
 * 打开时自动聚焦第一格 (Dialog 独立焦点域, 不聚焦则方向键无处可去; 等分页数据与
 * 卡片组合出来再请求): [itemContent] 的 modifier 参数在首格带焦点请求器, 必须应用
 * 到条目根 (或可聚焦的容器) 上.
 */
@Composable
internal fun <T : Any> ViewAllGridDialog(
    title: String,
    items: LazyPagingItems<T>,
    onDismissRequest: () -> Unit,
    cellMinWidth: Dp = 280.dp,
    /** 非 null 时固定列数 (卡宽 = 网格均分); null 按 [cellMinWidth] 自适应分列. */
    columns: Int? = null,
    headerAction: @Composable () -> Unit = {},
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val firstItemFocus = remember { FocusRequester() }
    // 到位确认标志: requestFocus 被焦点系统拒绝时静默失败 (runCatching 照样报成功),
    // 必须以"首格真的拿到焦点"为准, 否则假成功提前退出后弹窗内无焦点, 方向键死区
    var firstItemFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // attempts 60: 首格要等分页数据到达才组合, 比纯组合时序慢
        resolveFocusRepeatedly(attempts = 60, arrived = { firstItemFocused }) {
            if (items.itemCount > 0) {
                runCatching { firstItemFocus.requestFocus() }
            }
        }
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.fillMaxWidth(TV_DETAILS_DIALOG_WIDTH_FRACTION)
                .fillMaxHeight(TV_DETAILS_DIALOG_HEIGHT_FRACTION),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    headerAction()
                }
                LazyVerticalGrid(
                    if (columns != null) GridCells.Fixed(columns) else GridCells.Adaptive(minSize = cellMinWidth),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items.itemCount,
                        key = items.itemKey(),
                        contentType = items.itemContentType(),
                    ) { index ->
                        items[index]?.let { item ->
                            itemContent(
                                item,
                                Modifier.ifThen(index == 0) {
                                    focusRequester(firstItemFocus)
                                        .onFocusChanged { if (it.hasFocus) firstItemFocused = true }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 聚焦卡容器: 包住本身带 clickable 的移动端条目 (如 PersonCard), 使其在 TV 网格中
 * 呈卡片形态 —— 子树获得焦点时主题色描边示焦 (容器底色不变, 不做提亮"背光").
 * 自身不可聚焦, 焦点在内部条目上.
 *
 * 内部条目的 clickable 自带涟漪指示器, 其焦点状态层会在卡里再画一块小尺寸高亮
 * (与描边叠成两层特效); 这里整体禁用内部指示器, 示焦只由卡容器描边承担
 * (与播放器面板条目 TvPanelItem 的单层效果一致).
 */
@Composable
internal fun FocusHighlightCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier.onFocusChanged { focused = it.hasFocus },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = TV_CARD_CONTAINER_ALPHA),
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(10.dp)) {
            CompositionLocalProvider(LocalIndication provides NoIndication) {
                content()
            }
        }
    }
}

/** 无绘制的指示器: 条目仍可聚焦/点击, 只是不画自己的焦点/按压状态层. */
private data object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node() {}
}

/**
 * 评论列表的 TV 大弹窗 (条目评价 / 人物评论共用): 标题 (+可选"写评价"动作) + 评论卡片网格.
 * 卡片为紧凑纯文本形态 (头像 + 昵称 + 日期 + 评分星 + 正文摘要), 点击展开/收起全文;
 * 富文本图片/贴纸在 TV 上略过 (与播放器评论面板一致).
 */
@Composable
internal fun CommentsGridDialog(
    title: String,
    comments: LazyPagingItems<UIComment>,
    onDismissRequest: () -> Unit,
    showRating: Boolean,
    headerAction: @Composable () -> Unit = {},
) {
    ViewAllGridDialog(
        title = title,
        items = comments,
        onDismissRequest = onDismissRequest,
        cellMinWidth = COMMENT_GRID_CARD_MIN_WIDTH,
        headerAction = headerAction,
    ) { comment, modifier ->
        CommentGridCard(comment, showRating = showRating, modifier = modifier)
    }
}

/** 单条评论卡: 聚焦高亮, 确认键展开/收起全文. */
@Composable
private fun CommentGridCard(
    comment: UIComment,
    showRating: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = { expanded = !expanded },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (focused) {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = TV_CARD_CONTAINER_FOCUSED_ALPHA)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = TV_CARD_CONTAINER_ALPHA)
        },
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        interactionSource = interactionSource,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvatarImage(
                    comment.author?.avatarUrl,
                    Modifier.size(24.dp).clip(CircleShape),
                )
                Text(
                    comment.author?.nickname ?: comment.author?.id?.toString() ?: "",
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatDateTime(comment.createdAt, showTime = false),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (showRating) {
                    comment.rating?.takeIf { it > 0 }?.let { rating ->
                        FiveRatingStars(rating, starSize = 12.dp)
                    }
                }
            }
            Text(
                remember(comment) { comment.content.toPlainText() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else TV_COMMENT_COLLAPSED_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** TV 详情弹窗宽/高占屏比例 (与人物"查看全部"弹窗一致). */
private const val TV_DETAILS_DIALOG_WIDTH_FRACTION = 0.85f
private const val TV_DETAILS_DIALOG_HEIGHT_FRACTION = 0.88f

/** TV 卡片容器不透明度 (未聚焦/聚焦档): 半透明隐约透出下层背景, 不压住 backdrop. */
internal const val TV_CARD_CONTAINER_ALPHA = 0.45f
internal const val TV_CARD_CONTAINER_FOCUSED_ALPHA = 0.75f

/** 评论卡最小宽度 (自适应分列, 通常两列). */
private val COMMENT_GRID_CARD_MIN_WIDTH = 420.dp

/** 评论卡折叠态正文最大行数 (确认键展开全文). */
private const val TV_COMMENT_COLLAPSED_MAX_LINES = 5
