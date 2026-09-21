/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.bangumi

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.bangumi.BangumiMergeSummary
import me.him188.ani.app.data.repository.subject.BangumiMergeRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * 后台检查 Bangumi 收藏冲突 (服务端在全量同步时发现的冲突), 供主界面提示与设置入口使用.
 *
 * - 检查会请求 [BangumiMergeRepository.getSummary] (服务端顺带触发 `ensureSynced`), 在 [checkInterval] 内最多实际检查一次;
 *   [startCheck] / [check] 传 `force = true` 可绕过节流.
 * - 服务端全量同步进行中 ([BangumiMergeSummary.syncInProgress]) 或从未同步过 ([BangumiMergeSummary.lastSyncedAt] 为 `null`,
 *   例如首次绑定刚刚开始同步) 时, 每 [syncPollInterval] 轮询一次, 直到同步完成或超过 [syncPollTimeout];
 *   超时后采用最后一次拿到的 (未完成的) 摘要发布计数, 但不失效缓存.
 * - 检查失败只记日志, 不改变已知结果, 也不消耗节流窗口 (下次触发会重试).
 * - 确认服务端完成了一次新的全量同步 (自动合并的结果已写入服务端) 时调用 [SubjectCollectionRepository.invalidateAllCaches],
 *   使本地收藏缓存下次刷新. 条件: 发布的摘要已完成 (不在进行中且有同步时间), 且 (已知的 [lastSyncedAt] 发生变化,
 *   或进程内曾观察到同步未完成 (进行中 / 从未同步, 例如首次绑定) 而尚未看到它完成).
 *   "曾观察到未完成" 是 checker 级状态 ([pendingInvalidation]), 跨检查保留: 轮询超时、被 `force` 取消重启的检查都不会丢掉它,
 *   直到之后某次检查看到同步完成并失效一次, 或 [reset]. 同步还没完成时不能拉半成品, 所以超时发布的摘要不失效.
 *   冷启动时直接拿到已完成的摘要不失效: 进程内还不知道上次的同步时间, 不能据此断定服务端有新同步, 否则每次启动都会让全部缓存过期.
 *
 * 这是一个全局单例, 生命周期与 APP 相同; 会话失效或解绑 Bangumi 时调用 [reset].
 *
 * @param parentCoroutineContext 后台检查任务的父 context. 测试时可传入 `TestScope` 的 context 以使用虚拟时间.
 * @param getCurrentTimeMillis 节流用的时钟, 可注入.
 */
class BangumiConflictChecker(
    private val mergeRepository: BangumiMergeRepository,
    private val subjectCollectionRepository: SubjectCollectionRepository,
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val getCurrentTimeMillis: () -> Long = { currentTimeMillis() },
    private val checkInterval: Duration = 1.hours,
    private val syncPollInterval: Duration = 5.seconds,
    private val syncPollTimeout: Duration = 10.minutes,
) {
    private val scope = CoroutineScope(
        parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]) + CoroutineName("BangumiConflictChecker"),
    )

    /**
     * 串行化 [check]: 同一时刻只有一个检查在进行. 后来的调用等待前者完成后再判断节流.
     */
    private val checkMutex = Mutex()

    private val _conflictCount = MutableStateFlow(0)

    /**
     * 最近一次成功检查得到的待处理冲突字段总数 (所有条目 fields.size 之和). 未检查过或没有冲突时为 `0`.
     *
     * @see clearConflicts
     */
    val conflictCount: StateFlow<Int> = _conflictCount.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow<Instant?>(null)

    /**
     * 最近一次成功检查得到的服务端上次全量同步完成时间. 未检查过或服务端从未同步过时为 `null`.
     */
    val lastSyncedAt: StateFlow<Instant?> = _lastSyncedAt.asStateFlow()

    /**
     * 上次成功检查的时刻 ([getCurrentTimeMillis]); `null` 表示从未成功检查过, 不参与节流比较.
     */
    private val lastCheckTime = atomic<Long?>(null)

    /**
     * 上次成功检查的结果. 节流跳过时 [check] 直接返回它.
     */
    private val lastSummary = atomic<BangumiMergeSummary?>(null)

    /**
     * [startCheck] 启动的后台任务.
     */
    private val backgroundJob = atomic<Job?>(null)

    /**
     * 进程内曾观察到同步未完成 (进行中 / 从未同步), 且之后还没有看到已完成的摘要.
     * 看到已完成的摘要并失效缓存后清除; [reset] 时清除. 跨检查保留, 见类注释.
     */
    private val pendingInvalidation = atomic(false)

    /**
     * 在后台启动一次检查, 立即返回.
     *
     * - `force = false`: [checkInterval] 内已成功检查过, 或已有后台检查在进行时, 不做任何事.
     * - `force = true`: 取消进行中的后台检查并立即重新检查.
     *
     * 结果通过 [conflictCount] 与 [lastSyncedAt] 发布.
     */
    fun startCheck(force: Boolean = false) {
        if (!force) {
            if (isThrottled()) return
            val running = backgroundJob.value
            if (running != null && running.isActive) return
        }
        val job = scope.launch { check(force) }
        val previous = backgroundJob.getAndSet(job)
        if (force) {
            previous?.cancel()
        }
    }

    /**
     * 执行一次检查并等待完成 (包括全量同步进行中时的轮询).
     *
     * @param force 为 `false` 时受 [checkInterval] 节流: 节流跳过时不请求服务端, 直接返回上次成功检查的结果 (可能为 `null`).
     * @return 检查得到的摘要; 请求失败时返回 `null` (已记录日志; [conflictCount] 等保持不变, 也不消耗节流窗口).
     */
    suspend fun check(force: Boolean = false): BangumiMergeSummary? = checkMutex.withLock {
        if (!force && isThrottled()) {
            return@withLock lastSummary.value
        }
        val summary = fetchSettledSummary() ?: return@withLock null
        publish(summary)
        lastCheckTime.value = getCurrentTimeMillis()
        summary
    }

    /**
     * 会话失效或解绑 Bangumi 时调用: 取消进行中的检查并清空一切状态.
     *
     * 否则上一个账号的冲突数会继续驱动 UI, 且节流会压制新账号的首次检查.
     */
    fun reset() {
        // 保留引用, 使 joinCheck 能等待取消完成; 已取消的 job 不 active, 不影响下次 startCheck
        backgroundJob.value?.cancel()
        lastCheckTime.value = null
        lastSummary.value = null
        _conflictCount.value = 0
        _lastSyncedAt.value = null
        pendingInvalidation.value = false
    }

    /**
     * 用户已跳转到合并界面处理: 清空计数, 冲突是否仍存在由下次检查重新判定,
     * 避免用户处理完返回后仍被过期计数重复提示. 不影响节流与 [lastSyncedAt].
     */
    fun clearConflicts() {
        _conflictCount.value = 0
    }

    /**
     * 等待 [startCheck] 启动的后台检查完成. 仅测试用.
     */
    @TestOnly
    suspend fun joinCheck() {
        backgroundJob.value?.join()
    }

    private fun isThrottled(): Boolean {
        val last = lastCheckTime.value ?: return false
        return getCurrentTimeMillis() - last < checkInterval.inWholeMilliseconds
    }

    /**
     * 请求摘要; 若服务端全量同步进行中或从未同步过, 则记下 [pendingInvalidation] 并轮询直到同步完成或超时.
     *
     * @return 请求失败时 `null`; 超时时返回最后一次拿到的 (未完成的) 摘要.
     */
    private suspend fun fetchSettledSummary(): BangumiMergeSummary? {
        var summary = getSummaryOrNull() ?: return null
        if (summary.isSyncSettled) return summary

        // 进入轮询前就记下: 之后的取消 / 超时 / 失败都不会丢掉这次观察, 由之后看到完成的检查失效缓存.
        pendingInvalidation.value = true

        var failed = false
        val finished = withTimeoutOrNull(syncPollTimeout) {
            while (!summary.isSyncSettled) {
                delay(syncPollInterval)
                val next = getSummaryOrNull()
                if (next == null) {
                    failed = true
                    break
                }
                summary = next
            }
        }
        if (failed) return null
        if (finished == null) {
            logger.warn { "Bangumi full sync did not finish within $syncPollTimeout, using the latest summary: $summary" }
        }
        return summary
    }

    /**
     * 同步已完成 (不需要继续轮询).
     */
    private val BangumiMergeSummary.isSyncSettled: Boolean
        get() = !syncInProgress && lastSyncedAt != null

    private suspend fun getSummaryOrNull(): BangumiMergeSummary? {
        return try {
            mergeRepository.getSummary()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 后台检查, 失败不打扰用户
            logger.warn(e) { "Failed to check Bangumi merge conflicts" }
            null
        }
    }

    private suspend fun publish(summary: BangumiMergeSummary) {
        val previousSyncedAt = _lastSyncedAt.value
        lastSummary.value = summary
        _lastSyncedAt.value = summary.lastSyncedAt
        _conflictCount.value = summary.conflictCount

        // 只有确认服务端完成了一次新的全量同步才失效缓存: 摘要已完成, 且 (已知上次同步时间且它变了, 或进程内曾观察到同步未完成).
        // 轮询超时发布的摘要还未完成: 只发布计数, 不失效 (服务端还在写, 不能拉半成品), 观察记录保留到之后看到完成的检查.
        // 冷启动 (进程内还不知道同步时间) 直接拿到已完成的摘要时不失效, 本地缓存按正常过期刷新即可.
        val serverSynced = summary.isSyncSettled &&
            ((previousSyncedAt != null && summary.lastSyncedAt != previousSyncedAt) || pendingInvalidation.value)
        if (serverSynced) {
            pendingInvalidation.value = false
            // 自动合并可能修改了服务端上的收藏, 本地缓存需要刷新
            try {
                subjectCollectionRepository.invalidateAllCaches()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to invalidate subject collection caches after Bangumi sync" }
            }
        }
    }

    private companion object {
        private val logger = logger<BangumiConflictChecker>()
    }
}
