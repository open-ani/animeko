/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview

@Preview
@Composable
fun PreviewWizardNavHost() {
    ProvideFoundationCompositionLocalsForPreview {
        WizardNavHost(rememberWizardController {}) {
            step(
                key = "theme",
                title = { Text("选择主题") },
                defaultConfig = MyTheme("default", 0),
            ) {
                Text("my theme: ${data.theme} ${data.counter}")
                Button(
                    {
                        update { copy(counter = counter + 1) }
                    },
                ) { Text("Update theme") }
            }

            step(
                key = "proxy",
                title = { Text("设置代理") },
                defaultConfig = MyProxy("proxy default", 0),
                canForward = { it.counter >= 8 },
            ) {
                Text("my theme: ${data.proxy} ${data.counter}")
                Text("continue unless counter >= 8")
                Button(
                    {
                        update { copy(counter = counter + 1) }
                    },
                ) { Text("Update proxy") }
            }

            step(
                key = "bittorrent",
                title = { Text("BitTorrent 功能") },
                defaultConfig = MyBitTorrent("bittorrent default", 0),
                skippable = true,
            ) {
                Text("my bit torrent: ${data.proxy} ${data.counter}")
                Button(
                    {
                        update { copy(counter = counter + 1) }
                    },
                ) { Text("Update bittorrent") }

                if (requestedSkip) {
                    BasicAlertDialog(
                        onDismissRequest = { confirmSkip(false) },
                    ) {
                        Surface {
                            Column {
                                Text("Are you sure to skip this step?")
                                Row {
                                    Button(
                                        {
                                            confirmSkip(false)
                                        },
                                    ) { Text("No") }
                                    Button(
                                        {
                                            confirmSkip(true)
                                        },
                                    ) { Text("Skip") }
                                }
                            }
                        }
                    }
                }
            }

            step(
                key = "finish",
                title = { Text("完成") },
                defaultConfig = Unit,
            ) {
                Text("Finish")
            }
        }
    }
}

private data class MyTheme(val theme: String, val counter: Int)
private data class MyProxy(val proxy: String, val counter: Int)
private data class MyBitTorrent(val proxy: String, val counter: Int)