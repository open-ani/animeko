/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_episodes
import me.him188.ani.app.ui.lang.subject_details_info
import me.him188.ani.app.ui.lang.subject_details_related_subjects
import me.him188.ani.app.ui.lang.subject_details_summary
import me.him188.ani.app.ui.lang.subject_details_view_all
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.details.components.RelatedSubjectCard
import me.him188.ani.app.ui.subject.details.components.RelatedSubjectsLazyRow
import me.him188.ani.app.ui.subject.details.components.rememberNavigateToRelatedSubject
import me.him188.ani.app.ui.subject.details.sections.CharactersSection
import me.him188.ani.app.ui.subject.details.sections.EpisodesRow
import me.him188.ani.app.ui.subject.details.sections.SectionHeader
import me.him188.ani.app.ui.subject.details.sections.SectionHeaderActionButton
import me.him188.ani.app.ui.subject.details.sections.StaffSection
import me.him188.ani.app.ui.subject.details.sections.SubjectInfoTable
import me.him188.ani.app.ui.subject.details.sections.SubjectSummarySection
import me.him188.ani.app.ui.subject.details.sections.SubjectTagsSection
import me.him188.ani.app.ui.subject.details.sections.ViewAllSheet
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsState
import org.jetbrains.compose.resources.stringResource

/**
 * 手机 (Compact) "详情" Tab 的新内容区.
 *
 * 复用现有 Header + TabRow(用户要求手机 header 不动), 仅本 tab 内容为重写实现.
 * 区块顺序对齐 Figma 定稿手机详情 (1515:334): 选集 / 简介 / 标签 / 作品信息 / 角色 / 制作人员 / 关联作品.
 */
@Composable
internal fun CompactDetailsTabContent(
    state: SubjectDetailsState,
    info: SubjectInfo,
    onPlay: (episodeId: Int) -> Unit,
    onClickTag: (Tag) -> Unit,
    onShowEpisodeList: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalPadding: Dp = 16.dp,
) {
    val presentation by state.presentation.collectAsStateWithLifecycle()
    val episodes = presentation.episodeListUiState.mainEpisodes
    val currentEpisodeId = remember(episodes) { episodes.firstOrNull { !it.isDoneOrDropped }?.episodeId }

    val exposedCharacters = state.exposedCharactersPager.collectAsLazyPagingItemsWithLifecycle()
    val allCharacters = state.charactersPager.collectAsLazyPagingItemsWithLifecycle()
    val totalCharactersCount by state.totalCharactersCountState
    val exposedStaff = state.exposedStaffPager.collectAsLazyPagingItemsWithLifecycle()
    val allStaff = state.staffPager.collectAsLazyPagingItemsWithLifecycle()
    val totalStaffCount by state.totalStaffCountState
    val related = state.relatedSubjectsPager.collectAsLazyPagingItemsWithLifecycle()

    val horizontalPaddingValues = PaddingValues(horizontal = horizontalPadding)
    val horizontalPaddingModifier = Modifier.padding(horizontalPaddingValues)

    LazyColumn(
        modifier,
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 选集 (横滑, 不分页); header 链接 "全 N 话 · 已完结 ›" 打开完整选集列表 (定稿 1610:1003).
        // item 恒定存在 (剧集异步加载时为空): 若按条件插入, 加载完成后该 item 会出现在
        // LazyColumn 锚点 ("summary") 上方, 列表无法滚回顶部看到它 (真机复现).
        item("episodes") {
            if (episodes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        stringResource(Lang.subject_details_episodes),
                        horizontalPaddingModifier,
                    ) {
                        SectionHeaderActionButton(onShowEpisodeList) {
                            AiringLabel(
                                state.airingLabelState,
                                style = LocalTextStyle.current,
                                progressColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    EpisodesRow(
                        episodes,
                        currentEpisodeId = currentEpisodeId,
                        onEpisodeClick = { onPlay(it.episodeId) },
                        contentPadding = horizontalPaddingValues,
                    )
                }
            }
        }

        // 简介 (手机端带区块标题, 定稿 1515:334; 桌面中栏无标题)
        if (info.summary.isNotBlank()) {
            item("summary") {
                Column(
                    horizontalPaddingModifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(Lang.subject_details_summary), style = MaterialTheme.typography.titleMedium)
                    SubjectSummarySection(info.summary)
                }
            }
        }

        // 标签
        item("tags") {
            SubjectTagsSection(info.tags, onClickTag, horizontalPaddingModifier)
        }

        // 作品信息
        item("info") {
            Column(
                horizontalPaddingModifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Lang.subject_details_info), style = MaterialTheme.typography.titleMedium)
                SubjectInfoTable(info, mainEpisodeCount = episodes.size.takeIf { it > 0 })
            }
        }

        // 角色 (横向头像条, 边到边滚动; 手机为 Small 卡: 头像 56, 间距 0)
        item("characters") {
            CharactersSection(
                exposedCharacters,
                allCharacters,
                totalCharactersCount,
                contentPadding = horizontalPaddingValues,
                avatarSize = 56.dp,
                itemSpacing = 0.dp,
            )
        }

        // 制作人员 (3 列网格)
        item("staff") {
            StaffSection(
                exposedStaff,
                allStaff,
                totalStaffCount,
                horizontalPaddingModifier,
                gridColumns = 3,
            )
        }

        // 关联作品 (横滑; "查看全部" -> 封面网格 sheet)
        if (related.itemCount > 0) {
            item("related") {
                RelatedSubjectsCompactSection(
                    related,
                    headerModifier = horizontalPaddingModifier,
                    contentPadding = horizontalPaddingValues,
                )
            }
        }

        item("footer") {}
    }
}

@Composable
private fun RelatedSubjectsCompactSection(
    related: LazyPagingItems<RelatedSubjectInfo>,
    headerModifier: Modifier,
    contentPadding: PaddingValues,
) {
    val onClickRelated = rememberNavigateToRelatedSubject()
    var showAll by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(Lang.subject_details_related_subjects),
            actionLabel = stringResource(Lang.subject_details_view_all),
            onAction = { showAll = true },
            modifier = headerModifier,
        )
        RelatedSubjectsLazyRow(
            related,
            onClick = onClickRelated,
            contentPadding = contentPadding,
        )
    }
    if (showAll) {
        ViewAllSheet(
            title = stringResource(Lang.subject_details_related_subjects),
            items = related,
            onDismissRequest = { showAll = false },
            cellMinWidth = 104.dp,
        ) { item ->
            RelatedSubjectCard(item, onClick = { onClickRelated(item) })
        }
    }
}
