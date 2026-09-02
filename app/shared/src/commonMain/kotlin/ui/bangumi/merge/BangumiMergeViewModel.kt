/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.bangumi.BangumiAutoMergedChange
import me.him188.ani.app.data.models.bangumi.BangumiConflictKey
import me.him188.ani.app.data.models.bangumi.BangumiConflictResolution
import me.him188.ani.app.data.models.bangumi.BangumiMergeSide
import me.him188.ani.app.data.models.bangumi.BangumiMergeState
import me.him188.ani.app.data.repository.subject.BangumiMergeRepository
import me.him188.ani.app.data.repository.subject.BangumiMergeSyncInProgressException
import me.him188.ani.app.domain.bangumi.BangumiConflictChecker
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.logging.warn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * 合并收藏 (Bangumi 冲突处理) 界面状态.
 *
 * 冲突由服务端在全量同步 (对账) 时发现; 客户端只负责展示两侧取值、收集用户的选择并提交.
 */
@Immutable
data class BangumiMergeUiState(
    /**
     * 正在拉取服务端的冲突与自动合并明细.
     */
    val isLoading: Boolean,
    /**
     * 拉取失败.
     */
    val loadError: LoadError?,
    /**
     * 服务端状态 (自动合并明细 / 上次同步时间 / 是否同步中). 加载完成前为 `null`.
     */
    val mergeState: BangumiMergeState?,
    /**
     * 由 [mergeState] 的冲突映射得到的分组卡片.
     */
    val groups: List<SubjectMergeConflictGroup>,
    /**
     * 用户当前的选择.
     */
    val choices: Map<BangumiConflictKey, BangumiMergeSide>,
    /**
     * 正在提交选择.
     */
    val isApplying: Boolean,
    /**
     * 最近一次提交的结果, 由 UI 展示后调用 [BangumiMergeViewModel.clearApplyOutcome] 清除.
     */
    val applyOutcome: BangumiMergeApplyOutcome? = null,
) {
    /**
     * 有需要用户确认的冲突. 没有冲突时界面展示 "已同步" 空状态 (自动合并明细放在其中), 没有底栏与进度.
     */
    val hasConflicts: Boolean get() = groups.isNotEmpty()

    val totalConflictCount: Int get() = groups.sumOf { it.conflicts.size }

    val confirmedCount: Int
        get() = groups.sumOf { group -> group.conflicts.count { it.key in choices } }

    /**
     * 全部冲突都已选择.
     */
    val allResolved: Boolean get() = mergeState != null && confirmedCount == totalConflictCount

    /**
     * 服务端全量同步尚未完成: 正在进行中, 或从未完成过 (首次绑定的同步刚开始 / 尚未开始, [BangumiMergeState.lastSyncedAt] 为 `null`).
     * 冲突列表可能不完整, 服务端会拒绝提交 (409); 界面展示同步中提示而不是 "已同步", ViewModel 轮询直到完成.
     */
    val syncInProgress: Boolean get() = mergeState?.isSyncSettled == false

    /**
     * 可以点击 "应用合并".
     */
    val canApply: Boolean get() = hasConflicts && allResolved && !isApplying && !syncInProgress

    val autoMerged: List<BangumiAutoMergedChange> get() = mergeState?.autoMerged.orEmpty()

    val autoMergedTotal: Int get() = mergeState?.autoMergedTotal ?: 0

    val lastSyncedAt: Instant? get() = mergeState?.lastSyncedAt

    companion object {
        val Initial = BangumiMergeUiState(
            isLoading = true,
            loadError = null,
            mergeState = null,
            groups = emptyList(),
            choices = emptyMap(),
            isApplying = false,
        )
    }
}

/**
 * 一次 "应用合并" 的结果.
 */
@Immutable
sealed class BangumiMergeApplyOutcome {
    /**
     * 服务端已应用选择. [remainingCount] 为 `0` 时全部处理完毕, 界面可以返回;
     * 否则界面已替换为剩余的冲突 (同步期间新发现的等), 提示用户继续处理.
     */
    @Immutable
    data class Applied(
        /**
         * 本次成功合并的冲突数 (提交的选择中已不在剩余列表里的).
         */
        val mergedCount: Int,
        /**
         * 剩余待处理的冲突字段数.
         */
        val remainingCount: Int,
    ) : BangumiMergeApplyOutcome()

    /**
     * 服务端全量同步进行中 (HTTP 409), 稍后重试. 用户的选择保留.
     */
    @Immutable
    data object SyncInProgress : BangumiMergeApplyOutcome()

    /**
     * 提交失败. 用户的选择保留.
     */
    @Immutable
    data class Failed(val error: LoadError) : BangumiMergeApplyOutcome()
}

/**
 * 同步已完成 (不需要继续轮询): 不在进行中, 且服务端完成过至少一次全量同步.
 * 与 [BangumiConflictChecker] 的判定一致.
 */
internal val BangumiMergeState.isSyncSettled: Boolean
    get() = !syncInProgress && lastSyncedAt != null

/**
 * @param syncPollInterval 服务端全量同步尚未完成时, 静默刷新冲突列表的间隔.
 * @param syncPollTimeout 轮询的时间上限, 超过后停止轮询 (与 [BangumiConflictChecker] 一致), 避免同步失败时无限轮询.
 * @param pollCoroutineContext 轮询协程的额外 context. 测试时传入 `StandardTestDispatcher(testScheduler)` 以用虚拟时间驱动轮询.
 */
@Stable
class BangumiMergeViewModel(
    private val syncPollInterval: Duration = 5.seconds,
    private val syncPollTimeout: Duration = 10.minutes,
    pollCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractViewModel(), KoinComponent {
    private val mergeRepository: BangumiMergeRepository by inject()
    private val conflictChecker: BangumiConflictChecker by inject()

    private sealed class LoadState {
        data object Loading : LoadState()
        data class Failed(val error: LoadError) : LoadState()
        data class Ready(
            val mergeState: BangumiMergeState,
            val groups: List<SubjectMergeConflictGroup>,
        ) : LoadState()
    }

    private val loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    private val loadTasker = MonoTasker(backgroundScope)

    /**
     * 首次订阅 [uiState] 时才开始加载 (界面短暂失去订阅时不重新拉取, 重新拉取会丢失用户已做的选择).
     */
    private val loadStarted = atomic(false)

    private val choices = MutableStateFlow<Map<BangumiConflictKey, BangumiMergeSide>>(emptyMap())
    private val isApplying = MutableStateFlow(false)
    private val applyOutcome = MutableStateFlow<BangumiMergeApplyOutcome?>(null)

    val uiState: StateFlow<BangumiMergeUiState> = combine(
        loadState, choices, isApplying, applyOutcome,
    ) { load, choices, isApplying, applyOutcome ->
        BangumiMergeUiState(
            isLoading = load is LoadState.Loading,
            loadError = (load as? LoadState.Failed)?.error,
            mergeState = (load as? LoadState.Ready)?.mergeState,
            groups = (load as? LoadState.Ready)?.groups.orEmpty(),
            choices = choices,
            isApplying = isApplying,
            applyOutcome = applyOutcome,
        )
    }.onStart {
        if (loadStarted.compareAndSet(expect = false, update = true)) {
            startLoad()
        }
    }.stateInBackground(BangumiMergeUiState.Initial)

    init {
        // 服务端全量同步尚未完成 (进行中, 或从未同步过) 时冲突列表可能不完整, 静默轮询直到同步结束或超时; 用户已做的选择保留.
        backgroundScope.launch(pollCoroutineContext) {
            loadState
                .map { (it as? LoadState.Ready)?.mergeState?.isSyncSettled == false }
                .distinctUntilChanged()
                .collectLatest { unsettled ->
                    if (!unsettled) return@collectLatest
                    // 刷新得到已完成的状态时上游发出 false, collectLatest 取消这个循环.
                    withTimeoutOrNull(syncPollTimeout) {
                        while (true) {
                            delay(syncPollInterval)
                            refreshSilently()
                        }
                    } ?: logger.warn { "Bangumi full sync did not finish within $syncPollTimeout, stop polling merge state" }
                }
        }
    }

    /**
     * 重新拉取, 清空已有选择.
     */
    fun reload() {
        choices.value = emptyMap()
        startLoad()
    }

    private fun startLoad() {
        loadTasker.launch {
            loadState.value = LoadState.Loading
            loadState.value = try {
                mergeRepository.getMergeState().toReadyState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadState.Failed(LoadError.fromException(e))
            }
        }
    }

    /**
     * 静默刷新 (不展示加载态): 成功则替换状态并去掉已消失的选择, 失败只记日志.
     */
    private suspend fun refreshSilently() {
        val ready = try {
            mergeRepository.getMergeState().toReadyState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to refresh Bangumi merge state" }
            return
        }
        replaceReadyState(ready)
    }

    private fun BangumiMergeState.toReadyState() = LoadState.Ready(this, toConflictGroups())

    private fun replaceReadyState(ready: LoadState.Ready) {
        val remainingKeys = ready.groups.flatMapTo(mutableSetOf()) { it.conflictKeys }
        loadState.value = ready
        choices.update { current -> current.filterKeys { it in remainingKeys } }
    }

    /**
     * 为单个冲突选择一侧.
     */
    fun select(key: BangumiConflictKey, side: BangumiMergeSide) {
        choices.update { it + (key to side) }
    }

    /**
     * "采用较新的": 为所有能确定较新一侧的冲突选择较新一侧, 覆盖已有选择;
     * 无法确定较新一侧的冲突 (评分行, Bangumi 侧已删除的行) 保持原状.
     */
    fun adoptNewer() {
        val ready = loadState.value as? LoadState.Ready ?: return
        choices.update { current ->
            buildMap {
                putAll(current)
                for (group in ready.groups) {
                    for (conflict in group.conflicts) {
                        val newer = conflict.newerSide ?: continue
                        put(conflict.key, newer)
                    }
                }
            }
        }
    }

    /**
     * 列头 "全选": 为所有冲突选择 [side].
     */
    fun selectAll(side: BangumiMergeSide) {
        val ready = loadState.value as? LoadState.Ready ?: return
        choices.update {
            ready.groups.flatMap { group -> group.conflictKeys }.associateWith { side }
        }
    }

    /**
     * 在 [AbstractViewModel] 的后台作用域中提交. 提交与提交成功后的收尾在 [apply] 内以 [NonCancellable] 执行,
     * 离开界面 (ViewModel 清除, 作用域取消) 不会中断已开始的提交及其收尾; 只有结果上报可能因作用域已取消而被跳过.
     * 结果通过 [BangumiMergeUiState.applyOutcome] 上报.
     */
    fun startApply() {
        backgroundScope.launch {
            apply()?.let { applyOutcome.value = it }
        }
    }

    fun clearApplyOutcome() {
        applyOutcome.value = null
    }

    /**
     * 提交全部选择. 返回 `null` 表示没有可提交的 (没有冲突 / 未全部确认 / 正在提交).
     *
     * 直接读取源状态流并用 CAS 抢占 [isApplying], 防止连点导致重复提交.
     * 成功后用服务端返回的剩余状态替换当前状态, 去掉已消失的选择, 并触发一次冲突数的强制检查.
     *
     * 提交与收尾以 [NonCancellable] 执行: 调用方 (界面的 ViewModel 作用域) 被取消时, 请求多半已到达服务端并被应用,
     * 收尾 (仓库内失效本地缓存, 这里触发冲突数检查) 必须完成, 否则收藏页与冲突数会停留在合并前的状态.
     */
    suspend fun apply(): BangumiMergeApplyOutcome? {
        val ready = loadState.value as? LoadState.Ready ?: return null
        if (ready.groups.isEmpty()) return null
        val allKeys = ready.groups.flatMap { it.conflictKeys }
        val currentChoices = choices.value
        if (!allKeys.all { it in currentChoices }) return null
        if (!isApplying.compareAndSet(expect = false, update = true)) return null
        return try {
            val resolutions = allKeys.map { key ->
                BangumiConflictResolution(key.subjectId, key.fieldType, currentChoices.getValue(key))
            }
            withContext(NonCancellable) {
                val remaining = try {
                    mergeRepository.resolve(resolutions)
                } catch (e: BangumiMergeSyncInProgressException) {
                    return@withContext BangumiMergeApplyOutcome.SyncInProgress
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext BangumiMergeApplyOutcome.Failed(LoadError.fromException(e))
                }
                val remainingReady = remaining.toReadyState()
                val remainingKeys = remainingReady.groups.flatMapTo(mutableSetOf()) { it.conflictKeys }
                replaceReadyState(remainingReady)
                conflictChecker.startCheck(force = true)
                BangumiMergeApplyOutcome.Applied(
                    mergedCount = allKeys.count { it !in remainingKeys },
                    remainingCount = remaining.conflictCount,
                )
            }
        } finally {
            isApplying.value = false
        }
    }
}
