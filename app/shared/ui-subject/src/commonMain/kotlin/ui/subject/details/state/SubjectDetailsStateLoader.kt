/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.state

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.search.LoadError
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.coroutines.cancellation.CancellationException

/**
 * @see SubjectDetailsState
 */
@Stable
class SubjectDetailsStateLoader(
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory,
    private val backgroundScope: CoroutineScope,
) {
    private val tasker = MonoTasker(backgroundScope)

    private val _result: MutableState<LoadState> = mutableStateOf(LoadState.Loading)
    val result: State<LoadState> = _result

    fun load(
        subjectId: Int,
        placeholder: SubjectInfo? = null
    ): Job {
        val curr = _result.value
        if (curr is LoadState.Ok && curr.value.info.subjectId == subjectId) {
            // 已经加载完成了
            return completedJob
        }
        return tasker.launch {
            withContext(Dispatchers.Main) { _result.value = LoadState.Loading }
            try {
                subjectDetailsStateFactory.create(subjectId, placeholder).collectLatest {
                    withContext(Dispatchers.Main) { _result.value = LoadState.Ok(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _result.value = LoadState.Err(
                        placeholder ?: SubjectInfo.createPlaceholder(subjectId, "", ""),
                        LoadError.fromException(e),
                    ) 
                }
                return@launch
            }
        }
    }

    fun clear() {
        tasker.cancel()
        _result.value = LoadState.Loading
    }

    fun reload(
        subjectId: Int,
        placeholder: SubjectInfo? = null
    ) {
        clear()
        load(subjectId, placeholder)
    }

    sealed class LoadState {
        data object Loading : LoadState()
        class Ok(val value: SubjectDetailsState) : LoadState()
        class Err(val placeholder: SubjectInfo, val error: LoadError) : LoadState()
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
