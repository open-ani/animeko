/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 把进度条放在窗口底部, 与它在播放器里的位置一致: 预览浮窗显示在进度条上方.
 *
 * 进度条贴着窗口顶部时浮窗没有地方放, 会盖在进度条和鼠标上. 这时进度条失去悬浮, 浮窗消失, 进度条又重新悬浮,
 * 如此反复, 测试永远等不到空闲.
 */
@Composable
internal fun ProgressSliderAtWindowBottom(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        content()
    }
}
