/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.subject

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.first
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.leanback.ui.foundation.focus.TvAnchoredBringIntoViewSpec
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusExit
import me.him188.ani.leanback.ui.foundation.focus.tvFocusLink
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.widgets.TvHeroButton
import me.him188.ani.leanback.ui.foundation.widgets.TvPosterCardDefaults
import me.him188.ani.leanback.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.leanback.ui.foundation.widgets.tvHeroSecondaryContentColor
import me.him188.ani.leanback.ui.foundation.widgets.tvShellBackgroundColor
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
@Composable
fun TvSubjectDetailsScreen(
    uiState: TvSubjectDetailsUiState,
    onIntent: (TvSubjectDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(tvShellBackgroundColor())) {
        when {
            uiState.content == null && uiState.error == null ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))

            uiState.error != null -> Column(
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
                    onClick = { onIntent(TvSubjectDetailsIntent.Retry) },
                    onFocused = {},
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            else -> uiState.content?.let { content ->
                TvSubjectDetailsContent(content, uiState.images, onIntent)
            }
        }
    }
}

/** 详情页内容: 状态接线 (焦点/返回分层/滚动) + 各区块填进 [TvSubjectDetailsPageLayout] 骨架. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvSubjectDetailsContent(
    details: TvSubjectDetailsContentState,
    images: TvSubjectImages,
    onIntent: (TvSubjectDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectInfo = details.info
    val episodes = details.episodes
    val airingCollection = images.collection
    val backdropState = images.backdrop
    val episodeStills = images.episodeStills
    val heroBackdropUrl = backdropState?.url
        ?: subjectInfo.imageLarge.takeIf { backdropState != null && it.isNotBlank() }
    val playTargetId = details.playTargetId
    val playTargetSort = episodes.firstOrNull { it.episodeId == playTargetId }?.sort
    val episodesLoading = details.episodesLoading
    val watched = details.watchedCount

    val scrollState = rememberScrollState()
    // 统一焦点框架: 进页初始焦点落播放按钮 (转场结束后送达, 见 InitialFocus)
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvDetailsFocus.Play)
    // 首屏之下的区块 (简介/选集/角色…) 在播放钮拿到焦点之前不组合: 进页期间页面上只有
    // 播放钮一个可聚焦节点, 任何来路的默认聚焦/杂散按键都只能落在它上面, 不可能把页面滚下去.
    // 第二屏本就在折叠线之下 (hero 整屏), 晚组合几百毫秒不可见. RESUMED 兜底防按钮永不聚焦.
    var belowFoldReady by remember { mutableStateOf(false) }
    // 首屏/简介/选集使用显式锚点; 角色及后续区块沿用页面覆盖前的默认纵向滚动策略.
    val anchors = remember { TvDetailsScrollAnchors() }
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    val bringIntoViewSpec = remember(scrollState, defaultBringIntoViewSpec) {
        TvDetailsBringIntoViewSpec(
            anchors,
            scrollOffset = { scrollState.value },
            defaultSpec = defaultBringIntoViewSpec,
        )
    }
    val sectionAnchorInsetPx = with(LocalDensity.current) { TvSubjectDetailsDefaults.SectionAnchorInset.toPx() }
    fun Modifier.scrollSection(key: String, anchorInsetPx: Float = 0f) =
        tvDetailsScrollSection(anchors, key, anchorInsetPx) { scrollState.value }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        belowFoldReady = true
    }
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
        bringIntoViewSpec = bringIntoViewSpec,
        scrollContentModifier = Modifier.onGloballyPositioned { anchors.contentTopInRoot = it.positionInRoot().y },
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
            episodesLoading = episodesLoading,
            watchedCount = watched,
            onPlay = { onIntent(TvSubjectDetailsIntent.Resume) },
            playButtonModifier = Modifier
                // 首次聚焦后放开第二屏组合 (滚回页顶由 hero 区块锚点 = 0 自然给出)
                .onFocusChanged { if (it.isFocused) belowFoldReady = true }
                .tvFocusAnchor(focus, TvDetailsFocus.Play)
                .tvFocusLink(focus, down = TvDetailsFocus.ExpandSummary),
            modifier = Modifier.height(heroHeight).scrollSection("hero"),
        )
        if (!belowFoldReady) return@TvSubjectDetailsPageLayout
        TvDetailsEpisodesSection(
            subjectInfo = subjectInfo,
            episodes = episodes,
            episodeStills = episodeStills,
            fallbackImageUrl = heroBackdropUrl,
            pageHeight = heroHeight,
            onPlayEpisode = { onIntent(TvSubjectDetailsIntent.PlayEpisode(it)) },
            summaryButtonModifier = Modifier
                .tvFocusAnchor(focus, TvDetailsFocus.ExpandSummary)
                .tvFocusLink(
                    focus,
                    up = TvDetailsFocus.Play,
                    down = TvDetailsFocus.EpisodesCarousel,
                ),
            carouselModifier = Modifier
                .tvFocusAnchor(focus, TvDetailsFocus.EpisodesCarousel)
                // 选集卡聚焦: 卡片下边缘 + 64dp 对齐视口下边缘 (底边锚点, 优先于所在第二屏的顶边锚点)
                .tvDetailsScrollSectionBottom(anchors, "carousel", sectionAnchorInsetPx)
                // 行内任意卡按上都回"展开简介" (出组重定向, 不依赖几何对齐)
                .tvFocusExit(focus, FocusDirection.Up to TvDetailsFocus.ExpandSummary),
            onFocusedWithin = { backLevel = TvDetailsBackLevel.Episodes },
            // 锚点在区块上边缘之上 64dp: 聚焦「展开简介」时封面/标题不贴屏顶 (只动锚点, 不加布局 padding)
            modifier = Modifier.scrollSection("episodes", anchorInsetPx = sectionAnchorInsetPx),
        )
        TvDetailsBelowSections(
            details = details,
            onClickRelated = { onIntent(TvSubjectDetailsIntent.OpenRelatedSubject(it)) },
            onFocusedWithin = { backLevel = TvDetailsBackLevel.Below },
        )
    }
}

/**
 * 详情页骨架 (对齐手机 SubjectDetailsPageLayout 的 slot 模式):
 * 统一焦点接线 + 全屏 [backdrop] 背景层 + 纵向滚动内容列.
 * heroHeight = 视口高 - 16dp, 供 hero 首屏与选集整页各占一屏.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvSubjectDetailsPageLayout(
    focus: TvFocusScope,
    scrollState: ScrollState,
    bringIntoViewSpec: BringIntoViewSpec,
    scrollContentModifier: Modifier,
    backdrop: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(heroHeight: Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().tvFocusNavSignal(focus)) {
        val heroHeight = maxHeight - 16.dp
        backdrop()
        // 页面只为首屏/简介/选集覆盖纵向策略, 其余区块沿用平台默认行为;
        // 列内的横向行 (选集轮播/角色/制作人员…) 各自用"对齐行首 + 留起始 padding" —— 不显式给,
        // 它们会继承列的纵向策略, 把纵向距离当横向用
        val rowStartPaddingPx = with(LocalDensity.current) { TvSubjectDetailsDefaults.HorizontalPadding.toPx() }
        val rowSpec = remember(rowStartPaddingPx) { TvAnchoredBringIntoViewSpec { rowStartPaddingPx } }
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
            Column(scrollContentModifier.fillMaxSize().verticalScroll(scrollState)) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                    content(heroHeight)
                }
            }
        }
    }
}

/**
 * 详情页滚动锚点登记: hero / 第二屏的根节点上报自己在滚动
 * 内容里的上边缘, 以及子树是否持焦; BringIntoView 就把"聚焦项所在区块的上边缘"对齐到视口
 * 上边缘 —— 锚点是布局的边, 不是 30% 这种无依据的比例. hero 的上边缘 = 0, 播放钮聚焦即回页顶.
 *
 * 区块上边缘 = 区块 positionInRoot.y - 滚动内容 positionInRoot.y + 当时的滚动偏移
 * (每次布局都重新上报, 滚动中也一致; 嵌套在子容器里的区块同样成立).
 */
@Stable
private class TvDetailsScrollAnchors {
    var contentTopInRoot by mutableFloatStateOf(0f)

    /** 顶边锚点区块: key -> 锚点在滚动内容里的位置 (区块上边缘 - 内缩量). */
    val sectionTops = mutableStateMapOf<String, Float>()

    /** 底边锚点区块: key -> 聚焦项下边缘之下的留白; 对齐目标是"聚焦项下边缘 + 留白 = 视口下边缘". */
    val bottomInsets = mutableStateMapOf<String, Float>()

    /** 各区块子树是否持焦 (嵌套区块会同时为 true; 底边区块优先, 其后取任一持焦的顶边区块). */
    val focusedKeys = mutableStateMapOf<String, Boolean>()

    fun focusedBottomInset(): Float? = bottomInsets.entries.firstOrNull { focusedKeys[it.key] == true }?.value

    fun focusedSectionTop(): Float? = sectionTops.entries.firstOrNull { focusedKeys[it.key] == true }?.value
}

/**
 * 标注本节点为滚动区块 [key] (几何上报 + 子树持焦上报; 挂在区块根节点).
 * [anchorInsetPx]: 锚点在区块上边缘之上多少 —— 对齐后区块顶与视口顶留这段距离 (锚点位置的
 * 一部分, 不是布局 padding); 0 = 区块顶贴视口顶.
 */
private fun Modifier.tvDetailsScrollSection(
    anchors: TvDetailsScrollAnchors,
    key: String,
    anchorInsetPx: Float,
    scrollOffset: () -> Int,
): Modifier = this
    .onGloballyPositioned { coords ->
        val anchor = coords.positionInRoot().y - anchors.contentTopInRoot + scrollOffset() - anchorInsetPx
        if (anchors.sectionTops[key] != anchor) anchors.sectionTops[key] = anchor
    }
    .onFocusChanged { if (anchors.focusedKeys[key] != it.hasFocus) anchors.focusedKeys[key] = it.hasFocus }

/**
 * 标注本节点为**底边锚点**区块 [key] (如选集轮播): 子树内任一项聚焦时, 滚动到"该项下边缘 + [bottomInsetPx]
 * 对齐视口下边缘" (几何直接用 BringIntoView 传来的聚焦项矩形, 无需上报). 嵌套在顶边区块内时优先.
 */
private fun Modifier.tvDetailsScrollSectionBottom(
    anchors: TvDetailsScrollAnchors,
    key: String,
    bottomInsetPx: Float,
): Modifier = this
    .onGloballyPositioned { if (anchors.bottomInsets[key] != bottomInsetPx) anchors.bottomInsets[key] = bottomInsetPx }
    .onFocusChanged { if (anchors.focusedKeys[key] != it.hasFocus) anchors.focusedKeys[key] = it.hasFocus }

/**
 * 详情页纵向滚动策略: 首屏/简介按顶边锚点, 选集卡按底边锚点.
 * 角色及后续区块不登记纵向锚点, 直接交给页面覆盖前的 [defaultSpec], 保留平台默认行为.
 */
@OptIn(ExperimentalFoundationApi::class)
private class TvDetailsBringIntoViewSpec(
    private val anchors: TvDetailsScrollAnchors,
    private val scrollOffset: () -> Int,
    private val defaultSpec: BringIntoViewSpec,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // 底边锚点 (选集卡): 聚焦项下边缘 + 留白 对齐视口下边缘
        anchors.focusedBottomInset()?.let { inset -> return offset + size + inset - containerSize }
        // 顶边锚点 (hero / 第二屏): 区块上边缘 (- 内缩) 对齐视口上边缘
        anchors.focusedSectionTop()?.let { top -> return top - scrollOffset() }
        return defaultSpec.calculateScrollDistance(offset, size, containerSize)
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
    episodesLoading: Boolean,
    watchedCount: Int,
    onPlay: () -> Unit,
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
            // 左列: 播放按钮 (续播目标 = 手机 SubjectProgressState.episodeIdToPlay 同款语义).
            // 槽位常驻可聚焦: 选集未到时以「加载中…」占位 (同一锚点), 进页初始焦点当帧落定 ——
            // 否则页面无焦点持有者, 首个按键会让 Compose 默认聚焦「展开简介」并把整页滚下去
            Column(Modifier.width(210.dp)) {
                val label = when {
                    playTargetId != null && watchedCount > 0 && playTargetSort != null ->
                        "继续观看 第 $playTargetSort 话"

                    playTargetId != null -> "开始观看"
                    episodesLoading -> "加载中…"
                    else -> "暂无剧集"
                }
                TvHeroButton(
                    text = label,
                    icon = Icons.Rounded.PlayArrow,
                    filled = playTargetId != null,
                    onClick = onPlay,
                    onFocused = {},
                    modifier = playButtonModifier,
                )
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
 * 区块获得焦点时经 [onFocusedWithin] 上报返回分层; 不设置纵向锚点, 横向行仍保留行首锚定.
 */
@Composable
private fun TvDetailsBelowSections(
    details: TvSubjectDetailsContentState,
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
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                TvDetailsSectionHeader("角色", details.totalCharactersCount)
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
        }

        val staff = details.exposedStaffPager.collectAsLazyPagingItems()
        if (staff.itemCount > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                TvDetailsSectionHeader("制作人员", details.totalStaffCount)
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
        }

        val related = details.relatedSubjectsPager.collectAsLazyPagingItems()
        if (related.itemCount > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
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
        }

        val comments = details.commentsPager.collectAsLazyPagingItems()
        if (comments.itemCount > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                TvDetailsSectionHeader("评价", details.commentCount)
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
}

/** 详情页默认值/调参. */
internal object TvSubjectDetailsDefaults {
    /** 内容水平留白 (含让开侧栏收起宽; 详情页是独立目的地, 自带留白). */
    val HorizontalPadding = 48.dp

    /**
     * 滚动锚点留白: 第二屏 (简介) 区块顶边锚点在其上边缘之上 64dp (聚焦「展开简介」时封面/标题不贴屏顶);
     * 选集卡底边锚点 = 卡片下边缘 + 64dp 对齐视口下边缘.
     */
    val SectionAnchorInset = 64.dp

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
