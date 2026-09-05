/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.subject

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.matchToEpisodes
import me.him188.ani.app.data.network.newestAiredDateStringOrNull
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.subject.details.SubjectDetailsUIState
import me.him188.ani.app.ui.subject.details.SubjectDetailsViewModel
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusLink
import me.him188.ani.tv.ui.foundation.focus.tvFocusExit
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.widgets.TvHeroButton
import me.him188.ani.tv.ui.foundation.widgets.TvPosterCardDefaults
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvHeroSecondaryContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor
import androidx.compose.foundation.rememberScrollState as rememberDialogScrollState

/** 详情页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvDetailsFocus : TvFocusKey {
    /** Hero 播放按钮 (进页初始焦点 / 返回键回顶目标). */
    Play,

    /** 选集轮播行 (下方区块按返回的归还目标). */
    EpisodesCarousel,

    /** 选集整页区的"展开简介"按钮 (Play 与轮播之间的纵向链节点). */
    ExpandSummary,
}

/** 返回键分层的区块层级 (PR 的 backLevel 语义). */
private enum class TvDetailsBackLevel { Hero, Episodes, Below }

/*
 * TV 条目详情页. 布局对齐上游 PR#3217 的 SubjectDetailsTvPage (本实现为其首屏简化版):
 * Hero 全屏 backdrop (TMDB 三态: 未解析按有图排版等待 / TMDB 图 / 封面回退) +
 * 标题白字浮图 + 贴底信息带 (播放按钮 / 统计+连载+标签墙 / 评分直方图) + 选集剧照卡轮播.
 *
 * 未实现 (上游有): 圆钮行/选集网格菜单/标签菜单/吸附滚动.
 */
/** TMDB 横版 backdrop 三态: null = 未解析 (按有图排版等待); Resolved(url=null) = 确认无图回退封面. */
private data class TvBackdropState(val url: String?)

@Composable
fun TvSubjectDetailsScreen(
    subjectId: Int,
    placeholder: SubjectInfo?,
    onPlayEpisode: (episodeId: Int) -> Unit,
    onClickRelated: (subjectId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 状态层复用手机 SubjectDetailsViewModel (D3): info/选集列表/续播目标/角色/评论 pagers
    val viewModel = viewModel<SubjectDetailsViewModel>(key = subjectId.toString()) {
        SubjectDetailsViewModel(subjectId, placeholder)
    }
    LaunchedEffect(viewModel) { viewModel.reload() }
    val uiState by viewModel.state.collectAsState()

    // TMDB backdrop/剧照 (TV 特有, 页内加载; 匹配需 EpisodeCollectionInfo, 独立拉 collection)
    val collectionRepo = remember { GlobalKoin.get<SubjectCollectionRepository>() }
    val tmdb = remember { GlobalKoin.get<TmdbImageService>() }
    var backdropState by remember { mutableStateOf<TvBackdropState?>(null) }
    var episodeStills by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var airingCollection by remember { mutableStateOf<SubjectCollectionInfo?>(null) }
    LaunchedEffect(subjectId) {
        val collection = try {
            collectionRepo.subjectCollectionFlow(subjectId).first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return@LaunchedEffect
        airingCollection = collection
        val newest = collection.episodes.newestAiredDateStringOrNull()
        backdropState = TvBackdropState(
            runCatching {
                tmdb.getBackdropUrl(subjectId, collection.subjectInfo.name, activeAsOfDate = newest)
            }.getOrNull(),
        )
        runCatching {
            tmdb.getEpisodeStills(subjectId, collection.subjectInfo.name, "zh-CN", newestWantedAirDate = newest)
        }.onSuccess { stills ->
            episodeStills = stills.matchToEpisodes(collection.episodes)
                .mapNotNull { (id, media) -> media.stillUrl?.let { id to it } }
                .toMap()
        }
    }

    Box(modifier.fillMaxSize().background(tvShellBackgroundColor())) {
        when (val state = uiState) {
            is SubjectDetailsUIState.Placeholder ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))

            is SubjectDetailsUIState.Err -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "加载失败了, 请检查网络后重试",
                    style = MaterialTheme.typography.titleMedium,
                    color = tvHeroSecondaryContentColor(),
                )
                TvHeroButton(
                    text = "重试",
                    icon = Icons.Rounded.Refresh,
                    filled = true,
                    onClick = { viewModel.reload() },
                    onFocused = {},
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            is SubjectDetailsUIState.Ok -> TvSubjectDetailsContent(
                state.value, airingCollection, backdropState, episodeStills, onPlayEpisode, onClickRelated,
            )
        }
    }
}

/** 详情页内容: 状态接线 (焦点/返回分层/滚动) + 各区块填进 [TvSubjectDetailsPageLayout] 骨架. */
@Composable
private fun TvSubjectDetailsContent(
    details: SubjectDetailsState,
    airingCollection: SubjectCollectionInfo?,
    backdropState: TvBackdropState?,
    episodeStills: Map<Int, String>,
    onPlayEpisode: (episodeId: Int) -> Unit,
    onClickRelated: (subjectId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectInfo = details.info ?: SubjectInfo.Empty
    val presentation by details.presentation.collectAsState()
    val episodes = presentation.episodeListUiState.mainEpisodes + presentation.episodeListUiState.otherEpisodes
    // backdrop 三态 (对齐 PR): 未解析时不显示回退图, 按"有图"排版等待 (图到直接淡入零跳变)
    val heroBackdropUrl = backdropState?.url
        ?: subjectInfo.imageLarge.takeIf { backdropState != null && it.isNotBlank() }

    // 续播目标: 手机同款语义 (SubjectProgressState.episodeIdToPlay), 未就绪回退第一个未看正片
    val playTargetId = details.subjectProgressState.episodeIdToPlay
        ?: episodes.firstOrNull { !it.isDoneOrDropped }?.episodeId
        ?: episodes.firstOrNull()?.episodeId
    val playTargetSort = episodes.firstOrNull { it.episodeId == playTargetId }?.sort
    val watched = episodes.count { it.isDoneOrDropped }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    // 统一焦点框架: 进页初始焦点落播放按钮
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvDetailsFocus.Play)
    // 返回键三级分层 (PR 语义): 选集之下区块 -> 回选集; 选集 -> 回 Hero; Hero -> 退出.
    // 层级用"最后持有过焦点的区块"记忆 (各区块 onFocusChanged 上报), 不读瞬时焦点
    var backLevel by remember { mutableStateOf(TvDetailsBackLevel.Hero) }
    BackHandler(enabled = backLevel != TvDetailsBackLevel.Hero) {
        when (backLevel) {
            TvDetailsBackLevel.Below -> {
                backLevel = TvDetailsBackLevel.Episodes
                focus.request(TvDetailsFocus.EpisodesCarousel)
            }

            else -> {
                // 滚回页顶由播放钮聚焦回调统一处理
                backLevel = TvDetailsBackLevel.Hero
                focus.request(TvDetailsFocus.Play)
            }
        }
    }

    TvSubjectDetailsPageLayout(
        focus = focus,
        scrollState = scrollState,
        backdrop = {
            heroBackdropUrl?.let { url -> TvDetailsBackdrop(url, scrollState) }
        },
        modifier = modifier,
    ) { heroHeight ->
        // Play -> 展开简介 -> 选集轮播的纵向路径全显式声明: 三者相隔整屏且水平错位,
        // 空间搜索跨这种距离不可靠 (长简介条目实测 down 无法离开 Play; 轮播非首卡
        // 与上方按钮不重叠, up 是死路) —— TV 模拟器 E2E 实测结论
        TvDetailsHeroSection(
            subjectInfo = subjectInfo,
            airingCollection = airingCollection,
            playTargetId = playTargetId,
            playTargetSort = playTargetSort,
            watchedCount = watched,
            onPlayEpisode = onPlayEpisode,
            playButtonModifier = Modifier
                // 焦点落到播放钮即滚回页顶, 完整露出沉浸式封面 (焦点事件驱动; ↑ 回按钮 /
                // 返回分层回 Hero 共用这一条, 不另写滚动)
                .onFocusChanged { if (it.isFocused) scope.launch { scrollState.animateScrollTo(0) } }
                .tvFocusAnchor(focus, TvDetailsFocus.Play)
                .tvFocusLink(focus, down = TvDetailsFocus.ExpandSummary),
            modifier = Modifier.height(heroHeight),
        )
        TvDetailsEpisodesSection(
            subjectInfo = subjectInfo,
            episodes = episodes,
            episodeStills = episodeStills,
            fallbackImageUrl = heroBackdropUrl,
            pageHeight = heroHeight,
            onPlayEpisode = onPlayEpisode,
            summaryButtonModifier = Modifier
                .tvFocusAnchor(focus, TvDetailsFocus.ExpandSummary)
                .tvFocusLink(
                    focus,
                    up = TvDetailsFocus.Play,
                    down = TvDetailsFocus.EpisodesCarousel,
                ),
            carouselModifier = Modifier
                .tvFocusAnchor(focus, TvDetailsFocus.EpisodesCarousel)
                // 行内任意卡按上都回"展开简介" (出组重定向, 不依赖几何对齐)
                .tvFocusExit(focus, FocusDirection.Up to TvDetailsFocus.ExpandSummary),
            onFocusedWithin = { backLevel = TvDetailsBackLevel.Episodes },
        )
        TvDetailsBelowSections(
            details = details,
            onClickRelated = onClickRelated,
            onFocusedWithin = { backLevel = TvDetailsBackLevel.Below },
        )
    }
}

/**
 * 详情页骨架 (对齐手机 SubjectDetailsPageLayout 的 slot 模式):
 * 统一焦点接线 + 全屏 [backdrop] 背景层 + 纵向滚动内容列.
 * heroHeight = 视口高 - 16dp, 供 hero 首屏与选集整页各占一屏.
 */
@Composable
private fun TvSubjectDetailsPageLayout(
    focus: TvFocusScope,
    scrollState: ScrollState,
    backdrop: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(heroHeight: Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().tvFocusNavSignal(focus)) {
        val heroHeight = maxHeight - 16.dp
        backdrop()
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            content(heroHeight)
        }
    }
}

/** 背景层: 全屏 backdrop, 贴顶/贴右出血, 左缘 scrim + 底缘 DstOut 擦除, 随滚动淡出. */
@Composable
private fun TvDetailsBackdrop(
    url: String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val progress =
                    (scrollState.value / TvSubjectDetailsDefaults.BackdropFadeDistance.toPx()).coerceIn(0f, 1f)
                alpha = 1f - progress * (1f - TvSubjectDetailsDefaults.BackdropMinAlpha)
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                // 底部渐隐: 擦除图片自身 alpha 露出页面底色
                drawRect(
                    brush = Brush.verticalGradient(
                        0.62f to Color.Transparent,
                        0.98f to Color.Black,
                    ),
                    blendMode = BlendMode.DstOut,
                )
            },
    ) {
        AsyncImage(
            url,
            contentDescription = null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // 左侧暗色 scrim: 浮在图上的标题可读性
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.6f),
                    0.55f to Color.Transparent,
                ),
            ),
        )
    }
}

/**
 * Hero 首屏: 标题在顶, 贴底信息带 (左列播放按钮 / 中列统计+连载+标签墙 / 右列评分直方图).
 * [playButtonModifier] 由调用方注入焦点锚点.
 */
@Composable
private fun TvDetailsHeroSection(
    subjectInfo: SubjectInfo,
    airingCollection: SubjectCollectionInfo?,
    playTargetId: Int?,
    playTargetSort: EpisodeSort?,
    watchedCount: Int,
    onPlayEpisode: (episodeId: Int) -> Unit,
    playButtonModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = TvSubjectDetailsDefaults.HorizontalPadding)) {
        Column(Modifier.padding(top = 28.dp)) {
            Text(
                subjectInfo.displayName,
                Modifier.fillMaxWidth(0.55f).basicMarquee(iterations = 3),
                style = MaterialTheme.typography.headlineLarge,
                color = tvHeroContentColor(),
                maxLines = 1,
            )
            if (subjectInfo.name.isNotBlank() && subjectInfo.name != subjectInfo.displayName) {
                Text(
                    subjectInfo.name,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = tvHeroSecondaryContentColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(Modifier.weight(1f))

        // ── 贴底信息带: 左列按钮 / 中列统计+连载+标签墙 / 右列评分直方图 ──
        Row(
            Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 左列: 播放按钮 (续播目标 = 手机 SubjectProgressState.episodeIdToPlay 同款语义)
            Column(Modifier.width(210.dp)) {
                if (playTargetId != null) {
                    val label = if (watchedCount > 0 && playTargetSort != null) {
                        "继续观看 第 $playTargetSort 话"
                    } else {
                        "开始观看"
                    }
                    TvHeroButton(
                        text = label,
                        icon = Icons.Rounded.PlayArrow,
                        filled = true,
                        onClick = { onPlayEpisode(playTargetId) },
                        onFocused = {},
                        modifier = playButtonModifier,
                    )
                }
            }

            // 中列: 年月/连载 + 收藏统计 + 标签墙
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    subjectInfo.airDate.takeIf { it.isValid }?.let { date ->
                        Text(
                            "${date.year} 年 ${date.month} 月",
                            style = MaterialTheme.typography.titleMedium,
                            color = tvHeroContentColor(),
                        )
                    }
                    val latest = airingCollection?.airingInfo?.latestSort
                    val total = subjectInfo.totalEpisodes.takeIf { it > 0 }
                    val progress = buildString {
                        if (latest != null) append("连载至 $latest")
                        if (total != null) {
                            if (isNotEmpty()) append(" · ")
                            append("预定全 $total 话")
                        }
                    }
                    if (progress.isNotEmpty()) {
                        Text(
                            progress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tvHeroSecondaryContentColor(),
                        )
                    }
                }
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    val stats = subjectInfo.collectionStats
                    StatColumn(stats.collect, "收藏")
                    StatColumn(stats.doing, "在看")
                    StatColumn(stats.wish, "想看")
                }
                // 标签墙: 低透明度玻璃底 chip, 三行截断 (菜单入口未实现)
                FlowRow(
                    Modifier.padding(top = 10.dp).fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    maxLines = 3,
                ) {
                    subjectInfo.tags.take(14).forEach { tag ->
                        Text(
                            tag.name,
                            Modifier
                                .clip(TvSubjectDetailsDefaults.TagShape)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                )
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = tvHeroContentColor(),
                            maxLines = 1,
                        )
                    }
                }
            }

            // 右列: 评分直方图 + 分数
            RatingBlock(subjectInfo)
        }
    }
}

/**
 * 选集整页 (PR 布局): 标题 + 截断简介 (展开弹窗) + 右侧竖版封面 + 选集剧照卡轮播.
 * [carouselModifier] 由调用方注入焦点锚点; 区块获得焦点时经 [onFocusedWithin] 上报返回分层.
 */
@Composable
private fun TvDetailsEpisodesSection(
    subjectInfo: SubjectInfo,
    episodes: List<EpisodeListItem>,
    episodeStills: Map<Int, String>,
    fallbackImageUrl: String?,
    pageHeight: Dp,
    onPlayEpisode: (episodeId: Int) -> Unit,
    summaryButtonModifier: Modifier,
    carouselModifier: Modifier,
    onFocusedWithin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodesCoverHeight = pageHeight * 0.62f
    var summaryDialogOpen by remember { mutableStateOf(false) }
    Column(
        modifier
            .height(pageHeight)
            .onFocusChanged { if (it.hasFocus) onFocusedWithin() },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = TvSubjectDetailsDefaults.HorizontalPadding)) {
            val textEndReserve = if (subjectInfo.imageLarge.isNotBlank()) {
                episodesCoverHeight * TvPosterCardDefaults.CoverRatio + 32.dp
            } else 0.dp
            Column(
                Modifier.fillMaxSize().padding(end = textEndReserve),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    subjectInfo.displayName,
                    Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.headlineLarge,
                    color = tvHeroContentColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // 截断简介: 正文不可聚焦 (TV 惯例, 全文放显式入口后的弹窗)
                Text(
                    subjectInfo.summary.ifBlank { "暂无简介" },
                    Modifier.weight(1f).fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tvHeroSecondaryContentColor(),
                    overflow = TextOverflow.Ellipsis,
                )
                TvHeroButton(
                    text = "展开简介",
                    icon = Icons.Rounded.Refresh,
                    filled = false,
                    onClick = { summaryDialogOpen = true },
                    onFocused = {},
                    modifier = summaryButtonModifier,
                )
            }
            if (subjectInfo.imageLarge.isNotBlank()) {
                AsyncImage(
                    subjectInfo.imageLarge,
                    contentDescription = null,
                    Modifier
                        .align(Alignment.TopEnd)
                        .height(episodesCoverHeight)
                        .aspectRatio(TvPosterCardDefaults.CoverRatio)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        LazyRow(
            state = rememberLazyListState(),
            modifier = carouselModifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                start = TvSubjectDetailsDefaults.HorizontalPadding,
                end = TvSubjectDetailsDefaults.HorizontalPadding,
            ),
        ) {
            items(episodes, key = { it.episodeId }) { episode ->
                TvEpisodeCard(
                    episode = episode,
                    imageUrl = episodeStills[episode.episodeId]
                        ?: fallbackImageUrl
                        ?: subjectInfo.imageLarge,
                    onClick = { onPlayEpisode(episode.episodeId) },
                )
            }
        }
    }
    if (summaryDialogOpen) {
        AlertDialog(
            onDismissRequest = { summaryDialogOpen = false },
            title = { Text(subjectInfo.displayName) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberDialogScrollState()),
                ) {
                    Text(subjectInfo.summary, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton({ summaryDialogOpen = false }) { Text("关闭") }
            },
        )
    }
}

/**
 * 角色 / 制作人员 / 关联条目 / 评价区块 (数据来自复用的 SubjectDetailsState pagers).
 * 区块获得焦点时经 [onFocusedWithin] 上报返回分层.
 */
@Composable
private fun TvDetailsBelowSections(
    details: SubjectDetailsState,
    onClickRelated: (subjectId: Int) -> Unit,
    onFocusedWithin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(top = 8.dp, bottom = 32.dp)
            .onFocusChanged { if (it.hasFocus) onFocusedWithin() },
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val characters = details.exposedCharactersPager.collectAsLazyPagingItems()
        if (characters.itemCount > 0) {
            TvDetailsSectionHeader("角色", details.totalCharactersCountState.value)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = TvSubjectDetailsDefaults.HorizontalPadding,
                    end = TvSubjectDetailsDefaults.HorizontalPadding,
                ),
            ) {
                items(characters.itemCount, key = { characters.peek(it)?.character?.id ?: it }) { i ->
                    characters[i]?.let { TvCharacterCard(it) }
                }
            }
        }

        val staff = details.exposedStaffPager.collectAsLazyPagingItems()
        if (staff.itemCount > 0) {
            TvDetailsSectionHeader("制作人员", details.totalStaffCountState.value)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = TvSubjectDetailsDefaults.HorizontalPadding,
                    end = TvSubjectDetailsDefaults.HorizontalPadding,
                ),
            ) {
                items(staff.itemCount, key = { staff.peek(it)?.personInfo?.id ?: it }) { i ->
                    staff[i]?.let { TvStaffCard(it) }
                }
            }
        }

        val related = details.relatedSubjectsPager.collectAsLazyPagingItems()
        if (related.itemCount > 0) {
            TvDetailsSectionHeader("关联条目", null)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = TvSubjectDetailsDefaults.HorizontalPadding,
                    end = TvSubjectDetailsDefaults.HorizontalPadding,
                ),
            ) {
                items(related.itemCount, key = { related.peek(it)?.subjectId ?: it }) { i ->
                    related[i]?.let { TvRelatedSubjectCard(it, onClickRelated) }
                }
            }
        }

        val comments = details.subjectCommentState.list.collectAsLazyPagingItems()
        if (comments.itemCount > 0) {
            TvDetailsSectionHeader("评价", details.subjectCommentState.count)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = TvSubjectDetailsDefaults.HorizontalPadding,
                    end = TvSubjectDetailsDefaults.HorizontalPadding,
                ),
            ) {
                items(comments.itemCount, key = { comments.peek(it)?.stableId ?: it.toString() }) { i ->
                    comments[i]?.let { TvCommentCard(it) }
                }
            }
        }
    }
}

/** 详情页默认值/调参. */
internal object TvSubjectDetailsDefaults {
    /** 内容水平留白 (含让开侧栏收起宽; 详情页是独立目的地, 自带留白). */
    val HorizontalPadding = 48.dp

    /** backdrop 随滚动淡出的距离. */
    val BackdropFadeDistance = 400.dp

    /** backdrop 滚动淡出后的保留透明度. */
    const val BackdropMinAlpha = 0.25f

    /** 标签墙 chip 圆角. */
    val TagShape = RoundedCornerShape(6.dp)

    /** 选集剧照卡宽度 (16:9). */
    val EpisodeCardWidth = 226.dp

    /** 角色/制作人员卡宽度. */
    val PersonCardWidth = 96.dp

    /** 评价卡宽度. */
    val CommentCardWidth = 320.dp
}
