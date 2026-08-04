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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.collectWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.data.network.BangumiSummaryService
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.newestAiredDateStringOrNull
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.main.ExplorationPageViewModel
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusLink
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.widgets.TvBackdropDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvHeroDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvPageDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvPosterCard
import me.him188.ani.tv.ui.foundation.widgets.TvPosterCardDefaults
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor

/** 探索页 hero 区当前展示的条目 (聚焦卡/轮播驱动). */
data class TvHeroSubject(
    val subjectId: Int,
    val title: String,
    val imageUrl: String,
)

/** 探索页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvExplorationFocus : TvFocusKey {
    /** Hero 立即观看按钮 (进页初始焦点 / 返回键回顶目标). */
    Play,

    /** 卡片区首卡 (按钮块按下键的显式落点). */
    FirstCard,
}

/**
 * hero 异步媒体状态 (TV 特有, 按上游 PR 模式放页内): 标题即时展示, 详情/backdrop/简介兜底
 * 按条目缓存异步加载. [load] 由聚焦条目经 collectLatest 驱动 (换条目自动取消在途请求).
 */
@Stable
private class TvHeroMediaState(
    private val collectionRepo: SubjectCollectionRepository,
    private val tmdb: TmdbImageService,
    private val bangumiSummaryService: BangumiSummaryService,
) {
    val infoCache = mutableStateMapOf<Int, SubjectCollectionInfo>()
    val backdropCache = mutableStateMapOf<Int, String?>()
    val summaryFallbackCache = mutableStateMapOf<Int, String>()

    /** 加载 [target] 的详情/backdrop/简介兜底 (防抖 300ms: 焦点快速划过时不发请求). */
    suspend fun load(target: TvHeroSubject) {
        var info = infoCache[target.subjectId]
        if (info == null) {
            delay(TvHeroDefaults.MediaDebounceMillis)
            info = try {
                collectionRepo.subjectCollectionFlow(target.subjectId).first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: return
            infoCache[target.subjectId] = info
        }
        if (target.subjectId !in backdropCache) {
            runCatching {
                tmdb.getBackdropUrl(
                    target.subjectId,
                    info.subjectInfo.name,
                    activeAsOfDate = info.episodes.newestAiredDateStringOrNull(),
                )
            }.onSuccess { url -> backdropCache[target.subjectId] = url }
        }
        if (info.subjectInfo.summary.isBlank() && target.subjectId !in summaryFallbackCache) {
            runCatching { bangumiSummaryService.getSummary(target.subjectId) }
                .onSuccess { summaryFallbackCache[target.subjectId] = it.orEmpty() }
        }
    }
}

/*
 * TV 沉浸式探索页. 布局对齐上游 PR#3217 的 TvExplorationPage (本实现为其简化版):
 * 全屏背景为聚焦条目的 TMDB backdrop (左/下渐隐入背景色), 上半区展示聚焦条目的
 * 标题 / 评分 + 连载信息 / 简介; hero 轮播态有两枚操作按钮 + 轮播指示器,
 * 焦点在按钮上时左右键切换轮播, 静止 6s 自动轮播. 下半区为推荐纵向行 (竖版纯图卡).
 *
 * 未实现 (上游有): 继续观看行、锚点吸附卡片行、三区块吸顶滚动、返回键分层.
 */
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
    // 继续观看 (compose 探索页同款数据源): 在看条目 + 播放进度
    val followed = pageState.followedSubjectsPager.collectAsLazyPagingItems()

    // hero 异步加载缓存 (TV 特有): 标题即时, 详情/backdrop/简介兜底异步
    val heroMedia = remember {
        TvHeroMediaState(GlobalKoin.get(), GlobalKoin.get(), GlobalKoin.get())
    }

    val carouselSize = minOf(trendsPager.itemCount, TvExplorationDefaults.CarouselMaxDots)
    var carouselIndex by rememberSaveable { mutableIntStateOf(0) }
    var carouselInteraction by remember { mutableIntStateOf(0) }
    // 焦点是否在 hero 按钮区 (轮播态); 焦点下移进卡片区后 hero 由聚焦卡驱动
    var heroFocused by remember { mutableStateOf(true) }
    // 统一焦点框架: 锚点声明 + 显式链接 + 初始焦点共用一个调度器
    val listState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvExplorationFocus.Play)

    // hero 只展示最高热度轮播条目 (用户裁定: 卡片聚焦不再改 hero)
    val hero = (if (carouselSize > 0) {
        trendsPager[carouselIndex.coerceIn(0, carouselSize - 1)]
    } else null)
        ?.let { TvHeroSubject(it.bangumiId, it.nameCn, it.imageLarge) }

    // hero 变化驱动异步加载 (collectLatest: 换卡取消在途请求)
    var heroTarget by remember { mutableStateOf<TvHeroSubject?>(null) }
    LaunchedEffect(hero?.subjectId) { hero?.let { heroTarget = it } }
    LaunchedEffect(Unit) {
        snapshotFlow { heroTarget }.filterNotNull().collectLatest { heroMedia.load(it) }
    }

    // 自动轮播: 焦点在 hero 且用户静止 6s 切下一个; 手动切换重置计时
    LaunchedEffect(heroFocused, carouselInteraction, carouselSize) {
        if (!heroFocused || carouselSize < 2) return@LaunchedEffect
        while (true) {
            delay(TvExplorationDefaults.CarouselAutoAdvanceMillis)
            carouselIndex = (carouselIndex + 1) % carouselSize
        }
    }

    val info = hero?.let { heroMedia.infoCache[it.subjectId] }
    val backdropUrl = hero?.let { heroMedia.backdropCache[it.subjectId] } ?: hero?.imageUrl

    // backdrop 下缘渐隐起点: hero 态低 (露更多图), 卡片态高; 焦点移动时插值过渡
    val bottomFadeStart by animateFloatAsState(
        if (heroFocused) TvExplorationDefaults.HeroBottomFadeStart else TvBackdropDefaults.BottomFadeStart,
        animationSpec = tween(TvExplorationDefaults.BackdropStateAnimMillis),
        label = "bottomFade",
    )

    TvExplorationPageLayout(
        listState = listState,
        focus = focus,
        modifier = modifier,
    ) { rowWidth ->
        item(key = "hero") {
            // backdrop 放 hero item 内部 (hero 参与滚动, 背景图必须随之滚出, 不能固定在根层).
            // fillMaxWidth 必须给: 否则 Box 宽度收缩成图宽, TopEnd 对齐失效图会靠左压标题
            TvExplorationHero(
                hero = hero,
                info = info,
                summaryFallback = hero?.let { heroMedia.summaryFallbackCache[it.subjectId] },
                backdropUrl = backdropUrl,
                bottomFadeStart = { bottomFadeStart },
                carouselSize = carouselSize,
                carouselIndex = carouselIndex,
                onClickDetails = { hero?.let(onClickSubject) },
                onButtonFocused = { heroFocused = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillParentMaxHeight(TvExplorationDefaults.BackdropHeightFraction),
                buttonModifier = Modifier
                    .tvFocusAnchor(focus, TvExplorationFocus.Play)
                    // 跨过指示器/标题直达首卡 (空间搜索跨大间距不可靠)
                    .tvFocusLink(focus, down = TvExplorationFocus.FirstCard)
                    // 左右键切轮播 (单按钮无行内导航冲突); 首项按左不消费, 交给侧栏
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

                            else -> false
                        }
                    },
            )
        }

        if (followed.itemCount > 0) {
            tvContinueWatchingSection(
                followed = followed,
                onClickSubject = onClickSubject,
                onCardFocused = { heroFocused = false },
                onExitUp = {
                    // hero item 可能已被 LazyColumn 滚出回收 (节点不在组合里),
                    // 必须先滚回顶部让它重新组合, 框架轮询兜住附着时序
                    uiScope.launch { listState.animateScrollToItem(0) }
                    heroFocused = true
                    focus.request(TvExplorationFocus.Play)
                },
                firstCardModifier = Modifier.tvFocusAnchor(focus, TvExplorationFocus.FirstCard),
            )
        }

        tvRecommendationsSection(
            recommendations = recommendations,
            rowWidth = rowWidth,
            onClickSubject = onClickSubject,
            onCardFocused = { heroFocused = false },
            // 未登录无继续观看行时, 首卡兼任 FirstCard 锚点
            firstCardModifier = if (followed.itemCount == 0) {
                Modifier.tvFocusAnchor(focus, TvExplorationFocus.FirstCard)
            } else null,
        )
    }
}

/**
 * 探索页骨架 (对齐手机 CollectionPageLayout 的 slot 模式): 沉浸式底色 + 统一焦点接线 +
 * 整页单 LazyColumn (用户指定: hero 与卡片区一同纵向滚动, 见 §14.5).
 *
 * [content] 以 LazyListScope 填充区块; rowWidth 为内容区可用行宽 (已减左右留白,
 * 供为你推荐自适应网格算列数).
 */
@Composable
private fun TvExplorationPageLayout(
    listState: LazyListState,
    focus: TvFocusScope,
    modifier: Modifier = Modifier,
    content: LazyListScope.(rowWidth: Dp) -> Unit,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(tvShellBackgroundColor())
            .tvFocusNavSignal(focus),
    ) {
        val rowWidth = maxWidth - TvExplorationDefaults.StartPadding - TvPageDefaults.EndPadding
        LazyColumn(
            Modifier.fillMaxSize().padding(start = TvExplorationDefaults.StartPadding),
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(TvExplorationDefaults.SectionGap),
        ) {
            content(rowWidth)
        }
    }
}

/** 继续观看区块: 标题 + 单行海报卡横向列表. */
private fun LazyListScope.tvContinueWatchingSection(
    followed: LazyPagingItems<FollowedSubjectInfo>,
    onClickSubject: (TvHeroSubject) -> Unit,
    onCardFocused: () -> Unit,
    onExitUp: () -> Unit,
    firstCardModifier: Modifier,
) {
    item(key = "followed-header") {
        Text(
            "继续观看",
            style = MaterialTheme.typography.titleMedium,
            color = tvHeroContentColor(),
        )
    }
    item(key = "followed-row") {
        LazyRow(
            // 单行横向列表, 行内无纵向导航, 容器拦上键安全: 按上先让 hero 按钮块
            // 重新组合 (焦点在卡片时它已退场, 直接链接指向未附着节点会没反应),
            // 再经框架轮询聚焦 —— 修复"第一排按上回不到 hero 按钮"
            Modifier.onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionUp) {
                    if (event.type == KeyEventType.KeyDown) onExitUp()
                    true
                } else false
            },
            horizontalArrangement = Arrangement.spacedBy(TvPageDefaults.CardSpacing),
            contentPadding = PaddingValues(end = TvPageDefaults.EndPadding),
        ) {
            items(
                followed.itemCount,
                key = { followed.peek(it)?.subjectInfo?.subjectId ?: it },
            ) { index ->
                val info = followed[index] ?: return@items
                val item = TvHeroSubject(
                    info.subjectInfo.subjectId,
                    info.subjectInfo.displayName,
                    info.subjectInfo.imageLarge,
                )
                // 与收藏页统一的海报卡样式 (标题在卡内, 聚焦跑马灯)
                TvPosterCard(
                    imageUrl = info.subjectInfo.imageLarge,
                    title = info.subjectInfo.displayName,
                    onClick = { onClickSubject(item) },
                    onFocused = onCardFocused,
                    modifier = if (index == 0) firstCardModifier else Modifier,
                )
            }
        }
    }
}

/**
 * 为你推荐区块: 标题 + 纵向网格 (手机 GridCells.Adaptive 同语义) —— 列数按可用宽自适应,
 * 行内 weight 等分拉伸 (不会出现固定宽度下最右侧最后一项被压缩), 尾行空位 Spacer 占住.
 */
private fun LazyListScope.tvRecommendationsSection(
    recommendations: LazyPagingItems<RecommendedItemInfo>,
    rowWidth: Dp,
    onClickSubject: (TvHeroSubject) -> Unit,
    onCardFocused: () -> Unit,
    firstCardModifier: Modifier?,
) {
    item(key = "rec-header") {
        Text(
            "为你推荐",
            style = MaterialTheme.typography.titleMedium,
            color = tvHeroContentColor(),
        )
    }
    val recColumns = (
        (rowWidth + TvPageDefaults.CardSpacing) / (TvPosterCardDefaults.Width + TvPageDefaults.CardSpacing)
        ).toInt().coerceAtLeast(1)
    val recRows = (recommendations.itemCount + recColumns - 1) / recColumns
    items(recRows, key = { "rec-row-$it" }) { rowIndex ->
        Row(
            Modifier.fillMaxWidth().padding(end = TvPageDefaults.EndPadding),
            horizontalArrangement = Arrangement.spacedBy(TvPageDefaults.CardSpacing),
        ) {
            repeat(recColumns) { colIndex ->
                val index = rowIndex * recColumns + colIndex
                if (index < recommendations.itemCount) {
                    when (val rec = recommendations[index]) {
                        is RecommendedSubjectInfo -> {
                            val item = TvHeroSubject(rec.bangumiId, rec.nameCn, rec.imageLarge)
                            // 与收藏页统一的海报卡样式; 宽度由行内 weight 等分决定
                            TvPosterCard(
                                imageUrl = rec.imageLarge,
                                title = rec.nameCn,
                                onClick = { onClickSubject(item) },
                                onFocused = onCardFocused,
                                width = null,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (index == 0 && firstCardModifier != null) {
                                            firstCardModifier
                                        } else Modifier,
                                    ),
                            )
                        }

                        else -> Spacer(Modifier.weight(1f))
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 探索页默认值/调参 (对齐上游 PR 实机验证值; 共享参数见 ui-foundation-tv 的各 Defaults). */
internal object TvExplorationDefaults {
    /** Hero "评分/连载"状态行的固定高度. */
    val StatusRowHeight = 30.dp

    /** 区块标题到卡片行的间距. */
    val SectionGap = 12.dp

    /** 内容左侧额外留白 (外层已让开侧边栏收起宽 48dp, 总左缘 = 48 + 16 = 64). */
    val StartPadding = 16.dp

    /** backdrop 高度占屏高比例 (Prime 实测: 图占屏顶约 66%; 追番/搜索为共享的 0.70 档). */
    const val BackdropHeightFraction = 0.66f

    /** backdrop 下缘渐隐起点的轮播 (hero) 态档位; 卡片态用共享的 [TvBackdropDefaults.BottomFadeStart]. */
    const val HeroBottomFadeStart = 0.88f

    /** backdrop 两态渐变切换动画时长. */
    const val BackdropStateAnimMillis = 400

    /** 轮播指示器最多显示的圆点数. */
    const val CarouselMaxDots = 20

    /** 自动轮播切换间隔. */
    const val CarouselAutoAdvanceMillis = 6000L
}
