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
        if (currentState is SubjectDetailsUIState.Ok && currentState.value.info?.subjectId == subjectId) {
            // 已经加载完成了
            return completedJob
        }
        return tasker.launch {
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
                return@launch
            }
        }
    }

    fun clear() {
        tasker.cancel()
    }

    fun reload(
        subjectId: Int,
        placeholder: SubjectInfo? = null
    ) {
        clear()
        load(subjectId, placeholder)
    }
    
    private companion object {
        private val completedJob: Job = CompletableDeferred(Unit)
    }
}

@TestOnly
fun createTestSubjectDetailsLoader(
    backgroundScope: CoroutineScope,
    subjectDetailsStateFactory: SubjectDetailsStateFactory = TestSubjectDetailsStateFactory(),
): SubjectDetailsStateLoader {
    return SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope)
}
