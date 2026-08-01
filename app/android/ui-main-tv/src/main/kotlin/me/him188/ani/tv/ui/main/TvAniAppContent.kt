/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Surface
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.NavRoutes
import kotlin.reflect.typeOf

/**
 * TV 端根内容: 注册 TV 支持的 [NavRoutes] 子集 (atv-architecture.md §6.3).
 *
 * M0 仅注册 [NavRoutes.Main] 空壳; M1 起补充 SubjectDetail/EpisodeDetail 等.
 * `Caches`/`BangumiAuthorize` 等按 §1.2 裁剪永不注册.
 */
@Composable
fun TvAniAppContent(
    aniNavigator: AniNavigator,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    aniNavigator.setNavController(navController)

    CompositionLocalProvider(LocalNavigator provides aniNavigator) {
        // tv MaterialTheme 不绘制窗口背景, 根部铺一层 Surface (深色 surface + content color)
        Surface(modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = NavRoutes.Main(MainScreenPage.Exploration),
            ) {
                composable<NavRoutes.Main>(
                    typeMap = mapOf(typeOf<MainScreenPage>() to MainScreenPage.NavType),
                ) {
                    TvMainShell()
                }
            }
        }
    }
}
