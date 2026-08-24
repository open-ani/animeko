/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_add_emoji
import me.him188.ani.app.ui.lang.comment_copied
import me.him188.ani.app.ui.lang.comment_dislike
import me.him188.ani.app.ui.lang.comment_expand_replies
import me.him188.ani.app.ui.lang.comment_like
import me.him188.ani.app.ui.lang.comment_more_actions
import me.him188.ani.app.ui.rating.FiveRatingStars
import me.him188.ani.app.ui.richtext.RichText
import me.him188.ani.app.ui.richtext.RichTextDefaults
import me.him188.ani.app.ui.richtext.UIRichElement
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

object CommentItemTestTags {
    const val LikeButton = "CommentItem:like"
    const val DislikeButton = "CommentItem:dislike"
    const val EmojiButton = "CommentItem:emoji"
    const val MoreButton = "CommentItem:more"
    const val Actions = "CommentItem:actions"
    const val RepliesBlock = "CommentItem:replies"
}

/**
 * 上下文菜单的回调. 均可为 `null` 表示隐藏对应菜单项.
 *
 * 「复制内容」由组件内部实现, 不需要外部处理.
 */
@Immutable
class CommentMenuHandlers(
    /**
     * 在来源平台打开原评论. 仅 [UICommentSource.BANGUMI] 来源的评论应传入非 `null` 值.
     */
    val onOpenOriginal: ((UIComment) -> Unit)? = null,
    /**
     * 拉黑评论作者 (本地屏蔽).
     */
    val onBlockAuthor: ((UIComment) -> Unit)? = null,
    /**
     * 举报评论.
     */
    val onReport: ((UIComment) -> Unit)? = null,
)

/**
 * 统一评论条目, 对应 Figma 设计 "CommentItem".
 *
 * 布局结构 (从上到下): 头行 (作者名 + 可选评分星) / 正文 / 贴纸回应行 / 简要回复块 / 底行 (时间 + 操作按钮组).
 *
 * 交互能力按 [UIComment.source] 决定:
 * - [UICommentSource.ANI]: 显示操作按钮组 (点赞/点踩/贴纸/更多), 点击整条评论回复.
 * - [UICommentSource.BANGUMI]: 只读, 无操作行, 时间带 "· Bangumi" 后缀, 长按或右键唤出菜单.
 *
 * @param comment 要展示的评论. 若使用 [CommentState], 传入 [CommentState.withOverlay] 的结果.
 * @param showRating 是否在头行右侧显示评分星 (条目评价).
 * @param reactionsClipped 贴纸行是否为列表模式 (最多一行, 溢出渐隐). 在独立 thread 页应传 `false` 以自动换行.
 * @param onClickReply 点击评论主体回复. 仅当评论可回复 ([UIComment.canReply]) 时生效.
 * @param onExpandReplies 点击 "展开 N 条回复". `null` 时回落到 [onClickReply].
 * @param onToggleVote 点击点赞/点踩按钮, 参数为按下的按钮. `null` 时隐藏投票按钮.
 * @param onToggleReaction 贴纸回应 (点击已有贴纸或从选择器中选择). `null` 时隐藏贴纸按钮并禁用贴纸点击.
 * @param menu 上下文菜单回调. `null` 时不显示菜单入口.
 */
@Composable
fun CommentItem(
    comment: UIComment,
    onClickUrl: (String) -> Unit,
    onClickImage: (String) -> Unit,
    modifier: Modifier = Modifier,
    showRating: Boolean = false,
    reactionsClipped: Boolean = true,
    onClickReply: ((UIComment) -> Unit)? = null,
    onExpandReplies: ((UIComment) -> Unit)? = null,
    onToggleVote: ((UIComment, UICommentVote) -> Unit)? = null,
    onToggleReaction: ((UIComment, String) -> Unit)? = null,
    menu: CommentMenuHandlers? = null,
    contentPadding: PaddingValues = CommentItemDefaults.ContentPadding,
) {
    val isAni = comment.source == UICommentSource.ANI
    val replyable = isAni && comment.canReply && onClickReply != null
    val showActions = isAni && (onToggleVote != null || onToggleReaction != null || menu != null)

    var showPressMenu by remember(comment.stableId) { mutableStateOf(false) }
    var showActionsMenu by remember(comment.stableId) { mutableStateOf(false) }
    var showReactionPicker by remember(comment.stableId) { mutableStateOf(false) }

    val toaster = LocalToaster.current
    val clipboard = LocalClipboard.current
    val uiScope = rememberCoroutineScope()
    val copiedText = stringResource(Lang.comment_copied)
    val onCopyContent: () -> Unit = {
        val text = comment.rawContent ?: comment.content.toPlainText()
        uiScope.launch {
            clipboard.setClipEntryText(text)
            toaster.toast(copiedText)
        }
    }

    val gestureModifier = Modifier
        .then(
            if (replyable) {
                Modifier.combinedClickable(
                    onLongClick = if (menu != null) {
                        { showPressMenu = true }
                    } else null,
                    onClick = { onClickReply?.invoke(comment) },
                )
            } else if (menu != null) {
                Modifier.pointerInput(comment.stableId) {
                    detectTapGestures(onLongPress = { showPressMenu = true })
                }
            } else Modifier,
        )
        .then(
            if (menu != null) {
                Modifier.pointerInput(comment.stableId) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                showPressMenu = true
                            }
                        }
                    }
                }
            } else Modifier,
        )

    Box(modifier) {
        CommentItemLayout(
            avatar = { CommentDefaults.Avatar(comment.author?.avatarUrl) },
            title = {
                Text(
                    text = comment.author?.nickname ?: comment.author?.id.toString(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            titleTrailing = if (showRating && (comment.rating ?: 0) > 0) {
                {
                    FiveRatingStars(comment.rating ?: 0, starSize = 12.dp)
                }
            } else null,
            content = {
                val scaledContent = remember(comment.content) {
                    comment.content.withDefaultFontSize(CommentItemDefaults.ContentFontSize)
                }
                RichText(
                    elements = scaledContent.elements,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    onClickUrl = onClickUrl,
                    onClickImage = onClickImage,
                )
            },
            reactions = if (comment.reactions.isNotEmpty()) {
                {
                    CommentItemDefaults.ReactionsRow(
                        reactions = comment.reactions,
                        onClickItem = onToggleReaction?.let { handler ->
                            { value -> handler(comment, value) }
                        },
                        clipped = reactionsClipped,
                    )
                }
            } else null,
            replies = if (comment.briefReplies.isNotEmpty()) {
                {
                    CommentItemDefaults.RepliesBlock(
                        replies = comment.briefReplies,
                        totalReplyCount = comment.replyCount,
                        onClickUrl = onClickUrl,
                        modifier = Modifier.testTag(CommentItemTestTags.RepliesBlock),
                        // 回落到 onClickReply 时沿用 replyable 守卫, 避免只读评论 (如 Bangumi 源) 的回复块可点
                        onClickExpand = (onExpandReplies ?: onClickReply?.takeIf { replyable })?.let { handler ->
                            { handler(comment) }
                        },
                    )
                }
            } else null,
            timestamp = {
                Text(
                    text = formatDateTime(comment.createdAt) +
                            if (!isAni) " · Bangumi" else "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            },
            actions = if (showActions) {
                {
                    if (onToggleVote != null) {
                        CommentItemDefaults.LikeButton(
                            count = comment.likeCount,
                            active = comment.selfVote == UICommentVote.LIKE,
                            onClick = { onToggleVote(comment, UICommentVote.LIKE) },
                            modifier = Modifier.testTag(CommentItemTestTags.LikeButton),
                        )
                        CommentItemDefaults.IconActionButton(
                            icon = if (comment.selfVote == UICommentVote.DISLIKE) {
                                Icons.Filled.ThumbDown
                            } else Icons.Outlined.ThumbDown,
                            contentDescription = stringResource(Lang.comment_dislike),
                            active = comment.selfVote == UICommentVote.DISLIKE,
                            onClick = { onToggleVote(comment, UICommentVote.DISLIKE) },
                            modifier = Modifier.testTag(CommentItemTestTags.DislikeButton),
                        )
                    }
                    if (onToggleReaction != null) {
                        Box {
                            CommentItemDefaults.IconActionButton(
                                icon = Icons.Outlined.Mood,
                                contentDescription = stringResource(Lang.comment_add_emoji),
                                onClick = { showReactionPicker = true },
                                modifier = Modifier.testTag(CommentItemTestTags.EmojiButton),
                            )
                            if (showReactionPicker) {
                                Popup(
                                    onDismissRequest = { showReactionPicker = false },
                                    properties = PopupProperties(focusable = true),
                                ) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        tonalElevation = 6.dp,
                                        shadowElevation = 6.dp,
                                        modifier = Modifier
                                            .width(216.dp)
                                            .heightIn(max = 280.dp),
                                    ) {
                                        CommentDefaults.ReactionPicker(
                                            onClickItem = {
                                                showReactionPicker = false
                                                onToggleReaction(comment, it)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (menu != null) {
                        Box {
                            CommentItemDefaults.IconActionButton(
                                icon = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(Lang.comment_more_actions),
                                onClick = { showActionsMenu = true },
                                modifier = Modifier.testTag(CommentItemTestTags.MoreButton),
                            )
                            CommentContextMenu(
                                expanded = showActionsMenu,
                                onDismissRequest = { showActionsMenu = false },
                                onCopyContent = onCopyContent,
                                onOpenOriginal = menu.onOpenOriginal?.let { handler -> { handler(comment) } },
                                onBlockAuthor = menu.onBlockAuthor?.let { handler -> { handler(comment) } },
                                onReport = menu.onReport?.let { handler -> { handler(comment) } },
                            )
                        }
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .then(gestureModifier)
                .padding(contentPadding),
        )

        if (menu != null) {
            // 长按/右键唤出的菜单, 锚定在头像右侧
            Box(Modifier.align(Alignment.TopStart).padding(start = 48.dp)) {
                CommentContextMenu(
                    expanded = showPressMenu,
                    onDismissRequest = { showPressMenu = false },
                    onCopyContent = onCopyContent,
                    onOpenOriginal = menu.onOpenOriginal?.let { handler -> { handler(comment) } },
                    onBlockAuthor = menu.onBlockAuthor?.let { handler -> { handler(comment) } },
                    onReport = menu.onReport?.let { handler -> { handler(comment) } },
                    offset = DpOffset(0.dp, (-24).dp),
                )
            }
        }
    }
}

/**
 * [CommentItem] 的纯布局版本, 所有内容都由 slot 提供, 方便测试与预览.
 *
 * 结构对应 Figma "CommentItem": 头像 36dp 在左, 右侧主列从上到下为
 * 头行 ([title] + [titleTrailing]) / [content] / [reactions] / [replies] / 底行 ([timestamp] + [actions]).
 */
@Composable
fun CommentItemLayout(
    avatar: @Composable () -> Unit,
    title: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
    timestamp: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    titleTrailing: (@Composable RowScope.() -> Unit)? = null,
    reactions: (@Composable () -> Unit)? = null,
    replies: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.clip(CircleShape)) {
            avatar()
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f, fill = false)) {
                    title()
                }
                titleTrailing?.invoke(this)
            }

            Box(Modifier.padding(vertical = 3.dp)) {
                content()
            }

            reactions?.invoke()

            replies?.invoke()

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f, fill = false)) {
                    timestamp()
                }
                if (actions != null) {
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                    ) {
                        Row(
                            Modifier.testTag(CommentItemTestTags.Actions),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            actions()
                        }
                    }
                }
            }
        }
    }
}

object CommentItemDefaults {
    val ContentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

    /**
     * 正文默认字号 (M3 body/medium). BBCode 中显式指定的字号会按比例缩放.
     */
    const val ContentFontSize: Float = 14f

    /**
     * 简要回复块字号 (M3 body/small).
     */
    const val ReplyFontSize: Float = 12f

    val ActionButtonHeight = 28.dp
    val ActionIconSize = 15.dp
    val StickerChipHeight = 24.dp
    val StickerSize = 16.dp

    /**
     * 贴纸 chip, 对应 Figma "StickerChip".
     *
     * 未贴: 透明底 + outlineVariant 描边; 已贴: secondaryContainer 实底无描边.
     */
    @Composable
    fun StickerChip(
        reaction: UICommentReaction,
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
    ) {
        val selected = reaction.selected
        val backgroundColor by animateColorAsState(
            if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        )
        val contentColor =
            if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        val border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        val shape = RoundedCornerShape(8.dp)

        val chipContent: @Composable () -> Unit = {
            Row(
                Modifier.height(StickerChipHeight).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val previewing = LocalIsPreviewing.current
                val reactionDrawableRes = reaction.value.removePrefix("bgm").toIntOrNull()
                    ?.let { BangumiCommentSticker[it] }
                if (previewing || reactionDrawableRes == null) Icon(
                    imageVector = Icons.Rounded.Face,
                    modifier = Modifier.size(StickerSize),
                    contentDescription = reaction.value,
                ) else Image(
                    painter = painterResource(reactionDrawableRes),
                    modifier = Modifier.size(StickerSize),
                    contentDescription = reaction.value,
                )
                Text(
                    text = reaction.count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }

        if (onClick != null) {
            Surface(
                onClick = onClick,
                modifier = modifier,
                shape = shape,
                color = backgroundColor,
                contentColor = contentColor,
                border = border,
            ) { chipContent() }
        } else {
            Surface(
                modifier = modifier,
                shape = shape,
                color = backgroundColor,
                contentColor = contentColor,
                border = border,
            ) { chipContent() }
        }
    }

    /**
     * 贴纸回应行, 对应 Figma "ReactionsBar". 仅在已有人贴过贴纸时展示.
     *
     * @param onClickItem 点击贴纸 toggle 跟贴/取消. `null` 表示只读.
     * @param clipped 列表模式: 最多一行占满, 溢出隐藏 + 右缘渐隐. `false` 时自动换行 (thread 页).
     */
    @Composable
    fun ReactionsRow(
        reactions: List<UICommentReaction>,
        modifier: Modifier = Modifier,
        onClickItem: ((value: String) -> Unit)? = null,
        clipped: Boolean = true,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            if (clipped) {
                val scrollState = rememberScrollState()
                val fadeWidth = 24.dp
                Row(
                    modifier
                        .fillMaxWidth()
                        .then(
                            // 渐隐要画在视口坐标系, 因此必须在 horizontalScroll 之前
                            if (scrollState.maxValue > 0) {
                                Modifier
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                0f to Color.Black,
                                                1f to Color.Transparent,
                                                startX = size.width - fadeWidth.toPx(),
                                                endX = size.width,
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    }
                            } else Modifier,
                        )
                        .horizontalScroll(scrollState, enabled = false),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    reactions.forEach { reaction ->
                        StickerChip(
                            reaction = reaction,
                            onClick = onClickItem?.let { handler -> { handler(reaction.value) } },
                        )
                    }
                }
            } else {
                FlowRow(
                    modifier,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    reactions.forEach { reaction ->
                        StickerChip(
                            reaction = reaction,
                            onClick = onClickItem?.let { handler -> { handler(reaction.value) } },
                        )
                    }
                }
            }
        }
    }

    /**
     * 操作行按钮容器, 对应 Figma "CommentActionButton": 高 28 圆角 14, 无底无框.
     *
     * @param active 自己是否已点过 (点赞/点踩). Active 时图标染 primary, 文本保持灰色.
     */
    @Composable
    fun ActionButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        active: Boolean = false,
        horizontalPadding: Dp = 9.dp,
        content: @Composable RowScope.() -> Unit,
    ) {
        Surface(
            onClick = onClick,
            modifier = modifier.height(ActionButtonHeight),
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            contentColor = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                Modifier.padding(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        }
    }

    /**
     * 点赞按钮: 图标 + 计数. 计数为 0 时不显示数字; 计数恒为灰色, 不随 [active] 变色.
     */
    @Composable
    fun LikeButton(
        count: Int,
        active: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        ActionButton(onClick = onClick, modifier = modifier, active = active) {
            Icon(
                imageVector = if (active) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                contentDescription = stringResource(Lang.comment_like),
                modifier = Modifier.size(ActionIconSize),
            )
            if (count > 0) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }

    /**
     * 仅图标的操作按钮 (点踩 / 贴纸 / 更多).
     */
    @Composable
    fun IconActionButton(
        icon: ImageVector,
        contentDescription: String?,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        active: Boolean = false,
    ) {
        ActionButton(onClick = onClick, modifier = modifier, active = active) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ActionIconSize),
            )
        }
    }

    /**
     * 简要回复块, 对应 Figma "reply": surfaceContainerHigh 圆角 10, 每条回复一行
     * "作者名 内容", 底部为 "展开 N 条回复".
     */
    @Composable
    fun RepliesBlock(
        replies: List<UIComment>,
        totalReplyCount: Int,
        onClickUrl: (String) -> Unit,
        modifier: Modifier = Modifier,
        onClickExpand: (() -> Unit)? = null,
    ) {
        val content: @Composable () -> Unit = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                replies.forEach { reply ->
                    val briefContent = remember(reply.content, reply.author) {
                        reply.content
                            .withDefaultFontSize(ReplyFontSize)
                            .prependAuthorName(reply.author?.nickname ?: reply.author?.id.toString())
                    }
                    RichText(
                        elements = briefContent.elements,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        onClickUrl = onClickUrl,
                    )
                }
                if (totalReplyCount > replies.size && onClickExpand != null) {
                    Text(
                        text = stringResource(Lang.comment_expand_replies, totalReplyCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }

        if (onClickExpand != null) {
            Surface(
                onClick = onClickExpand,
                modifier = modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(10.dp),
            ) { content() }
        } else {
            Surface(
                modifier = modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(10.dp),
            ) { content() }
        }
    }
}

/**
 * 将默认字号的文本缩放到 [target], BBCode 显式指定的字号按同比例缩放.
 */
internal fun UIRichText.withDefaultFontSize(target: Float): UIRichText {
    val factor = target / RichTextDefaults.FontSize
    if (factor == 1f) return this
    return UIRichText(elements.map { it.scaleFontSize(factor) })
}

private fun UIRichElement.scaleFontSize(factor: Float): UIRichElement = when (this) {
    is UIRichElement.AnnotatedText -> copy(
        slice = slice.map { annotated ->
            if (annotated is UIRichElement.Annotated.Text) {
                annotated.copy(size = annotated.size * factor)
            } else annotated
        },
    )

    is UIRichElement.Quote -> copy(content = content.map { it.scaleFontSize(factor) })
    else -> this
}

/**
 * 在回复内容前加上加粗的作者名, 并限制为最多两行.
 */
private fun UIRichText.prependAuthorName(name: String): UIRichText {
    val nameElement = UIRichElement.Annotated.Text(
        content = "$name ",
        size = CommentItemDefaults.ReplyFontSize,
        bold = true,
    )
    val first = elements.firstOrNull()
    val newElements = if (first is UIRichElement.AnnotatedText) {
        listOf(first.copy(slice = listOf(nameElement) + first.slice, maxLine = 2)) + elements.drop(1)
    } else {
        listOf(UIRichElement.AnnotatedText(listOf(nameElement), maxLine = 2)) + elements
    }
    return UIRichText(newElements)
}

internal fun UIRichText.toPlainText(): String = buildString {
    for (element in elements) {
        when (element) {
            is UIRichElement.AnnotatedText -> element.slice.forEach { annotated ->
                when (annotated) {
                    is UIRichElement.Annotated.Text -> append(annotated.content)
                    is UIRichElement.Annotated.Sticker -> append("(${annotated.id})")
                }
            }

            is UIRichElement.Quote -> append(UIRichText(element.content).toPlainText())
            is UIRichElement.Image -> append(element.imageUrl)
        }
    }
}
