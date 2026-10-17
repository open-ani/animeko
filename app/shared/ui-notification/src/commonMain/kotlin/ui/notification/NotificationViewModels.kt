/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.data.models.notification.Announcement
import me.him188.ani.app.data.models.notification.AppNotification
import me.him188.ani.app.data.models.notification.NotificationBadgeState
import me.him188.ani.app.data.models.notification.NotificationGroup
import me.him188.ani.app.data.models.notification.NotificationType
import me.him188.ani.app.data.models.preference.NotificationSettings
import me.him188.ani.app.data.repository.notification.NotificationRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.notification.NotificationBadgeManager
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.logging.warn
import org.koin.core.Koin

/**
 * 当前用户能否查看自己的通知.
 */
enum class NotificationAccess {
    /**
     * 登录状态尚未确定 (App 刚启动, 正在刷新 token).
     */
    LOADING,

    /**
     * 游客, 或登录已失效. 只能看公告.
     */
    GUEST,
    AUTHORIZED,
    ;

    companion object {
        /**
         * 网络错误导致无法刷新 token 时仍视为已登录: 请求会以网络错误失败, 由列表展示可重试的错误.
         */
        fun from(state: SessionState): NotificationAccess = when (state) {
            is SessionState.Valid -> AUTHORIZED
            is SessionState.Invalid -> when (state.reason) {
                InvalidSessionReason.NETWORK_ERROR -> AUTHORIZED
                InvalidSessionReason.NO_TOKEN,
                InvalidSessionReason.UNKNOWN,
                    -> GUEST
            }
        }
    }
}

private fun SessionStateProvider.accessFlow(): Flow<NotificationAccess> =
    stateFlow.map { NotificationAccess.from(it) }.distinctUntilChanged()

/**
 * 通知中心.
 *
 * - 每个分组 ([groups]) 有自己的列表, 按分组 (服务端的分类) 分页加载, 排除本机关闭的类型
 *   ([NotificationSettings.excludedTypes]). 用户是游客时不请求, 列表为空. 本机关闭的类型变化时重新加载.
 * - 标记已读先更新 [readOverlays], 再请求服务端; 失败时撤销本地标记. 请求结束后 (无论成败) 请求刷新未读数.
 */
@Stable
class NotificationCenterViewModel(
    koin: Koin = GlobalKoin,
) : AbstractViewModel() {
    private val repository: NotificationRepository by koin.inject()
    private val badgeManager: NotificationBadgeManager by koin.inject()
    private val sessionStateProvider: SessionStateProvider by koin.inject()
    private val settingsRepository: SettingsRepository by koin.inject()

    /**
     * 通知中心的标签页, 按显示顺序. 多于一个时显示标签页.
     */
    val groups: List<NotificationGroup> = NotificationGroup.TABS

    private val selectedGroupFlow = MutableStateFlow(NotificationGroup.NOTICE)

    /**
     * 当前选中的标签页. 默认为 [NotificationGroup.NOTICE].
     */
    val selectedGroup: StateFlow<NotificationGroup> = selectedGroupFlow.asStateFlow()

    val access: StateFlow<NotificationAccess> = sessionStateProvider.accessFlow()
        .stateIn(backgroundScope, SharingStarted.Eagerly, NotificationAccess.LOADING)

    /**
     * 本机关闭的类型. 每个分组的列表和 "全部标为已读" 都排除这些类型.
     */
    private val excludedTypes: Flow<Set<NotificationType>> = settingsRepository.notificationSettings.flow
        .map { it.excludedTypes }
        .distinctUntilChanged()

    private val pagersByGroup: Map<NotificationGroup, Flow<PagingData<AppNotification>>> =
        groups.associateWith { group ->
            combine(access, excludedTypes) { access, excludedTypes -> access to excludedTypes }
                .distinctUntilChanged()
                .flatMapLatest { (access, excludedTypes) ->
                    if (access == NotificationAccess.AUTHORIZED) {
                        repository.notificationsPager(group, excludedTypes)
                    } else {
                        flowOf(PagingData.empty())
                    }
                }
                .cachedIn(backgroundScope)
        }

    private val readOverlaysFlow = MutableStateFlow(emptyReadOverlays())

    /**
     * 每个分组本地叠加的已读状态. "全部标为已读" 不作用于当时关闭的类型, 所以本机关闭的类型变化时重置各分组的这一标记.
     * 登录状态变化时整个重置: 列表会重新加载, 已读状态以服务端为准, 而上一个用户的本地标记会让新用户的未读通知显示为已读,
     * 点击也不会再标记.
     */
    val readOverlays: StateFlow<Map<NotificationGroup, ReadOverlay>> = readOverlaysFlow.asStateFlow()

    val badgeState: StateFlow<NotificationBadgeState> get() = badgeManager.state

    init {
        badgeManager.requestRefresh()
        resetOnAccessChange(access) { readOverlaysFlow.value = emptyReadOverlays() }
        excludedTypes.drop(1)
            .onEach {
                readOverlaysFlow.update { overlays ->
                    overlays.mapValues { (_, overlay) -> overlay.copy(allReadUpToMillis = null) }
                }
            }
            .launchIn(backgroundScope)
    }

    private fun emptyReadOverlays(): Map<NotificationGroup, ReadOverlay> = groups.associateWith { ReadOverlay.Empty }

    private fun updateReadOverlay(group: NotificationGroup, update: (ReadOverlay) -> ReadOverlay) {
        readOverlaysFlow.update { overlays ->
            val overlay = overlays[group] ?: return@update overlays
            overlays + (group to update(overlay))
        }
    }

    private fun readOverlay(group: NotificationGroup): ReadOverlay =
        readOverlaysFlow.value[group] ?: ReadOverlay.Empty

    /**
     * [group] 的通知列表. [group] 必须在 [groups] 中.
     */
    fun notificationsPager(group: NotificationGroup): Flow<PagingData<AppNotification>> = pagersByGroup.getValue(group)

    fun selectGroup(group: NotificationGroup) {
        if (group in groups) selectedGroupFlow.value = group
    }

    /**
     * 打开一条通知时调用. 已读的通知, 以及不属于任何标签页的通知不做任何事.
     */
    fun markRead(notification: AppNotification) {
        val group = notification.group?.takeIf { it in groups } ?: return
        if (readOverlay(group).isRead(notification)) return
        updateReadOverlay(group) { it.withRead(notification.id) }
        backgroundScope.launch {
            try {
                repository.markNotificationsRead(listOf(notification.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateReadOverlay(group) { it.withoutRead(notification.id) }
                logger.warn(e) { "Failed to mark notification ${notification.id} as read" }
            } finally {
                // 请求可能已经到达服务端, 即使随后被取消
                badgeManager.requestRefresh()
            }
        }
    }

    /**
     * 把 [group] 中不晚于 [newestCreatedAtMillis] 的通知全部标为已读, 本机关闭的类型除外. 传入这个分组已加载的最新一条的创建时间,
     * 以免把用户还没看到的通知也标为已读. 只在本地标记这个分组.
     *
     * 本地先把不晚于 [newestCreatedAtMillis] 的通知显示为已读, 再在 ViewModel 的作用域中请求并等待它完成:
     * 调用方的协程被取消 (例如离开页面) 时, 已经发出的请求仍会完成. 无论结果如何, 结束后都请求刷新未读数,
     * 因为请求可能已经到达服务端.
     *
     * @throws me.him188.ani.app.data.repository.RepositoryException 请求失败时. 此时撤销本地标记.
     */
    suspend fun markAllRead(group: NotificationGroup, newestCreatedAtMillis: Long) {
        val excludedTypes = excludedTypes.first()
        val previous = readOverlay(group).allReadUpToMillis
        val applied = maxOf(previous ?: Long.MIN_VALUE, newestCreatedAtMillis)
        updateReadOverlay(group) { it.copy(allReadUpToMillis = applied) }
        backgroundScope.async {
            try {
                repository.markAllNotificationsRead(group, excludedTypes, beforeMillis = newestCreatedAtMillis)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 只撤销这次的标记: 请求期间本地标记可能已被重置或再次修改
                updateReadOverlay(group) {
                    if (it.allReadUpToMillis == applied) it.copy(allReadUpToMillis = previous) else it
                }
                throw e
            } finally {
                badgeManager.requestRefresh()
            }
        }.await()
    }

    fun refreshBadge() {
        badgeManager.requestRefresh()
    }
}

/**
 * 公告列表. 游客也能浏览, 但没有已读状态, 也不能标记已读.
 *
 * 登录用户进入列表即已读: 列表每加载完一页, 就把发布时间不晚于已加载的最新一条的公告全部标为已读, 并刷新未读数.
 * 列表仍显示进入前的未读标记, 让用户看出哪些是新的: 每条公告保持本次浏览中第一次加载到它时服务端的已读状态, 刷新列表也不变.
 * 所以之后才加载到的公告 (例如下一页) 显示加载时服务端的状态, 发布时间不晚于已标记时间的公告这时已经是已读.
 */
@Stable
class AnnouncementsViewModel(
    koin: Koin = GlobalKoin,
) : AbstractViewModel() {
    private val repository: NotificationRepository by koin.inject()
    private val badgeManager: NotificationBadgeManager by koin.inject()
    private val sessionStateProvider: SessionStateProvider by koin.inject()

    val access: StateFlow<NotificationAccess> = sessionStateProvider.accessFlow()
        .stateIn(backgroundScope, SharingStarted.Eagerly, NotificationAccess.LOADING)

    private val visitFlow = MutableStateFlow<AnnouncementsVisit?>(null)

    /**
     * 登录状态变化时重新加载, 以获得对应用户的已读状态.
     */
    val announcementsPager: Flow<PagingData<Announcement>> = access
        .flatMapLatest { access ->
            if (access == NotificationAccess.LOADING) {
                flowOf(PagingData.empty())
            } else {
                val visit = AnnouncementsVisit(markAsRead = access == NotificationAccess.AUTHORIZED)
                visitFlow.value = visit
                repository.announcementsPager().map { data -> data.map { visit.onLoaded(it) } }
            }
        }
        .cachedIn(backgroundScope)

    private val readOverlayFlow = MutableStateFlow(ReadOverlay.Empty)

    /**
     * 本地叠加的已读状态, 记录本次浏览中点击过的公告. 登录状态变化时重置, 原因见 [NotificationCenterViewModel.readOverlays].
     */
    val readOverlay: StateFlow<ReadOverlay> = readOverlayFlow.asStateFlow()

    init {
        resetOnAccessChange(access) { readOverlayFlow.value = ReadOverlay.Empty }
    }

    /**
     * 列表加载完一页后调用. 登录用户把发布时间不晚于已加载的最新一条的公告全部标为已读, 并刷新未读数.
     * 已经标记过的时间不再请求; 请求失败时, 下次调用会重试.
     */
    fun markLoadedAnnouncementsRead() {
        val visit = visitFlow.value ?: return
        if (!visit.markAsRead) return
        backgroundScope.launch {
            visit.markMutex.withLock {
                val newest = visit.newestLoadedMillis.value ?: return@launch
                val marked = visit.markedUpToMillis.value
                if (marked != null && newest <= marked) return@launch
                visit.markedUpToMillis.value = newest
                try {
                    repository.markAllAnnouncementsRead(beforeMillis = newest)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    visit.markedUpToMillis.value = marked
                    logger.warn(e) { "Failed to mark announcements up to $newest as read" }
                } finally {
                    // 请求可能已经到达服务端, 即使随后被取消
                    badgeManager.requestRefresh()
                }
            }
        }
    }

    /**
     * 打开一条公告时调用, 在本地把它显示为已读. 游客 ([Announcement.read] 为 `null`) 和已读的公告不做任何事.
     * 进入列表时已经标为已读的公告不再请求服务端.
     */
    fun markRead(announcement: Announcement) {
        if (access.value != NotificationAccess.AUTHORIZED) return
        if (readOverlayFlow.value.isRead(announcement) != false) return
        readOverlayFlow.update { it.withRead(announcement.id) }
        if (visitFlow.value?.isMarkedRead(announcement) == true) return
        backgroundScope.launch {
            try {
                repository.markAnnouncementsRead(listOf(announcement.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                readOverlayFlow.update { it.withoutRead(announcement.id) }
                logger.warn(e) { "Failed to mark announcement ${announcement.id} as read" }
            } finally {
                // 请求可能已经到达服务端, 即使随后被取消
                badgeManager.requestRefresh()
            }
        }
    }

    fun refreshBadge() {
        badgeManager.requestRefresh()
    }
}

/**
 * 同一登录状态下的一次浏览. 登录状态变化时公告列表重新加载, 开始新的一次浏览.
 *
 * @property markAsRead 是否把加载到的公告标为已读. 只有登录用户有已读状态.
 */
private class AnnouncementsVisit(val markAsRead: Boolean) {
    /**
     * 每条公告第一次加载到时服务端的已读状态.
     */
    private val firstSeenRead = MutableStateFlow<Map<String, Boolean?>>(emptyMap())

    /**
     * 已加载的公告中最新的发布时间. 尚未加载到公告时为 `null`.
     */
    val newestLoadedMillis = MutableStateFlow<Long?>(null)

    /**
     * 已经在服务端标为已读 (或请求正在进行) 的发布时间上限. 只在持有 [markMutex] 时修改.
     */
    val markedUpToMillis = MutableStateFlow<Long?>(null)
    val markMutex = Mutex()

    /**
     * 记录加载到的 [announcement], 返回第一次加载到它时的已读状态.
     */
    fun onLoaded(announcement: Announcement): Announcement {
        newestLoadedMillis.update { maxOf(it ?: Long.MIN_VALUE, announcement.publishedAtMillis) }
        val firstSeen = firstSeenRead.updateAndGet {
            if (announcement.id in it) it else it + (announcement.id to announcement.read)
        }
        val read = firstSeen.getValue(announcement.id)
        return if (read == announcement.read) announcement else announcement.copy(read = read)
    }

    fun isMarkedRead(announcement: Announcement): Boolean {
        val markedUpTo = markedUpToMillis.value ?: return false
        return announcement.publishedAtMillis <= markedUpTo
    }
}

/**
 * 登录状态变化时调用 [reset]. 启动时从 [NotificationAccess.LOADING] 得到第一个确定的状态不算变化.
 */
private fun AbstractViewModel.resetOnAccessChange(
    access: Flow<NotificationAccess>,
    reset: () -> Unit,
) {
    access.filter { it != NotificationAccess.LOADING }
        .distinctUntilChanged()
        .drop(1)
        .onEach { reset() }
        .launchIn(backgroundScope)
}

/**
 * 详情页的加载状态.
 */
@Immutable
sealed interface DetailLoadState<out T> {
    @Immutable
    data object Loading : DetailLoadState<Nothing>

    @Immutable
    data class Loaded<T>(val value: T) : DetailLoadState<T>

    /**
     * 通知或公告不存在. 重试也不会成功.
     */
    @Immutable
    data object NotFound : DetailLoadState<Nothing>

    /**
     * 例如网络错误.
     */
    @Immutable
    data class Failed(val error: LoadError) : DetailLoadState<Nothing>
}

/**
 * @param load 不存在时返回 `null`.
 */
private fun <T : Any> loadDetailFlow(
    restarter: FlowRestarter,
    load: suspend () -> T?,
): Flow<DetailLoadState<T>> = flow {
    emit(DetailLoadState.Loading)
    val result = try {
        load()?.let { DetailLoadState.Loaded(it) } ?: DetailLoadState.NotFound
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DetailLoadState.Failed(LoadError.fromException(e))
    }
    emit(result)
}.restartable(restarter)

/**
 * 单独打开的通知详情页 (窄屏).
 */
@Stable
class NotificationDetailViewModel(
    val notificationId: String,
    koin: Koin = GlobalKoin,
) : AbstractViewModel() {
    private val repository: NotificationRepository by koin.inject()
    private val restarter = FlowRestarter()

    val state: StateFlow<DetailLoadState<AppNotification>> =
        loadDetailFlow(restarter) { repository.getNotification(notificationId) }
            .stateIn(backgroundScope, SharingStarted.Lazily, DetailLoadState.Loading)

    fun reload() {
        restarter.restart()
    }
}

/**
 * 单独打开的公告详情页 (窄屏).
 */
@Stable
class AnnouncementDetailViewModel(
    val announcementId: String,
    koin: Koin = GlobalKoin,
) : AbstractViewModel() {
    private val repository: NotificationRepository by koin.inject()
    private val restarter = FlowRestarter()

    val state: StateFlow<DetailLoadState<Announcement>> =
        loadDetailFlow(restarter) { repository.getAnnouncement(announcementId) }
            .stateIn(backgroundScope, SharingStarted.Lazily, DetailLoadState.Loading)

    fun reload() {
        restarter.restart()
    }
}
