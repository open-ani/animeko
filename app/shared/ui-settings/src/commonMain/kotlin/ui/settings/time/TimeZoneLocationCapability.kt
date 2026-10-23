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
import androidx.compose.runtime.Stable

/**
 * 时区设置页定位框所需的平台能力.
 *
 * 定位 API 本身只检查权限、从不弹窗, 因此授权申请由本接口完成: 拿到授权后才应调用定位.
 */
@Stable
interface LocationTimeZoneCapability {
    /**
     * 当前平台是否提供定位. 桌面与 iOS 为 `false`, 定位框直接显示「当前平台不支持定位」.
     */
    val supported: Boolean

    /**
     * 申请定位权限, [onResult] 表示是否已授权. [supported] 为 `false` 时不会被调用.
     */
    fun requestPermission(onResult: (granted: Boolean) -> Unit)
}

@Composable
expect fun rememberLocationTimeZoneCapability(): LocationTimeZoneCapability
