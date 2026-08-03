/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.exploration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.network.BangumiSummaryService
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.newestAiredDateStringOrNull
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.main.ExplorationPageViewModel
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.focus.tvFocusLink
import me.him188.ani.tv.ui.foundation.widgets.TV_BACKDROP_CROSSFADE_MILLIS
import me.him188.ani.tv.ui.foundation.widgets.TV_BACKDROP_LEFT_FADE_END
import me.him188.ani.tv.ui.foundation.widgets.TV_BACKDROP_LEFT_FADE_START
import me.him188.ani.tv.ui.foundation.widgets.TV_BACKDROP_TOP_SCRIM_ALPHA
import me.him188.ani.tv.ui.foundation.widgets.TV_BACKDROP_TOP_SCRIM_END
import me.him188.ani.tv.ui.foundation.widgets.TV_HERO_MEDIA_DEBOUNCE_MILLIS
import me.him188.ani.tv.ui.foundation.widgets.TV_HERO_SUMMARY_WIDTH_FRACTION
import me.him188.ani.tv.ui.foundation.widgets.TV_HERO_TEXT_FADE_MILLIS
import me.him188.ani.tv.ui.foundation.widgets.TV_HERO_TITLE_WIDTH_FRACTION
import me.him188.ani.tv.ui.foundation.widgets.TV_PAGE_CARD_SPACING
import me.him188.ani.tv.ui.foundation.widgets.TV_PAGE_CARD_WIDTH
import me.him188.ani.tv.ui.foundation.widgets.TV_PAGE_END_PAD
import me.him188.ani.tv.ui.foundation.widgets.TvHeroButton
import me.him188.ani.tv.ui.foundation.widgets.TvPortraitCard
import me.him188.ani.tv.ui.foundation.widgets.tvBackdropFadeFromBlackStops
import me.him188.ani.tv.ui.foundation.widgets.tvBackdropFadeToBlackStops
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvHeroSecondaryContentColor
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

    // hero 异步加载缓存 (TV 特有, 按上游 PR 模式放页内): 标题即时, 详情/backdrop/简介兜底异步
    val collectionRepo = remember { GlobalKoin.get<SubjectCollectionRepository>() }
    val tmdb = remember { GlobalKoin.get<TmdbImageService>() }
    val bangumiSummaryService = remember { GlobalKoin.get<BangumiSummaryService>() }
    val infoCache = remember { mutableStateMapOf<Int, SubjectCollectionInfo>() }
    val backdropCache = remember { mutableStateMapOf<Int, String?>() }
    val summaryFallbackCache = remember { mutableStateMapOf<Int, String>() }

    val carouselSize = minOf(trendsPager.itemCount, TV_CAROUSEL_MAX_DOTS)
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

    // hero 变化驱动异步加载 (collectLatest: 换卡取消在途请求; 防抖 300ms 快速划过不发请求)
    var heroTarget by remember { mutableStateOf<TvHeroSubject?>(null) }
    LaunchedEffect(hero?.subjectId) { hero?.let { heroTarget = it } }
    LaunchedEffect(Unit) {
        snapshotFlow { heroTarget }.filterNotNull().collectLatest { target ->
            var info = infoCache[target.subjectId]
            if (info == null) {
                delay(TV_HERO_MEDIA_DEBOUNCE_MILLIS)
                info = try {
                    collectionRepo.subjectCollectionFlow(target.subjectId).first()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } ?: return@collectLatest
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

    // 自动轮播: 焦点在 hero 且用户静止 6s 切下一个; 手动切换重置计时
    LaunchedEffect(heroFocused, carouselInteraction, carouselSize) {
        if (!heroFocused || carouselSize < 2) return@LaunchedEffect
        while (true) {
            delay(TV_CAROUSEL_AUTO_ADVANCE_MILLIS)
            carouselIndex = (carouselIndex + 1) % carouselSize
        }
    }

    val info = hero?.let { infoCache[it.subjectId] }
    val backdropUrl = hero?.let { backdropCache[it.subjectId] } ?: hero?.imageUrl
    val shellBackground = tvShellBackgroundColor()

    // backdrop 下缘渐隐起点: hero 态低 (露更多图), 卡片态高; 焦点移动时插值过渡
    val bottomFadeStart by animateFloatAsState(
        if (heroFocused) TV_EXPLORATION_BOTTOM_FADE_START_HERO else 0.78f,
        animationSpec = tween(TV_BACKDROP_STATE_ANIM_MILLIS),
        label = "bottomFade",
    )

    BoxWithConstraints(modifier.fillMaxSize().background(shellBackground).tvFocusNavSignal(focus)) {
        // 为你推荐网格的可用行宽 (减去内容区左缘留白与右缘留白)
        val recAvailableWidth = maxWidth - TV_EXPLORATION_START_PAD - TV_PAGE_END_PAD
        // ── 整页单 LazyColumn (用户指定): hero 与卡片区一同纵向滚动 ──
        LazyColumn(
            Modifier.fillMaxSize().padding(start = TV_EXPLORATION_START_PAD),
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(TV_SECTION_HEADER_TO_ROW_GAP),
        ) {
            item(key = "hero") {
                // backdrop 放 hero item 内部 (hero 参与滚动, 背景图必须随之滚出, 不能固定在根层).
                // fillMaxWidth 必须给: 否则 Box 宽度收缩成图宽, TopEnd 对齐失效图会靠左压标题
                Box(Modifier.fillMaxWidth().fillParentMaxHeight(TV_EXPLORATION_BACKDROP_HEIGHT_FRACTION)) {
                // Backdrop: 16:9 贴右上, 高度占屏 0.66, 顶缘轻压暗 + 左缘/下缘平滑渐隐 (采样停点无马赫带)
                Crossfade(
                    backdropUrl,
                    Modifier.align(Alignment.TopEnd),
                    animationSpec = tween(TV_BACKDROP_CROSSFADE_MILLIS),
                    label = "backdrop",
                ) { url ->
                    if (url != null) {
                        Box(
                            Modifier
                                // 外层 hero Box 已限高 0.66 屏, 这里占满它 (再乘 fraction 会双重缩小)
                                .fillMaxHeight()
                                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            *tvBackdropFadeFromBlackStops(
                                                start = 0f, end = TV_BACKDROP_TOP_SCRIM_END,
                                                maxAlpha = TV_BACKDROP_TOP_SCRIM_ALPHA,
                                                color = shellBackground,
                                            ),
                                        ),
                                    )
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            *tvBackdropFadeFromBlackStops(
                                                start = TV_BACKDROP_LEFT_FADE_START,
                                                end = TV_BACKDROP_LEFT_FADE_END,
                                                color = shellBackground,
                                            ),
                                        ),
                                    )
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            *tvBackdropFadeToBlackStops(
                                                start = bottomFadeStart,
                                                end = 1f,
                                                color = shellBackground,
                                            ),
                                        ),
                                    )
                                },
                        ) {
                            AsyncImage(
                                url,
                                contentDescription = null,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
                // 左侧信息列: 占满 hero 高度 —— 标题/评分(固定) + 简介(weight=1 弹性) +
                // 按钮 Row + 指示器(底部, 信息列宽内居中). 按钮与指示器常驻 (不再随焦点隐藏,
                // 之前被固定高度信息块挤出裁掉)
                Column(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 10.dp)) {
                    // 标题: 定高一行, 长标题跑马灯; 换条目 crossfade
                    Crossfade(hero?.title, animationSpec = tween(TV_HERO_TEXT_FADE_MILLIS), label = "title") { title ->
                        Text(
                            title.orEmpty(),
                            Modifier.fillMaxWidth(TV_HERO_TITLE_WIDTH_FRACTION).basicMarquee(iterations = 3),
                            style = MaterialTheme.typography.headlineLarge,
                            color = tvHeroContentColor(),
                            maxLines = 1,
                        )
                    }
                    // 评分 + 连载信息行
                    Row(
                        Modifier.padding(top = 8.dp).height(TV_HERO_STATUS_ROW_HEIGHT),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        info?.subjectInfo?.ratingInfo?.score
                            ?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
                            ?.let { score ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "★",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "$score/10",
                                        color = tvHeroContentColor(),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        val latest = info?.airingInfo?.latestSort
                        val total = info?.subjectInfo?.totalEpisodes?.takeIf { it > 0 }
                        val progressText = buildString {
                            if (latest != null) append("连载至 $latest")
                            if (total != null) {
                                if (isNotEmpty()) append(" · ")
                                append("预定全 $total 话")
                            }
                        }
                        if (progressText.isNotEmpty()) {
                            Text(
                                progressText,
                                style = MaterialTheme.typography.titleSmall,
                                color = tvHeroSecondaryContentColor(),
                            )
                        }
                        info?.subjectInfo?.airDate?.takeIf { it.isValid }?.let { date ->
                            Text(
                                "${date.year}年${date.month}月",
                                style = MaterialTheme.typography.titleSmall,
                                color = tvHeroSecondaryContentColor(),
                            )
                        }
                    }
                    // 简介: 弹性占据剩余空间 (用户指定 weight=1)
                    val summary = info?.subjectInfo?.summary?.takeIf { it.isNotBlank() }
                        ?: hero?.let { summaryFallbackCache[it.subjectId] }
                    Crossfade(
                        summary,
                        Modifier.padding(top = 8.dp).weight(1f),
                        animationSpec = tween(TV_HERO_TEXT_FADE_MILLIS),
                        label = "summary",
                    ) { text ->
                        Text(
                            text.orEmpty().trim(),
                            Modifier.fillMaxWidth(TV_HERO_SUMMARY_WIDTH_FRACTION),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tvHeroSecondaryContentColor(),
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 唯一操作按钮 (用户裁定去掉立即观看): 聚焦时左右键切换轮播
                    TvHeroButton(
                        text = "更多详细内容",
                        icon = Icons.Outlined.Info,
                        filled = true,
                        onClick = { hero?.let(onClickSubject) },
                        onFocused = { heroFocused = true },
                        modifier = Modifier
                            .padding(top = 10.dp)
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
                    // 轮播指示器: hero 底部, 信息列宽内居中 (不可聚焦, 纯展示; 由自动轮播驱动)
                    if (carouselSize >= 2) {
                        Row(
                            Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(TV_HERO_SUMMARY_WIDTH_FRACTION),
                            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(carouselSize) { index ->
                                val selected = index == carouselIndex
                                Box(
                                    Modifier
                                        .height(5.dp)
                                        .width(if (selected) 22.dp else 5.dp)
                                        .clip(if (selected) RoundedCornerShape(2.5.dp) else CircleShape)
                                        .background(
                                            tvHeroContentColor().copy(alpha = if (selected) 0.95f else 0.35f),
                                        ),
                                )
                            }
                        }
                    }
                }
                }
            }

            if (followed.itemCount > 0) {
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
                                if (event.type == KeyEventType.KeyDown) {
                                    // hero item 可能已被 LazyColumn 滚出回收 (节点不在组合里),
                                    // 必须先滚回顶部让它重新组合, 框架轮询兜住附着时序
                                    uiScope.launch { listState.animateScrollToItem(0) }
                                    heroFocused = true
                                    focus.request(TvExplorationFocus.Play)
                                }
                                true
                            } else false
                        },
                        horizontalArrangement = Arrangement.spacedBy(TV_PAGE_CARD_SPACING),
                        contentPadding = PaddingValues(end = TV_PAGE_END_PAD),
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
                            Column(Modifier.width(TV_PAGE_CARD_WIDTH)) {
                                TvPortraitCard(
                                    imageUrl = info.subjectInfo.imageLarge,
                                    contentDescription = info.subjectInfo.displayName,
                                    onClick = { onClickSubject(item) },
                                    onFocused = { heroFocused = false },
                                    modifier = if (index == 0) {
                                        Modifier.tvFocusAnchor(focus, TvExplorationFocus.FirstCard)
                                    } else Modifier,
                                )
                                Text(
                                    info.subjectInfo.displayName,
                                    Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tvHeroSecondaryContentColor(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "rec-header") {
                Text(
                    "为你推荐",
                    style = MaterialTheme.typography.titleMedium,
                    color = tvHeroContentColor(),
                )
            }
            // 为你推荐: 纵向网格 (手机 GridCells.Adaptive 同语义) —— 列数按可用宽自适应,
            // 行内 weight 等分拉伸 (不会出现固定宽度下最右侧最后一项被压缩), 尾行空位 Spacer 占住
            val recColumns = (
                (recAvailableWidth + TV_PAGE_CARD_SPACING) / (TV_PAGE_CARD_WIDTH + TV_PAGE_CARD_SPACING)
            ).toInt().coerceAtLeast(1)
            val recRows = (recommendations.itemCount + recColumns - 1) / recColumns
            items(recRows, key = { "rec-row-$it" }) { rowIndex ->
                Row(
                    Modifier.fillMaxWidth().padding(end = TV_PAGE_END_PAD),
                    horizontalArrangement = Arrangement.spacedBy(TV_PAGE_CARD_SPACING),
                ) {
                    repeat(recColumns) { colIndex ->
                        val index = rowIndex * recColumns + colIndex
                        if (index < recommendations.itemCount) {
                            when (val rec = recommendations[index]) {
                                is RecommendedSubjectInfo -> {
                                    val item = TvHeroSubject(rec.bangumiId, rec.nameCn, rec.imageLarge)
                                    Column(Modifier.weight(1f)) {
                                        TvPortraitCard(
                                            imageUrl = rec.imageLarge,
                                            contentDescription = rec.nameCn,
                                            onClick = { onClickSubject(item) },
                                            onFocused = { heroFocused = false },
                                            modifier = if (followed.itemCount == 0 && index == 0) {
                                                // 未登录无继续观看行时, 首卡兼任 FirstCard 锚点
                                                Modifier.tvFocusAnchor(focus, TvExplorationFocus.FirstCard)
                                            } else Modifier,
                                        )
                                        Text(
                                            rec.nameCn,
                                            Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = tvHeroSecondaryContentColor(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
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
    }
}

// ============ 探索页调参 (对齐上游 PR 实机验证值) ============

/** Hero 信息块固定高度 (标题 + 评分/连载行 + 简介); 固定保证换条目时卡片区不跳动. */
private val TV_HERO_INFO_HEIGHT = 200.dp

/** Hero "评分/连载"状态行的固定高度. */
private val TV_HERO_STATUS_ROW_HEIGHT = 30.dp

/** Hero 信息块与操作按钮块之间的间距. */
private val TV_HERO_INFO_TO_BUTTONS_GAP = 6.dp

/** 两枚操作按钮之间的间距 (很短). */
private val TV_HERO_BUTTON_GAP = 4.dp

/** 按钮块下方到卡片区的间距. */
private val TV_HERO_BUTTONS_TO_CONTENT_GAP = 12.dp

/** 区块标题到卡片行的间距. */
private val TV_SECTION_HEADER_TO_ROW_GAP = 12.dp

/** 轮播指示器最多显示的圆点数. */
private const val TV_CAROUSEL_MAX_DOTS = 20

/** 自动轮播切换间隔. */
private const val TV_CAROUSEL_AUTO_ADVANCE_MILLIS = 6000L

/** backdrop 下缘渐隐起点的轮播 (hero) 态档位; 卡片态用共享的 0.78. */
private const val TV_EXPLORATION_BOTTOM_FADE_START_HERO = 0.88f

/** backdrop 两态渐变切换动画时长. */
private const val TV_BACKDROP_STATE_ANIM_MILLIS = 400

/** 内容左侧额外留白 (外层已让开侧边栏收起宽 48dp, 总左缘 = 48 + 16 = 64). */
private val TV_EXPLORATION_START_PAD = 16.dp

/** 为你推荐纵向网格列数 (1080p 横屏: 内容区宽约 (1080dp*2-64-48), 112dp 卡 + 10dp 距). */
private const val TV_REC_GRID_COLUMNS = 14

/** backdrop 高度占屏高比例 (Prime 实测: 图占屏顶约 66%). */
private const val TV_EXPLORATION_BACKDROP_HEIGHT_FRACTION = 0.66f
