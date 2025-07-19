/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import androidx.room.Dao
import androidx.room.RoomDatabase

/**
 * Calls the specified suspending [block] in a database transaction. The transaction will be marked
 * as successful unless an exception is thrown in the suspending [block] or the coroutine is
 * cancelled.
 *
 * Room will only perform at most one transaction at a time, additional transactions are queued and
 * executed on a first come, first serve order.
 *
 * Performing blocking database operations is not permitted in a coroutine scope other than the one
 * received by the suspending block. It is recommended that all [Dao] function invoked within the
 * [block] be suspending functions.
 *
 * The internal dispatcher used to execute the given [block] will block an utilize a thread from
 * Room's transaction executor until the [block] is complete.
 */
expect suspend fun <T> RoomDatabase.withTransaction(
    block: suspend () -> T
): T
