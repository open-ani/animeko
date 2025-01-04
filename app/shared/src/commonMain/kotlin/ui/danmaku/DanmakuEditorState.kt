/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import me.him188.ani.app.data.network.protocol.DanmakuInfo
import me.him188.ani.app.tools.MonoTasker

@Stable
class DanmakuEditorState(
    private val onPost: suspend (DanmakuInfo) -> me.him188.ani.danmaku.api.Danmaku,
    private val onPostSuccess: suspend (me.him188.ani.danmaku.api.Danmaku) -> Unit,
    uiScope: CoroutineScope,
) {
    var text: String by mutableStateOf("")

    private val sendDanmakuTasker = MonoTasker(uiScope)
    val isSending: StateFlow<Boolean> get() = sendDanmakuTasker.isRunning

    @UiThread
    suspend fun post(
        info: DanmakuInfo,
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