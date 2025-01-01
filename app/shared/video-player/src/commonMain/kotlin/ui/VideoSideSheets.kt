/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.enums.enumEntries

internal typealias PageTypeUpperBound<P> = Enum<P>

/**
 * Common side sheet implementation.
 * @param P must be parcelable on Android.
 */
@Composable
inline fun <reified P : PageTypeUpperBound<P>> VideoSideSheets(
    controller: VideoSideSheetsController<P>,
    modifier: Modifier = Modifier,
    noinline pageContent: @Composable (VideoSideSheetScope.(page: P) -> Unit),
) {
    VideoSideSheets(controller, enumEntries(), modifier, pageContent)
}

/**
 * Common side sheet implementation.
 * @param P must be parcelable on Android.
 */
@Composable
fun <P : PageTypeUpperBound<P>> VideoSideSheets(
    controller: VideoSideSheetsController<P>,
    pages: List<P>,
    modifier: Modifier = Modifier,
    pageContent: @Composable (VideoSideSheetScope.(page: P) -> Unit),
) {
    val navController = controller.navController
    NavHost(
        navController,
        startDestination = ROUTE_NONE,
        modifier,
        enterTransition = { fadeIn(snap()) },
        exitTransition = { fadeOut(snap()) },
        popEnterTransition = { fadeIn(snap()) },
        popExitTransition = { fadeOut(snap()) },
    ) {
        composable(ROUTE_NONE) {
            // Nothing here
        }
        composable(
            ROUTE_PAGE + "?${ROUTE_ARG_PAGE}={${ROUTE_ARG_PAGE}}",
            arguments = listOf(navArgument(ROUTE_ARG_PAGE) { type = NavType.StringType }),
        ) { backStackEntry ->
            backStackEntry.arguments?.getString(ROUTE_ARG_PAGE)
                ?.let { pages.firstOrNull { p -> p.name == it } }
                ?.let { page ->
                    val scope = remember(navController, backStackEntry) {
                        VideoSideSheetScopeImpl(navController, backStackEntry)
                    }
                    pageContent(scope, page)
                }
        }
    }
}

@Stable
sealed class VideoSideSheetsController<P : PageTypeUpperBound<P>> {
    @PublishedApi
    internal abstract val navController: NavHostController

    /**
     * Whether a sheet is displaying.
     */
    val hasPageFlow: Flow<Boolean>
        get() = navController.currentBackStackEntryFlow.map {
            !it.destination.hasRoute(ROUTE_NONE, null)
        }

    fun navigateTo(route: P) {
        navController.navigate(ROUTE_PAGE + "?${ROUTE_ARG_PAGE}=${route.name}")
    }
}

/**
 * Whether a sheet is displaying.
 */
@Composable
fun <P : PageTypeUpperBound<P>> VideoSideSheetsController<P>.hasPageAsState(): State<Boolean> {
    return hasPageFlow.collectAsState(initial = false)
}

@Composable
fun <P : PageTypeUpperBound<P>> rememberVideoSideSheetsController(): VideoSideSheetsController<P> {
    val navController = rememberNavController()
    return remember(navController) {
        VideoSideSheetsControllerImpl(navController)
    }
}

internal const val ROUTE_NONE = "/ROUTE_NONE"
internal const val ROUTE_PAGE = "/ROUTE_PAGE"
internal const val ROUTE_ARG_PAGE = "content"

@Stable
sealed interface VideoSideSheetScope {
    /**
     * Pops up the current back stack entry.
     */
    fun goBack()

    /**
     * Clears all back stack entries and effectively closes the side sheet.
     */
    fun closeSideSheet()
}


private class VideoSideSheetsControllerImpl<P : PageTypeUpperBound<P>>(override val navController: NavHostController) :
    VideoSideSheetsController<P>()

@PublishedApi
internal class VideoSideSheetScopeImpl(
    private val controller: NavController,
    private val backStackEntry: NavBackStackEntry,
) : VideoSideSheetScope {
    override fun goBack() {
        backStackEntry.destination.route?.let { controller.popBackStack(it, inclusive = true) }
    }

    override fun closeSideSheet() {
        controller.currentBackStack.value.firstOrNull()?.let {
            controller.popBackStack(it, inclusive = false)
        }
    }
}
