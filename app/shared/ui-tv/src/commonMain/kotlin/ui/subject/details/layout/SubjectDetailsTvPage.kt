/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import me.him188.ani.app.ui.foundation.widgets.AniCenteredPanelDialog
import me.him188.ani.app.ui.foundation.widgets.AniScrollableTextDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import coil3.compose.AsyncImagePainter
import com.kmpalette.color
import com.kmpalette.palette.graphics.Palette
import kotlinx.collections.immutable.toImmutableList
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeRequest
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.tools.ColorUtils
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.session.TvNavigationSideRail
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.foundation.focus.restoreFocusAfter
import me.him188.ani.app.ui.foundation.tvLongPressKey
import me.him188.ani.app.ui.foundation.session.buildTvRailItems
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.GLASS_CONTAINER_ALPHA
import me.him188.ani.app.ui.foundation.theme.glassContainerColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_episodes
import me.him188.ani.app.ui.lang.subject_details_login_to_collect
import me.him188.ani.app.ui.lang.subject_details_info
import me.him188.ani.app.ui.lang.subject_details_no_summary
import me.him188.ani.app.ui.lang.subject_details_related_subjects
import me.him188.ani.app.ui.lang.subject_details_show_more
import me.him188.ani.app.ui.lang.subject_details_stat_collected
import me.him188.ani.app.ui.lang.subject_details_stat_watching
import me.him188.ani.app.ui.lang.subject_details_stat_wish
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.app.ui.subject.rememberSubjectStatusStrings
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeDialogsHost
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionActions
import me.him188.ani.app.ui.subject.collection.components.EditCollectionTypeDropDown
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionActionsForCollect
import me.him188.ani.app.ui.subject.collection.components.renderCollectionTypeAsCurrent
import me.him188.ani.app.ui.subject.details.components.AnimatedGradientBackground
import me.him188.ani.app.ui.subject.details.components.COVER_WIDTH_TO_HEIGHT_RATIO
import me.him188.ani.app.ui.subject.details.components.RatingHistogram
import me.him188.ani.app.ui.subject.details.components.RelatedSubjectsLazyRow
import me.him188.ani.app.ui.subject.details.components.rememberNavigateToRelatedSubject
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.subject.details.sections.CharactersSection
import me.him188.ani.app.ui.subject.details.sections.ReviewsPreviewSection
import me.him188.ani.app.ui.subject.details.sections.SectionHeader
import me.him188.ani.app.ui.subject.details.sections.StaffSection
import me.him188.ani.app.ui.subject.details.sections.SubjectInfoTable
import me.him188.ani.app.ui.subject.details.sections.groupThousands
import me.him188.ani.app.ui.subject.details.sections.SubjectRatingSummary
import me.him188.ani.app.ui.subject.details.sections.DETAILS_TEXT_CONTENT_PADDING
import me.him188.ani.app.ui.subject.details.sections.DETAILS_TEXT_END_RESERVE
import me.him188.ani.app.ui.subject.details.sections.MENU_CONTAINER_ALPHA
import me.him188.ani.app.ui.subject.details.sections.FocusEpisodeCarousel
import me.him188.ani.app.ui.subject.details.sections.FocusEpisodeGridDropdown
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsState
import me.him188.ani.app.ui.subject.renderSubjectSeason
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.stringResource

/**
 * [SubjectDetailsTvPage] 的首屏占位: 详情数据还在路上时, 先用**手上已有的东西**按目标页的
 * 版式画出 Hero —— 背景图取自进程内热缓存 ([TmdbImageService.peekBackdropUrl], 上一个页面
 * 聚焦这张卡时就查过), 标题取自导航占位.
 *
 * 目的是消掉"点一张卡看三段画面"里的第一段. 原先这里是居中转圈的空白页, 于是依次看到:
 * 转圈 -> 整页换成真布局 -> 背景图再淡进来. 现在落地即有大图和标题, 且**位置与真布局完全一致**
 * (共用 [MultiColumnScaffold] + 同一套留白/字号/阴影), 真布局到达时 Hero 区域原地不动,
 * 只有信息带与下方区块补上来.
 *
 * 背景图的三态判定与 [SubjectDetailsTvPage] 逐字对应 (有图 / 确认无图回退竖版封面 / 未解析
 * 则不放图), 否则两边会在切换的一瞬互相跳变.
 *
 * 冷启 (热缓存里没有) 时只有标题, 没有转圈 —— 短等待放个转圈反而更显慢; 真的久等
 * ([SLOW_LOAD_SPINNER_DELAY] 之后) 才把转圈补出来, 免得慢网络下看着像卡死.
 */
@Composable
fun SubjectDetailsTvLoadingPlaceholder(
    subjectInfo: SubjectInfo?,
    layoutParams: SubjectDetailsLayoutParams,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    val tmdbImageService = remember { GlobalKoin.get<TmdbImageService>() }
    // "" = 已确认无图, null = 本进程还没解析过 —— 与详情页的 backdropResolved 同义
    val warmBackdrop = remember(subjectInfo?.subjectId) {
        subjectInfo?.subjectId?.let { tmdbImageService.peekBackdropUrl(it) }
    }
    val heroBackdropUrl = warmBackdrop?.takeIf { it.isNotEmpty() }
        ?: subjectInfo?.imageLarge?.takeIf { warmBackdrop != null && it.isNotBlank() }

    var slowLoad by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SLOW_LOAD_SPINNER_DELAY)
        slowLoad = true
    }

    val pad = layoutParams.contentHorizontalPadding
    val scrollState = rememberScrollState()
    Box(modifier.fillMaxSize()) {
        MultiColumnScaffold(
            layoutParams.copy(
                contentHorizontalPadding = 0.dp,
                contentTopPadding = (pad - TV_HERO_TITLE_TOP_TRIM).coerceAtLeast(0.dp),
            ),
            Modifier,
            showTopBar = false,
            windowInsets,
            scrollState = scrollState,
            backgroundOverlay = {
                heroBackdropUrl?.let { TvHeroBackdrop(it, scrollState, onSuccess = {}) }
            },
        ) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(start = pad)) {
                // 与 TvHeroBlock 的标题列逐项对齐 (top 8dp / headlineLarge / 白字 + 柔和黑影 /
                // 两行截断), 真布局到达时标题不位移
                val titleShadow = with(LocalDensity.current) {
                    Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = Offset(0f, 1.dp.toPx()),
                        blurRadius = 6.dp.toPx(),
                    )
                }
                Column(
                    Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        subjectInfo?.displayName.orEmpty(),
                        style = MaterialTheme.typography.headlineLarge.copy(shadow = titleShadow),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (slowLoad) {
                        CircularProgressIndicator(
                            Modifier.padding(top = 16.dp).size(28.dp),
                            color = Color.White,
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }
        }
    }
}

/** 首屏占位里补出加载转圈的等待阈值: 短等待放转圈反而更显慢. */
private val SLOW_LOAD_SPINNER_DELAY = 800.milliseconds

/**
 * TV (10-foot UI) 条目详情页: 单列信息流, 参考主流 TV 流媒体应用的结构 —
 * Hero 首屏 (背景封面 + 标题/元数据/简介/主操作) + 各内容区块顺序下排.
 *
 * 与 [SubjectDetailsMultiColumnPage] 内容一致, 仅重排:
 * 原侧栏的作品信息表 / 收藏统计 / 标签下沉到"关联作品"之后的"作品信息"块.
 *
 * 首屏数据未到时的占位见 [SubjectDetailsTvLoadingPlaceholder].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubjectDetailsTvPage(
    state: SubjectDetailsState,
    selfInfo: SelfInfoUiState,
    layoutParams: SubjectDetailsLayoutParams,
    onPlay: (episodeId: Int) -> Unit,
    onClickTag: (Tag) -> Unit,
    onClickLogin: () -> Unit,
    onShowComments: () -> Unit,
    modifier: Modifier = Modifier,
    onEpisodeCollectionUpdate: (SetEpisodeCollectionTypeRequest) -> Unit = {},
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    backgroundPalette: Palette? = null,
    onClickOpenExternal: () -> Unit = {},
    onCoverImageSuccess: (AsyncImagePainter.State.Success) -> Unit = {},
    onClickCache: (() -> Unit)? = null,
    /**
     * 视频背景模式 (TV 播放器内嵌): 页面底色透明, 不放渐变/TMDB 背景图,
     * 改为对下层视频画遮罩 —— 首屏只压底部, 滚动后整屏变暗 (与独立详情页视觉一致).
     */
    videoBackground: Boolean = false,
    /** 内嵌变体介绍页顶部按上键的回调 (回到播放器选集条); null 不处理. */
    onVideoBackgroundExitUp: (() -> Unit)? = null,
) {
    // 页面间过渡由导航转场承担 (NavigationMotionScheme.calculateCrossfade, 同步 crossfade):
    // 滚动归零/焦点落位等状态恢复发生在入场淡入的头几帧, 无可见闪动. 页内不再叠加渐显
    // (两层透明度相乘会让入场页中途露出底色).

    // info 加载中: 显示 TV 布局自己的加载占位, 不 return 空白 —— 调用方在 TV 上
    // 不等 info 就进入本页 (避免先闪单栏旧布局), 加载通常一瞬.
    val info = state.info
    if (info == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val presentation by state.presentation.collectAsStateWithLifecycle()
    // 卡片流用保持数据源顺序的全量列表: 特别篇按序号插在正片之间 (尸鬼 20.5 落在 20 与 21
    // 中间), 与播放器选集列表看到的顺序一致. 它们的 TMDB 剧照/简介/时长本来就已按全量分集
    // 匹配好 (SubjectDetailsStateFactory 传的是 collection.episodes), 这里只是把先前没人取的
    // 那几个 key 用起来. 选集网格仍要正片/特别篇分组, 故两份都留着.
    val episodes = presentation.episodeListUiState.allEpisodes
    val mainEpisodes = presentation.episodeListUiState.mainEpisodes
    val specialEpisodes = presentation.episodeListUiState.otherEpisodes
    // "当前集"只在正片里找: 特别篇通常一直是未看状态, 算进来会让看完正片的条目
    // 把 SP 当成"下一集要看的", 进页面直接滚到那里
    val currentEpisodeId = remember(mainEpisodes) { mainEpisodes.firstOrNull { !it.isDoneOrDropped }?.episodeId }

    // 角色/制作人员/作品信息: 仅独立页组合完整区块; 内嵌变体不收集 (全量名单由
    // 播放器胶囊面板承担, 这里收集只会白发请求)
    val exposedCharacters =
        if (videoBackground) null else state.exposedCharactersPager.collectAsLazyPagingItemsWithLifecycle()
    val allCharacters =
        if (videoBackground) null else state.charactersPager.collectAsLazyPagingItemsWithLifecycle()
    val totalCharactersCount by state.totalCharactersCountState
    val exposedStaff =
        if (videoBackground) null else state.exposedStaffPager.collectAsLazyPagingItemsWithLifecycle()
    val allStaff =
        if (videoBackground) null else state.staffPager.collectAsLazyPagingItemsWithLifecycle()
    val totalStaffCount by state.totalStaffCountState
    val related = state.relatedSubjectsPager.collectAsLazyPagingItemsWithLifecycle()
    val comments = state.subjectCommentState.list.collectAsLazyPagingItemsWithLifecycle()
    val commentCount = state.subjectCommentState.count

    // 水平留白由本页面各区块自理 (Hero 背景图需贴屏幕边缘出血), 不在滚动容器上统一加
    val pad = layoutParams.contentHorizontalPadding
    // TMDB 横版背景图, 三态: 结果未出时 Hero 不放任何图并按"有图"样式排版 (等待,
    // 常见情形图直接淡入零跳变); 确认无图才回退到竖版封面. 若直接用 null 当加载中,
    // 每次进页都会先闪一下回退布局再切到有图, 视觉上像页面跳变.
    //
    // 首帧初值取进程内热缓存 (TmdbImageService.peekBackdropUrl): 上一个页面 (探索/搜索/
    // 时间表) 聚焦这张卡时就已经查过同一条目, 结果同步可读. 拿到就等于**首帧即有图** ——
    // 那张图还在 Coil 内存缓存里, Hero 一进场就是满的; 下面的 flow 仍照常收, 只是从
    // "决定首屏长什么样"退化成"后台校正". 热缓存没有 (冷启/别处进来) 时行为与从前一致.
    val tmdbImageService = remember { GlobalKoin.get<TmdbImageService>() }
    val warmBackdrop = remember(state) {
        if (videoBackground) null else tmdbImageService.peekBackdropUrl(state.subjectId)
    }
    var backdropResolved by remember(state) { mutableStateOf(warmBackdrop != null) }
    var tmdbBackdropUrl by remember(state) { mutableStateOf(warmBackdrop?.takeIf { it.isNotEmpty() }) }
    LaunchedEffect(state) {
        // 视频背景模式不放背景图, 不必发起 TMDB 请求
        if (videoBackground) return@LaunchedEffect
        state.tmdbBackdropUrlFlow.collect {
            tmdbBackdropUrl = it
            backdropResolved = true
        }
    }
    // 无 TMDB 横版图时的回退: 拿竖版封面当全屏背景 (Crop 默认居中 = 取海报中间那条横带).
    // 封面用 Bangumi 的 l 档, 即上传原图 (实测 1400~2700 px 宽), 4K 面板上放大 1.4~2.7 倍,
    // 压着 scrim 与底缘渐隐看不出来; 更高清晰度没有来源 (c/m 档是 150/100 px 缩略图,
    // /r/<宽>/ 缩放前缀对封面路径返回 400, TMDB 那边本项目只取 backdrop 不取 poster).
    //
    // 关键: 回退后**排版与有图时完全一致** —— 标题白字浮在图上, 简介留给"作品信息"子页,
    // 右侧不再单独摆一张竖版封面 (原先那套"无图版式"只在连封面也没有时才出现).
    // 只在 backdropResolved 之后才用它, 否则会先闪一下封面再被 TMDB 图换掉.
    val heroBackdropUrl = tmdbBackdropUrl
        ?: info.imageLarge.takeIf { backdropResolved && it.isNotBlank() }
    // TMDB 分集缩略图 (episodeId -> URL); 无图的集回退纯文字卡
    val tmdbEpisodeStills by state.tmdbEpisodeStillsFlow.collectAsStateWithLifecycle(emptyMap())
    // 各集播放进度 (episodeId -> 0..1), 选集卡片底部进度条
    val playProgress by state.playProgressFlow.collectAsStateWithLifecycle(emptyMap())
    // TMDB 分集时长 (episodeId -> 分钟), 聚焦集信息行右侧
    val episodeRuntimes by state.tmdbEpisodeRuntimesFlow.collectAsStateWithLifecycle(emptyMap())
    // TMDB 本地化分集简介 (episodeId -> 简介), 排在 Bangumi 简介前展示
    val episodeOverviews by state.tmdbEpisodeOverviewsFlow.collectAsStateWithLifecycle(emptyMap())
    // Bangumi 简介整段无中文 (全日文/纯英文) 时用 TMDB 中文整部简介替换; null = 用原文
    val tmdbSummaryOverride by state.tmdbSummaryOverrideFlow.collectAsStateWithLifecycle(null)
    // Ani 服务器简介为空时的 bgm.tv 兜底 (null = 结果未出, "" = bgm 也没有, 非空 = bgm 简介)
    val bangumiSummaryFallback by state.bangumiSummaryFallbackFlow.collectAsStateWithLifecycle(null)
    // 简介优先级: Ani 服务器 > bgm.tv (仅替代不合并) > TMDB 中文.
    // Ani 有简介时维持原逻辑 (全外文则被 TMDB 中文替换); Ani 为空时等 bgm.tv 结果 (未出结果先按
    // 空显示, 避免先闪 TMDB 再换成 bgm), bgm.tv 也没有才用 TMDB 中文兜底.
    val displaySummary = if (info.summary.isNotBlank()) {
        tmdbSummaryOverride ?: info.summary
    } else when (bangumiSummaryFallback) {
        null -> ""
        "" -> tmdbSummaryOverride.orEmpty()
        else -> bangumiSummaryFallback.orEmpty()
    }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Hero 标签墙的状态: rememberSaveable 跨"点击标签→搜索→返回"保留 —
    // 返回本页时浏览模式不变, 焦点直接恢复到最后聚焦的那个标签上 (restorePending 标记)
    var tagsBrowseMode by rememberSaveable { mutableStateOf(false) }
    var focusedTagIndex by rememberSaveable { mutableStateOf(-1) }
    var tagsRestorePending by rememberSaveable { mutableStateOf(false) }

    // 统一焦点锚点调度器 (见 [TvDetailsFocusAnchors]): 进页初始焦点 / 返回键分层 /
    // 侧边栏退出 / 选集网格关闭 / 标签菜单关闭 / 标签墙跨页恢复, 全部只是 send 不同锚点.
    val anchors = remember { TvDetailsFocusAnchors() }
    LaunchedEffect(anchors.pending) { anchors.resolve() }
    // 跨区块纵向导航路由 (见 [TvDetailsSectionNav]): 区块边缘元素的 上/下 键显式
    // 送焦点到相邻区块, 落点/存在性解析全在路由内, 每次组合从头登记.
    val sectionNav = remember { TvDetailsSectionNav() }
    sectionNav.onExitTop = onVideoBackgroundExitUp
    sectionNav.register(
        TvDetailsSection.HERO,
        if (videoBackground) anchors.episodesSummary else anchors.heroPlay,
    )
    sectionNav.register(TvDetailsSection.EPISODES, anchors.episodesCarousel)
    sectionNav.setPresent(TvDetailsSection.HERO, true)
    sectionNav.setPresent(TvDetailsSection.EPISODES, !videoBackground)
    // 角色/制作人员区块自身在空数据时不渲染 (Section 内部 early return), 存在性同步该条件
    sectionNav.setPresent(
        TvDetailsSection.CHARACTERS,
        exposedCharacters != null && exposedCharacters.itemCount > 0,
    )
    sectionNav.setPresent(
        TvDetailsSection.STAFF,
        exposedStaff != null && exposedStaff.itemCount > 0,
    )
    sectionNav.setPresent(
        TvDetailsSection.BELOW,
        // 与该区块的组合条件一致 (无内容不组合)
        related.itemCount > 0 || (!videoBackground && comments.itemCount > 0),
    )
    // 标签墙跨页恢复目标 (进页那一刻的快照; -1 = 无). 菜单开着离开的情形由菜单自理.
    val tagRestoreIndex = remember {
        if (tagsRestorePending && !tagsBrowseMode && focusedTagIndex >= 0) focusedTagIndex else -1
    }

    // 进入页面时初始焦点给 Hero 区的播放按钮.
    // 过去初始焦点由左上角返回按钮提供, 该按钮在 TV 上已移除.
    // 例外: 从标签跳转的搜索页返回时, 恢复到离开前聚焦的标签.
    LaunchedEffect(Unit) {
        // 返回本页 (关联条目跳转返回/播放页退出) 时滚动位置会恢复到离开时的区块,
        // 与"初始焦点在海报页"不一致 —— 先显示旧位置再跳回, 有一次可见的回跳运动.
        // 统一为"重新进入直接落在海报页": 无动画瞬时归零, 首帧即海报页.
        scrollState.scrollTo(0)
        withFrameNanos { }
        when {
            // 标签菜单开着离开的: 菜单重新展开后自理初始焦点 (Popup 独立焦点域)
            tagsRestorePending && tagsBrowseMode -> {}

            tagRestoreIndex >= 0 -> {
                anchors.send(TvDetailsFocusAnchor.TAG_WALL)
                tagsRestorePending = false
            }

            // 播放器内嵌变体: 首屏是介绍页 (选集条已移入播放器控制层, 不在本页),
            // 进入焦点给简介块 ("暂无信息"兜底保证恒可聚焦)
            videoBackground -> anchors.send(TvDetailsFocusAnchor.EPISODES_SUMMARY)

            else -> anchors.send(TvDetailsFocusAnchor.HERO_PLAY)
        }
    }

    // 返回键分层, 三级: 选集页之下的区域 (角色/制作人员/关联条目...)
    // 按返回先回到选集卡片; 选集页内按返回回到最顶上的海报页 (焦点回播放按钮);
    // 海报页再按返回才真正退出详情页. 纵向滚动均由聚焦驱动 (SnapOnFocusSection 吸附).
    // 弹窗/阅读模式等自行消费返回键的场景优先级更高, 不会走到这里.
    //
    // 层级用"最后一个真正持有过焦点的区块"记忆 (各区块 onFocused 上报), 不读瞬时焦点, 也不读
    // 滚动位置, 因为这两个量在过渡期间都会说谎:
    //  - 焦点"不在页面任何地方"是独立状态 (弹窗是独立窗口, 开着期间宿主无焦点元素; 关闭后
    //    归还也要好几帧), 不能和"焦点在别处"合并 —— 合并后关弹窗那一下会被判成"在选集页
    //    下方", 于是把用户往下送回卡片, 白吃一次按键;
    //  - 滚动是动画量, 一次返回按下后仍 > 0 好几百毫秒, 而焦点已经落到上一层, 期间再按返回
    //    就会读到"层级在下方"这种不存在的组合, 表现为连按两次又回到原地.
    var backLevel by remember { mutableStateOf(TvDetailsSection.HERO) }
    // 选集整页的简介是否渲染了展开按钮 (即简介被截断了): 决定卡片上键要不要指向它
    var summaryExpandPresent by remember { mutableStateOf(false) }
    BackHandler(enabled = backLevel != TvDetailsSection.HERO) {
        if (backLevel == TvDetailsSection.EPISODES) {
            scope.launch { scrollState.animateScrollTo(0) }
            anchors.send(TvDetailsFocusAnchor.HERO_PLAY)
        } else {
            // 聚焦轮播行 (focusRestorer 恢复到上次聚焦的卡片), 选集页随焦点吸附滚入.
            // 没有轮播可聚焦时 (内嵌变体的选集条在播放器控制层; 未开播条目连分集都没有)
            // 退而聚焦简介块 ("暂无信息"兜底保证它恒可聚焦), 返回键不至于无效
            anchors.send(
                if (videoBackground || episodes.isEmpty()) TvDetailsFocusAnchor.EPISODES_SUMMARY
                else TvDetailsFocusAnchor.EPISODES_CAROUSEL,
            )
        }
    }

    // 选集快速跳转网格 (辅助入口, 轮播仍是主体): 上千集时逐格横向导航不现实
    var showEpisodeGrid by rememberSaveable { mutableStateOf(false) }
    // 网格菜单关闭后轮播要跳到的集 (菜单里最后聚焦的那格)
    var revealEpisodeId by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier) {
        // TV 上不渲染顶栏 (原本只为返回/主页/外链按钮而设, 已全部移除), 滚动内容直达屏幕上边缘.
        // 顶部留白 ≈ 内容左侧留白 (区块统一的水平留白, 海报页与下方区块左边界对齐),
        // 名义值要再减去标题上方的附加空白 (标题列自带 top 8dp + headlineLarge 行高
        // 顶部内衬约 12dp): 左边距贴的是字形左缘, 顶部也要贴字形上缘才对等.
        val contentTopPad = (pad - TV_HERO_TITLE_TOP_TRIM).coerceAtLeast(0.dp)
        // Hero 区块占满首屏: 标题在顶, 信息带锚定在画面最底部.
        // 信息带底缘正好贴屏幕下边界, 下一区块完全在折叠线以下.
        val heroHeight = maxHeight - contentTopPad - 16.dp
        // 区块吸附位置不再解析推算: SnapOnFocusSection 内部实测区块在滚动内容中的位置
        // (屏幕位置 + 已滚距离), 脚手架顶部留白/insets 等全部自动包含, 无固定偏差.
        // 选集区自成完整一屏 (标题+简介+封面+轮播): 吸附后整页占满屏幕,
        // 上下各留 EPISODES_PAGE_VERTICAL_MARGIN 的空隙.
        val episodesPageHeight = maxHeight - EPISODES_PAGE_VERTICAL_MARGIN * 2
        // 画面纵向运动全部由"分区吸附"显式驱动: 焦点在 Hero 区 (顶栏/信息带)
        // 内移动画面固定在顶部; 焦点进入某个区块则滚动到该区块顶部. 为此禁用纵向滚动容器的
        // 默认 BringIntoView (否则它与吸附动画互相打架, 造成跳动); 区块列内部重新提供默认
        // spec, 保证选集行等横向 LazyRow 的横向滚动不受影响.
        val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
        val noBringIntoView = remember {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
            }
        }
        // 左缘 overlay 导航栏 (zIndex 置顶): 仅在横屏海报首屏 (未下滑) 显示.
        // 收起态是贴左缘的一列纯图标; 焦点从 Hero 左列按钮按左进入后展开为图标+文字,
        // 并从左侧压一层渐变遮罩盖住海报页. 用途: 详情页可经关联条目无限嵌套,
        // 这里提供一键回主页的逃生通道 (返回键只逐层退). 图标/文字尺寸对齐主页导航栏.
        // 播放器内嵌变体不渲染: 播放器界面不该提供离开播放器的侧边入口 (返回键即退出),
        // 去掉后左右边距对称 (rail 图标不再占据左边距).
        // derivedStateOf: 本作用域 (BoxWithConstraints) 包住整页内容, 裸读 scrollState.value
        // 会让吸附滚动动画期间整页每帧重组; 收敛为只在 0/非0 边界失效一次
        val atPageTop by remember { derivedStateOf { scrollState.value == 0 } }
        if (atPageTop && !videoBackground) {
            // 遮罩颜色用主题色 (surface 向 surfaceTint 偏移, 再稍向黑压深以在海报上保证可读),
            // 随主题/动态取色变化; 压深比例按日夜主题分档 (见常量注释, 可调).
            // 羽化渐变方式由共用侧边栏统一按探索页那套平滑多色标处理.
            val railScrimDarken = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                TV_DETAILS_RAIL_SCRIM_DARKEN_DARK
            } else {
                TV_DETAILS_RAIL_SCRIM_DARKEN_LIGHT
            }
            val railScrimColor = lerp(
                lerp(
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.surfaceTint,
                    TV_DETAILS_RAIL_SCRIM_TINT,
                ),
                Color.Black,
                railScrimDarken,
            )
            TvDetailsSideRail(
                onExitToHero = { anchors.send(TvDetailsFocusAnchor.HERO_PLAY) },
                modifier = Modifier.align(Alignment.CenterStart).zIndex(1f),
                scrimColor = railScrimColor,
            )
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides noBringIntoView) {
        MultiColumnScaffold(
        layoutParams.copy(
            contentHorizontalPadding = 0.dp,
            contentTopPadding = contentTopPad,
        ),
        Modifier,
        // 顶栏按钮 (返回/主页/外链) 在 TV 上已全部移除, 顶栏本身也不再渲染,
        // 否则 Scaffold 会把滚动区压到顶栏之下, 往下翻时内容在 64dp 处被裁出一条边界
        showTopBar = false,
        windowInsets,
        scrollState = scrollState,
        backgroundOverlay = {
            if (videoBackground) {
                // 视频作背景: 对下层视频画遮罩 (首屏只压底部 + 左缘, 滚动后整屏变暗)
                TvVideoBackgroundScrim(scrollState)
            } else {
                val surfaceColor = MaterialTheme.colorScheme.surface
                val colors = remember(backgroundPalette) {
                    backgroundPalette?.swatches
                        ?.map { ColorUtils.blendColor(it.color, surfaceColor, 0.85f) }
                        ?.toImmutableList()
                }
                // backdrop 图到位且停在页顶 (图不透明盖满) 时暂停光斑动画: 被盖住还在跑的
                // 全屏 blur 是探针实测里详情页"永不静止"的主因 (2026-07-31, 常驻 13-30fps).
                // 滚动后 backdrop 渐隐、光斑重新露出, 动画随之恢复
                var backdropLoaded by remember(heroBackdropUrl) { mutableStateOf(false) }
                if (colors != null) {
                    AnimatedGradientBackground(
                        colors,
                        speed = 0.05,
                        modifier = Modifier.fillMaxSize(),
                        paused = { backdropLoaded && scrollState.value <= 0 },
                    )
                }
                // 全屏背景: TMDB 横版图, 没有则竖版封面居中裁切 (见 heroBackdropUrl).
                // 连封面也没有时才什么都不铺, 由 TvHeroBlock 走"无图版式"
                heroBackdropUrl?.let { url ->
                    TvHeroBackdrop(
                        imageUrl = url,
                        scrollState = scrollState,
                        onSuccess = {
                            backdropLoaded = true
                            onCoverImageSuccess(it)
                        },
                    )
                }
            }
        },
        containerColor = if (videoBackground) Color.Transparent else AniThemeDefaults.pageContentBackgroundColor,
    ) {
        Column(
            // 起始留白只加在 Hero 块内 (startPadding), 不能加在整列上:
            // 列级 padding 会把选集轮播 LazyRow 的左边界一起右移, 向左滑过锚点的
            // 卡片在此处被硬裁出一条边 (卡片行必须保持全宽出血); 侧边栏也只在海报首屏显示
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(layoutParams.sectionSpacing),
        ) {
            // 播放器内嵌变体不渲染独立页 Hero (介绍页在选集条之后, 见下方 videoBackground 分支)
            if (!videoBackground) TvHeroBlock(
                state = state,
                info = info,
                selfInfo = selfInfo,
                onPlay = onPlay,
                onClickLogin = onClickLogin,
                onClickOpenExternal = onClickOpenExternal,
                horizontalPadding = pad,
                // 播放按钮 = HERO_PLAY 锚点 (挂请求器 + 到位确认)
                primaryButtonModifier = Modifier
                    .focusRequester(anchors.heroPlay)
                    .onFocusChanged {
                        anchors.onAnchorFocusChanged(TvDetailsFocusAnchor.HERO_PLAY, it.isFocused)
                    },
                // 中列: 收藏统计 + 标签墙 + 连载信息. 标签墙浏览模式需要 BringIntoView
                // 滚动露出隐藏标签, 恢复默认 spec (页面级已禁用)
                middleColumn = {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
                        TvHeroInfoColumn(
                            state = state,
                            info = info,
                            // 点击标签跳转搜索: 标记返回时要把焦点恢复到该标签
                            onClickTag = {
                                tagsRestorePending = true
                                onClickTag(it)
                            },
                            browseMode = tagsBrowseMode,
                            onBrowseModeChange = { tagsBrowseMode = it },
                            focusedTagIndex = focusedTagIndex,
                            onFocusedTagIndexChange = { focusedTagIndex = it },
                            restorePending = tagsRestorePending,
                            onRestoreConsumed = { tagsRestorePending = false },
                            anchors = anchors,
                            wallRestoreIndex = tagRestoreIndex,
                            modifier = Modifier.weight(1f).padding(start = 24.dp),
                        )
                    }
                },
                // 播放按钮底部进度条: 取"继续观看"目标集的进度
                playProgress = state.subjectProgressState.episodeIdToPlay?.let { playProgress[it] },
                // 播放按钮长按: 跳到当前集的选集卡片 (复用网格菜单的 reveal 机制 ——
                // 轮播滚到该集并聚焦, 页面随焦点吸附到选集页, 按住的残余确认键由卡片吞掉)
                onLongPressPlay = {
                    (state.subjectProgressState.episodeIdToPlay ?: currentEpisodeId)
                        ?.let { revealEpisodeId = it }
                },
                // 加载中按"有图"排版: 大多数条目有 backdrop, 图到了直接淡入; 确认无 TMDB 图时
                // 竖版封面会顶上来当背景 (heroBackdropUrl), 排版不变 —— 于是只有"连封面都没有"
                // 的条目才落到无图版式 (标题用主题色 + 右侧竖版封面 + 简介挪到 Hero 上).
                // 视频背景模式恒为"有图"排版 (视频画面就是背景, 竖版封面会挡住它)
                hasBackdrop = videoBackground || heroBackdropUrl != null || !backdropResolved,
                onCoverImageSuccess = onCoverImageSuccess,
                displaySummary = displaySummary,
                // 收藏钮右侧的"选集"圆钮 + 锚定其下的快速跳转网格菜单
                episodeGridCapsule = {
                    Box {
                        TvCapsuleButton(
                            onClick = { showEpisodeGrid = true },
                            icon = { Icon(Icons.Rounded.GridView, contentDescription = null) },
                            label = { Text(stringResource(Lang.subject_details_episodes), softWrap = false) },
                            modifier = Modifier
                                .focusRequester(anchors.episodeGridEntry)
                                .onFocusChanged {
                                    anchors.onAnchorFocusChanged(
                                        TvDetailsFocusAnchor.EPISODE_GRID_ENTRY, it.isFocused,
                                    )
                                },
                        )
                        FocusEpisodeGridDropdown(
                            expanded = showEpisodeGrid,
                            // 网格是数字方块快速跳转, 保持正片/特别篇分组 (对应旧版选集对话框)
                            episodes = mainEpisodes,
                            specialEpisodes = specialEpisodes,
                            currentEpisodeId = currentEpisodeId,
                            episodeRuntimes = episodeRuntimes,
                            onEpisodeClick = {
                                showEpisodeGrid = false
                                onPlay(it.episodeId)
                            },
                            // 返回键正常关闭: 焦点还给入口圆钮, 不跳转
                            onDismissRequest = {
                                showEpisodeGrid = false
                                anchors.send(TvDetailsFocusAnchor.EPISODE_GRID_ENTRY)
                            },
                            // 长按 (按住 OK) 某集方格: 轮播跳到该集, 焦点落到卡片上并触发选集区吸附滚动
                            onEpisodeLongClick = { item ->
                                showEpisodeGrid = false
                                revealEpisodeId = item.episodeId
                            },
                            onCacheClick = onClickCache,
                        )
                    }
                },
                // 占满首屏, 信息带贴底
                modifier = Modifier.height(heroHeight)
                    // 焦点回到 Hero 信息带时滚回页面顶部, 否则标题永远滚不回来
                    // (滚动仅由焦点元素的 BringIntoView 驱动, 而标题不可聚焦)
                    .onFocusChanged {
                        if (it.hasFocus) {
                            backLevel = TvDetailsSection.HERO
                            scope.launch { scrollState.animateScrollTo(0) }
                        }
                    },
            )
            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
            // 水平留白不加在区块列上, 由各区块自理: 选集轮播的卡片行要一直画到屏幕右边缘
            // (出血, 停靠留边由轮播内部 contentPadding 提供), 其余区块照常留边
            Column(
                verticalArrangement = Arrangement.spacedBy(layoutParams.sectionSpacing),
            ) {
            if (videoBackground) {
                // ---- 播放器内嵌变体: 页序为 介绍页 -> 其余区块 ----
                // 选集条已移入播放器控制层 (图标行下方, Prime 形态), 不在本页.
                // 介绍页: 整屏吸附区块, 无操作按钮 (播放/选集/收藏/缓存/外链全部由
                // 播放器控制栏承担), 只有 标题 / 简介+封面 / 单排信息带
                SnapOnFocusSection(
                    scrollState,
                    layoutParams.sectionSpacing,
                    // 内嵌变体的介绍页就是本页最顶层 (对应独立页的海报页)
                    onFocused = { backLevel = TvDetailsSection.HERO },
                ) {
                    TvEmbeddedHeroPage(
                        state = state,
                        info = info,
                        displaySummary = displaySummary,
                        comments = comments,
                        commentCount = commentCount,
                        onShowComments = onShowComments,
                        // 上/下边缘接线 (上回播放器选集条, 下到关联条目区) 全走路由
                        sectionNav = sectionNav,
                        // 点击标签跳转搜索: 标记返回时要把焦点恢复到该标签
                        onClickTag = {
                            tagsRestorePending = true
                            onClickTag(it)
                        },
                        browseMode = tagsBrowseMode,
                        onBrowseModeChange = { tagsBrowseMode = it },
                        focusedTagIndex = focusedTagIndex,
                        onFocusedTagIndexChange = { focusedTagIndex = it },
                        restorePending = tagsRestorePending,
                        onRestoreConsumed = { tagsRestorePending = false },
                        anchors = anchors,
                        wallRestoreIndex = tagRestoreIndex,
                        horizontalPadding = pad,
                        modifier = Modifier.height(heroHeight),
                    )
                }
            } else SnapOnFocusSection(
                scrollState,
                EPISODES_PAGE_VERTICAL_MARGIN,
                onFocused = { backLevel = TvDetailsSection.EPISODES },
            ) {
            // 选集整页: 上半 = 完整标题 + 简介 (截断, 占满剩余高度) + 右侧竖版封面,
            // 下半 = 选集轮播; 合起来正好一屏 (上下留 EPISODES_PAGE_VERTICAL_MARGIN).
            // 封面尺寸从整页高度推出 (而非上半区高度): 高 = 整页 x TV_EPISODES_COVER_HEIGHT_FRACTION,
            // 锚定右上, 超出上半区的部分向下延伸 (不占布局高度, 不推挤轮播); 上半区的
            // 标题/简介与下方轮播的小标题/集简介都以"封面宽 + 32dp 间距"收右边界 ——
            // 四者右缘对齐到同一条线, 全部不与封面重叠
            val episodesCoverHeight = episodesPageHeight * TV_EPISODES_COVER_HEIGHT_FRACTION
            val episodesTextEndReserve = if (info.imageLarge.isNotBlank()) {
                episodesCoverHeight * COVER_WIDTH_TO_HEIGHT_RATIO + 32.dp
            } else {
                0.dp
            }
            Column(
                // 整页高度收窄 EPISODES_PAGE_CONTENT_LIFT: 上半是 weight(1f) 的简介, 收窄只吃掉
                // 简介文字下方的留白 (文字顶对齐不动), 把轮播及其后区块整体上移. 封面仍按原
                // episodesPageHeight 计算 (TopEnd 无界锚定不占布局高度), 不受影响.
                Modifier.height(episodesPageHeight - EPISODES_PAGE_CONTENT_LIFT)
                    // 选集页两个锚点的到位确认 (轮播行经 focusRestorer 委托,
                    // 焦点落进区域子树即算到位). 返回键分层的层级记忆由本区块的
                    // SnapOnFocusSection(onFocused) 上报, 不在这里
                    .onFocusChanged {
                        anchors.onAnchorFocusChanged(TvDetailsFocusAnchor.EPISODES_CAROUSEL, it.hasFocus)
                        anchors.onAnchorFocusChanged(TvDetailsFocusAnchor.EPISODES_SUMMARY, it.hasFocus)
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = pad)
                    // 从上方 (Hero) 向下进入本区域时不要停在简介的展开按钮上, 直接送到选集卡片:
                    // 按钮贴右缘、又在卡片行上方, 空间搜索向下必然先命中它. 从下方 (卡片按上键,
                    // 走显式 upFocus) 进入时方向不是 Down, 不受影响.
                    .focusProperties {
                        onEnter = {
                            if (requestedFocusDirection == FocusDirection.Down) {
                                runCatching { anchors.episodesCarousel.requestFocus() }
                            }
                        }
                    }
                    .focusGroup(),
            ) {
                Column(
                    Modifier.fillMaxHeight().padding(end = episodesTextEndReserve),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        info.displayName,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 固定占满标题下的剩余高度 (两种模式尺寸一致);
                    // 聚焦后按确认键进入阅读模式 (上下键滚动 + 右侧滚动条).
                    // 简介为空 (未开播条目常见, 可能连分集都没有) 时兜底显示"暂无信息",
                    // 保持本块始终可聚焦 —— 否则选集页可能没有任何焦点目标, 向下导航整页跳过
                    TvTruncatedSummary(
                        displaySummary.ifBlank { stringResource(Lang.subject_details_no_summary) },
                        dialogTitle = info.displayName,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        // 无分集 (未开播条目) 时展开按钮是整页唯一的焦点目标, 简介不长也要渲染
                        alwaysShowExpand = episodes.isEmpty(),
                        // 上报按钮有没有渲染: 卡片的上键只在它存在时才指过去 (见 upFocus)
                        onExpandButtonPresenceChange = { summaryExpandPresent = it },
                        // EPISODES_SUMMARY 锚点挂在按钮上 (到位确认由外层 Column 的 onFocusChanged 做)
                        expandFocusRequester = anchors.episodesSummary,
                        // 按钮再往上: 显式回上一区块 (Hero), 同样不指望空间搜索
                        onNavigateUp = if (sectionNav.canMoveUp(TvDetailsSection.EPISODES)) {
                            { sectionNav.moveUp(TvDetailsSection.EPISODES) }
                        } else null,
                    )
                }
                if (info.imageLarge.isNotBlank()) {
                    AsyncImage(
                        info.imageLarge,
                        contentDescription = null,
                        Modifier
                            .align(Alignment.TopEnd)
                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            .height(episodesCoverHeight)
                            .aspectRatio(COVER_WIDTH_TO_HEIGHT_RATIO)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            FocusEpisodeCarousel(
                episodes = episodes,
                horizontalPadding = pad,
                // 小标题行/集简介行与上半区文字共用右边界 (给封面让位)
                endPadding = pad + episodesTextEndReserve,
                // 无图的集用玻璃底: 本页底下压着 backdrop, 实心底色会把图整块盖掉
                // (同页的标签/信息带按钮本来就是这个底)
                glass = true,
                // 聚焦集简介用阅读模式组件: 平时按高度截断, 按确认键进入滚动阅读;
                // 视口只有两行高, 一次滚一行
                // 集简介: 3 行截断, 不可聚焦 (全文在长按卡片的本集详情弹窗里) ——
                // 正文让位后从上方到卡片只需一次下键
                descContent = { desc, _ ->
                    TvTruncatedSummary(
                        desc,
                        dialogTitle = null,
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        // contentPadding 用默认值 (共用常量): 两块正文左右缘对齐,
                        // 且与右侧时长/日期行对行齐平
                        maxLines = TV_EPISODE_DESC_MAX_LINES,
                        minLines = TV_EPISODE_DESC_MAX_LINES,
                    )
                },
                currentEpisodeId = currentEpisodeId,
                onEpisodeClick = { onPlay(it.episodeId) },
                episodeStills = tmdbEpisodeStills,
                playProgress = playProgress,
                episodeRuntimes = episodeRuntimes,
                episodeOverviews = episodeOverviews,
                // 长按卡片: 标记看过/取消看过
                onSetEpisodeCollectionType = { item, type ->
                    onEpisodeCollectionUpdate(
                        SetEpisodeCollectionTypeRequest(state.subjectId, item.episodeId, type),
                    )
                },
                // 网格菜单关闭后跳到菜单里聚焦的那一集
                revealEpisodeId = revealEpisodeId,
                onRevealConsumed = { revealEpisodeId = null },
                // 返回键分层: 选集之下的区域按返回把焦点送回轮播卡片
                rowFocusRequester = anchors.episodesCarousel,
                // 卡片按下键显式送往下一区块 (关联条目/评价); 无下一区块时为 null 不接线
                downFocus = sectionNav.downTargetFrom(TvDetailsSection.EPISODES),
                // 卡片按上键落到简介的展开按钮: 不能交给空间焦点搜索 —— 简介正文已不可聚焦,
                // 上方唯一的目标是贴右缘的小按钮, 从左侧卡片往上找不到几何上方的候选.
                // 按钮没渲染 (简介没被截断) 时传 null: 指向未附着的请求器会变成"按了没反应",
                // 交回空间搜索至少还能跳出本区块
                upFocus = anchors.episodesSummary.takeIf { summaryExpandPresent },
                // 不再有"选集"标题行: 该位置改放聚焦集的小标题 (见 FocusEpisodeCarousel),
                // "看过/全X话"连载进度与 Hero 重复已去掉
            )
            }
            }
            // ---- 角色 / 制作人员 (仅独立页; 内嵌变体是精简版, 这两类内容由播放器
            // 胶囊面板承担). "查看全部"与人物点击均为 TV 居中弹窗形态
            // (ViewAllSheet/PeoplePreview 已按平台分支). 两块共用一个吸附区块:
            // 焦点从区块外进入时角色行吸顶, 在角色/制作人员之间移动不再重新吸附
            // (最小滚动逐步露出). 空数据时区块自身不渲染 (Section 内部 early return),
            // sectionNav 的存在性与之同步, 跨区块下键自动跳过.
            if (exposedCharacters != null && allCharacters != null && exposedStaff != null && allStaff != null &&
                (exposedCharacters.itemCount > 0 || exposedStaff.itemCount > 0)
            ) {
                SnapOnFocusSection(
                    scrollState,
                    layoutParams.sectionSpacing,
                    onFocused = { backLevel = TvDetailsSection.CHARACTERS },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(layoutParams.sectionSpacing)) {
                        CharactersSection(
                            exposedCharacters, allCharacters, totalCharactersCount,
                            modifier = Modifier
                                // 区块进入落点 (上方选集卡片下键经路由落到第一个头像)
                                .focusRequester(sectionNav.entry(TvDetailsSection.CHARACTERS))
                                .focusGroup(),
                            // 水平留白走行内 contentPadding 而**不是**外层 padding: 卡片行要
                            // 保持全宽出血, 外层 padding 会把行的左边界一起右移, 于是向左滑过
                            // 停靠位的卡片正好在停靠线上被硬裁出一条边 (同选集轮播, 见本页
                            // 顶部 Column 的注释). 标题由区块内部按同一留白对齐.
                            contentPadding = PaddingValues(horizontal = pad),
                            // 卡片下键显式送往下一区块 (跨区块空间搜索不可靠)
                            downFocus = sectionNav.downTargetFrom(TvDetailsSection.CHARACTERS),
                        )
                        Box(
                            Modifier
                                .focusRequester(sectionNav.entry(TvDetailsSection.STAFF))
                                .focusGroup()
                                .padding(horizontal = pad),
                        ) {
                            StaffSection(
                                exposedStaff,
                                allStaff,
                                totalStaffCount,
                                gridColumns = layoutParams.staffGridColumns,
                                downFocus = sectionNav.downTargetFrom(TvDetailsSection.STAFF),
                            )
                        }
                    }
                }
            }
            // 作品信息 + 关联条目 + 评价 (独立页): 同一个吸附区块 —— 信息表不可聚焦且
            // 排在区块顶, 焦点下到关联条目时区块吸顶, 信息表正好完整露出在关联条目上方.
            // 内嵌变体不放信息表 (播放器控制层已有), 评价预览也已并入介绍页 (标签墙下方),
            // 只剩关联条目, 无关联时整块不组合 (上方区块即页面终点, 下键无落点属预期).
            if (!videoBackground || related.itemCount > 0) {
                SnapOnFocusSection(
                    scrollState,
                    layoutParams.sectionSpacing,
                    onFocused = { backLevel = TvDetailsSection.BELOW },
                ) {
                    Column(
                        // 区块进入落点挂在根焦点组上: requestFocus 经 enter 落到
                        // 第一个可聚焦子项 (关联卡片; 无关联时是评价区), 不必按内容分情况挂
                        Modifier
                            .focusRequester(sectionNav.entry(TvDetailsSection.BELOW))
                            .focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(layoutParams.sectionSpacing),
                    ) {
                        if (!videoBackground) {
                            Column(
                                Modifier.padding(horizontal = pad),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                // 收藏统计与标签已上移到 Hero 信息带中列
                                SectionHeader(stringResource(Lang.subject_details_info))
                                // 集数只算正片 (episodes 含特别篇)
                                SubjectInfoTable(info, mainEpisodeCount = mainEpisodes.size.takeIf { it > 0 })
                            }
                        }
                        if (related.itemCount > 0) {
                            // TV 上用横向单行 rail 而非多行网格 (锚位条: 聚焦卡停在停靠位)
                            Column(
                                Modifier
                                    // 独立页: 关联卡片上键回制作人员 (中间隔着不可聚焦的
                                    // 信息表, 空间搜索不可靠). 内嵌变体维持原空间搜索行为
                                    .ifThen(!videoBackground) {
                                        tvSectionEdge(sectionNav, TvDetailsSection.BELOW, up = true)
                                    },
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                // 留白只加在标题上; 卡片行全宽出血, 停靠留边由行内
                                // contentPadding 提供 (外层 padding 会在停靠线上硬裁离场卡)
                                SectionHeader(
                                    stringResource(Lang.subject_details_related_subjects),
                                    modifier = Modifier.padding(horizontal = pad),
                                )
                                RelatedSubjectsLazyRow(
                                    related,
                                    onClick = rememberNavigateToRelatedSubject(),
                                    itemWidth = 150.dp,
                                    spacing = 20.dp,
                                    contentPadding = PaddingValues(horizontal = pad),
                                )
                            }
                        }
                        if (!videoBackground) {
                            ReviewsPreviewSection(
                                comments, commentCount, onShowAll = onShowComments,
                                modifier = Modifier.padding(horizontal = pad),
                            )
                        }
                    }
                }
            }
            }
            }
        }
        }
        }
    }
}

/**
 * 详情页左缘 overlay 导航栏, 仅横屏海报首屏显示: 与主页侧边栏完全同一实现 ([TvNavigationSideRail]).
 * 条目也与主页一致 (头像 → 用户信息页 / 搜索 / 探索 / 收藏 / 缓存 / 设置); 头像 selfInfo 就地取.
 * 详情页可经关联条目无限嵌套, 返回键只逐层退, 故本栏在条目上按返回键/右键把焦点还给 Hero 播放按钮
 * (由 [onExitToHero] 处理), 作为一键回主页的逃生通道.
 */
@Composable
private fun TvDetailsSideRail(
    onExitToHero: () -> Unit,
    modifier: Modifier = Modifier,
    scrimColor: Color? = null,
) {
    val navigator = LocalNavigator.current
    // 详情页不显示头像/用户名 (selfInfo = null), 但保留头像槽位使其余按钮位置不变
    TvNavigationSideRail(
        selfInfo = null,
        onAvatarClick = {},
        onExitFocus = onExitToHero,
        scrimColor = scrimColor,
        // 与主页同一份条目, 只差点击行为: 切 tab 前先弹掉整个嵌套栈回主页
        items = buildTvRailItems(
            onSearch = { navigator.navigateSubjectSearch() },
            onNavigateToPage = { navigator.popBackOrNavigateToMain(it) },
            onSettings = { navigator.navigateSettings() },
        ),
        modifier = modifier,
    )
}

/**
 * 图标按钮的字形 (glyph) 尺寸: 侧边栏与 Hero 圆钮共用. 配 32dp 容器,
 * 即 M3 extra-small icon button 规格 (20dp icon / 32dp container).
 */
private val TV_ICON_GLYPH_SIZE = 20.dp

/** 详情页侧边栏遮罩: surface 向 surfaceTint (封面取色动态主色) 的偏移比例, 调大主题色更浓. */
private const val TV_DETAILS_RAIL_SCRIM_TINT = 0.35f

/**
 * 详情页侧边栏遮罩向黑压深的比例 —— 浅色 (白天) 主题档.
 * 调小更浅 (0 = 不压深, 纯主题色面板); 白天面板浅、文字图标是深色 (onSurface), 越浅反而对比越高.
 */
private const val TV_DETAILS_RAIL_SCRIM_DARKEN_LIGHT = 0.06f

/** 详情页侧边栏遮罩向黑压深的比例 —— 深色 (黑夜) 主题档. 深色底配浅色文字, 压深无碍可读. */
private const val TV_DETAILS_RAIL_SCRIM_DARKEN_DARK = 0.35f

/**
 * Hero 标题顶部留白的视觉补偿: 标题列自带 top 8dp + headlineLarge 行高顶部内衬约 12dp,
 * 从名义顶部留白中减去, 使字形上缘到屏幕上边界的距离 ≈ 字形左缘到左边界的距离.
 */
private val TV_HERO_TITLE_TOP_TRIM = 20.dp

/** 展开按钮的圆角. */
private val TV_SUMMARY_EXPAND_CORNER = 8.dp

/**
 * 展开按钮常态底色的不透明度 ("玻璃"): 半透明到能透出底下的背景图, 又足以把文字从图上托起来.
 * 全透明的话按钮只剩文字, 在花哨的 backdrop 上认不出是个可按的东西.
 */
private const val TV_SUMMARY_EXPAND_GLASS_ALPHA = 0.4f

/** 选集信息行里剧集简介的行数上限 (全文在长按卡片的本集详情弹窗里). */
private const val TV_EPISODE_DESC_MAX_LINES = 3

/**
 * 截断简介: 正文**不可聚焦**, 溢出时右下角出现展开按钮, 按下开纯文字弹窗读全文.
 *
 * 为什么正文不参与焦点: TV 上每个焦点停留点都要一次按键, 而正文一旦可聚焦就有歧义 ——
 * 「按下键是滚动文字还是移到下一行?」主流流媒体 (Netflix / Prime Video / Disney+) 一律不让正文
 * 可聚焦, 全文放在显式入口后面的弹层里. 弹窗是模态, 里面没有别的焦点目标, 上下键滚动天然无歧义.
 *
 * 尾部按 [DETAILS_TEXT_END_RESERVE] **恒定**预留 (不随是否截断变化): 否则「是否截断」由排版
 * 决定、而预留又会改变排版, 互为因果会抖动. 该常量与集简介共用, 两块正文宽度因此完全一致.
 */
@Composable
private fun TvTruncatedSummary(
    summary: String,
    /** 全文弹窗的标题; null = 不提供展开入口 (全文在别处, 如长按卡片的本集详情弹窗). */
    dialogTitle: String?,
    modifier: Modifier = Modifier,
    /** 文字样式; null 用 bodyMedium. */
    style: TextStyle? = null,
    /** 块内边距; 与集简介右侧时长/日期的上下内收共用同一常量 (两者要行对行齐平). */
    contentPadding: Dp = DETAILS_TEXT_CONTENT_PADDING,
    /** 最大行数; null = 按父级给的高度截断 (占满剩余空间). */
    maxLines: Int? = null,
    /**
     * 最小行数: 恒定预留这么多行的高度, 内容再短也不缩 —— 切集时简介长短不同, 不预留会让
     * 下方卡片行跳动. 与 [maxLines] 取同值即「固定 N 行」.
     *
     * 用 minLines 而不是给容器写死 dp: 排版真正的约束是容器高度, 标称行高又摊不平首行的字体
     * 内衬, 算出来的 dp 只要差几像素末行就被裁掉 (表现为设了 maxLines=3 却只显示 2 行).
     */
    minLines: Int? = null,
    /**
     * 即使没被截断也渲染展开按钮. 调用方需要本块提供页面**唯一**焦点目标时传 true
     * (无分集的条目 / 播放器内嵌介绍页), 否则整页可能没有任何可聚焦元素, 向下导航会整页跳过.
     */
    alwaysShowExpand: Boolean = false,
    /** 展开按钮的外部焦点请求器 (焦点锚点挂载点). */
    expandFocusRequester: FocusRequester? = null,
    onExpandFocusChanged: ((Boolean) -> Unit)? = null,
    /** 展开按钮上键的显式出口 (内嵌介绍页: 回播放器选集条). */
    onNavigateUp: (() -> Unit)? = null,
    /**
     * 上报当前是否渲染了展开按钮. 调用方据此决定别处的按键落点要不要指向它 ——
     * 按钮不存在时那个请求器未附着, 指过去会变成"按了没反应".
     */
    onExpandButtonPresenceChange: ((Boolean) -> Unit)? = null,
) {
    val textStyle = style ?: MaterialTheme.typography.bodyMedium
    // summary 变化 (切集 / 兜底简介到达) 时重新判定截断
    var truncated by remember(summary) { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    // 弹窗关闭后焦点归还按钮 (Dialog 关闭不会自动归还)
    val expandFocus = remember { FocusRequester() }
    var expandFocused by remember { mutableStateOf(false) }
    var restorePending by remember { mutableStateOf(false) }
    LaunchedEffect(restorePending) {
        if (!restorePending) return@LaunchedEffect
        resolveFocusRepeatedly(arrived = { expandFocused }) {
            runCatching { expandFocus.requestFocus() }
        }
        restorePending = false
    }

    val showExpand = dialogTitle != null && (truncated || alwaysShowExpand)
    LaunchedEffect(showExpand) { onExpandButtonPresenceChange?.invoke(showExpand) }
    Box(modifier) {
        // 内层这一圈 Box 只包住正文的高度. 按钮要贴的是**正文末行**而不是本块的下边界 ——
        // 本块常被 weight 拉满剩余空间, 而正文按整行收敛, 下面必然余出一截; 直接对齐外层
        // BottomEnd 会掉到那一截的底部, 离末行老远.
        Box(Modifier.fillMaxWidth()) {
            Text(
                summary,
                Modifier.fillMaxWidth()
                    .padding(contentPadding)
                    // 只有可能出按钮时才在这里留位: 没有展开入口的正文块 (集简介, 全文在长按
                    // 弹窗里) 由调用方自己按同一个常量留 —— 那截预留归时长/日期用
                    .ifThen(dialogTitle != null) { padding(end = DETAILS_TEXT_END_RESERVE) },
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxLines ?: Int.MAX_VALUE,
                minLines = minLines ?: 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { truncated = it.hasVisualOverflow },
            )
            if (showExpand) {
                TvSummaryExpandButton(
                    onClick = { showDialog = true },
                    onNavigateUp = onNavigateUp,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        // 与正文同一内边距: 按钮下边界与正文末行下边界齐平
                        .padding(contentPadding)
                        .focusRequester(expandFocus)
                        // 外部锚点请求器 (可空): 与内部请求器同挂一个节点
                        .let { m -> expandFocusRequester?.let(m::focusRequester) ?: m }
                        .onFocusChanged {
                            expandFocused = it.isFocused
                            onExpandFocusChanged?.invoke(it.hasFocus)
                        },
                )
            }
        }
    }
    if (showDialog && dialogTitle != null) {
        AniScrollableTextDialog(
            title = dialogTitle,
            text = summary,
            onDismissRequest = {
                showDialog = false
                restorePending = true
            },
        )
    }
}

/**
 * 简介右下角的展开按钮: 圆角矩形 + 文字, 常态半透明玻璃底, 聚焦时填充主题色.
 *
 * 用文字而不是省略号图标: 图标要靠猜, 文字直接说明按下去会发生什么. Apple TV 的做法是在截断
 * 的正文末尾直接接一个 "... More" (tvOS 社区据此做了 TvOSMoreButton 组件), 即入口就在文字被
 * 切断的那个位置 —— 这里沿用同一逻辑, 把它放进正文块的右下角.
 */
@Composable
private fun TvSummaryExpandButton(
    onClick: () -> Unit,
    onNavigateUp: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(TV_SUMMARY_EXPAND_CORNER),
            color = if (focused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = TV_SUMMARY_EXPAND_GLASS_ALPHA)
            },
            interactionSource = interactionSource,
            modifier = onNavigateUp?.let { up ->
                modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                        up()
                        true
                    } else {
                        false
                    }
                }
            } ?: modifier,
        ) {
            Text(
                stringResource(Lang.subject_details_show_more),
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                // 玻璃底上用 onSurface (而非正文的 onSurfaceVariant): 底色已被压深, 弱化色会糊掉
                color = if (focused) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

/**
 * Hero 信息带中列: 左 = 统计数字 + 连载信息 (总高对齐左列按钮块); 右 = 标签墙 (三行截断).
 *
 * 标签平时即可聚焦/点击; 放不下时"显示更多"跟在最后一个可见标签右边 (FlowRow overflow).
 * 按下"显示更多"弹出标签菜单 (Popup, 同选集网格菜单形态): 尽可能显示全部标签,
 * 放不下时纵向导航自动滚动; Popup 独立于页面, 页面绝不会跟着滚. 返回键关闭菜单,
 * 焦点回到"显示更多".
 *
 * 点击标签跳转搜索后返回本页: [browseMode] (菜单开合)/[focusedTagIndex]/[restorePending]
 * 由调用方 rememberSaveable 保留, 重组时菜单原样恢复, 焦点直接回到最后聚焦的标签上.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvHeroInfoColumn(
    state: SubjectDetailsState,
    info: SubjectInfo,
    onClickTag: (Tag) -> Unit,
    browseMode: Boolean,
    onBrowseModeChange: (Boolean) -> Unit,
    /** 最后聚焦的标签下标 (-1 无), 跨页面往返恢复焦点用. */
    focusedTagIndex: Int,
    onFocusedTagIndexChange: (Int) -> Unit,
    /** 为 true 时 (点击标签跳转后返回, 菜单开合状态) 菜单打开后恢复焦点到 [focusedTagIndex]. */
    restorePending: Boolean,
    onRestoreConsumed: () -> Unit,
    /** 页面统一焦点锚点调度器: 标签墙恢复 (TAG_WALL) 与菜单关闭归还 (TAG_SHOW_MORE) 走它. */
    anchors: TvDetailsFocusAnchors,
    /** 截断态标签墙的跨页恢复目标下标 (进页快照, -1 = 无): 该标签挂 TAG_WALL 锚点请求器. */
    wallRestoreIndex: Int,
    modifier: Modifier = Modifier,
) {
    val tags = info.tags

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // 左: 连载信息在上, 垂直中心对齐左列 44dp 圆钮行的中心; 统计数字在下,
        // 垂直中心对齐 38dp 播放按钮的中心 (44 + SpaceBetween 自动 10 + 38 与左列几何同构;
        // 三列底对齐, 总高一致, 顶也是对齐的)
        Column(
            Modifier.height(TV_HERO_MIDDLE_HEIGHT),
            verticalArrangement = Arrangement.SpaceBetween,
            // 列宽 = 两块中较宽者, 窄的一块水平居中 —— 连载信息与统计数字的水平中心对齐
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.height(TV_CAPSULE_SIZE).offset(y = TV_AIRING_ALIGN_TRIM),
                contentAlignment = Alignment.CenterStart,
            ) {
                // 两行内容比锚定盒 (圆钮行高, 32dp) 高: 无界测量 + 居中对齐,
                // 超出部分对称溢出而不是被盒子底边裁掉 (圆钮从 44dp 缩小后两行放不下了)
                Column(
                    Modifier.wrapContentHeight(align = Alignment.CenterVertically, unbounded = true),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        renderSubjectSeason(info.airDate),
                        // 连载信息整体比统计数字小一号 (titleSmall / labelMedium)
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        AiringLabel(
                            state.airingLabelState,
                            style = MaterialTheme.typography.labelMedium,
                            progressColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box(
                Modifier.height(38.dp).offset(y = TV_STATS_ALIGN_TRIM),
                contentAlignment = Alignment.CenterStart,
            ) {
                // 同上: 内容高于锚定盒时无界测量, 对称溢出不裁剪
                Box(Modifier.wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)) {
                    TvCompactStatsRow(info.collectionStats)
                }
            }
        }
        // 右: 标签墙, 占剩余宽度. 容器与左列按钮块同高, 内容顶对齐 —— 顶行顶缘 = 块顶 =
        // 连载信息顶缘 (连载信息盒在中列顶部), 超出块高的部分向下溢出.
        TvHeroTagsWall(
            tags = tags,
            onClickTag = onClickTag,
            browseMode = browseMode,
            onBrowseModeChange = onBrowseModeChange,
            focusedTagIndex = focusedTagIndex,
            onFocusedTagIndexChange = onFocusedTagIndexChange,
            restorePending = restorePending,
            onRestoreConsumed = onRestoreConsumed,
            anchors = anchors,
            wallRestoreIndex = wallRestoreIndex,
            modifier = Modifier.weight(1f).height(TV_HERO_MIDDLE_HEIGHT),
            flowModifier = Modifier.fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .offset(y = TV_TAGS_ALIGN_TRIM),
        )
    }
}

/**
 * 标签墙: 三行截断的 FlowRow (行尾"显示更多"按钮, TAG_SHOW_MORE 锚点) + 弹出的完整
 * 标签菜单. 独立详情页信息带 ([TvHeroInfoColumn]) 与播放器内嵌介绍页信息带
 * ([TvEmbeddedHeroPage]) 共用; 几何差异 (定高/对齐微调) 由 [modifier]/[flowModifier] 注入.
 *
 * wrapContentHeight(unbounded) 由调用方按需传入: FlowRow 需用无限高度测量 —— 若受容器
 * 约束, FlowRow 的 overflow 逻辑会把放不下的一整行标签丢掉 (曾导致少一行).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvHeroTagsWall(
    tags: List<Tag>,
    onClickTag: (Tag) -> Unit,
    browseMode: Boolean,
    onBrowseModeChange: (Boolean) -> Unit,
    focusedTagIndex: Int,
    onFocusedTagIndexChange: (Int) -> Unit,
    restorePending: Boolean,
    onRestoreConsumed: () -> Unit,
    anchors: TvDetailsFocusAnchors,
    wallRestoreIndex: Int,
    modifier: Modifier = Modifier,
    flowModifier: Modifier = Modifier.fillMaxWidth(),
    /** 截断行数 (超出进"显示更多"菜单); 内嵌介绍页空间大, 传更大值. */
    maxLines: Int = 3,
) {
    Box(modifier, contentAlignment = Alignment.TopStart) {
        FlowRow(
            flowModifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            maxLines = maxLines,
            overflow = FlowRowOverflow.expandIndicator {
                TvShowMoreTagsButton(
                    onClick = { onBrowseModeChange(true) },
                    // 菜单关闭后焦点归还本按钮 (TAG_SHOW_MORE 锚点)
                    modifier = Modifier
                        .focusRequester(anchors.tagShowMore)
                        .onFocusChanged {
                            anchors.onAnchorFocusChanged(TvDetailsFocusAnchor.TAG_SHOW_MORE, it.isFocused)
                        },
                )
            },
        ) {
            tags.forEachIndexed { i, tag ->
                TvTagChip(
                    tag.name,
                    Modifier
                        // 跨页返回的恢复目标标签挂 TAG_WALL 锚点 (调度器轮询它并确认到位)
                        .then(
                            if (i == wallRestoreIndex) {
                                Modifier.focusRequester(anchors.tagWall)
                            } else Modifier,
                        )
                        .onFocusChanged {
                            if (it.isFocused) onFocusedTagIndexChange(i)
                            if (i == wallRestoreIndex) {
                                anchors.onAnchorFocusChanged(TvDetailsFocusAnchor.TAG_WALL, it.isFocused)
                            }
                        }
                        .clickable { onClickTag(tag) },
                )
            }
        }
        TvTagsMenu(
            expanded = browseMode,
            tags = tags,
            onClickTag = onClickTag,
            initialFocusIndex = if (restorePending && focusedTagIndex >= 0) focusedTagIndex else 0,
            onTagFocused = onFocusedTagIndexChange,
            onRestoreConsumed = onRestoreConsumed,
            onDismissRequest = {
                onBrowseModeChange(false)
                anchors.send(TvDetailsFocusAnchor.TAG_SHOW_MORE)
            },
        )
    }
}

/**
 * "显示更多"弹出的标签菜单: 从锚点上方弹出 (同选集网格菜单的形态与定位), 尽可能显示全部标签;
 * 放不下时纵向移动焦点自动滚动 (菜单内恢复默认 BringIntoView), 可导航到所有标签.
 * Popup 独立于页面滚动容器, 页面不会跟着动. 返回键/点击外部关闭.
 *
 * 需组合在锚点 (标签墙) 所在的 Box 内.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun TvTagsMenu(
    expanded: Boolean,
    tags: List<Tag>,
    onClickTag: (Tag) -> Unit,
    onDismissRequest: () -> Unit,
    /** 打开时聚焦的标签下标 (跨页返回时为上次聚焦的标签, 平时为 0). */
    initialFocusIndex: Int = 0,
    onTagFocused: (Int) -> Unit = {},
    onRestoreConsumed: () -> Unit = {},
) {
    if (!expanded) return
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val gap = with(density) { 8.dp.roundToPx() }
                val x = anchorBounds.left
                    .coerceAtMost(windowSize.width - popupContentSize.width)
                    .coerceAtLeast(0)
                val y = (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0)
                return IntOffset(x, y)
            }
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        // 页面级禁用了 BringIntoView; 菜单内恢复默认行为, 焦点纵向移动时自动滚动
        val defaultBringIntoView = remember { object : BringIntoViewSpec {} }
        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoView) {
            val tagFocus = remember { FocusRequester() }
            var currentFocus by remember { mutableStateOf(-1) }
            var pendingFocus by remember { mutableStateOf(initialFocusIndex.coerceIn(0, tags.lastIndex)) }
            LaunchedEffect(Unit) {
                // 起点快照 + 放弃判据: 用户自己移到别的标签就让路 (理由见 resolveFocusRepeatedly)
                val startFocus = currentFocus
                resolveFocusRepeatedly(
                    attempts = 20, delayMillis = 0,
                    arrived = { currentFocus == pendingFocus },
                    abandon = { currentFocus >= 0 && currentFocus != startFocus && currentFocus != pendingFocus },
                ) {
                    runCatching { tagFocus.requestFocus() }
                }
                pendingFocus = -1
                onRestoreConsumed()
            }
            Surface(
                Modifier.width(560.dp).heightIn(max = 400.dp),
                shape = RoundedCornerShape(16.dp),
                // 半透明容器 (详情页所有弹出菜单统一), 隐约透出下层内容
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MENU_CONTAINER_ALPHA),
                shadowElevation = 8.dp,
            ) {
                FlowRow(
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEachIndexed { i, tag ->
                        TvTagChip(
                            tag.name,
                            Modifier
                                .then(if (i == pendingFocus) Modifier.focusRequester(tagFocus) else Modifier)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        currentFocus = i
                                        onTagFocused(i)
                                    } else if (currentFocus == i) {
                                        currentFocus = -1
                                    }
                                }
                                .clickable { onClickTag(tag) },
                        )
                    }
                }
            }
        }
    }
}


/** 收藏统计: 竖排单元 —— 数字在上 (细字重), 收藏/在看/想看小字在下. */
@Composable
private fun TvCompactStatsRow(
    stats: SubjectCollectionStats,
    modifier: Modifier = Modifier,
    /** 单元排布; 内嵌介绍页传 SpaceBetween (配 fillMaxWidth, 首末单元与海报左右边界对齐). */
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(16.dp),
    /** 文字整体缩放, 数字与下方小字同步 (1 = 原始大小: 数字 titleMedium / 小字 labelSmall). */
    textScale: Float = 0.8f,
) {
    fun TextStyle.scaled(): TextStyle = if (textScale == 1f) this else copy(
        fontSize = fontSize * textScale,
        lineHeight = if (lineHeight.isSpecified) lineHeight * textScale else lineHeight,
    )

    val resolvedNumberStyle = MaterialTheme.typography.titleMedium.scaled()
    val labelStyle = MaterialTheme.typography.labelSmall.scaled()
    Row(modifier, horizontalArrangement = horizontalArrangement) {
        listOf(
            stats.collect to stringResource(Lang.subject_details_stat_collected),
            stats.doing to stringResource(Lang.subject_details_stat_watching),
            stats.wish to stringResource(Lang.subject_details_stat_wish),
        ).forEach { (count, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    remember(count) { groupThousands(count) },
                    style = resolvedNumberStyle,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                )
                Text(
                    label,
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * TV 标签 chip: 无描边, 低透明度主题色玻璃底 (对齐 M3 state-layer 观感), 紧凑内边距.
 * [modifier] 由调用方注入 focusRequester/onFocusChanged/clickable; clip 在最外层,
 * 点击/聚焦指示随圆角裁切.
 */
@Composable
private fun TvTagChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        Modifier
            .clip(TV_TAG_SHAPE)
            .then(modifier)
            .background(tvGlassColor(0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** [TvTagChip] 的圆角. */
private val TV_TAG_SHAPE = RoundedCornerShape(6.dp)

/**
 * 标签墙的"显示更多"小按钮 (跟在最后一个可见标签右边): 聚焦时玻璃底提示.
 *
 * 结构与 [TvTagChip] 完全同构 (Box + 同字号 + 同内边距) -> 高度严格相同, 与标签
 * 最后一行对齐. 不能用可点击 Surface: M3 会给它套最小交互尺寸 (48dp), 占位变高、
 * 可见部分垂直居中, 看起来比标签矮半截且下沉.
 */
@Composable
private fun TvShowMoreTagsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        if (focused) tvGlassColor() else Color.Transparent,
    )
    Box(
        Modifier
            .clip(TV_TAG_SHAPE)
            .then(modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            stringResource(Lang.subject_details_show_more),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * 区块级导航控制:
 * - 焦点从区块外进入 -> 区块顶吸附到屏幕上方 ([snapTopMargin] 处);
 * - 区块内自由导航: 焦点仍在可见范围内则页面完全不动; 焦点会越出可见范围时
 *   最小滚动露出焦点 (边缘留 [SECTION_ITEM_REVEAL_MARGIN] 余量);
 * - 区块内向上滚动的下限是吸附位 —— 永不越过区块顶, 不会露出上一个区块;
 * - 焦点落到区块最上排 (顶缘距区块顶 < [TV_SECTION_TOPMOST_THRESHOLD]) 时,
 *   区块整体回吸到屏幕顶部.
 *
 * 页面级纵向 BringIntoView 已禁用 (见 [SubjectDetailsTvPage]), 纵向滚动全部由本组件
 * 通过 bringIntoViewResponder 显式驱动: focusable 获得焦点时总会发起 bringIntoView
 * 请求, 请求自带焦点元素在本区块内的精确边界, 是唯一可靠的"焦点位置"来源.
 *
 * 区块在滚动内容中的位置是实测的 (屏幕位置 + 已滚距离, 该和在滚动中恒定),
 * 而非按布局参数解析推算 —— 脚手架的顶部留白/insets 等全部自动包含, 不会有固定偏差.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SnapOnFocusSection(
    scrollState: ScrollState,
    /**
     * 吸附后区块顶距屏幕顶的留白. 普通区块传区块间距 (layoutParams.sectionSpacing):
     * 吸附后区块上方只剩区块间的纯空隙, 上一个区块的底边正好压在屏幕顶上, 不露出.
     * 选集整页传 [EPISODES_PAGE_VERTICAL_MARGIN] (整页上下各留同样空隙).
     */
    snapTopMargin: Dp,
    /**
     * 焦点从区块外进入本区块时回调一次 (区块内部移动不重复触发).
     *
     * 页面用它记住"焦点最后落在哪一层", 供返回键分层判定. 之所以要记忆而不是现读焦点:
     * 焦点"不在页面任何地方"是一个独立状态 (弹窗是独立窗口), 现读会把它误判成"在别的层".
     */
    onFocused: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // 区块顶在滚动内容坐标系中的 y: 实测屏幕位置 + 当前滚动量
    var sectionContentY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val snapMarginPx = with(density) { snapTopMargin.toPx() }
    val revealMarginPx = with(density) { SECTION_ITEM_REVEAL_MARGIN.toPx() }
    val topmostThresholdPx = with(density) { TV_SECTION_TOPMOST_THRESHOLD.toPx() }
    var sectionFocused by remember { mutableStateOf(false) }
    // 焦点刚从区块外进入, 下一个 bringIntoView 请求执行吸顶 (焦点回调在请求前同步触发)
    var pendingEntrySnap by remember { mutableStateOf(false) }
    val responder = remember(scrollState) {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect = localRect

            override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                val rect = localRect() ?: return
                val sectionTop = sectionContentY
                val snapTarget = (sectionTop - snapMarginPx).coerceAtLeast(0f)
                val itemTop = sectionTop + rect.top
                val itemBottom = sectionTop + rect.bottom
                val viewport = scrollState.viewportSize
                val current = scrollState.value.toFloat()
                val entry = pendingEntrySnap
                pendingEntrySnap = false
                val target = when {
                    // 进入区块 / 焦点在区块最上排: 区块顶吸附到屏幕上方.
                    // 焦点元素上方有大段不可聚焦内容 (如关联条目区顶部的作品信息表) 时
                    // 吸顶可能把焦点推出屏幕下缘, 保底继续下滚到焦点下缘可见
                    entry || rect.top < topmostThresholdPx ->
                        maxOf(snapTarget, itemBottom - viewport + revealMarginPx)
                    // 焦点上缘越出可见范围: 上滚露出, 但不越过区块顶 (不露出上一个区块)
                    itemTop < current + revealMarginPx ->
                        (itemTop - revealMarginPx).coerceAtLeast(snapTarget)
                    // 焦点下缘越出可见范围: 下滚露出
                    itemBottom > current + viewport - revealMarginPx ->
                        itemBottom - viewport + revealMarginPx
                    // 完全可见: 页面不动
                    else -> return
                }
                val rounded = target.roundToInt().coerceAtLeast(0)
                if (rounded != scrollState.value) scrollState.animateScrollTo(rounded)
            }
        }
    }
    Box(
        Modifier
            .onGloballyPositioned {
                // 前提: 滚动视口顶 == root y0 (TV 全屏无顶栏/insets, 成立).
                // 若将来给页面加顶部 padding, 此处需改为减去实测的视口顶 y
                sectionContentY = it.positionInRoot().y + scrollState.value
            }
            .onFocusChanged { state ->
                if (state.hasFocus && !sectionFocused) {
                    pendingEntrySnap = true
                    onFocused?.invoke()
                }
                sectionFocused = state.hasFocus
            }
            .bringIntoViewResponder(responder)
            // 区块焦点围栏: 左右键锁定在区块内 —— 边缘元素按左右时空间搜索找不到同区块目标,
            // 会斜跳到上/下一个区块的元素, 页面跟着滚走, 观感是"按左右跳到了上一页". 这里
            // 统一取消横向离组 (上下键照常跨区块; 程序化 requestFocus 不走此钩子, 返回键
            // 分层的跨区块送焦点不受影响). 区块内部的显式 focusProperties 指路仍然有效.
            .focusProperties {
                onExit = {
                    if (requestedFocusDirection == FocusDirection.Left ||
                        requestedFocusDirection == FocusDirection.Right
                    ) {
                        cancelFocus()
                    }
                }
            }
            .focusGroup(),
    ) { content() }
}

/** 页面内的固定焦点锚点, 统一调度见 [TvDetailsFocusAnchors]. */
private enum class TvDetailsFocusAnchor {
    /** Hero 播放按钮 (进页初始焦点 / 返回键回顶 / 侧边栏退出). */
    HERO_PLAY,

    /** 选集轮播行 (focusRestorer 恢复到上次聚焦的卡片). */
    EPISODES_CAROUSEL,

    /** 选集页简介块 (无分集时返回键分层的兜底目标). */
    EPISODES_SUMMARY,

    /** 选集快速跳转网格的入口圆钮 (网格菜单关闭后焦点归还). */
    EPISODE_GRID_ENTRY,

    /** 标签墙上的恢复目标标签 (点标签进搜索页返回时). */
    TAG_WALL,

    /** 标签墙行尾"显示更多"按钮 (标签菜单关闭后焦点归还). */
    TAG_SHOW_MORE,
}

/**
 * 统一焦点锚点调度器: 页面内所有"把焦点送到某处"的入口 (进页初始焦点 / 返回键三级分层 /
 * 侧边栏退出 / 选集网格与标签菜单关闭后的归还 / 标签墙跨页恢复) 都通过 [send] 发请求,
 * 由单个解析效应 ([resolve]) 消化 —— 轮询 requestFocus, 锚点节点 (或其子树) 获得焦点时
 * 经 [onAnchorFocused] 确认到位即停.
 *
 * 与网格页的落点解析器 (GridFocusController) 不同, 本页为 verticalScroll Column,
 * 锚点全部常驻组合, 解析通常一两帧内完成; 轮询用于兜底页面切换期间其它异步焦点分配
 * 后到抢焦点的竞争 (原标签恢复机制的"多帧断言"由此统一承担), 到位确认避免请求间打架.
 */
@Stable
private class TvDetailsFocusAnchors {
    val heroPlay = FocusRequester()
    val episodesCarousel = FocusRequester()
    val episodesSummary = FocusRequester()
    val episodeGridEntry = FocusRequester()
    val tagWall = FocusRequester()
    val tagShowMore = FocusRequester()

    /** 当前待解析的锚点请求 (锚点 + 序号, 序号使同锚点连续请求也能重新触发); null = 空闲. */
    var pending: Pair<TvDetailsFocusAnchor, Int>? by mutableStateOf(null)
        private set

    // 当前聚焦的锚点集合 (各锚点 onFocusChanged 得失双向上报). 到位判据必须查当前状态
    // 而非一次性事件: 弹窗关闭时系统可能在解析启动前就自行把焦点还给锚点, 事件标志
    // 若被解析起手重置, 对已聚焦节点的 requestFocus 不再产生任何焦点事件 —— 轮询烧满
    // 全部次数, 期间用户移开的焦点每帧被抢回 (表现为关菜单后约 1s 内导航被拉回锚点).
    // 不能拿 requestFocus 的返回当成功: 节点未附着/被拒绝时静默失败, runCatching 照样报成功.
    private val focusedAnchors = mutableSetOf<TvDetailsFocusAnchor>()

    // 事件闩 (兜底): 焦点在两次轮询之间落到锚点又立即被移走时当前状态查不到, 靠它记住.
    // 在 send 重置 (而非 resolve 起手): send 到解析启动隔着一帧, 期间到位的事件不能丢
    private var arrivedLatch = false

    fun send(anchor: TvDetailsFocusAnchor) {
        arrivedLatch = false
        pending = anchor to ((pending?.second ?: 0) + 1)
    }

    /** 锚点 (或其子树) 焦点变化上报: 得失都要报 (到位判据靠当前聚焦状态). */
    fun onAnchorFocusChanged(anchor: TvDetailsFocusAnchor, focused: Boolean) {
        if (focused) {
            focusedAnchors.add(anchor)
            if (pending?.first == anchor) arrivedLatch = true
        } else {
            focusedAnchors.remove(anchor)
        }
    }

    /** 解析循环: 在页面组合内 `LaunchedEffect(anchors.pending)` 调用. */
    suspend fun resolve() {
        val (anchor, _) = pending ?: return
        val requester = when (anchor) {
            TvDetailsFocusAnchor.HERO_PLAY -> heroPlay
            TvDetailsFocusAnchor.EPISODES_CAROUSEL -> episodesCarousel
            TvDetailsFocusAnchor.EPISODES_SUMMARY -> episodesSummary
            TvDetailsFocusAnchor.EPISODE_GRID_ENTRY -> episodeGridEntry
            TvDetailsFocusAnchor.TAG_WALL -> tagWall
            TvDetailsFocusAnchor.TAG_SHOW_MORE -> tagShowMore
        }
        resolveFocusRepeatedly(attempts = 20, arrived = { arrivedLatch || anchor in focusedAnchors }) {
            runCatching { requester.requestFocus() }
        }
        pending = null
    }
}

/** 页面纵向区块, 按导航顺序排列 (跨区块 上/下 键的路由依据, 见 [TvDetailsSectionNav]). */
private enum class TvDetailsSection {
    /** 首屏: 独立页 = Hero (标题/简介/信息带); 内嵌变体 = 介绍页 (海报/简介/标签墙/评价). */
    HERO,

    /** 选集整页 (仅独立页; 内嵌变体的选集条在播放器控制层, 不在本页). */
    EPISODES,

    /** 角色区块 (仅独立页; 内嵌变体由播放器胶囊面板承担). 无数据不组合. */
    CHARACTERS,

    /** 制作人员 (仅独立页; 与角色共用一个吸附区块, 进入区域时角色行吸顶). 无数据不组合. */
    STAFF,

    /**
     * 关联条目 + 独立页的 作品信息表/评价 (信息表不可聚焦, 排区块顶 —— 焦点下到
     * 关联条目时区块吸顶, 信息表正好露出). 无可聚焦内容时上一区块即页面终点.
     */
    BELOW,
}

/**
 * 跨区块纵向导航路由: TV 上跨区块的空间焦点搜索不可靠 (中间隔大段不可聚焦内容时
 * 落错或落空), 区块边缘元素的 上/下 键必须显式送焦点. 过去各处手接 FocusRequester
 * (条件挂载 + 逐参数穿透), 这里统一成一张有序区块表 ——
 *
 * - 每个区块经 [register]/[entry] 提供进入落点 (焦点组容器, requestFocus 经 enter
 *   落到第一个可聚焦子项), 经 [setPresent] 报告当前是否存在 (无内容的区块被跳过);
 * - 边缘元素只声明"我在区块 X 的 上/下 边缘" ([tvSectionEdge] 修饰符, 或把
 *   [downTargetFrom] 挂到 focusProperties), 落点解析 (下一个存在的区块) 全在本类;
 * - 最顶区块再按上走 [onExitTop] 出口 (内嵌变体回播放器选集条);
 *   最底区块按下消费按键 (页面终点, 防空间搜索斜跳到别的区块).
 *
 * 与 [TvDetailsFocusAnchors] 分工: 那边管程序化"送焦点到某处" (进页初始焦点/返回键
 * 分层/菜单关闭归还, 带轮询与到位确认); 这边管方向键驱动的相邻区块移动 (即时, 无轮询).
 * 纵向滚动仍由 [SnapOnFocusSection] 随焦点吸附, 与两者正交.
 *
 * [present] 与 [onExitTop] 是普通字段, 每次组合从头赋值: 事件处理只在按键时读取;
 * 组合期唯一的读者 ([downTargetFrom] 给选集轮播传落点) 与写入同处一个重组作用域
 * (该作用域本就读 paging itemCount, 数量变化必然整体重组), 不需要快照状态.
 */
@Stable
private class TvDetailsSectionNav {
    /** 最顶区块再按上的出口; null = 页顶即终点 (不消费, 交回空间搜索). */
    var onExitTop: (() -> Unit)? = null

    private val entries = mutableMapOf<TvDetailsSection, FocusRequester>()
    private val present = mutableSetOf<TvDetailsSection>()

    /** [section] 的进入落点请求器 (挂到区块根焦点组; 惰性创建). */
    fun entry(section: TvDetailsSection): FocusRequester =
        entries.getOrPut(section) { FocusRequester() }

    /** 复用已有请求器 (如 [TvDetailsFocusAnchors] 的锚点) 作为 [section] 的进入落点. */
    fun register(section: TvDetailsSection, requester: FocusRequester) {
        entries[section] = requester
    }

    /** 组合期更新: [section] 当前是否存在 (未组合/无内容的区块在路由中被跳过). */
    fun setPresent(section: TvDetailsSection, value: Boolean) {
        if (value) present.add(section) else present.remove(section)
    }

    /** [from] 之下第一个存在区块的进入落点; null = [from] 已是最底 (可挂 focusProperties.down). */
    fun downTargetFrom(from: TvDetailsSection): FocusRequester? =
        TvDetailsSection.entries.firstOrNull { it.ordinal > from.ordinal && it in present }
            ?.let { entry(it) }

    private fun prevPresent(from: TvDetailsSection): TvDetailsSection? =
        TvDetailsSection.entries.lastOrNull { it.ordinal < from.ordinal && it in present }

    fun canMoveUp(from: TvDetailsSection): Boolean =
        prevPresent(from) != null || onExitTop != null

    /**
     * 从 [from] 的下缘向下: 焦点送往下一个存在的区块. 恒返回 true ——
     * 没有下一区块时也消费按键 (页面底缘, 防斜跳; "无关联条目时下键无落点"属预期).
     */
    fun moveDown(from: TvDetailsSection): Boolean {
        downTargetFrom(from)?.let { runCatching { it.requestFocus() } }
        return true
    }

    /** 从 [from] 的上缘向上: 焦点送往上一个存在的区块, 页顶走 [onExitTop]; 返回是否消费. */
    fun moveUp(from: TvDetailsSection): Boolean {
        prevPresent(from)?.let {
            runCatching { entry(it).requestFocus() }
            return true
        }
        onExitTop?.let {
            it()
            return true
        }
        return false
    }
}

/**
 * 声明本元素处于 [section] 的纵向边缘: 声明方向的按键不交给空间焦点搜索,
 * 由 [nav] 显式路由到相邻区块. KeyUp 与 KeyDown 同进退 (释放事件漏给下层会多走一步).
 */
private fun Modifier.tvSectionEdge(
    nav: TvDetailsSectionNav,
    section: TvDetailsSection,
    up: Boolean = false,
    down: Boolean = false,
): Modifier = onPreviewKeyEvent { event ->
    when {
        up && event.key == Key.DirectionUp ->
            if (event.type == KeyEventType.KeyDown) nav.moveUp(section) else nav.canMoveUp(section)

        down && event.key == Key.DirectionDown ->
            // 下缘恒消费 (见 [TvDetailsSectionNav.moveDown]), 释放事件同样吞掉
            if (event.type == KeyEventType.KeyDown) nav.moveDown(section) else true

        else -> false
    }
}

/**
 * Hero 全屏背景图 (页面背景层, 不随内容滚动): 贴顶/贴右出血, 左缘与底缘渐变入页面背景色,
 * 随滚动淡出以免与滚上来的内容争夺可读性.
 */
@Composable
private fun TvHeroBackdrop(
    imageUrl: String,
    scrollState: ScrollState,
    onSuccess: (AsyncImagePainter.State.Success) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 向下滚动逐渐淡出, 但保留半透明而非完全消失
                    val progress = (scrollState.value / HERO_BACKDROP_FADE_DISTANCE.toPx()).coerceIn(0f, 1f)
                    alpha = 1f - progress * (1f - HERO_BACKDROP_MIN_ALPHA)
                    // 底部渐隐用 DstOut 擦除本层 alpha, 需要离屏合成
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // 底部渐隐: 擦除图片自身的透明度, 露出下层的动态渐变背景,
                    // 而不是画一层纯背景色盖住它 (否则浅色主题下是一片突兀的纯白).
                    //
                    // 起点压后 + 底缘留一成不擦: 原来从 0.62 起擦、0.98 擦光, 屏幕下四成完全没有图,
                    // 露出的页面底色在深色主题里近乎纯黑 —— 选集卡片那一带整片发黑, 与上方还有图的
                    // 部分界线分明 (常被当成"多压了一层黑遮罩", 其实是图被擦没了)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.72f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                        blendMode = BlendMode.DstOut,
                    )
                },
        ) {
            AsyncImage(
                imageUrl,
                contentDescription = null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = onSuccess,
            )
            // 左侧暗色 scrim: 保证浮在图上的标题可读
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
}

/**
 * 视频背景模式的遮罩层 (TV 播放器内嵌): 与 [TvHeroBackdrop] 的视觉规则对应, 但方向相反 ——
 * 那边是"擦除背景图露出页面底色", 这边没有图, 直接对下层视频画黑色渐变:
 * 首屏只压底部 (托住选集/文字区) + 左缘 (标题可读), 滚动后整屏渐进变暗.
 * 滚动值在 draw 阶段读取, 只触发重绘不触发重组.
 */
@Composable
private fun TvVideoBackgroundScrim(scrollState: ScrollState) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // 基础整屏压暗: 首屏 (介绍页) 就是满屏文字, 视频原亮度下看不清
                drawRect(Color.Black.copy(alpha = TV_VIDEO_SCRIM_BASE_ALPHA))
                // 滚动后进一步变暗 (滚过 FADE_DISTANCE 后达到 基础 + 附加)
                val progress = (scrollState.value / HERO_BACKDROP_FADE_DISTANCE.toPx()).coerceIn(0f, 1f)
                if (progress > 0f) {
                    drawRect(Color.Black.copy(alpha = progress * TV_VIDEO_SCRIM_SCROLL_EXTRA_ALPHA))
                }
                // 底部渐变 (对应原版 backdrop 底部渐隐位置)
                drawRect(
                    brush = Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        0.98f to Color.Black.copy(alpha = TV_VIDEO_SCRIM_BOTTOM_ALPHA),
                    ),
                )
                // 左缘 scrim: 浮在视频上的标题可读性 (同原版)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = TV_VIDEO_SCRIM_LEFT_ALPHA),
                        0.55f to Color.Transparent,
                    ),
                )
            },
    )
}

/**
 * 视频背景遮罩: 首屏基础整屏压暗 (调大更暗, 文字更清晰但视频更不可见).
 *
 * 调这四个值前先算叠加: 四层都是画在一起的黑, 观感亮度是 `1-(1-a)(1-b)` 连乘, 不是各自的值.
 * 当前一档下: 首屏 0.38, 滚到底 0.48, 底缘 0.66 (滚动后 0.71), 左缘 0.66 —— 都还能透出画面.
 * 之前一档 (0.55/0.25/0.95/0.7) 的底缘叠到 0.98, 等于纯黑, 就是"底部几乎全黑"的来源.
 */
private const val TV_VIDEO_SCRIM_BASE_ALPHA = 0.38f

/** 视频背景遮罩: 滚动后在基础之上叠加的压暗量 (滚过 FADE_DISTANCE 后满额). */
private const val TV_VIDEO_SCRIM_SCROLL_EXTRA_ALPHA = 0.16f

/**
 * 视频背景遮罩: 底部渐变的最深处不透明度.
 *
 * 这层只为对齐独立页 backdrop 的底部渐隐 (那边下面是页面底色, 这边下面是视频),
 * 不承担可读性 —— 底部那点文字已经压在基础层上了, 所以可以给得比别处松.
 */
private const val TV_VIDEO_SCRIM_BOTTOM_ALPHA = 0.45f

/** 视频背景遮罩: 左缘渐变 (标题可读性) 的最深处不透明度. */
private const val TV_VIDEO_SCRIM_LEFT_ALPHA = 0.45f

/** 滚动多远后背景图淡到 [HERO_BACKDROP_MIN_ALPHA]. */
private val HERO_BACKDROP_FADE_DISTANCE = 300.dp

/**
 * 向下滚动后背景图保留的透明度 (不完全消失).
 *
 * 与底缘擦除叠乘: 滚下去之后底部那一带看到的图 ≈ 本值 × 未被擦掉的比例, 0.3 那档在深色主题里
 * 基本等于没有 (选集卡片以下整片近黑). 调大更亮但内容区背后更花.
 */
private const val HERO_BACKDROP_MIN_ALPHA = 0.42f

/** 区块内导航时焦点边缘距屏幕上/下缘的最小可见余量: 焦点越出才滚动, 滚动后留出该余量. */
private val SECTION_ITEM_REVEAL_MARGIN = 24.dp

/**
 * 焦点元素顶缘距区块顶小于此值视为"区块最上排", 聚焦时区块整体回吸到屏幕顶部
 * (须大于区块标题行高度, 小于第二排可聚焦元素的顶缘位置).
 */
private val TV_SECTION_TOPMOST_THRESHOLD = 100.dp

/** 选集整页 (标题+简介+封面+轮播) 吸附后距屏幕上/下边缘的空隙. */
private val EPISODES_PAGE_VERTICAL_MARGIN = 24.dp

/**
 * 选集整页"上半简介 + 轮播"这层相对整页高度的收窄量: 收窄吃掉简介文字下方留白 (文字顶对齐
 * 不动), 把轮播及其后区块整体上移. 调大上移更多, 0 则恢复占满整页. 不影响封面 (按原整页高算).
 */
private val EPISODES_PAGE_CONTENT_LIFT = 16.dp

/**
 * 选集整页右侧竖版封面高度占整页高度的比例. 封面锚定右上, 超出"标题+简介"区的部分
 * 向下延伸到聚焦集小标题/简介右侧 (这些文字均以封面宽收右边界, 不会被盖住).
 */
private const val TV_EPISODES_COVER_HEIGHT_FRACTION = 0.6f

/** Hero 主操作按钮的圆角: 比 M3 默认胶囊更尖. */
private val TV_BUTTON_SHAPE = RoundedCornerShape(8.dp)

/**
 * 圆钮 (收藏 / 选集 / 在 Bangumi 打开) 的容器直径: M3 extra-small icon button 规格
 * (32dp 容器 / 20dp 字形, 见 [TV_ICON_GLYPH_SIZE]), 与左缘侧边栏图标按钮同尺寸.
 * 聚焦填充即容器本身.
 */
private val TV_CAPSULE_SIZE = 32.dp

/** Hero 中列左侧 (统计+连载) 的高度: 与左列 "圆钮行 (44dp) + 间距 (10dp) + 播放按钮 (38dp)" 一致. */
private val TV_HERO_MIDDLE_HEIGHT = TV_CAPSULE_SIZE + 10.dp + 38.dp

// ===== 信息带中列对齐手动微调 (在几何对齐基础上的修正量; 正值向下移, 负值向上移) =====

/** 连载信息 (两行整体) 相对左列三圆钮中心的垂直微调. */
private val TV_AIRING_ALIGN_TRIM = -10.dp

/** 统计数字 (两行整体) 相对播放按钮中心的垂直微调. */
private val TV_STATS_ALIGN_TRIM = -10.dp

/** 标签墙 (整体) 相对左列按钮块 (圆钮行+播放按钮) 整体中心的垂直微调. */
private val TV_TAGS_ALIGN_TRIM = -8.dp

/**
 * 信息带按钮玻璃底的墨色浓度.
 *
 * 信息带所在的背景图底部区域已渐隐、露出 surface 色的页面背景 (见 [TvHeroBackdrop]),
 * 因此按钮底色以 onSurface 为墨色加此透明度: 暗色主题为白色半透明,
 * 浅色主题自动变为深色半透明; 配合不透明 onSurface 内容色, 任意主题下均清晰.
 */
private const val TV_GLASS_ALPHA = GLASS_CONTAINER_ALPHA

/** 阅读模式等大面积底色再减淡一档, 避免大块墨色压住文字. */
private const val TV_GLASS_READING_ALPHA = 0.03f

/**
 * 简介块右侧为阅读模式滚动条预留的宽度. 截断态与阅读态都预留 —— 两种模式文字宽度
 * 完全一致, 换行/每行字数不变 (阅读态视口的整行量化也依赖两态排版一致).
 */
private val TV_READING_SCROLLBAR_RESERVE = 12.dp

/** 阅读模式每次按键滚动的动画时长 (ms): 越小滚得越快. */
private const val TV_READING_SCROLL_ANIM_MS = 120

/** TV 详情页玻璃底色, 见 [glassContainerColor] (选集卡片也用它, 所以放在了 ui-foundation). */
@Composable
private fun tvGlassColor(alpha: Float = TV_GLASS_ALPHA): Color = glassContainerColor(alpha)

/** 按钮聚焦时主色填充的不透明度: 留一点透明让背景透出来, 不至于一块实心色块. */
private const val TV_FOCUSED_CONTAINER_ALPHA = 0.8f

/**
 * 收藏圆钮: 平时只显示当前收藏状态的图标, 聚焦时横向展开状态文字.
 * 点击弹出五态收藏菜单 (DropdownMenu); 设为"看过"时弹出"同时标记所有剧集"对话框.
 */
@Composable
private fun TvCollectionCapsule(
    state: EditableSubjectCollectionTypeState,
    modifier: Modifier = Modifier,
) {
    EditableSubjectCollectionTypeDialogsHost(state)
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    val type = presentation.selfCollectionType
    val action = remember(type) { SubjectCollectionActionsForCollect.find { it.type == type } }
    Box(modifier) {
        TvCapsuleButton(
            // 更新进行中忽略点击但保持可聚焦: enabled=false 会让按钮失去焦点能力, 焦点会飞走
            onClick = { if (!presentation.isSetSelfCollectionTypeWorking) state.showDropdown = true },
            icon = { action?.icon?.invoke() },
            label = {
                if (type == UnifiedCollectionType.NOT_COLLECTED) {
                    action?.title?.invoke()
                } else {
                    Text(renderCollectionTypeAsCurrent(type), softWrap = false)
                }
            },
        )
        EditCollectionTypeDropDown(
            state,
            // 半透明容器 (详情页所有弹出菜单统一)
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MENU_CONTAINER_ALPHA),
        )
    }
}

/**
 * 图标圆钮: 固定 [TV_CAPSULE_SIZE] 正圆, 无底色无描边, 仅主题色图标;
 * 聚焦时填充主题主色 (动态主题下即封面取色), 并在圆钮上方浮现 [label] 纯文字标签
 * 标签不占布局空间, 不推挤周围.
 */
@Composable
private fun TvCapsuleButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val containerColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary.copy(alpha = TV_FOCUSED_CONTAINER_ALPHA)
        else Color.Transparent,
    )
    val contentColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.onPrimary else onSurface,
    )
    Box(modifier) {
        Box(
            Modifier
                .size(TV_CAPSULE_SIZE)
                .onFocusChanged { focused = it.isFocused }
                .clip(CircleShape)
                .background(containerColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                // 20dp 字形: 外层约束把默认 24dp 的 Icon 收到字形尺寸
                Box(Modifier.size(TV_ICON_GLYPH_SIZE), contentAlignment = Alignment.Center) {
                    icon()
                }
            }
        }
        // 聚焦时上方浮现的文字标签: layout(0,0) 不占任何布局空间;
        // 相对圆钮水平居中, 底缘在圆钮上缘之上 8dp
        Box(
            Modifier.layout { measurable, _ ->
                val placeable = measurable.measure(Constraints())
                val anchorWidth = TV_CAPSULE_SIZE.roundToPx()
                layout(0, 0) {
                    placeable.place(
                        x = (anchorWidth - placeable.width) / 2,
                        y = -(placeable.height + 8.dp.roundToPx()),
                    )
                }
            },
        ) {
            AnimatedVisibility(focused, enter = fadeIn(), exit = fadeOut()) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    CompositionLocalProvider(LocalContentColor provides onSurface) {
                        Row { label() }
                    }
                }
            }
        }
    }
}

/**
 * Hero 主操作 (播放) 按钮: 玻璃底圆角矩形, ▶ 图标 + 文字居中,
 * 聚焦时填充主题主色 (动态主题下即封面取色). 当前要播的集有播放进度时,
 * 按钮正下方画一条与按钮同宽的细进度条 (在按钮外部, 不随聚焦反色).
 *
 * [onLongPress] 非 null 时支持长按确认键 (按住到阈值即触发, 不等松开): 详情页用来
 * 跳到当前集的选集卡片. 此时短按的点击改在 KeyUp 触发 (确认键全部在 preview 层消费).
 */
@Composable
private fun TvPlayButton(
    state: SubjectProgressState,
    onPlay: () -> Unit,
    playProgress: Float?,
    modifier: Modifier = Modifier,
    /** 作用于按钮本体 (如 focusRequester); [modifier] 作用于"按钮 + 进度条"整体. */
    buttonModifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val containerColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary.copy(alpha = TV_FOCUSED_CONTAINER_ALPHA)
        else tvGlassColor(),
    )
    val contentColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.onPrimary else onSurface,
    )
    // 长按 (同选集网格/排序格, 共用实现见 tvLongPressKey): 按住到阈值立即触发跳转 (不等松开),
    // 残余按键由目标卡片吞掉 (不是从它起手的手势, 它的 tvLongPressKey 不计数不派发).
    val strings = rememberSubjectStatusStrings()
    Column(modifier) {
        Surface(
            onClick = onPlay,
            modifier = buttonModifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .then(
                    if (onLongPress == null) {
                        Modifier
                    } else {
                        Modifier.tvLongPressKey(onLongPress = onLongPress, onShortPress = onPlay)
                    },
                ),
            shape = TV_BUTTON_SHAPE,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Row(
                // 文字 titleMedium (行高 24sp) 配 38dp 高: 文字与上下边界各留 7dp,
                Modifier.height(38.dp).fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Text(
                    state.buttonText(strings),
                    style = MaterialTheme.typography.titleSmall,
                    softWrap = false,
                )
            }
        }
        val progress = playProgress?.coerceIn(0f, 1f)
        if (progress != null && progress > 0f && progress < 1f) {
            Box(
                Modifier
                    // 进度条嵌在按钮底边内侧 (负偏移整条叠上按钮, 条的下缘与按钮下缘重合);
                    // 不占布局高度 (layout 高度上报 0):
                    // 信息带三列底对齐, 左列底必须恒为播放按钮底 —— 若进度条占高度,
                    // 有观看进度的条目左列会多出高度, 中列所有中心对齐整体歪掉
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, 0) {
                            placeable.place(0, -placeable.height)
                        }
                    }
                    .fillMaxWidth()
                    // 两端缩进按钮圆角半径: 条长 = 按钮底边未被圆角削掉的直线段
                    .padding(horizontal = 8.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(onSurface.copy(alpha = 0.25f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(onSurface),
                )
            }
        }
    }
}

/**
 * Hero 首屏内容 (滚动列内): 标题浮于背景图上 (白色, 图左有暗色 scrim 保证对比);
 * 其余 (元数据 / 评分 / 简介 / 主操作) 下沉到图的底部渐变区自成一段. 背景图见 [TvHeroBackdrop].
 */
@Composable
private fun TvHeroBlock(
    state: SubjectDetailsState,
    info: SubjectInfo,
    selfInfo: SelfInfoUiState,
    onPlay: (episodeId: Int) -> Unit,
    onClickLogin: () -> Unit,
    onClickOpenExternal: () -> Unit,
    horizontalPadding: Dp,
    /** 作用于 Hero 区主操作按钮 (播放) 本体: 页面挂 HERO_PLAY 锚点请求器与到位确认. */
    primaryButtonModifier: Modifier,
    /** 信息带中列内容 (收藏统计/标签墙/连载信息), 由调用方注入 (需要页面级状态). */
    middleColumn: @Composable RowScope.() -> Unit = {},
    /** 是否有全屏横版背景图. 无图时标题用主题色, 且在右侧展示竖版封面. */
    hasBackdrop: Boolean,
    onCoverImageSuccess: (AsyncImagePainter.State.Success) -> Unit,
    modifier: Modifier = Modifier,
    /** 当前要播的集的播放进度 (0..1), 无记录为 null; 播放按钮底部进度条用. */
    playProgress: Float? = null,
    /** 播放按钮长按 (按住确认键到阈值) 的动作; 详情页传"跳到当前集的选集卡片". */
    onLongPressPlay: (() -> Unit)? = null,
    /** 收藏钮右侧的"选集"圆钮 (含锚定其下的网格菜单), 由调用方注入. */
    episodeGridCapsule: @Composable () -> Unit = {},
    /** 展示用简介 (Bangumi 全外文时已替换为 TMDB 中文); 默认用原文. */
    displaySummary: String = info.summary,
) {
    Column(modifier.fillMaxWidth().padding(start = horizontalPadding)) {
        // 上半区: 左 = 标题 (有背景图时白色浮于图上); 右 = 无横版图时的竖版封面,
        // 高度正好撑满 "顶栏按钮之下、信息带之上", 随内容滚出屏幕
        Row(Modifier.weight(1f).fillMaxWidth().padding(end = horizontalPadding)) {
            Column(
                Modifier.weight(1f).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 白色标题浮于背景图上, 图亮部会看不清: 加柔和黑色阴影兜底
                val titleShadow = if (hasBackdrop) {
                    with(LocalDensity.current) {
                        Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            offset = Offset(0f, 1.dp.toPx()),
                            blurRadius = 6.dp.toPx(),
                        )
                    }
                } else null
                Text(
                    info.displayName,
                    style = MaterialTheme.typography.headlineLarge.copy(shadow = titleShadow),
                    color = if (hasBackdrop) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (info.name.isNotBlank() && info.name != info.displayName) {
                    Text(
                        info.name,
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = titleShadow),
                        color = if (hasBackdrop) Color.White.copy(alpha = 0.78f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!hasBackdrop && displaySummary.isNotBlank()) {
                    // 无横版图时标题下方较空: 简介填进来, 放不下省略;
                    // 完整简介看"作品信息"子页面 (此时信息带入口只显示标签, 不重复文字)
                    Text(
                        displaySummary,
                        Modifier.weight(1f, fill = false).padding(top = 8.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!hasBackdrop) {
                AsyncImage(
                    info.imageLarge,
                    contentDescription = null,
                    Modifier
                        .fillMaxHeight()
                        .padding(bottom = 16.dp)
                        .aspectRatio(COVER_WIDTH_TO_HEIGHT_RATIO)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                    onSuccess = onCoverImageSuccess,
                )
            }
        }

        // 信息带: 左 = 圆钮行 / 播放按钮 上下两行; 中 = 统计+标签墙+连载信息;
        // 右 = 完整评分区. 三列底部对齐 (标签墙底缘与评分底缘齐平, 信息带整体贴底的延续).
        // 列间距显式控制 (不用 spacedBy): 左↔中 24; 中↔右 12 —— 标签墙右缘外扩一档,
        // FlowRow 换行的锯齿空白不至于叠上整份间距显得中右之间空一条
        Row(
            Modifier.fillMaxWidth().padding(end = horizontalPadding),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 左列整体提层: 圆钮聚焦时上方浮现的文字标签要盖在上方内容之上.
            // IntrinsicSize.Max: 播放按钮 fillMaxWidth 后与上方"圆钮行 + 连载信息"等宽.
            Column(
                Modifier.zIndex(1f).width(IntrinsicSize.Max),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 上: 圆钮行 — 收藏 + 选集 + 在 Bangumi 打开 (原右上角按钮; TV 上仅详情页有该操作).
                // 间距 8dp: M3 图标按钮排布的标准相邻间距 (容器即聚焦填充, 不会互相贴上)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selfInfo.isSessionValid == false) {
                        // 未登录: 圆钮显示"收藏"图标, 聚焦浮现登录提示, 点击进登录页
                        TvCapsuleButton(
                            onClick = onClickLogin,
                            icon = { SubjectCollectionActions.Collect.icon() },
                            label = {
                                Text(stringResource(Lang.subject_details_login_to_collect), softWrap = false)
                            },
                        )
                    } else {
                        TvCollectionCapsule(state.editableSubjectCollectionTypeState)
                    }
                    // 选集快速跳转 (圆钮 + 下拉网格), 样式与相邻圆钮一致
                    episodeGridCapsule()
                    TvCapsuleButton(
                        onClick = onClickOpenExternal,
                        icon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                        label = { Text("Bangumi", softWrap = false) },
                    )
                }
                // 下: 播放按钮 (下方带播放进度条), 宽度与列同宽
                // (IntrinsicSize.Max: 取"圆钮行 / 按钮文字固有宽"中较大者).
                // offset 微微上移 (纯视觉, 不占布局, 周围组件与三列底对齐的几何全部不动)
                TvPlayButton(
                    state.subjectProgressState,
                    onPlay = { state.subjectProgressState.episodeIdToPlay?.let(onPlay) },
                    playProgress = playProgress,
                    modifier = Modifier.fillMaxWidth().offset(y = (-4).dp),
                    buttonModifier = primaryButtonModifier,
                    onLongPress = onLongPressPlay,
                )
            }
            middleColumn()
            // 右: 评分区 — 分布直方图在上, 评分摘要在下. IntrinsicSize.Max 取两者固有宽度的
            // 较大值: 直方图不会小于自身最小宽度 (压窄会让 "10" 标签折行), 摘要更宽时直方图拉伸同宽
            // 居中: 列宽 = 两者中较宽者 (IntrinsicSize.Max), 窄的一个水平居中 ——
            // 直方图与评分摘要的水平中心对齐
            Column(
                Modifier.padding(start = 12.dp).width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RatingHistogram(
                    info.ratingInfo,
                    Modifier.fillMaxWidth(),
                    barHeight = 36.dp,
                )
                // 直方图紧贴下方评分: 无额外间距 (直方图自身与刻度行间已有 6dp)
                // 只认"自己打开的那次"评分弹窗: 同一个 state 也挂在「查看全部」评论里的写评价上,
                // 不分辨来源的话从那边打开、关闭后焦点会被这里抢过来
                val ratingSource = remember { Any() }
                SubjectRatingSummary(
                    info.ratingInfo,
                    // 评分弹窗关掉之后焦点还回本按钮 (弹窗是独立窗口, 关掉后不保证还回来)
                    Modifier.restoreFocusAfter(state.editableRatingState.isEditingFrom(ratingSource)),
                    onClick = { state.editableRatingState.requestEdit(ratingSource) },
                )
            }
        }
    }
}

/**
 * 播放器内嵌变体的介绍页 (整屏吸附区块, 首屏), 左右两列:
 * - 左列 (定宽): 海报 / 收藏统计 / 评分 (直方图 + 摘要), 自上而下排满;
 * - 右列 (自适应): 标题(+原名) / 连载信息 / 简介 (阅读模式) / 标签墙 (占满简介与
 *   评论之间的空间, 整列宽) / 评论预览 (贴页底, 与下边界留正常空隙).
 *
 * 无任何操作按钮 (播放/选集/收藏/缓存/外链全部由播放器控制栏承担), 本页只展示内容.
 * 简介块带"暂无信息"兜底恒可聚焦, 是本页的保底焦点目标 (也是 EPISODES_SUMMARY
 * 锚点在内嵌变体的挂载点: 进入焦点落在这里).
 */
@Composable
private fun TvEmbeddedHeroPage(
    state: SubjectDetailsState,
    info: SubjectInfo,
    displaySummary: String,
    comments: LazyPagingItems<UIComment>,
    commentCount: Int?,
    onShowComments: () -> Unit,
    /** 跨区块导航路由: 本页上缘 (回播放器选集条) 与下缘 (关联条目区) 的按键接线全走它. */
    sectionNav: TvDetailsSectionNav,
    onClickTag: (Tag) -> Unit,
    browseMode: Boolean,
    onBrowseModeChange: (Boolean) -> Unit,
    focusedTagIndex: Int,
    onFocusedTagIndexChange: (Int) -> Unit,
    restorePending: Boolean,
    onRestoreConsumed: () -> Unit,
    anchors: TvDetailsFocusAnchors,
    wallRestoreIndex: Int,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = 8.dp)
            // 页内焦点向上越界 (简介/评分等顶部元素再按上, 页内没有更高的目标) 时
            // 回到播放器选集条; 页内的向上移动 (标签→简介等) 不触发.
            // 兜底钩子 (设备上不总触发): 主路径是下方左列/简介块的按键拦截
            .focusProperties {
                onExit = {
                    if (requestedFocusDirection == FocusDirection.Up &&
                        sectionNav.moveUp(TvDetailsSection.HERO)
                    ) {
                        cancelFocus()
                    }
                }
            }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        // 左列: 海报 / 收藏统计 / 评分.
        // 列内唯一焦点元素是评分摘要 (上方海报/统计不可聚焦): 按上直接回播放器选集条
        // (按键拦截比 focusProperties.onExit 钩子可靠, 与简介块同一套路)
        Column(
            Modifier.width(TV_EMBEDDED_LEFT_COLUMN_WIDTH)
                .tvSectionEdge(sectionNav, TvDetailsSection.HERO, up = true),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (info.imageLarge.isNotBlank()) {
                AsyncImage(
                    info.imageLarge,
                    contentDescription = null,
                    Modifier.fillMaxWidth()
                        .aspectRatio(COVER_WIDTH_TO_HEIGHT_RATIO)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            // 三个统计单元均匀摊开, 首末单元与海报左右边界对齐;
            // 文字大小由 TV_EMBEDDED_STATS_TEXT_SCALE 统一缩放 (数字与小字同步)
            TvCompactStatsRow(
                info.collectionStats,
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                textScale = TV_EMBEDDED_STATS_TEXT_SCALE,
            )
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RatingHistogram(
                    info.ratingInfo,
                    Modifier.fillMaxWidth(),
                    barHeight = 36.dp,
                )
                // 直方图紧贴下方评分: 无额外间距 (直方图自身与刻度行间已有 6dp)
                val ratingSource = remember { Any() }
                SubjectRatingSummary(
                    info.ratingInfo,
                    Modifier.restoreFocusAfter(state.editableRatingState.isEditingFrom(ratingSource)),
                    onClick = { state.editableRatingState.requestEdit(ratingSource) },
                )
            }
        }
        // 右列: 标题 / 连载信息 / 简介 / 标签墙
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    info.displayName,
                    style = MaterialTheme.typography.headlineLarge,
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
            }
            // 连载信息行 (标题与简介之间)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    renderSubjectSeason(info.airDate),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                AiringLabel(
                    state.airingLabelState,
                    style = MaterialTheme.typography.titleSmall,
                    progressColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TvTruncatedSummary(
                displaySummary.ifBlank { stringResource(Lang.subject_details_no_summary) },
                dialogTitle = info.displayName,
                Modifier.height(TV_EMBEDDED_SUMMARY_HEIGHT).fillMaxWidth(),
                // 展开按钮是本页顶部唯一焦点目标 (HERO 段落的落点 + 进页初始焦点):
                // 简介短到不截断也必须渲染, 否则内嵌介绍页进去没有焦点
                alwaysShowExpand = true,
                // EPISODES_SUMMARY 锚点 (内嵌变体挂载点) + 到位确认
                expandFocusRequester = anchors.episodesSummary,
                onExpandFocusChanged = {
                    anchors.onAnchorFocusChanged(TvDetailsFocusAnchor.EPISODES_SUMMARY, it)
                },
                // 按钮是页面顶部焦点元素: 按上回播放器选集条; 无出口时不传 (交回空间搜索)
                onNavigateUp = if (sectionNav.canMoveUp(TvDetailsSection.HERO)) {
                    { sectionNav.moveUp(TvDetailsSection.HERO) }
                } else null,
            )
            // 标签墙: 占满简介与评论区之间的全部剩余空间 (整列宽)
            TvHeroTagsWall(
                tags = info.tags,
                onClickTag = onClickTag,
                browseMode = browseMode,
                onBrowseModeChange = onBrowseModeChange,
                focusedTagIndex = focusedTagIndex,
                onFocusedTagIndexChange = onFocusedTagIndexChange,
                restorePending = restorePending,
                onRestoreConsumed = onRestoreConsumed,
                anchors = anchors,
                wallRestoreIndex = wallRestoreIndex,
                maxLines = TV_EMBEDDED_TAGS_MAX_LINES,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                flowModifier = Modifier.fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true),
            )
            // 评论预览: 贴介绍页底部 (标签墙 weight 把它压到最下), 与下边界留正常空隙.
            // 下缘接线只挂在卡片行 —— 挂整个区块会把标题行"查看全部"按钮的
            // 下键也吃掉 (导航不到卡片)
            ReviewsPreviewSection(
                comments, commentCount, onShowAll = onShowComments,
                modifier = Modifier.fillMaxWidth()
                    .padding(bottom = TV_EMBEDDED_BOTTOM_MARGIN),
                cardsModifier = Modifier.tvSectionEdge(sectionNav, TvDetailsSection.HERO, down = true),
                // 本页整个浮在视频画面上: 评论卡改半透明黑底, 否则页底是两块不透明的黑砖
                videoBackground = true,
            )
        }
    }
}

/** 内嵌介绍页左列 (海报/收藏统计/评分) 宽度; 海报高按 2:3 从此宽推出. */
private val TV_EMBEDDED_LEFT_COLUMN_WIDTH = 200.dp

/** 内嵌介绍页简介块高度 (其下全部剩余空间给标签墙). */
private val TV_EMBEDDED_SUMMARY_HEIGHT = 150.dp

/** 内嵌介绍页标签墙最大行数 (空间比独立页信息带大, 多显示几行). */
private const val TV_EMBEDDED_TAGS_MAX_LINES = 6

/** 内嵌介绍页评论预览与页底边界的空隙 (页块本身已距屏幕下缘 16dp, 视觉总空隙 ≈ 两者之和). */
private val TV_EMBEDDED_BOTTOM_MARGIN = 24.dp

/** 内嵌介绍页收藏统计文字缩放 (数字与小字同步; 1 = 与独立页 Hero 信息带相同大小). */
private const val TV_EMBEDDED_STATS_TEXT_SCALE = 1f
