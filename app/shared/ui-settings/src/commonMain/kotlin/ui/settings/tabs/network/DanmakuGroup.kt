/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.network

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import me.him188.ani.app.data.models.preference.DanmakuSettings
import me.him188.ani.app.data.network.danmaku.AniBangumiSeverBaseUrls
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_network_danmaku
import me.him188.ani.app.ui.lang.settings_network_danmaku_connection_test
import me.him188.ani.app.ui.lang.settings_network_danmaku_currently_using
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
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextButtonItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScope.DanmakuGroup(
    danmakuSettingsState: SettingsState<DanmakuSettings>,
    danmakuServerTesters: ConnectionTesterRunner<ConnectionTester>,
) {
    Group(
        title = { Text(stringResource(Lang.settings_network_danmaku)) },
    ) {
        val danmakuSettings by danmakuSettingsState
        SwitchItem(
            checked = danmakuSettings.useGlobal,
            onCheckedChange = { danmakuSettingsState.update(danmakuSettings.copy(useGlobal = it)) },
            title = { Text(stringResource(Lang.settings_network_danmaku_global_acceleration)) },
            description = { Text(stringResource(Lang.settings_network_danmaku_global_acceleration_description)) },
        )

        SubGroup {
            Group(
                title = { Text(stringResource(Lang.settings_network_danmaku_connection_test)) },
                useThinHeader = true,
            ) {
                for (tester in danmakuServerTesters.testers) {
                    val currentlySelected by derivedStateOf {
                        danmakuSettings.useGlobal == (tester.id == AniBangumiSeverBaseUrls.GLOBAL)
                    }
                    TextItem(
                        description = when {
                            currentlySelected -> {
                                { Text(stringResource(Lang.settings_network_danmaku_currently_using)) }
                            }

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
                            val textColor =
                                if (currentlySelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Unspecified
                                }
                            if (tester.id == AniBangumiSeverBaseUrls.GLOBAL) {
                                Text(stringResource(Lang.settings_network_danmaku_global), color = textColor)
                            } else {
                                Text(stringResource(Lang.settings_network_danmaku_mainland), color = textColor)
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
}
