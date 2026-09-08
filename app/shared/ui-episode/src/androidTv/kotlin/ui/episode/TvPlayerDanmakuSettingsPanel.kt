/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.episode_danmaku_sources_timing
import me.him188.ani.app.ui.lang.episode_danmaku_timing
import me.him188.ani.app.ui.lang.subject_episode_danmaku_list_title
import me.him188.ani.app.ui.lang.subject_episode_danmaku_rematch
import me.him188.ani.app.ui.lang.subject_episode_video_settings_bottom
import me.him188.ani.app.ui.lang.subject_episode_video_settings_colorful
import me.him188.ani.app.ui.lang.subject_episode_video_settings_density
import me.him188.ani.app.ui.lang.subject_episode_video_settings_density_dense
import me.him188.ani.app.ui.lang.subject_episode_video_settings_density_medium
import me.him188.ani.app.ui.lang.subject_episode_video_settings_density_sparse
import me.him188.ani.app.ui.lang.subject_episode_video_settings_display_area
import me.him188.ani.app.ui.lang.subject_episode_video_settings_floating
import me.him188.ani.app.ui.lang.subject_episode_video_settings_font_size
import me.him188.ani.app.ui.lang.subject_episode_video_settings_font_weight
import me.him188.ani.app.ui.lang.subject_episode_video_settings_opacity
import me.him188.ani.app.ui.lang.subject_episode_video_settings_speed
import me.him188.ani.app.ui.lang.subject_episode_video_settings_stroke_width
import me.him188.ani.app.ui.lang.subject_episode_video_settings_top
import me.him188.ani.app.ui.lang.video_player_off
import me.him188.ani.app.ui.lang.video_player_on
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuConfigRanges
import me.him188.ani.danmaku.ui.DanmakuStyle
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.widgets.LocalTvOptionColors
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private sealed interface DanmakuSettingsKey : TvFocusKey {
    data object List : DanmakuSettingsKey
    data class Match(val serviceId: DanmakuServiceId) : DanmakuSettingsKey
}

/** The settings list owns its row identities and the preparation of return focus. */
internal class TvDanmakuSettingsPanelState(val listState: LazyListState) {
    suspend fun prepareReturn(origins: List<TvDanmakuOrigin>, source: DanmakuServiceId?): TvFocusKey {
        val match = origins.firstOrNull { it.serviceId == source && it.canMatch }
        val target = match?.let { DanmakuSettingsKey.Match(it.serviceId) } ?: DanmakuSettingsKey.List
        val rowKeys = buildList {
            addAll(TvDanmakuProperty.entries)
            add("origins-heading")
            addAll(origins.map { "origin-${it.serviceId.value}" })
            add(DanmakuSettingsKey.List)
            addAll(origins.filter { it.canMatch }.map { DanmakuSettingsKey.Match(it.serviceId) })
        }
        listState.scrollToItem(rowKeys.indexOf(target))
        return target
    }
}

@Composable
internal fun TvPlayerDanmakuSettingsPanel(
    config: DanmakuConfig,
    origins: List<TvDanmakuOrigin>,
    onIntent: (TvEpisodeIntent) -> Boolean,
    onOpenList: () -> Unit,
    onMatch: (TvDanmakuOrigin) -> Unit,
    danmakuAdjustment: TvDanmakuAdjustment?,
    onDanmakuAdjustmentChange: (TvDanmakuAdjustment?) -> Unit,
    state: TvDanmakuSettingsPanelState,
    focus: TvFocusScope,
    entryModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvOptionColors.current
    TvPlayerOptionPanelLayout(TvPlayerPanel.DanmakuSettings, state.listState, modifier) {
        items(TvDanmakuProperty.entries) { property ->
            val (label, value) = when (property) {
                TvDanmakuProperty.FontSize -> stringResource(Lang.subject_episode_video_settings_font_size) to "${(config.style.fontSize.value / DanmakuStyle.Default.fontSize.value * 100).roundToInt()}%"
                TvDanmakuProperty.Opacity -> stringResource(Lang.subject_episode_video_settings_opacity) to "${(config.style.alpha * 100).roundToInt()}%"
                TvDanmakuProperty.Speed -> stringResource(Lang.subject_episode_video_settings_speed) to "${(config.speed / DanmakuConfig.Default.speed * 100).roundToInt()}%"
                TvDanmakuProperty.Density -> stringResource(Lang.subject_episode_video_settings_density) to when (DanmakuConfigRanges.densityLevel(
                    config.safeSeparation, DanmakuConfigRanges.densitySeparation(desktop = false),
                ).toInt()) {
                    in 7..10 -> stringResource(Lang.subject_episode_video_settings_density_dense)
                    in 4..6 -> stringResource(Lang.subject_episode_video_settings_density_medium)
                    else -> stringResource(Lang.subject_episode_video_settings_density_sparse)
                }
                TvDanmakuProperty.Area -> stringResource(Lang.subject_episode_video_settings_display_area) to if (config.displayArea == 0f) stringResource(Lang.video_player_off) else "${(config.displayArea * 100).roundToInt()}%"
                TvDanmakuProperty.Stroke -> stringResource(Lang.subject_episode_video_settings_stroke_width) to "${(config.style.strokeWidth / DanmakuStyle.Default.strokeWidth * 100).roundToInt()}%"
                TvDanmakuProperty.Weight -> stringResource(Lang.subject_episode_video_settings_font_weight) to config.style.fontWeight.weight.toString()
                TvDanmakuProperty.Top -> stringResource(Lang.subject_episode_video_settings_top) to if (config.enableTop) stringResource(Lang.video_player_on) else stringResource(Lang.video_player_off)
                TvDanmakuProperty.Bottom -> stringResource(Lang.subject_episode_video_settings_bottom) to if (config.enableBottom) stringResource(Lang.video_player_on) else stringResource(Lang.video_player_off)
                TvDanmakuProperty.Floating -> stringResource(Lang.subject_episode_video_settings_floating) to if (config.enableFloating) stringResource(Lang.video_player_on) else stringResource(Lang.video_player_off)
                TvDanmakuProperty.Color -> stringResource(Lang.subject_episode_video_settings_colorful) to if (config.enableColor) stringResource(Lang.video_player_on) else stringResource(Lang.video_player_off)
            }
            val checked = when (property) {
                TvDanmakuProperty.Top -> config.enableTop
                TvDanmakuProperty.Bottom -> config.enableBottom
                TvDanmakuProperty.Floating -> config.enableFloating
                TvDanmakuProperty.Color -> config.enableColor
                else -> null
            }
            if (checked == null) {
                val adjustment = TvDanmakuAdjustment.Parameter(property)
                TvDanmakuAdjustmentRow(
                    label, value,
                    adjusting = danmakuAdjustment == adjustment,
                    onAdjustingChange = { adjusting -> onDanmakuAdjustmentChange(adjustment.takeIf { adjusting }) },
                    onStep = { onIntent(TvEpisodeIntent.AdjustDanmaku(property, it)) },
                    modifier = (if (property == TvDanmakuProperty.FontSize) entryModifier else Modifier)
                        .testTag("tv-danmaku-property-${property.name}"),
                )
            } else {
                TvOptionRow(
                    label,
                    checked = checked,
                    modifier = Modifier.tvStepKeys { onIntent(TvEpisodeIntent.AdjustDanmaku(property, it)) },
                ) { onIntent(TvEpisodeIntent.AdjustDanmaku(property, 1)) }
            }
        }
        item { TvPlayerSectionLabel(stringResource(Lang.episode_danmaku_sources_timing)) }
        items(origins, key = { "origin-${it.serviceId.value}" }) { origin ->
            Column {
                TvOptionRow(
                    origin.name,
                    checked = origin.enabled,
                ) { onIntent(TvEpisodeIntent.ToggleDanmakuSource(origin.serviceId)) }
                Text(
                    origin.matchDescription,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                val adjustment = TvDanmakuAdjustment.Timing(origin.serviceId)
                TvDanmakuAdjustmentRow(
                    stringResource(Lang.episode_danmaku_timing), "${origin.shiftMillis / 1000f}s",
                    adjusting = danmakuAdjustment == adjustment,
                    onAdjustingChange = { adjusting -> onDanmakuAdjustmentChange(adjustment.takeIf { adjusting }) },
                    onStep = {
                        onIntent(
                            TvEpisodeIntent.ShiftDanmakuSource(
                                origin.serviceId,
                                it * 500L,
                            ),
                        )
                    },
                    onReset = { onIntent(TvEpisodeIntent.ShiftDanmakuSource(origin.serviceId, null)) },
                    modifier = Modifier.testTag("tv-danmaku-timing-${origin.serviceId.value}"),
                )
            }
        }
        item(key = "danmaku-list") {
            TvOptionRow(stringResource(Lang.subject_episode_danmaku_list_title), modifier = Modifier.tvFocusAnchor(focus, DanmakuSettingsKey.List).testTag("tv-danmaku-list-button")) {
                onOpenList()
            }
        }
        items(
            origins.filter { it.canMatch },
            key = { "match-${it.serviceId.value}" }
        ) { origin ->
            TvOptionRow(
                stringResource(Lang.subject_episode_danmaku_rematch),
                value = origin.name,
                modifier = Modifier.tvFocusAnchor(focus, DanmakuSettingsKey.Match(origin.serviceId))
                    .testTag("tv-danmaku-rematch-${origin.serviceId.value}"),
            ) { onMatch(origin) }
        }
    }
}
