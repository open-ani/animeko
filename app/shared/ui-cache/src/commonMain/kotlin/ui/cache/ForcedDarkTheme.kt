/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache

import androidx.compose.runtime.Composable
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.theme.AniTheme

/**
 * 按 [AniUiBehavior.forceDarkInPlayer][me.him188.ani.app.ui.foundation.AniUiBehavior.forceDarkInPlayer]
 * 强制深色主题, 否则原样.
 *
 * 缓存相关页面 (缓存管理 / 缓存详情) 在只从播放链路进入的形态下
 * (播放器 → 条目缓存页 → 管理全部缓存 → 缓存详情) 前后都是暗色内容;
 * 浅色主题下这些页面突然一页亮白非常刺眼, 统一成深色.
 */
@Composable
internal fun ForcedDarkTheme(content: @Composable () -> Unit) {
    if (LocalAniUiBehavior.current.forceDarkInPlayer) {
        AniTheme(darkModeOverride = DarkMode.DARK, content = content)
    } else {
        content()
    }
}
