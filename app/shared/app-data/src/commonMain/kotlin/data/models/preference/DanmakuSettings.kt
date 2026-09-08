/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.danmaku.api.DanmakuLocation

@Serializable
data class DanmakuSettings(
    val useGlobal: Boolean? = null,

    /**
     * 发送弹幕时使用的颜色, RGB (不含 alpha), 例如白色为 `0xFFFFFF`.
     */
    val sendColor: Int = DEFAULT_SEND_COLOR,

    /**
     * 发送弹幕时使用的位置.
     */
    val sendLocation: DanmakuLocation = DanmakuLocation.NORMAL,

    @Suppress("PropertyName")
    @Transient val _placeholder: Int = 0,
) {
    companion object {
        /**
         * 白色
         */
        const val DEFAULT_SEND_COLOR: Int = 0xFFFFFF

        val Default = DanmakuSettings()
    }
}