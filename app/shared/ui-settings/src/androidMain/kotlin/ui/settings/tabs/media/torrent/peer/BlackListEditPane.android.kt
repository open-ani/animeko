/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.torrent.peer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import kotlin.random.Random
import kotlin.random.nextInt

private fun generateRandomIp(): String {
    return sequence<Int> { Random.nextInt(0..255) }
        .take(4)
        .joinToString(".")
}

@Preview
@Composable
fun PreviewBlackListEditPane() {
    val list = remember {
        sequence<String> { generateRandomIp() }
            .take(Random.nextInt(3..30))
            .toMutableList()
    }
    BlackListEditPane(
        ipBlackList = list,
        showTitle = true,
        onAdd = { list.addAll(it) },
        onRemove = { newIp -> list.removeIf { it == newIp } },
    )
}