/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models

/**
 * 游标翻页的一页.
 *
 * @property nextCursor 下一页的游标, 原样传回服务端. 为 `null` 表示没有更多.
 */
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
)
