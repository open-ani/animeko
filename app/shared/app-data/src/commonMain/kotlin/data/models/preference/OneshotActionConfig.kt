/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

/**
 * 用于存储一次性提示的开关状态
 */
@Serializable
@Immutable
data class OneshotActionConfig(
    /**
     * 在搜索标签页显示长按删除标签的 Tip
     */
    val deleteSearchTagTip: Boolean = true,
    val horizontalScrollOnDesktopTip: Boolean = true,
) {
    companion object {
        @Stable
        val Default = OneshotActionConfig()
    }
}