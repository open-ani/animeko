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
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.collectWithLifecycle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.main.ExplorationPageViewModel
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.widgets.TvBackdropDefaults
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor

/** 探索页 hero 区当前展示的条目 (轮播 / 聚焦卡驱动). */
data class TvHeroSubject(
    val subjectId: Int,
    val title: String,
    val imageUrl: String,
)

/** 探索页焦点锚点 (卡片锚点见 [TvExplorationCardKey]). */
private enum class TvExplorationFocus : TvFocusKey {
    /** Hero 「更多详细内容」按钮 (进页初始焦点 / 行 0 按上的回归目标). */
    Play,
}

/*
 * TV 探索页 (v5, 交互对齐 Amazon Prime Video 实测, atv-architecture.md §7.1):
 * - hero **常驻**顶部, 双态: 焦点在 hero → 展开 (0.66 屏高), 轮播最高热度条目 + 按钮 + 居中指示器;
 *   焦点在下方卡片行 → 收缩 (0.46 屏高), 展示聚焦条目信息, 按钮/指示器淡出;
 *   文字即时切换, backdrop 防抖 500ms 后 crossfade (快速划过卡片不闪图).
 * - 下方为纵向行列表 (继续观看 + 为你推荐分段多行), 每行横向 16:9 卡 (TMDB 横图);
 *   **焦点项恒在左上角**: BringIntoViewSpec 锚定 —— 焦点卡对齐行首, 焦点行头对齐 hero 下缘;
 *   每行各自保留横向位置 (Prime 同款).
 */
@OptIn(FlowPreview::class)
@Composable
fun TvExplorationScreen(
    onClickSubject: (TvHeroSubject) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 状态层复用手机 ExplorationPageViewModel/ExplorationPageState (D3)
    val pageViewModel = viewModel<ExplorationPageViewModel> { ExplorationPageViewModel() }
    val pageState = pageViewModel.explorationPageState
    val trendsPager = pageState.trendingSubjectInfoPager.collectWithLifecycle()
    val recommendations = pageState.recommendationPager.collectAsLazyPagingItems()
    val followed = pageState.followedSubjectsPager.collectAsLazyPagingItems()

    val uiScope = rememberCoroutineScope()
    val media = remember {
        TvSubjectMediaState(GlobalKoin.get(), GlobalKoin.get(), GlobalKoin.get(), uiScope)
    }

    val carouselSize = minOf(trendsPager.itemCount, TvExplorationDefaults.CarouselMaxDots)
    var carouselIndex by rememberSaveable { mutableIntStateOf(0) }
    var carouselInteraction by remember { mutableIntStateOf(0) }
    // hero 双态: 焦点在 hero 按钮 = 展开 (轮播); 焦点在卡片行 = 收缩 (聚焦卡驱动)
    var heroFocused by remember { mutableStateOf(true) }
    var focusedCardSubject by remember { mutableStateOf<TvHeroSubject?>(null) }

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

    // hero 目标变化驱动异步加载 (collectLatest: 换条目取消在途请求)
    var heroTarget by remember { mutableStateOf<TvHeroSubject?>(null) }
    LaunchedEffect(heroSubject?.subjectId) { heroSubject?.let { heroTarget = it } }
    LaunchedEffect(Unit) {
        snapshotFlow { heroTarget }.filterNotNull().collectLatest { media.loadHero(it) }
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

    // 两态插值: hero 高度 + backdrop 下缘渐隐起点 (展开露更多图)
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

    // 行规格: 继续观看 (有数据时) + 为你推荐分段; 随 Paging 加载增长
    val rows: List<TvExplorationRow> = buildList {
        if (followed.itemCount > 0) add(TvExplorationRow.ContinueWatching(followed))
        val chunks = (recommendations.itemCount + TvExplorationDefaults.RecommendationRowSize - 1) /
            TvExplorationDefaults.RecommendationRowSize
        repeat(chunks) { add(TvExplorationRow.Recommendations(recommendations, it)) }
    }

    // 行结构变化 (如 Paging 后到的「继续观看」插到首行) 时, hero 聚焦态下把列表滚回顶:
    // LazyColumn 按 key 保住原首行位置, 新插入的首行会被藏在视口上方
    LaunchedEffect(rows.firstOrNull()?.key) {
        if (heroFocused) columnState.scrollToItem(0)
    }

    /** 行间导航: 滚列表让目标行组合, 送焦其记住的卡 (悬挂到锚点附着), BringIntoView 再对齐左上. */
    fun navigateToRow(target: Int) {
        val row = rows.getOrNull(target) ?: return
        uiScope.launch { columnState.animateScrollToItem(target) }
        val remembered = focusedIndexByRow[row.key] ?: rowStates[row.key]?.firstVisibleItemIndex ?: 0
        focus.request(TvExplorationCardKey(row.key, remembered.coerceIn(0, (row.count - 1).coerceAtLeast(0))))
    }

    fun returnToHero() {
        heroFocused = true
        focus.request(TvExplorationFocus.Play)
    }

    TvExplorationPageLayout(
        heroHeightFraction = heroHeightFraction,
        columnState = columnState,
        focus = focus,
        modifier = modifier,
        hero = { heroModifier ->
            TvExplorationHero(
                hero = heroSubject,
                info = info,
                summaryFallback = heroSubject?.let { media.summaryFallbackCache[it.subjectId] },
                backdropUrl = backdropUrl,
                bottomFadeStart = { bottomFadeStart },
                collapsed = !heroFocused,
                carouselSize = carouselSize,
                carouselIndex = carouselIndex,
                onClickDetails = { heroSubject?.let(onClickSubject) },
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
                                if (rows.isNotEmpty()) navigateToRow(0)
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
                focus = focus,
                rowStates = rowStates,
                focusedIndexByRow = focusedIndexByRow,
                onCardFocused = { subject ->
                    heroFocused = false
                    focusedCardSubject = subject
                },
                onClickSubject = onClickSubject,
                onNavigateVertical = { delta ->
                    val target = rowIndex + delta
                    if (target < 0) returnToHero() else navigateToRow(target)
                },
            )
        }
    }
}

/**
 * 探索页骨架 (slot 模式): 沉浸式底色 + 统一焦点接线 + [常驻 hero (两态高度)] + [纵向行列表].
 * 行列表提供锚定 BringIntoViewSpec (行头对齐顶部), 底部留整屏 padding 让末行也能锚到顶.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvExplorationPageLayout(
    heroHeightFraction: Float,
    columnState: LazyListState,
    focus: TvFocusScope,
    modifier: Modifier = Modifier,
    hero: @Composable (Modifier) -> Unit,
    rows: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val topAligned = remember(density) {
        TvAnchoredBringIntoViewSpec(with(density) { TvExplorationDefaults.RowHeaderHeight.toPx() })
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(tvShellBackgroundColor())
            .tvFocusNavSignal(focus),
    ) {
        val viewportHeight: Dp = maxHeight
        Column(Modifier.fillMaxSize()) {
            hero(Modifier.fillMaxWidth().height(viewportHeight * heroHeightFraction))
            CompositionLocalProvider(LocalBringIntoViewSpec provides topAligned) {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
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

    /** hero 两态高度过渡时长 (Prime 近乎瞬时, 取短). */
    const val HeroHeightAnimMillis = 250

    /** 按钮/指示器随两态淡入淡出时长. */
    const val HeroChromeFadeMillis = 200

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

    /** 行头 (区块标题) 固定高度; 也是纵向锚定时行卡上方预留的空间. */
    val RowHeaderHeight = 32.dp

    /** 行与行之间的间距. */
    val RowGap = 20.dp

    /** 为你推荐每行卡片数 (分段成多行纵向浏览). */
    const val RecommendationRowSize = 12

    /** 卡片 TMDB 横图预取并发上限. */
    const val BackdropConcurrency = 3
}
