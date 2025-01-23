/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.step

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.appColorScheme
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.theme.ColorButton
import me.him188.ani.app.ui.theme.DiagonalMixedThemePreviewPanel
import me.him188.ani.app.ui.theme.ThemePreviewPanel
import me.him188.ani.app.ui.theme.colorList
import me.him188.ani.app.ui.wizard.WizardLayoutParams


@Composable
internal fun SelectTheme(
    config: ThemeSettings,
    onUpdate: (ThemeSettings) -> Unit,
    modifier: Modifier = Modifier,
    layoutParams: WizardLayoutParams = WizardLayoutParams.Default
) {
    SettingsTab(modifier = modifier) {
        Row(
            modifier = Modifier
                .padding(horizontal = layoutParams.horizontalPadding)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            ColorSchemePreviewItem(
                onClick = { onUpdate(config.copy(darkMode = DarkMode.AUTO)) },
                text = { Text("自动") },
                panel = {
                    DiagonalMixedThemePreviewPanel(
                        leftTopColorScheme = appColorScheme(isDark = false),
                        rightBottomColorScheme = appColorScheme(isDark = true),
                        modifier = Modifier.size(96.dp, 146.dp),
                    )
                },
                selected = config.darkMode == DarkMode.AUTO,
            )
            ColorSchemePreviewItem(
                onClick = { onUpdate(config.copy(darkMode = DarkMode.LIGHT)) },
                panel = {
                    ThemePreviewPanel(
                        colorScheme = appColorScheme(isDark = false),
                        modifier = Modifier.size(96.dp, 146.dp),
                    )
                },
                text = { Text("亮色") },
                selected = config.darkMode == DarkMode.LIGHT,
            )
            ColorSchemePreviewItem(
                onClick = { onUpdate(config.copy(darkMode = DarkMode.DARK)) },
                panel = {
                    ThemePreviewPanel(
                        colorScheme = appColorScheme(isDark = true),
                        modifier = Modifier.size(96.dp, 146.dp),
                    )
                },
                text = { Text("暗色") },
                selected = config.darkMode == DarkMode.DARK,
            )
        }
        Group(
            title = { Text("色彩") },
            useThinHeader = true,
        ) {
            TextItem(
                modifier = Modifier.fillMaxWidth(),
                title = { Text("动态色彩") },
                description = { Text("使用桌面壁纸动态生成主题颜色") },
                action = {
                    Switch(
                        checked = config.useDynamicTheme,
                        onCheckedChange = {
                            onUpdate(config.copy(useDynamicTheme = !config.useDynamicTheme))
                        },
                    )
                },
            )
            FlowRow(
                modifier = Modifier
                    .padding(horizontal = layoutParams.horizontalPadding, vertical = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                AniThemeDefaults.colorList.forEach {
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
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.Start,
    ) {
        panel()
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                interactionSource = interactionSource,
            )
            ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
        }
    }
}