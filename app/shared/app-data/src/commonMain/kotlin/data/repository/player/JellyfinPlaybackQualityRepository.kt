/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.persistent.DataStoreJson
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackQuality

interface JellyfinPlaybackQualityRepository {
    fun preferenceFlow(mediaSourceId: String): Flow<JellyfinPlaybackQuality>

    suspend fun get(mediaSourceId: String): JellyfinPlaybackQuality

    suspend fun set(mediaSourceId: String, quality: JellyfinPlaybackQuality)
}

class JellyfinPlaybackQualityRepositoryImpl(
    private val store: DataStore<Preferences>,
) : JellyfinPlaybackQualityRepository {
    override fun preferenceFlow(mediaSourceId: String): Flow<JellyfinPlaybackQuality> {
        return store.data.map { preferences ->
            preferences[KEY]
                ?.let(::decode)
                ?.byMediaSourceId
                ?.get(mediaSourceId)
                ?: JellyfinPlaybackQuality.Auto
        }
    }

    override suspend fun get(mediaSourceId: String): JellyfinPlaybackQuality {
        return preferenceFlow(mediaSourceId).first()
    }

    override suspend fun set(mediaSourceId: String, quality: JellyfinPlaybackQuality) {
        store.edit { preferences ->
            val current = preferences[KEY]?.let(::decode) ?: JellyfinPlaybackQualitySave()
            preferences[KEY] = DataStoreJson.encodeToString(
                JellyfinPlaybackQualitySave.serializer(),
                current.copy(byMediaSourceId = current.byMediaSourceId + (mediaSourceId to quality)),
            )
        }
    }

    private fun decode(value: String): JellyfinPlaybackQualitySave? {
        return runCatching {
            DataStoreJson.decodeFromString(JellyfinPlaybackQualitySave.serializer(), value)
        }.getOrNull()
    }

    private companion object {
        val KEY = stringPreferencesKey("jellyfinPlaybackQualityByMediaSource")
    }
}

@Serializable
private data class JellyfinPlaybackQualitySave(
    val byMediaSourceId: Map<String, JellyfinPlaybackQuality> = emptyMap(),
)
