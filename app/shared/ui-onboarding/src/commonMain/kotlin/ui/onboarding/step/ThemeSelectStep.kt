/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding.step

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.isPlatformSupportDynamicTheme
import me.him188.ani.app.ui.onboarding.WizardLayoutParams
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.tabs.theme.ColorButton
import me.him188.ani.app.ui.settings.tabs.theme.DarkModeSelectPanel
import me.him188.ani.app.ui.theme.DefaultSeedColor
import me.him188.ani.app.ui.theme.themeColorOptions

@Composable
internal fun ThemeSelectStep(
    config: ThemeSelectUIState,
    onUpdateUseDarkMode: (DarkMode) -> Unit,
    onUpdateUseDynamicTheme: (Boolean) -> Unit,
    onUpdateSeedColor: (Color) -> Unit,
    modifier: Modifier = Modifier,
    layoutParams: WizardLayoutParams = WizardLayoutParams.calculate(currentWindowAdaptiveInfo1().windowSizeClass)
) {
    SettingsTab(modifier = modifier) {
        DarkModeSelectPanel(
            currentMode = config.darkMode,
            onModeSelected = onUpdateUseDarkMode,
            modifier = Modifier
                .padding(horizontal = layoutParams.horizontalPadding)
                .padding(top = layoutParams.verticalPadding),
        )
        Group(
            title = { Text("色彩") },
            useThinHeader = true,
        ) {
            if (isPlatformSupportDynamicTheme()) {
                TextItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdateUseDynamicTheme(!config.useDynamicTheme) },
                    title = { Text("动态色彩") },
                    description = { Text("使用系统强调色") },
                    action = {
                        Switch(
                            checked = config.useDynamicTheme,
                            onCheckedChange = { onUpdateUseDynamicTheme(!config.useDynamicTheme) },
                        )
                    },
                )
            }
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
                            onClick = { onUpdateSeedColor(it) },
                            baseColor = it,
                            selected = !config.useDynamicTheme && config.seedColor == it,
                            cardColor = Color.Transparent,
                        )
                    }
                }
            }
        }
    }
}

@Stable
class ThemeSelectUIState(
    val darkMode: DarkMode = DarkMode.AUTO,
    val useDynamicTheme: Boolean = false,
    val seedColor: Color = DefaultSeedColor,
) {
    companion object {
        @Stable
        val Placeholder = ThemeSelectUIState()
    }
}