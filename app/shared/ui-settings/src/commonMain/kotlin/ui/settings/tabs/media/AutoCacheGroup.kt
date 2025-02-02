/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.RowButtonItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SliderItem
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import kotlin.math.roundToInt

@Composable
internal fun SettingsScope.AutoCacheGroup(
    mediaCacheSettingsState: SettingsState<MediaCacheSettings>,
) {
    Group(
        title = { Text("自动缓存") },
        description = { Text("自动缓存 \"在看\" 分类中未观看的剧集") },
    ) {
        val mediaCacheSettings by mediaCacheSettingsState
        SwitchItem(
            checked = mediaCacheSettings.enabled,
            onCheckedChange = {
                mediaCacheSettingsState.update(mediaCacheSettings.copy(enabled = it))
            },
            title = { Text("启用自动缓存") },
        )

        AniAnimatedVisibility(mediaCacheSettings.enabled) {
            Column {
                HorizontalDividerItem()

                var maxCount by remember(mediaCacheSettings) { mutableFloatStateOf(mediaCacheSettings.maxCountPerSubject.toFloat()) }
                SliderItem(
                    title = { Text("最大自动缓存话数") },
                    description = {
                        Column {
                            Text("若手动缓存数量超过该设置值，将不会自动缓存")
                            Row {
                                Text(remember(maxCount) { autoCacheDescription(maxCount) })
                                if (maxCount == 10f) {
                                    Text("可能会占用大量空间", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    },
                ) {
                    Slider(
                        value = maxCount,
                        onValueChange = { maxCount = it },
                        valueRange = 0f..10f,
                        onValueChangeFinished = {
                            mediaCacheSettingsState.update(mediaCacheSettings.copy(maxCountPerSubject = maxCount.roundToInt()))
                        },
                        steps = 9,
                    )
                }

                HorizontalDividerItem()

                var mostRecentOnly by remember(mediaCacheSettings) {
                    mutableStateOf(mediaCacheSettings.mostRecentOnly)
                } // for preview
                SwitchItem(
                    checked = mostRecentOnly,
                    onCheckedChange = {
                        mostRecentOnly = it
                        mediaCacheSettingsState.update(mediaCacheSettings.copy(mostRecentOnly = it))
                    },
                    title = { Text("仅缓存最近看过的番剧") },
                )

                AniAnimatedVisibility(mostRecentOnly) {
                    SubGroup {
                        var mostRecentCount by remember(mediaCacheSettings) { mutableFloatStateOf(mediaCacheSettings.mostRecentCount.toFloat()) }
                        SliderItem(
                            title = { Text("缓存数量") },
                            description = {
                                Text("当前设置: 仅缓存最近看过的 ${mostRecentCount.roundToInt()} 部番剧")
                            },
                        ) {
                            Slider(
                                value = mostRecentCount,
                                onValueChange = { mostRecentCount = it },
                                onValueChangeFinished = {
                                    mediaCacheSettingsState.update(mediaCacheSettings.copy(mostRecentCount = mostRecentCount.roundToInt()))
                                },
                                valueRange = 0f..30f,
                                steps = 30 - 1,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDividerItem()

        val navigator = LocalNavigator.current
        RowButtonItem(
            onClick = { navigator.navigateCaches() },
            icon = { Icon(Icons.Rounded.ArrowOutward, null) },
        ) { Text("管理已缓存的剧集") }
    }
}

