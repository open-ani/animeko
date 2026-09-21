/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.danmaku

import androidx.annotation.UiThread
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.tools.MonoTasker

@Stable
class DanmakuEditorState(
    private val onPost: suspend (me.him188.ani.danmaku.api.DanmakuContent) -> me.him188.ani.danmaku.api.DanmakuInfo,
    private val onPostSuccess: suspend (me.him188.ani.danmaku.api.DanmakuInfo) -> Unit,
    uiScope: CoroutineScope,
    initialStyle: DanmakuSendStyle = DanmakuSendStyle.Default,
    private val onStyleChange: (DanmakuSendStyle) -> Unit = {},
) {
    var text: String by mutableStateOf("")

    /**
     * 发送弹幕使用的样式 (颜色, 位置).
     *
     * 直接赋值仅更新本地状态 (用于从设置同步), 不会触发 [onStyleChange]; 用户主动修改请用 [updateStyle].
     */
    var style: DanmakuSendStyle by mutableStateOf(initialStyle)

    /**
     * 用户主动修改样式: 立即更新本地状态, 并通过 [onStyleChange] 通知持久化.
     */
    fun updateStyle(style: DanmakuSendStyle) {
        this.style = style
        onStyleChange(style)
    }

    private val sendDanmakuTasker = MonoTasker(uiScope)
    val isSending: StateFlow<Boolean> get() = sendDanmakuTasker.isRunning

    @UiThread
    suspend fun post(
        info: me.him188.ani.danmaku.api.DanmakuContent,
    ) {
        val deferred = sendDanmakuTasker.async {
            onPost(info)
        }

        val danmaku = try {
            deferred.await().also {
                text = ""
            }
        } catch (e: Throwable) {
            text = info.text
            null
        }

        danmaku?.let {
            onPostSuccess(danmaku)
        }
    }
}