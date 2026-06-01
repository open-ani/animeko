/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.nameCn
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.UIRichText
import me.him188.ani.app.ui.danmaku.DanmakuEditorState
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_reply_to
import me.him188.ani.app.ui.lang.episode_send_danmaku
import me.him188.ani.app.ui.lang.subject_episode_danmaku_list_empty
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.app.ui.subject.details.SubjectDetailsUIState
import me.him188.ani.app.ui.subject.episode.EpisodePageState
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
import me.him188.ani.app.ui.subject.person.PeoplePreviewTarget
import me.him188.ani.app.ui.subject.person.rememberPeopleClickHandler
import me.him188.ani.app.ui.subject.episode.details.DanmakuSourceChips
import me.him188.ani.app.ui.subject.episode.details.DanmakuTimeShiftDialog
import me.him188.ani.app.ui.subject.episode.details.components.renderDanmakuServiceId
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.SubjectEnter
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.SubjectRecommendationClick
import me.him188.ani.utils.analytics.recordEvent
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---- 面板调参 ----

/** 面板宽度 (Prime 实测约屏宽 1/4): 弹幕列表/评论等文字面板. */
private val TV_PANEL_WIDTH = 420.dp

/** 卡片类面板宽度 (相关推荐/角色/制作人员: 头像 + 单行文字, 收窄; 放不下的文字聚焦跑马灯). */
private val TV_PANEL_CARD_WIDTH = 240.dp

/**
 * 面板最大高度的**下限兜底** (条目少时仍按内容收缩).
 *
 * 实际上限按屏实算: 上到顶部信息 (标题/时钟) 下缘、下到胶囊行上缘, 由
 * [TvPlayerControlsOverlay] 实测两者位置传入 (见 [TvPlayerPanelHost] 的 `availableHeightPx`).
 * 固定 300dp 在 960x540dp (正常 1080p TV) 上约等于这段空隙, 但 4K 上报原生 density 时
 * 逻辑高度翻倍, 锁死就只剩三分之一屏. 本值仅在未测得 (首帧) 或屏幕过小时兜底, 保证不比旧版差.
 */
private val TV_PANEL_FALLBACK_MAX_HEIGHT = 300.dp

/** 面板上缘与顶部信息下缘之间保留的间隙. */
private val TV_PANEL_TOP_GAP = 16.dp

/**
 * 面板条目的焦点登记处 (焦点找回用).
 *
 * [requester] 由条目获焦时登记 —— 不按 focusedIndex 在组合里挂请求器: 那要求每个条目都读
 * focusedIndex, 一步导航就让在场的所有条目重组.
 *
 * [focused] 是找回解析的**到位判据**: 目标未附着时 requestFocus 会被焦点系统静默拒绝
 * (不抛异常), 所以不能拿"请求没抛异常"当到位 (见 resolveFocusRepeatedly 的文档).
 * 也不能用 overlay.focusRegion —— 它失焦不清除, 找回开始那一刻还停在 PANEL 上,
 * 判据会当场为真, 一次请求都不发.
 */
@Stable
internal class TvPanelItemFocusRegistry {
    var requester: FocusRequester? by mutableStateOf(null)
    var focused: Boolean by mutableStateOf(false)
}

/** 面板条目未聚焦底色 (半透明玻璃, 视频上可读). */
private val TV_PANEL_ITEM_COLOR = Color.Black.copy(alpha = 0.55f)

/** 面板条目聚焦底色. */
private val TV_PANEL_ITEM_FOCUSED_COLOR = Color.Black.copy(alpha = 0.8f)

/**
 * L2 浮出面板宿主: 弹幕列表 / 相关推荐 / 本集评论.
 *
 * 统一形态: `LazyColumn(reverseLayout = true)` —— index 0 在底部, 向上导航 = 索引递增,
 * 聚焦项吸附到底缘 (禁默认 bring-into-view + animateScrollToItem), 最底项按下键
 * 显式回到打开面板的胶囊. 面板每次浮出都复位到底部 (进入焦点恒落最下第一项).
 */
@Composable
internal fun TvPlayerPanelHost(
    overlay: TvPlayerOverlayState,
    vm: EpisodeViewModel,
    page: EpisodePageState,
    pauseOnPlaying: () -> Unit,
    /** 各胶囊按钮的焦点请求器: 面板最底项按下键显式回到打开它的那个胶囊. */
    pillFocusRequesters: Map<TvPlayerPanel, FocusRequester>,
    /**
     * 面板可向上生长的空间 (window px): 胶囊行上缘 − 顶部信息下缘, 由调用方实测.
     * NaN (尚未测得) 时兜底 [TV_PANEL_FALLBACK_MAX_HEIGHT]. Provider 形式 + 测量阶段才读:
     * 位置变化只触发面板重测量, 不重组控制层.
     */
    availableHeightPx: () -> Float = { Float.NaN },
    modifier: Modifier = Modifier,
) {
    val danmakuListState = rememberLazyListState()
    val recommendationsListState = rememberLazyListState()
    val commentsListState = rememberLazyListState()
    val charactersListState = rememberLazyListState()
    val staffListState = rememberLazyListState()

    // 当前聚焦条目下标 (吸底滚动用; 同一时刻只有一个面板在场, 共享一个)
    val focusedIndex = remember { mutableIntStateOf(-1) }
    // 每面板独立的入口请求器: AnimatedContent 淡切期间新旧两个面板并存,
    // 共享一个请求器会双挂载 (解析可能聚到即将卸载的旧面板上, 焦点随之丢失)
    val entryFocusRequesters = remember { TvPlayerPanel.entries.associateWith { FocusRequester() } }
    // 弹幕面板底部 chips 行当前是否存在 (加载早期/全部源失败时不组合), 由面板上报;
    // 左右键跳相邻胶囊的豁免判断依据 —— chips 缺席时 index 0 是普通弹幕行, 不豁免
    val danmakuChipsPresent = remember { mutableStateOf(false) }
    // 当前聚焦条目的登记 (焦点找回用, 见 overlay.panelItemFocusTick)
    val itemFocus = remember { TvPanelItemFocusRegistry() }

    // 面板每次浮出都复位到底部 (index 0): 进入焦点 (点击胶囊/上键) 永远落在最下面
    // 第一项 —— 保留上次滚动位置会让进入焦点落在"不知道第几项"上, 反直觉
    LaunchedEffect(overlay.activePanel) {
        // 换面板/关面板: 旧面板条目的登记作废 (它的节点即将被移除, 焦点找回不能落在上面)
        itemFocus.requester = null
        itemFocus.focused = false
        val listState = when (overlay.activePanel) {
            TvPlayerPanel.DANMAKU_LIST -> danmakuListState
            TvPlayerPanel.RECOMMENDATIONS -> recommendationsListState
            TvPlayerPanel.COMMENTS -> commentsListState
            TvPlayerPanel.CHARACTERS -> charactersListState
            TvPlayerPanel.STAFF -> staffListState
            null -> return@LaunchedEffect
        }
        focusedIndex.intValue = -1
        runCatching { listState.scrollToItem(0) }
    }

    // 点击胶囊把焦点送进面板: 等面板组合完成, 到位确认 (focusRegion) + 重试.
    // 消化统一 pendingFocus 的 PANEL 目标 (入口请求器在本子树, 屏幕级解析器够不着);
    // collectLatest: 其它目标的新请求会替换 PANEL 请求并取消本解析, 无互抢
    LaunchedEffect(Unit) {
        snapshotFlow { overlay.pendingFocus }.collectLatest { (target, _) ->
            if (target != TvPlayerFocusTarget.PANEL) return@collectLatest
            resolveFocusRepeatedly(
                arrived = {
                    // 面板已关掉也视为到位 (放弃解析)
                    overlay.activePanel == null || overlay.focusRegion == TvPlayerFocusRegion.PANEL
                },
            ) {
                overlay.activePanel?.let {
                    runCatching { entryFocusRequesters.getValue(it).requestFocus() }
                }
            }
        }
    }

    // 焦点找回: 从面板条目点开的东西 (回复弹窗/人物预览/弹幕延迟对话框) 关掉之后, 把焦点
    // 送回**刚点开的那一条**. Compose 移除聚焦节点时会清掉整棵树的焦点且不交给祖先, 所以
    // 每一次这样的移除都得有人显式还回来, 否则整层没有焦点 (方向键全失效).
    // drop(1): 初值不是一次真实请求
    LaunchedEffect(Unit) {
        snapshotFlow { overlay.panelItemFocusTick }.drop(1).collectLatest {
            val requester = itemFocus.requester ?: return@collectLatest
            resolveFocusRepeatedly(
                attempts = 20,
                arrived = {
                    // 面板/控制层已经不在场也算收工 (焦点归属由根路由的解析器负责)
                    itemFocus.focused || overlay.activePanel == null ||
                            overlay.layer != TvPlayerLayer.CONTROLS
                },
                // 用户自己把焦点挪去了别的区域 (胶囊行/进度条): 别跟他抢
                abandon = { overlay.focusRegion != TvPlayerFocusRegion.PANEL },
            ) {
                runCatching { requester.requestFocus() }
            }
        }
    }

    AnimatedContent(
        targetState = overlay.activePanel,
        modifier = modifier,
        transitionSpec = {
            when {
                initialState == null ->
                    (slideInVertically(tween(200)) { it / 4 } + fadeIn(tween(200))) togetherWith
                            fadeOut(tween(120))

                targetState == null ->
                    fadeIn(tween(120)) togetherWith
                            (slideOutVertically(tween(200)) { it / 4 } + fadeOut(tween(200)))

                else -> fadeIn(tween(150)) togetherWith fadeOut(tween(100))
            }
        },
        contentAlignment = Alignment.BottomStart,
        label = "tvPlayerPanel",
    ) { panel ->
        val panelWidth = when (panel) {
            TvPlayerPanel.RECOMMENDATIONS, TvPlayerPanel.CHARACTERS, TvPlayerPanel.STAFF ->
                TV_PANEL_CARD_WIDTH

            else -> TV_PANEL_WIDTH
        }
        val panelModifier = Modifier
            .width(panelWidth)
            // 最大高度按屏实算 (顶到顶部信息下缘为止); 在测量阶段读位置, 不因位置变化重组
            .layout { measurable, constraints ->
                val available = availableHeightPx()
                val fallbackPx = TV_PANEL_FALLBACK_MAX_HEIGHT.roundToPx()
                val maxHeightPx = if (available.isNaN()) {
                    fallbackPx
                } else {
                    // 小屏算出来比兜底还小时用兜底: 行为不比固定 300dp 的旧版差
                    max(fallbackPx, (available - TV_PANEL_TOP_GAP.toPx()).roundToInt())
                }
                val placeable = measurable.measure(
                    constraints.copy(maxHeight = min(constraints.maxHeight, maxHeightPx)),
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .padding(bottom = 14.dp)
            // 最底项 (index 0) 按下键: 显式回到打开本面板的胶囊 —— 交给空间搜索会落到
            // 面板正下方的任意按钮, 落错后该按钮的聚焦回调又把面板切成自己的 (面板跳变)
            .onPreviewKeyEvent { event ->
                when {
                    panel == null -> false

                    event.key == Key.DirectionDown && focusedIndex.intValue == 0 -> {
                        if (event.type == KeyEventType.KeyDown) {
                            runCatching { pillFocusRequesters.getValue(panel).requestFocus() }
                        }
                        true
                    }

                    // 条目上按左/右: 焦点跳到打开本面板的胶囊的左/右相邻胶囊 (面板随之
                    // 切换). 面板条目单列, 左右键没有面板内目标, 交给空间搜索会斜跳到
                    // 下方按钮行的任意按钮; 例外: 弹幕列表最底行 (index 0) 是横排 chips
                    // (且 chips 确实在场, 缺席时 index 0 是普通弹幕行), 左右键留给行内导航
                    (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) &&
                            !(panel == TvPlayerPanel.DANMAKU_LIST && focusedIndex.intValue == 0 &&
                                    danmakuChipsPresent.value) -> {
                        if (event.type == KeyEventType.KeyDown) {
                            val neighborIndex = TV_PILL_VISUAL_ORDER.indexOf(panel) +
                                    (if (event.key == Key.DirectionLeft) -1 else 1)
                            TV_PILL_VISUAL_ORDER.getOrNull(neighborIndex)?.let {
                                runCatching { pillFocusRequesters.getValue(it).requestFocus() }
                            }
                        }
                        // 到头 (最左/最右胶囊的面板) 也消费, 防斜跳
                        true
                    }

                    else -> false
                }
            }
        when (panel) {
            null -> Box(Modifier.height(0.dp))

            TvPlayerPanel.STAFF -> TvStaffPanel(
                vm, overlay, staffListState, focusedIndex, itemFocus,
                entryFocusRequesters.getValue(panel), panelModifier,
            )

            TvPlayerPanel.CHARACTERS -> TvCharactersPanel(
                vm, overlay, charactersListState, focusedIndex, itemFocus,
                entryFocusRequesters.getValue(panel), panelModifier,
            )

            TvPlayerPanel.RECOMMENDATIONS -> TvRecommendationsPanel(
                vm, overlay, recommendationsListState, focusedIndex, itemFocus,
                entryFocusRequesters.getValue(panel), panelModifier,
            )

            TvPlayerPanel.COMMENTS -> TvCommentsPanel(
                vm, page, overlay, commentsListState, focusedIndex, itemFocus,
                entryFocusRequesters.getValue(panel), pauseOnPlaying, panelModifier,
            )

            TvPlayerPanel.DANMAKU_LIST -> TvDanmakuListPanel(
                vm, page, overlay, danmakuListState, focusedIndex, itemFocus,
                entryFocusRequesters.getValue(panel),
                onChipsPresentChanged = { danmakuChipsPresent.value = it },
                modifier = panelModifier,
            )
        }
    }
}

// ============================ 共用脚手架 ============================

/**
 * 底锚可导航列表: reverseLayout (index 0 在底), 聚焦项吸底, 关默认 bring-into-view.
 *
 * 面板入口请求器不挂在本列表上 (对焦点组 requestFocus 的进组落点不确定, 实测会落到
 * 视觉最上面的条目), 由各面板挂到自己的 index 0 (最底, 紧邻胶囊按钮) 条目上.
 */
@Composable
private fun TvPanelList(
    listState: LazyListState,
    overlay: TvPlayerOverlayState,
    focusedIndex: MutableIntState,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 8.dp,
    content: LazyListScope.() -> Unit,
) {
    val noBringIntoView = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
        }
    }
    // 聚焦项吸底: reverseLayout 下 scrollToItem 把目标项对齐到列表起点 = 底缘
    LaunchedEffect(listState) {
        snapshotFlow { focusedIndex.intValue }.collectLatest { idx ->
            if (idx >= 0) runCatching { listState.animateScrollToItem(idx) }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides noBringIntoView) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = modifier
                .focusGroup()
                .onFocusChanged { if (it.hasFocus) overlay.focusRegion = TvPlayerFocusRegion.PANEL },
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            content()
        }
    }
}

/** 面板条目: 半透明玻璃底, 聚焦白色圆角描边 (参考 Prime 实测). [content] 收到实时聚焦态 (跑马灯用). */
@Composable
private fun TvPanelItem(
    index: Int,
    focusedIndex: MutableIntState,
    /** 焦点找回登记处: 本条目获焦时把自己的请求器登记进去 (见 [TvPlayerPanelHost]). */
    itemFocus: TvPanelItemFocusRegistry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val selfFocusRequester = remember { FocusRequester() }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(selfFocusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    focusedIndex.intValue = index
                    itemFocus.requester = selfFocusRequester
                    itemFocus.focused = true
                } else if (itemFocus.requester === selfFocusRequester) {
                    // 只有"当前登记的那一条"能清标记: 条目间交接时两条的回调先后不定,
                    // 无条件清的话后到的失焦回调会把新条目刚置的 true 抹掉
                    itemFocus.focused = false
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = if (focused) TV_PANEL_ITEM_FOCUSED_COLOR else TV_PANEL_ITEM_COLOR,
        contentColor = Color.White,
        border = if (focused) BorderStroke(2.dp, Color.White) else null,
        interactionSource = interactionSource,
    ) {
        content(focused)
    }
}

// ============================ 弹幕列表面板 ============================

/**
 * 弹幕列表: 底部 (index 0) 是弹幕源开关/重新匹配/调整延迟 chips (即导航入口第一站),
 * 上方为全集弹幕 (时间升序, 越往上越晚).
 */
@Composable
private fun TvDanmakuListPanel(
    vm: EpisodeViewModel,
    page: EpisodePageState,
    overlay: TvPlayerOverlayState,
    listState: LazyListState,
    focusedIndex: MutableIntState,
    itemFocus: TvPanelItemFocusRegistry,
    entryFocusRequester: FocusRequester,
    /** 底部 chips 行是否在场 (宿主的左右键豁免判断依据), 变化时上报. */
    onChipsPresentChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.danmakuListState.collectAsStateWithLifecycle()
    var editingShiftServiceId by remember { mutableStateOf<DanmakuServiceId?>(null) }
    SideEffect { onChipsPresentChanged(state.sourceItems.isNotEmpty()) }

    // 弹幕条目多且单行, 间距收到最紧. 条目是可点击 Surface, M3 会给它套 48dp
    // 最小交互尺寸 —— 单行文字实际不到 30dp, 多出的全变成上下空隙, 这里关掉
    // 弹幕条目的导航 index 基底: chips 项存在时它占 0, 条目从 1 起; chips 未组合
    // (加载早期/全部源失败) 时条目自己从 0 起 —— 否则没有任何项是 index 0,
    // 面板"最底项按下键回胶囊"的守卫 (focusedIndex == 0) 永不命中
    val itemIndexBase = if (state.sourceItems.isNotEmpty()) 1 else 0
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    TvPanelList(listState, overlay, focusedIndex, modifier, itemSpacing = 2.dp) {
        // 底部: 弹幕源 chips (原侧边栏弹幕列表里的源开关/延迟按钮, 功能不变)
        if (state.sourceItems.isNotEmpty()) {
            item("danmaku_sources") {
                Surface(
                    Modifier
                        .fillMaxWidth()
                        // 面板入口落点 (index 0, 进焦点落到第一个 chip)
                        .focusRequester(entryFocusRequester)
                        .onFocusChanged {
                            if (it.hasFocus) {
                                focusedIndex.intValue = 0
                                // 焦点找回落到本行第一个 chip (行内具体哪个 chip 不记)
                                itemFocus.requester = entryFocusRequester
                                itemFocus.focused = true
                            } else if (itemFocus.requester === entryFocusRequester) {
                                itemFocus.focused = false
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = TV_PANEL_ITEM_COLOR,
                    contentColor = Color.White,
                ) {
                    DanmakuSourceChips(
                        sourceItems = state.sourceItems,
                        onToggleSource = { serviceId, enabled -> vm.setDanmakuSourceEnabled(serviceId, enabled) },
                        onManualMatch = { serviceId ->
                            page.danmakuStatistics.fetchResults.find { it.serviceId == serviceId }?.let {
                                vm.startMatchingDanmaku(it.providerId)
                            }
                        },
                        onAdjustShift = { serviceId -> editingShiftServiceId = serviceId },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        if (state.isLoading) {
            item("danmaku_loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        } else if (state.danmakuItems.isEmpty()) {
            item("danmaku_empty") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Lang.subject_episode_danmaku_list_empty),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        items(
            count = state.danmakuItems.size,
            key = { i -> "${state.danmakuItems[i].id}-$i" },
        ) { i ->
            val danmaku = state.danmakuItems[i]
            TvPanelItem(
                index = i + itemIndexBase,
                focusedIndex = focusedIndex,
                itemFocus = itemFocus,
                onClick = {},
                // chips 未组合时首条弹幕即 index 0, 兼任面板入口落点
                modifier = if (i + itemIndexBase == 0) Modifier.focusRequester(entryFocusRequester) else Modifier,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        danmaku.content,
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (danmaku.isSelf) MaterialTheme.colorScheme.primary else Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        renderTvPlayerTimeShort(danmaku.timeMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
    }

    // 弹幕源延迟调整对话框 (复用手机版, 已带 TV slider 按键适配)
    val editingShiftSource = editingShiftServiceId?.let { serviceId ->
        page.danmakuStatistics.fetchResults.firstOrNull { it.serviceId == serviceId }
    }
    if (editingShiftSource != null) {
        DanmakuTimeShiftDialog(
            serviceName = renderDanmakuServiceId(editingShiftSource.serviceId),
            currentShiftMillis = editingShiftSource.config.shiftMillis,
            // 对话框收起后焦点还给 chips 行: 不还的话焦点停在被移除的对话框节点上 (= 没有焦点)
            onDismissRequest = {
                editingShiftServiceId = null
                overlay.requestPanelItemFocus()
            },
            onConfirm = { newShift ->
                vm.setDanmakuSourceShiftMillis(editingShiftSource.serviceId, newShift)
                editingShiftServiceId = null
                overlay.requestPanelItemFocus()
            },
        )
    }
}

private fun renderTvPlayerTimeShort(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

// ============================ 相关推荐面板 ============================

/** 相关推荐: 卡片形态 (封面 + 标题), 点击离开播放器进入对应条目详情页 (与手机版行为一致). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvRecommendationsPanel(
    vm: EpisodeViewModel,
    overlay: TvPlayerOverlayState,
    listState: LazyListState,
    focusedIndex: MutableIntState,
    itemFocus: TvPanelItemFocusRegistry,
    entryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val recommendations by vm.episodeDetailsState.recommendations
    TvPanelList(listState, overlay, focusedIndex, modifier) {
        items(
            count = recommendations.size,
            key = { i -> recommendations[i].uniqueId },
        ) { i ->
            val recommendation = recommendations[i]
            TvPanelItem(
                index = i,
                focusedIndex = focusedIndex,
                itemFocus = itemFocus,
                modifier = if (i == 0) Modifier.focusRequester(entryFocusRequester) else Modifier,
                onClick = {
                    val targetSubjectId = recommendation.subjectId?.toInt() ?: return@TvPanelItem
                    Analytics.recordEvent(SubjectRecommendationClick) {
                        put("subject_id", targetSubjectId)
                    }
                    Analytics.recordEvent(SubjectEnter) {
                        put("source", "episode_recommendation")
                        put("subject_id", targetSubjectId)
                    }
                    navigator.navigateSubjectDetails(
                        targetSubjectId,
                        SubjectDetailPlaceholder(
                            id = targetSubjectId,
                            name = recommendation.name,
                            nameCN = recommendation.nameCn ?: "",
                            coverUrl = recommendation.imageUrl,
                        ),
                    )
                },
            ) { focused ->
                Row(
                    Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        recommendation.imageUrl,
                        contentDescription = null,
                        Modifier
                            .size(width = 52.dp, height = 72.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        // 单行, 放不下时聚焦跑马灯 (与选集卡片同规矩)
                        Text(
                            recommendation.nameCn ?: recommendation.name.orEmpty(),
                            if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                        )
                        recommendation.name?.takeIf { it != recommendation.nameCn }?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                it,
                                if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================ 本集评论面板 ============================

/** 回复卡的左缩进 (给 thread 竖线留出的槽). */
private val TV_COMMENT_REPLY_INDENT = 28.dp

/** thread 竖线: 在缩进槽里的 x 位置 / 线宽 / 颜色. */
private val TV_COMMENT_THREAD_LINE_INSET = 12.dp
private val TV_COMMENT_THREAD_LINE_WIDTH = 2.dp
private val TV_COMMENT_THREAD_LINE_COLOR = Color.White.copy(alpha = 0.22f)

/** 条目间距. 与 [TvPanelList] 的默认值一致 (即改动前的间距), 竖线跨缝延伸也用它. */
private val TV_COMMENT_ROW_SPACING = 8.dp

/** 楼与楼的额外分隔留白 (只在上一楼确实展开了回复时给). */
private val TV_COMMENT_GROUP_GAP = 6.dp

/** 条目里 "回复 X" / 回复数这类元信息的图标尺寸. */
private val TV_COMMENT_META_ICON_SIZE = 12.dp

/**
 * 本集评论: 紧凑纯文本条目 (保持单条目单焦点, 富文本/大图等完整功能在详情页评论区).
 *
 * 主楼 + 一层回复展平进同一个列表 (见 [flattenTvCommentRows]), 回复用缩进 + thread 竖线
 * (回复的是同层另一条回复时再加一行 "回复 X") 表达回复关系 —— 不做 Reddit 那样的多层嵌套,
 * 10 尺 UI 上横向层级没法用方向键导航.
 *
 * 点击任一条目 = 回复其**所属主楼** (与改动前一致): 服务端写接口只接受主楼 id,
 * 见 [me.him188.ani.app.domain.comment.PostCommentUseCase]; 弹窗里回显的是被点的那一条.
 */
@Composable
private fun TvCommentsPanel(
    vm: EpisodeViewModel,
    page: EpisodePageState,
    overlay: TvPlayerOverlayState,
    listState: LazyListState,
    focusedIndex: MutableIntState,
    itemFocus: TvPanelItemFocusRegistry,
    entryFocusRequester: FocusRequester,
    pauseOnPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val comments = vm.episodeCommentState.list.collectAsLazyPagingItemsWithLifecycle()
    val snapshot = comments.itemSnapshotList
    val rows = remember(snapshot) { flattenTvCommentRows(snapshot) }

    /**
     * 打开评论弹窗. [shown] 是弹窗里回显的那一条, 回复实际发到 [thread] (主楼).
     *
     * [canReply] 为 false 时弹窗是只读的 (没有输入框和发送按钮): Bangumi 评论在 Ani 内只读,
     * 楼中回复也没有对应的写接口 —— 与其给个发不出去的输入框, 不如明说只能看.
     */
    fun openComment(shown: UIComment, shownText: TvCommentText, thread: UIComment, canReply: Boolean) {
        val context = CommentContext.EpisodeReply(
            vm.subjectId,
            page.episodePresentation.episodeId.toLong(),
            thread.sourceCommentId,
        )
        // 只读态不进编辑态: startEdit 会清掉草稿, 而这里根本不会发送
        if (canReply) {
            vm.commentEditorState.startEdit(context)
        }
        // TV 走自己那个大弹窗 (见 TvCommentReplyDialog), 不是手机端的底部 sheet
        overlay.startReply(
            TvCommentReplyTarget(
                context = context,
                authorName = shownText.authorName,
                timeText = shownText.timeText,
                // 弹窗按"文本段 + 图片"逐块渲染 (卡片上的 [图片] 占位在这里变成真图)
                blocks = shown.content.toCommentBlocks(),
                canReply = canReply,
            ),
        )
        pauseOnPlaying()
    }

    TvPanelList(listState, overlay, focusedIndex, modifier, itemSpacing = TV_COMMENT_ROW_SPACING) {
        items(
            count = rows.size,
            key = { rows[it].key },
        ) { i ->
            val row = rows[i]
            // 展平后不再是 1 pager item : 1 list item, 读一次以保住 paging 的预取/append 触发
            comments[row.pagerIndex]
            val text = rememberTvCommentText(row.comment)
            // 面板入口落点恒在 index 0 (最底那一条, 不一定还是主楼)
            val entryModifier = if (i == 0) Modifier.focusRequester(entryFocusRequester) else Modifier
            when (row) {
                is TvCommentMainRow -> {
                    // 楼与楼的分隔. reverseLayout 下视觉上方 = index 更大, 所以看 i + 1;
                    // 上一楼没展开回复时不加, 全是无回复主楼的列表间距与改动前完全一致
                    val groupGap = if (rows.getOrNull(i + 1) is TvCommentReplyRow) TV_COMMENT_GROUP_GAP else 0.dp
                    TvPanelItem(
                        index = i,
                        focusedIndex = focusedIndex,
                        itemFocus = itemFocus,
                        // reverseLayout 只反排列, 条目内部的上下方向不反
                        modifier = Modifier.padding(top = groupGap).then(entryModifier),
                        onClick = {
                            openComment(row.comment, text, row.comment, canReply = row.comment.canReply)
                        },
                    ) {
                        TvCommentRowContent(
                            text = text,
                            isReply = false,
                            replyCount = row.replyCount,
                            replyToName = null,
                        )
                    }
                }

                is TvCommentReplyRow -> TvPanelItem(
                    index = i,
                    focusedIndex = focusedIndex,
                    itemFocus = itemFocus,
                    modifier = Modifier
                        // 竖线画在缩进槽里 (卡片之外), 不和聚焦白描边打架.
                        // 只向上多画一个条目间距: 顶端接上方 (主楼卡 / 上一条回复) 的下边缘,
                        // 底端停在自己身上 —— 向下延伸会看起来连到下一楼去
                        .drawBehind {
                            val x = TV_COMMENT_THREAD_LINE_INSET.toPx()
                            drawLine(
                                color = TV_COMMENT_THREAD_LINE_COLOR,
                                start = Offset(x, -TV_COMMENT_ROW_SPACING.toPx()),
                                end = Offset(x, size.height),
                                strokeWidth = TV_COMMENT_THREAD_LINE_WIDTH.toPx(),
                            )
                        }
                        .padding(start = TV_COMMENT_REPLY_INDENT)
                        .then(entryModifier),
                    // 楼中回复没法被单独回复 (写接口只认主楼), 弹窗只读
                    onClick = { openComment(row.comment, text, row.thread, canReply = false) },
                ) {
                    TvCommentRowContent(
                        text = text,
                        isReply = true,
                        replyCount = 0,
                        replyToName = row.replyToName,
                    )
                }
            }
        }
    }
}

/**
 * 评论面板的一行. 展平后一行 = 一个焦点目标, index 连续 —— 面板宿主"最底项按下键回胶囊"
 * 的守卫依赖 `focusedIndex == 0`.
 */
private sealed class TvCommentRow {
    /** 所属主楼在 pager 里的下标 (读一次以保住 paging 预取). */
    abstract val pagerIndex: Int

    /** 本行展示的评论 (主楼行是主楼自己, 回复行是那条回复). */
    abstract val comment: UIComment
    abstract val key: String
}

private class TvCommentMainRow(
    override val pagerIndex: Int,
    override val comment: UIComment,
    /** 本楼展开的回复条数, 0 表示不显示计数. */
    val replyCount: Int,
) : TvCommentRow() {
    override val key: String = "main-" + comment.stableId
}

private class TvCommentReplyRow(
    override val pagerIndex: Int,
    override val comment: UIComment,
    /** 所属主楼: 点击回复时用它的 id. */
    val thread: UIComment,
    /** 非空 = 回复的是同层某条回复, 要显式指明; null = 直接回复主楼, 缩进已足够表达. */
    val replyToName: String?,
) : TvCommentRow() {
    override val key: String = "reply-" + comment.stableId
}

/**
 * 主楼 + 一层回复展平成面板行.
 *
 * 面板是 reverseLayout (index 0 在底, 越大越靠上), 所以同一楼里回复要**先**发且倒序,
 * 主楼最后发 —— 这样视觉自上而下才是 主楼 → 回复1 → 回复2, 方向键上下 = 空间上下.
 *
 * Ani 源按无回复关系处理 (服务端不返回被回复者, 见 `AniEpisodeCommentReply`): 只出主楼,
 * 与改动前完全一致.
 */
private fun flattenTvCommentRows(comments: List<UIComment?>): List<TvCommentRow> = buildList {
    comments.forEachIndexed { pagerIndex, comment ->
        if (comment == null) return@forEachIndexed
        val replies = if (comment.source == UICommentSource.ANI) emptyList() else comment.briefReplies
        replies.asReversed().forEach { reply ->
            add(TvCommentReplyRow(pagerIndex, reply, comment, reply.replyTo?.authorName))
        }
        add(TvCommentMainRow(pagerIndex, comment, replies.size))
    }
}

/** 条目文本. 时间要 [formatDateTime] (@Composable), 所以在组合里算好再给非组合的点击回调用. */
private class TvCommentText(
    val authorName: String,
    val timeText: String,
    val plainContent: String,
)

@Composable
private fun rememberTvCommentText(comment: UIComment): TvCommentText {
    val timeText = formatDateTime(comment.createdAt)
    val plainContent = remember(comment.stableId) { comment.content.toPlainText() }
    return remember(comment.stableId, timeText, plainContent) {
        TvCommentText(
            authorName = comment.author?.nickname ?: comment.author?.id.orEmpty(),
            timeText = timeText,
            plainContent = plainContent,
        )
    }
}

/** 评论/回复条目内容 (纯文本紧凑形态). [isReply] 收紧一号字与行数; [replyToName] 非空时顶部多一行. */
@Composable
private fun TvCommentRowContent(
    text: TvCommentText,
    isReply: Boolean,
    replyCount: Int,
    replyToName: String?,
) {
    val secondaryColor = Color.White.copy(alpha = 0.6f)
    Column(
        Modifier.padding(
            horizontal = if (isReply) 10.dp else 12.dp,
            vertical = if (isReply) 8.dp else 10.dp,
        ),
    ) {
        if (replyToName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Rounded.Reply,
                    null,
                    Modifier.size(TV_COMMENT_META_ICON_SIZE),
                    tint = secondaryColor,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(Lang.comment_reply_to, replyToName),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.authorName,
                Modifier.weight(1f),
                style = if (isReply) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelLarge
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (replyCount > 0) {
                Icon(
                    Icons.AutoMirrored.Rounded.Comment,
                    null,
                    Modifier.size(TV_COMMENT_META_ICON_SIZE),
                    tint = secondaryColor,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    replyCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryColor,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text.timeText,
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
            )
        }
        Spacer(Modifier.height(if (isReply) 3.dp else 4.dp))
        Text(
            text.plainContent,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = if (isReply) 3 else 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 富文本压成纯文本 (面板紧凑形态; 完整富文本在详情页评论区). */
private fun UIRichText.toPlainText(): String =
    elements.joinToString(separator = "") { element ->
        when (element) {
            is UIRichElement.AnnotatedText -> element.slice.joinToString(separator = "") { slice ->
                when (slice) {
                    is UIRichElement.Annotated.Text -> slice.content
                    is UIRichElement.Annotated.Sticker -> "[表情]"
                }
            }

            is UIRichElement.Quote -> ""
            is UIRichElement.Image -> "[图片]"
        }
    }

/**
 * 富文本拆成弹窗用的正文块: 文本段与图片按原顺序排列, 相邻文本并成一段.
 *
 * 引用块与 [toPlainText] 一样跳过 —— 拍平进正文会把别人被引用的话混成本人说的,
 * 而在这个"整块单焦点"的引用区里没有地方摆引用的边框和出处.
 */
private fun UIRichText.toCommentBlocks(): List<TvCommentBlock> {
    val blocks = mutableListOf<TvCommentBlock>()
    val pending = StringBuilder()

    fun flushText() {
        val text = pending.toString().trim()
        if (text.isNotEmpty()) blocks += TvCommentBlock.Text(text)
        pending.clear()
    }

    elements.forEach { element ->
        when (element) {
            is UIRichElement.AnnotatedText -> element.slice.forEach { slice ->
                when (slice) {
                    is UIRichElement.Annotated.Text -> pending.append(slice.content)
                    is UIRichElement.Annotated.Sticker -> pending.append("[表情]")
                }
            }

            is UIRichElement.Image -> {
                flushText()
                blocks += TvCommentBlock.Image(element.imageUrl)
            }

            is UIRichElement.Quote -> {}
        }
    }
    flushText()
    return blocks
}

// ============================ 角色 / 制作人员面板 ============================

/**
 * 角色面板: 卡片形态 (头像 + 名字 + 角色/CV), 点击弹居中人物预览
 * (需调用方在面板宿主外包 [me.him188.ani.app.ui.subject.person.PeoplePreviewHost]).
 * 数据与详情层/选集条共用 subjectDetailsStateLoader (进屏已预载);
 * 用完整名单 pager (原详情页"查看全部"的数据源) —— 详情页的角色/制作人员区块已移除,
 * 本面板是 TV 上唯一入口, 向上翻页自动加载更多.
 */
@Composable
private fun TvCharactersPanel(
    vm: EpisodeViewModel,
    overlay: TvPlayerOverlayState,
    listState: LazyListState,
    focusedIndex: MutableIntState,
    itemFocus: TvPanelItemFocusRegistry,
    entryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val detailsState = vm.episodeDetailsState
    val uiState by detailsState.subjectDetailsStateLoader.state
        .collectAsStateWithLifecycle(SubjectDetailsUIState.Placeholder(detailsState.subjectId))
    val details = (uiState as? SubjectDetailsUIState.Ok)?.value ?: return
    val characters = details.charactersPager.collectAsLazyPagingItemsWithLifecycle()
    val onClickCharacter = rememberPeopleClickHandler()
    TvPanelList(listState, overlay, focusedIndex, modifier) {
        items(
            count = characters.itemCount,
            key = characters.itemKey { it.character.id },
        ) { i ->
            val item = characters[i] ?: return@items
            TvPanelItem(
                index = i,
                focusedIndex = focusedIndex,
                itemFocus = itemFocus,
                modifier = if (i == 0) Modifier.focusRequester(entryFocusRequester) else Modifier,
                onClick = { onClickCharacter(PeoplePreviewTarget.Character(item.character.id)) },
            ) {
                val cv = item.character.actors.firstOrNull()?.displayName
                TvPersonPanelItemContent(
                    avatarUrl = item.character.imageMedium,
                    name = item.character.displayName,
                    subtitle = if (cv.isNullOrBlank()) item.role.nameCn else item.role.nameCn + " · " + cv,
                )
            }
        }
    }
}

/** 制作人员面板: 卡片形态 (头像 + 名字 + 职位), 点击弹居中人物预览; 完整名单 (同角色面板). */
@Composable
private fun TvStaffPanel(
    vm: EpisodeViewModel,
    overlay: TvPlayerOverlayState,
    listState: LazyListState,
    focusedIndex: MutableIntState,
    itemFocus: TvPanelItemFocusRegistry,
    entryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val detailsState = vm.episodeDetailsState
    val uiState by detailsState.subjectDetailsStateLoader.state
        .collectAsStateWithLifecycle(SubjectDetailsUIState.Placeholder(detailsState.subjectId))
    val details = (uiState as? SubjectDetailsUIState.Ok)?.value ?: return
    val staff = details.staffPager.collectAsLazyPagingItemsWithLifecycle()
    val onClickPerson = rememberPeopleClickHandler()
    TvPanelList(listState, overlay, focusedIndex, modifier) {
        items(
            count = staff.itemCount,
            key = staff.itemKey { it.personInfo.id },
        ) { i ->
            val person = staff[i] ?: return@items
            TvPanelItem(
                index = i,
                focusedIndex = focusedIndex,
                itemFocus = itemFocus,
                modifier = if (i == 0) Modifier.focusRequester(entryFocusRequester) else Modifier,
                onClick = { onClickPerson(PeoplePreviewTarget.Person(person.personInfo.id)) },
            ) {
                TvPersonPanelItemContent(
                    avatarUrl = person.personInfo.imageMedium,
                    name = person.personInfo.displayName,
                    subtitle = person.position.nameCn ?: "",
                )
            }
        }
    }
}

/** 面板人物条目内容: 头像 + 名字 + 副标题 (容器用面板玻璃条目). */
@Composable
private fun TvPersonPanelItemContent(
    avatarUrl: String?,
    name: String,
    subtitle: String,
) {
    Row(
        Modifier.padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像方圆角, crop 顶部对齐 (人物图多为立绘, 顶部对齐保证露脸)
        Box(Modifier.size(TV_PERSON_PANEL_AVATAR_SIZE).clip(RoundedCornerShape(8.dp))) {
            AvatarImage(
                avatarUrl,
                Modifier.size(TV_PERSON_PANEL_AVATAR_SIZE),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 人物面板条目头像尺寸. */
private val TV_PERSON_PANEL_AVATAR_SIZE = 48.dp

// ============================ 弹幕发送入口 ============================

/**
 * 弹幕发送入口: 胶囊行末尾的圆钮, 点击向右展开成输入框 (自动聚焦弹系统键盘,
 * IME 确认发送后收起, 返回键收起 —— 与搜索页输入框同套路).
 */
@Composable
internal fun TvDanmakuSendEntry(
    overlay: TvPlayerOverlayState,
    danmakuEditorState: DanmakuEditorState,
    vm: EpisodeViewModel,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val expanded = overlay.danmakuInputExpanded
    val fieldFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var fieldFocused by remember { mutableStateOf(false) }
    var everExpanded by remember { mutableStateOf(false) }
    val isSending by danmakuEditorState.isSending.collectAsStateWithLifecycle()

    // 展开: 轮询聚焦输入框 + 弹键盘; 收起: 焦点还给圆钮
    LaunchedEffect(expanded) {
        if (expanded) {
            everExpanded = true
            if (resolveFocusRepeatedly(attempts = 20, arrived = { fieldFocused }) {
                    runCatching { fieldFocusRequester.requestFocus() }
                }
            ) {
                keyboard?.show()
            }
        } else if (everExpanded) {
            keyboard?.hide()
            // 圆钮是常驻焦点目标, 请求器一附着 (不抛异常) 即视为到位;
            // 控制层已隐藏则放弃 (焦点归属由根路由处理)
            var requested = false
            resolveFocusRepeatedly(
                attempts = 20,
                arrived = { requested || overlay.layer != TvPlayerLayer.CONTROLS },
            ) {
                if (overlay.layer == TvPlayerLayer.CONTROLS) {
                    runCatching { buttonFocusRequester.requestFocus() }.onSuccess { requested = true }
                }
            }
        }
    }

    val send: () -> Unit = send@{
        val text = danmakuEditorState.text.trim()
        if (text.isEmpty() || isSending) return@send
        scope.launch {
            danmakuEditorState.post(
                DanmakuContent(
                    vm.player.getCurrentPositionMillis(),
                    text = text,
                    color = Color.White.toArgb(),
                    location = DanmakuLocation.NORMAL,
                ),
            )
        }
        overlay.danmakuInputExpanded = false
    }

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = { if (!expanded) overlay.danmakuInputExpanded = true },
        modifier = modifier
            .focusRequester(buttonFocusRequester)
            // 本按钮与面板胶囊同在 PILLS 区域, 但它不是面板触发器: 聚焦到它时收起浮出的
            // 面板 (面板只在焦点区域变成进度条/图标行时才自动清, 从"评论"胶囊右移过来
            // 区域不变, 不收就一直挂着)
            .onFocusChanged { if (it.hasFocus) overlay.activePanel = null },
        shape = CircleShape,
        color = if (focused && !expanded) Color.White else Color.White.copy(alpha = 0.14f),
        contentColor = if (focused && !expanded) Color.Black else Color.White,
        interactionSource = interactionSource,
    ) {
        Row(
            Modifier.padding(horizontal = TV_PILL_PADDING_H, vertical = TV_PILL_PADDING_V),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Edit, null, Modifier.size(TV_PILL_ICON_SIZE))
            if (!expanded) {
                Text(
                    stringResource(Lang.episode_send_danmaku),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            } else {
                BasicTextField(
                    value = danmakuEditorState.text,
                    onValueChange = { danmakuEditorState.text = it },
                    modifier = Modifier
                        .width(240.dp)
                        .focusRequester(fieldFocusRequester)
                        .onFocusChanged {
                            fieldFocused = it.isFocused
                            if (it.isFocused) overlay.focusRegion = TvPlayerFocusRegion.PILLS
                        },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    ),
                    cursorBrush = SolidColor(Color.White),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )
            }
        }
    }
}
