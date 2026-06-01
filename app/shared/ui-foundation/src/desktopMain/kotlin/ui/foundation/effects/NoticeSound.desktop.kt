/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.him188.ani.app.data.models.preference.NoticeSoundKind

/**
 * 桌面端空实现: 唯一的调用方是后台会话提示, 而保留会话只在电视上有入口
 * (`AniUiBehavior.retainPlaybackSession`), 桌面端根本不会走到这里.
 */
@Composable
actual fun rememberNoticeSoundPlayer(): (NoticeSoundKind) -> Unit = remember { {} }
