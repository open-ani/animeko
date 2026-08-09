/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences

/**
 * 内存 [DataStore]<[Preferences]> 测试夹具, 无真实 IO, 可安全用于 runTest 虚拟时间.
 *
 * 模拟跨会话持久化时, 复用同一个返回实例 (app 级 store 生命周期长于 session), 在其上重建 repository/session 即可.
 */
fun createTestPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
): DataStore<Preferences> = MemoryDataStore(initial)
