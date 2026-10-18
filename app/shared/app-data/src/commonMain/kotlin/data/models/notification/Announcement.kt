/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.notification

import androidx.compose.runtime.Immutable

/**
 * 管理员发布的全局公告. 所有用户 (包括游客) 都能看到.
 *
 * @property id 服务端 ID (UUID 字符串).
 * @property tag 已知分类. 客户端不认识的分类为 [AnnouncementTag.UNKNOWN], 原始值见 [rawTag].
 * @property body 纯文本, 保留换行. 其中的网址由客户端自动转换为链接.
 * @property linkUrl 可选的 http(s) 链接.
 * @property pinned 置顶公告排在列表最前.
 * @property publishedAtMillis 发布时间, 毫秒时间戳.
 * @property read 当前用户是否已读. 游客为 `null`.
 */
@Immutable
data class Announcement(
    val id: String,
    val tag: AnnouncementTag,
    val rawTag: String,
    val title: String,
    val body: String,
    val linkUrl: String? = null,
    val pinned: Boolean = false,
    val publishedAtMillis: Long,
    val read: Boolean? = null,
)

/**
 * 公告分类. 服务端以字符串下发, 以后可能增加新的分类, 客户端不认识的值映射为 [UNKNOWN] 并按普通公告展示.
 *
 * @property wireValue 服务端协议中的取值. [UNKNOWN] 没有对应的取值.
 */
enum class AnnouncementTag(val wireValue: String?) {
    GENERAL("general"),
    MAINTENANCE("maintenance"),
    RELEASE("release"),
    COMMUNITY("community"),
    UNKNOWN(null),
    ;

    companion object {
        fun fromWireValue(value: String): AnnouncementTag =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}
