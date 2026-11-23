/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.ui.DanmakuTextConversion

/**
 * 弹幕文本转换设置: 全局目标文字 + 按弹幕来源的覆盖.
 *
 * 来源未设置覆盖时跟随 [global].
 */
data class DanmakuTextConversionSettings(
    val global: DanmakuTextConversion = DanmakuTextConversion.ORIGINAL,
    val overrides: Map<DanmakuServiceId, DanmakuTextConversion> = emptyMap(),
) {
    /**
     * 返回 [serviceId] 实际使用的转换目标.
     */
    fun targetFor(serviceId: DanmakuServiceId): DanmakuTextConversion =
        overrides[serviceId] ?: global

    companion object {
        val Default = DanmakuTextConversionSettings()
    }
}
