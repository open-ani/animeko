/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.EpisodeEnter

/**
 * Supports navigation to any page in the app.
 *
 * 这是 Navigation 3 的导航入口. 导航状态就是一个 [NavRoutes] 的栈 ([backStack]), 栈顶为最后一个元素.
 * 所有导航操作都是对这个栈的增删, 由 `NavDisplay` 观察并渲染.
 *
 * 应当总是使用 [AniNavigator] 提供的方法, 而不要直接修改 [backStack].
 *
 * @see LocalNavigator
 */
interface AniNavigator {
    /**
     * 绑定 back stack. 由 UI 入口点 (`AniAppContent`) 在创建 back stack 后调用.
     */
    fun setBackStack(backStack: SnapshotStateList<NavRoutes>)

    fun isBackStackReady(): Boolean

    suspend fun awaitBackStack(): List<NavRoutes>

    /**
     * 当前的 back stack, 栈顶为最后一个元素. 在 composition 中读取它会自动订阅变化.
     *
     * 在 [setBackStack] 之前访问会抛出异常.
     */
    // Not @Stable
    val backStack: List<NavRoutes>

    /**
     * 入栈一个页面.
     */
    fun navigate(route: NavRoutes)

    /**
     * 弹出栈顶页面. 如果栈中只剩一个页面, 则不做任何操作 (由系统决定是否退出 APP).
     */
    fun popBackStack()

    /**
     * 弹出到栈中最近的 [route] 为止. [inclusive] 为 `true` 时 [route] 自身也会被弹出.
     *
     * 如果栈中没有 [route], 则不做任何操作. 永远不会把栈弹空.
     */
    fun popBackStack(route: NavRoutes, inclusive: Boolean)

    fun navigateSubjectDetails(
        subjectId: Int,
        placeholder: SubjectDetailPlaceholder?,
    ) {
        navigate(NavRoutes.SubjectDetail(subjectId, placeholder))
    }

    fun navigateSubjectCaches(subjectId: Int) {
        navigate(NavRoutes.SubjectCaches(subjectId))
    }

    fun navigatePersonDetails(personId: Int) {
        navigate(NavRoutes.PersonDetail(personId))
    }

    fun navigateCharacterDetails(characterId: Int) {
        navigate(NavRoutes.CharacterDetail(characterId))
    }

    fun navigateEpisodeDetails(
        subjectId: Int,
        episodeId: Int,
        fullscreen: Boolean = false,
        force: Boolean = false,
    ) {
        if (!force && !EpisodeNavigationGuardRegistry.checkOrNotifyDenied(subjectId, episodeId)) {
            return
        }
        // 避免同一个剧集在栈中重复出现
        popBackStack(NavRoutes.EpisodeDetail(subjectId, episodeId), inclusive = true)
        navigate(NavRoutes.EpisodeDetail(subjectId, episodeId))
        Analytics.recordEvent(
            EpisodeEnter,
            mapOf("subject_id" to subjectId, "episode_id" to episodeId),
        )
    }

    fun navigateWelcome() {
        navigate(NavRoutes.Welcome)
    }

    /**
     * 向导结束后, 导航到主页时要弹出的目标.
     *
     * @see NavRoutes.Onboarding.popUpTargetInclusive
     */
    fun navigateOnboarding(completionPopUpTargetInclusive: NavRoutes?) {
        navigate(NavRoutes.Onboarding(completionPopUpTargetInclusive))
    }

    /**
     * 向导结束后, 导航到主页时要弹出的目标.
     *
     * @see NavRoutes.Onboarding.popUpTargetInclusive
     */
    fun navigateOnboardingComplete(completionPopUpTargetInclusive: NavRoutes?) {
        navigate(NavRoutes.OnboardingComplete(completionPopUpTargetInclusive))
        Analytics.recordEvent(AnalyticsEvent.OnboardingDone)
    }

    /**
     * 导航到主页. 如果指定了 [popUpTargetInclusive], 则先把它 (含) 之上的页面全部弹出.
     *
     * 注意这里允许把栈弹空 (例如从 [NavRoutes.Welcome] 开始的向导结束时), 因为紧接着就会压入主页.
     */
    fun navigateMain(
        page: MainScreenPage,
        popUpTargetInclusive: NavRoutes? = null,
    )

    @MainThread
    fun navigateEmailLoginStart() {
        navigate(NavRoutes.EmailLoginStart)
    }

    @MainThread
    fun navigateEmailLoginVerify() {
        navigate(NavRoutes.EmailLoginVerify)
    }

    /**
     * 返回到第一个 [NavRoutes.Main], 根据当前的 [backStack] 进行不同的操作:
     *
     * * 如果 [backStack] 中有 [NavRoutes.Main], 则弹出到栈中**第一个** [NavRoutes.Main] 为止 (不含它自己).
     * * 如果 [backStack] 中没有 [NavRoutes.Main], 则清空栈并导航到 [NavRoutes.Main],
     *   此时栈中将只有一个 [NavRoutes.Main]. **这种情况通常不会出现**.
     */
    fun popBackOrNavigateToMain(mainSceneInitialPage: MainScreenPage)

    /**
     * 登录页面
     */
    fun navigateLogin() {
        navigate(NavRoutes.EmailLoginStart)
    }

    fun navigateBangumiAuthorize() {
        navigate(NavRoutes.BangumiAuthorize)
    }

    fun navigatePlaybackHistorySyncStatus() {
        navigate(NavRoutes.PlaybackHistorySyncStatus)
    }

    fun navigateSettings(tab: SettingsTab? = null) {
        navigate(NavRoutes.Settings(tab))
    }

    fun navigateSubjectSearch(search: NavRoutes.SubjectSearch = NavRoutes.SubjectSearch()) {
        navigate(search)
    }

    fun navigateSubjectSearch(tag: String) {
        navigate(NavRoutes.SubjectSearch(tags = listOf(tag)))
    }

    fun navigateEditMediaSource(
        factoryId: FactoryId,
        mediaSourceInstanceId: String,
    ) {
        navigate(NavRoutes.EditMediaSource(factoryId.value, mediaSourceInstanceId))
    }

    fun navigateTorrentPeerSettings() {
        navigate(NavRoutes.TorrentPeerSettings)
    }

    fun navigateCaches() {
        navigate(NavRoutes.Caches)
    }

    fun navigateCacheDetails(cacheId: String) {
        navigate(NavRoutes.CacheDetail(cacheId))
    }

    fun navigateSchedule() {
        navigate(NavRoutes.Schedule)
    }

    fun navigatePlaybackHistory() {
        navigate(NavRoutes.PlaybackHistory)
    }
}

fun AniNavigator(): AniNavigator = AniNavigatorImpl()

private class AniNavigatorImpl : AniNavigator {
    private val _backStack: MutableStateFlow<SnapshotStateList<NavRoutes>?> = MutableStateFlow(null)

    private val currentBackStack: SnapshotStateList<NavRoutes>
        get() = _backStack.value ?: error("Back stack is not yet set")

    override val backStack: List<NavRoutes>
        get() = currentBackStack

    override fun setBackStack(backStack: SnapshotStateList<NavRoutes>) {
        _backStack.value = backStack
    }

    override fun isBackStackReady(): Boolean = _backStack.value != null

    override suspend fun awaitBackStack(): List<NavRoutes> = _backStack.filterNotNull().first()

    override fun navigate(route: NavRoutes) {
        currentBackStack.add(route)
    }

    override fun popBackStack() {
        val stack = currentBackStack
        // 栈至少要保留一个页面, 否则 NavDisplay 会抛异常. 根页面的返回由系统处理 (如 Android 退出 APP).
        if (stack.size <= 1) return
        stack.removeAt(stack.lastIndex)
    }

    override fun popBackStack(route: NavRoutes, inclusive: Boolean) {
        val stack = currentBackStack
        val index = stack.indexOfLast { it == route }
        if (index == -1) return
        val targetSize = if (inclusive) index else index + 1
        stack.popTo(targetSize)
    }

    override fun navigateMain(page: MainScreenPage, popUpTargetInclusive: NavRoutes?) {
        val stack = currentBackStack
        // pop 和 push 必须原子完成: pop 可能会把栈清空, 而空栈会让 NavDisplay 抛异常
        Snapshot.withMutableSnapshot {
            if (popUpTargetInclusive != null) {
                val index = stack.indexOfLast { it == popUpTargetInclusive }
                if (index != -1) {
                    stack.popTo(index, keepAtLeastOne = false)
                }
            }
            stack.add(NavRoutes.Main(page))
        }
    }

    override fun popBackOrNavigateToMain(mainSceneInitialPage: MainScreenPage) {
        val stack = currentBackStack
        val firstMain = stack.indexOfFirst { it is NavRoutes.Main }
        if (firstMain != -1) {
            stack.popTo(firstMain + 1)
            return
        }
        Snapshot.withMutableSnapshot {
            stack.clear()
            stack.add(NavRoutes.Main(mainSceneInitialPage))
        }
    }

    /**
     * 把 [stack] 弹到只剩 [targetSize] 个元素.
     *
     * [keepAtLeastOne] 为 `true` 时至少保留一个元素, 避免空栈让 NavDisplay 抛异常.
     * 只有在调用方保证紧接着会压入新页面时才能传 `false`.
     */
    private fun SnapshotStateList<NavRoutes>.popTo(targetSize: Int, keepAtLeastOne: Boolean = true) {
        val size = if (keepAtLeastOne) targetSize.coerceAtLeast(1) else targetSize
        while (this.size > size) {
            removeAt(lastIndex)
        }
    }
}

/**
 * Find last route of type [T] in the back stack.
 */
inline fun <reified T : NavRoutes> AniNavigator.findLast(): T? =
    backStack.lastOrNull { it is T } as T?

/**
 * Find first route of type [T] in the back stack.
 */
inline fun <reified T : NavRoutes> AniNavigator.findFirst(): T? =
    backStack.firstOrNull { it is T } as T?

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
