/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.builtins.ListSerializer
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.IosContext
import me.him188.ani.app.platform.asIosContext
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.resolve
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Must not be stored
 */
actual fun Context.createPlatformDataStoreManager(): PlatformDataStoreManager =
    IosPlatformDataStoreManager(this.asIosContext())

private class IosPlatformDataStoreManager(
    private val context: IosContext,
) : PlatformDataStoreManager() {
    override val legacyTokenStore: DataStore<Preferences> by lazy {
        createPreferencesDataStore("token")
    }
    override val preferencesStore: DataStore<Preferences> by lazy {
        createPreferencesDataStore("preferences")
    }
    override val preferredAllianceStore: DataStore<Preferences> by lazy {
        createPreferencesDataStore("preferredAlliance")
    }

    override val mediaCacheMetadataStore: DataStore<List<MediaCacheSave>> by lazy {
        DataStoreFactory.create(
            serializer = ListSerializer(MediaCacheSave.serializer()).asDataStoreSerializer({ emptyList() }),
            produceFile = { resolveDataStoreFile("mediaCacheMetadataV2") },
            corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
        )
    }

    override fun resolveDataStoreFile(name: String): SystemPath =
        context.files.datastoreDir
            .apply { createDirectories() }
            .resolve(name)

    private fun createPreferencesDataStore(name: String): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        storage = OkioStorage(
            FileSystem.Companion.SYSTEM,
            PreferencesSerializer,
            producePath = {
                resolveDataStoreFile(name).absolutePath.toPath()
            },
        ),
        corruptionHandler = ReplaceFileCorruptionHandler {
            mutablePreferencesOf()
        },
    )
}

