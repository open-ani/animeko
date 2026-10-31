/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 按住前进键进入倍速快进的状态: [press] 后 200ms 开始快进, [release] 结束它.
 * 没有进入快进就松开, 调用方应执行一次普通前进.
 *
 * 键盘前进的两个入口 (快捷键处理器和 `Modifier.keyboardSeekAndFastForward`) 共用.
 */
internal class FastSkipHold(
    private val scope: CoroutineScope,
    private val fastSkipState: FastSkipState?,
) {
    private var job: Job? = null
    private var ticket: Int? = null

    /**
     * 前进键是否处于按住状态.
     */
    val isPressed: Boolean get() = job != null

    /**
     * 前进键按下. 按住期间重复的按下不重新计时.
     */
    fun press() {
        if (job != null) return
        job = scope.launch {
            delay(200)
            ticket = fastSkipState?.startSkipping(SkipDirection.FORWARD)
        }
    }

    /**
     * 前进键松开. 返回按住期间是否进入了快进. 只能在 [isPressed] 时调用.
     */
    fun release(): Boolean {
        val wasSkipping = ticket != null
        cancel()
        return wasSkipping
    }

    /**
     * 结束按住状态和正在进行的快进, 不触发普通前进.
     */
    fun cancel() {
        job?.cancel()
        job = null
        ticket?.let { fastSkipState?.stopSkipping(it) }
        ticket = null
    }
}
