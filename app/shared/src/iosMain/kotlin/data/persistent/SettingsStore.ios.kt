/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import me.him188.ani.app.platform.Context
import me.him188.ani.utils.io.SystemDocumentDir
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.resolve

/**
 * Must not be stored
 */
actual val Context.dataStoresImpl: PlatformDataStoreManager
    get() = IosPlatformDataStoreManager

object IosPlatformDataStoreManager : PlatformDataStoreManager() {
    override val tokenStore: DataStore<Preferences> by lazy {
        MemoryDataStore(mutablePreferencesOf())
    }
    override val preferencesStore: DataStore<Preferences> by lazy {
        MemoryDataStore(mutablePreferencesOf())
    }
    override val preferredAllianceStore: DataStore<Preferences> by lazy {
        MemoryDataStore(mutablePreferencesOf())
    }

    override fun resolveDataStoreFile(name: String): SystemPath =
        SystemDocumentDir.resolve("datastores")
            .apply { createDirectories() }
            .resolve(name)
}
