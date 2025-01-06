/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.utils.coroutines.childScope
import org.koin.core.Koin
import kotlin.coroutines.CoroutineContext


/**
 * 该类封装了针对单个 episode 的基础信息获取 ([SubjectEpisodeInfoBundle]), 媒体获取 ([MediaFetchSession]) 和媒体选择 ([MediaSelector]) 逻辑，
 * 并与特定的 `episodeId` 绑定, 充当一个管理针对该 episodeId 启动的后台任务的作用域。
 *
 * 当需要切换到新的 `episodeId` 时，需要关闭当前 [EpisodeSession]（调用 [sessionScope].cancel），并创建新的 [EpisodeSession] 实例来管理新的 episode 流程。
 * 这个切换逻辑在 [EpisodeFetchSelectPlayState.switchEpisode] 中实现.
 *
 * ### 主要功能
 * 1. [MediaFetchSession] 和 [MediaSelector] 的管理和同步. 通过 [fetchSelectFlow] 提供. 作为一个 bundle 提供, 确保两者的数据一致性.
 * 2. **信息加载**：利用 [infoBundleFlow] 加载并维护 episode 相关的信息 ([SubjectEpisodeInfoBundle]).
 * 3. **加载状态与错误处理**：
 *   - [infoLoadErrorStateFlow] 用于捕获并上报加载错误，例如网络错误或数据解析错误。
 *   - [mediaSourceLoadingFlow] 用于指示 [MediaFetcher] 的所有数据是否都加载完成了, 供下游监听使用.
 * 4. 后台任务作用域. [sessionScope] 可以用来启动针对该 episode 的后台任务, 例如自动选择数据源.
 *
 * **注意**：若要在不同的 Flow 中同时收集 [EpisodeSession] 提供的 flow，请仔细参考 [UnsafeEpisodeSessionApi] 提示，以防止在切换 episode 时产生意外的竞争或数据错配。
 *
 * @param subjectId 当前所管理的番剧或节目主体 ID。
 * @param episodeId 当前 session 管理的 episode ID，与本会话的生命周期强绑定。
 */
class EpisodeSession(
    subjectId: Int,
    val episodeId: Int,
    koin: Koin,
    parentCoroutineContext: CoroutineContext,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(),
) {
    private val createMediaFetchSelectBundleFlowUseCase: CreateMediaFetchSelectBundleFlowUseCase by koin.inject()

    /**
     * Scope for this session (bound to episodeId).
     * Should be closed when switching to another episode.
     *
     * @see EpisodeFetchSelectPlayState.switchEpisode
     */
    internal val sessionScope =
        parentCoroutineContext.childScope(CoroutineName("SubjectEpisodeFetchSelectSession")) // supervisor scope

    // 防止重新启动
    internal val sessionScopeTasksStarted = MutableStateFlow(false)

    /**
     * 加载 episode 的基本信息
     */
    private val infoLoader = SubjectEpisodeInfoBundleLoader(
        subjectId,
        flowOf(episodeId), // single element, so infoBundleFlow may complete.
        koin,
    )

    /**
     * @see SubjectEpisodeInfoBundleLoader.infoBundleFlow
     */
    val infoBundleFlow: SharedFlow<SubjectEpisodeInfoBundle?> = infoLoader.infoBundleFlow
        .shareIn(sessionScope, started = sharingStarted, replay = 1)

    /**
     * @see SubjectEpisodeInfoBundleLoader.infoLoadErrorState
     */
    val infoLoadErrorStateFlow: StateFlow<LoadError?> get() = infoLoader.infoLoadErrorState


    /**
     * A flow of the bundle of [MediaFetchSession] and [MediaSelector].
     *
     * Flow re-emits (almost immediately) when [infoBundleFlow] emits.
     *
     * This flow does not produce errors.
     */
    val fetchSelectFlow = createMediaFetchSelectBundleFlowUseCase(infoBundleFlow)
        .shareIn(sessionScope, sharingStarted, replay = 1)
    // TODO: 2025/1/4 test fetchSelectFlow changes only when infoBundleFlow's value equality changes 

    /**
     * A cold flow that emits `true` when media sources are loading.
     *
     * - When all media sources have completed, this flow emits `false`.
     * - If there is an error when loading episode data, this flow emits `false`.
     */
    val mediaSourceLoadingFlow = fetchSelectFlow
        .transformLatest { bundle ->
            if (bundle == null) {
                emit(false)
                return@transformLatest
            }
            emitAll(
                bundle.mediaFetchSession.hasCompleted.map {
                    !it.allCompleted()
                },
            )
        }
}
