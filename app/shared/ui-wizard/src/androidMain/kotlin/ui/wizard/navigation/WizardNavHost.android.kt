/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview

@Preview
@Composable
fun PreviewWizardNavHost() {
    ProvideFoundationCompositionLocalsForPreview {
        WizardNavHost(rememberWizardController()) {
            step(
                key = "theme",
                title = { Text("选择主题") },
            ) {
                val data = remember {
                    TestWizardData.MyTheme("theme default", 0)
                }
                Text("my theme: ${data.theme} ${data.counter}")
                Button(
                    {

                    },
                ) { Text("Update theme") }
            }

            step(
                key = "proxy",
                title = { Text("设置代理") },
            ) {
                val data = remember {
                    TestWizardData.MyProxy("proxy default", 0)
                }
                Text("my theme: ${data.proxy} ${data.counter}")
                Text("continue unless counter >= 8")
                Button(
                    {

                    },
                ) { Text("Update proxy") }
            }

            step(
                key = "bit_torrent",
                title = { Text("BitTorrent 功能") },
            ) {
                val data = remember {
                    TestWizardData.MyBitTorrent("bittorrent default", 0)
                }
                Text("my bit torrent: ${data.bittorrent} ${data.counter}")
                Button(
                    {
                        //update { copy(counter = counter + 1) }
                    },
                ) { Text("Update bittorrent") }
            }

            step(
                key = "finish",
                title = { Text("完成") },
            ) {
                Text("Finish")
            }
        }
    }
}

private sealed class TestWizardData {
    data class MyTheme(val theme: String, val counter: Int) : TestWizardData()
    data class MyProxy(val proxy: String, val counter: Int) : TestWizardData()
    data class MyBitTorrent(val bittorrent: String, val counter: Int) : TestWizardData()
    data object MyFinish : TestWizardData()
}