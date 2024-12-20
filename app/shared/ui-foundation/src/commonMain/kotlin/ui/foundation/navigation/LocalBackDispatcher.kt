/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

fun interface BackDispatcher {
    /**
     * 调用此 [BackDispatcher], 也就是相当于调用最后一个注册并启用的 [BackHandler].
     */
    fun onBackPressed()
}

/**
 * 可模拟点击返回键
 */
object LocalBackDispatcher {
    @Stable
    private val NoopBackDispatcher = BackDispatcher {}
    
    /**
     * 获取当前的 [BackDispatcher] 实例.
     * 注意, 此功能不能用于返回上一页面, 会导致 #1343.
     */
    val current: BackDispatcher
        @Composable
        get() = LocalOnBackPressedDispatcherOwner.current?.let { owner ->
            BackDispatcher {
                owner.onBackPressedDispatcher.onBackPressed()
            }
        } ?: NoopBackDispatcher
}
