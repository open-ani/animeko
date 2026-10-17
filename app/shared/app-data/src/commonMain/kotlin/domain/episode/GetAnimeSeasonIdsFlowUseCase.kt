/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import me.him188.ani.app.data.models.schedule.AnimeSeasonId
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.domain.usecase.UseCase
import kotlin.coroutines.CoroutineContext

/**
 * 提供可浏览的季度列表 (按时间降序, 最新在前).
 *
 * 服务端不保证返回顺序, 因此这里统一排序; 调用方可按需使用全部列表 (如搜索页的年份筛选),
 * 或取 [List.first] 作为最新季度. 数据经 [AnimeScheduleRepository] 获取.
 */
fun interface GetAnimeSeasonIdsFlowUseCase : UseCase {
    operator fun invoke(): Flow<List<AnimeSeasonId>>

    companion object {
        /**
         * 将季度列表按时间降序排列 (最新在前).
         */
        fun sorted(seasons: List<AnimeSeasonId>): List<AnimeSeasonId> = seasons.sortedDescending()
    }
}

class GetAnimeSeasonIdsFlowUseCaseImpl(
    private val animeScheduleRepository: AnimeScheduleRepository,
    private val defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : GetAnimeSeasonIdsFlowUseCase {
    override fun invoke(): Flow<List<AnimeSeasonId>> =
        flow { emit(GetAnimeSeasonIdsFlowUseCase.sorted(animeScheduleRepository.getSeasonIds())) }
            .flowOn(defaultDispatcher)
}
