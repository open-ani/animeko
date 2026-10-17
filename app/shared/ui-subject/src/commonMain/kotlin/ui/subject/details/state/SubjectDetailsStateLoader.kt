/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.state

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.subject.details.SubjectDetailsLoadState
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 按需加载条目详情. [state] 由当前请求的条目派生, 只要 loader 所在的 scope 活着, 当前条目的 [SubjectDetailsState] 就一直在更新.
 * 未加载时为 subjectId = 0 的 [SubjectDetailsLoadState.Placeholder]; 失败可通过 [retry] 重试.
 *
 * @see SubjectDetailsState
 */
@Stable
class SubjectDetailsStateLoader(
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory,
    backgroundScope: CoroutineScope,
) {
    /**
     * @param attempt 让同一条目的重新加载也能触发 [flatMapLatest].
     */
    private data class Request(
        val subjectId: Int,
        val placeholder: SubjectInfo?,
        val attempt: Int,
    )

    private val request = MutableStateFlow<Request?>(null)

    /**
     * 当前请求的加载状态. 没有请求时为 subjectId = 0 的占位状态.
     *
     * [SubjectDetailsState] 内部的 flow 都跑在加载它的协程里, 因此这里用 [flatMapLatest] 让它与请求绑定:
     * 换条目或重新加载时取消上一个, 其余时候持续收集, 数据库里的更新 (例如播放页标记看过) 才能一直传到页面上.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SubjectDetailsLoadState> = request
        .flatMapLatest { req ->
            if (req == null) return@flatMapLatest flowOf(Idle)
            flow<SubjectDetailsLoadState> {
                emit(SubjectDetailsLoadState.Placeholder(req.subjectId, req.placeholder))
                emitAll(
                    subjectDetailsStateFactory.create(req.subjectId, req.placeholder)
                        .map { SubjectDetailsLoadState.Ok(it.subjectId, it) },
                )
            }.catch { e ->
                emit(SubjectDetailsLoadState.Err(req.subjectId, req.placeholder, LoadError.fromException(e)))
            }
        }
        .stateIn(backgroundScope, SharingStarted.Eagerly, Idle)

    /**
     * 确保 [subjectId] 已加载. 已在加载或已加载完成时跳过, 上次加载失败则重试; [force] 强制重新加载.
     *
     * 从播放页等返回时会再次调用, 此时不应该重新加载: 页面的内容会闪一下占位, 角色/制作人员/评论等请求也会全部重发.
     */
    fun load(
        subjectId: Int,
        placeholder: SubjectInfo? = null,
        force: Boolean = false,
    ) {
        if (!force && request.value?.subjectId == subjectId && state.value !is SubjectDetailsLoadState.Err) {
            return
        }
        request.value = Request(subjectId, placeholder, nextAttempt())
    }

    /** 加载失败后的原地重试: 目标条目取自当前 [SubjectDetailsLoadState.Err], 非错误态时无操作. */
    fun retry() {
        val err = state.value as? SubjectDetailsLoadState.Err ?: return
        load(err.subjectId, err.placeholder, force = true)
    }

    /** 取消加载并回到未加载占位状态. */
    fun clear() {
        request.value = null
    }

    private companion object {
        /** 未加载任何条目时的占位 (subjectId = 0 不对应真实条目). */
        private val Idle = SubjectDetailsLoadState.Placeholder(subjectId = 0)
    }

    private fun nextAttempt(): Int = (request.value?.attempt ?: 0) + 1
}

@TestOnly
fun createTestSubjectDetailsLoader(
    backgroundScope: CoroutineScope,
    subjectDetailsStateFactory: SubjectDetailsStateFactory = TestSubjectDetailsStateFactory(),
): SubjectDetailsStateLoader {
    return SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope)
}
