/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.models.preference.FullscreenSwitchMode
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.preference.VideoEnhancementDefaultMode
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.SupportedLocales
import me.him188.ani.app.ui.lang.renderLocale
import me.him188.ani.app.ui.lang.settings_app_danmaku_refresh_rate
import me.him188.ani.app.ui.lang.settings_app_language
import me.him188.ani.app.ui.lang.settings_app_not_show_done_and_dropped_subjects
import me.him188.ani.app.ui.lang.settings_app_nsfw_blur
import me.him188.ani.app.ui.lang.settings_app_nsfw_content
import me.him188.ani.app.ui.lang.settings_app_nsfw_display
import me.him188.ani.app.ui.lang.settings_app_nsfw_hide
import me.him188.ani.app.ui.lang.settings_danmaku_add_regex_filter
import me.him188.ani.app.ui.lang.settings_danmaku_export_to_clipboard
import me.him188.ani.app.ui.lang.settings_danmaku_import_from_clipboard
import me.him188.ani.app.ui.lang.settings_danmaku_import_title
import me.him188.ani.app.ui.lang.settings_danmaku_regex_filter_group
import me.him188.ani.app.ui.lang.settings_media_alliance
import me.him188.ani.app.ui.lang.settings_media_alliance_description
import me.him188.ani.app.ui.lang.settings_media_any
import me.him188.ani.app.ui.lang.settings_media_auto_enable_last
import me.him188.ani.app.ui.lang.settings_media_cache_ttl_15min
import me.him188.ani.app.ui.lang.settings_media_cache_ttl_1d
import me.him188.ani.app.ui.lang.settings_media_cache_ttl_1h
import me.him188.ani.app.ui.lang.settings_media_cache_ttl_30min
import me.him188.ani.app.ui.lang.settings_media_cache_ttl_5min
import me.him188.ani.app.ui.lang.settings_media_cache_ttl_6h
import me.him188.ani.app.ui.lang.settings_media_cache_ttl_none
import me.him188.ani.app.ui.lang.settings_media_fast_select_web
import me.him188.ani.app.ui.lang.settings_media_fast_select_web_description
import me.him188.ani.app.ui.lang.settings_media_hide_no_subtitle
import me.him188.ani.app.ui.lang.settings_media_image_captcha_auto_solve
import me.him188.ani.app.ui.lang.settings_media_image_captcha_auto_solve_description
import me.him188.ani.app.ui.lang.settings_media_max_wait_time
import me.him188.ani.app.ui.lang.settings_media_none
import me.him188.ani.app.ui.lang.settings_media_resolution
import me.him188.ani.app.ui.lang.settings_media_show_disabled
import me.him188.ani.app.ui.lang.settings_media_subtitle_language
import me.him188.ani.app.ui.lang.settings_media_video_link_resolve_timeout
import me.him188.ani.app.ui.lang.settings_media_video_link_resolve_timeout_description
import me.him188.ani.app.ui.lang.settings_media_wait_time_10s
import me.him188.ani.app.ui.lang.settings_media_wait_time_15s
import me.him188.ani.app.ui.lang.settings_media_wait_time_3s
import me.him188.ani.app.ui.lang.settings_media_wait_time_5s
import me.him188.ani.app.ui.lang.settings_media_wait_time_8s
import me.him188.ani.app.ui.lang.settings_media_wait_time_infinite
import me.him188.ani.app.ui.lang.settings_media_wait_time_none
import me.him188.ani.app.ui.lang.settings_media_web_search_cache_ttl
import me.him188.ani.app.ui.lang.settings_option_disabled
import me.him188.ani.app.ui.lang.settings_option_enabled
import me.him188.ani.app.ui.lang.settings_player_audio_time_stretch
import me.him188.ani.app.ui.lang.settings_player_audio_time_stretch_description
import me.him188.ani.app.ui.lang.settings_player_auto_fullscreen_on_landscape
import me.him188.ani.app.ui.lang.settings_player_auto_mark_done
import me.him188.ani.app.ui.lang.settings_player_auto_play_next
import me.him188.ani.app.ui.lang.settings_player_auto_skip_op_ed
import me.him188.ani.app.ui.lang.settings_player_auto_switch_media_on_error
import me.him188.ani.app.ui.lang.settings_player_default_playback_speed
import me.him188.ani.app.ui.lang.settings_player_enable_regex_filter
import me.him188.ani.app.ui.lang.settings_player_exoplayer_preinit_effect_graph
import me.him188.ani.app.ui.lang.settings_player_exoplayer_preinit_effect_graph_desc
import me.him188.ani.app.ui.lang.settings_player_experimental_hls_segment_filter
import me.him188.ani.app.ui.lang.settings_player_experimental_hls_segment_filter_description
import me.him188.ani.app.ui.lang.settings_player_frame_preview
import me.him188.ani.app.ui.lang.settings_player_fullscreen_always_show
import me.him188.ani.app.ui.lang.settings_player_fullscreen_auto_hide
import me.him188.ani.app.ui.lang.settings_player_fullscreen_button
import me.him188.ani.app.ui.lang.settings_player_fullscreen_only_in_controller
import me.him188.ani.app.ui.lang.settings_media_advanced_settings
import me.him188.ani.app.ui.lang.settings_media_advanced_settings_description
import me.him188.ani.app.ui.lang.settings_media_source_subscription
import me.him188.ani.app.ui.lang.settings_player_group_advanced_description
import me.him188.ani.app.ui.lang.settings_player_group_danmaku
import me.him188.ani.app.ui.lang.settings_player_group_danmaku_description
import me.him188.ani.app.ui.lang.settings_player_group_picture
import me.him188.ani.app.ui.lang.settings_player_group_picture_description
import me.him188.ani.app.ui.lang.settings_player_group_playback
import me.him188.ani.app.ui.lang.settings_player_group_playback_description
import me.him188.ani.app.ui.lang.settings_player_hide_selector_on_select
import me.him188.ani.app.ui.lang.settings_player_long_press_fast_forward_speed
import me.him188.ani.app.ui.lang.settings_player_op_ed_skip_duration
import me.him188.ani.app.ui.lang.settings_player_op_ed_skip_duration_seconds
import me.him188.ani.app.ui.lang.settings_player_pause_on_edit_danmaku
import me.him188.ani.app.ui.lang.settings_player_playback_speed_range
import me.him188.ani.app.ui.lang.settings_player_remember_playback_speed
import me.him188.ani.app.ui.lang.settings_player_remember_playback_speed_description
import me.him188.ani.app.ui.lang.settings_player_video_enhancement_default
import me.him188.ani.app.ui.lang.settings_player_video_enhancement_default_description
import me.him188.ani.app.ui.lang.settings_search_visibility_description
import me.him188.ani.app.ui.lang.settings_tab_player
import me.him188.ani.app.ui.lang.settings_theme_mode_auto
import me.him188.ani.app.ui.lang.settings_theme_palette
import me.him188.ani.app.ui.lang.tv_settings_subscription_sources_count
import me.him188.ani.app.ui.settings.tabs.media.MediaSelectorWorkflowDemoState
import me.him188.ani.app.ui.lang.video_player_enable_danmaku
import me.him188.ani.app.ui.lang.video_player_off
import me.him188.ani.app.ui.lang.video_player_performance
import me.him188.ani.app.ui.lang.video_player_quality
import me.him188.ani.app.ui.media.rememberMediaDetailsStrings
import me.him188.ani.app.ui.media.renderSubtitleLanguage
import me.him188.ani.app.ui.theme.themeColorOptions
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.utils.platform.Uuid
import org.jetbrains.compose.resources.stringResource

data class TvSettingsDisplayMode(val id: Int, val label: String)

@Composable
internal fun TvSettingsItems.appearance() {
    val appearance = state.appearance
    val languages = listOf<Locale?>(null) + SupportedLocales
    choice(
        "language", stringResource(Lang.settings_app_language), appearance.appLanguage, languages,
        languages.map { renderLocale(it) },
    ) { locale -> onIntent(TvSettingsIntent.Appearance { copy(appLanguage = locale) }) }
    choice(
        "nsfw", stringResource(Lang.settings_app_nsfw_content), appearance.searchSettings.nsfwMode, NsfwMode.entries,
        listOf(
            stringResource(Lang.settings_app_nsfw_hide), stringResource(Lang.settings_app_nsfw_blur),
            stringResource(Lang.settings_app_nsfw_display),
        ),
    ) { value -> onIntent(TvSettingsIntent.Appearance { copy(searchSettings = searchSettings.copy(nsfwMode = value)) }) }
    toggle(
        "hide-watched", stringResource(Lang.settings_app_not_show_done_and_dropped_subjects),
        appearance.searchSettings.ignoreDoneAndDroppedSubjects,
        description = stringResource(Lang.settings_search_visibility_description),
    ) { value ->
        onIntent(TvSettingsIntent.Appearance { copy(searchSettings = searchSettings.copy(ignoreDoneAndDroppedSubjects = value)) })
    }
}

@Composable
internal fun TvSettingsItems.palette() {
    val title = stringResource(Lang.settings_theme_palette)
    AniThemeDefaults.themeColorOptions.forEachIndexed { index, color ->
        items += TvSettingItem(
            "palette-$index", "$title ${index + 1}", color = color,
            selected = state.theme.seedColor == color,
            onClick = { onIntent(TvSettingsIntent.Theme { copy(seedColorValue = color.value, useDynamicTheme = false) }) },
        )
    }
}

@Composable
internal fun TvSettingsPlayerPage.title(): String = stringResource(when (this) {
    TvSettingsPlayerPage.Overview -> Lang.settings_tab_player
    TvSettingsPlayerPage.Playback -> Lang.settings_player_group_playback
    TvSettingsPlayerPage.Picture -> Lang.settings_player_group_picture
    TvSettingsPlayerPage.Danmaku -> Lang.settings_player_group_danmaku
    TvSettingsPlayerPage.Advanced -> Lang.settings_media_advanced_settings
})

@Composable
internal fun TvSettingsItems.player(
    page: TvSettingsPlayerPage,
    displayModes: List<TvSettingsDisplayMode>,
    navigate: (TvSettingsPlayerPage) -> Unit,
) {
    when (page) {
        TvSettingsPlayerPage.Overview -> {
            val descriptions = listOf(
                Lang.settings_player_group_playback_description, Lang.settings_player_group_picture_description,
                Lang.settings_player_group_danmaku_description, Lang.settings_player_group_advanced_description,
            )
            TvSettingsPlayerPage.entries.drop(1).forEachIndexed { index, group ->
                action("player-$group", group.title(), description = stringResource(descriptions[index]), opensPane = true) { navigate(group) }
            }
        }
        TvSettingsPlayerPage.Playback -> playerPlayback()
        TvSettingsPlayerPage.Picture -> playerPicture()
        TvSettingsPlayerPage.Danmaku -> playerDanmaku(displayModes)
        TvSettingsPlayerPage.Advanced -> playerAdvanced()
    }
}

@Composable
private fun TvSettingsItems.playerPlayback() {
    val video = state.video
    toggle("auto-next", stringResource(Lang.settings_player_auto_play_next), video.autoPlayNext) {
        onIntent(TvSettingsIntent.Video { copy(autoPlayNext = it) })
    }
    toggle("skip-op-ed", stringResource(Lang.settings_player_auto_skip_op_ed), video.autoSkipOpEd) {
        onIntent(TvSettingsIntent.Video { copy(autoSkipOpEd = it) })
    }
    val skipDurations = listOf(80, 85, 90)
    choice(
        "skip-duration", stringResource(Lang.settings_player_op_ed_skip_duration), video.opEdSkipDuration.inWholeSeconds.toInt(),
        skipDurations, skipDurations.map { stringResource(Lang.settings_player_op_ed_skip_duration_seconds, it) },
    ) { value -> onIntent(TvSettingsIntent.Video { copy(opEdSkipDuration = value.seconds) }) }
    toggle("auto-source", stringResource(Lang.settings_player_auto_switch_media_on_error), video.autoSwitchMediaOnPlayerError) {
        onIntent(TvSettingsIntent.Video { copy(autoSwitchMediaOnPlayerError = it) })
    }
    toggle("mark-done", stringResource(Lang.settings_player_auto_mark_done), video.autoMarkDone) {
        onIntent(TvSettingsIntent.Video { copy(autoMarkDone = it) })
    }
    toggle("close-selector", stringResource(Lang.settings_player_hide_selector_on_select), video.hideSelectorOnSelect) {
        onIntent(TvSettingsIntent.Video { copy(hideSelectorOnSelect = it) })
    }
}

@Composable
private fun TvSettingsItems.playerPicture() {
    val video = state.video
    choice(
        "enhancement", stringResource(Lang.settings_player_video_enhancement_default), video.videoEnhancementDefaultMode,
        listOf(VideoEnhancementDefaultMode.OFF, VideoEnhancementDefaultMode.PERFORMANCE, VideoEnhancementDefaultMode.QUALITY),
        listOf(stringResource(Lang.video_player_off), stringResource(Lang.video_player_performance), stringResource(Lang.video_player_quality)),
        stringResource(Lang.settings_player_video_enhancement_default_description),
    ) { value -> onIntent(TvSettingsIntent.Video { copy(videoEnhancementDefaultMode = value) }) }
    toggle("frame-preview", stringResource(Lang.settings_player_frame_preview), video.enableFramePreview) {
        onIntent(TvSettingsIntent.Video { copy(enableFramePreview = it) })
    }
    val speedRangeTitle = stringResource(Lang.settings_player_playback_speed_range)
    action("speed-range", speedRangeTitle, "${video.minPlaybackSpeed}× – ${video.maxPlaybackSpeed}×") {
        open(TvSettingsDialog.SpeedRange("speed-range", speedRangeTitle, video))
    }
    toggle(
        "remember-speed", stringResource(Lang.settings_player_remember_playback_speed), video.rememberPlaybackSpeed,
        stringResource(Lang.settings_player_remember_playback_speed_description),
    ) { onIntent(TvSettingsIntent.Video { copy(rememberPlaybackSpeed = it) }) }
    val speeds = (1..16).map { it * .25f }.filter { it in video.minPlaybackSpeed..video.maxPlaybackSpeed }
    if (!video.rememberPlaybackSpeed) {
        choice("default-speed", stringResource(Lang.settings_player_default_playback_speed), video.playbackSpeed, speeds, speeds.map { "$it×" }) {
            value -> onIntent(TvSettingsIntent.Video { copy(playbackSpeed = value.coerceIn(minPlaybackSpeed, maxPlaybackSpeed)) })
        }
    }
    choice(
        "hold-speed", stringResource(Lang.settings_player_long_press_fast_forward_speed), video.fastForwardSpeed,
        speeds, speeds.map { "$it×" },
    ) { value -> onIntent(TvSettingsIntent.Video { copy(fastForwardSpeed = value.coerceIn(minPlaybackSpeed, maxPlaybackSpeed)) }) }
    toggle(
        "audio-stretch", stringResource(Lang.settings_player_audio_time_stretch), video.enableHighQualityAudioTimeStretch,
        stringResource(Lang.settings_player_audio_time_stretch_description),
    ) { onIntent(TvSettingsIntent.Video { copy(enableHighQualityAudioTimeStretch = it) }) }
}

@Composable
private fun TvSettingsItems.playerDanmaku(displayModes: List<TvSettingsDisplayMode>) {
    val video = state.video
    toggle("danmaku", stringResource(Lang.video_player_enable_danmaku), state.danmakuEnabled) {
        onIntent(TvSettingsIntent.Danmaku(it))
    }
    toggle("pause-danmaku", stringResource(Lang.settings_player_pause_on_edit_danmaku), video.pauseVideoOnEditDanmaku) {
        onIntent(TvSettingsIntent.Video { copy(pauseVideoOnEditDanmaku = it) })
    }
    choice(
        "refresh-rate", stringResource(Lang.settings_app_danmaku_refresh_rate), video.displayModeId,
        listOf(0) + displayModes.map { it.id },
        listOf(stringResource(Lang.settings_theme_mode_auto)) + displayModes.map { it.label },
    ) { value -> onIntent(TvSettingsIntent.Video { copy(displayModeId = value) }) }
    toggle("regex-enabled", stringResource(Lang.settings_player_enable_regex_filter), state.filter.enableRegexFilter) {
        onIntent(TvSettingsIntent.Filter(it))
    }
    val addTitle = stringResource(Lang.settings_danmaku_add_regex_filter)
    action("regex-add", addTitle) {
        open(TvSettingsDialog.Regex("regex-add", addTitle, DanmakuRegexFilter(Uuid.randomString(), "", ""), true))
    }
    state.regexFilters.forEach { filter ->
        val editTitle = stringResource(Lang.settings_danmaku_regex_filter_group)
        items += TvSettingItem(
            "regex-${filter.id}", filter.regex,
            value = stringResource(if (filter.enabled) Lang.settings_option_enabled else Lang.settings_option_disabled),
            description = filter.name.takeIf { it.isNotBlank() },
        ) { open(TvSettingsDialog.Regex("regex-${filter.id}", editTitle, filter, false)) }
    }
    action("regex-export", stringResource(Lang.settings_danmaku_export_to_clipboard), opensDetail = false) {
        onIntent(TvSettingsIntent.ExportRegex)
    }
    val importTitle = stringResource(Lang.settings_danmaku_import_title)
    action("regex-import", stringResource(Lang.settings_danmaku_import_from_clipboard)) {
        open(TvSettingsDialog.Import("regex-import", importTitle))
    }
}

@Composable
private fun TvSettingsItems.playerAdvanced() {
    val video = state.video
    toggle(
        "hls-filter", stringResource(Lang.settings_player_experimental_hls_segment_filter), video.enableExperimentalHlsSegmentFiltering,
        stringResource(Lang.settings_player_experimental_hls_segment_filter_description),
    ) { onIntent(TvSettingsIntent.Video { copy(enableExperimentalHlsSegmentFiltering = it) }) }
    toggle(
        "preinit-effects", stringResource(Lang.settings_player_exoplayer_preinit_effect_graph), state.kernel.exoPlayerInitEffectGraphInAdvance,
        stringResource(Lang.settings_player_exoplayer_preinit_effect_graph_desc),
    ) { onIntent(TvSettingsIntent.Kernel { copy(exoPlayerInitEffectGraphInAdvance = it) }) }

    choice(
        "fullscreen", stringResource(Lang.settings_player_fullscreen_button), video.fullscreenSwitchMode,
        FullscreenSwitchMode.entries,
        listOf(
            stringResource(Lang.settings_player_fullscreen_always_show),
            stringResource(Lang.settings_player_fullscreen_auto_hide),
            stringResource(Lang.settings_player_fullscreen_only_in_controller),
        ),
    ) { value -> onIntent(TvSettingsIntent.Video { copy(fullscreenSwitchMode = value) }) }
    toggle("landscape-fullscreen", stringResource(Lang.settings_player_auto_fullscreen_on_landscape), video.autoFullscreenOnLandscapeMode) {
        onIntent(TvSettingsIntent.Video { copy(autoFullscreenOnLandscapeMode = it) })
    }
}

@Composable
internal fun TvSettingsItems.watching(openAdvanced: () -> Unit) {
    val preference = state.preference
    val mediaStrings = rememberMediaDetailsStrings()
    val any = stringResource(Lang.settings_media_any)
    val none = stringResource(Lang.settings_media_none)
    val languages = SubtitleLanguage.matchableEntries.map { TvSettingsChoice(it.id, renderSubtitleLanguage(it.id, mediaStrings)) }
    val resolutions = Resolution.entries.map { TvSettingsChoice(it.id, it.id) }
    fun order(id: String, title: String, selected: List<String>?, choices: List<TvSettingsChoice>, save: (List<String>) -> Unit) {
        val value = when {
            selected == null -> any
            selected.isEmpty() -> none
            else -> selected.joinToString { id -> choices.find { it.id == id }?.title ?: id }
        }
        action(id, title, value) { open(TvSettingsDialog.Order(id, title, choices, selected, save)) }
    }
    order("subtitles", stringResource(Lang.settings_media_subtitle_language), preference.fallbackSubtitleLanguageIds, languages) {
        value -> onIntent(TvSettingsIntent.Preference { copy(fallbackSubtitleLanguageIds = value) })
    }
    order("resolution", stringResource(Lang.settings_media_resolution), preference.fallbackResolutions, resolutions) {
        value -> onIntent(TvSettingsIntent.Preference { copy(fallbackResolutions = value) })
    }
    val allianceTitle = stringResource(Lang.settings_media_alliance)
    val allianceDescription = stringResource(Lang.settings_media_alliance_description)
    action("alliance", allianceTitle, preference.alliancePatterns?.joinToString().orEmpty().ifEmpty { any }) {
        open(TvSettingsDialog.Input(
            "alliance", allianceTitle, preference.alliancePatterns?.joinToString().orEmpty(), allianceDescription,
            validate = { text -> text.split(',', '，').all { it.isBlank() || isTvSettingsRegexValid(it.trim()) } },
        ) { text ->
            val patterns = text.split(',', '，').map(String::trim).filter(String::isNotEmpty).takeIf { it.isNotEmpty() }
            onIntent(TvSettingsIntent.Preference { copy(alliancePatterns = patterns) })
        })
    }
    action(
        "watching-advanced", stringResource(Lang.settings_media_advanced_settings),
        description = stringResource(Lang.settings_media_advanced_settings_description), opensPane = true,
        onClick = openAdvanced,
    )
}

@Composable
internal fun TvSettingsItems.watchingAdvanced(workflow: MediaSelectorWorkflowDemoState) {
    val selector = state.selector
    val preference = state.preference
    toggle(
        "fast-select", stringResource(Lang.settings_media_fast_select_web), selector.fastSelectWebKind,
        stringResource(Lang.settings_media_fast_select_web_description),
    ) {
        onIntent(TvSettingsIntent.Selector { copy(fastSelectWebKind = it) })
        workflow.onFastSelectWebKindChanged(it)
    }
    val waits = listOf(Duration.ZERO, 3.seconds, 5.seconds, 8.seconds, 10.seconds, 15.seconds, Duration.INFINITE)
    choice(
        "source-wait", stringResource(Lang.settings_media_max_wait_time), selector.fastSelectWebLowTierToleranceDuration,
        waits, listOf(
            stringResource(Lang.settings_media_wait_time_none), stringResource(Lang.settings_media_wait_time_3s),
            stringResource(Lang.settings_media_wait_time_5s), stringResource(Lang.settings_media_wait_time_8s),
            stringResource(Lang.settings_media_wait_time_10s), stringResource(Lang.settings_media_wait_time_15s),
            stringResource(Lang.settings_media_wait_time_infinite),
        ), enabled = selector.fastSelectWebKind,
    ) { value ->
        onIntent(TvSettingsIntent.Selector { copy(fastSelectWebLowTierToleranceDuration = value) })
        workflow.onLowTierToleranceChanged(value)
    }
    val timeouts = VideoResolverSettings.ResourceExtractionTimeoutSecondsOptions
    choice(
        "resolve-timeout", stringResource(Lang.settings_media_video_link_resolve_timeout),
        state.resolver.effectiveResourceExtractionTimeoutSeconds, timeouts, timeouts.map {
            stringResource(Lang.settings_player_op_ed_skip_duration_seconds, it)
        },
        stringResource(Lang.settings_media_video_link_resolve_timeout_description),
    ) { value ->
        onIntent(TvSettingsIntent.Resolver { copy(resourceExtractionTimeoutSeconds = value) })
        workflow.onResolveTimeoutChanged(value)
    }
    val ttls = listOf(Duration.ZERO, 5.minutes, 15.minutes, 30.minutes, 1.hours, 6.hours, 1.days)
    choice(
        "search-cache", stringResource(Lang.settings_media_web_search_cache_ttl), selector.webSearchCacheTtl, ttls,
        listOf(
            stringResource(Lang.settings_media_cache_ttl_none), stringResource(Lang.settings_media_cache_ttl_5min),
            stringResource(Lang.settings_media_cache_ttl_15min), stringResource(Lang.settings_media_cache_ttl_30min),
            stringResource(Lang.settings_media_cache_ttl_1h), stringResource(Lang.settings_media_cache_ttl_6h),
            stringResource(Lang.settings_media_cache_ttl_1d),
        ),
    ) { value ->
        onIntent(TvSettingsIntent.Selector { copy(webSearchCacheTtl = value) })
        workflow.onWebSearchCacheTtlChanged(value)
    }
    toggle(
        "captcha", stringResource(Lang.settings_media_image_captcha_auto_solve), selector.enableImageCaptchaAutoSolve,
        stringResource(Lang.settings_media_image_captcha_auto_solve_description),
    ) { onIntent(TvSettingsIntent.Selector { copy(enableImageCaptchaAutoSolve = it) }) }
    toggle("show-disabled", stringResource(Lang.settings_media_show_disabled), selector.showDisabled) {
        onIntent(TvSettingsIntent.Selector { copy(showDisabled = it) })
    }
    toggle("hide-no-subtitle", stringResource(Lang.settings_media_hide_no_subtitle), !preference.showWithoutSubtitle) {
        onIntent(TvSettingsIntent.Preference { copy(showWithoutSubtitle = !it) })
    }
    toggle("enable-last-source", stringResource(Lang.settings_media_auto_enable_last), selector.autoEnableLastSelected) {
        onIntent(TvSettingsIntent.Selector { copy(autoEnableLastSelected = it) })
    }
}

@Composable
internal fun TvSettingsItems.sources(openSubscriptions: () -> Unit) {
    action("subscriptions", stringResource(Lang.settings_media_source_subscription), opensPane = true, onClick = openSubscriptions)
    state.sources.forEach { source ->
        toggle("source-${source.id}", source.name, source.enabled, source.description.takeIf(String::isNotBlank)) {
            onIntent(TvSettingsIntent.SourceEnabled(source.id, it))
        }
    }
}

@Composable
internal fun TvSettingsItems.subscriptions() {
    state.subscriptions.forEach { subscription ->
        toggle(
            "subscription-${subscription.id}", subscription.url.removePrefix("https://").removePrefix("http://"),
            subscription.enabled,
            stringResource(Lang.tv_settings_subscription_sources_count, state.sources.count { it.subscription == subscription.id }),
        ) { onIntent(TvSettingsIntent.SubscriptionEnabled(subscription.id, it)) }
    }
}
