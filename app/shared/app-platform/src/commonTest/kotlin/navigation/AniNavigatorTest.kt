/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.navigation

import androidx.compose.runtime.mutableStateListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [AniNavigator] 是 Navigation 3 的 back stack 操作入口. 这里覆盖出栈的边界情况,
 * 因为空栈会让 `NavDisplay` 抛异常.
 */
class AniNavigatorTest {
    private fun navigatorWith(vararg routes: NavRoutes): AniNavigator =
        AniNavigator().apply { setBackStack(mutableStateListOf(*routes)) }

    private val main = NavRoutes.Main(MainScreenPage.Exploration)

    @Test
    fun `navigate pushes onto the stack`() {
        val navigator = navigatorWith(main)
        navigator.navigateSettings(SettingsTab.PLAYER)

        assertEquals(listOf(main, NavRoutes.Settings(SettingsTab.PLAYER)), navigator.backStack)
    }

    @Test
    fun `popBackStack removes the top route`() {
        val navigator = navigatorWith(main, NavRoutes.Settings())
        navigator.popBackStack()

        assertEquals(listOf(main), navigator.backStack)
    }

    @Test
    fun `popBackStack keeps the last route`() {
        val navigator = navigatorWith(main)
        navigator.popBackStack()

        assertEquals(listOf(main), navigator.backStack)
    }

    @Test
    fun `popBackStack to route inclusive removes the route itself`() {
        val settings = NavRoutes.Settings()
        val navigator = navigatorWith(main, settings, NavRoutes.Caches)
        navigator.popBackStack(settings, inclusive = true)

        assertEquals(listOf(main), navigator.backStack)
    }

    @Test
    fun `popBackStack to route exclusive keeps the route`() {
        val settings = NavRoutes.Settings()
        val navigator = navigatorWith(main, settings, NavRoutes.Caches)
        navigator.popBackStack(settings, inclusive = false)

        assertEquals(listOf(main, settings), navigator.backStack)
    }

    @Test
    fun `popBackStack targets the nearest matching route`() {
        val episode = NavRoutes.EpisodeDetail(1, 2)
        val navigator = navigatorWith(main, episode, NavRoutes.Caches, episode, NavRoutes.Schedule)
        navigator.popBackStack(episode, inclusive = true)

        assertEquals(listOf(main, episode, NavRoutes.Caches), navigator.backStack)
    }

    @Test
    fun `popBackStack does nothing when the route is absent`() {
        val navigator = navigatorWith(main, NavRoutes.Caches)
        navigator.popBackStack(NavRoutes.Schedule, inclusive = true)

        assertEquals(listOf(main, NavRoutes.Caches), navigator.backStack)
    }

    @Test
    fun `popBackStack never empties the stack`() {
        val navigator = navigatorWith(NavRoutes.EmailLoginStart)
        navigator.popBackStack(NavRoutes.EmailLoginStart, inclusive = true)

        assertEquals(listOf(NavRoutes.EmailLoginStart), navigator.backStack)
    }

    @Test
    fun `navigateEpisodeDetails does not duplicate the same episode`() {
        val episode = NavRoutes.EpisodeDetail(1, 2)
        val navigator = navigatorWith(main, episode)
        navigator.navigateEpisodeDetails(subjectId = 1, episodeId = 2, force = true)

        assertEquals(listOf(main, episode), navigator.backStack)
    }

    @Test
    fun `navigateMain pops up to the target before pushing`() {
        val navigator = navigatorWith(NavRoutes.EmailLoginStart, NavRoutes.EmailLoginVerify, NavRoutes.BangumiAuthorize)
        navigator.navigateMain(MainScreenPage.Collection, popUpTargetInclusive = NavRoutes.EmailLoginStart)

        assertEquals(listOf(NavRoutes.Main(MainScreenPage.Collection)), navigator.backStack)
    }

    @Test
    fun `navigateMain without a pop target just pushes`() {
        val navigator = navigatorWith(NavRoutes.EmailLoginStart)
        navigator.navigateMain(MainScreenPage.Collection)

        assertEquals(listOf(NavRoutes.EmailLoginStart, NavRoutes.Main(MainScreenPage.Collection)), navigator.backStack)
    }

    @Test
    fun `popBackOrNavigateToMain pops back to the first Main`() {
        val secondMain = NavRoutes.Main(MainScreenPage.Collection)
        val navigator = navigatorWith(main, NavRoutes.Caches, secondMain, NavRoutes.Schedule)
        navigator.popBackOrNavigateToMain(MainScreenPage.CacheManagement)

        assertEquals(listOf(main), navigator.backStack)
    }

    @Test
    fun `popBackOrNavigateToMain resets the stack when there is no Main`() {
        val navigator = navigatorWith(NavRoutes.EmailLoginStart, NavRoutes.EmailLoginVerify)
        navigator.popBackOrNavigateToMain(MainScreenPage.Collection)

        assertEquals(listOf(NavRoutes.Main(MainScreenPage.Collection)), navigator.backStack)
    }

    @Test
    fun `findLast and findFirst locate routes by type`() {
        val firstEpisode = NavRoutes.EpisodeDetail(1, 2)
        val lastEpisode = NavRoutes.EpisodeDetail(3, 4)
        val navigator = navigatorWith(main, firstEpisode, NavRoutes.Caches, lastEpisode)

        assertEquals(lastEpisode, navigator.findLast<NavRoutes.EpisodeDetail>())
        assertEquals(firstEpisode, navigator.findFirst<NavRoutes.EpisodeDetail>())
        assertEquals(main, navigator.findLast<NavRoutes.Main>())
        assertNull(navigator.findLast<NavRoutes.Schedule>())
    }
}
