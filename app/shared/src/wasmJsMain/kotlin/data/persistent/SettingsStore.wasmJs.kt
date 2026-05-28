package me.him188.ani.app.data.persistent

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.builtins.ListSerializer
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.platform.Context
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import kotlinx.io.files.Path

actual fun Context.createPlatformDataStoreManager(): PlatformDataStoreManager = WebPlatformDataStoreManager()

class WebPlatformDataStoreManager : PlatformDataStoreManager() {
    override val legacyTokenStore: DataStore<Preferences> = preferencesStore("tokens.preferences_pb")
    override val preferencesStore: DataStore<Preferences> = preferencesStore("settings.preferences_pb")
    override val preferredAllianceStore: DataStore<Preferences> =
        preferencesStore("preferredAllianceStore.preferences_pb")

    override val mediaCacheMetadataStore: DataStore<List<MediaCacheSave>> by lazy {
        DataStoreFactory.create(
            serializer = ListSerializer(MediaCacheSave.serializer()).asDataStoreSerializer({ emptyList() }),
            produceFile = { resolveDataStoreFile("mediaCacheMetadataV2") },
            corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
        )
    }

    override fun resolveDataStoreFile(name: String): SystemPath = Path("/animeko-web/$name").inSystem

    private fun preferencesStore(name: String): DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        return object : DataStore<Preferences> {
            override val data: Flow<Preferences> = state
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val updated = transform(state.value)
                state.value = updated
                return updated
            }
        }
    }
}
