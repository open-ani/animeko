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
 * iOS 空实现: 同桌面端, 保留会话只在电视上有入口, 这里不会被调用.
 */
@Composable
actual fun rememberNoticeSoundPlayer(): (NoticeSoundKind) -> Unit = remember { {} }
