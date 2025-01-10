/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HdrAuto
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.toHct
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.DropdownItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.utils.platform.isAndroid
import me.him188.ani.utils.platform.isDesktop
import me.him188.ani.utils.platform.isMobile

private val colorList =
    ((4..10) + (1..3))
        .map { it * 35.0 }
        .map { Color(Hct.from(it, 40.0, 40.0).toInt()) }

@Composable
fun SettingsScope.ThemeGroup(
    state: SettingsState<ThemeSettings>,
) {
    val themeSettings by state

    Group(title = { Text("主题") }) {
        // TODO: DarkThemePreference.kt
        //  Use TextButton with Icon. And only show if build sdk_int >= 29
        AnimatedVisibility(
            LocalPlatform.current.isDesktop() || LocalPlatform.current.isAndroid(),
        ) {
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

//        SwitchItem(
//            checked = themeSettings.isAmoled,
//            onCheckedChange = { checked ->
//                state.update(themeSettings.copy(isAmoled = checked))
//            },
//            title = { Text("AMOLED") },
//            description = { Text("在深色模式下使用纯黑背景") },
//        )
    }

    Group(title = { Text("调色板") }) {
        if (LocalPlatform.current.isMobile()) {
            val colors = colorList.chunked(4)
            val pageCount = colors.size

            val pagerState = rememberPagerState(
                initialPage = colors.indexOfFirst { chunk ->
                    chunk.any { it.toArgb() == themeSettings.seedColor }
                }.let { if (it == -1) 0 else it },
            ) {
                pageCount
            }

            HorizontalPager(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { page ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    colors[page].forEach { color ->
                        ColorButton(
                            color = color,
                            themeSettings = themeSettings,
                            state = state,
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .clearAndSetSemantics {},
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pageCount) { iteration ->
                    val color = if (pagerState.currentPage == iteration)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                // maxItemsInEachRow = 4
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
    color: Color,
    themeSettings: ThemeSettings,
    state: SettingsState<ThemeSettings>
) {
    ColorButton(
        modifier = Modifier,
        selected = color.toArgb() == themeSettings.seedColor && !themeSettings.useDynamicTheme,
        onClick = {
            state.update(
                themeSettings.copy(
                    seedColor = color.toArgb(),
                    useDynamicTheme = false,
                ),
            )
        },
        baseColor = color,
    )
}

@Composable
fun ColorButton(
    onClick: () -> Unit,
    baseColor: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    cardColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val containerSize by animateDpAsState(targetValue = if (selected) 28.dp else 0.dp)
    val iconSize by animateDpAsState(targetValue = if (selected) 16.dp else 0.dp)

    Surface(
        modifier = modifier
            .padding(4.dp)
            .sizeIn(maxHeight = 80.dp, maxWidth = 80.dp, minHeight = 64.dp, minWidth = 64.dp)
            .aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize()) {
            val hct = baseColor.toHct()
            val color1 = Color(Hct.from(hct.hue, 40.0, 80.0).toInt())
            val color2 = Color(Hct.from(hct.hue, 40.0, 90.0).toInt())
            val color3 = Color(Hct.from(hct.hue, 40.0, 60.0).toInt())

            Box(
                modifier = modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .drawBehind { drawCircle(color1) }
                    .align(Alignment.Center),
            ) {
                Surface(
                    color = color2,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(24.dp),
                ) {}
                Surface(
                    color = color3,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp),
                ) {}
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .size(containerSize)
                        .drawBehind { drawCircle(containerColor) },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
