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
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.serialization.builtins.ListSerializer
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.platform.Context
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.io.toKtPath

actual val Context.dataStoresImpl: PlatformDataStoreManager
    get() = PlatformDataStoreManagerAndroid(this)

internal class PlatformDataStoreManagerAndroid(
    private val context: Context,
) : PlatformDataStoreManager() {
    override fun resolveDataStoreFile(name: String): SystemPath {
        return context.applicationContext.dataStoreFile(name).toKtPath().inSystem
    }

    private val Context.tokenStoreImpl by preferencesDataStore(
        "tokens",
        corruptionHandler = replaceFileCorruptionHandlerForPreferences,
    )
    override val tokenStore: DataStore<Preferences> get() = context.tokenStoreImpl

    private val Context.preferencesStoreImpl by preferencesDataStore(
        "preferences",
        corruptionHandler = replaceFileCorruptionHandlerForPreferences,
    )
    override val preferencesStore: DataStore<Preferences> get() = context.preferencesStoreImpl

    private val Context.preferredAlliancesStoreImpl by preferencesDataStore(
        "preferredAlliances",
        corruptionHandler = replaceFileCorruptionHandlerForPreferences,
    )
    override val preferredAllianceStore: DataStore<Preferences> get() = context.preferredAlliancesStoreImpl

    private val Context.mediaCacheMetadataStoreImpl by lazy {
        MultiProcessDataStoreFactory.create(
            serializer = ListSerializer(MediaCacheSave.serializer()).asDataStoreSerializer({ emptyList() }),
            produceFile = { resolveDataStoreFile("mediaCacheMetadata").toFile() },
            corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
        )
    }

    override val mediaCacheMetadataStore: DataStore<List<MediaCacheSave>>
        get() = context.mediaCacheMetadataStoreImpl
}
