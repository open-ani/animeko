/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.danmaku

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.danmaku.protocol.DanmakuInfo
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuPresentation
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuHostState
import me.him188.ani.danmaku.ui.DanmakuTrackProperties

@Stable
class PlayerDanmakuState(
    danmakuEnabled: State<Boolean>,
    danmakuConfig: State<DanmakuConfig>,
    private val onSend: suspend (info: DanmakuInfo) -> Danmaku,
    private val onSetEnabled: suspend (enabled: Boolean) -> Unit,
    private val onHideController: () -> Unit,
    private val backgroundScope: CoroutineScope,
    danmakuTrackProperties: DanmakuTrackProperties = DanmakuTrackProperties.Default,
) {
    val danmakuHostState: DanmakuHostState =
        DanmakuHostState(danmakuConfig, danmakuTrackProperties)

    val enabled: Boolean by danmakuEnabled
    private val setEnabledTasker = MonoTasker(backgroundScope)

    var danmakuEditorText: String by mutableStateOf("")

    private val sendDanmakuTasker = MonoTasker(backgroundScope)
    val isSending: StateFlow<Boolean> get() = sendDanmakuTasker.isRunning


    fun setEnabled(enabled: Boolean) {
        setEnabledTasker.launch {
            onSetEnabled(enabled)
        }
    }

    suspend fun send(
        info: DanmakuInfo,
    ) {
        val deferred = sendDanmakuTasker.async {
            onSend(info)
        }

        val danmaku = try {
            deferred.await()
        } catch (e: Throwable) {
            danmakuEditorText = info.text
            null
        }

        danmaku?.let {
            backgroundScope.launch {
                // 如果用户此时暂停了视频, 这里就会一直挂起, 所以单独开一个
                danmakuHostState.send(DanmakuPresentation(danmaku, isSelf = true))
            }
        }

        onHideController()
    }
}