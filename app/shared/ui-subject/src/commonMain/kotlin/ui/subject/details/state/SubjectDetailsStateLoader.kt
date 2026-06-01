/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.state

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.subject.details.SubjectDetailsUIState
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * @see SubjectDetailsState
 */
@Stable
class SubjectDetailsStateLoader(
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory,
    backgroundScope: CoroutineScope,
) {
    private val tasker = MonoTasker(backgroundScope)

    private val _state = MutableStateFlow<SubjectDetailsUIState?>(null)
    val state: StateFlow<SubjectDetailsUIState?> = _state

    fun load(
        subjectId: Int,
        placeholder: SubjectInfo? = null
    ): Job {
        val currentState = _state.value
        if (currentState is SubjectDetailsUIState.Ok && currentState.value.info?.subjectId == subjectId &&
            tasker.isRunning.value
        ) {
            // 已经加载完成, 且收集任务仍在运行 (state 内部的 scope 存活).
            // 必须检查 isRunning: 若收集已被取消 (如 clear() 后), 已展示的 state 是"僵尸" ——
            // 它的 MonoTasker 都挂在已取消的 scope 上, 收藏/评分等操作会静默失效, 必须重新加载.
            return completedJob
        }
        return tasker.launch {
            _state.value = SubjectDetailsUIState.Placeholder(subjectId, placeholder)
            // 首屏内容依赖一次 Bangumi 请求 (本地无缓存时), 而它挂住时全局 ktor 超时长达 5 分钟,
            // 表现为详情页无限转圈. 这里限制首次发射的等待时间, 超时或失败则取消本次并重试,
            // 重试若干次仍不成功才进入错误页 (可手动重试).
            var attempts = 0
            while (true) {
                attempts++
                try {
                    collectWithFirstLoadTimeout(subjectId, placeholder)
                    return@launch // 正常情况下 collect 不会自行结束, 防御性返回
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (attempts >= MAX_LOAD_ATTEMPTS) {
                        val error = if (e is FirstLoadTimeoutException) {
                            LoadError.fromException(RepositoryNetworkException("加载超时", e))
                        } else {
                            LoadError.fromException(e)
                        }
                        _state.value = SubjectDetailsUIState.Err(subjectId, placeholder, error)
                        return@launch
                    }
                    // 超时的尝试立即重发; 立刻抛错的请求稍等再试, 避免密集重试
                    if (e !is FirstLoadTimeoutException) delay(1.seconds)
                }
            }
        }
    }

    /**
     * 收集详情数据流. 首次发射超过 [FIRST_LOAD_TIMEOUT] 时取消本次收集并抛出
     * [FirstLoadTimeoutException]; 首次发射成功后持续收集, 不再限时.
     */
    private suspend fun collectWithFirstLoadTimeout(
        subjectId: Int,
        placeholder: SubjectInfo?,
    ): Unit = coroutineScope {
        val firstEmission = CompletableDeferred<Unit>()
        val collectJob = launch {
            subjectDetailsStateFactory.create(subjectId, placeholder)
                .collectLatest {
                    _state.value = SubjectDetailsUIState.Ok(it.subjectId, it)
                    firstEmission.complete(Unit)
                }
        }
        try {
            withTimeout(FIRST_LOAD_TIMEOUT) { firstEmission.await() }
        } catch (e: TimeoutCancellationException) {
            collectJob.cancelAndJoin()
            throw FirstLoadTimeoutException()
        }
        // 首屏已显示, 后续更新由 collectJob 持续收集直到外层取消
    }

    fun clear() {
        tasker.cancel()
    }

    fun reload(
        subjectId: Int,
        placeholder: SubjectInfo? = null
    ) {
        clear()
        // 清掉已展示的旧状态: 它的 scope 已随 clear() 取消, 若保留, load() 会因"已加载"跳过,
        // UI 停留在僵尸状态上 (收藏/评分等操作静默失效).
        _state.value = null
        load(subjectId, placeholder)
    }
    
    private companion object {
        private val completedJob: Job = CompletableDeferred(Unit)

        /** 首次发射 (首屏内容) 的最长等待时间, 超过则取消并重试. */
        private val FIRST_LOAD_TIMEOUT = 5.seconds

        /** 首屏加载总尝试次数 (含第一次), 全部失败后进入错误页. */
        private const val MAX_LOAD_ATTEMPTS = 5
    }
}

/** 首屏数据在 [FIRST_LOAD_TIMEOUT] 内未就绪. 不是 [CancellationException], 以便被重试逻辑捕获. */
private class FirstLoadTimeoutException : Exception("Subject details first load timed out")

@TestOnly
fun createTestSubjectDetailsLoader(
    backgroundScope: CoroutineScope,
    subjectDetailsStateFactory: SubjectDetailsStateFactory = TestSubjectDetailsStateFactory(),
): SubjectDetailsStateLoader {
    return SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope)
}
