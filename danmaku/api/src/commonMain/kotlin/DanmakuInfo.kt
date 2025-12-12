/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api

import kotlinx.serialization.Serializable
import me.him188.ani.danmaku.api.DanmakuServiceId.Companion.Animeko
import me.him188.ani.danmaku.api.DanmakuServiceId.Companion.Dandanplay
import kotlin.jvm.JvmInline

data class DanmakuInfo(
    val id: String,
    val serviceId: DanmakuServiceId,
    val senderId: String,
    val content: DanmakuContent,
) {
    val playTimeMillis get() = content.playTimeMillis
    val color get() = content.color
    val text get() = content.text
    val location get() = content.location
}

@Serializable
data class DanmakuContent(
    val playTimeMillis: Long, // in milliseconds
    val color: Int, // RGB
    val text: String,
    val location: DanmakuLocation,
)


enum class DanmakuLocation {
    TOP,
    BOTTOM,

    /**
     * Floating
     */
    NORMAL,
}

/**
 * 表示一个弹幕源. 例如 [Animeko], [Dandanplay].
 *
 * ### 实现细节
 *
 * 这是一个 value class 而不是 sealed class: 为了未来扩展和旧代码的兼容性. 但请尽量使用 [DanmakuServiceId.Companion] 中的"常量", 而不要使用构造器.
 *
 * 如果添加了新的 service, 请同时更新 UI `renderDanmakuServiceId`.
 */
@JvmInline
value class DanmakuServiceId(
    val value: String,
) {
    companion object {
        // Sort alphabetically

        val Animeko = DanmakuServiceId("Animeko")
        val AcFun = DanmakuServiceId("Acfun")
        val Baha = DanmakuServiceId("Baha")
        val Bilibili = DanmakuServiceId("Bilibili")
        val Dandanplay = DanmakuServiceId("Dandanplay")
        val Tucao = DanmakuServiceId("Tucao")
    }
}
