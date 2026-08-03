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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.tv.ui.foundation.focus.FOCUS_REQ_DELAY_MILLIS
import me.him188.ani.tv.ui.foundation.widgets.TV_BACKDROP_CROSSFADE_MILLIS
import me.him188.ani.tv.ui.foundation.widgets.TV_BACKDROP_LEFT_FADE_END
import me.him188.ani.tv.ui.foundation.widgets.TV_BACKDROP_LEFT_FADE_START
import me.him188.ani.tv.ui.foundation.widgets.TV_BACKDROP_TOP_SCRIM_ALPHA
import me.him188.ani.tv.ui.foundation.widgets.TV_BACKDROP_TOP_SCRIM_END
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
    viewModel: TvExplorationViewModel,
    onClickSubject: (TvHeroSubject) -> Unit,
    onPlaySubject: (TvHeroSubject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trends by viewModel.trends.collectAsState()
    val recommendations = viewModel.recommendations.collectAsLazyPagingItems()

    val carouselSize = minOf(trends.size, TV_CAROUSEL_MAX_DOTS)
    var carouselIndex by rememberSaveable { mutableIntStateOf(0) }
    var carouselInteraction by remember { mutableIntStateOf(0) }
    // 焦点是否在 hero 按钮区 (轮播态); 焦点下移进卡片区后 hero 由聚焦卡驱动
    var heroFocused by remember { mutableStateOf(true) }
    var focusedCardSubject by remember { mutableStateOf<TvHeroSubject?>(null) }
    // 显式焦点链接 (空间搜索跨大间距不可靠, 对齐 PR 的显式 FocusRequester 方案)
    val firstCardFocus = remember { FocusRequester() }
    val playButtonFocus = remember { FocusRequester() }

    val carouselItem = trends.getOrNull(carouselIndex.coerceIn(0, (carouselSize - 1).coerceAtLeast(0)))
        ?.let { TvHeroSubject(it.bangumiId, it.nameCn, it.imageLarge) }
    val hero = (if (heroFocused) carouselItem else focusedCardSubject) ?: carouselItem

    // hero 变化上报 VM 拉数据 (标题即时, 详情/backdrop 异步)
    LaunchedEffect(hero?.subjectId) {
        hero?.let { viewModel.setFocusedSubject(it) }
    }

    // 冷启动初始焦点: 立即观看按钮 (布局就绪延迟, 对齐 PR 的 FOCUS_REQ_DELAY; 轮询直到成功)
    LaunchedEffect(Unit) {
        // 轮询数次: 首帧后节点可能尚未 attach (转场中), requestFocus 会静默失败
        repeat(4) {
            delay(FOCUS_REQ_DELAY_MILLIS)
            if (runCatching { playButtonFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
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

    val info = hero?.let { viewModel.infoCache[it.subjectId] }
    val backdropUrl = hero?.let { viewModel.backdropCache[it.subjectId] } ?: hero?.imageUrl
    val shellBackground = tvShellBackgroundColor()

    // backdrop 下缘渐隐起点: hero 态低 (露更多图), 卡片态高; 焦点移动时插值过渡
    val bottomFadeStart by animateFloatAsState(
        if (heroFocused) TV_EXPLORATION_BOTTOM_FADE_START_HERO else 0.78f,
        animationSpec = tween(TV_BACKDROP_STATE_ANIM_MILLIS),
        label = "bottomFade",
    )

    Box(modifier.fillMaxSize().background(shellBackground)) {
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
                        .fillMaxHeight(TV_EXPLORATION_BACKDROP_HEIGHT_FRACTION)
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

        Column(Modifier.fillMaxSize().padding(start = TV_EXPLORATION_START_PAD, top = 24.dp)) {
            // ── Hero 信息块 (固定高度, 换条目时下方卡片区不跳动) ──
            Column(Modifier.height(TV_HERO_INFO_HEIGHT)) {
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
                // 简介: weight 填满信息块剩余空间
                val summary = info?.subjectInfo?.summary?.takeIf { it.isNotBlank() }
                    ?: hero?.let { viewModel.summaryFallbackCache[it.subjectId] }
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
            }

            // ── 操作按钮块 (焦点在 hero 时展示; 移到卡片区后消失, 卡片区上移) ──
            AnimatedVisibility(heroFocused, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Spacer(Modifier.height(TV_HERO_INFO_TO_BUTTONS_GAP))
                    Column(
                        // 焦点在按钮上时左右键切轮播; 在第一个条目按左不消费 (交给侧边栏)
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionRight -> {
                                    carouselIndex = (carouselIndex + 1) % carouselSize.coerceAtLeast(1)
                                    carouselInteraction++
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
                        verticalArrangement = Arrangement.spacedBy(TV_HERO_BUTTON_GAP),
                    ) {
                        TvHeroButton(
                            text = "立即观看",
                            icon = Icons.Rounded.PlayArrow,
                            filled = true,
                            onClick = { hero?.let(onPlaySubject) },
                            onFocused = { heroFocused = true },
                            focusRequester = playButtonFocus,
                        )
                        TvHeroButton(
                            text = "更多详细内容",
                            icon = Icons.Outlined.Info,
                            filled = false,
                            onClick = { hero?.let(onClickSubject) },
                            onFocused = { heroFocused = true },
                            modifier = Modifier.focusProperties {
                                down = firstCardFocus // 跨过指示器/标题直达首卡
                            },
                        )
                    }
                    Spacer(Modifier.height(TV_HERO_BUTTONS_TO_CONTENT_GAP))
                }
            }

            // ── 轮播指示器 (不可聚焦, 纯展示): 当前项拉长胶囊 ──
            if (heroFocused && carouselSize >= 2) {
                Row(
                    Modifier.padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
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

            // ── 卡片区: 推荐纵向行 (竖版纯图卡, 聚焦色圈) ──
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(TV_SECTION_HEADER_TO_ROW_GAP),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item("recommendations-header") {
                    Text(
                        "推荐",
                        style = MaterialTheme.typography.titleMedium,
                        color = tvHeroContentColor(),
                    )
                }
                item("recommendations-row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(TV_PAGE_CARD_SPACING),
                        contentPadding = PaddingValues(end = TV_PAGE_END_PAD),
                    ) {
                        itemsIndexed(trends, key = { _, it -> it.bangumiId }) { index, subject ->
                            val item = TvHeroSubject(subject.bangumiId, subject.nameCn, subject.imageLarge)
                            TvPortraitCard(
                                imageUrl = subject.imageLarge,
                                contentDescription = subject.nameCn,
                                onClick = { onClickSubject(item) },
                                onFocused = {
                                    heroFocused = false
                                    focusedCardSubject = item
                                },
                                modifier = Modifier.width(TV_PAGE_CARD_WIDTH),
                                focusRequester = firstCardFocus.takeIf { index == 0 },
                            )
                        }
                    }
                }
                item("more-header") {
                    Text(
                        "为你推荐",
                        style = MaterialTheme.typography.titleMedium,
                        color = tvHeroContentColor(),
                    )
                }
                item("more-row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(TV_PAGE_CARD_SPACING),
                        contentPadding = PaddingValues(end = TV_PAGE_END_PAD),
                    ) {
                        items(recommendations.itemCount) { index ->
                            when (val rec = recommendations[index]) {
                                is RecommendedSubjectInfo -> {
                                    val item = TvHeroSubject(rec.bangumiId, rec.nameCn, rec.imageLarge)
                                    TvPortraitCard(
                                        imageUrl = rec.imageLarge,
                                        contentDescription = rec.nameCn,
                                        onClick = { onClickSubject(item) },
                                        onFocused = {
                                            heroFocused = false
                                            focusedCardSubject = item
                                        },
                                        modifier = Modifier.width(TV_PAGE_CARD_WIDTH),
                                    )
                                }

                                else -> {}
                            }
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

/** backdrop 高度占屏高比例 (Prime 实测: 图占屏顶约 66%). */
private const val TV_EXPLORATION_BACKDROP_HEIGHT_FRACTION = 0.66f
