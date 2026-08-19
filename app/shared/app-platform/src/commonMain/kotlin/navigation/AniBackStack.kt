/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import kotlinx.serialization.json.Json

/**
 * 创建 APP 的导航 back stack, 栈顶为最后一个元素.
 *
 * 这个栈会在 configuration change 和进程重启后恢复. 它至少包含一个元素, 初始为 [initialRoute].
 *
 * 把它交给 `NavDisplay` 渲染, 同时用 [AniNavigator.setBackStack] 绑定到 [AniNavigator] 上进行修改.
 */
@Composable
fun rememberAniBackStack(initialRoute: NavRoutes): SnapshotStateList<NavRoutes> =
    rememberSaveable(saver = AniBackStackSaver) { mutableStateListOf(initialRoute) }

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val AniBackStackSaver: Saver<SnapshotStateList<NavRoutes>, Any> = listSaver(
    save = { stack -> stack.map { json.encodeToString(NavRoutes.serializer(), it) } },
    restore = { saved ->
        // 空栈会让 NavDisplay 抛异常, 此时放弃恢复, 回退到初始页面
        if (saved.isEmpty()) {
            null
        } else {
            saved.map { json.decodeFromString(NavRoutes.serializer(), it) }.toMutableStateList()
        }
    },
)
