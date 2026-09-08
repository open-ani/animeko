/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import me.him188.ani.app.data.models.schedule.AnimeSeason
import me.him188.ani.app.data.models.schedule.AnimeSeasonId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 服务端不保证季度列表顺序, UseCase 统一按时间降序 (最新在前).
 */
class GetAnimeSeasonIdsFlowUseCaseTest {
    @Test
    fun `sorted puts latest season first`() {
        val seasons = listOf(
            AnimeSeasonId(2025, AnimeSeason.SPRING),
            AnimeSeasonId(2026, AnimeSeason.WINTER),
            AnimeSeasonId(2025, AnimeSeason.AUTUMN),
        )

        assertEquals(
            listOf(
                AnimeSeasonId(2026, AnimeSeason.WINTER),
                AnimeSeasonId(2025, AnimeSeason.AUTUMN),
                AnimeSeasonId(2025, AnimeSeason.SPRING),
            ),
            GetAnimeSeasonIdsFlowUseCase.sorted(seasons),
        )
    }

    @Test
    fun `sorted keeps empty list empty`() {
        assertEquals(emptyList(), GetAnimeSeasonIdsFlowUseCase.sorted(emptyList()))
    }
}
