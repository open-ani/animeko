/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import me.him188.ani.datasources.api.source.FactoryId

/**
 * Supports navigation to any page in the app.
 *
 * 应当总是使用 [AniNavigator], 而不要访问 [navigator].
 *
 * @see LocalNavigator
 */
interface AniNavigator {
    fun setNavController(
        controller: NavHostController,
    )

    fun isNavControllerReady(): Boolean

    suspend fun awaitNavController(): NavHostController

    val navigator: NavHostController

    fun popBackStack() {
        navigator.popBackStack()
    }

    fun popBackStack(route: NavRoutes, inclusive: Boolean, saveState: Boolean = false) {
        navigator.popBackStack(route, inclusive, saveState)
    }

//    fun popBackStack(
//        route: String,
//        inclusive: Boolean,
//    ) {
//        navigator.popBackStackIfExist(route, inclusive = true)
//    }

    fun popUntilNotWelcome() {
        navigator.popBackStack(NavRoutes.Welcome, inclusive = true)
    }

    fun popUntilNotAuth() {
        navigator.popBackStack(NavRoutes.BangumiTokenAuth, inclusive = true)
        navigator.popBackStack(NavRoutes.BangumiOAuth, inclusive = true)
    }

    fun navigateSubjectDetails(
        subjectId: Int,
        placeholder: SubjectDetailPlaceholder?,
    ) {
        navigator.navigate(
            NavRoutes.SubjectDetail(subjectId, placeholder),
        )
    }

    fun navigateSubjectCaches(subjectId: Int) {
        navigator.navigate(NavRoutes.SubjectCaches(subjectId))
    }

    fun navigateEpisodeDetails(subjectId: Int, episodeId: Int, fullscreen: Boolean = false) {
        navigator.popBackStack(NavRoutes.EpisodeDetail(subjectId, episodeId), inclusive = true)
        navigator.navigate(NavRoutes.EpisodeDetail(subjectId, episodeId))
    }

    fun navigateWelcome() {
        navigator.navigate(NavRoutes.Welcome)
    }

    fun navigateMain(
        page: MainScreenPage,
        requestFocus: Boolean = false
    ) {
        navigator.popBackStack<NavRoutes.Main>(inclusive = false)
    }

    /**
     * 登录页面
     */
    fun navigateBangumiOAuthOrTokenAuth() {
        navigator.navigate(NavRoutes.BangumiOAuth) {
            launchSingleTop = true
        }
    }

    fun navigateBangumiTokenAuth() {
        navigator.navigate(
            NavRoutes.BangumiTokenAuth,
        ) {
            launchSingleTop = true
            popUpTo(NavRoutes.BangumiOAuth) {
                inclusive = true
            }
        }
    }

    fun navigateSettings(tab: SettingsTab? = null) {
        navigator.navigate(NavRoutes.Settings(tab))
    }

    fun navigateEditMediaSource(
        factoryId: FactoryId,
        mediaSourceInstanceId: String,
    ) {
        navigator.navigate(
            NavRoutes.EditMediaSource(factoryId.value, mediaSourceInstanceId),
        )
    }

    fun navigateTorrentPeerSettings() {
        navigator.navigate(NavRoutes.TorrentPeerSettings)
    }

    fun navigateCaches() {
        navigator.navigate(NavRoutes.Caches)
    }

    fun navigateCacheDetails(cacheId: String) {
        navigator.navigate(NavRoutes.CacheDetail(cacheId))
    }

    fun navigateSchedule() {
        navigator.navigate(NavRoutes.Schedule)
    }
}

fun AniNavigator(): AniNavigator = AniNavigatorImpl()

private class AniNavigatorImpl : AniNavigator {
    private val _navigator: MutableSharedFlow<NavHostController> =
        MutableSharedFlow(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val navigator: NavHostController
        get() = _navigator.replayCache.firstOrNull() ?: error("Navigator is not yet set")

    override fun setNavController(controller: NavHostController) {
        this._navigator.tryEmit(controller)
    }

    override fun isNavControllerReady(): Boolean = _navigator.replayCache.isNotEmpty()

    override suspend fun awaitNavController(): NavHostController {
        return _navigator.first()
    }
}

/**
 * It is always provided.
 */
val LocalNavigator = compositionLocalOf<AniNavigator> {
    error("Navigator not found")
}

@Composable
inline fun OverrideNavigation(
    noinline newNavigator: @DisallowComposableCalls (AniNavigator) -> AniNavigator,
    crossinline content: @Composable () -> Unit
) {
    val current by rememberUpdatedState(LocalNavigator.current)
    val newNavigatorUpdated by rememberUpdatedState(newNavigator)
    val new by remember {
        derivedStateOf {
            newNavigatorUpdated(current)
        }
    }
    CompositionLocalProvider(LocalNavigator provides new) {
        content()
    }
}
