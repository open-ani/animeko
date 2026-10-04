/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings.remote

import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.preference.DanmakuCacheStrategy
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.preference.WatchTogetherSettings
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.remote.settings.RemoteSettingsProtocol

/** The serialized value is used only to compute an opaque resource revision. */
fun interface RemoteSettingsRevision {
    fun of(resource: String, encodedValue: String): String
}

/** Typed TV settings access; projections preserve local-only state during remote writes. */
class RemotePreferenceRegistry(
    repository: SettingsRepository,
    private val revisions: RemoteSettingsRevision,
) {
    private val videoScaffoldConfig =
        Entry(
            "videoScaffoldConfig",
            repository.videoScaffoldConfig,
            VideoScaffoldConfig.serializer(),
            validator = {
                checkRemote(
                    it.playbackSpeed in 0.25f..4f &&
                        it.fastForwardSpeed in 0.25f..4f &&
                        it.minPlaybackSpeed in 0.25f..4f &&
                        it.maxPlaybackSpeed in 0.25f..4f &&
                        it.minPlaybackSpeed < it.maxPlaybackSpeed,
                    message = "Invalid playback speed range",
                )
                checkRemote(
                    it.opEdSkipDuration.isFinite() && it.opEdSkipDuration.inWholeSeconds in 0..600,
                    message = "Invalid opening or ending skip duration",
                )
            },
        )

    private val playerKernelConfig =
        Entry(
            "playerKernelConfig",
            repository.playerKernelConfig,
            PlayerKernelConfig.serializer(),
            project = {
                PlayerKernelConfig(
                    exoPlayerInitEffectGraphInAdvance = it.exoPlayerInitEffectGraphInAdvance
                )
            },
            merge = { current, proposed ->
                current.copy(
                    exoPlayerInitEffectGraphInAdvance = proposed.exoPlayerInitEffectGraphInAdvance
                )
            },
        )

    private val danmakuFilterConfig =
        Entry(
            "danmakuFilterConfig",
            repository.danmakuFilterConfig,
            DanmakuFilterConfig.serializer(),
        )

    private val mediaSelectorSettings =
        Entry(
            "mediaSelectorSettings",
            repository.mediaSelectorSettings,
            MediaSelectorSettings.serializer(),
            validator = {
                checkRemote(
                    it.preferKind == MediaSourceKind.WEB,
                    message = "TV supports web sources only",
                )
            },
        )

    private val defaultMediaPreference =
        Entry(
            "defaultMediaPreference",
            repository.defaultMediaPreference,
            MediaPreference.serializer(),
        )

    private val videoResolverSettings =
        Entry(
            "videoResolverSettings",
            repository.videoResolverSettings,
            VideoResolverSettings.serializer(),
        )

    private val watchTogetherSettings =
        Entry(
            "watchTogetherSettings",
            repository.watchTogetherSettings,
            WatchTogetherSettings.serializer(),
            project = { WatchTogetherSettings(enabled = it.enabled, followHost = it.followHost) },
            merge = { current, proposed ->
                current.copy(enabled = proposed.enabled, followHost = proposed.followHost)
            },
        )

    private val mediaCacheSettings =
        Entry(
            "mediaCacheSettings",
            repository.mediaCacheSettings,
            MediaCacheSettings.serializer(),
            project = { MediaCacheSettings(danmakuCacheStrategy = it.danmakuCacheStrategy) },
            merge = { current, proposed ->
                current.copy(danmakuCacheStrategy = proposed.danmakuCacheStrategy)
            },
            validator = {
                checkRemote(
                    it.danmakuCacheStrategy != DanmakuCacheStrategy.CACHE_ON_MEDIA_CACHE,
                    message = "TV does not support media download caching",
                )
            },
        )

    suspend fun snapshot() =
        RemotePreferencesSnapshot(
            videoScaffoldConfig = videoScaffoldConfig.read(),
            playerKernelConfig = playerKernelConfig.read(),
            danmakuFilterConfig = danmakuFilterConfig.read(),
            mediaSelectorSettings = mediaSelectorSettings.read(),
            defaultMediaPreference = defaultMediaPreference.read(),
            videoResolverSettings = videoResolverSettings.read(),
            watchTogetherSettings = watchTogetherSettings.read(),
            mediaCacheSettings = mediaCacheSettings.read(),
        )

    fun validate(value: RemotePreference) = bind(value).validate()

    /**
     * @param sent the fields of [value] as the sender wrote them. Fields that are missing, because
     *   the sender is an older version that does not know them, keep the TV's values. Null writes
     *   [value] as a whole.
     */
    suspend fun write(expectedRevision: String, value: RemotePreference, sent: JsonObject? = null) =
        bind(value).write(expectedRevision, sent)

    private fun bind(value: RemotePreference): Bound<*> =
        when (value) {
            is RemotePreference.VideoScaffold -> Bound(videoScaffoldConfig, value.value)
            is RemotePreference.PlayerKernel -> Bound(playerKernelConfig, value.value)
            is RemotePreference.DanmakuFilter -> Bound(danmakuFilterConfig, value.value)
            is RemotePreference.MediaSelector -> Bound(mediaSelectorSettings, value.value)
            is RemotePreference.MediaSelection -> Bound(defaultMediaPreference, value.value)
            is RemotePreference.VideoResolver -> Bound(videoResolverSettings, value.value)
            is RemotePreference.WatchTogether -> Bound(watchTogetherSettings, value.value)
            is RemotePreference.MediaCache -> Bound(mediaCacheSettings, value.value)
        }

    /** A proposed value together with the entry it is stored in. */
    private class Bound<T>(private val entry: Entry<T>, private val value: T) {
        fun validate() = entry.validate(value)

        suspend fun write(expectedRevision: String, sent: JsonObject?) =
            entry.write(expectedRevision, value, sent)
    }

    private inner class Entry<T>(
        val key: String,
        val settings: Settings<T>,
        val serializer: KSerializer<T>,
        val project: (T) -> T = { it },
        val merge: (T, T) -> T = { _, proposed -> proposed },
        val validator: (T) -> Unit = {},
    ) {
        /** The last projected value and its revision. Polling reads the same value repeatedly. */
        @Volatile private var cached: Pair<T, String>? = null

        private fun revision(value: T): String {
            val projected = project(value)
            cached?.let { (cachedValue, revision) -> if (cachedValue == projected) return revision }
            return revisions
                .of(key, RemoteSettingsProtocol.json.encodeToString(serializer, projected))
                .also { cached = projected to it }
        }

        suspend fun read(): VersionedValue<T> {
            val value = project(settings.flow.first())
            return VersionedValue(revision(value), value)
        }

        fun validate(value: T) {
            checkRemote(project(value) == value, message = "Unsupported preference fields")
            validator(value)
        }

        suspend fun write(expectedRevision: String, proposed: T, sent: JsonObject?) {
            settings.update {
                checkRemote(
                    revision(this) == expectedRevision,
                    "REVISION_CONFLICT",
                    "Settings revision has changed",
                )
                val value = if (sent == null) proposed else withSentFields(project(this), sent)
                validate(value)
                merge(this, value)
            }
        }

        private fun withSentFields(current: T, sent: JsonObject): T {
            val json = RemoteSettingsProtocol.json
            return json.decodeFromJsonElement(
                serializer,
                overlay(json.encodeToJsonElement(serializer, current), sent, serializer.descriptor),
            )
        }
    }
}

/**
 * [sent] on top of [current]. Only classes are merged field by field; lists, maps and polymorphic
 * values are taken from [sent] as a whole.
 */
private fun overlay(
    current: JsonElement,
    sent: JsonElement,
    descriptor: SerialDescriptor,
): JsonElement {
    if (descriptor.kind != StructureKind.CLASS || current !is JsonObject || sent !is JsonObject)
        return sent
    return JsonObject(
        current +
            sent.mapValues { (name, value) ->
                val index = descriptor.getElementIndex(name)
                val existing = current[name]
                if (index >= 0 && existing != null)
                    overlay(existing, value, descriptor.getElementDescriptor(index))
                else value
            }
    )
}
