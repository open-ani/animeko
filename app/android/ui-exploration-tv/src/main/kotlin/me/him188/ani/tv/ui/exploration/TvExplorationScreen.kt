/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.exploration

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.widgets.TvBackdropDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvLandscapeCardDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvPageDefaults
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor

/** 探索页焦点锚点 (卡片锚点见 [TvExplorationCardKey]). */
private enum class TvExplorationFocus : TvFocusKey {
    /** Hero 「更多详细内容」按钮 (进页初始焦点 / 行 0 按上的回归目标). */
    Play,
}

/*
 * TV 探索页 (v5, 交互对齐 Amazon Prime Video 实测, atv-architecture.md §7.1):
 * - backdrop 画在页面根层 (surface 背景级), 16:9 贴右上, 高度随 hero 两态 + 下探量,
 *   渐隐尾部延伸到卡片行下方; 目标防抖 500ms 后 crossfade (快速划过卡片不闪图).
 * - hero **常驻**顶部, 双态: 焦点在 hero → 展开 (0.66 屏高), 轮播最高热度条目 + 按钮 + 居中指示器;
 *   焦点在下方卡片行 → 收缩 (0.46), 展示聚焦条目信息; 按钮/指示器随同一条高度动画收放.
 * - 下方为纵向行列表: 继续观看 (横向锚定行) + 为你推荐 (纵向自适应网格, 16:9 TMDB 横图卡);
 *   按布局边缘锚定: 焦点卡顶边距列表上边界 32dp, 列表上下边缘随滚动渐隐.
 */
@OptIn(FlowPreview::class)
@Composable
fun TvExplorationScreen(
    trendsPager: LazyPagingItems<TrendingSubjectInfo>,
    recommendations: LazyPagingItems<RecommendedItemInfo>,
    followed: LazyPagingItems<FollowedSubjectInfo>,
    media: TvSubjectMediaUiState,
    onIntent: (TvExplorationIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiScope = rememberCoroutineScope()

    val carouselSize = minOf(trendsPager.itemCount, TvExplorationDefaults.CarouselMaxDots)
    var carouselIndex by rememberSaveable { mutableIntStateOf(0) }
    var carouselInteraction by remember { mutableIntStateOf(0) }
    // hero 双态: 焦点在 hero 按钮 = 展开 (轮播); 焦点在卡片行 = 收缩 (聚焦卡驱动)
    var heroFocused by remember { mutableStateOf(true) }
    var focusedCardSubject by remember { mutableStateOf<TvHeroSubject?>(null) }
    var focusedRow by remember { mutableStateOf<TvExplorationRow?>(null) }

    val columnState = rememberLazyListState()
    val rowStates = remember { mutableStateMapOf<String, LazyListState>() }
    val focusedIndexByRow = remember { mutableStateMapOf<String, Int>() }
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvExplorationFocus.Play)

    val carouselHero = (if (carouselSize > 0) {
        trendsPager[carouselIndex.coerceIn(0, carouselSize - 1)]
    } else null)
        ?.let { TvHeroSubject(it.bangumiId, it.nameCn, it.imageLarge) }
    val heroSubject = if (heroFocused) carouselHero else (focusedCardSubject ?: carouselHero)

    // 向 VM 上报展示目标；本地目标仅用于 backdrop 过渡动画。
    var heroTarget by remember { mutableStateOf<TvHeroSubject?>(null) }
    LaunchedEffect(heroSubject?.subjectId) { heroSubject?.let { heroTarget = it } }
    LaunchedEffect(heroSubject) {
        heroSubject?.let { onIntent(TvExplorationIntent.ShowHero(it)) }
    }
    // backdrop 展示目标防抖 (Prime: 文字即时换, 背景图约半秒后才跟上)
    var heroBackdropTarget by remember { mutableStateOf<TvHeroSubject?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { heroTarget }
            .debounce(TvExplorationDefaults.HeroBackdropDebounceMillis)
            .collect { heroBackdropTarget = it }
    }

    // 自动轮播: 焦点在 hero 且用户静止 6s 切下一个; 手动切换重置计时
    LaunchedEffect(heroFocused, carouselInteraction, carouselSize) {
        if (!heroFocused || carouselSize < 2) return@LaunchedEffect
        while (true) {
            delay(TvExplorationDefaults.CarouselAutoAdvanceMillis)
            carouselIndex = (carouselIndex + 1) % carouselSize
        }
    }

    val info = heroSubject?.let { media.infoCache[it.subjectId] }
    val backdropUrl = heroBackdropTarget?.let { media.backdropCache[it.subjectId] ?: it.imageUrl }

    // 两态插值: hero 高度 (按钮/指示器跟随同一进度) + backdrop 下缘渐隐起点 (展开露更多图)
    val heroHeightFraction by animateFloatAsState(
        if (heroFocused) TvExplorationDefaults.HeroExpandedFraction else TvExplorationDefaults.HeroCollapsedFraction,
        animationSpec = tween(TvExplorationDefaults.HeroHeightAnimMillis),
        label = "heroHeight",
    )
    val bottomFadeStart by animateFloatAsState(
        if (heroFocused) TvExplorationDefaults.HeroBottomFadeStart else TvBackdropDefaults.BottomFadeStart,
        animationSpec = tween(TvExplorationDefaults.BackdropStateAnimMillis),
        label = "bottomFade",
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val rowWidth = maxWidth - TvExplorationDefaults.StartPadding - TvPageDefaults.EndPadding
        val gridColumns = (
            (rowWidth + TvLandscapeCardDefaults.Spacing) /
                (TvLandscapeCardDefaults.Width + TvLandscapeCardDefaults.Spacing)
            ).toInt().coerceAtLeast(1)

        // 行规格: 继续观看 (有数据时) + 为你推荐网格各行; 随 Paging 加载增长
        val rows: List<TvExplorationRow> = buildList {
            if (followed.itemCount > 0) add(TvExplorationRow.ContinueWatching(followed))
            val gridRows = (recommendations.itemCount + gridColumns - 1) / gridColumns
            repeat(gridRows) { add(TvExplorationRow.RecommendationGrid(recommendations, it, gridColumns)) }
        }

        // 行结构变化 (如 Paging 后到的「继续观看」插到首行) 时, hero 聚焦态下把列表滚回顶:
        // LazyColumn 按 key 保住原首行位置, 新插入的首行会被藏在视口上方
        LaunchedEffect(rows.firstOrNull()?.key) {
            if (heroFocused) columnState.scrollToItem(0)
        }

        /**
         * 行间导航: 目标行被回收时先滚动使其组合, 再送焦; BringIntoView 负责最终锚定.
         * 网格行保持同列 (Prime/手机网格同语义); 横向锚定行回其记住的卡.
         */
        fun navigateToRow(target: Int, fromIndex: Int) {
            val row = rows.getOrNull(target) ?: return
            val index = when (row) {
                is TvExplorationRow.RecommendationGrid -> fromIndex
                is TvExplorationRow.ContinueWatching ->
                    focusedIndexByRow[row.key] ?: rowStates[row.key]?.firstVisibleItemIndex ?: 0
            }.coerceIn(0, (row.count - 1).coerceAtLeast(0))
            uiScope.launch {
                if (columnState.layoutInfo.visibleItemsInfo.none { it.key == row.key }) {
                    columnState.scrollToItem(target)
                }
                focus.request(TvExplorationCardKey(row.key, index))
            }
        }

        fun returnToHero() {
            heroFocused = true
            focus.request(TvExplorationFocus.Play)
        }

        TvExplorationPageLayout(
            viewportHeight = viewportHeight,
            heroHeightFraction = heroHeightFraction,
            columnState = columnState,
            focusedRow = { focusedRow },
            focus = focus,
            backdrop = { backdropModifier ->
                TvExplorationBackdrop(
                    url = backdropUrl,
                    fadeColor = tvShellBackgroundColor(),
                    bottomFadeStart = { bottomFadeStart },
                    leftFadeEnd = TvExplorationDefaults.BackdropLeftFadeEnd,
                    modifier = backdropModifier,
                )
            },
            hero = { heroModifier ->
                TvExplorationHero(
                    hero = heroSubject,
                    info = info,
                    summaryFallback = heroSubject?.let { media.summaryFallbackCache[it.subjectId] },
                    expandProgress = {
                        (heroHeightFraction - TvExplorationDefaults.HeroCollapsedFraction) /
                            (TvExplorationDefaults.HeroExpandedFraction - TvExplorationDefaults.HeroCollapsedFraction)
                    },
                    carouselSize = carouselSize,
                    carouselIndex = carouselIndex,
                    onClickDetails = { heroSubject?.let { onIntent(TvExplorationIntent.OpenSubject(it)) } },
                    onButtonFocused = { heroFocused = true },
                    modifier = heroModifier,
                    buttonModifier = Modifier
                        .tvFocusAnchor(focus, TvExplorationFocus.Play)
                        // 左右切轮播; 下进行 0 (显式送焦, 空间搜索跨指示器/标题不可靠); 首项按左交给侧栏
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionRight -> {
                                    if (carouselSize > 0) {
                                        carouselIndex = (carouselIndex + 1) % carouselSize
                                        carouselInteraction++
                                    }
                                    true
                                }

                                Key.DirectionLeft -> {
                                    if (carouselIndex > 0) {
                                        carouselIndex--
                                        carouselInteraction++
                                        true
                                    } else false
                                }

                                Key.DirectionDown -> {
                                    rows.firstOrNull()?.let { first ->
                                        navigateToRow(0, focusedIndexByRow[first.key] ?: 0)
                                    }
                                    true
                                }

                                else -> false
                            }
                        },
                )
            },
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { rowIndex, row ->
                TvExplorationRowItem(
                    row = row,
                    media = media,
                    onIntent = onIntent,
                    focus = focus,
                    rowStates = rowStates,
                    focusedIndexByRow = focusedIndexByRow,
                    onCardFocused = { row, subject ->
                        focusedRow = row
                        heroFocused = false
                        focusedCardSubject = subject
                    },
                    onClickSubject = { onIntent(TvExplorationIntent.OpenSubject(it)) },
                    onNavigateVertical = { delta, fromIndex ->
                        val target = rowIndex + delta
                        if (target < 0) returnToHero() else navigateToRow(target, fromIndex)
                    },
                )
            }
        }
    }
}

/**
 * 探索页骨架 (slot 模式): 沉浸式底色 + 根层 backdrop (surface 背景级, 贴右上, 高度随 hero
 * 两态 + 下探量) + 统一焦点接线 + [常驻 hero (两态高度)] + [纵向行列表].
 * 行列表提供按行布局边缘计算的 BringIntoViewSpec (焦点卡顶边距列表上边界 32dp),
 * 底部留整屏 padding 让末行也能锚到顶.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvExplorationPageLayout(
    viewportHeight: Dp,
    heroHeightFraction: Float,
    columnState: LazyListState,
    focusedRow: () -> TvExplorationRow?,
    focus: TvFocusScope,
    backdrop: @Composable (Modifier) -> Unit,
    hero: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    rows: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val topAligned = remember(columnState, density) {
        TvExplorationBringIntoViewSpec(
            columnState = columnState,
            focusedRow = focusedRow,
            headerHeightPx = with(density) { TvExplorationDefaults.RowHeaderHeight.toPx() },
            anchorInsetPx = with(density) { TvExplorationDefaults.RowAnchorInset.toPx() },
        )
    }
    Box(
        modifier
            .fillMaxSize()
            .background(tvShellBackgroundColor())
            .tvFocusNavSignal(focus),
    ) {
        backdrop(
            Modifier
                .align(Alignment.TopEnd)
                .height(viewportHeight * (heroHeightFraction + TvExplorationDefaults.BackdropOverhangFraction)),
        )
        Column(Modifier.fillMaxSize()) {
            hero(Modifier.fillMaxWidth().height(viewportHeight * heroHeightFraction))
            CompositionLocalProvider(LocalBringIntoViewSpec provides topAligned) {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalFadingEdges(columnState)
                        .padding(start = TvExplorationDefaults.StartPadding),
                    state = columnState,
                    contentPadding = PaddingValues(bottom = viewportHeight),
                    verticalArrangement = Arrangement.spacedBy(TvExplorationDefaults.RowGap),
                    content = rows,
                )
            }
        }
    }
}

/**
 * 同详情页区块锚点: 用真实布局边缘计算滚动距离, 不依赖嵌套 LazyRow 可能裁切的聚焦矩形.
 * LazyColumn 已提供当前视口内的行 offset, 加上行头高度就是卡片顶边, 无需另登记绝对坐标.
 */
@OptIn(ExperimentalFoundationApi::class)
private class TvExplorationBringIntoViewSpec(
    private val columnState: LazyListState,
    private val focusedRow: () -> TvExplorationRow?,
    private val headerHeightPx: Float,
    private val anchorInsetPx: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val row = focusedRow()
        val layout = columnState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row?.key }
        val cardTop = if (layout != null) {
            layout.offset + if (row?.title != null) headerHeightPx else 0f
        } else {
            offset
        }
        return cardTop - anchorInsetPx
    }
}

/** 擦除列表边缘自身的 alpha, 透出原有 backdrop; 渐隐高度小于锚点留白, 避开聚焦描边. */
private fun Modifier.verticalFadingEdges(state: LazyListState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edgeHeight = TvExplorationDefaults.FadingEdgeHeight.toPx().coerceAtMost(size.height / 2)
        if (state.canScrollBackward) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = 0f,
                    endY = edgeHeight,
                ),
                blendMode = BlendMode.DstOut,
            )
        }
        if (state.canScrollForward) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = size.height - edgeHeight,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstOut,
            )
        }
    }

/** 探索页默认值/调参 (Prime Video 实测 + 上游 PR 值; 共享参数见 ui-foundation-tv 的各 Defaults). */
internal object TvExplorationDefaults {
    /** Hero "评分/连载"状态行的固定高度. */
    val StatusRowHeight = 30.dp

    /** 内容左侧额外留白 (外层已让开侧边栏收起宽 48dp, 总左缘 = 48 + 16 = 64). */
    val StartPadding = 16.dp

    /** hero 展开态 (焦点在 hero) 高度占屏高比例. */
    const val HeroExpandedFraction = 0.66f

    /** hero 收缩态 (焦点在卡片行) 高度占屏高比例 (Prime 实测约 0.53 含顶栏; 我们无顶栏取 0.46). */
    const val HeroCollapsedFraction = 0.46f

    /** hero 两态高度过渡时长 (Prime 近乎瞬时, 取短); 按钮/指示器跟随同一条动画. */
    const val HeroHeightAnimMillis = 250

    /** backdrop 比 hero 多下探的屏高比例: 图的渐隐尾部延伸到卡片行下方 (Prime 同款). */
    const val BackdropOverhangFraction = 0.10f

    /** backdrop 左缘渐隐终点 (图宽比例): 下探后图更宽, 左缘要比共享档 (0.3) 更宽才不压到简介文字. */
    const val BackdropLeftFadeEnd = 0.42f

    /** hero backdrop 跟随目标的防抖 (Prime 实测: 文字即时, 图约半秒后换). */
    const val HeroBackdropDebounceMillis = 500L

    /** backdrop 下缘渐隐起点的轮播 (hero) 态档位; 卡片态用共享的 [TvBackdropDefaults.BottomFadeStart]. */
    const val HeroBottomFadeStart = 0.88f

    /** backdrop 两态渐变切换动画时长. */
    const val BackdropStateAnimMillis = 400

    /** 轮播指示器最多显示的圆点数. */
    const val CarouselMaxDots = 20

    /** 自动轮播切换间隔. */
    const val CarouselAutoAdvanceMillis = 6000L

    /** 行头 (区块标题) 固定高度. */
    val RowHeaderHeight = 32.dp

    /** 聚焦卡片顶边到列表视口上边界的距离 (含行头的行与网格续行一致). */
    val RowAnchorInset = 32.dp

    /** 上下边缘的渐隐范围; 在 32dp 锚点之前结束, 保留完整聚焦描边. */
    val FadingEdgeHeight = 24.dp

    /** 行与行之间的间距 (含网格行间). */
    val RowGap = 16.dp

    /** 卡片 TMDB 横图预取并发上限. */
    const val BackdropConcurrency = 3
}
