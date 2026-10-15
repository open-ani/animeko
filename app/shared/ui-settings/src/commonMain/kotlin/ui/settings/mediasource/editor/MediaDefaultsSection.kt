/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_default_resolution
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_default_resolution_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_default_subtitle_language
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_default_subtitle_language_description
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import org.jetbrains.compose.resources.stringResource

/**
 * 站点不提供分辨率与字幕语言信息时, 资源使用的默认值.
 */
@Composable
fun MediaDefaultsSection(
    resolution: Resolution,
    onResolutionChange: (Resolution) -> Unit,
    subtitleLanguage: SubtitleLanguage,
    onSubtitleLanguageChange: (SubtitleLanguage) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    listItemColors: ListItemColors = MediaSourceEditorDefaults.listItemColors,
) {
    Column(modifier) {
        DropdownListItem(
            stringResource(Lang.settings_mediasource_selector_config_default_resolution),
            resolution,
            Resolution.entries.asReversed(),
            onResolutionChange,
            supporting = stringResource(Lang.settings_mediasource_selector_config_default_resolution_description),
            enabled = enabled,
            colors = listItemColors,
        ) { it.displayName }
        DropdownListItem(
            stringResource(Lang.settings_mediasource_selector_config_default_subtitle_language),
            subtitleLanguage,
            SubtitleLanguage.matchableEntries.asReversed(),
            onSubtitleLanguageChange,
            supporting = stringResource(Lang.settings_mediasource_selector_config_default_subtitle_language_description),
            enabled = enabled,
            colors = listItemColors,
        ) { it.displayName }
    }
}
