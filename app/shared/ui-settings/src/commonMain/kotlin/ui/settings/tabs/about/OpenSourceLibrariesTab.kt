/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries

/**
 * 开源许可页, 展示应用使用的开源库列表及许可证.
 *
 * @param loadLibrariesJson 读取 AboutLibraries gradle 插件导出的 `aboutlibraries.json`.
 * 数据在 `:app:shared` 的 compose resources 里, 由调用方传入以避免依赖上层模块的 Res.
 */
@Composable
fun OpenSourceLibrariesTab(
    loadLibrariesJson: suspend () -> ByteArray,
    modifier: Modifier = Modifier,
) {
    val libraries by produceLibraries { loadLibrariesJson().decodeToString() }
    LibrariesContainer(libraries, modifier)
}
