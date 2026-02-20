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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.preference.EpisodeListProgressTheme
import me.him188.ani.app.data.models.preference.FullscreenSwitchMode
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.UpdateSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.network.protocol.ReleaseClass
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.getIcon
import me.him188.ani.app.navigation.getText
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.SupportedLocales
import me.him188.ani.app.ui.lang.settings_app_episode_playback
import me.him188.ani.app.ui.lang.settings_app_initial_page
import me.him188.ani.app.ui.lang.settings_app_initial_page_description
import me.him188.ani.app.ui.lang.settings_app_language
import me.him188.ani.app.ui.lang.settings_app_language_restart
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
import me.him188.ani.app.ui.lang.settings_app_use_new_search_api
import me.him188.ani.app.ui.lang.settings_app_use_new_search_api_description
import me.him188.ani.app.ui.lang.settings_player
import me.him188.ani.app.ui.lang.settings_player_auto_fullscreen_on_landscape
import me.him188.ani.app.ui.lang.settings_player_auto_mark_done
import me.him188.ani.app.ui.lang.settings_player_auto_play_next
import me.him188.ani.app.ui.lang.settings_player_auto_skip_op_ed
import me.him188.ani.app.ui.lang.settings_player_auto_skip_op_ed_description
import me.him188.ani.app.ui.lang.settings_player_auto_switch_media_on_error
import me.him188.ani.app.ui.lang.settings_player_enable_regex_filter
import me.him188.ani.app.ui.lang.settings_player_fullscreen_always_show
import me.him188.ani.app.ui.lang.settings_player_fullscreen_auto_hide
import me.him188.ani.app.ui.lang.settings_player_fullscreen_button
import me.him188.ani.app.ui.lang.settings_player_fullscreen_button_description
import me.him188.ani.app.ui.lang.settings_player_fullscreen_only_in_controller
import me.him188.ani.app.ui.lang.settings_player_hide_selector_on_select
import me.him188.ani.app.ui.lang.settings_player_long_press_fast_forward_speed
import me.him188.ani.app.ui.lang.settings_player_long_press_fast_forward_speed_description
import me.him188.ani.app.ui.lang.settings_player_pause_on_edit_danmaku
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
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterGroup
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.settings.danmaku.createTestDanmakuRegexFilterState
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.DropdownItem
import me.him188.ani.app.ui.settings.framework.components.RowButtonItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
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
import me.him188.ani.app.ui.update.UpdateNotifier
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isAndroid
import me.him188.ani.utils.platform.isDesktop
import me.him188.ani.utils.platform.isIos
import me.him188.ani.utils.platform.isMobile
import org.jetbrains.compose.resources.stringResource

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
    danmakuFilterConfig: SettingsState<DanmakuFilterConfig>,
    danmakuRegexFilterState: DanmakuRegexFilterState,
    showDebug: Boolean,
    modifier: Modifier = Modifier
) {
    SettingsTab(modifier) {
        SoftwareUpdateGroup(softwareUpdateGroupState)
        AppearanceGroup(uiSettings)
        ThemeGroup(themeSettings)
        PlayerGroup(
            videoScaffoldConfig,
            danmakuFilterConfig,
            danmakuRegexFilterState,
            showDebug,
        )
        AppSettingsTabPlatform()
    }
}

@Composable
fun SettingsScope.AppearanceGroup(
    state: SettingsState<UISettings>,
) {
    val uiSettings by state

    if (LocalPlatform.current.isDesktop() || LocalPlatform.current.isAndroid()) {
        DropdownItem(
            selected = { uiSettings.appLanguage },
            values = { SupportedLocales },
            itemText = {
                Text(renderLocale(it))
            },
            onSelect = {
                state.update(uiSettings.copy(appLanguage = it))
            },
            title = { Text(stringResource(Lang.settings_app_language)) },
            description = if (LocalPlatform.current.isDesktop()) {
                { Text(stringResource(Lang.settings_app_language_restart)) }
            } else null,
        )
    }
    IosLanguageSettings()

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

    Group(title = { Text(stringResource(Lang.settings_app_search)) }, useThinHeader = true) {
        SwitchItem(
            checked = uiSettings.searchSettings.enableNewSearchSubjectApi,
            onCheckedChange = {
                state.update(
                    uiSettings.copy(
                        searchSettings = uiSettings.searchSettings.copy(
                            enableNewSearchSubjectApi = !uiSettings.searchSettings.enableNewSearchSubjectApi,
                        ),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_use_new_search_api)) },
            description = { Text(stringResource(Lang.settings_app_use_new_search_api_description)) },
        )
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
            UpdateNotifier(autoUpdate)
        }
    }
}

@Composable
fun SettingsScope.PlayerGroup(
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
    danmakuFilterConfig: SettingsState<DanmakuFilterConfig>,
    danmakuRegexFilterState: DanmakuRegexFilterState,
    showDebug: Boolean
) {
    Group(title = { Text(stringResource(Lang.settings_player)) }) {
        val config by videoScaffoldConfig
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
        if (LocalPlatform.current.isMobile()) {
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
        if (LocalPlatform.current.isDesktop()) {
            HorizontalDividerItem()
            SwitchItem(
                checked = config.autoSkipOpEd,
                onCheckedChange = {
                    videoScaffoldConfig.update(config.copy(autoSkipOpEd = it))
                },
                title = { Text(stringResource(Lang.settings_player_auto_skip_op_ed)) },
                description = { Text(stringResource(Lang.settings_player_auto_skip_op_ed_description)) },
            )
        }
        HorizontalDividerItem()
        SwitchItem(
            checked = config.autoSwitchMediaOnPlayerError,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(autoSwitchMediaOnPlayerError = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_switch_media_on_error)) },
        )
        HorizontalDividerItem()
        DropdownItem(
            selected = { config.fastForwardSpeed },
            values = { listOf(1.5f, 2f, 2.5f, 3f) },
            itemText = { Text("${it}x") },
            onSelect = {
                videoScaffoldConfig.update(config.copy(fastForwardSpeed = it))
            },
            title = { Text(stringResource(Lang.settings_player_long_press_fast_forward_speed)) },
            description = { Text(stringResource(Lang.settings_player_long_press_fast_forward_speed_description)) },
        )
        PlayerGroupPlatform(videoScaffoldConfig)
    }
}

@Composable
internal expect fun SettingsScope.IosLanguageSettings()

@Composable
internal expect fun SettingsScope.AppSettingsTabPlatform()

@Composable
internal expect fun SettingsScope.PlayerGroupPlatform(
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
)

@Composable
private fun renderLocale(it: Locale?): String {
    if (it == null) {
        return "系统语言"
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
