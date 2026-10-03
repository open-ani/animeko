/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.domain.settings.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.preference.WatchTogetherSettings
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.remote.settings.generated.models.RemoteError

/** Each discriminator selects one existing preference model and its TV repository. */
@Serializable
sealed interface RemotePreference {
    @Serializable
    @SerialName("videoScaffoldConfig")
    data class VideoScaffold(val value: VideoScaffoldConfig) : RemotePreference

    @Serializable
    @SerialName("playerKernelConfig")
    data class PlayerKernel(val value: PlayerKernelConfig) : RemotePreference

    @Serializable
    @SerialName("danmakuFilterConfig")
    data class DanmakuFilter(val value: DanmakuFilterConfig) : RemotePreference

    @Serializable
    @SerialName("mediaSelectorSettings")
    data class MediaSelector(val value: MediaSelectorSettings) : RemotePreference

    @Serializable
    @SerialName("defaultMediaPreference")
    data class MediaSelection(val value: MediaPreference) : RemotePreference

    @Serializable
    @SerialName("videoResolverSettings")
    data class VideoResolver(val value: VideoResolverSettings) : RemotePreference

    @Serializable
    @SerialName("watchTogetherSettings")
    data class WatchTogether(val value: WatchTogetherSettings) : RemotePreference

    @Serializable
    @SerialName("mediaCacheSettings")
    data class MediaCache(val value: MediaCacheSettings) : RemotePreference
}

/** Identifies a preference in revisions, backups and restore results. */
val RemotePreference.key: String
    get() =
        when (this) {
            is RemotePreference.VideoScaffold -> "videoScaffoldConfig"
            is RemotePreference.PlayerKernel -> "playerKernelConfig"
            is RemotePreference.DanmakuFilter -> "danmakuFilterConfig"
            is RemotePreference.MediaSelector -> "mediaSelectorSettings"
            is RemotePreference.MediaSelection -> "defaultMediaPreference"
            is RemotePreference.VideoResolver -> "videoResolverSettings"
            is RemotePreference.WatchTogether -> "watchTogetherSettings"
            is RemotePreference.MediaCache -> "mediaCacheSettings"
        }

@Serializable data class VersionedValue<T>(val revision: String, val value: T)

@Serializable
data class RemotePreferencesSnapshot(
    val videoScaffoldConfig: VersionedValue<VideoScaffoldConfig>,
    val playerKernelConfig: VersionedValue<PlayerKernelConfig>,
    val danmakuFilterConfig: VersionedValue<DanmakuFilterConfig>,
    val mediaSelectorSettings: VersionedValue<MediaSelectorSettings>,
    val defaultMediaPreference: VersionedValue<MediaPreference>,
    val videoResolverSettings: VersionedValue<VideoResolverSettings>,
    val watchTogetherSettings: VersionedValue<WatchTogetherSettings>,
    val mediaCacheSettings: VersionedValue<MediaCacheSettings>,
) {
    fun revisionOf(value: RemotePreference): String =
        when (value) {
            is RemotePreference.VideoScaffold -> videoScaffoldConfig.revision
            is RemotePreference.PlayerKernel -> playerKernelConfig.revision
            is RemotePreference.DanmakuFilter -> danmakuFilterConfig.revision
            is RemotePreference.MediaSelector -> mediaSelectorSettings.revision
            is RemotePreference.MediaSelection -> defaultMediaPreference.revision
            is RemotePreference.VideoResolver -> videoResolverSettings.revision
            is RemotePreference.WatchTogether -> watchTogetherSettings.revision
            is RemotePreference.MediaCache -> mediaCacheSettings.revision
        }

    fun values(): List<RemotePreference> =
        listOf(
            RemotePreference.VideoScaffold(videoScaffoldConfig.value),
            RemotePreference.PlayerKernel(playerKernelConfig.value),
            RemotePreference.DanmakuFilter(danmakuFilterConfig.value),
            RemotePreference.MediaSelector(mediaSelectorSettings.value),
            RemotePreference.MediaSelection(defaultMediaPreference.value),
            RemotePreference.VideoResolver(videoResolverSettings.value),
            RemotePreference.WatchTogether(watchTogetherSettings.value),
            RemotePreference.MediaCache(mediaCacheSettings.value),
        )
}

/** Form metadata contains no engine instances, HTTP clients, or executable validation callbacks. */
@Serializable
data class RemoteSourceTemplate(
    val factoryId: String,
    val name: String,
    val description: String,
    val allowMultiple: Boolean,
    val parameters: List<RemoteSourceParameter>,
    val iconUrl: String? = null,
)

@Serializable
data class RemoteSourceParameter(
    val name: String,
    val description: String,
    val kind: String,
    val defaultValue: String,
    val choices: List<String> = emptyList(),
    val required: Boolean = false,
    val visibleWhen: String? = null,
    val acceptedValues: Set<String> = emptySet(),
)

@Serializable
data class SettingsSnapshot(
    val preferences: RemotePreferencesSnapshot,
    val mediaSources: VersionedValue<List<MediaSourceSave>>,
    val subscriptions: VersionedValue<List<MediaSourceSubscription>>,
    val danmakuFilters: VersionedValue<List<DanmakuRegexFilter>>,
    val templates: List<RemoteSourceTemplate>,
)

@Serializable
data class PreferenceRequest(
    override val operationId: String,
    override val baseRevision: String,
    val value: RemotePreference,
) : RemoteCommandRequest

sealed interface RemoteCommandRequest {
    val operationId: String
    val baseRevision: String?
}

@Serializable
data class MediaSourceRequest(
    override val operationId: String,
    val command: MediaSourceCommand,
    override val baseRevision: String? = null,
) : RemoteCommandRequest

@Serializable
data class DanmakuFilterRequest(
    override val operationId: String,
    val command: ReplaceDanmakuFilters,
    override val baseRevision: String? = null,
) : RemoteCommandRequest

@Serializable
data class BackupRequest(
    override val operationId: String,
    val command: RemoteBackupCommand,
    override val baseRevision: String? = null,
) : RemoteCommandRequest

/** Operation polling uses the same typed payload as the initial response. */
@Serializable
sealed interface RemoteOperationPayload {
    @Serializable @SerialName("applied") data object Applied : RemoteOperationPayload

    @Serializable
    @SerialName("sourceExport")
    data class SourceExport(val text: String) : RemoteOperationPayload

    @Serializable
    @SerialName("backupExport")
    data class BackupExport(val value: RemoteSettingsBackup) : RemoteOperationPayload

    @Serializable
    @SerialName("backupPreview")
    data class BackupPreview(val value: RemoteBackupPreview) : RemoteOperationPayload

    @Serializable
    @SerialName("backupApplied")
    data class BackupApplied(val value: RemoteBackupResult) : RemoteOperationPayload
}

@Serializable
data class OperationResult(
    val operationId: String,
    val status: String,
    val result: RemoteOperationPayload? = null,
    val error: RemoteError? = null,
)
