/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.fastAll
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.ui.media.renderResolution
import me.him188.ani.app.ui.media.renderSubtitleLanguage
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.DropdownItem
import me.him188.ani.app.ui.settings.framework.components.SelectableItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SorterItem
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextFieldItem
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcons
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage

@Stable
class MediaSelectionGroupState(
    val defaultMediaPreferenceState: SettingsState<MediaPreference>,
    val mediaSelectorSettingsState: SettingsState<MediaSelectorSettings>,
) {
    val defaultMediaPreference by defaultMediaPreferenceState

    private val allSubtitleLanguageIds = SubtitleLanguage.matchableEntries.map { it.id }
    val sortedLanguages by derivedStateOf {
        defaultMediaPreference.fallbackSubtitleLanguageIds.extendTo(allSubtitleLanguageIds)
    }

    private val allResolutionIds = Resolution.entries.map { it.id }
    val sortedResolutions by derivedStateOf {
        defaultMediaPreference.fallbackResolutions.extendTo(allResolutionIds)
    }

    /**
     * 将 [this] 扩展到 [all]，并保持顺序.
     */
    private fun List<String>?.extendTo(
        all: List<String>
    ): List<SelectableItem<String>> {
        val fallback = this ?: return all.map { SelectableItem(it, selected = true) }

        return fallback.map {
            SelectableItem(it, selected = true)
        } + (all - fallback.toSet()).map {
            SelectableItem(it, selected = false)
        }
    }
}

@Composable
internal fun SettingsScope.MediaSelectionGroup(
    state: MediaSelectionGroupState
) {
    Group(
        title = {
            Text("资源选择偏好")
        },
        description = {
            Column {
                Text("设置默认的资源选择偏好。将同时影响在线播放和缓存")
                Text("每部番剧在播放时的选择将覆盖这里的设置")
            }
        },
    ) {
        val textAny = "任意"
        val textNone = "无"

        SorterItem(
            values = { state.sortedLanguages },
            onSort = { list ->
                state.defaultMediaPreferenceState.update(
                    state.defaultMediaPreference.copy(
                        fallbackSubtitleLanguageIds = list.filter { it.selected }
                            .map { it.item },
                    ),
                )
            },
            exposed = { list ->
                Text(
                    remember(list) {
                        if (list.fastAll { it.selected }) {
                            textAny
                        } else if (list.fastAll { !it.selected }) {
                            textNone
                        } else
                            list.asSequence().filter { it.selected }
                                .joinToString { renderSubtitleLanguage(it.item) }
                    },
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            item = { Text(renderSubtitleLanguage(it)) },
            key = { it },
            dialogDescription = {
                Text(
                    TIPS_LONG_CLICK_SORT,
                )
            },
            icon = { Icon(Icons.Rounded.Language, null) },
            title = { Text("字幕语言") },
        )

        HorizontalDividerItem()

        SorterItem(
            values = { state.sortedResolutions },
            onSort = { list ->
                state.defaultMediaPreferenceState.update(
                    state.defaultMediaPreference.copy(
                        fallbackResolutions = list.filter { it.selected }
                            .map { it.item },
                    ),
                )
            },
            exposed = { list ->
                Text(
                    remember(list) {
                        if (list.fastAll { it.selected }) {
                            textAny
                        } else if (list.fastAll { !it.selected }) {
                            textNone
                        } else
                            list.asSequence().filter { it.selected }
                                .joinToString { renderResolution(it.item) }
                    },
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            item = { Text(renderResolution(it)) },
            key = { it },
            dialogDescription = { Text(TIPS_LONG_CLICK_SORT) },
            icon = { Icon(Icons.Rounded.Hd, null) },
            title = { Text("分辨率") },
            description = { Text("未选择的分辨率也会显示，但不会自动选择") },
        )

        HorizontalDividerItem()

        val allianceRegexes by remember(state) {
            derivedStateOf { state.defaultMediaPreference.alliancePatterns?.joinToString() ?: "" }
        }
        TextFieldItem(
            value = allianceRegexes,
            title = { Text("字幕组") },
            description = {
                Text("支持使用正则表达式，使用逗号分隔。越靠前的表达式的优先级越高\n\n示例: 桜都, 喵萌, 北宇治\n将优先采用桜都字幕组资源，否则采用喵萌，以此类推")
            },
            icon = { Icon(Icons.Rounded.Subtitles, null) },
            placeholder = { Text(textAny) },
            onValueChangeCompleted = { new ->
                state.defaultMediaPreferenceState.update(
                    state.defaultMediaPreference.copy(
                        alliancePatterns = new.split(",", "，").map { it.trim() },
                    ),
                )
            },
            sanitizeValue = { it.replace("，", ",") },
        )

        Group(
            title = { Text("高级设置") },
            useThinHeader = true,
        ) {
            val mediaSelectorSettings by state.mediaSelectorSettingsState

            kotlin.run {
                val values = remember {
                    MediaSourceKind.selectableEntries + null
                }
                DropdownItem(
                    selected = { mediaSelectorSettings.preferKind },
                    values = { values },
                    itemText = {
                        Text(
                            when (it) {
                                MediaSourceKind.WEB -> "在线 (推荐)"
                                MediaSourceKind.BitTorrent -> "BT"
                                null -> "无偏好"
                                MediaSourceKind.LocalCache -> "" // not possible
                            },
                        )
                    },
                    onSelect = {
                        state.mediaSelectorSettingsState.update(mediaSelectorSettings.copy(preferKind = it))
                    },
                    itemIcon = {
                        it?.let {
                            Icon(MediaSourceIcons.kind(it), null)
                        }
                    },
                    title = { Text("优先选择数据源类型") },
                    description = { Text("在线数据源载入快但清晰度可能偏低，BT 数据源相反") },
                )
            }

            HorizontalDividerItem()

            SwitchItem(
                checked = mediaSelectorSettings.showDisabled,
                onCheckedChange = {
                    state.mediaSelectorSettingsState.update(
                        mediaSelectorSettings.copy(showDisabled = it),
                    )
                },
                title = { Text("显示禁用的数据源") },
                description = { Text("以便在偏好数据源中未找到资源时，可临时启用禁用的数据源") },
            )

            HorizontalDividerItem()

            SwitchItem(
                checked = !state.defaultMediaPreference.showWithoutSubtitle,
                onCheckedChange = {
                    state.defaultMediaPreferenceState.update(
                        state.defaultMediaPreference.copy(showWithoutSubtitle = !it),
                    )
                },
                title = { Text("隐藏无字幕资源") },
            )

            HorizontalDividerItem()

            SwitchItem(
                checked = mediaSelectorSettings.hideSingleEpisodeForCompleted,
                onCheckedChange = {
                    state.mediaSelectorSettingsState.update(
                        mediaSelectorSettings.copy(hideSingleEpisodeForCompleted = it),
                    )
                },
                title = { Text("完结一年后隐藏单集 BT 资源") },
                description = { Text("在番剧完结一年后，单集资源通常会没有速度") },
            )

            HorizontalDividerItem()

            SwitchItem(
                checked = mediaSelectorSettings.preferSeasons,
                onCheckedChange = {
                    state.mediaSelectorSettingsState.update(
                        mediaSelectorSettings.copy(preferSeasons = it),
                    )
                },
                title = { Text("BT 资源优先选择季度全集") },
                description = { Text("季度全集资源通常更快") },
            )

            HorizontalDividerItem()

            SwitchItem(
                checked = mediaSelectorSettings.autoEnableLastSelected,
                onCheckedChange = {
                    state.mediaSelectorSettingsState.update(
                        mediaSelectorSettings.copy(autoEnableLastSelected = it),
                    )
                },
                title = { Text("自动启用上次临时启用选择的数据源") },
            )
        }
    }
}

fun autoCacheDescription(sliderValue: Float) = when (sliderValue) {
    0f -> "当前设置: 不自动缓存"
    10f -> "当前设置: 自动缓存全部未观看剧集, "
    else -> "当前设置: 自动缓存观看进度之后的 ${sliderValue.toInt()} 话, " +
            "预计占用空间 ${600.megaBytes * sliderValue}/番剧"
}

private const val TIPS_LONG_CLICK_SORT = "长按排序，优先选择顺序较高的项目。\n" +
        "选中数量越少，查询越快。"
