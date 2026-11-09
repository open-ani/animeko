/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment.thread

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.comment.CommentColumn
import me.him188.ani.app.ui.comment.CommentItem
import me.him188.ani.app.ui.comment.CommentMenuHandlers
import me.him188.ani.app.ui.comment.CommentOverlayCleanupEffect
import me.him188.ani.app.ui.comment.CommentReportState
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_reply_to_name
import me.him188.ani.app.ui.lang.comment_thread_no_replies
import me.him188.ani.app.ui.lang.comment_thread_not_found
import me.him188.ani.app.ui.lang.comment_thread_replies_count
import me.him188.ani.app.ui.lang.comment_thread_reply_root
import me.him188.ani.app.ui.lang.comment_thread_title
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.isLoadingFirstPageOrRefreshing
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object CommentThreadTestTags {
    const val Pane = "CommentThread:pane"
    const val BackButton = "CommentThread:back"
    const val Root = "CommentThread:root"
    const val RepliesTitle = "CommentThread:repliesTitle"
    const val NoReplies = "CommentThread:noReplies"
    const val NotFound = "CommentThread:notFound"
    const val ReplyBar = "CommentThread:replyBar"
    const val ReplyTargetChip = "CommentThread:replyTargetChip"
    const val ClearReplyTarget = "CommentThread:clearReplyTarget"

    fun reply(stableId: String) = "CommentThread:reply:$stableId"
}

object CommentThreadDefaults {
    /**
     * 独立路由里内容的最大宽度, 与条目详情页一致; 宽屏居中单栏.
     */
    val MaximumContentWidth = 1300.dp

    /**
     * 通知进入时为了找到要定位的回复最多翻的页数, 翻不到就停在顶部.
     */
    const val MaxFocusPages = 10

    /**
     * 定位到的回复高亮的时长. 这段时间里布局再变 (表头长高、底部回复栏出现、图片加载) 也把它留在可视区域内.
     */
    val FocusHighlightDuration = 1.5.seconds

    /**
     * 定位前最多等根评论加载这么久: 表头的高度和底部回复栏有没有都取决于它.
     */
    val FocusRootSettleTimeout = 5.seconds

    /**
     * 回复列表前的表头项数: 根评论 (或占位) 与分组标题在同一项里.
     */
    internal const val HeaderItemCount = 1
}

/**
 * 一条评论的详情 (thread): 完整的根评论, 下面是 "相关回复共 N 条" 与时间正序的全部回复; 可回复的根评论还有底部回复栏.
 *
 * 根评论与回复共用 [CommentThreadState.commentState] 的点赞 / 贴纸乐观更新. 加载与刷新只用列表的下拉刷新指示器, 不另做 spinner.
 *
 * - 点击根评论正文: 回复对象回到根评论. 点击一条回复的正文: 回复对象切到这条回复, 底部栏出现可清除的 "回复 @昵称".
 * - 点击底部栏: 以当前回复对象调用 [onReply], 由宿主打开编辑器.
 * - 根评论不存在时整个区域显示 "评论不存在或已被删除"; 没有快照又加载失败时显示错误卡.
 *
 * @param onBack 表头返回箭头. `null` 时不显示表头 (独立路由用自己的顶栏).
 * @param onReply 打开编辑器回复 [UIComment] (根评论或某条回复).
 * @param reportState 举报. `null` 时隐藏举报入口; 举报弹层由宿主的 [me.him188.ani.app.ui.comment.CommentReportHost] 渲染.
 * @param onOpenOriginal 在 Bangumi 打开, 只对 Bangumi 来源的评论显示.
 */
@Composable
fun CommentThreadPane(
    state: CommentThreadState,
    onClickUrl: (String) -> Unit,
    onClickImage: (String) -> Unit,
    onReply: (target: UIComment) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    reportState: CommentReportState? = null,
    onOpenOriginal: ((UIComment) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val rootState by state.root.collectAsStateWithLifecycle()
    val items = state.commentState.list.collectAsLazyPagingItemsWithLifecycle()
    CommentOverlayCleanupEffect(state.commentState, items)

    val toaster = LocalToaster.current
    LaunchedEffect(state) {
        state.commentState.actionSubmitFailures.collect { toaster.showLoadError(LoadError.fromException(it)) }
    }
    LaunchedEffect(state) {
        state.rootLoadFailures.collect { toaster.showLoadError(LoadError.fromException(it)) }
    }

    val root = rootState.commentOrNull?.let { state.commentState.withOverlay(it) }
    val canReply = root != null && root.source == UICommentSource.ANI && root.canReply
    var highlightedReplyId by remember(state) { mutableStateOf<String?>(null) }

    Column(modifier.testTag(CommentThreadTestTags.Pane)) {
        if (onBack != null) {
            ThreadPaneHeader(onBack)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val current = rootState
            when {
                current is CommentThreadRootState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ThreadEmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(Lang.comment_thread_not_found),
                        modifier = Modifier.testTag(CommentThreadTestTags.NotFound),
                    )
                }

                current is CommentThreadRootState.Failed && root == null -> LoadErrorCard(
                    error = LoadError.fromException(current.error),
                    onRetry = { state.refresh() },
                    modifier = Modifier.padding(16.dp),
                )

                else -> CommentColumn(
                    items,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    state = gridState,
                    isRefreshing = current is CommentThreadRootState.Loading || items.isLoadingFirstPageOrRefreshing,
                    onRefresh = { state.refresh() },
                    header = {
                        ThreadHeader(
                            root = root,
                            replyTotal = state.replyTotal ?: root?.replyCount ?: 0,
                            onClickUrl = onClickUrl,
                            onClickImage = onClickImage,
                            onClickRoot = if (canReply) {
                                { state.selectReplyTarget(null) }
                            } else null,
                            state = state,
                            reportState = reportState,
                            onOpenOriginal = onOpenOriginal,
                        )
                    },
                    emptyPlaceholder = {
                        Text(
                            stringResource(Lang.comment_thread_no_replies),
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag(CommentThreadTestTags.NoReplies),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) { _, reply ->
                    val replyWithOverlay = state.commentState.withOverlay(reply)
                    val highlightColor by animateColorAsState(
                        if (highlightedReplyId == reply.stableId) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else Color.Transparent,
                        label = "replyHighlight",
                    )
                    CommentItem(
                        comment = replyWithOverlay,
                        onClickUrl = onClickUrl,
                        onClickImage = onClickImage,
                        modifier = Modifier
                            .background(highlightColor)
                            .testTag(CommentThreadTestTags.reply(reply.stableId)),
                        showReplyTarget = true,
                        onClickReply = if (canReply) {
                            { state.selectReplyTarget(it) }
                        } else null,
                        onToggleVote = if (replyWithOverlay.source == UICommentSource.ANI) {
                            { c, vote -> state.commentState.toggleVote(c, vote) }
                        } else null,
                        onToggleReaction = if (replyWithOverlay.source == UICommentSource.ANI) {
                            { c, value -> state.commentState.submitReaction(c, value) }
                        } else null,
                        menu = commentMenu(replyWithOverlay, reportState, onOpenOriginal),
                    )
                }
            }
        }
        if (root != null && canReply) {
            ReplyBar(
                root = root,
                target = state.replyTarget,
                onClick = { onReply(state.replyTarget ?: root) },
                onClearTarget = { state.selectReplyTarget(null) },
            )
        }
    }

    FocusReplyEffect(state, items, gridState) { highlightedReplyId = it }
    ScrollToEndEffect(state, items, gridState)
}

/**
 * 表头: 返回箭头 + "评论详情".
 */
@Composable
private fun ThreadPaneHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, Modifier.testTag(CommentThreadTestTags.BackButton)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
        }
        Text(
            stringResource(Lang.comment_thread_title),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 回复列表的表头项: 完整形态的根评论 (贴纸换行, 不显示简要回复块), 下面是分组标题 "相关回复共 N 条".
 * 根评论还没加载到 (无快照) 时只有分组标题, 刷新指示由列表顶部的下拉刷新承担.
 */
@Composable
private fun ThreadHeader(
    root: UIComment?,
    replyTotal: Int,
    onClickUrl: (String) -> Unit,
    onClickImage: (String) -> Unit,
    onClickRoot: ((UIComment) -> Unit)?,
    state: CommentThreadState,
    reportState: CommentReportState?,
    onOpenOriginal: ((UIComment) -> Unit)?,
) {
    Column(Modifier.fillMaxWidth()) {
        if (root != null) {
            CommentItem(
                comment = root,
                onClickUrl = onClickUrl,
                onClickImage = onClickImage,
                modifier = Modifier.testTag(CommentThreadTestTags.Root),
                reactionsClipped = false,
                showReplies = false,
                onClickReply = onClickRoot,
                onToggleVote = if (root.source == UICommentSource.ANI) {
                    { c, vote -> state.commentState.toggleVote(c, vote) }
                } else null,
                onToggleReaction = if (root.source == UICommentSource.ANI) {
                    { c, value -> state.commentState.submitReaction(c, value) }
                } else null,
                menu = commentMenu(root, reportState, onOpenOriginal),
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider(color = DividerDefaults.color.stronglyWeaken())
        Text(
            stringResource(Lang.comment_thread_replies_count, replyTotal),
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag(CommentThreadTestTags.RepliesTitle),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = DividerDefaults.color.stronglyWeaken())
    }
}

/**
 * 菜单: Bangumi 评论可在 Bangumi 打开; 只有 Ani 评论可举报.
 */
private fun commentMenu(
    comment: UIComment,
    reportState: CommentReportState?,
    onOpenOriginal: ((UIComment) -> Unit)?,
): CommentMenuHandlers = CommentMenuHandlers(
    onOpenOriginal = if (comment.source == UICommentSource.BANGUMI) onOpenOriginal else null,
    onReport = if (comment.source == UICommentSource.ANI) {
        reportState?.let { report -> { report.show(it) } }
    } else null,
)

/**
 * 底部回复栏: 一个看起来像输入框的按钮, 点击打开编辑器. 回复对象是某条回复时, 上方多一个可清除的 chip.
 */
@Composable
private fun ReplyBar(
    root: UIComment,
    target: UIComment?,
    onClick: () -> Unit,
    onClearTarget: () -> Unit,
) {
    val targetName = (target ?: root).author?.nickname ?: (target ?: root).author?.id ?: ""
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (target != null) {
                InputChip(
                    selected = false,
                    onClick = onClearTarget,
                    label = { Text(stringResource(Lang.comment_reply_to_name, targetName), maxLines = 1) },
                    modifier = Modifier.testTag(CommentThreadTestTags.ReplyTargetChip),
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(Lang.comment_thread_reply_root),
                            Modifier.size(18.dp).testTag(CommentThreadTestTags.ClearReplyTarget),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
            Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(40.dp).testTag(CommentThreadTestTags.ReplyBar),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Lang.comment_reply_to_name, targetName),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 整个区域的空状态: 圆底图标 + 标题, 与通知中心的空状态同一形态.
 */
@Composable
private fun ThreadEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .widthIn(max = 320.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * 通知进入时定位到 [CommentThreadKey.focusReplyId]: 回复按时间正序分页, 目标不在已加载的页里就继续翻, 最多翻
 * [CommentThreadDefaults.MaxFocusPages] 页; 找到后滚到它并高亮 [CommentThreadDefaults.FocusHighlightDuration], 找不到就停在顶部不提示.
 *
 * 定位是这次进入的目的, 所以一直等到楼加载成功为止 ([awaitThreadLoaded]): 打开时断网之类的失败不算完, 用户重试成功后照样定位.
 * 等的内容里包括根评论 (最多 [CommentThreadDefaults.FocusRootSettleTimeout]): 独立路由没有快照, 根评论常常比回复晚一点到,
 * 到了表头才长到最终高度, 底部回复栏也才出现. 目标在末尾时列表只能停在最底, 先滚再缩小可视区域会把它压到回复栏下面.
 * 高亮期间继续盯着布局, 目标被挤出可视区域就挪回来; 用户自己开始滚动则不再干预.
 */
@Composable
private fun FocusReplyEffect(
    state: CommentThreadState,
    items: LazyPagingItems<UIComment>,
    gridState: LazyGridState,
    onHighlight: (stableId: String?) -> Unit,
) {
    val focusReplyId = state.key.focusReplyId ?: return
    val currentOnHighlight by rememberUpdatedState(onHighlight)
    LaunchedEffect(state, items) {
        awaitThreadLoaded(state, items)
        repeat(CommentThreadDefaults.MaxFocusPages) {
            snapshotFlow { items.loadState }.first { it.refresh !is LoadState.Loading && it.append !is LoadState.Loading }
            val index = items.itemSnapshotList.indexOfFirst { it?.stableId == focusReplyId }
            if (index >= 0) {
                val itemIndex = index + CommentThreadDefaults.HeaderItemCount
                gridState.scrollToItem(itemIndex)
                currentOnHighlight(focusReplyId)
                try {
                    gridState.keepItemVisible(itemIndex, CommentThreadDefaults.FocusHighlightDuration)
                } finally {
                    currentOnHighlight(null)
                }
                return@LaunchedEffect
            }
            val loaded = items.itemCount
            if (loaded == 0 || items.loadState.append.endOfPaginationReached || items.loadState.hasError) {
                return@LaunchedEffect
            }
            // 访问末项让 Paging 加载下一页
            items[loaded - 1]
            withTimeoutOrNull(5.seconds) {
                snapshotFlow { items.itemCount != loaded || items.loadState.append.endOfPaginationReached }.first { it }
            } ?: return@LaunchedEffect
        }
    }
}

/**
 * 等到回复首页加载成功、根评论加载结束, 而且回复列表在屏幕上.
 *
 * 回复首页失败 (比如从通知进入时断网) 就等用户重试后的下一次结果, 直到成功. 根评论最多等 [CommentThreadDefaults.FocusRootSettleTimeout];
 * 但它没有快照又失败时整个区域是错误卡, 列表根本不在屏幕上, 这时同样等重试让它重新开始加载, 再从头等一遍.
 */
private suspend fun awaitThreadLoaded(state: CommentThreadState, items: LazyPagingItems<UIComment>) {
    while (true) {
        snapshotFlow { items.loadState.refresh }.first { it is LoadState.NotLoading }
        val root = withTimeoutOrNull(CommentThreadDefaults.FocusRootSettleTimeout) {
            state.root.first { it !is CommentThreadRootState.Loading }
        }
        if (root is CommentThreadRootState.Failed && root.snapshot == null) {
            state.root.first { it is CommentThreadRootState.Loading }
            continue
        }
        return
    }
}

/**
 * 在 [duration] 内盯着布局, 第 [index] 项有一部分露出可视区域时把它挪回来; 项比可视区域还高时对齐它的顶部.
 * 用户的滚动手势会打断这里的滚动 (更高优先级的 mutator 取消当前调用), 那之后就交给用户, 不再挪.
 */
private suspend fun LazyGridState.keepItemVisible(index: Int, duration: Duration) {
    try {
        withTimeoutOrNull(duration) {
            snapshotFlow { layoutInfo }.collect { info ->
                val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@collect
                val bottomOverflow = item.offset.y + item.size.height - info.viewportEndOffset
                val topOverflow = info.viewportStartOffset - item.offset.y
                val delta = when {
                    bottomOverflow > 0 -> minOf(bottomOverflow, -topOverflow).coerceAtLeast(0)
                    topOverflow > 0 -> -topOverflow
                    else -> 0
                }
                if (delta != 0) scrollBy(delta.toFloat())
            }
        }
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
    }
}

/**
 * 发送回复后 ([CommentThreadState.onReplySent]) 等刷新完成, 滚到列表末尾: 回复按时间正序, 新回复在最后.
 */
@Composable
private fun ScrollToEndEffect(
    state: CommentThreadState,
    items: LazyPagingItems<UIComment>,
    gridState: LazyGridState,
) {
    val request = state.scrollToEndRequests
    LaunchedEffect(state, request) {
        if (request == 0) return@LaunchedEffect
        // 先等刷新开始再等它结束, 否则会在旧数据上滚动
        withTimeoutOrNull(5.seconds) {
            snapshotFlow { items.loadState.refresh }.first { it is LoadState.Loading }
        }
        snapshotFlow { items.loadState.refresh }.first { it !is LoadState.Loading }
        val count = items.itemCount
        if (count > 0) {
            gridState.scrollToItem(count - 1 + CommentThreadDefaults.HeaderItemCount)
        }
    }
}
