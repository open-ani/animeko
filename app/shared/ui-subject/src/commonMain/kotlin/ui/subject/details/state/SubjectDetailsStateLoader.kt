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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.subject.details.SubjectDetailsUIState
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.coroutines.cancellation.CancellationException

/**
 * 条目详情状态的加载壳: 把 [SubjectDetailsStateFactory] 的冷流装进
 * [SubjectDetailsUIState] 三态 (Placeholder/Ok/Err) 并管理加载任务的生命周期.
 *
 * 设计约定:
 * - [state] **恒非空**: 未加载过 = [SubjectDetailsUIState.Placeholder] (subjectId = 0).
 *   消费者只需处理三态, 不再有 "null 或 Placeholder" 的双份空态.
 * - [load] 是唯一入口: 同一条目已加载完成时默认跳过, 强制刷新传 [force]; 新任务自动
 *   取消在途任务 (MonoTasker), 调用方不需要先 [clear].
 * - 失败重试用 [retry]: 目标条目已记录在 [SubjectDetailsUIState.Err] 里, 调用方不必复述参数.
 *
 * @see SubjectDetailsState
 */
@Stable
class SubjectDetailsStateLoader(
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory,
    backgroundScope: CoroutineScope,
) {
    private val tasker = MonoTasker(backgroundScope)

    private val _state = MutableStateFlow<SubjectDetailsUIState>(Idle)
    val state: StateFlow<SubjectDetailsUIState> = _state

    /**
     * 加载 [subjectId] 的详情. 该条目已加载完成时不重复加载 (除非 [force]);
     * 在途任务 (无论哪个条目) 会被新任务取消.
     */
    fun load(
        subjectId: Int,
        placeholder: SubjectInfo? = null,
        force: Boolean = false,
    ) {
        val current = _state.value
        if (!force && current is SubjectDetailsUIState.Ok && current.value.info?.subjectId == subjectId) {
            return // 已经加载完成了
        }
        tasker.launch {
            _state.value = SubjectDetailsUIState.Placeholder(subjectId, placeholder)
            try {
                subjectDetailsStateFactory.create(subjectId, placeholder)
                    .collectLatest {
                        _state.value = SubjectDetailsUIState.Ok(it.subjectId, it)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SubjectDetailsUIState.Err(subjectId, placeholder, LoadError.fromException(e))
            }
        }
    }

    /** 加载失败后的原地重试: 目标条目取自当前 [SubjectDetailsUIState.Err], 非错误态时无操作. */
    fun retry() {
        val err = _state.value as? SubjectDetailsUIState.Err ?: return
        load(err.subjectId, err.placeholder, force = true)
    }

    /** 取消在途加载并清空状态 (回到未加载占位). 之前的实现只取消不清态, 旧条目数据会残留. */
    fun clear() {
        tasker.cancel()
        _state.value = Idle
    }

    private companion object {
        /** 未加载任何条目时的占位 (subjectId = 0 不对应真实条目). */
        private val Idle = SubjectDetailsUIState.Placeholder(subjectId = 0)
    }
}

@TestOnly
fun createTestSubjectDetailsLoader(
    backgroundScope: CoroutineScope,
    subjectDetailsStateFactory: SubjectDetailsStateFactory = TestSubjectDetailsStateFactory(),
): SubjectDetailsStateLoader {
    return SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope)
}
