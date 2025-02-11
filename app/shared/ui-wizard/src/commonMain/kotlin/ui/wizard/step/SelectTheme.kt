/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.step

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.appColorScheme
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.tabs.theme.ColorButton
import me.him188.ani.app.ui.settings.tabs.theme.DiagonalMixedThemePreviewPanel
import me.him188.ani.app.ui.settings.tabs.theme.ThemePreviewPanel
import me.him188.ani.app.ui.theme.themeColorOptions
import me.him188.ani.app.ui.wizard.WizardLayoutParams
import me.him188.ani.utils.platform.isAndroid

@Composable
private fun renderThemeModeText(mode: DarkMode): String {
    return when (mode) {
        DarkMode.LIGHT -> "亮色"
        DarkMode.DARK -> "暗色"
        DarkMode.AUTO -> "自动"
    }
}

@Composable
internal fun SelectTheme(
    config: ThemeSettings,
    onUpdate: (ThemeSettings) -> Unit,
    layoutParams: WizardLayoutParams,
    modifier: Modifier = Modifier
) {
    val platform = LocalPlatform.current

    val panelModifier = remember { Modifier.size(96.dp, 146.dp) }
    val themePanelItem: @Composable (DarkMode) -> Unit = {
        ColorSchemePreviewItem(
            onClick = { onUpdate(config.copy(darkMode = it)) },
            panel = {
                if (it != DarkMode.AUTO) ThemePreviewPanel(
                    colorScheme = appColorScheme(isDark = it == DarkMode.DARK),
                    modifier = panelModifier,
                ) else DiagonalMixedThemePreviewPanel(
                    leftTopColorScheme = appColorScheme(isDark = false),
                    rightBottomColorScheme = appColorScheme(isDark = true),
                    modifier = panelModifier,
                )
            },
            text = { Text(renderThemeModeText(it)) },
            selected = config.darkMode == it,
        )
    }
    
    SettingsTab(modifier = modifier) {
        Row(
            modifier = Modifier
                .padding(horizontal = layoutParams.horizontalPadding)
                .padding(top = 16.dp)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            themePanelItem(DarkMode.LIGHT)
            themePanelItem(DarkMode.DARK)
            themePanelItem(DarkMode.AUTO)
        }
        Group(
            title = { Text("色彩") },
            useThinHeader = true,
        ) {
            if (platform.isAndroid()) TextItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onUpdate(config.copy(useDynamicTheme = !config.useDynamicTheme))
                    },
                title = { Text("动态色彩") },
                description = { Text("使用桌面壁纸生成主题颜色") },
                action = {
                    Switch(
                        checked = config.useDynamicTheme,
                        onCheckedChange = {
                            onUpdate(config.copy(useDynamicTheme = !config.useDynamicTheme))
                        },
                    )
                },
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                FlowRow(
                    modifier = Modifier
                        .padding(horizontal = layoutParams.horizontalPadding, vertical = 16.dp)
                        .widthIn(max = 480.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AniThemeDefaults.themeColorOptions.forEach {
                        ColorButton(
                            onClick = {
                                onUpdate(
                                    config.copy(
                                        useDynamicTheme = false,
                                        seedColorValue = it.value,
                                    ),
                                )
                            },
                            baseColor = it,
                            selected = !config.useDynamicTheme && config.seedColorValue == it.value,
                            cardColor = Color.Transparent,
                        )
                    }
                }
            }
        }
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