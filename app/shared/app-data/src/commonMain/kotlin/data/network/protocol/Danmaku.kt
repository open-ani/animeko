/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network.protocol

import kotlinx.serialization.Serializable

@Serializable
data class DanmakuPostRequest(
    val danmakuInfo: DanmakuInfo,
)

@Serializable
data class DanmakuGetResponse(
    val danmakuList: List<Danmaku>,
)

@Serializable
data class Danmaku(
    val id: String, // unique danmaku id
    val senderId: String, // unique sender id
    val danmakuInfo: DanmakuInfo,
)

@Serializable
data class DanmakuInfo(
    val playTime: Long, // in milliseconds
    val color: Int, // RGB
    val text: String,
    val location: DanmakuLocation,
)

@Serializable
enum class DanmakuLocation {
    TOP,
    BOTTOM,
    NORMAL,
}