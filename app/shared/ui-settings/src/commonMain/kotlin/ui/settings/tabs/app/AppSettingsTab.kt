/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.preference.DesktopCloseBehavior
import me.him188.ani.app.data.models.preference.EpisodeListProgressTheme
import me.him188.ani.app.data.models.preference.FullscreenSwitchMode
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.preference.SkipOpEdMode
import me.him188.ani.app.data.models.preference.NoticeSoundKind
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.UpdateSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.preference.WatchTogetherSettings
import me.him188.ani.app.data.network.protocol.ReleaseClass
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.getIcon
import me.him188.ani.app.navigation.getText
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.effects.rememberNoticeSoundPlayer
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.SteppedSlider
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.quantizeSliderValue
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_app_close_behavior
import me.him188.ani.app.ui.lang.settings_app_close_behavior_exit
import me.him188.ani.app.ui.lang.settings_app_close_behavior_minimize_to_tray
import me.him188.ani.app.ui.lang.settings_app_episode_playback
import me.him188.ani.app.ui.lang.settings_app_initial_page
import me.him188.ani.app.ui.lang.settings_app_initial_page_description
import me.him188.ani.app.ui.lang.settings_app_light_up_mode
import me.him188.ani.app.ui.lang.settings_app_light_up_mode_description
import me.him188.ani.app.ui.lang.settings_app_list_animation
import me.him188.ani.app.ui.lang.settings_app_list_animation_description
import me.him188.ani.app.ui.lang.settings_app_my_collections
import me.him188.ani.app.ui.lang.settings_app_not_show_done_and_dropped_subjects
import me.him188.ani.app.ui.lang.settings_app_nsfw_blur
import me.him188.ani.app.ui.lang.settings_app_nsfw_content
import me.him188.ani.app.ui.lang.settings_app_nsfw_display
import me.him188.ani.app.ui.lang.settings_app_nsfw_hide
import me.him188.ani.app.ui.lang.settings_app_search
import me.him188.ani.app.ui.lang.settings_app_language_system
import me.him188.ani.app.ui.lang.settings_player
import me.him188.ani.app.ui.lang.settings_player_audio_time_stretch
import me.him188.ani.app.ui.lang.settings_player_audio_time_stretch_description
import me.him188.ani.app.ui.lang.settings_player_auto_fullscreen_on_landscape
import me.him188.ani.app.ui.lang.settings_player_auto_mark_done
import me.him188.ani.app.ui.lang.settings_player_auto_play_next
import me.him188.ani.app.ui.lang.settings_player_auto_skip_op_ed
import me.him188.ani.app.ui.lang.settings_player_auto_skip_op_ed_description
import me.him188.ani.app.ui.lang.settings_player_auto_switch_media_on_error
import me.him188.ani.app.utils.formatSpeedValue
import me.him188.ani.app.ui.lang.settings_player_default_playback_speed
import me.him188.ani.app.ui.lang.settings_player_default_playback_speed_description
import me.him188.ani.app.ui.lang.settings_player_experimental_hls_segment_filter
import me.him188.ani.app.ui.lang.settings_player_experimental_hls_segment_filter_description
import me.him188.ani.app.ui.lang.settings_player_enable_regex_filter
import me.him188.ani.app.ui.lang.settings_player_frame_preview
import me.him188.ani.app.ui.lang.settings_player_frame_preview_description
import me.him188.ani.app.ui.lang.settings_player_fullscreen_always_show
import me.him188.ani.app.ui.lang.settings_player_fullscreen_auto_hide
import me.him188.ani.app.ui.lang.settings_player_fullscreen_button
import me.him188.ani.app.ui.lang.settings_player_fullscreen_button_description
import me.him188.ani.app.ui.lang.settings_player_fullscreen_only_in_controller
import me.him188.ani.app.ui.lang.settings_player_hide_selector_on_select
import me.him188.ani.app.ui.lang.settings_player_long_press_fast_forward_speed
import me.him188.ani.app.ui.lang.settings_player_long_press_fast_forward_speed_description
import me.him188.ani.app.ui.lang.settings_player_notice_sound
import me.him188.ani.app.ui.lang.settings_player_notice_sound_alert
import me.him188.ani.app.ui.lang.settings_player_notice_sound_confirm
import me.him188.ani.app.ui.lang.settings_player_notice_sound_delete
import me.him188.ani.app.ui.lang.settings_player_notice_sound_description
import me.him188.ani.app.ui.lang.settings_player_notice_sound_none
import me.him188.ani.app.ui.lang.settings_player_notice_sound_space
import me.him188.ani.app.ui.lang.settings_player_notice_sound_standard
import me.him188.ani.app.ui.lang.settings_player_notice_sound_tick
import me.him188.ani.app.ui.lang.settings_player_op_ed_skip_duration
import me.him188.ani.app.ui.lang.settings_player_skip_op_ed_auto
import me.him188.ani.app.ui.lang.settings_player_skip_op_ed_auto_then_manual
import me.him188.ani.app.ui.lang.settings_player_skip_op_ed_manual
import me.him188.ani.app.ui.lang.settings_player_skip_op_ed_off
import me.him188.ani.app.ui.lang.settings_player_op_ed_skip_duration_seconds
import me.him188.ani.app.ui.lang.settings_player_pause_on_edit_danmaku
import me.him188.ani.app.ui.lang.settings_player_playback_speed_range
import me.him188.ani.app.ui.lang.settings_player_playback_speed_range_description
import me.him188.ani.app.ui.lang.settings_player_remember_playback_speed
import me.him188.ani.app.ui.lang.settings_player_remember_playback_speed_description
import me.him188.ani.app.ui.lang.settings_theme_tv_retain_playback_session
import me.him188.ani.app.ui.lang.settings_theme_tv_retain_playback_session_description
import me.him188.ani.app.ui.lang.settings_theme_tv_ui_scale
import me.him188.ani.app.ui.lang.settings_theme_tv_ui_scale_description
import me.him188.ani.app.ui.lang.settings_update_auto_check
import me.him188.ani.app.ui.lang.settings_update_auto_check_description
import me.him188.ani.app.ui.lang.settings_update_auto_download
import me.him188.ani.app.ui.lang.settings_update_auto_download_description
import me.him188.ani.app.ui.lang.settings_update_check
import me.him188.ani.app.ui.lang.settings_update_check_failed
import me.him188.ani.app.ui.lang.settings_update_checking
import me.him188.ani.app.ui.lang.settings_update_current_version
import me.him188.ani.app.ui.lang.settings_update_in_app_download
import me.him188.ani.app.ui.lang.settings_update_in_app_download_disabled
import me.him188.ani.app.ui.lang.settings_update_in_app_download_enabled
import me.him188.ani.app.ui.lang.settings_update_new_version
import me.him188.ani.app.ui.lang.settings_update_software
import me.him188.ani.app.ui.lang.settings_update_type
import me.him188.ani.app.ui.lang.settings_update_type_alpha
import me.him188.ani.app.ui.lang.settings_update_type_alpha_short
import me.him188.ani.app.ui.lang.settings_update_type_beta
import me.him188.ani.app.ui.lang.settings_update_type_beta_short
import me.him188.ani.app.ui.lang.settings_update_type_stable
import me.him188.ani.app.ui.lang.settings_update_type_stable_short
import me.him188.ani.app.ui.lang.settings_update_up_to_date
import me.him188.ani.app.ui.lang.settings_update_view_changelog
import me.him188.ani.app.ui.lang.settings_watch_together_description
import me.him188.ani.app.ui.lang.settings_watch_together_social
import me.him188.ani.app.ui.lang.watch_together_title
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterGroup
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.settings.danmaku.createTestDanmakuRegexFilterState
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.DropdownItem
import me.him188.ani.app.ui.settings.framework.components.RangeSliderItem
import me.him188.ani.app.ui.settings.framework.components.RowButtonItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SliderItem
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextButtonItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.framework.createTestSettingsState
import me.him188.ani.app.ui.settings.framework.rememberTestSettingsState
import me.him188.ani.app.ui.settings.rendering.ReleaseClassIcon
import me.him188.ani.app.ui.settings.rendering.guessReleaseClass
import me.him188.ani.app.ui.settings.tabs.theme.ThemeGroup
import me.him188.ani.app.ui.update.AppUpdateState
import me.him188.ani.app.ui.update.AppUpdateViewModel
import me.him188.ani.app.ui.update.NewVersion
import me.him188.ani.app.ui.update.UpdateSettingsNotifier
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isAndroid
import me.him188.ani.utils.platform.isDesktop
import me.him188.ani.utils.platform.isIos
import me.him188.ani.utils.platform.isMobile
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

sealed class CheckVersionResult {
    data class HasNewVersion(
        val newVersion: NewVersion,
    ) : CheckVersionResult()

    data object UpToDate : CheckVersionResult()
    data class Failed(
        val throwable: Throwable,
    ) : CheckVersionResult()
}

@Composable
fun AppSettingsTab(
    softwareUpdateGroupState: SoftwareUpdateGroupState,
    uiSettings: SettingsState<UISettings>,
    themeSettings: SettingsState<ThemeSettings>,
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
    watchTogetherSettings: SettingsState<WatchTogetherSettings>,
    danmakuFilterConfig: SettingsState<DanmakuFilterConfig>,
    danmakuRegexFilterState: DanmakuRegexFilterState,
    showDebug: Boolean,
    modifier: Modifier = Modifier
) {
    SettingsTab(modifier) {
        SoftwareUpdateGroup(softwareUpdateGroupState)
        AppearanceGroup(uiSettings, themeSettings)
        ThemeGroup(themeSettings)
        PlayerGroup(
            videoScaffoldConfig,
            danmakuFilterConfig,
            danmakuRegexFilterState,
            showDebug,
            themeSettings,
        )
        WatchTogetherGroup(watchTogetherSettings)
        AppSettingsTabPlatform()
    }
}

@Composable
fun SettingsScope.WatchTogetherGroup(state: SettingsState<WatchTogetherSettings>) {
    val config by state
    Group(title = { Text(stringResource(Lang.settings_watch_together_social)) }, useThinHeader = true) {
        SwitchItem(
            checked = config.enabled,
            onCheckedChange = { state.update(config.copy(enabled = it)) },
            title = { Text(stringResource(Lang.watch_together_title)) },
            description = { Text(stringResource(Lang.settings_watch_together_description)) },
        )
    }
}

@Composable
fun SettingsScope.AppearanceGroup(
    state: SettingsState<UISettings>,
    themeSettings: SettingsState<ThemeSettings>,
) {
    val uiSettings by state

    // 放在整页最前: 调整缩放会让整页按新 density 重新布局, 条目越靠上位移越小 ——
    // 排在后面的话, 上方那些条目的高度变化会叠加起来把它推出屏幕
    if (LocalAniUiBehavior.current.immersiveShell) {
        UiScaleSliderItem(themeSettings)
    }

    LanguageSettingsPlatform(state)

    DropdownItem(
        selected = { uiSettings.mainSceneInitialPage },
        values = { MainScreenPage.visibleEntries },
        itemText = { Text(it.getText()) },
        onSelect = {
            state.update(uiSettings.copy(mainSceneInitialPage = it))
        },
        itemIcon = { Icon(it.getIcon(), null) },
        title = { Text(stringResource(Lang.settings_app_initial_page)) },
        description = { Text(stringResource(Lang.settings_app_initial_page_description)) },
    )
    if (LocalPlatform.current.isDesktop()) {
        DropdownItem(
            selected = { uiSettings.desktopCloseBehavior },
            values = { listOf(DesktopCloseBehavior.EXIT, DesktopCloseBehavior.MINIMIZE) },
            itemText = {
                Text(it.renderText())
            },
            exposedItemText = {
                Text(it.renderText())
            },
            onSelect = {
                state.update(uiSettings.copy(desktopCloseBehavior = it))
            },
            title = { Text(stringResource(Lang.settings_app_close_behavior)) },
        )
    }

    Group(title = { Text(stringResource(Lang.settings_app_search)) }, useThinHeader = true) {
        SwitchItem(
            checked = uiSettings.searchSettings.ignoreDoneAndDroppedSubjects,
            onCheckedChange = {
                state.update(
                    uiSettings.copy(
                        searchSettings = uiSettings.searchSettings.copy(
                            ignoreDoneAndDroppedSubjects = !uiSettings.searchSettings.ignoreDoneAndDroppedSubjects,
                        ),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_not_show_done_and_dropped_subjects)) },
        )
        DropdownItem(
            selected = { uiSettings.searchSettings.nsfwMode },
            values = { NsfwMode.entries },
            itemText = {
                when (it) {
                    NsfwMode.HIDE -> Text(stringResource(Lang.settings_app_nsfw_hide))
                    NsfwMode.BLUR -> Text(stringResource(Lang.settings_app_nsfw_blur))
                    NsfwMode.DISPLAY -> Text(stringResource(Lang.settings_app_nsfw_display))
                }
            },
            onSelect = {
                state.update(
                    uiSettings.copy(
                        searchSettings = uiSettings.searchSettings.copy(nsfwMode = it),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_nsfw_content)) },
        )
    }

    Group(title = { Text(stringResource(Lang.settings_app_my_collections)) }, useThinHeader = true) {
        SwitchItem(
            checked = uiSettings.myCollections.enableListAnimation1,
            onCheckedChange = {
                state.update(
                    uiSettings.copy(
                        myCollections = uiSettings.myCollections.copy(
                            enableListAnimation1 = !uiSettings.myCollections.enableListAnimation1,
                        ),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_list_animation)) },
            description = { Text(stringResource(Lang.settings_app_list_animation_description)) },
        )
    }

    Group(title = { Text(stringResource(Lang.settings_app_episode_playback)) }, useThinHeader = true) {
        val episode by remember { derivedStateOf { uiSettings.episodeProgress } }
        SwitchItem(
            checked = episode.theme == EpisodeListProgressTheme.LIGHT_UP,
            onCheckedChange = {
                state.update(
                    uiSettings.copy(
                        episodeProgress = episode.copy(
                            theme = if (it) EpisodeListProgressTheme.LIGHT_UP else EpisodeListProgressTheme.ACTION,
                        ),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_light_up_mode)) },
            description = { Text(stringResource(Lang.settings_app_light_up_mode_description)) },
        )
    }
}

/**
 * 界面缩放滑块. 每格 [ThemeSettings.UI_SCALE_STEP], 实时生效 (整个应用的 `LocalDensity` 立刻跟随),
 * 用户可以边调边看效果 —— 这也是它必须实时提交、而不是松手才写的原因.
 *
 * 值存在 [ThemeSettings] 里 (与深色模式同一份配置), 但它是界面尺寸而不是配色, 所以摆在"界面"这一页.
 */
@Composable
private fun SettingsScope.UiScaleSliderItem(
    state: SettingsState<ThemeSettings>,
) {
    val themeSettings by state
    val range = ThemeSettings.UI_SCALE_RANGE
    val step = ThemeSettings.UI_SCALE_STEP
    val current = themeSettings.effectiveUiScale

    // 改一格缩放, 整页就按新 density 重排, 滑块自己也跟着上下挪 —— 很容易挪出可视区.
    // 焦点其实还在滑块上 (按左右仍在调值), 但人看不见它, 只能靠上下键把它导航回来.
    // 所以每次值变化后主动把它拉回视野.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(current) {
        // 必须等一帧: LaunchedEffect 在重组提交后就跑, 而此时新 density 下的布局还没落定,
        // 立刻请求会按旧坐标滚动. withFrameNanos 挂起到下一帧开始, 那时上一帧的布局已完成.
        withFrameNanos { }
        bringIntoViewRequester.bringIntoView()
    }

    SliderItem(
        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
        value = current,
        onValueChange = { raw ->
            // Slider 的 steps 吸附后仍带浮点误差, 量化到步进网格再写, 否则会存进 0.7000001 这种值
            val quantized = ((raw / step).roundToInt() * step).coerceIn(range)
            if (quantized != current) {
                state.update(themeSettings.copy(uiScale = quantized))
            }
        },
        valueRange = range,
        steps = ((range.endInclusive - range.start) / step).roundToInt() - 1,
        title = { Text(stringResource(Lang.settings_theme_tv_ui_scale)) },
        description = { Text(stringResource(Lang.settings_theme_tv_ui_scale_description)) },
        valueLabel = { Text("${(current * 100).roundToInt()}%") },
    )
}

@Stable
class SoftwareUpdateGroupState(
    val updateSettings: SettingsState<UpdateSettings>,
    val currentVersion: String = currentAniBuildConfig.versionName,
    val releaseClass: ReleaseClass = guessReleaseClass(currentVersion),
)

@Composable
fun SettingsScope.SoftwareUpdateGroup(
    state: SoftwareUpdateGroupState,
    modifier: Modifier = Modifier,
) {
    val autoUpdate: AppUpdateViewModel = viewModel { AppUpdateViewModel() }
    Group(title = { Text(stringResource(Lang.settings_update_software)) }, modifier = modifier) {
        TextItem(
            description = { Text(stringResource(Lang.settings_update_current_version)) },
            icon = { ReleaseClassIcon(state.releaseClass) },
            title = { Text(state.currentVersion) },
        )
        HorizontalDividerItem()
        val uriHandler = LocalUriHandler.current
        RowButtonItem(
            onClick = {
                uriHandler.openUri(
                    "https://github.com/open-ani/ani/releases/tag/v${currentAniBuildConfig.versionName}",
                )
            },
            icon = { Icon(Icons.Rounded.ArrowOutward, null) },
        ) { Text(stringResource(Lang.settings_update_view_changelog)) }
        HorizontalDividerItem()
        val updateSettings by state.updateSettings
        SwitchItem(
            updateSettings.autoCheckUpdate,
            onCheckedChange = {
                state.updateSettings.update(updateSettings.copy(autoCheckUpdate = !updateSettings.autoCheckUpdate))
            },
            title = { Text(stringResource(Lang.settings_update_auto_check)) },
            description = { Text(stringResource(Lang.settings_update_auto_check_description)) },
        )
        HorizontalDividerItem()
        DropdownItem(
            selected = { updateSettings.releaseClass },
            values = { ReleaseClass.enabledEntries },
            itemText = {
                when (it) {
                    ReleaseClass.ALPHA -> Text(stringResource(Lang.settings_update_type_alpha))
                    ReleaseClass.BETA -> Text(stringResource(Lang.settings_update_type_beta))
                    ReleaseClass.RC, // RC 实际上不会有
                    ReleaseClass.STABLE -> Text(stringResource(Lang.settings_update_type_stable))
                }
            },
            exposedItemText = {
                when (it) {
                    ReleaseClass.ALPHA -> Text(stringResource(Lang.settings_update_type_alpha_short))
                    ReleaseClass.BETA -> Text(stringResource(Lang.settings_update_type_beta_short))
                    ReleaseClass.RC, // RC 实际上不会有
                    ReleaseClass.STABLE -> Text(stringResource(Lang.settings_update_type_stable_short))
                }
            },
            onSelect = {
                state.updateSettings.update(updateSettings.copy(releaseClass = it))
            },
            itemIcon = {
                ReleaseClassIcon(it)
            },
            title = { Text(stringResource(Lang.settings_update_type)) },
        )
        if (!LocalPlatform.current.isIos()) {
            HorizontalDividerItem()
            SwitchItem(
                updateSettings.inAppDownload,
                { state.updateSettings.update(updateSettings.copy(inAppDownload = it)) },
                title = { Text(stringResource(Lang.settings_update_in_app_download)) },
                description = {
                    if (updateSettings.inAppDownload) {
                        Text(stringResource(Lang.settings_update_in_app_download_enabled))
                    } else {
                        Text(stringResource(Lang.settings_update_in_app_download_disabled))
                    }
                },
                enabled = updateSettings.autoCheckUpdate,
            )
            AniAnimatedVisibility(updateSettings.inAppDownload) {
                Column {
                    HorizontalDividerItem()
                    SwitchItem(
                        updateSettings.autoDownloadUpdate,
                        { state.updateSettings.update(updateSettings.copy(autoDownloadUpdate = it)) },
                        title = { Text(stringResource(Lang.settings_update_auto_download)) },
                        description = { Text(stringResource(Lang.settings_update_auto_download_description)) },
                        enabled = updateSettings.autoCheckUpdate,
                    )
                }
            }
        }
        HorizontalDividerItem()

        val updatePresentation by autoUpdate.presentationFlow.collectAsStateWithLifecycle()
        TextButtonItem(
            onClick = {
                if (updatePresentation.isCheckingUpdate) {
                    return@TextButtonItem
                }
                autoUpdate.startCheckLatestVersion(uriHandler)
            },
            title = {
                when {
                    updatePresentation.isCheckingUpdate -> {
                        Text(stringResource(Lang.settings_update_checking))
                    }

                    updatePresentation.checkUpdateError != null -> {
                        Text(stringResource(Lang.settings_update_check_failed))
                    }

                    updatePresentation.state is AppUpdateState.HasNewVersion -> {
                        val newVersion = updatePresentation.newVersion
                        if (newVersion != null) {
                            Text(stringResource(Lang.settings_update_new_version, newVersion.name))
                        } else {
                            Text(stringResource(Lang.settings_update_up_to_date))
                        }
                    }

                    else -> {
                        Text(stringResource(Lang.settings_update_check))
                    }
                }
            },
        )
        Box(Modifier.fillMaxWidth()) {
            UpdateSettingsNotifier(autoUpdate)
        }
    }
}

@Composable
fun SettingsScope.PlayerGroup(
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
    danmakuFilterConfig: SettingsState<DanmakuFilterConfig>,
    danmakuRegexFilterState: DanmakuRegexFilterState,
    showDebug: Boolean,
    themeSettings: SettingsState<ThemeSettings>,
) {
    Group(title = { Text(stringResource(Lang.settings_player)) }) {
        val config by videoScaffoldConfig

        // 「退出播放页后保留播放状态」: 只有自带"回到会话"入口的形态才给这条 (见 AniUiBehavior
        // .retainPlaybackSession), 否则关不掉也回不去. 存在 ThemeSettings 里只是存储位置.
        if (LocalAniUiBehavior.current.retainPlaybackSession) {
            val themeConfig by themeSettings
            SwitchItem(
                checked = themeConfig.tvRetainPlaybackSession,
                onCheckedChange = { checked ->
                    themeSettings.update(themeConfig.copy(tvRetainPlaybackSession = checked))
                },
                title = { Text(stringResource(Lang.settings_theme_tv_retain_playback_session)) },
                description = {
                    Text(stringResource(Lang.settings_theme_tv_retain_playback_session_description))
                },
            )
            HorizontalDividerItem()
        }
        // 恒为全屏的设备没有窗口/全屏切换, 该"全屏按钮"设置无意义, 隐藏
        if (LocalAniUiBehavior.current.supportsWindowedPlayback) {
            DropdownItem(
                selected = { config.fullscreenSwitchMode },
                values = { FullscreenSwitchMode.entries },
                itemText = {
                    Text(
                        when (it) {
                            FullscreenSwitchMode.ALWAYS_SHOW_FLOATING -> stringResource(Lang.settings_player_fullscreen_always_show)
                            FullscreenSwitchMode.AUTO_HIDE_FLOATING -> stringResource(Lang.settings_player_fullscreen_auto_hide)
                            FullscreenSwitchMode.ONLY_IN_CONTROLLER -> stringResource(Lang.settings_player_fullscreen_only_in_controller)
                        },
                    )
                },
                onSelect = {
                    videoScaffoldConfig.update(config.copy(fullscreenSwitchMode = it))
                },
                title = { Text(stringResource(Lang.settings_player_fullscreen_button)) },
                description = { Text(stringResource(Lang.settings_player_fullscreen_button_description)) },
            )
            HorizontalDividerItem()
        }
        SwitchItem(
            danmakuFilterConfig.value.enableRegexFilter,
            onCheckedChange = {
                danmakuFilterConfig.update(danmakuFilterConfig.value.copy(enableRegexFilter = it))
            },
            title = { Text(stringResource(Lang.settings_player_enable_regex_filter)) },
        )
        HorizontalDividerItem()
        DanmakuRegexFilterGroup(
            state = danmakuRegexFilterState,
        )
        HorizontalDividerItem()
        SwitchItem(
            checked = config.pauseVideoOnEditDanmaku,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(pauseVideoOnEditDanmaku = it))
            },
            title = { Text(stringResource(Lang.settings_player_pause_on_edit_danmaku)) },
        )
        HorizontalDividerItem()
        SwitchItem(
            checked = config.autoMarkDone,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(autoMarkDone = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_mark_done)) },
        )
        HorizontalDividerItem()
        SwitchItem(
            checked = config.hideSelectorOnSelect,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(hideSelectorOnSelect = it))
            },
            title = { Text(stringResource(Lang.settings_player_hide_selector_on_select)) },
        )
        // isMobile() 在 Android TV 上也是真, 但恒为全屏的设备既不会旋转也没有"非全屏"可回,
        // 这条开关在那儿没有任何效果
        if (LocalPlatform.current.isMobile() && LocalAniUiBehavior.current.supportsWindowedPlayback) {
            HorizontalDividerItem()
            SwitchItem(
                checked = config.autoFullscreenOnLandscapeMode,
                onCheckedChange = {
                    videoScaffoldConfig.update(config.copy(autoFullscreenOnLandscapeMode = it))
                },
                title = { Text(stringResource(Lang.settings_player_auto_fullscreen_on_landscape)) },
            )
        }
        HorizontalDividerItem()
        SwitchItem(
            checked = config.autoPlayNext,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(autoPlayNext = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_play_next)) },
        )
        HorizontalDividerItem()
        DropdownItem(
            selected = { config.effectiveSkipOpEdMode },
            values = { SkipOpEdMode.entries },
            itemText = {
                Text(
                    stringResource(
                        when (it) {
                            SkipOpEdMode.AUTO -> Lang.settings_player_skip_op_ed_auto
                            SkipOpEdMode.AUTO_THEN_MANUAL -> Lang.settings_player_skip_op_ed_auto_then_manual
                            SkipOpEdMode.MANUAL -> Lang.settings_player_skip_op_ed_manual
                            SkipOpEdMode.OFF -> Lang.settings_player_skip_op_ed_off
                        },
                    ),
                )
            },
            onSelect = {
                videoScaffoldConfig.update(config.copy(skipOpEdMode = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_skip_op_ed)) },
            description = { Text(stringResource(Lang.settings_player_auto_skip_op_ed_description)) },
        )
        HorizontalDividerItem()
        DropdownItem(
            selected = { config.opEdSkipDuration },
            values = { listOf(80.seconds, 85.seconds, 90.seconds) },
            itemText = {
                Text(stringResource(Lang.settings_player_op_ed_skip_duration_seconds, it.inWholeSeconds))
            },
            onSelect = {
                videoScaffoldConfig.update(config.copy(opEdSkipDuration = it))
            },
            title = { Text(stringResource(Lang.settings_player_op_ed_skip_duration)) },
        )
        HorizontalDividerItem()
        SwitchItem(
            checked = config.autoSwitchMediaOnPlayerError,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(autoSwitchMediaOnPlayerError = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_switch_media_on_error)) },
        )
        if (LocalPlatform.current.isAndroid()) {
            HorizontalDividerItem()
            SwitchItem(
                checked = config.enableHighQualityAudioTimeStretch,
                onCheckedChange = {
                    videoScaffoldConfig.update(config.copy(enableHighQualityAudioTimeStretch = it))
                },
                title = { Text(stringResource(Lang.settings_player_audio_time_stretch)) },
                description = { Text(stringResource(Lang.settings_player_audio_time_stretch_description)) },
            )
        }
        HorizontalDividerItem()
        if (!LocalPlatform.current.isIos()) {
            SwitchItem(
                checked = config.enableExperimentalHlsSegmentFiltering,
                onCheckedChange = {
                    videoScaffoldConfig.update(config.copy(enableExperimentalHlsSegmentFiltering = it))
                },
                title = { Text(stringResource(Lang.settings_player_experimental_hls_segment_filter)) },
                description = { Text(stringResource(Lang.settings_player_experimental_hls_segment_filter_description)) },
            )
            HorizontalDividerItem()
        }
        HorizontalDividerItem()
        SwitchItem(
            checked = config.enableFramePreview,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(enableFramePreview = it))
            },
            title = { Text(stringResource(Lang.settings_player_frame_preview)) },
            description = { Text(stringResource(Lang.settings_player_frame_preview_description)) },
        )
        HorizontalDividerItem()
        PlaybackSpeedItems(config, videoScaffoldConfig)
        PlayerGroupPlatform(videoScaffoldConfig)
        // 「后台就绪提示音」与上面那条"保留播放状态"是同一个功能的两个参数: 没有回到会话的入口就
        // 不会有后台提示, 这条也就无从谈起. 所以用同一个门控.
        if (LocalAniUiBehavior.current.retainPlaybackSession) {
            val themeConfig by themeSettings
            val playNoticeSound = rememberNoticeSoundPlayer()
            HorizontalDividerItem()
            DropdownItem(
                selected = { themeConfig.tvNoticeSound },
                values = { NoticeSoundKind.entries },
                itemText = {
                    Text(
                        stringResource(
                            when (it) {
                                NoticeSoundKind.None -> Lang.settings_player_notice_sound_none
                                NoticeSoundKind.Confirm -> Lang.settings_player_notice_sound_confirm
                                NoticeSoundKind.Standard -> Lang.settings_player_notice_sound_standard
                                NoticeSoundKind.Alert -> Lang.settings_player_notice_sound_alert
                                NoticeSoundKind.Tick -> Lang.settings_player_notice_sound_tick
                                NoticeSoundKind.Delete -> Lang.settings_player_notice_sound_delete
                                NoticeSoundKind.Space -> Lang.settings_player_notice_sound_space
                            },
                        ),
                    )
                },
                onSelect = { kind ->
                    themeSettings.update(themeConfig.copy(tvNoticeSound = kind))
                    // 选完立刻响一次: 这些都是系统按键音, 光看名字听不出是什么, 不试听没法选.
                    // 传新值而不是等设置写回去再读 —— 写回是异步的, 那时候响的还是旧音色.
                    playNoticeSound(kind)
                },
                title = { Text(stringResource(Lang.settings_player_notice_sound)) },
                description = { Text(stringResource(Lang.settings_player_notice_sound_description)) },
            )
        }
    }
}

/**
 * 倍速范围 + 记住倍速 + 默认倍速 + 长按播放速度.
 *
 * 各条 Slider 共享范围拖动状态: 拖动范围 RangeSlider 期间, 下方各条 Slider 的范围和 clamp 后的值
 * 实时跟随, 被 clamp 时通过动画过渡, 避免松手后数值「突变」.
 */
@Composable
private fun SettingsScope.PlaybackSpeedItems(
    config: VideoScaffoldConfig,
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
) {
    // 遥控器形态下不提供"倍速范围": RangeSlider 的两个 thumb 在遥控器上没有明确的切换语义,
    // 根本调不动; 而它偏偏是唯一会**顺带改掉**常驻倍速与长按倍速的入口 —— withPlaybackSpeedRange
    // 会把这两个值一起夹进新范围, 而范围调回来它们不还原, 于是配置里可能留下一个用户在界面上
    // 看不见、也改不掉的倍速 (「记住播放倍速」开着时默认倍速那条滑块是隐藏的).
    //
    // 此时范围固定用播放器支持的全范围 (0.25x–4x), 并且**不读**配置里的 min/max: 既然这条设置
    // 已经不给了, 就别再替用户收窄, 而且以前被改窄过的配置也不该永远生效.
    val rangeConfigurable = !LocalAniUiBehavior.current.focusDrivenNavigation
    val persistedRange = if (rangeConfigurable) {
        config.minPlaybackSpeed..config.maxPlaybackSpeed
    } else {
        VideoScaffoldConfig.MIN_SUPPORTED_PLAYBACK_SPEED..VideoScaffoldConfig.MAX_SUPPORTED_PLAYBACK_SPEED
    }

    // 范围拖动期间的瞬态值, 提交后清空; 拖动期间下方长按倍速 Slider 实时使用该范围
    var rangeDragOverride by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    var rangeDragging by remember { mutableStateOf(false) }
    val effectiveRange = rangeDragOverride ?: persistedRange
    LaunchedEffect(persistedRange, rangeDragOverride, rangeDragging) {
        if (!rangeDragging && rangeDragOverride == persistedRange) {
            rangeDragOverride = null
        }
    }

    if (rangeConfigurable) {
        RangeSliderItem(
            value = effectiveRange,
            onValueChange = {
                rangeDragging = true
                rangeDragOverride = VideoScaffoldConfig.normalizePlaybackSpeedRange(it, effectiveRange)
            },
            onValueChangeFinished = {
                val finalRange = rangeDragOverride ?: return@RangeSliderItem
                // 缩小范围时把相关值一并 clamp 进新区间；保留最终范围直到配置写回，避免短暂回跳
                val committedConfig = config.withPlaybackSpeedRange(finalRange)
                rangeDragging = false
                rangeDragOverride = committedConfig.minPlaybackSpeed..committedConfig.maxPlaybackSpeed
                videoScaffoldConfig.update(committedConfig)
            },
            valueRange = VideoScaffoldConfig.MIN_SUPPORTED_PLAYBACK_SPEED..VideoScaffoldConfig.MAX_SUPPORTED_PLAYBACK_SPEED,
            steps = 14,
            valueIndicator = { Text(it.formatSpeedValue()) },
            valueLabel = {
                Text("${effectiveRange.start.formatSpeedValue()}x–${effectiveRange.endInclusive.formatSpeedValue()}x")
            },
            title = { Text(stringResource(Lang.settings_player_playback_speed_range)) },
            description = { Text(stringResource(Lang.settings_player_playback_speed_range_description)) },
        )
    }

    // 范围变化以动画过渡, 避免 thumb 映射位置瞬移
    val animatedRangeStart by animateFloatAsState(effectiveRange.start, label = "playbackSpeedRangeStart")
    val animatedRangeEnd by animateFloatAsState(effectiveRange.endInclusive, label = "playbackSpeedRangeEnd")
    val displayRange =
        if (animatedRangeStart < animatedRangeEnd) animatedRangeStart..animatedRangeEnd else effectiveRange

    // 上一条被隐藏时这里就是本组的第一条, 不能再画一道分隔线 (调用方已经画过一道)
    if (rangeConfigurable) {
        HorizontalDividerItem()
    }

    SwitchItem(
        checked = config.rememberPlaybackSpeed,
        onCheckedChange = {
            videoScaffoldConfig.update(config.copy(rememberPlaybackSpeed = it))
        },
        title = { Text(stringResource(Lang.settings_player_remember_playback_speed)) },
        description = { Text(stringResource(Lang.settings_player_remember_playback_speed_description)) },
    )

    // 此处没有 ColumnScope 接收者, 不显式指定就会落到 fadeIn/fadeOut 的重载上, 高度瞬间撑开、下方条目跳位.
    val motionScheme = LocalAniMotionScheme.current.animatedVisibility
    AniAnimatedVisibility(
        visible = !config.rememberPlaybackSpeed,
        enter = motionScheme.columnEnter,
        exit = motionScheme.columnExit,
    ) {
        Column {
            HorizontalDividerItem()
            SpeedSliderItem(
                value = config.playbackSpeed,
                displayRange = displayRange,
                commitRange = effectiveRange,
                onCommit = { videoScaffoldConfig.update(config.copy(playbackSpeed = it)) },
                title = { Text(stringResource(Lang.settings_player_default_playback_speed)) },
                description = { Text(stringResource(Lang.settings_player_default_playback_speed_description)) },
            )
        }
    }

    HorizontalDividerItem()

    SpeedSliderItem(
        value = config.fastForwardSpeed,
        displayRange = displayRange,
        commitRange = effectiveRange,
        onCommit = { videoScaffoldConfig.update(config.copy(fastForwardSpeed = it)) },
        title = { Text(stringResource(Lang.settings_player_long_press_fast_forward_speed)) },
        description = { Text(stringResource(Lang.settings_player_long_press_fast_forward_speed_description)) },
    )
}

/**
 * 一条倍速 Slider. 拖动期间使用瞬态值实时预览, 松手后量化到 [commitRange] 再提交.
 *
 * @param displayRange 渲染用范围, 可能正处于动画过渡中
 * @param commitRange 提交用范围, 即用户配置的真实范围
 */
@Composable
private fun SettingsScope.SpeedSliderItem(
    value: Float,
    displayRange: ClosedFloatingPointRange<Float>,
    commitRange: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
    title: @Composable RowScope.() -> Unit,
    description: @Composable (() -> Unit)? = null,
) {
    var dragOverride by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val displayValue = (dragOverride ?: value).coerceIn(displayRange)
    LaunchedEffect(value, dragOverride, dragging) {
        if (!dragging && dragOverride == value) {
            dragOverride = null
        }
    }
    SliderItem(
        title = title,
        description = description,
        valueLabel = {
            Text("${quantizeSliderValue(displayValue, displayRange).formatSpeedValue()}x")
        },
    ) {
        SteppedSlider(
            value = displayValue,
            onValueChange = {
                dragging = true
                dragOverride = quantizeSliderValue(it, displayRange)
            },
            onValueChangeFinished = { displayedValue ->
                val finalValue = dragOverride ?: displayedValue
                val committedValue = quantizeSliderValue(finalValue, commitRange)
                dragging = false
                dragOverride = committedValue
                onCommit(committedValue)
            },
            valueRange = displayRange,
            valueIndicator = { Text(it.formatSpeedValue(), maxLines = 1, softWrap = false) },
        )
    }
}

@Composable
internal expect fun SettingsScope.LanguageSettingsPlatform(
    state: SettingsState<UISettings>,
)

@Composable
internal expect fun SettingsScope.AppSettingsTabPlatform()

@Composable
private fun DesktopCloseBehavior.renderText(): String {
    return when (this) {
        DesktopCloseBehavior.EXIT -> stringResource(Lang.settings_app_close_behavior_exit)
        DesktopCloseBehavior.MINIMIZE -> stringResource(Lang.settings_app_close_behavior_minimize_to_tray)
    }
}

@Composable
internal expect fun SettingsScope.PlayerGroupPlatform(
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
)

@Composable
internal fun renderLocale(it: Locale?): String {
    if (it == null) {
        return stringResource(Lang.settings_app_language_system)
    }

    // The following code does not need to be localized
    return when (it.language) {
        "en", "eng" -> "English"
        "zh", "chi", "zho" -> when (it.region) {
            "CN" -> "简体中文"
            "HK" -> "繁體中文(香港)"
            "TW" -> "正體中文"
            else -> "繁體中文"
        }

        else -> """${it.language}-${it.region}"""
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewAppSettingsTab() {
    AppSettingsTab(
        softwareUpdateGroupState = rememberTestSoftwareUpdateGroupState(),
        uiSettings = rememberTestSettingsState(UISettings.Default),
        themeSettings = rememberTestSettingsState(ThemeSettings.Default),
        videoScaffoldConfig = rememberTestSettingsState(VideoScaffoldConfig.Default),
        watchTogetherSettings = rememberTestSettingsState(WatchTogetherSettings.Default),
        danmakuFilterConfig = rememberTestSettingsState(DanmakuFilterConfig.Default),
        danmakuRegexFilterState = createTestDanmakuRegexFilterState(),
        showDebug = true,
    )
}

@TestOnly
@Composable
internal fun rememberTestSoftwareUpdateGroupState(): SoftwareUpdateGroupState {
    val scope = rememberCoroutineScope()
    return remember {
        SoftwareUpdateGroupState(
            updateSettings = createTestSettingsState(UpdateSettings(autoCheckUpdate = true), scope),
        )
    }
}
