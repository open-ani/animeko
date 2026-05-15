/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.rememberDebugSettingsViewModel
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_video_settings_bottom
import me.him188.ani.app.ui.lang.subject_episode_video_settings_colorful
import me.him188.ani.app.ui.lang.subject_episode_video_settings_debug_mode
import me.him188.ani.app.ui.lang.subject_episode_video_settings_density
import me.him188.ani.app.ui.lang.subject_episode_video_settings_density_dense
import me.him188.ani.app.ui.lang.subject_episode_video_settings_density_medium
import me.him188.ani.app.ui.lang.subject_episode_video_settings_density_sparse
import me.him188.ani.app.ui.lang.subject_episode_video_settings_display_area
import me.him188.ani.app.ui.lang.subject_episode_video_settings_display_area_full
import me.him188.ani.app.ui.lang.subject_episode_video_settings_display_area_half
import me.him188.ani.app.ui.lang.subject_episode_video_settings_display_area_off
import me.him188.ani.app.ui.lang.subject_episode_video_settings_display_area_one_eighth
import me.him188.ani.app.ui.lang.subject_episode_video_settings_display_area_one_quarter
import me.him188.ani.app.ui.lang.subject_episode_video_settings_display_area_one_sixth
import me.him188.ani.app.ui.lang.subject_episode_video_settings_display_area_three_quarters
import me.him188.ani.app.ui.lang.subject_episode_video_settings_enable_regex_filter
import me.him188.ani.app.ui.lang.subject_episode_video_settings_floating
import me.him188.ani.app.ui.lang.subject_episode_video_settings_font_size
import me.him188.ani.app.ui.lang.subject_episode_video_settings_font_weight
import me.him188.ani.app.ui.lang.subject_episode_video_settings_manage_regex_filter
import me.him188.ani.app.ui.lang.subject_episode_video_settings_opacity
import me.him188.ani.app.ui.lang.subject_episode_video_settings_speed
import me.him188.ani.app.ui.lang.subject_episode_video_settings_speed_description
import me.him188.ani.app.ui.lang.subject_episode_video_settings_stroke_width
import me.him188.ani.app.ui.lang.subject_episode_video_settings_top
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.AbstractSettingsViewModel
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsDefaults
import me.him188.ani.app.ui.settings.framework.components.SliderItem
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuStyle
import me.him188.ani.utils.platform.isDesktop
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt

@Stable
class EpisodeVideoSettingsViewModel : AbstractSettingsViewModel(), KoinComponent {
    private val settingsRepository by inject<SettingsRepository>()
    private val danmakuRegexFilterRepository by inject<DanmakuRegexFilterRepository>()

    private val danmakuConfigState: SettingsState<DanmakuConfig> =
        settingsRepository.danmakuConfig.stateInBackground(
            placeholder = DanmakuConfig.Default,
        )

    private val danmakuFilterConfigState =
        settingsRepository.danmakuFilterConfig.stateInBackground(
            DanmakuFilterConfig.Default.copy(_placeholder = -1),
        )

    val danmakuConfig: DanmakuConfig by danmakuConfigState
    val danmakuRegexFilterList: List<DanmakuRegexFilter> by danmakuRegexFilterRepository.flow.produceState(
        initialValue = emptyList(),
    )
    val danmakuFilterConfig: DanmakuFilterConfig by danmakuFilterConfigState
    val isLoading: Boolean by derivedStateOf {
        danmakuConfigState.isLoading || danmakuFilterConfigState.isLoading
    }

    fun setDanmakuConfig(transform: DanmakuConfig.() -> DanmakuConfig) {
        danmakuConfigState.update(transform(danmakuConfig))
    }

    fun switchDanmakuRegexFilterCompletely() {
        danmakuFilterConfigState.update(
            danmakuFilterConfig.copy(enableRegexFilter = !danmakuFilterConfig.enableRegexFilter),
        )
    }
}

@Composable
fun EpisodeVideoSettings(
    vm: EpisodeVideoSettingsViewModel,
    onNavigateToFilterSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    return EpisodeVideoSettings(
        danmakuConfig = vm.danmakuConfig,
        setDanmakuConfig = remember(vm) {
            vm::setDanmakuConfig
        },
        modifier = modifier,
        onManageRegexFilters = onNavigateToFilterSettings,
        enableRegexFilter = vm.danmakuFilterConfig.enableRegexFilter,
        switchDanmakuRegexFilterCompletely = vm::switchDanmakuRegexFilterCompletely,
    )
}

@Composable
fun EpisodeVideoSettings(
    danmakuConfig: DanmakuConfig,
    setDanmakuConfig: ((DanmakuConfig) -> DanmakuConfig) -> Unit,
    enableRegexFilter: Boolean,
    onManageRegexFilters: () -> Unit,
    switchDanmakuRegexFilterCompletely: () -> Unit,
    modifier: Modifier = Modifier,
    useThinSlider: Boolean = true
) {
    val topText = stringResource(Lang.subject_episode_video_settings_top)
    val floatingText = stringResource(Lang.subject_episode_video_settings_floating)
    val bottomText = stringResource(Lang.subject_episode_video_settings_bottom)
    val colorfulText = stringResource(Lang.subject_episode_video_settings_colorful)
    val fontSizeText = stringResource(Lang.subject_episode_video_settings_font_size)
    val opacityText = stringResource(Lang.subject_episode_video_settings_opacity)
    val strokeWidthText = stringResource(Lang.subject_episode_video_settings_stroke_width)
    val fontWeightText = stringResource(Lang.subject_episode_video_settings_font_weight)
    val speedText = stringResource(Lang.subject_episode_video_settings_speed)
    val speedDescriptionText = stringResource(Lang.subject_episode_video_settings_speed_description)
    val densityText = stringResource(Lang.subject_episode_video_settings_density)
    val denseText = stringResource(Lang.subject_episode_video_settings_density_dense)
    val mediumText = stringResource(Lang.subject_episode_video_settings_density_medium)
    val sparseText = stringResource(Lang.subject_episode_video_settings_density_sparse)
    val displayAreaText = stringResource(Lang.subject_episode_video_settings_display_area)
    val displayAreaOffText = stringResource(Lang.subject_episode_video_settings_display_area_off)
    val displayAreaOneEighthText = stringResource(Lang.subject_episode_video_settings_display_area_one_eighth)
    val displayAreaOneSixthText = stringResource(Lang.subject_episode_video_settings_display_area_one_sixth)
    val displayAreaOneQuarterText = stringResource(Lang.subject_episode_video_settings_display_area_one_quarter)
    val displayAreaHalfText = stringResource(Lang.subject_episode_video_settings_display_area_half)
    val displayAreaThreeQuartersText = stringResource(Lang.subject_episode_video_settings_display_area_three_quarters)
    val displayAreaFullText = stringResource(Lang.subject_episode_video_settings_display_area_full)
    val enableRegexFilterText = stringResource(Lang.subject_episode_video_settings_enable_regex_filter)
    val manageRegexFilterText = stringResource(Lang.subject_episode_video_settings_manage_regex_filter)
    val debugModeText = stringResource(Lang.subject_episode_video_settings_debug_mode)

    SettingsTab(modifier.verticalScroll(rememberScrollState())) {
        Column {
            Surface(Modifier.fillMaxWidth(), color = SettingsDefaults.groupBackgroundColor) {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ElevatedFilterChip(
                        selected = danmakuConfig.enableTop,
                        onClick = { setDanmakuConfig { config -> config.copy(enableTop = !config.enableTop) } },
                        leadingIcon = {
                            if (danmakuConfig.enableTop) Icon(Icons.Rounded.Check, contentDescription = null)
                            else Icon(Icons.Rounded.Close, contentDescription = null)
                        },
                        label = { Text(topText, maxLines = 1) },
                    )
                    ElevatedFilterChip(
                        selected = danmakuConfig.enableFloating,
                        onClick = { setDanmakuConfig { config -> config.copy(enableFloating = !config.enableFloating) } },
                        label = { Text(floatingText, maxLines = 1) },
                        leadingIcon = {
                            if (danmakuConfig.enableFloating) Icon(Icons.Rounded.Check, contentDescription = null)
                            else Icon(Icons.Rounded.Close, contentDescription = null)
                        },
                    )
                    ElevatedFilterChip(
                        selected = danmakuConfig.enableBottom,
                        onClick = { setDanmakuConfig { config -> config.copy(enableBottom = !config.enableBottom) } },
                        label = { Text(bottomText, maxLines = 1) },
                        leadingIcon = {
                            if (danmakuConfig.enableBottom) Icon(Icons.Rounded.Check, contentDescription = null)
                            else Icon(Icons.Rounded.Close, contentDescription = null)
                        },
                    )
                    ElevatedFilterChip(
                        selected = danmakuConfig.enableColor,
                        onClick = {
                            setDanmakuConfig { config -> config.copy(enableColor = !config.enableColor) }
                        },
                        leadingIcon = {
                            if (danmakuConfig.enableColor) Icon(Icons.Rounded.Check, contentDescription = null)
                            else Icon(Icons.Rounded.Close, contentDescription = null)
                        },
                        label = { Text(colorfulText, maxLines = 1) },
                    )
                }
            }
            val fontSize by remember(danmakuConfig) {
                mutableFloatStateOf(danmakuConfig.style.fontSize.value / DanmakuStyle.Default.fontSize.value)
            }
            SliderItem(
                value = fontSize,
                onValueChange = { newValue ->
                    // 故意每次改都更新, 可以即时预览
                    setDanmakuConfig { config -> config.copy(style = config.style.copy(fontSize = DanmakuStyle.Default.fontSize * newValue)) }
                },
                valueRange = 0.50f..3f,
//                steps = ((3f - 0.50f) / 0.05f).toInt() - 1,
                title = { Text(fontSizeText) },
                valueLabel = { Text(remember(fontSize) { "${(fontSize * 100).roundToInt()}%" }) },
                useThinSlider = useThinSlider,
            )

            val alpha by remember(danmakuConfig) {
                mutableFloatStateOf(danmakuConfig.style.alpha)
            }
            SliderItem(
                value = alpha,
                onValueChange = { newValue ->
                    // 故意每次改都更新, 可以即时预览
                    setDanmakuConfig { config -> config.copy(style = config.style.copy(alpha = newValue)) }
                },
                valueRange = 0f..1f,
//                steps = ((1f - 0f) / 0.05f).toInt() - 1,
                title = { Text(opacityText) },
                valueLabel = { Text(remember(alpha) { "${(alpha * 100).roundToInt()}%" }) },
                useThinSlider = useThinSlider,
            )

            val strokeWidth by remember(danmakuConfig) {
                mutableFloatStateOf(danmakuConfig.style.strokeWidth / DanmakuStyle.Default.strokeWidth)
            }
            SliderItem(
                value = strokeWidth,
                onValueChange = { newValue ->
                    // 故意每次改都更新, 可以即时预览
                    setDanmakuConfig { config -> config.copy(style = config.style.copy(strokeWidth = newValue * DanmakuStyle.Default.strokeWidth)) }
                },
                valueRange = 0f..2f,
//                steps = ((2f - 0f) / 0.1f).toInt() - 1,
                title = { Text(strokeWidthText) },
                valueLabel = { Text(remember(strokeWidth) { "${(strokeWidth * 100).roundToInt()}%" }) },
                useThinSlider = useThinSlider,
            )

            val fontWeight by remember(danmakuConfig) {
                derivedStateOf {
                    danmakuConfig.style.fontWeight.weight.toFloat()
                }
            }
            SliderItem(
                value = fontWeight,
                onValueChange = { newValue ->
                    if (newValue != fontWeight) {
                        // 故意每次改都更新, 可以即时预览
                        setDanmakuConfig { config ->
                            config.copy(style = config.style.copy(fontWeight = FontWeight(newValue.toInt())))
                        }
                    }
                },
                valueRange = 100f..900f,
//                steps = ((900 - 100) / 100) - 1,
                title = { Text(fontWeightText) },
                valueLabel = { Text(remember(fontWeight) { "${fontWeight.toInt()}" }) },
                useThinSlider = useThinSlider,
            )

            val speed by remember(danmakuConfig) {
                mutableFloatStateOf(
                    danmakuConfig.speed / DanmakuConfig.Default.speed,
                )
            }
            SliderItem(
                value = speed,
                onValueChange = { newValue ->
                    setDanmakuConfig { config -> config.copy(speed = newValue * DanmakuConfig.Default.speed) }
                },
                valueRange = 0.2f..3f,
//                steps = ((3f - 0.2f) / 0.1f).toInt() - 1,
                title = { Text(speedText) },
                description = { Text(speedDescriptionText) },
                valueLabel = { Text(remember(speed) { "${(speed * 100).roundToInt()}%" }) },
                useThinSlider = useThinSlider,
            )

            val platform = LocalPlatform.current
            val displayDensityRange = remember(platform) {
                // 100% .. 0%
                36.dp..(if (platform.isDesktop()) 720.dp else 240.dp)
            }
            var displayDensity by remember(danmakuConfig) {
                mutableFloatStateOf(
                    1.minus(
                        (danmakuConfig.safeSeparation - displayDensityRange.start) /
                                (displayDensityRange.endInclusive - displayDensityRange.start + 1.dp),
                    ).div(0.1f).roundToInt().toFloat(),
                )
            }
            SliderItem(
                value = displayDensity,
                onValueChange = {
                    displayDensity = it
                },
                // 这个会导致 repopulate, 所以改完了才更新
                onValueChangeFinished = {
                    setDanmakuConfig { config ->
                        config.copy(
                            safeSeparation = displayDensityRange.start +
                                    ((displayDensityRange.endInclusive - displayDensityRange.start + 1.dp)
                                        .times((1 - displayDensity * 0.1f))),
                        )
                    }
                },
                valueRange = 0f..10f,
                steps = 9,
                title = { Text(densityText) },
                valueLabel = {
                    when (displayDensity.toInt()) {
                        in 7..10 -> Text(denseText)
                        in 4..6 -> Text(mediumText)
                        in 0..3 -> Text(sparseText)
                    }
                },
                useThinSlider = useThinSlider,
            )


            SliderItem(
                value = danmakuConfig.displayArea,
                onValueChange = { newValue ->
                    setDanmakuConfig { config -> config.copy(displayArea = newValue.coerceIn(0f, 1f)) }
                },
                valueRange = 0f..1f,
                title = { Text(displayAreaText) },
                valueLabel = {
                    val v = danmakuConfig.displayArea
                    when {
                        v == 0f -> Text(displayAreaOffText)
                        v <= 1 / 8f -> Text(displayAreaOneEighthText)
                        v <= 1 / 6f -> Text(displayAreaOneSixthText)
                        v <= 1 / 4f -> Text(displayAreaOneQuarterText)
                        v <= 1 / 2f -> Text(displayAreaHalfText)
                        v <= 3 / 4f -> Text(displayAreaThreeQuartersText)
                        v == 1f -> Text(displayAreaFullText)
                    }
                },
                useThinSlider = useThinSlider,
            )

            SwitchItem(
                enableRegexFilter,
                onCheckedChange = {
                    switchDanmakuRegexFilterCompletely()
                },
                title = { Text(enableRegexFilterText) },
            )

            TextItem(
                onClick = { onManageRegexFilters() },
            ) {
                Text(manageRegexFilterText)
            }

            val debugViewModel = rememberDebugSettingsViewModel()
            if (debugViewModel.isAppInDebugMode) {

                SwitchItem(
                    danmakuConfig.isDebug,
                    onCheckedChange = { checked ->
                        setDanmakuConfig { config -> config.copy(isDebug = checked) }
                    },
                    title = { Text(debugModeText) },
                )

                val debugSettings by debugViewModel.debugSettings
                SwitchItem(
                    debugSettings.showControllerAlwaysOnRequesters,
                    onCheckedChange = {
                        debugViewModel.updateDebugSettings(debugSettings.copy(showControllerAlwaysOnRequesters = it))
                    },
                    title = { Text("showControllerAlwaysOnRequesters") },
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewEpisodeVideoSettings() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoSettings(
            remember { EpisodeVideoSettingsViewModel() },
            { },
        )
    }
}

@Preview(heightDp = 200)
@Composable
private fun PreviewEpisodeVideoSettingsSmall() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoSettings(
            remember { EpisodeVideoSettingsViewModel() },
            { },
        )
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Preview
@Composable
private fun PreviewEpisodeVideoSettingsSideSheet() = ProvideCompositionLocalsForPreview {
    var showSettings by remember { mutableStateOf(true) }
    if (showSettings) {
        SideSheetLayout(
            title = {},
            onDismissRequest = { showSettings = false },
        ) {
            EpisodeVideoSettings(
                remember { EpisodeVideoSettingsViewModel() },
                { },
                Modifier.padding(8.dp),
            )
        }
    }
}
