/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.media

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.persistent.DataStoreJson
import me.him188.ani.app.data.persistent.database.dao.PreferredWebMediaSource
import me.him188.ani.app.data.persistent.database.dao.PreferredWebMediaSourceDao
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.mp.KoinPlatform

interface EpisodePreferencesRepository : KoinComponent {
    /**
     * 获取用户对这个条目的设置, 当不存在时返回全局默认设置 [SettingsRepository.defaultMediaPreference]
     * @see SettingsRepository.defaultMediaPreference
     */
    fun mediaPreferenceFlow(subjectId: Int): Flow<MediaPreference>
    suspend fun setMediaPreference(subjectId: Int, mediaPreference: MediaPreference)

    suspend fun setPreferredWebMediaSource(subjectId: Int, webSourceId: String)

    fun getPreferredWebMediaSource(subjectId: Int): Flow<String?>

    suspend fun removePreferredWebMediaSource(subjectId: Int)
}

class EpisodePreferencesRepositoryImpl(
    private val store: DataStore<Preferences>,
    private val preferredWebMediaSourceDao: PreferredWebMediaSourceDao,
    private val defaultMediaPreference: Flow<MediaPreference> = KoinPlatform.getKoin()
        .get<SettingsRepository>().defaultMediaPreference.flow
) : EpisodePreferencesRepository, KoinComponent {
    private val logger = logger<EpisodePreferencesRepositoryImpl>()
    private val json = DataStoreJson

    override fun mediaPreferenceFlow(subjectId: Int): Flow<MediaPreference> {
        return store.data.map {
            it[stringPreferencesKey(subjectId.toString())]
        }.map {
            if (it.isNullOrBlank()) {
//                logger.info { "Loaded user MediaPreference for subject $subjectId: null, use default" }
                return@map defaultMediaPreference.first()
            }
            val res = kotlin.runCatching {
                json.decodeFromString(MediaPreference.serializer(), it)
            }.getOrNull() ?: defaultMediaPreference.first()
//            logger.info { "Loaded user MediaPreference for subject $subjectId: $res" }
            res
        }
    }

    override suspend fun setMediaPreference(subjectId: Int, mediaPreference: MediaPreference) {
        logger.info { "Saved user MediaPreference for subject $subjectId: $mediaPreference" }
        store.edit {
            it[stringPreferencesKey(subjectId.toString())] =
                json.encodeToString(MediaPreference.serializer(), mediaPreference)
        }
    }

    override suspend fun setPreferredWebMediaSource(subjectId: Int, webSourceId: String) {
        logger.info { "Saved user preferred web source for subject $subjectId to $webSourceId" }
        preferredWebMediaSourceDao.setPreferredMediaSource(PreferredWebMediaSource(subjectId, webSourceId))
    }

    override fun getPreferredWebMediaSource(subjectId: Int): Flow<String?> {
        return preferredWebMediaSourceDao.getPreferredMediaSourceId(subjectId)
    }

    override suspend fun removePreferredWebMediaSource(subjectId: Int) {
        preferredWebMediaSourceDao.deletePreferredMediaSource(subjectId)
    }
}