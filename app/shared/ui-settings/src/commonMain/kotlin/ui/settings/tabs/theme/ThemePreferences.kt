/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HdrAuto
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.settings.components.ColorButton
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.DropdownItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.utils.platform.isAndroid
import me.him188.ani.utils.platform.isDesktop

private val colorList =
    listOf(Color.Unspecified) + ((4..10) + (1..3)).map { it * 35.0 }.map { Color(Hct.from(it, 40.0, 40.0).toInt()) }

@Composable
fun SettingsScope.ThemeGroup(
    state: SettingsState<ThemeSettings>,
) {
    val themeSettings by state

    Group(
        title = { Text("主题") },
        modifier = Modifier.animateContentSize(),
    ) {
        if (LocalPlatform.current.isDesktop() || LocalPlatform.current.isAndroid()) {
            DropdownItem(
                selected = { themeSettings.darkMode },
                values = { DarkMode.entries },
                itemText = {
                    when (it) {
                        DarkMode.AUTO -> Text("自动")
                        DarkMode.LIGHT -> Text("浅色")
                        DarkMode.DARK -> Text("深色")
                    }
                },
                onSelect = {
                    state.update(themeSettings.copy(darkMode = it))
                },
                itemIcon = {
                    when (it) {
                        DarkMode.AUTO -> Icon(Icons.Rounded.HdrAuto, null)
                        DarkMode.LIGHT -> Icon(Icons.Rounded.LightMode, null)
                        DarkMode.DARK -> Icon(Icons.Rounded.DarkMode, null)
                    }
                },
                description = {
                    when (themeSettings.darkMode) {
                        DarkMode.AUTO -> Text("根据系统设置自动切换")
                        else -> {}
                    }
                },
                title = { Text("深色模式") },
            )
        }

//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(8.dp),
//            horizontalArrangement = Arrangement.Center,
//        ) {
//            var selectedIndex by remember(themeSettings.darkMode) {
//                mutableIntStateOf(themeSettings.darkMode.ordinal)
//            }
//            val options = DarkMode.entries.toList()
//
//            SingleChoiceSegmentedButtonRow(
//                modifier = Modifier.widthIn(240.dp, 240.dp)
//            ) {
//                options.forEachIndexed { index, mode ->
//                    SegmentedButton(
//                        selected = index == selectedIndex,
//                        onClick = {
//                            selectedIndex = index
//                            state.update(themeSettings.copy(darkMode = options[index]))
//                        },
//                        shape = SegmentedButtonDefaults.itemShape(
//                            index = index,
//                            count = options.size,
//                        ),
//                        label = {
//                            when (mode) {
//                                DarkMode.AUTO -> Text("系统")
//                                DarkMode.LIGHT -> Text("浅色")
//                                DarkMode.DARK -> Text("深色")
//                            }
//                        },
//                    )
//                }
//            }
//        }

        if (LocalPlatform.current.isAndroid()) {
            SwitchItem(
                checked = themeSettings.useDynamicTheme,
                onCheckedChange = { checked ->
                    state.update(themeSettings.copy(useDynamicTheme = checked))
                },
                title = { Text("动态色彩") },
                description = { Text("将壁纸主题色应用于应用主题") },
            )
        }

        SwitchItem(
            checked = themeSettings.useBlackBackground,
            onCheckedChange = { checked ->
                state.update(themeSettings.copy(useBlackBackground = checked))
            },
            title = { Text("高对比度深色主题") },
            description = { Text("深色模式使用纯黑背景，在 AMOLED 屏幕使用纯黑背景可以省电") },
        )
    }

    Box(
        modifier = Modifier.alpha(if (themeSettings.useDynamicTheme) 0.5f else 1f),
    ) {
        Group(title = { Text("调色板") }) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                colorList.forEach { color ->
                    ColorButton(
                        color = color,
                        themeSettings = themeSettings,
                        state = state,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorButton(
    color: Color, themeSettings: ThemeSettings, state: SettingsState<ThemeSettings>
) {
    ColorButton(
        modifier = Modifier,
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
