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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.matchToEpisodes
import me.him188.ani.app.data.network.newestAiredDateStringOrNull
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.subject.details.SubjectDetailsViewModel
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsState
import me.him188.ani.app.ui.subject.details.SubjectDetailsUIState
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.app.ui.foundation.AsyncImage
import kotlinx.coroutines.flow.first
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.widgets.TvHeroButton
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvHeroSecondaryContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor

/** 详情页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvDetailsFocus : TvFocusKey {
    /** Hero 播放按钮 (进页初始焦点 / 选集区返回键分层目标). */
    Play,
}

/*
 * TV 条目详情页. 布局对齐上游 PR#3217 的 SubjectDetailsTvPage (本实现为其首屏简化版):
 * Hero 全屏 backdrop (TMDB 三态: 未解析按有图排版等待 / TMDB 图 / 封面回退) +
 * 标题白字浮图 + 贴底信息带 (播放按钮 / 统计+连载+标签墙 / 评分直方图) + 选集剧照卡轮播.
 *
 * 未实现 (上游有): 圆钮行/选集网格菜单/标签菜单/吸附滚动/角色/制作人员/评论区块.
 */
/** TMDB 横版 backdrop 三态: null = 未解析 (按有图排版等待); Resolved(url=null) = 确认无图回退封面. */
private data class TvBackdropState(val url: String?)

@Composable
fun TvSubjectDetailsScreen(
    subjectId: Int,
    placeholder: SubjectInfo?,
    onPlayEpisode: (episodeId: Int) -> Unit,
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

            is SubjectDetailsUIState.Ok -> SubjectContent(
                state.value, airingCollection, backdropState, episodeStills, onPlayEpisode,
            )
        }
    }
}

@Composable
private fun SubjectContent(
    details: SubjectDetailsState,
    airingCollection: SubjectCollectionInfo?,
    backdropState: TvBackdropState?,
    episodeStills: Map<Int, String>,
    onPlayEpisode: (episodeId: Int) -> Unit,
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
    // 统一焦点框架: 进页初始焦点落播放按钮; 选集区返回键分层也经同一调度器归还
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvDetailsFocus.Play)
    var episodesFocused by remember { mutableStateOf(false) }
    BackHandler(enabled = episodesFocused) {
        scope.launch { scrollState.animateScrollTo(0) }
        focus.request(TvDetailsFocus.Play)
    }

    BoxWithConstraints(modifier.fillMaxSize().tvFocusNavSignal(focus)) {
        val heroHeight = maxHeight - 16.dp

        // ── 背景层: 全屏 backdrop, 贴顶/贴右出血, 左缘 scrim + 底缘 DstOut 擦除, 随滚动淡出 ──
        heroBackdropUrl?.let { url ->
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = (scrollState.value / HERO_BACKDROP_FADE_DISTANCE.toPx()).coerceIn(0f, 1f)
                        alpha = 1f - progress * (1f - HERO_BACKDROP_MIN_ALPHA)
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

        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            // ── Hero 首屏: 标题在顶, 信息带贴底 ──
            Column(Modifier.height(heroHeight).padding(horizontal = TV_DETAILS_PAD)) {
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
                            val label = if (watched > 0 && playTargetSort != null) {
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
                                modifier = Modifier.tvFocusAnchor(focus, TvDetailsFocus.Play),
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
                                        .clip(TV_TAG_SHAPE)
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

            // ── 选集: 横向剧照卡轮播 (色圈焦点) ──
            Column(
                Modifier
                    .padding(bottom = 24.dp)
                    .onFocusChanged { episodesFocused = it.hasFocus },
            ) {
                Text(
                    "选集",
                    Modifier.padding(start = TV_DETAILS_PAD, bottom = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = tvHeroContentColor(),
                )
                LazyRow(
                    state = rememberLazyListState(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = TV_DETAILS_PAD, end = TV_DETAILS_PAD),
                ) {
                    items(episodes, key = { it.episodeId }) { episode ->
                        TvEpisodeCard(
                            episode = episode,
                            imageUrl = episodeStills[episode.episodeId]
                                ?: heroBackdropUrl
                                ?: subjectInfo.imageLarge,
                            onClick = { onPlayEpisode(episode.episodeId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(value: Int, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            formatCount(value),
            style = MaterialTheme.typography.titleMedium,
            color = tvHeroContentColor(),
        )
        Text(
            label,
            Modifier.padding(bottom = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = tvHeroSecondaryContentColor(),
        )
    }
}

/** 评分直方图 (1..10 竖条) + 分数 + 评分人数. */
@Composable
private fun RatingBlock(info: SubjectInfo, modifier: Modifier = Modifier) {
    val rating = info.ratingInfo
    val counts = (1..10).map { rating.count.get(it) }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    Column(modifier.width(240.dp), horizontalAlignment = Alignment.End) {
        Row(
            Modifier.height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            counts.forEach { count ->
                val fraction = (count.toFloat() / max).coerceIn(0.04f, 1f)
                Box(
                    Modifier
                        .width(13.dp)
                        .height((44 * fraction).dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                )
            }
        }
        Row(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            (1..10).forEach {
                Text(
                    "$it",
                    Modifier.width(13.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = tvHeroSecondaryContentColor(),
                )
            }
        }
        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                rating.score.takeIf { it.isNotBlank() } ?: "-",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tvHeroContentColor(),
            )
            Text(
                buildString {
                    if (rating.rank > 0) append("#${rating.rank} · ")
                    append("${formatCount(rating.total)} 人评分")
                },
                style = MaterialTheme.typography.labelMedium,
                color = tvHeroSecondaryContentColor(),
            )
        }
    }
}

/** 选集剧照卡: 16:9, 色圈+留白焦点 (与竖版卡同规格), 卡内左下角序号+标题. */
@Composable
private fun TvEpisodeCard(
    episode: EpisodeListItem,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val watched = episode.isDoneOrDropped
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier
            .width(226.dp)
            .aspectRatio(16f / 9f)
            .then(
                if (focused) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(11.dp))
                } else Modifier,
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(3.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                ),
        ) {
            AsyncImage(
                imageUrl,
                contentDescription = null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
            )
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    episode.sort.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (watched) Color.White.copy(alpha = 0.55f) else Color.White,
                )
                Text(
                    episode.nameCn.ifBlank { episode.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (watched) Color.White.copy(alpha = 0.55f) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatCount(value: Int): String = when {
    value >= 1000 -> "%,d".format(value)
    else -> value.toString()
}

/** 内容水平留白 (含让开侧栏收起宽; 详情页是独立目的地, 自带留白). */
private val TV_DETAILS_PAD = 48.dp

/** backdrop 随滚动淡出的距离. */
private val HERO_BACKDROP_FADE_DISTANCE = 400.dp

/** backdrop 滚动淡出后的保留透明度. */
private const val HERO_BACKDROP_MIN_ALPHA = 0.25f

private val TV_TAG_SHAPE = RoundedCornerShape(6.dp)
