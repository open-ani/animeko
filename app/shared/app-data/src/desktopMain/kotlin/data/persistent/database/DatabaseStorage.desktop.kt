/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import androidx.room.Room
import androidx.room.RoomDatabase
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.DesktopContext

actual fun Context.createDatabaseBuilder(): RoomDatabase.Builder<AniDatabase> {
    this as DesktopContext
    return Room.databaseBuilder<AniDatabase>(
        name = dataDir.resolve("ani_room_database.db").absolutePath,
    )
}