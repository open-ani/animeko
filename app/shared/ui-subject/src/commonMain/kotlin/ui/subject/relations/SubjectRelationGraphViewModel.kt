/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.relations

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.data.models.subject.SubjectRelationGraph
import me.him188.ani.app.data.repository.subject.SubjectRelationGraphRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 系列关系图页面的状态. [graph] 和 [error] 均为 `null` 表示加载中.
 */
@Immutable
data class SubjectRelationGraphUiState(
    val graph: SubjectRelationGraph?,
    val error: LoadError?,
) {
    companion object {
        val Loading = SubjectRelationGraphUiState(graph = null, error = null)
    }
}

class SubjectRelationGraphViewModel(
    val subjectId: Int,
) : AbstractViewModel(), KoinComponent {
    private val repository: SubjectRelationGraphRepository by inject()
    private val restarter = FlowRestarter()

    val state: StateFlow<SubjectRelationGraphUiState> = repository.subjectRelationGraphFlow(subjectId)
        .map { SubjectRelationGraphUiState(it, error = null) }
        .onStart { emit(SubjectRelationGraphUiState.Loading) }
        .catch { emit(SubjectRelationGraphUiState(graph = null, error = LoadError.fromException(it))) }
        .restartable(restarter)
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5000), SubjectRelationGraphUiState.Loading)

    fun retry() {
        restarter.restart()
    }
}
