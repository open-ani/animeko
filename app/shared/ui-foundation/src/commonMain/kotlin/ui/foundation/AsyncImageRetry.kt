/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * [AsyncImage] 的失败重试: Coil 的 painter 一旦进 Error 就停在那里, 模型不变永不重试.
 *
 * TV 上遥控器快速滑过卡片列表会瞬间并发几十个图片请求, 偶发超时/限流让个别卡片永久无图
 * (同 URL 新建请求能加载, 证明数据和网络都没坏). 置 model 为 null 一帧再放回同一请求即可
 * 强制 painter 重启; 按次数退避, 有上限, 免得真坏的 URL 无限打转.
 *
 * 用法:
 * ```
 * val retry = rememberAsyncImageRetryState(imageUrl)
 * AsyncImage(
 *     model = if (retry.suppressed) null else imageUrl,
 *     onError = { retry.onError() },
 *     ...
 * )
 * ```
 */
@Stable
class AsyncImageRetryState internal constructor(private val maxAttempts: Int) {
    /** true = 本帧把 model 置 null (退避窗口); 由 [rememberAsyncImageRetryState] 定时放回. */
    var suppressed: Boolean by mutableStateOf(false)
        internal set

    internal var attempt: Int by mutableIntStateOf(0)

    /** 挂到 `AsyncImage` 的 `onError` 上. 超过上限后不再重试 (保持 Error, 与原行为一致). */
    fun onError() {
        if (attempt < maxAttempts) {
            attempt++
            suppressed = true
        }
    }

    internal suspend fun runBackoff() {
        // 退避: 失败多半是并发洪峰, 缓一拍再试
        delay(500L * attempt)
        suppressed = false
    }
}

/**
 * 见 [AsyncImageRetryState]. [key] 换图时重置重试计数 (通常传图片 URL).
 */
@Composable
fun rememberAsyncImageRetryState(key: Any?, maxAttempts: Int = 3): AsyncImageRetryState {
    val state = remember(key) { AsyncImageRetryState(maxAttempts) }
    LaunchedEffect(state.suppressed) {
        if (state.suppressed) state.runBackoff()
    }
    return state
}
