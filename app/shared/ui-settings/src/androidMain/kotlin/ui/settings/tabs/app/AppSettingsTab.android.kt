/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.DropdownItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import kotlin.math.roundToInt

@Composable
actual fun SettingsScope.PlayerGroupPlatform(videoScaffoldConfig: SettingsState<VideoScaffoldConfig>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val context = LocalContext.current
        val supportedModes = remember(context) {
            context.display.supportedModes.orEmpty().toList() + null
        }

        HorizontalDividerItem()
        DropdownItem(
            selected = {
                supportedModes.find { it?.modeId == videoScaffoldConfig.value.displayModeId }
            },
            values = { supportedModes },
            itemText = {
                if (it == null) {
                    Text("自动")
                } else {
                    Text(it.refreshRate.roundToInt().toString())
                }
            },
            onSelect = {
                videoScaffoldConfig.update(
                    videoScaffoldConfig.value.copy(
                        displayModeId = it?.modeId ?: 0,
                    ),
                )
            },
            title = {
                Text("弹幕刷新率")
            },
        )
    }
}
