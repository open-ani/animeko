/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.data.repository.subject.SubjectRelationsRepository
import me.him188.ani.app.domain.usecase.UseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 获取条目的系列信息, 包括识别拆分季所需的系列里其他条目的剧集列表.
 * 系列里的条目没有本地缓存时会从网络获取, 比只用条目自己的信息计算的 [SubjectSeriesInfo.compute] 慢.
 */
fun interface GetSubjectSeriesInfoFlowUseCase : UseCase {
    operator fun invoke(subjectId: Int): Flow<SubjectSeriesInfo>
}

class GetSubjectSeriesInfoFlowUseCaseImpl : GetSubjectSeriesInfoFlowUseCase, KoinComponent {
    private val subjectRelationsRepository: SubjectRelationsRepository by inject()

    override fun invoke(subjectId: Int): Flow<SubjectSeriesInfo> =
        subjectRelationsRepository.subjectSeriesInfoFlow(subjectId)
}
