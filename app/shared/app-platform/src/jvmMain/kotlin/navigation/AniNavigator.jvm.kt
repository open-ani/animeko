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

actual fun NavHostController.popBackOrNavigateToMain(mainSceneInitialPage: MainScreenPage) {
    val firstMain = findFirst<NavRoutes.Main>()
    if (firstMain != null) {
        // 弹回不会重建 Main, initialPage 不会重新生效; 经 SavedStateHandle 通知主页切 tab
        requestMainPage(mainSceneInitialPage)
        popBackStack(firstMain, inclusive = false)
        return
    }

    val firstRouteId = currentBackStack.value
        // drop 第一个, 第一个不是我们的 NavRoute destination
        .drop(1).firstOrNull()?.destination?.id

    navigate(NavRoutes.Main(mainSceneInitialPage)) {
        if (firstRouteId != null) {
            popUpTo(id = firstRouteId) { inclusive = true }
        }
    }
}