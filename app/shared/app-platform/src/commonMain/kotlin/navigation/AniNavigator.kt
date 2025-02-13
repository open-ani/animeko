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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
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
 * 应当总是使用 [AniNavigator], 而不要访问 [currentNavigator].
 *
 * @see LocalNavigator
 */
interface AniNavigator {
    fun setNavController(
        controller: NavHostController,
    )

    fun isNavControllerReady(): Boolean

    suspend fun awaitNavController(): NavHostController

    // Not @Stable
    val currentNavigator: NavHostController

    @Composable
    fun collectNavigatorAsState(): State<NavHostController>

    fun popBackStack() {
        currentNavigator.popBackStack()
    }

    fun popBackStack(route: NavRoutes, inclusive: Boolean, saveState: Boolean = false) {
        currentNavigator.popBackStack(route, inclusive, saveState)
    }

//    fun popBackStack(
//        route: String,
//        inclusive: Boolean,
//    ) {
//        navigator.popBackStackIfExist(route, inclusive = true)
//    }

    fun popUntilNotWelcome() {
        currentNavigator.popBackStack(NavRoutes.Welcome, inclusive = true)
    }

    fun popUntilNotAuth() {
        currentNavigator.popBackStack(NavRoutes.BangumiTokenAuth, inclusive = true)
        currentNavigator.popBackStack(NavRoutes.BangumiOAuth, inclusive = true)
    }

    fun navigateSubjectDetails(
        subjectId: Int,
        placeholder: SubjectDetailPlaceholder?,
    ) {
        currentNavigator.navigate(
            NavRoutes.SubjectDetail(subjectId, placeholder),
        )
    }

    fun navigateSubjectCaches(subjectId: Int) {
        currentNavigator.navigate(NavRoutes.SubjectCaches(subjectId))
    }

    fun navigateEpisodeDetails(subjectId: Int, episodeId: Int, fullscreen: Boolean = false) {
        currentNavigator.popBackStack(NavRoutes.EpisodeDetail(subjectId, episodeId), inclusive = true)
        currentNavigator.navigate(NavRoutes.EpisodeDetail(subjectId, episodeId))
    }

    fun navigateWelcome() {
        currentNavigator.navigate(NavRoutes.Welcome)
    }

    fun navigateOnboarding() {
        currentNavigator.navigate(NavRoutes.Onboarding)
    }
    
    fun navigateOnboardingComplete() {
        currentNavigator.navigate(NavRoutes.OnboardingComplete)
    }

    fun navigateMain(
        page: MainScreenPage,
        requestFocus: Boolean = false
    ) {
        currentNavigator.navigate(NavRoutes.Main(page)) {
            popUpTo(NavRoutes.OnboardingComplete) { inclusive = true }
            popUpTo(NavRoutes.Onboarding) { inclusive = true }
            popUpTo(NavRoutes.Welcome) { inclusive = true }
        }
    }

    /**
     * 登录页面
     */
    fun navigateBangumiOAuthOrTokenAuth() {
        currentNavigator.navigate(NavRoutes.BangumiOAuth) {
            launchSingleTop = true
        }
    }

    fun navigateBangumiTokenAuth() {
        currentNavigator.navigate(
            NavRoutes.BangumiTokenAuth,
        ) {
            launchSingleTop = true
            popUpTo(NavRoutes.BangumiOAuth) {
                inclusive = true
            }
        }
    }

    fun navigateSettings(tab: SettingsTab? = null) {
        currentNavigator.navigate(NavRoutes.Settings(tab))
    }

    fun navigateEditMediaSource(
        factoryId: FactoryId,
        mediaSourceInstanceId: String,
    ) {
        currentNavigator.navigate(
            NavRoutes.EditMediaSource(factoryId.value, mediaSourceInstanceId),
        )
    }

    fun navigateTorrentPeerSettings() {
        currentNavigator.navigate(NavRoutes.TorrentPeerSettings)
    }

    fun navigateCaches() {
        currentNavigator.navigate(NavRoutes.Caches)
    }

    fun navigateCacheDetails(cacheId: String) {
        currentNavigator.navigate(NavRoutes.CacheDetail(cacheId))
    }

    fun navigateSchedule() {
        currentNavigator.navigate(NavRoutes.Schedule)
    }
}

fun AniNavigator(): AniNavigator = AniNavigatorImpl()

private class AniNavigatorImpl : AniNavigator {
    private val _navigator: MutableSharedFlow<NavHostController> =
        MutableSharedFlow(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val currentNavigator: NavHostController
        get() = _navigator.replayCache.firstOrNull() ?: error("Navigator is not yet set")

    @Composable
    override fun collectNavigatorAsState(): State<NavHostController> = _navigator.collectAsState(currentNavigator)

    override fun setNavController(controller: NavHostController) {
        check(this._navigator.tryEmit(controller)) {
            "Failed to set NavController"
        }
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
    val currentState = rememberUpdatedState(LocalNavigator.current)
    val newNavigatorState = rememberUpdatedState(newNavigator)
    val new by remember {
        derivedStateOf {
            newNavigatorState.value(currentState.value)
        }
    }
    CompositionLocalProvider(LocalNavigator provides new) {
        content()
    }
}
