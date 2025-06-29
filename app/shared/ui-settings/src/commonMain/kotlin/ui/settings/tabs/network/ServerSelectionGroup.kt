/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import me.him188.ani.app.data.models.preference.DanmakuSettings
import me.him188.ani.app.data.network.danmaku.AniBangumiSeverBaseUrls
import me.him188.ani.app.platform.AniServers
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_network_danmaku
import me.him188.ani.app.ui.lang.settings_network_danmaku_connection_test
import me.him188.ani.app.ui.lang.settings_network_danmaku_global
import me.him188.ani.app.ui.lang.settings_network_danmaku_global_acceleration
import me.him188.ani.app.ui.lang.settings_network_danmaku_global_acceleration_description
import me.him188.ani.app.ui.lang.settings_network_danmaku_mainland
import me.him188.ani.app.ui.lang.settings_network_danmaku_recommended_mainland_hk
import me.him188.ani.app.ui.lang.settings_network_danmaku_recommended_other_regions
import me.him188.ani.app.ui.lang.settings_network_danmaku_start_test
import me.him188.ani.app.ui.lang.settings_network_danmaku_stop_test
import me.him188.ani.app.ui.settings.framework.ConnectionTester
import me.him188.ani.app.ui.settings.framework.ConnectionTesterResultIndicator
import me.him188.ani.app.ui.settings.framework.ConnectionTesterRunner
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextButtonItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScope.ServerSelectionGroup(
    danmakuSettingsState: SettingsState<DanmakuSettings>,
    danmakuServerTesters: ConnectionTesterRunner<ConnectionTester>,
) {
    Group(
        title = { Text(stringResource(Lang.settings_network_danmaku)) },
    ) {
        val danmakuSettings by danmakuSettingsState

        // Danmaku source selection: null = automatic, true = global, false = mainland/HK
        val options = listOf(
            null to "自动",
            true to stringResource(Lang.settings_network_danmaku_global_acceleration),
            false to stringResource(Lang.settings_network_danmaku_mainland),
        )

        options.forEach { (value, label) ->
            ListItem(
                headlineContent = { Text(label) },
                supportingContent = {
                    Text(
                        when (value) {
                            null -> {
                                val autoSelected = if (AniServers.shouldUseGlobalServer()) {
                                    stringResource(Lang.settings_network_danmaku_global_acceleration)
                                } else {
                                    stringResource(Lang.settings_network_danmaku_mainland)
                                }
                                "自动选择：$autoSelected"
                            }

                            true -> stringResource(Lang.settings_network_danmaku_global_acceleration_description)
                            false -> ""
                        },
                    )
                },
                trailingContent = {
                    RadioButton(
                        selected = danmakuSettings.useGlobal == value,
                        onClick = { danmakuSettingsState.update(danmakuSettings.copy(useGlobal = value)) },
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { danmakuSettingsState.update(danmakuSettings.copy(useGlobal = value)) },
            )
        }

        Group(
            title = { Text(stringResource(Lang.settings_network_danmaku_connection_test)) },
            useThinHeader = true,
        ) {
            for (tester in danmakuServerTesters.testers) {
                TextItem(
                    description = when {
                        tester.id == AniBangumiSeverBaseUrls.GLOBAL -> {
                            { Text(stringResource(Lang.settings_network_danmaku_recommended_other_regions)) }
                        }

                        else -> {
                            { Text(stringResource(Lang.settings_network_danmaku_recommended_mainland_hk)) }
                        }
                    },
                    icon = {
                        if (tester.id == AniBangumiSeverBaseUrls.GLOBAL)
                            Icon(
                                Icons.Rounded.Public, null,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        else Text("CN", fontFamily = FontFamily.Monospace)

                    },
                    action = {
                        ConnectionTesterResultIndicator(
                            tester,
                            showTime = true,
                        )
                    },
                    title = {
                        if (tester.id == AniBangumiSeverBaseUrls.GLOBAL) {
                            Text(stringResource(Lang.settings_network_danmaku_global))
                        } else {
                            Text(stringResource(Lang.settings_network_danmaku_mainland))
                        }
                    },
                )
            }

            TextButtonItem(
                onClick = {
                    danmakuServerTesters.toggleTest()
                },
                title = {
                    if (danmakuServerTesters.anyTesting) {
                        Text(stringResource(Lang.settings_network_danmaku_stop_test))
                    } else {
                        Text(stringResource(Lang.settings_network_danmaku_start_test))
                    }
                },
            )
        }
    }
}
