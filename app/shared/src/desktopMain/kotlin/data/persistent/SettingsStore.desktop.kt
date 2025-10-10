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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.DesktopContext
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toKtPath

actual fun Context.createPlatformDataStoreManager(): PlatformDataStoreManager =
    PlatformDataStoreManagerDesktop(this as DesktopContext)

val Context.dataStoresDesktop: PlatformDataStoreManagerDesktop get() = dataStores as PlatformDataStoreManagerDesktop

class PlatformDataStoreManagerDesktop(
    private val context: DesktopContext,
) : PlatformDataStoreManager() {
    override val legacyTokenStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(corruptionHandler = replaceFileCorruptionHandlerForPreferences) {
            context.dataStoreDir.resolve("tokens.preferences_pb")
        }
    override val preferencesStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(corruptionHandler = replaceFileCorruptionHandlerForPreferences) {
            context.dataStoreDir.resolve("settings.preferences_pb")
        }
    override val preferredAllianceStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(corruptionHandler = replaceFileCorruptionHandlerForPreferences) {
            context.dataStoreDir.resolve("preferredAllianceStore.preferences_pb")
        }

    override val mediaCacheMetadataStore: DataStore<List<MediaCacheSave>> by lazy {
        DataStoreFactory.create(
            serializer = ListSerializer(MediaCacheSave.serializer()).asDataStoreSerializer({ emptyList() }),
            produceFile = { context.dataStoreDir.resolve("mediaCacheMetadata") },
            corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
        )
    }

    /**
     * @since 5.0.2
     */
    val firebaseDataStore by lazy {
        DataStoreFactory.create(
            serializer = MapSerializer(
                String.serializer(),
                String.serializer(),
            ).asDataStoreSerializer({ emptyMap() }),
            produceFile = { context.dataStoreDir.resolve("firebaseDataStore") },
            corruptionHandler = ReplaceFileCorruptionHandler { emptyMap() },
        )
    }

    override fun resolveDataStoreFile(name: String): SystemPath = context.dataStoreDir.resolve(name).toKtPath().inSystem
}
