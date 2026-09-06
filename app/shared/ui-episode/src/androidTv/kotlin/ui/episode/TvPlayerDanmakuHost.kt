/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.danmaku.ui.DanmakuHost
import me.him188.ani.danmaku.ui.DanmakuHostState

/** A render-only host; the ViewModel supplies and advances danmaku state. */
@Composable
fun TvPlayerDanmakuHost(danmakuHostState: DanmakuHostState, modifier: Modifier = Modifier) {
    DanmakuHost(danmakuHostState, modifier)
}
