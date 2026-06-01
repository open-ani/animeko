/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.rating.EditableRatingState
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListUiState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/**
 * 条目详情页 UI 状态. 所有属性 null 都表示正在加载中.
 */
@Stable
class SubjectDetailsState(
    val subjectId: Int,
    val info: SubjectInfo?,
    selfCollectionTypeState: State<UnifiedCollectionType>,
    val airingLabelState: AiringLabelState,

    // 附加信息, pager
    val staffPager: Flow<PagingData<RelatedPersonInfo>>,
    val exposedStaffPager: Flow<PagingData<RelatedPersonInfo>>,
    val totalStaffCountState: State<Int?>,
    val charactersPager: Flow<PagingData<RelatedCharacterInfo>>,
    val exposedCharactersPager: Flow<PagingData<RelatedCharacterInfo>>,
    val totalCharactersCountState: State<Int?>,
    val relatedSubjectsPager: Flow<PagingData<RelatedSubjectInfo>>,
    val editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    val editableRatingState: EditableRatingState,
    val subjectProgressState: SubjectProgressState,
    val subjectCommentState: CommentState,
    val presentation: StateFlow<SubjectDetailsPresentation>, // default to placeholder
    /** TMDB 横版背景图 URL (TV 详情页 Hero 用); null = 加载中或不可用. 惰性: 无收集者时不发请求. */
    val tmdbBackdropUrlFlow: Flow<String?> = flowOf(null),
    /** TMDB 分集缩略图 (episodeId -> URL, TV 选集卡片用); 空 = 加载中或不可用. 惰性: 无收集者时不发请求. */
    val tmdbEpisodeStillsFlow: Flow<Map<Int, String>> = flowOf(emptyMap()),
    /** TMDB 分集时长 (episodeId -> 分钟, TV 选集信息行用); Bangumi 侧无此数据. 惰性同上. */
    val tmdbEpisodeRuntimesFlow: Flow<Map<Int, Int>> = flowOf(emptyMap()),
    /** TMDB 中文分集简介 (episodeId -> 简介, TV 选集卡片优先展示); Bangumi 的简介多为日文. 惰性同上. */
    val tmdbEpisodeOverviewsFlow: Flow<Map<Int, String>> = flowOf(emptyMap()),
    /**
     * TMDB 中文整部简介, 仅当 Bangumi 简介整段无中文 (全日文原文/纯英文) 时非 null,
     * UI 直接整段替换 [SubjectInfo.summary]; null = 不需要替换 / TMDB 无翻译 / 加载中 (用原文). 惰性同上.
     */
    val tmdbSummaryOverrideFlow: Flow<String?> = flowOf(null),
    /**
     * bgm.tv 简介兜底, 仅当 Ani 服务器返回的简介为空时请求 (服务端部分条目 summary 缺失).
     * 语义: null = 结果未出 (加载中); "" = 已确认 bgm.tv 也没有 (此时才轮到 TMDB 兜底);
     * 非空 = bgm.tv 的简介, 整段替代 (不合并). 惰性同上.
     */
    val bangumiSummaryFallbackFlow: Flow<String?> = flowOf(""),
    /** 各集播放进度 (episodeId -> 0..1 观看比例, TV 选集卡片进度条用); 无记录的集不含在内. */
    val playProgressFlow: Flow<Map<Int, Float>> = flowOf(emptyMap()),
) {
    private val selfCollectionTypeOrNull by selfCollectionTypeState
    val selfCollectionType by derivedStateOf { selfCollectionTypeOrNull }

    val selfCollected by derivedStateOf { this.selfCollectionType != UnifiedCollectionType.NOT_COLLECTED }

    val detailsTabLazyListState = LazyListState()
    val commentTabLazyGridState = LazyGridState()
}

@Immutable
data class SubjectDetailsPresentation(
    val subjectId: Int,
    val displayName: String,
    val episodeListUiState: EpisodeListUiState,
    val isPlaceholder: Boolean = false,
) {
    companion object {
        val Placeholder = SubjectDetailsPresentation(
            subjectId = 0,
            displayName = "",
            episodeListUiState = EpisodeListUiState.Placeholder,
            isPlaceholder = true,
        )
    }
}
