/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * 供 desktopTest 使用的内存 [AniDatabase] 夹具, driver 与 query context 配置对齐生产
 * (`CommonKoinModule` 中的 `single<AniDatabase>`).
 */
fun createTestAniDatabase(
    queryCoroutineContext: CoroutineContext = Dispatchers.Default,
): AniDatabase {
    return Room.inMemoryDatabaseBuilder<AniDatabase> { AniDatabaseConstructor.initialize() }
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(queryCoroutineContext)
        .build()
}
