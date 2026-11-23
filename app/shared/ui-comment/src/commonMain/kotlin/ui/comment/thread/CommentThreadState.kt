/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment.thread

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.navigation.CommentThreadTarget
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentVote
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable

/**
 * 一个评论 thread 的身份: 评论挂在哪个对象上, 以及根评论的稳定 id.
 *
 * @property focusReplyId 打开后要定位并高亮的回复的稳定 id (通知落地), 为 `null` 时停在根评论.
 */
@Immutable
data class CommentThreadKey(
    val target: CommentThreadTarget,
    val rootStableId: String,
    val focusReplyId: String? = null,
)

/**
 * 根评论的加载状态.
 */
@Immutable
sealed interface CommentThreadRootState {
    /**
     * 正在加载. [snapshot] 是从评论列表带过来的那条评论, 有的话先显示它, 加载完再替换.
     */
    data class Loading(val snapshot: UIComment?) : CommentThreadRootState

    data class Loaded(val comment: UIComment) : CommentThreadRootState

    /**
     * 根评论不存在、已被删除, 或不属于这个对象.
     */
    data object NotFound : CommentThreadRootState

    /**
     * 加载失败. 有 [snapshot] 时继续显示它并提示一次 ([CommentThreadState.rootLoadFailures]), 否则显示错误卡.
     */
    data class Failed(val error: Throwable, val snapshot: UIComment?) : CommentThreadRootState
}

/**
 * 当前能显示的根评论: 已加载的, 或加载前 / 加载失败时的快照.
 */
val CommentThreadRootState.commentOrNull: UIComment?
    get() = when (this) {
        is CommentThreadRootState.Loading -> snapshot
        is CommentThreadRootState.Loaded -> comment
        CommentThreadRootState.NotFound -> null
        is CommentThreadRootState.Failed -> snapshot
    }

/**
 * 一个评论 thread 的状态: 根评论, 时间正序的回复分页, 以及底部回复栏的回复对象.
 *
 * 根评论与回复共用一个 [CommentState] ([commentState]): 点赞与贴纸回应的乐观更新按稳定 id 覆盖, 对根评论
 * ([CommentState.withOverlay]) 和回复列表里的项都生效. 回复列表是 [commentState] 的 `list`; 根评论不在列表里, 由页面作为表头渲染.
 *
 * @param snapshot 从评论列表进入时已有的根评论 (含简要回复), 打开瞬间先显示它; 独立路由进入时为 `null`.
 * @param loadRoot 加载根评论, 不存在时返回 `null`.
 * @param repliesPager 回复分页. 参数是服务端给出可见回复总数时的回调.
 */
@Stable
class CommentThreadState(
    val key: CommentThreadKey,
    snapshot: UIComment?,
    private val loadRoot: suspend () -> UIComment?,
    repliesPager: (onTotal: (Int) -> Unit) -> Flow<PagingData<UIComment>>,
    onSubmitCommentReaction: suspend (comment: UIComment, value: String, selected: Boolean) -> Unit,
    onSubmitCommentVote: suspend (comment: UIComment, vote: UICommentVote?) -> Unit,
    private val backgroundScope: CoroutineScope,
) {
    private val restarter = FlowRestarter()
    private val rootLoadFailureChannel = Channel<Throwable>(Channel.BUFFERED)

    /**
     * 根评论加载失败但还有快照可以显示时发出一次, 页面以 toast 提示. 没有快照时失败直接体现在 [root] 里.
     */
    val rootLoadFailures: Flow<Throwable> = rootLoadFailureChannel.receiveAsFlow()

    /**
     * 最近一次能显示的根评论: 初始是快照, 加载成功后是服务端的结果. 刷新失败时继续显示它.
     */
    private var lastKnownRoot: UIComment? = snapshot

    val root: StateFlow<CommentThreadRootState> = flow {
        val previous = lastKnownRoot
        emit(CommentThreadRootState.Loading(previous))
        val loaded = try {
            loadRoot()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (previous != null) rootLoadFailureChannel.trySend(e)
            emit(CommentThreadRootState.Failed(e, previous))
            return@flow
        }
        if (loaded == null) {
            lastKnownRoot = null
            emit(CommentThreadRootState.NotFound)
        } else {
            lastKnownRoot = loaded
            emit(CommentThreadRootState.Loaded(loaded))
        }
    }.restartable(restarter)
        .stateIn(backgroundScope, SharingStarted.Eagerly, CommentThreadRootState.Loading(snapshot))

    private val replyTotalState = mutableStateOf<Int?>(null)

    /**
     * 服务端给出的可见回复总数. 回复首页加载前为 `null`, 此时页面用根评论的 `replyCount`.
     */
    val replyTotal: Int? get() = replyTotalState.value

    val commentState: CommentState = CommentState(
        list = repliesPager { total -> replyTotalState.value = total }
            .restartable(restarter)
            .cachedIn(backgroundScope),
        countState = replyTotalState,
        onSubmitCommentReaction = onSubmitCommentReaction,
        backgroundScope = backgroundScope,
        commentLoadFailures = emptyFlow(),
        onSubmitCommentVote = onSubmitCommentVote,
    )

    /**
     * 底部回复栏当前回复的那条回复; `null` 表示回复根评论.
     */
    var replyTarget: UIComment? by mutableStateOf(null)
        private set

    /**
     * 发送成功后请求页面滚到回复列表末尾 (新回复在末尾) 的次数, 页面按变化响应.
     */
    var scrollToEndRequests: Int by mutableIntStateOf(0)
        private set

    fun selectReplyTarget(reply: UIComment?) {
        replyTarget = reply
    }

    /**
     * 重新加载根评论与回复列表. 下拉刷新与发送回复后调用.
     */
    fun refresh() {
        restarter.restart()
    }

    /**
     * 一条回复发送成功: 回复对象回到根评论, 重新加载, 加载完滚到末尾让用户看到自己的回复.
     */
    fun onReplySent() {
        replyTarget = null
        scrollToEndRequests++
        refresh()
    }
}
