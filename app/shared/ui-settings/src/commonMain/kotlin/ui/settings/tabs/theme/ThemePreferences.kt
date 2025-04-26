/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.isPlatformSupportDynamicTheme
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_theme_always_dark_episode
import me.him188.ani.app.ui.lang.settings_theme_always_dark_episode_description
import me.him188.ani.app.ui.lang.settings_theme_dynamic_colors
import me.him188.ani.app.ui.lang.settings_theme_dynamic_colors_description
import me.him188.ani.app.ui.lang.settings_theme_dynamic_subject
import me.him188.ani.app.ui.lang.settings_theme_dynamic_subject_description
import me.him188.ani.app.ui.lang.settings_theme_high_contrast
import me.him188.ani.app.ui.lang.settings_theme_high_contrast_description
import me.him188.ani.app.ui.lang.settings_theme_palette
import me.him188.ani.app.ui.lang.settings_theme_title
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.theme.themeColorOptions
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScope.ThemeGroup(
    state: SettingsState<ThemeSettings>,
) {
    val themeSettings by state

    Group(
        title = { Text(stringResource(Lang.settings_theme_title)) },
    ) {
        DarkModeSelectPanel(
            currentMode = themeSettings.darkMode,
            onModeSelected = { state.update(themeSettings.copy(darkMode = it)) },
            modifier = Modifier.padding(vertical = SettingsScope.itemVerticalSpacing),
        )

        if (isPlatformSupportDynamicTheme()) {
            SwitchItem(
                checked = themeSettings.useDynamicTheme,
                onCheckedChange = { checked ->
                    state.update(themeSettings.copy(useDynamicTheme = checked))
                },
                title = { Text(stringResource(Lang.settings_theme_dynamic_colors)) },
                description = { Text(stringResource(Lang.settings_theme_dynamic_colors_description)) },
            )
        }

        SwitchItem(
            checked = themeSettings.useBlackBackground,
            onCheckedChange = { checked ->
                state.update(themeSettings.copy(useBlackBackground = checked))
            },
            title = { Text(stringResource(Lang.settings_theme_high_contrast)) },
            description = { Text(stringResource(Lang.settings_theme_high_contrast_description)) },
        )

        SwitchItem(
            checked = themeSettings.alwaysDarkInEpisodePage,
            onCheckedChange = { checked ->
                state.update(themeSettings.copy(alwaysDarkInEpisodePage = checked))
            },
            title = { Text(stringResource(Lang.settings_theme_always_dark_episode)) },
            description = { Text(stringResource(Lang.settings_theme_always_dark_episode_description)) },
        )

        SwitchItem(
            checked = themeSettings.useDynamicSubjectPageTheme,
            onCheckedChange = { checked ->
                state.update(themeSettings.copy(useDynamicSubjectPageTheme = checked))
            },
            title = { Text(stringResource(Lang.settings_theme_dynamic_subject)) },
            description = { Text(stringResource(Lang.settings_theme_dynamic_subject_description)) },
        )
    }

    Box(
        modifier = Modifier.alpha(if (themeSettings.useDynamicTheme) 0.5f else 1f),
    ) {
        Group(title = { Text(stringResource(Lang.settings_theme_palette)) }) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                AniThemeDefaults.themeColorOptions.forEach { color ->
                    ColorButton(
                        color = color,
                        themeSettings = themeSettings,
                        state = state,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorButton(
    color: Color,
    themeSettings: ThemeSettings,
    state: SettingsState<ThemeSettings>,
    modifier: Modifier = Modifier,
) {
    ColorButton(
        modifier = modifier,
        selected = color.value == themeSettings.seedColorValue && !themeSettings.useDynamicTheme,
        onClick = {
            state.update(
                themeSettings.copy(
                    seedColorValue = color.value,
                    useDynamicTheme = false,
                ),
            )

        },
        baseColor = color,
    )
}
