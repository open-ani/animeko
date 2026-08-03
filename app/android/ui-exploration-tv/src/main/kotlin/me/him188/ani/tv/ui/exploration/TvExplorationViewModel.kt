/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.exploration

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.data.network.BangumiSummaryService
import me.him188.ani.app.data.network.RecommendationRepository
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.TrendsRepository
import me.him188.ani.app.data.network.newestAiredDateStringOrNull
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.tv.ui.foundation.widgets.TV_HERO_MEDIA_DEBOUNCE_MILLIS
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

/** 探索页 hero 区当前展示的条目 (聚焦卡/轮播驱动). */
data class TvHeroSubject(
    val subjectId: Int,
    val title: String,
    val imageUrl: String,
)

/**
 * TV 探索页薄 VM (atv-architecture.md §7.1).
 *
 * Hero 数据加载模式对齐上游 PR: 聚焦换卡先立即换标题, Bangumi 完整信息与 TMDB backdrop
 * 异步跟上; 每个条目的结果都缓存 (mutableStateMap), 回焦即时显示. 防抖 300ms —— 焦点在
 * 卡片间快速划过时不发请求.
 */
@Stable
class TvExplorationViewModel : AbstractViewModel(), KoinComponent {
    private val trendsRepository: TrendsRepository by inject()
    private val recommendationRepository: RecommendationRepository by inject()
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val tmdbImageService: TmdbImageService by inject()
    private val bangumiSummaryService: BangumiSummaryService by inject()

    private val _trends = MutableStateFlow(emptyList<TrendingSubjectInfo>())
    val trends: StateFlow<List<TrendingSubjectInfo>> = _trends.asStateFlow()

    val recommendations: Flow<PagingData<RecommendedItemInfo>> =
        recommendationRepository.recommendedSubjectsPager().cachedIn(backgroundScope)

    /** subjectId -> Bangumi 完整条目信息 (评分/连载/简介); 聚焦时异步拉取并缓存. */
    val infoCache = mutableStateMapOf<Int, SubjectCollectionInfo>()

    /** subjectId -> TMDB backdrop URL (null = 已查过但没有; 请求失败不缓存, 下次聚焦重试). */
    val backdropCache = mutableStateMapOf<Int, String?>()

    /** subjectId -> bgm.tv 简介兜底 (Ani 服务器部分条目 summary 为空; "" = 也没有). */
    val summaryFallbackCache = mutableStateMapOf<Int, String>()

    private val heroTarget = MutableStateFlow<TvHeroSubject?>(null)

    fun setFocusedSubject(subject: TvHeroSubject) {
        heroTarget.value = subject
    }

    init {
        backgroundScope.launch {
            // 错误静默重试 (PR 结论, §7.1)
            while (_trends.value.isEmpty() && isActive) {
                runCatching { trendsRepository.getTrendsInfo() }
                    .onSuccess { _trends.value = it.subjects }
                    .onFailure {
                        logger.warn(it) { "Failed to load trends, retrying in 5s" }
                        delay(5.seconds)
                    }
            }
        }

        backgroundScope.launch {
            heroTarget.filterNotNull().collectLatest { target ->
                var info = infoCache[target.subjectId]
                if (info == null) {
                    delay(TV_HERO_MEDIA_DEBOUNCE_MILLIS) // 防抖: 快速划过不发请求
                    // 慢网络就等 (换卡时 collectLatest 取消, 等待无害); 真实异常才放弃, 下次聚焦重试
                    info = try {
                        subjectCollectionRepository.subjectCollectionFlow(target.subjectId).first()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    } ?: return@collectLatest
                    infoCache[target.subjectId] = info
                }
                // 整部 backdrop (TMDB 横版); 已查过 (含"确认没有") 不重查
                if (target.subjectId !in backdropCache) {
                    runCatching {
                        tmdbImageService.getBackdropUrl(
                            target.subjectId,
                            info.subjectInfo.name,
                            activeAsOfDate = info.episodes.newestAiredDateStringOrNull(),
                        )
                    }.onSuccess { url -> backdropCache[target.subjectId] = url }
                }
                // Ani 服务器简介为空时直连 bgm.tv 补 (放在 backdrop 之后, 不拖慢背景图)
                if (info.subjectInfo.summary.isBlank() && target.subjectId !in summaryFallbackCache) {
                    runCatching { bangumiSummaryService.getSummary(target.subjectId) }
                        .onSuccess { summaryFallbackCache[target.subjectId] = it.orEmpty() }
                }
            }
        }
    }

    private companion object {
        private val logger = logger<TvExplorationViewModel>()
    }
}
