/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * 桌面端不提供定位, 定位框恒显示「当前平台不支持定位」.
 */
@Composable
actual fun rememberLocationTimeZoneCapability(): LocationTimeZoneCapability = remember {
    object : LocationTimeZoneCapability {
        override val supported: Boolean get() = false

        override fun requestPermission(onResult: (granted: Boolean) -> Unit) = Unit
    }
}
