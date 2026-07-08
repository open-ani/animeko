/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.layout

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import coil3.compose.AsyncImagePainter
import com.kmpalette.color
import com.kmpalette.palette.graphics.Palette
import kotlinx.collections.immutable.toImmutableList
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.tools.ColorUtils
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.rating_self_score
import me.him188.ani.app.ui.lang.subject_details_episodes
import me.him188.ani.app.ui.lang.subject_details_info
import me.him188.ani.app.ui.lang.subject_details_login_to_collect
import me.him188.ani.app.ui.lang.subject_details_rate
import me.him188.ani.app.ui.lang.subject_details_rating
import me.him188.ani.app.ui.lang.subject_details_related_subjects
import me.him188.ani.app.ui.rating.EditableRatingState
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeButton
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressButton
import me.him188.ani.app.ui.subject.details.components.AnimatedGradientBackground
import me.him188.ani.app.ui.subject.details.components.COVER_WIDTH_TO_HEIGHT_RATIO
import me.him188.ani.app.ui.subject.details.components.RatingHistogram
import me.him188.ani.app.ui.subject.details.components.RelatedSubjectsGrid
import me.him188.ani.app.ui.subject.details.components.rememberNavigateToRelatedSubject
import me.him188.ani.app.ui.subject.details.sections.CharactersSection
import me.him188.ani.app.ui.subject.details.sections.HotReviewsCardContent
import me.him188.ani.app.ui.subject.details.sections.PagedEpisodesGrid
import me.him188.ani.app.ui.subject.details.sections.ReviewsPreviewSection
import me.him188.ani.app.ui.subject.details.sections.SectionHeader
import me.him188.ani.app.ui.subject.details.sections.StaffSection
import me.him188.ani.app.ui.subject.details.sections.SubjectCollectionStatsRow
import me.him188.ani.app.ui.subject.details.sections.SubjectInfoTable
import me.him188.ani.app.ui.subject.details.sections.SubjectRatingSummary
import me.him188.ani.app.ui.subject.details.sections.SubjectSummarySection
import me.him188.ani.app.ui.subject.details.sections.SubjectTagsSection
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsState
import me.him188.ani.app.ui.subject.renderSubjectSeason
import me.him188.ani.app.ui.user.SelfInfoUiState
import org.jetbrains.compose.resources.stringResource

/**
 * 桌面双栏/三栏条目详情页 (对应 Figma 定稿双栏 1505:335 / 三栏 1515:336).
 *
 * 数据沿用现有 [SubjectDetailsState] (复用其交互 state holder); 本文件只负责布局.
 * Compact(手机) 走另一条路径 (复用现有 Header + TabRow), 见 SubjectDetailsPage.
 */
@Composable
internal fun SubjectDetailsMultiColumnPage(
    state: SubjectDetailsState,
    selfInfo: SelfInfoUiState,
    layoutParams: SubjectDetailsLayoutParams,
    onPlay: (episodeId: Int) -> Unit,
    onClickTag: (Tag) -> Unit,
    onClickLogin: () -> Unit,
    onShowComments: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    backgroundPalette: Palette? = null,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
    onCoverImageSuccess: (AsyncImagePainter.State.Success) -> Unit = {},
) {
    val info = state.info ?: return
    val presentation by state.presentation.collectAsStateWithLifecycle()
    val episodes = presentation.episodeListUiState.mainEpisodes
    // "当前/下一集": 第一集未看(非 DONE/DROPPED)者, 用于选集高亮与初始分页页.
    val currentEpisodeId = remember(episodes) { episodes.firstOrNull { !it.isDoneOrDropped }?.episodeId }

    val exposedCharacters = state.exposedCharactersPager.collectAsLazyPagingItemsWithLifecycle()
    val allCharacters = state.charactersPager.collectAsLazyPagingItemsWithLifecycle()
    val totalCharactersCount by state.totalCharactersCountState
    val exposedStaff = state.exposedStaffPager.collectAsLazyPagingItemsWithLifecycle()
    val allStaff = state.staffPager.collectAsLazyPagingItemsWithLifecycle()
    val totalStaffCount by state.totalStaffCountState
    val related = state.relatedSubjectsPager.collectAsLazyPagingItemsWithLifecycle()
    val comments = state.subjectCommentState.list.collectAsLazyPagingItemsWithLifecycle()
    val commentCount = state.subjectCommentState.count

    MultiColumnScaffold(
        layoutParams,
        modifier,
        showTopBar,
        windowInsets,
        navigationIcon,
        onClickOpenExternal,
        topBarTitle = info.displayName,
        backgroundOverlay = {
            val surfaceColor = MaterialTheme.colorScheme.surface
            val colors = remember(backgroundPalette) {
                backgroundPalette?.swatches
                    ?.map { ColorUtils.blendColor(it.color, surfaceColor, 0.85f) }
                    ?.toImmutableList()
            }
            if (colors != null) {
                AnimatedGradientBackground(
                    colors,
                    speed = 0.05,
                    modifier = Modifier.fillMaxSize(),
                )
            }

        },
    ) {
        // 左侧信息栏
        SubjectSidebar(
            state = state,
            info = info,
            selfInfo = selfInfo,
            mainEpisodeCount = episodes.size,
            onPlay = onPlay,
            onClickTag = onClickTag,
            onClickLogin = onClickLogin,
            itemSpacing = layoutParams.sidebarItemSpacing,
            onCoverImageSuccess = onCoverImageSuccess,
            modifier = Modifier.width(layoutParams.sidebarWidth),
        )

        // 中栏内容流. 区块序 (定稿): 标题 → 评分 → 简介 → 选集 → 角色
        // → (双栏: 制作人员) → 关联作品 → (双栏: 评价预览)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(layoutParams.sectionSpacing),
        ) {
            SubjectTitleBlock(info, state)
            if (layoutParams.kind != SubjectDetailsPaneKind.EXPANDED) {
                SubjectRatingRow(state, showHistogram = layoutParams.showInlineRatingHistogram)
            }
            if (info.summary.isNotBlank()) {
                SubjectSummarySection(info.summary)
            }
            PagedEpisodesGrid(
                episodes = episodes,
                currentEpisodeId = currentEpisodeId,
                onEpisodeClick = { onPlay(it.episodeId) },
                header = { pager ->
                    SectionHeader(stringResource(Lang.subject_details_episodes)) {
                        // 分页时分页控件替代集数文案; 不足一页时恢复 (定稿 1610:1003)
                        pager?.invoke() ?: ProvideContentColor(MaterialTheme.colorScheme.onSurfaceVariant) {
                            AiringLabel(
                                state.airingLabelState,
                                style = MaterialTheme.typography.bodyMedium,
                                progressColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
            CharactersSection(exposedCharacters, allCharacters, totalCharactersCount)
            if (!layoutParams.showRail) {
                StaffSection(
                    exposedStaff,
                    allStaff,
                    totalStaffCount,
                    gridColumns = layoutParams.staffGridColumns,
                )
            }
            if (related.itemCount > 0) {
                SubjectRelatedBlock(related)
            }
            if (!layoutParams.showRail) {
                ReviewsPreviewSection(comments, commentCount, onShowAll = onShowComments)
            }
        }

        // 右栏 (仅三栏): 评分卡 / 热门评价卡 / 制作人员卡
        if (layoutParams.showRail) {
            Column(
                Modifier.width(layoutParams.railWidth),
                verticalArrangement = Arrangement.spacedBy(layoutParams.railItemSpacing),
            ) {
                RailCard {
                    SectionHeader(stringResource(Lang.subject_details_rating)) {
                        EditRatingButton(state.editableRatingState)
                    }
                    SubjectRatingSummary(
                        info.ratingInfo,
                        Modifier.padding(top = 8.dp),
                        scoreStyle = MaterialTheme.typography.headlineMedium,
                        onClick = { state.editableRatingState.requestEdit() },
                    )
                    RatingHistogram(info.ratingInfo, Modifier.padding(top = 16.dp))
                }
                if (comments.itemCount > 0) {
                    RailCard {
                        HotReviewsCardContent(comments, commentCount, onShowAll = onShowComments)
                    }
                }
                if (exposedStaff.itemCount > 0) {
                    RailCard {
                        StaffSection(exposedStaff, allStaff, totalStaffCount)
                    }
                }
            }
        }
    }
}

/**
 * 双栏/三栏共用的 Scaffold, 亦用于加载占位骨架.
 *
 * 整页统一滚动 (侧栏/中栏/右栏不各自滚动), 顶部留白随内容滚出;
 * 页内标题滚过顶栏后, 顶栏按 M3 规范渐显 [topBarTitle] 并加底色 (与手机版 SubjectDetailsLayout 行为一致).
 */
@Composable
private fun MultiColumnScaffold(
    layoutParams: SubjectDetailsLayoutParams,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
    topBarTitle: String? = null,
    scrollState: ScrollState = rememberScrollState(),
    backgroundOverlay: @Composable (PaddingValues) -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current
    // 页内标题位于中栏顶部 (contentTopPadding 之下); 其滚出可视区后切换到粘性标题栏.
    val stickyTopBarVisible by remember(scrollState, density, layoutParams) {
        derivedStateOf {
            scrollState.value >
                    with(density) { (layoutParams.contentTopPadding + TITLE_LINE_HEIGHT).toPx() }
        }
    }
    val topAppBarWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    val topAppBarActions: @Composable RowScope.() -> Unit = {
        IconButton(onClickOpenExternal) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
        }
    }

    Scaffold(
        modifier,
        topBar = {
            if (showTopBar) {
                Box {
                    // 透明背景的, 总是显示
                    TopAppBar(
                        title = {},
                        navigationIcon = navigationIcon,
                        actions = topAppBarActions,
                        colors = AniThemeDefaults.topAppBarColors().copy(containerColor = Color.Transparent),
                        windowInsets = topAppBarWindowInsets,
                    )
                    // 有背景和标题的, 仅在页内标题滚出后显示
                    AniAnimatedVisibility(stickyTopBarVisible && topBarTitle != null) {
                        TopAppBar(
                            title = {
                                Text(topBarTitle ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            navigationIcon = navigationIcon,
                            actions = topAppBarActions,
                            colors = AniThemeDefaults.topAppBarColors(
                                containerColor = AniThemeDefaults.navigationContainerColor,
                            ),
                            windowInsets = topAppBarWindowInsets,
                        )
                    }
                }
            }
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { scaffoldPadding ->
        backgroundOverlay(scaffoldPadding)
        Column(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(scrollState),
        ) {
            Row(
                // 留白放在滚动容器内, 随内容一起滚出
                Modifier.padding(
                    start = layoutParams.contentHorizontalPadding,
                    end = layoutParams.contentHorizontalPadding,
                    top = layoutParams.contentTopPadding,
                    bottom = layoutParams.contentBottomPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(layoutParams.columnSpacing),
                content = content,
            )
        }
    }
}

/** 页内标题 (headlineSmall) 约一行的高度, 用于估算标题滚出的时机. */
private val TITLE_LINE_HEIGHT = 32.dp

/**
 * 多栏加载占位骨架: 几何对齐 [SubjectDetailsMultiColumnPage] (侧栏封面/按钮 + 中栏标题/文本行),
 * 避免宽屏下从单列占位跳变为多栏内容.
 *
 * [subjectInfo] (导航占位) 可用时直接展示真实封面与标题.
 */
@Composable
internal fun SubjectDetailsMultiColumnPlaceholder(
    subjectInfo: SubjectInfo?,
    layoutParams: SubjectDetailsLayoutParams,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
) {
    MultiColumnScaffold(
        layoutParams,
        modifier,
        showTopBar,
        windowInsets,
        navigationIcon,
        onClickOpenExternal,
        topBarTitle = subjectInfo?.displayName,
    ) {
        Column(
            Modifier.width(layoutParams.sidebarWidth),
            verticalArrangement = Arrangement.spacedBy(layoutParams.sidebarItemSpacing),
        ) {
            val coverModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_WIDTH_TO_HEIGHT_RATIO)
                .clip(RoundedCornerShape(16.dp))
            val coverUrl = subjectInfo?.imageLarge
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(coverUrl, contentDescription = null, coverModifier, contentScale = ContentScale.Crop)
            } else {
                Spacer(coverModifier.placeholder(true))
            }
            repeat(2) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .placeholder(true),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val title = subjectInfo?.displayName
            if (!title.isNullOrBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.width(240.dp).height(32.dp).placeholder(true))
            }
            Spacer(Modifier.width(160.dp).height(20.dp).placeholder(true))
            repeat(4) {
                Spacer(Modifier.fillMaxWidth().height(16.dp).placeholder(true))
            }
        }
        if (layoutParams.showRail) {
            Column(
                Modifier.width(layoutParams.railWidth),
                verticalArrangement = Arrangement.spacedBy(layoutParams.railItemSpacing),
            ) {
                repeat(2) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .placeholder(true),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectSidebar(
    state: SubjectDetailsState,
    info: SubjectInfo,
    selfInfo: SelfInfoUiState,
    mainEpisodeCount: Int,
    onPlay: (episodeId: Int) -> Unit,
    onClickTag: (Tag) -> Unit,
    onClickLogin: () -> Unit,
    itemSpacing: Dp,
    onCoverImageSuccess: (AsyncImagePainter.State.Success) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
        // 封面: 固定海报比例 (849:1200) + 圆角 16, 对齐定稿 340×482, 不随图片本征尺寸变形.
        AsyncImage(
            info.imageLarge,
            contentDescription = null,
            Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_WIDTH_TO_HEIGHT_RATIO)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
            onSuccess = onCoverImageSuccess,
        )
        // 播放按钮 (定稿: 全宽 Filled; 无选集列表小按钮, 选集操作走中栏网格)
        SubjectProgressButton(
            state.subjectProgressState,
            onPlay = { state.subjectProgressState.episodeIdToPlay?.let(onPlay) },
            Modifier.fillMaxWidth(),
        )
        // 收藏 (定稿: 全宽 Tonal)
        if (selfInfo.isSessionValid == false) {
            OutlinedButton(onClickLogin, Modifier.fillMaxWidth()) {
                Text(stringResource(Lang.subject_details_login_to_collect))
            }
        } else {
            EditableSubjectCollectionTypeButton(
                state.editableSubjectCollectionTypeState,
                Modifier.fillMaxWidth(),
            )
        }
        // 收藏统计三格 (收藏 / 在看 / 想看)
        SubjectCollectionStatsRow(info.collectionStats)

        HorizontalDivider()

        // 作品信息
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Lang.subject_details_info), style = MaterialTheme.typography.titleMedium)
            SubjectInfoTable(info, mainEpisodeCount = mainEpisodeCount.takeIf { it > 0 })
        }

        // 标签
        SubjectTagsSection(info.tags, onClickTag)
    }
}

@Composable
private fun SubjectTitleBlock(info: SubjectInfo, state: SubjectDetailsState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            info.displayName,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (info.name.isNotBlank() && info.name != info.displayName) {
            Text(
                info.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 元数据行 (对齐定稿的内联文本, 非 chip): 季度 · 播出状态 · 总集数
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Text(
                renderSubjectSeason(info.airDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("·", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AiringLabel(
                state.airingLabelState,
                style = MaterialTheme.typography.bodyMedium,
                progressColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 评分行: 评分摘要靠左 (点击打开评分编辑); [showHistogram] 时直方图右对齐 (定宽, 定稿 274×90 Size=Large).
 */
@Composable
private fun SubjectRatingRow(state: SubjectDetailsState, showHistogram: Boolean) {
    val info = state.info ?: return
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubjectRatingSummary(
            info.ratingInfo,
            onClick = { state.editableRatingState.requestEdit() },
        )
        if (showHistogram) {
            Spacer(Modifier.weight(1f))
            RatingHistogram(info.ratingInfo, Modifier.width(RATING_HISTOGRAM_WIDTH))
        }
    }
}

private val RATING_HISTOGRAM_WIDTH = 274.dp

/**
 * 三栏右栏"评分"卡的显式打分入口: 未打分显示"打分", 已打分显示"你的评分: N"
 * (复用手机版同款 [Lang.rating_self_score] 文案); 点击打开评分编辑.
 */
@Composable
private fun EditRatingButton(editableRatingState: EditableRatingState) {
    TextButton({ editableRatingState.requestEdit() }) {
        Icon(
            Icons.Rounded.StarOutline,
            contentDescription = null,
            Modifier.size(18.dp),
        )
        val selfScore = editableRatingState.selfRatingInfo.score
        Text(
            if (selfScore > 0) {
                stringResource(Lang.rating_self_score, selfScore)
            } else {
                stringResource(Lang.subject_details_rate)
            },
            Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun SubjectRelatedBlock(related: LazyPagingItems<RelatedSubjectInfo>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(stringResource(Lang.subject_details_related_subjects))
        RelatedSubjectsGrid(related, onClick = rememberNavigateToRelatedSubject())
    }
}

@Composable
private fun RailCard(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // 对齐设计稿 rail 卡 (视觉: 标题字形距顶 ~21, 距侧 20): 卡内首行 SectionHeader
        // 的 TextButton (min 40dp) 居中 + 行框留白自带 ~17dp 顶空 (截图实测), 故 top=4.
        Column(Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 18.dp)) {
            content()
        }
    }
}
