/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.foundation.theme.appColorScheme
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_theme_mode_auto
import me.him188.ani.app.ui.lang.settings_theme_mode_dark
import me.him188.ani.app.ui.lang.settings_theme_mode_light
import org.jetbrains.compose.resources.stringResource

@Composable
fun DarkModeSelectPanel(
    currentMode: DarkMode,
    onModeSelected: (DarkMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelModifier = Modifier.size(96.dp, 146.dp)

    val themePanelItem: @Composable (DarkMode) -> Unit = {
        ColorSchemePreviewItem(
            onClick = { onModeSelected(it) },
            panel = {
                if (it != DarkMode.AUTO) {
                    ThemePreviewPanel(
                        colorScheme = appColorScheme(isDark = it == DarkMode.DARK),
                        modifier = panelModifier,
                    )
                } else {
                    DiagonalMixedThemePreviewPanel(
                        leftTopColorScheme = appColorScheme(isDark = false),
                        rightBottomColorScheme = appColorScheme(isDark = true),
                        modifier = panelModifier,
                    )
                }
            },
            text = { Text(renderThemeModeText(it)) },
            selected = currentMode == it,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        themePanelItem(DarkMode.LIGHT)
        themePanelItem(DarkMode.DARK)
        themePanelItem(DarkMode.AUTO)
    }
}

@Composable
private fun ColorSchemePreviewItem(
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    panel: @Composable () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.Start,
    ) {
        panel()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                RadioButton(
                    selected = selected,
                    interactionSource = interactionSource,
                    onClick = null,
                )
            }
            ProvideContentColor(MaterialTheme.colorScheme.onSurface) {
                text()
            }
        }
    }
}

@Composable
private fun renderThemeModeText(mode: DarkMode): String {
    return when (mode) {
        DarkMode.LIGHT -> stringResource(Lang.settings_theme_mode_light)
        DarkMode.DARK -> stringResource(Lang.settings_theme_mode_dark)
        DarkMode.AUTO -> stringResource(Lang.settings_theme_mode_auto)
    }
}
