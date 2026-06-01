/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.tv

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeRequest
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.subject.details.SubjectDetailsUIState
import me.him188.ani.app.ui.subject.details.sections.FocusEpisodeCarousel
import me.him188.ani.app.ui.subject.details.sections.FocusEpisodeMetaColumn
import me.him188.ani.app.ui.subject.details.sections.mergedEpisodeDesc
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem

// ---- 调参 ----

/** 一行完整显示的卡片数 (卡宽由屏宽反推: 左右各留页面边距, 正好放下这么多张). */
private const val TV_STRIP_VISIBLE_CARDS = 4

/** 卡片间距. */
private val TV_STRIP_CARD_SPACING = 16.dp

/** 选集条展开/收起的滑动动画时长 (毫秒). */
private const val TV_STRIP_SLIDE_MS = 250

/** 聚焦集简介最多显示几行, 放不下的直接截断 (不滚动: 卡片行上下切换很频繁, 动的文字反而干扰). */
private const val TV_EPISODE_DESC_VISIBLE_LINES = 3

/**
 * 播放器控制层里的选集条 (Prime 形态): 复用详情页的选集轮播
 * ([FocusEpisodeCarousel], 缩略图/播放进度/长按标记看过等一应俱全).
 *
 * 仅在展开态渲染 (由 [TvPlayerOverlayState.episodeStripExpanded] 驱动, 图标行按下键
 * 唤出, 平时完全不可见): 控制行隐藏 (调用方处理), 卡片行在上 (无标题行, 一行正好
 * [TV_STRIP_VISIBLE_CARDS] 张完整卡, 聚焦卡左侧露上一张卡切边), 下方是聚焦集简介
 * (自动滚动) + 右侧 时长/播出日期 两行. 点击卡片切换当前播放集并回纯画面;
 * 再按下键进详情层.
 *
 * 分集列表取自 [EpisodeViewModel.episodeListUiStateFlow] (播放器自己的数据路径, 起播必经),
 * 而不是详情状态里的那份 —— 后者要等整套详情组装完, 在起播这一刻能慢到两三秒.
 * 详情状态 (subjectDetailsStateLoader, 进屏已预载) 只供 TMDB 剧照/时长/简介和播放进度,
 * 没到就先无图, 不影响选集.
 *
 * 列表状态按三态上报 [TvPlayerOverlayState.onEpisodeStripStateChanged]: 还没到时下键会等,
 * 只有确认无分集 (未开播/加载失败) 才让图标行下键直通详情页.
 */
@Composable
internal fun TvPlayerEpisodeStrip(
    vm: EpisodeViewModel,
    overlay: TvPlayerOverlayState,
    stripFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val detailsState = vm.episodeDetailsState
    // 兜底触发加载 (正常已在进屏时预载, loader 有"已加载"守卫). 只为下面那些增量信息 ——
    // 分集列表本身不等它
    LaunchedEffect(Unit) {
        detailsState.subjectDetailsStateLoader.load(detailsState.subjectId, detailsState.subjectInfo.value)
    }
    // 分集列表走播放器自己的数据路径 (见 EpisodeViewModel.episodeListUiStateFlow): 那是起播的
    // 必经之路, 必然比整套详情状态先到. 详情状态只供 TMDB 剧照/时长/简介, 没到就先无图
    val episodeList by vm.episodeListUiStateFlow.collectAsStateWithLifecycle()
    val episodes = episodeList?.mainEpisodes.orEmpty()
    val uiState by detailsState.subjectDetailsStateLoader.state.collectAsStateWithLifecycle()
    val state = (uiState as? SubjectDetailsUIState.Ok)?.value
    SideEffect {
        val stripState = when {
            // "还没到"与"没有"必须分开: 前者下键要等, 后者才该直通详情层
            episodeList == null ->
                // 详情状态都加载失败了, 按"确认无分集"上报: 详情层有错误页和重试入口
                if (uiState is SubjectDetailsUIState.Err) TvEpisodeStripState.EMPTY
                else TvEpisodeStripState.LOADING

            episodes.isEmpty() -> TvEpisodeStripState.EMPTY
            else -> TvEpisodeStripState.AVAILABLE
        }
        overlay.onEpisodeStripStateChanged(stripState)
    }
    if (episodes.isEmpty()) return

    // 详情状态未到时挂空流: 各自的初值 (emptyMap) 保持不变, 到了再重新订阅
    val stillsFlow = remember(state) { state?.tmdbEpisodeStillsFlow ?: emptyFlow() }
    val progressFlow = remember(state) { state?.playProgressFlow ?: emptyFlow() }
    val runtimesFlow = remember(state) { state?.tmdbEpisodeRuntimesFlow ?: emptyFlow() }
    val overviewsFlow = remember(state) { state?.tmdbEpisodeOverviewsFlow ?: emptyFlow() }
    val tmdbEpisodeStills by stillsFlow.collectAsStateWithLifecycle(emptyMap())
    val playProgress by progressFlow.collectAsStateWithLifecycle(emptyMap())
    val episodeRuntimes by runtimesFlow.collectAsStateWithLifecycle(emptyMap())
    val episodeOverviews by overviewsFlow.collectAsStateWithLifecycle(emptyMap())

    val navigator = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    // 展示中的集 (聚焦卡, 无聚焦时为当前集), 轮播回调上报; 下方简介/时长/日期跟随它
    var displayed by remember { mutableStateOf<EpisodeListItem?>(null) }

    // 仅展开态渲染: 平时完全不可见 (无 peek), 图标行按下键唤出.
    // 入场从底部整体上滑 (视觉上 = 卡片本来就在进度条下方, 聚焦时上移进画面,
    // 上方控制行同时淡出), 收起反向滑出
    AniAnimatedVisibility(
        visible = overlay.episodeStripExpanded,
        modifier = modifier,
        enter = slideInVertically(tween(TV_STRIP_SLIDE_MS)) { it } + fadeIn(tween(TV_STRIP_SLIDE_MS)),
        exit = slideOutVertically(tween(TV_STRIP_SLIDE_MS)) { it } + fadeOut(tween(TV_STRIP_SLIDE_MS)),
    ) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .onFocusChanged { if (it.hasFocus) overlay.focusRegion = TvPlayerFocusRegion.EPISODES }
            .focusGroup(),
    ) {
        // 卡宽由屏宽反推: 左右各留页面边距, 一行正好 TV_STRIP_VISIBLE_CARDS 张完整卡
        val cellWidth = (this.maxWidth - TV_PLAYER_HORIZONTAL_PAD * 2 -
            TV_STRIP_CARD_SPACING * (TV_STRIP_VISIBLE_CARDS - 1)) / TV_STRIP_VISIBLE_CARDS
        val cellHeight = cellWidth * 9f / 16f
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FocusEpisodeCarousel(
                episodes = episodes,
                // "正在播放" 徽标 + 初始滚动位置 = 当前播放集 (详情页里是"下一集要看的")
                currentEpisodeId = vm.episodeSelectorState.current?.episodeId,
                onEpisodeClick = { item ->
                    // 与详情层选集一致: 就地切换当前播放集; 不在列表里 (特别篇等) 整页导航
                    if (!vm.episodeSelectorState.selectEpisodeId(item.episodeId)) {
                        navigator.navigateEpisodeDetails(vm.subjectId, item.episodeId)
                    }
                    overlay.hideAll()
                },
                episodeStills = tmdbEpisodeStills,
                playProgress = playProgress,
                episodeRuntimes = episodeRuntimes,
                episodeOverviews = episodeOverviews,
                horizontalPadding = TV_PLAYER_HORIZONTAL_PAD,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                cellSpacing = TV_STRIP_CARD_SPACING,
                // 集信息不放卡片行上方: 简介/时长/日期由下方区域展示 (见 onDisplayedChanged)
                showEpisodeInfo = false,
                onDisplayedChanged = { displayed = it },
                // 长按卡片: 标记看过/取消看过 (菜单开合上报, 抑制控制层自动隐藏)
                onSetEpisodeCollectionType = { item, type ->
                    scope.launch {
                        vm.setEpisodeCollectionType.invokeSafe(
                            SetEpisodeCollectionTypeRequest(vm.subjectId, item.episodeId, type),
                        )?.let { toaster.showLoadError(it) }
                    }
                },
                onActionMenuExpandedChanged = { overlay.onPopupExpandedChanged(it) },
                // 卡片浮在视频画面上, 与进度条旁的胶囊按钮同一套黑白配色 (聚焦即白底黑字)
                monochrome = true,
                rowFocusRequester = stripFocusRequester,
            )
            // 卡片行下方: 聚焦集简介 (自动滚动) + 右侧 时长/播出日期 两行
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TV_PLAYER_HORIZONTAL_PAD),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                val desc = displayed?.let {
                    mergedEpisodeDesc(episodeOverviews[it.episodeId], it.desc)
                }.orEmpty()
                // 简介: 固定 3 行视口自动滚动 (不可聚焦, 无阅读模式)
                TvEpisodeDesc(desc, Modifier.weight(1f))
                displayed?.let {
                    FocusEpisodeMetaColumn(
                        runtimeMinutes = episodeRuntimes[it.episodeId],
                        airDate = it.airDate,
                    )
                }
            }
        }
    }
    }
}

/**
 * 选集条聚焦集简介: 最多 [TV_EPISODE_DESC_VISIBLE_LINES] 行, 放不下直接截断 (省略号), 不可聚焦.
 *
 * 高度固定成整数行 (`Modifier.height`) 而不是随文字长短伸缩: 左右切换聚焦集时简介长度不一,
 * 不定高会让下面的元素跟着上下跳.
 */
@Composable
private fun TvEpisodeDesc(desc: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodySmall
    val density = LocalDensity.current
    val lineHeight = if (style.lineHeight.isSpecified) style.lineHeight else 16.sp
    val viewportHeight = with(density) { lineHeight.toDp() } * TV_EPISODE_DESC_VISIBLE_LINES
    Box(
        modifier
            .fillMaxWidth()
            .height(viewportHeight),
    ) {
        Text(
            desc,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = style,
            maxLines = TV_EPISODE_DESC_VISIBLE_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
