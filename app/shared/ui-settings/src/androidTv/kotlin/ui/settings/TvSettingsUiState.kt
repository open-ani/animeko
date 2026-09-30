/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig

data class TvSettingsUiState(
    val loaded: Boolean = false,
    val loadFailed: Boolean = false,
    val appearance: UISettings = UISettings.Default,
    val theme: ThemeSettings = ThemeSettings.Default,
    val danmakuEnabled: Boolean = true,
    val video: VideoScaffoldConfig = VideoScaffoldConfig.Default,
    val kernel: PlayerKernelConfig = PlayerKernelConfig.Default,
    val filter: DanmakuFilterConfig = DanmakuFilterConfig.Default,
    val regexFilters: List<DanmakuRegexFilter> = emptyList(),
    val preference: MediaPreference = MediaPreference.PlatformDefault,
    val selector: MediaSelectorSettings = MediaSelectorSettings.Default,
    val resolver: VideoResolverSettings = VideoResolverSettings.Default,
    val sources: List<TvSettingsSource> = emptyList(),
    val subscriptions: List<TvSettingsSubscription> = emptyList(),
    val libraries: List<TvSettingsLibrary> = emptyList(),
    val librariesLoading: Boolean = false,
    val librariesFailed: Boolean = false,
)

/** Source metadata and availability; source configuration is managed by the shared repository. */
data class TvSettingsSource(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val enabled: Boolean,
    val factory: String,
    val subscription: String?,
)

data class TvSettingsSubscription(val id: String, val url: String, val enabled: Boolean)

data class TvSettingsLibrary(
    val id: String,
    val name: String,
    val version: String,
    val website: String?,
    val license: String,
    val licenseText: String,
)

/** Updates are applied to the latest persisted value, preserving unrelated fields. */
sealed interface TvSettingsIntent {
    data class Appearance(val update: UISettings.() -> UISettings) : TvSettingsIntent
    data class Theme(val update: ThemeSettings.() -> ThemeSettings) : TvSettingsIntent
    data class Video(val update: VideoScaffoldConfig.() -> VideoScaffoldConfig) : TvSettingsIntent
    data class Kernel(val update: PlayerKernelConfig.() -> PlayerKernelConfig) : TvSettingsIntent
    data class Filter(val enabled: Boolean) : TvSettingsIntent
    data class Danmaku(val enabled: Boolean) : TvSettingsIntent
    data class Preference(val update: MediaPreference.() -> MediaPreference) : TvSettingsIntent
    data class Selector(val update: MediaSelectorSettings.() -> MediaSelectorSettings) : TvSettingsIntent
    data class Resolver(val update: VideoResolverSettings.() -> VideoResolverSettings) : TvSettingsIntent
    data class SourceEnabled(val id: String, val enabled: Boolean) : TvSettingsIntent
    data class SubscriptionEnabled(val id: String, val enabled: Boolean) : TvSettingsIntent
    data class SaveRegex(val filter: DanmakuRegexFilter, val isNew: Boolean) : TvSettingsIntent
    data class RemoveRegex(val filter: DanmakuRegexFilter) : TvSettingsIntent
    data class ImportRegex(val text: String) : TvSettingsIntent
    data object ExportRegex : TvSettingsIntent
    data object LoadLibraries : TvSettingsIntent
    data object Retry : TvSettingsIntent
}

sealed interface TvSettingsEvent {
    data class Copy(val text: String) : TvSettingsEvent
    data object ImportSucceeded : TvSettingsEvent
    data object ImportFailed : TvSettingsEvent
    data object SaveFailed : TvSettingsEvent
}
