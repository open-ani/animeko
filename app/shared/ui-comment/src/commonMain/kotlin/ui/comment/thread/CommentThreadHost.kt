/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment.thread

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.foundation.navigation.BackHandler

/**
 * 评论列表所在容器里原地打开的 thread. 状态放在 view model 里 (旋转屏幕不丢), 页面用 [CommentThreadHostContent] 渲染.
 *
 * @param createState 为一个 thread 创建状态. 参数是 thread 的身份与列表里已有的根评论快照.
 */
@Stable
class CommentThreadHost(
    private val createState: (key: CommentThreadKey, snapshot: UIComment?) -> CommentThreadState,
) {
    /**
     * 当前打开的 thread, `null` 表示显示评论列表.
     */
    var current: CommentThreadState? by mutableStateOf(null)
        private set

    /**
     * 原地打开 [key] 对应的 thread. [snapshot] 是列表里已有的那条根评论, 打开瞬间先显示它.
     */
    fun open(key: CommentThreadKey, snapshot: UIComment?) {
        current = createState(key, snapshot)
    }

    /**
     * 关闭 thread, 回到评论列表.
     */
    fun close() {
        current = null
    }
}

/**
 * 在评论列表的位置上渲染 [host]: 没有打开 thread 时显示 [list], 否则显示 [thread], 两者之间做横向切换动画.
 *
 * 系统返回 (返回键、返回手势、桌面端 Esc) 先关闭 thread, 而不是离开页面: 这里的 [BackHandler] 比页面自己的返回处理
 * 更晚注册, 所以在 thread 打开时优先生效.
 *
 * @param backHandlerEnabled 为 `false` 时返回不由本宿主处理, 例如全屏播放时返回应先退出全屏.
 */
@Composable
fun CommentThreadHostContent(
    host: CommentThreadHost,
    modifier: Modifier = Modifier,
    backHandlerEnabled: Boolean = true,
    list: @Composable () -> Unit,
    thread: @Composable (CommentThreadState) -> Unit,
) {
    val current = host.current
    BackHandler(enabled = current != null && backHandlerEnabled) { host.close() }
    AnimatedContent(
        targetState = current,
        modifier = modifier,
        contentKey = { it?.key },
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
            }
        },
        label = "CommentThreadHost",
    ) { state ->
        if (state == null) {
            list()
        } else {
            thread(state)
        }
    }
}
