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
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuConfigRanges
import me.him188.ani.danmaku.ui.DanmakuStyle
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
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
    val colors = LocalTvPlayerSurfaceColors.current
    TvPlayerOptionPanelLayout(TvPlayerPanel.DanmakuSettings, state.listState, modifier) {
        items(TvDanmakuProperty.entries) { property ->
            val (label, value) = when (property) {
                TvDanmakuProperty.FontSize -> "字号" to "${(config.style.fontSize.value / DanmakuStyle.Default.fontSize.value * 100).roundToInt()}%"
                TvDanmakuProperty.Opacity -> "不透明度" to "${(config.style.alpha * 100).roundToInt()}%"
                TvDanmakuProperty.Speed -> "移动速度" to "${(config.speed / DanmakuConfig.Default.speed * 100).roundToInt()}%"
                TvDanmakuProperty.Density -> "密度" to when (DanmakuConfigRanges.densityLevel(
                    config.safeSeparation, DanmakuConfigRanges.densitySeparation(desktop = false),
                ).toInt()) {
                    in 7..10 -> "密集"
                    in 4..6 -> "适中"
                    else -> "稀疏"
                }
                TvDanmakuProperty.Area -> "显示区域" to if (config.displayArea == 0f) "关闭" else "${(config.displayArea * 100).roundToInt()}%"
                TvDanmakuProperty.Stroke -> "描边" to "${(config.style.strokeWidth / DanmakuStyle.Default.strokeWidth * 100).roundToInt()}%"
                TvDanmakuProperty.Weight -> "字重" to config.style.fontWeight.weight.toString()
                TvDanmakuProperty.Top -> "顶部弹幕" to if (config.enableTop) "开启" else "关闭"
                TvDanmakuProperty.Bottom -> "底部弹幕" to if (config.enableBottom) "开启" else "关闭"
                TvDanmakuProperty.Floating -> "滚动弹幕" to if (config.enableFloating) "开启" else "关闭"
                TvDanmakuProperty.Color -> "彩色弹幕" to if (config.enableColor) "开启" else "关闭"
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
                    modifier = if (property == TvDanmakuProperty.FontSize) entryModifier else Modifier,
                )
            } else {
                TvOptionRow(
                    label,
                    checked = checked,
                    modifier = Modifier.tvStepKeys { onIntent(TvEpisodeIntent.AdjustDanmaku(property, it)) },
                ) { onIntent(TvEpisodeIntent.AdjustDanmaku(property, 1)) }
            }
        }
        item { TvPlayerSectionLabel("弹幕来源与时间校准") }
        items(origins, key = { "origin-${it.serviceId.value}" }) { origin ->
            Column {
                TvOptionRow(
                    origin.name,
                    checked = origin.enabled,
                ) { onIntent(TvEpisodeIntent.ToggleDanmakuSource(origin.serviceId)) }
                Text(
                    origin.match,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                val adjustment = TvDanmakuAdjustment.Timing(origin.serviceId)
                TvDanmakuAdjustmentRow(
                    "时间校准", "${origin.shiftMillis / 1000f}s",
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
                )
            }
        }
        item(key = "danmaku-list") {
            TvOptionRow("弹幕列表", modifier = Modifier.tvFocusAnchor(focus, DanmakuSettingsKey.List).testTag("tv-danmaku-list-button")) {
                onOpenList()
            }
        }
        items(
            origins.filter { it.canMatch },
            key = { "match-${it.serviceId.value}" }
        ) { origin ->
            TvOptionRow(
                "重新匹配弹幕",
                value = origin.name,
                modifier = Modifier.tvFocusAnchor(focus, DanmakuSettingsKey.Match(origin.serviceId)),
            ) { onMatch(origin) }
        }
    }
}
