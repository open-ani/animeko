/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.navigation

import androidx.annotation.MainThread
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
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.toRoute
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.EpisodeEnter

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
        Analytics.recordEvent(
            EpisodeEnter,
            mapOf("subject_id" to subjectId, "episode_id" to episodeId),
        )
    }

    fun navigateWelcome() {
        currentNavigator.navigate(NavRoutes.Welcome)
    }

    /**
     * 向导结束后, 导航到主页时 [NavOptionsBuilder.popUpTo] 的目标.
     *
     * @see NavRoutes.Onboarding.popUpTargetInclusive
     */
    fun navigateOnboarding(completionPopUpTargetInclusive: NavRoutes?) {
        currentNavigator.navigate(NavRoutes.Onboarding(completionPopUpTargetInclusive))
    }

    /**
     * 向导结束后, 导航到主页时 [NavOptionsBuilder.popUpTo] 的目标.
     *
     * @see NavRoutes.Onboarding.popUpTargetInclusive
     */
    fun navigateOnboardingComplete(completionPopUpTargetInclusive: NavRoutes?) {
        currentNavigator.navigate(NavRoutes.OnboardingComplete(completionPopUpTargetInclusive))
        Analytics.recordEvent(AnalyticsEvent.OnboardingDone)
    }

    fun navigateMain(
        page: MainScreenPage,
        popUpTargetInclusive: NavRoutes? = null,
    ) {
        currentNavigator.navigate(NavRoutes.Main(page)) {
            if (popUpTargetInclusive != null) {
                popUpTo(popUpTargetInclusive) { inclusive = true }
            }
        }
    }

    @MainThread
    fun navigateEmailLoginStart() {
        currentNavigator.navigate(NavRoutes.EmailLoginStart)
    }

    @MainThread
    fun navigateEmailLoginVerify() {
        currentNavigator.navigate(NavRoutes.EmailLoginVerify)
    }

    /**
     * 返回到第一个 [NavRoutes.Main], 根据当前的 back stack 进行不同的操作:
     *
     * * 如果 [currentBackStack][NavHostController.currentBackStack] 中有 [NavRoutes.Main],
     *   则 [pop back][NavHostController.popBackStack] 到 back stack 中第一个 [NavRoutes.Main].
     * * 如果 [currentBackStack][NavHostController.currentBackStack] 没有 [NavRoutes.Main],
     *   则 [navigate][NavHostController.navigate] 到 [NavRoutes.Main], 并 pop 所有的 back stack,
     *   此时 back stack 中将只有一个 [NavRoutes.Main]. **这种情况通常不会出现**.
     *
     * TODO: ios uses restricted api.
     */
    fun popBackOrNavigateToMain(mainSceneInitialPage: MainScreenPage) {
        currentNavigator.popBackOrNavigateToMain(mainSceneInitialPage)
    }

    /**
     * 登录页面
     */
    fun navigateLogin() {
        currentNavigator.navigate(NavRoutes.EmailLoginStart)
    }

    fun navigateBangumiAuthorize() {
        currentNavigator.navigate(NavRoutes.BangumiAuthorize)
    }

    fun navigateSettings(tab: SettingsTab? = null) {
        currentNavigator.navigate(NavRoutes.Settings(tab))
    }

    fun navigateSubjectSearch(search: NavRoutes.SubjectSearch = NavRoutes.SubjectSearch()) {
        currentNavigator.navigate(search)
    }

    fun navigateSubjectSearch(tag: String) {
        currentNavigator.navigate(NavRoutes.SubjectSearch(tags = listOf(tag)))
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

// Workaround for calling restricted API on common source
// - androidx.navigation.NavDestination#id
// - androidx.navigation.NavOptionsBuilder#popUpTo(id: Int, popUpToBuilder: PopUpToBuilder.() -> Unit = {})
expect fun NavHostController.popBackOrNavigateToMain(mainSceneInitialPage: MainScreenPage)

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
 * Find last route of type [T] in the back stack.
 */
inline fun <reified T : NavRoutes> NavHostController.findLast(): NavRoutes? {
    val routeFQN = T::class.qualifiedName ?: return null
    return currentBackStack.value
        .asReversed()
        .firstOrNull { it.destination.route?.contains(routeFQN) == true }
        ?.toRoute<T>()
}

/**
 * Find first route of type [T] in the back stack.
 */
inline fun <reified T : NavRoutes> NavHostController.findFirst(): NavRoutes? {
    val routeFQN = T::class.qualifiedName ?: return null
    return currentBackStack.value
        .firstOrNull { it.destination.route?.contains(routeFQN) == true }
        ?.toRoute<T>()
}

inline fun <reified T : NavRoutes> NavHostController.hasAnotherRoute(): Boolean {
    val routeFQN = T::class.qualifiedName ?: return false
    return currentBackStack.value
        .dropLast(1)
        .any { it.destination.route?.contains(routeFQN) == true }
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
