/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.subject

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * TV 条目详情页薄 VM (atv-architecture.md §7.5, M1 精简版).
 * 选集列表直接来自 [SubjectCollectionInfo.episodes], 无需单独请求.
 */
@Stable
class TvSubjectDetailsViewModel(
    val subjectId: Int,
) : AbstractViewModel(), KoinComponent {
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()

    val subject: StateFlow<SubjectCollectionInfo?> = subjectCollectionRepository
        .subjectCollectionFlow(subjectId)
        .retry(3)
        .catch { logger.warn(it) { "Failed to load subject $subjectId" } }
        .map<SubjectCollectionInfo, SubjectCollectionInfo?> { it }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object {
        private val logger = logger<TvSubjectDetailsViewModel>()
    }
}
