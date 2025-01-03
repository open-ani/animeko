/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import me.him188.ani.app.data.network.AniBangumiSeverBaseUrls
import me.him188.ani.app.ui.settings.framework.ConnectionTester
import me.him188.ani.app.ui.settings.framework.ConnectionTesterResultIndicator
import me.him188.ani.app.ui.settings.framework.ConnectionTesterRunner
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextButtonItem
import me.him188.ani.app.ui.settings.framework.components.TextItem

@Composable
internal fun SettingsScope.DanmakuGroup(
    danmakuSettingsState: SettingsState<DanmakuSettings>,
    danmakuServerTesters: ConnectionTesterRunner<ConnectionTester>,
) {
    Group(
        title = { Text("弹幕") },
    ) {
        val danmakuSettings by danmakuSettingsState
        SwitchItem(
            checked = danmakuSettings.useGlobal,
            onCheckedChange = { danmakuSettingsState.update(danmakuSettings.copy(useGlobal = it)) },
            title = { Text("全球加速") },
            description = { Text("提升在获取弹幕数据的速度\n在中国大陆内启用会减速") },
        )

        SubGroup {
            Group(
                title = { Text("连接速度测试") },
                useThinHeader = true,
            ) {
                for (tester in danmakuServerTesters.testers) {
                    val currentlySelected by derivedStateOf {
                        danmakuSettings.useGlobal == (tester.id == AniBangumiSeverBaseUrls.GLOBAL)
                    }
                    TextItem(
                        description = when {
                            currentlySelected -> {
                                { Text("当前使用") }
                            }

                            tester.id == AniBangumiSeverBaseUrls.GLOBAL -> {
                                { Text("建议在其他地区使用") }
                            }

                            else -> {
                                { Text("建议在中国大陆和香港使用") }
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
                                Text("全球", color = textColor)
                            } else {
                                Text("中国大陆", color = textColor)
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
                            Text("终止测试")
                        } else {
                            Text("开始测试")
                        }
                    },
                )
            }

        }
    }
}
