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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.subject.BangumiMergeRepository
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeConflictKey
import me.him188.ani.app.domain.bangumi.merge.BangumiMergePlan
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeResolution
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeSide
import me.him188.ani.app.domain.bangumi.merge.conflictKeys
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 合并收藏 (Bangumi 冲突处理) 界面状态.
 *
 * @see me.him188.ani.app.domain.bangumi.merge.BangumiMergePlanComputer
 */
@Immutable
data class BangumiMergeUiState(
    /**
     * 正在拉取远端状态并计算合并计划.
     */
    val isLoading: Boolean,
    /**
     * 计算合并计划失败.
     */
    val loadError: LoadError?,
    /**
     * 合并计划. 加载完成前为 `null`.
     */
    val plan: BangumiMergePlan?,
    /**
     * 用户当前的选择.
     */
    val choices: Map<BangumiMergeConflictKey, BangumiMergeSide>,
    /**
     * 正在应用合并.
     */
    val isApplying: Boolean,
    /**
     * 合并已成功应用.
     */
    val applied: Boolean,
    /**
     * 应用合并失败的错误, 由 UI 展示后调用 [BangumiMergeViewModel.clearApplyError] 清除.
     */
    val applyError: LoadError? = null,
) {
    val totalConflictCount: Int get() = plan?.totalConflictCount ?: 0

    val confirmedCount: Int
        get() = plan?.conflictGroups
            ?.sumOf { group -> group.conflictKeys.count { it in choices } }
            ?: 0

    /**
     * 全部冲突都已选择, 可以应用合并.
     */
    val allResolved: Boolean get() = plan != null && confirmedCount == totalConflictCount

    /**
     * 没有任何冲突与自动合并 (两侧已完全一致).
     */
    val isFullySynced: Boolean get() = plan?.isEmpty == true

    companion object {
        val Initial = BangumiMergeUiState(
            isLoading = true,
            loadError = null,
            plan = null,
            choices = emptyMap(),
            isApplying = false,
            applied = false,
        )
    }
}

@Stable
class BangumiMergeViewModel : AbstractViewModel(), KoinComponent {
    private val mergeRepository: BangumiMergeRepository by inject()

    private val reloader = FlowRestarter()

    private sealed class PlanLoadState {
        data object Loading : PlanLoadState()
        data class Failed(val error: LoadError) : PlanLoadState()
        data class Ready(val plan: BangumiMergePlan) : PlanLoadState()
    }

    private val planLoadFlow: Flow<PlanLoadState> = flow {
        emit(PlanLoadState.Loading)
        try {
            emit(PlanLoadState.Ready(mergeRepository.computeMergePlan()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(PlanLoadState.Failed(LoadError.fromException(e)))
        }
    }.restartable(reloader)

    // Lazily: 只计算一次, 界面短暂失去订阅时不重新拉取 (重新拉取会丢失用户已做的选择).
    private val planLoadState: StateFlow<PlanLoadState> =
        planLoadFlow.stateIn(backgroundScope, SharingStarted.Lazily, PlanLoadState.Loading)

    private val choices = MutableStateFlow<Map<BangumiMergeConflictKey, BangumiMergeSide>>(emptyMap())
    private val isApplying = MutableStateFlow(false)
    private val applied = MutableStateFlow(false)
    private val applyError = MutableStateFlow<LoadError?>(null)

    val uiState: StateFlow<BangumiMergeUiState> = combine(
        planLoadState, choices, isApplying, applied, applyError,
    ) { planLoad, choices, isApplying, applied, applyError ->
        BangumiMergeUiState(
            isLoading = planLoad is PlanLoadState.Loading,
            loadError = (planLoad as? PlanLoadState.Failed)?.error,
            plan = (planLoad as? PlanLoadState.Ready)?.plan,
            choices = choices,
            isApplying = isApplying,
            applied = applied,
            applyError = applyError,
        )
    }.stateInBackground(BangumiMergeUiState.Initial)

    /**
     * 重新拉取并计算合并计划, 清空已有选择.
     */
    fun reload() {
        choices.value = emptyMap()
        reloader.restart()
    }

    /**
     * 为单个冲突选择一侧.
     */
    fun select(key: BangumiMergeConflictKey, side: BangumiMergeSide) {
        choices.update { it + (key to side) }
    }

    /**
     * "采用较新的": 为所有能确定较新一侧的冲突选择较新一侧, 覆盖已有选择;
     * 无法确定较新一侧的冲突保持原状.
     */
    fun adoptNewer() {
        val plan = uiState.value.plan ?: return
        choices.update { current ->
            buildMap {
                putAll(current)
                for (group in plan.conflictGroups) {
                    for (conflict in group.conflicts) {
                        val newer = conflict.newerSide ?: continue
                        put(BangumiMergeConflictKey(group.subjectId, conflict.id), newer)
                    }
                }
            }
        }
    }

    /**
     * 列头 "全选": 为所有冲突选择 [side].
     */
    fun selectAll(side: BangumiMergeSide) {
        val plan = uiState.value.plan ?: return
        choices.update {
            plan.conflictGroups.flatMap { group -> group.conflictKeys }.associateWith { side }
        }
    }

    /**
     * 在 [AbstractViewModel] 的后台作用域中应用合并: 离开界面或 Activity 重建不会中断已开始的提交序列.
     * 失败通过 [BangumiMergeUiState.applyError] 上报.
     */
    fun startApply() {
        backgroundScope.launch {
            apply()?.let { applyError.value = it }
        }
    }

    fun clearApplyError() {
        applyError.value = null
    }

    /**
     * 应用合并. 返回 `null` 表示成功 (或无需/不能应用).
     *
     * 直接读取源状态流并用 CAS 抢占 [isApplying], 防止连点导致重复提交.
     */
    suspend fun apply(): LoadError? {
        val plan = (planLoadState.value as? PlanLoadState.Ready)?.plan ?: return null
        val resolution = BangumiMergeResolution(choices.value)
        val allResolved = plan.conflictGroups.flatMap { it.conflictKeys }.all { it in resolution.choices }
        if (!allResolved || applied.value) return null
        if (!isApplying.compareAndSet(expect = false, update = true)) return null
        return try {
            mergeRepository.applyMerge(plan, resolution)
            applied.value = true
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadError.fromException(e)
        } finally {
            isApplying.value = false
        }
    }
}
