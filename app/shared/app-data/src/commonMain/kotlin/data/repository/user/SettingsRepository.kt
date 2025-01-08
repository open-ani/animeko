/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.user

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.danmaku.DanmakuConfigSerializer
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.DanmakuSettings
import me.him188.ani.app.data.models.preference.DebugSettings
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.OneshotActionConfig
import me.him188.ani.app.data.models.preference.ProfileSettings
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.UpdateSettings
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger

/**
 * 所有设置
 */
interface SettingsRepository {
    val danmakuEnabled: Settings<Boolean>
    val danmakuConfig: Settings<DanmakuConfig>
    val danmakuFilterConfig: Settings<DanmakuFilterConfig>

    val mediaSelectorSettings: Settings<MediaSelectorSettings>

    /**
     * 全局默认选择资源的偏好设置
     *
     * @see EpisodePreferencesRepository
     */
    val defaultMediaPreference: Settings<MediaPreference>

    /**
     * @since 3.5
     */
    val profileSettings: Settings<ProfileSettings>
    val proxySettings: Settings<ProxySettings>
    val mediaCacheSettings: Settings<MediaCacheSettings>
    val danmakuSettings: Settings<DanmakuSettings>
    val uiSettings: Settings<UISettings>
    val themeSettings: Settings<ThemeSettings>
    val updateSettings: Settings<UpdateSettings>
    val videoScaffoldConfig: Settings<VideoScaffoldConfig>

    val videoResolverSettings: Settings<VideoResolverSettings>
    val anitorrentConfig: Settings<AnitorrentConfig>
    val torrentPeerConfig: Settings<TorrentPeerConfig>

    val oneshotActionConfig: Settings<OneshotActionConfig>

    val debugSettings: Settings<DebugSettings>
}

@Stable
interface Settings<T> {
    val flow: Flow<T>
    suspend fun set(value: T)
    suspend fun update(update: T.() -> T) = set(flow.first().update())
}

class PreferencesRepositoryImpl(
    private val preferences: DataStore<Preferences>,
) : SettingsRepository {
    private val format = Json {
        ignoreUnknownKeys = true
    }

    inner class BooleanPreference(
        val name: String,
        private val default: Boolean,
    ) : Settings<Boolean> {
        private val key = booleanPreferencesKey(name)
        override val flow: Flow<Boolean> = preferences.data.map { it[key] ?: default }
        override suspend fun update(update: (Boolean) -> Boolean) {
            preferences.edit {
                it[key] = update(it[key] ?: default)
            }
        }

        override suspend fun set(value: Boolean) {
            preferences.edit { it[key] = value }
        }
    }

    inner class SerializablePreference<T : Any>(
        val name: String,
        private val serializer: KSerializer<T>,
        private val default: () -> T,
    ) : Settings<T> {
        private val key = stringPreferencesKey(name)
        override val flow: Flow<T> = preferences.data
            .map { it[key] }
            .distinctUntilChanged()
            .map { string ->
                if (string == null) {
                    default()
                } else try {
                    format.decodeFromString(serializer, string)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to decode preference '$name'. Using default. Failed json: $string" }
                    default()
                }
            }

        override suspend fun update(update: (T) -> T) {
            logger.debug { "Updating preference '$key' with lambda" }
            preferences.edit { pref ->
                pref[key] = format.encodeToString(
                    serializer,
                    update(
                        pref[key]?.let { format.decodeFromString(serializer, it) }
                            ?: default(),
                    ),
                )
            }
        }

        override suspend fun set(value: T) {
            logger.debug { "Updating preference '$key' with: $value" }
            preferences.edit {
                it[key] = format.encodeToString(serializer, value)
            }
        }
    }

    override val danmakuEnabled: Settings<Boolean> = BooleanPreference("danmaku_enabled", default = true)
    override val danmakuConfig: Settings<DanmakuConfig> =
        SerializablePreference("danmaku_config", DanmakuConfigSerializer, default = { DanmakuConfig.Default })
    override val danmakuFilterConfig: Settings<DanmakuFilterConfig> =
        SerializablePreference(
            "danmaku_filter_config",
            DanmakuFilterConfig.serializer(),
            default = { DanmakuFilterConfig.Default },
        )
    override val mediaSelectorSettings: Settings<MediaSelectorSettings> = SerializablePreference(
        "mediaSelectorSettings",
        MediaSelectorSettings.serializer(),
        default = { MediaSelectorSettings.Default },
    )
    override val defaultMediaPreference: Settings<MediaPreference> =
        SerializablePreference(
            "defaultMediaPreference",
            MediaPreference.serializer(),
            default = { MediaPreference.PlatformDefault },
        )
    override val profileSettings: Settings<ProfileSettings> = SerializablePreference(
        "profileSettings",
        ProfileSettings.serializer(),
        default = { ProfileSettings.Default },
    )
    override val proxySettings: Settings<ProxySettings> = SerializablePreference(
        "proxyPreferences",
        ProxySettings.serializer(),
        default = { ProxySettings.Default },
    )
    override val mediaCacheSettings: Settings<MediaCacheSettings> = SerializablePreference(
        "cachePreferences",
        MediaCacheSettings.serializer(),
        default = { MediaCacheSettings.Default },
    )
    override val danmakuSettings: Settings<DanmakuSettings> = SerializablePreference(
        "danmakuSettings",
        DanmakuSettings.serializer(),
        default = { DanmakuSettings.Default },
    )
    override val uiSettings: Settings<UISettings> = SerializablePreference(
        "uiSettings",
        UISettings.serializer(),
        default = { UISettings.Default },
    )
    override val themeSettings: Settings<ThemeSettings> = SerializablePreference(
        "themeSettings",
        ThemeSettings.serializer(),
        default = { ThemeSettings.Default },
    )
    override val updateSettings: Settings<UpdateSettings> = SerializablePreference(
        "updateSettings",
        UpdateSettings.serializer(),
        default = { UpdateSettings.Default },
    )
    override val videoScaffoldConfig: Settings<VideoScaffoldConfig> = SerializablePreference(
        "videoScaffoldConfig",
        VideoScaffoldConfig.serializer(),
        default = { VideoScaffoldConfig.Default },
    )
    override val videoResolverSettings: Settings<VideoResolverSettings> = SerializablePreference(
        "videoResolverSettings",
        VideoResolverSettings.serializer(),
        default = { VideoResolverSettings.Default },
    )
    override val anitorrentConfig: Settings<AnitorrentConfig> = SerializablePreference(
        "anitorrentConfig",
        AnitorrentConfig.serializer(),
        default = { AnitorrentConfig.Default },
    )

    override val torrentPeerConfig: Settings<TorrentPeerConfig> = SerializablePreference(
        "torrentPeerConfig",
        TorrentPeerConfig.serializer(),
        default = { TorrentPeerConfig.Default }
    )

    override val oneshotActionConfig: Settings<OneshotActionConfig> = SerializablePreference(
        "oneshotActionConfig",
        OneshotActionConfig.serializer(),
        default = { OneshotActionConfig.Default },
    )

    override val debugSettings: Settings<DebugSettings> = SerializablePreference(
        "debugSettings",
        DebugSettings.serializer(),
        default = { DebugSettings.Default },
    )

    private companion object {
        private val logger = logger<SettingsRepository>()
    }
}
