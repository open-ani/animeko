/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.trends

import androidx.compose.runtime.Stable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.trending.TrendingRankingItemInfo
import me.him188.ani.app.data.network.TrendsRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.exploration.search.SubjectPreviewItemInfo
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class TrendingRankingViewModel(private val koin: Koin = GlobalKoin) : AbstractViewModel(), KoinComponent {
    override fun getKoin(): Koin = koin

    private val trendsRepository: TrendsRepository by inject()
    private val settingsRepository: SettingsRepository by inject()

    private val nsfwModeFlow = settingsRepository.uiSettings.flow
        .map { it.searchSettings.nsfwMode }
        .distinctUntilChanged()

    val items: Flow<PagingData<TrendingRankingItemPresentation>> = combine(
        trendsRepository.trendingRankingPager(),
        nsfwModeFlow,
    ) { data, nsfwMode ->
        data
            .filter { nsfwMode != NsfwMode.HIDE || !it.subject.subjectInfo.nsfw }
            .map { it.toPresentation(nsfwMode) }
    }.cachedIn(backgroundScope)

    private suspend fun TrendingRankingItemInfo.toPresentation(nsfwMode: NsfwMode): TrendingRankingItemPresentation {
        return TrendingRankingItemPresentation(
            rank = rank,
            heat = heat,
            subject = SubjectPreviewItemInfo.compute(
                subject.subjectInfo,
                subject.mainEpisodeCount,
                nsfwModeSettings = nsfwMode,
                relatedPersonList = subject.lightSubjectRelations.lightRelatedPersonInfoList,
                characters = subject.lightSubjectRelations.lightRelatedCharacterInfoList,
            ),
        )
    }
}
