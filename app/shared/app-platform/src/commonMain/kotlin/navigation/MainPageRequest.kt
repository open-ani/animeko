/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.navigation

import androidx.navigation.NavHostController

/**
 * [popBackOrNavigateToMain] 弹回已有主页时, 经 Main 路由的 SavedStateHandle 传递要切换到的
 * tab ([MainScreenPage.name]): 弹回不会重建 Main, 路由参数 `initialPage` 不会重新生效,
 * 主页需自行观察此 key 并切页 (见 AniAppContent 的 Main composable).
 */
const val MAIN_REQUESTED_PAGE_KEY = "ani.main.requestedPage"

/**
 * 在返回栈里找到第一个 [NavRoutes.Main] 的 entry, 把 [MAIN_REQUESTED_PAGE_KEY] 写入其
 * SavedStateHandle (供 [popBackOrNavigateToMain] 的各平台实现在弹回前调用). 无 Main 时返回 false.
 */
fun NavHostController.requestMainPage(page: MainScreenPage): Boolean {
    val routeFQN = NavRoutes.Main::class.qualifiedName ?: return false
    // 精确到类名边界 (route 为 FQN 或 "FQN?args"/"FQN/args"), 不用 contains: 防前缀同名路由误匹配
    val entry = currentBackStack.value
        .firstOrNull { e ->
            val route = e.destination.route ?: return@firstOrNull false
            route == routeFQN || route.startsWith("$routeFQN?") || route.startsWith("$routeFQN/")
        }
        ?: return false
    entry.savedStateHandle[MAIN_REQUESTED_PAGE_KEY] = page.name
    return true
}
