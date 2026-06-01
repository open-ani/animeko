/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.sections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import me.him188.ani.app.ui.foundation.widgets.AniScrollableTextDialog
import me.him188.ani.app.ui.lang.subject_details_no_summary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import kotlinx.coroutines.launch
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import me.him188.ani.app.data.network.tmdbStillCardSizeUrl
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.rememberAsyncImageRetryState
import me.him188.ani.app.ui.foundation.rememberTvLongPressKeyState
import me.him188.ani.app.ui.foundation.tvLongPressKey
import me.him188.ani.app.ui.foundation.focus.TvScrollAnimator
import me.him188.ani.app.ui.foundation.focus.tvAnchorBringIntoViewSpec
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.foundation.focus.tvFocusMoveRateLimit
import me.him188.ani.app.ui.foundation.theme.glassContainerColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_episodes
import me.him188.ani.app.ui.lang.subject_episode_cache
import me.him188.ani.app.ui.lang.subject_episode_duration_minutes
import me.him188.ani.app.ui.lang.subject_episode_mark_watched
import me.him188.ani.app.ui.lang.subject_episode_unwatch
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.stringResource

/**
 * TV 选集: 单行固定锚点轮播 (Prime Video 式) —— 聚焦框钉在行首停靠位不动,
 * 遥控器左右导航时只有卡片列表在框下平滑滑过, 聚焦卡滑进框里停靠. 行上方展示当前
 * 聚焦集的 "集号. 集名 + 简介". 自动滚至当前集.
 *
 * 未聚焦任何格子时展示 [currentEpisodeId] (当前/下一集) 的信息.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusEpisodeCarousel(
    episodes: List<EpisodeListItem>,
    currentEpisodeId: Int?,
    onEpisodeClick: (EpisodeListItem) -> Unit,
    modifier: Modifier = Modifier,
    /** episodeId -> TMDB 分集缩略图 URL. 无图的集回退纯文字卡, 卡片尺寸不变. */
    episodeStills: Map<Int, String> = emptyMap(),
    /** episodeId -> 播放进度 (0..1), 有记录的集在卡片底部画进度条. */
    playProgress: Map<Int, Float> = emptyMap(),
    /** episodeId -> 分集时长 (分钟, TMDB), 显示在聚焦集信息行右侧; 缺失则不显示. */
    episodeRuntimes: Map<Int, Int> = emptyMap(),
    /** episodeId -> TMDB 中文分集简介; 有则排在 Bangumi 简介 (多为日文) 之前展示. */
    episodeOverviews: Map<Int, String> = emptyMap(),
    /**
     * 聚焦集简介的自定义渲染 (如注入 TV 阅读模式组件); null 时用默认 2 行截断文本.
     * [onHorizontalNav] 供简介组件在聚焦时把左右键转为切换聚焦集 (±1, 到两端无效果),
     * 卡片行同步滑动, 焦点仍留在简介上.
     */
    descContent: (@Composable ColumnScope.(desc: String, onHorizontalNav: (delta: Int) -> Unit) -> Unit)? = null,
    /** 非 null 时卡片支持长按确认键打开单集操作菜单 (标记看过/取消看过). */
    onSetEpisodeCollectionType: ((EpisodeListItem, UnifiedCollectionType) -> Unit)? = null,
    /**
     * 长按菜单开合上报 (播放器选集条用于抑制控制层自动隐藏; 菜单是独立窗口,
     * 按键不经过播放器根路由, 不上报会在菜单开着时被 5 秒计时收掉).
     */
    onActionMenuExpandedChanged: ((Boolean) -> Unit)? = null,
    /**
     * false 时不渲染卡片行上方的聚焦集小标题行与简介行 (播放器选集条的集信息
     * 由调用方放在卡片行下方, 见 [onDisplayedChanged]). 切换不影响卡片行状态.
     */
    showEpisodeInfo: Boolean = true,
    /**
     * 展示中的集 (聚焦卡, 无聚焦时为当前集) 变化时回调 —— 调用方在轮播外自行渲染
     * 集信息 (如播放器选集条在卡片行下方放简介). null 时不回调.
     */
    onDisplayedChanged: ((EpisodeListItem?) -> Unit)? = null,
    cellWidth: Dp = 256.dp,
    cellHeight: Dp = 144.dp,
    cellSpacing: Dp = 16.dp,
    /**
     * 页面级水平留白. 标题行/信息行正常留边; 卡片行只把它作为滚动停靠的 contentPadding,
     * 内容可一直画到容器 (屏幕) 右边缘不被裁 (出血).
     * 为此本组件应以全宽放置, 不要包在带水平 padding 的容器里.
     */
    horizontalPadding: Dp = 0.dp,
    /**
     * 聚焦集小标题行/简介行的右侧留白 (距容器右缘). 选集整页的放大封面向下延伸到这片区域,
     * 传"封面宽 + 间距"可让这些文字与上方大标题/简介共用同一右边界, 不与封面重叠.
     * 默认与 [horizontalPadding] 相同 (左右对称). 卡片行不受影响 (仍全宽出血).
     */
    endPadding: Dp = horizontalPadding,
    /** 可选标题行 (如 "选集" + 连载进度); null 时不渲染 (TV 详情页该位置放聚焦集小标题). */
    header: (@Composable () -> Unit)? = null,
    /**
     * true 时卡片与聚焦描边改用纯黑白 (见 [FocusEpisodeCard] 的同名参数).
     * 播放器选集条用: 卡片浮在视频画面上, 主题色与画面抢注意力.
     */
    monochrome: Boolean = false,
    /**
     * true 时无图卡片改用半透明玻璃底 (见 [FocusEpisodeCard] 的同名参数).
     * TV 详情页用: 底下压着 backdrop 背景图, 实心底色会把图盖掉.
     */
    glass: Boolean = false,
    /**
     * 非 null 时卡片按上键固定聚焦到该目标 (如区块标题行右侧的网格入口按钮).
     * 不指定时空间焦点搜索只考虑与聚焦卡片同列 ("beam" 内) 的候选 —— 聚焦卡片固定在行首最左,
     * 而标题行按钮在最右, 不同列, 于是向上会跳过按钮直接落到更上方 Hero 区的按钮.
     */
    upFocus: FocusRequester? = null,
    /**
     * 非 null 时卡片按下键固定聚焦到该目标 (如选集页之下的区块). 跨区块向下的空间焦点
     * 搜索隔着大段不可聚焦内容 (作品信息表) 时找不到目标, 需显式指路.
     */
    downFocus: FocusRequester? = null,
    /** 非 null 时滚动到该集卡片并聚焦 (如选集网格菜单关闭后跳到菜单里聚焦的集), 完成后回调 [onRevealConsumed]. */
    revealEpisodeId: Int? = null,
    onRevealConsumed: () -> Unit = {},
    /**
     * 非 null 时挂在卡片行 (LazyRow) 上: 调用方对它 requestFocus 可把焦点送进轮播,
     * 进行落点改道会送到展示中的那张卡 (首次为当前集). 详情页返回键分层用
     * ("选集之下的区域按返回回到选集卡片").
     */
    rowFocusRequester: FocusRequester? = null,
) {
    var focusedEpisodeId by remember { mutableStateOf<Int?>(null) }
    // 实时聚焦的卡片 (失焦即清空), 区别于 focusedEpisodeId (记录"最后聚焦", 不清空):
    // 焦点断言必须用实时值 —— 用最后聚焦值会在"目标恰好是上次聚焦的卡"时误判已完成
    var activeFocusEpisodeId by remember { mutableStateOf<Int?>(null) }
    // 压暗的分界线下标: 它左边的卡片是"已经滑过去的", 见 [EPISODE_PAST_CARD_DIM_ALPHA].
    //
    // **焦点离开本行时刻意不清空**, 与 activeFocusEpisodeId 相反: 分界线表达的是行**停在哪儿**
    // (哪张卡卡在停靠线左边露出切边), 而行不会因为焦点挪到「显示更多」就滚回去. 清成 -1 的话
    // 整行会在焦点离行的一瞬全部变亮、焦点回来又暗下去, 而压暗的两条理由 (左边是已过去的内容、
    // 盖住卡片穿过聚焦框轮廓的圆角错位) 在焦点不在行上时一条都没失效.
    //
    // 直接记下标而不经 episodes 映射: 映射 lambda 会被 item 的 remember 缓存住旧实例,
    // 见 [FocusEpisodeAnchorRing] 的教训.
    var dimPivotIndex by remember { mutableIntStateOf(-1) }
    // 聚焦卡是否正被按住 (长按确认键): 固定聚焦框读它跟着缩放
    var pressingCard by remember { mutableStateOf(false) }

    // 长按卡片打开的单集操作菜单; 关闭后把焦点还给弹窗当前显示的那集的卡片
    var actionTarget by remember { mutableStateOf<EpisodeListItem?>(null) }
    // 长按打开弹窗那一刻聚焦的卡: 弹窗内左右切换过再关闭时, 系统归还的焦点仍会先落回它
    // (归还目标是"打开前"的焦点), 与显式恢复到新集竞争 —— 记下来供恢复解析放行, 不算用户介入
    var actionOpenedOnEpisodeId by remember { mutableStateOf<Int?>(null) }
    // 菜单开合上报 (开着期间挂起, 关闭/离开组合时自动回报 false)
    if (actionTarget != null && onActionMenuExpandedChanged != null) {
        DisposableEffect(Unit) {
            onActionMenuExpandedChanged(true)
            onDispose { onActionMenuExpandedChanged(false) }
        }
    }
    var actionRestoreEpisodeId by remember { mutableStateOf<Int?>(null) }
    // 本次焦点恢复期间"焦点被还给这张卡"不算用户介入 (见下方 yieldEpisodeId): 窗口关闭时
    // 系统会把焦点还给弹窗打开前聚焦的那张卡, 与显式聚焦目标集竞争
    var actionRestoreYieldEpisodeId by remember { mutableStateOf<Int?>(null) }
    val actionRestoreFocus = remember { FocusRequester() }
    LaunchedEffect(actionRestoreEpisodeId) {
        val target = actionRestoreEpisodeId ?: return@LaunchedEffect
        // 只请求一次不够: 对话框窗口关闭时系统会异步把焦点还给宿主窗口之前的元素,
        // 落到行上又会经 onEnter 改道到"展示中的那张卡", 与这里的
        // 显式聚焦竞争, 谁后执行谁生效 (表现为随机跳错集). 另外 scrollToItem 后目标卡
        // 可能还没组合完成, 首次请求会落空.
        //
        // 归还时机不定 (可能在我们抢到之后才发生), 所以到位后不能立刻收手, 也不能把归还
        // 误判成用户介入:
        //  - 目标须连续持有焦点 [FOCUS_HOLD_FRAMES] 帧才算稳; 中途被归还抢走就再抢回来;
        //  - 放弃判据放行 [yieldEpisodeId] (归还的落点). 网格长按跳转的场景下用户此刻还按着
        //    确认键, 根本没法移动焦点, 焦点出现在旧卡上只可能是归还. 落到第三张卡才算用户介入.
        // 起点快照同理: 解析开始那一刻焦点通常正停在要离开的元素上, 不算介入.
        val startEpisodeId = activeFocusEpisodeId
        val yieldEpisodeId = actionRestoreYieldEpisodeId
        var heldFrames = 0
        resolveFocusRepeatedly(
            attempts = 30, delayMillis = 0,
            arrived = { heldFrames >= FOCUS_HOLD_FRAMES },
            abandon = {
                activeFocusEpisodeId.let {
                    it != null && it != startEpisodeId && it != target && it != yieldEpisodeId
                }
            },
        ) {
            if (activeFocusEpisodeId == target) {
                heldFrames++
            } else {
                heldFrames = 0
                runCatching { actionRestoreFocus.requestFocus() }
            }
        }
        actionRestoreEpisodeId = null
        actionRestoreYieldEpisodeId = null
    }
    // "展示中的集"由 focusedEpisodeId (每次左右键都变) 推导, 一律不在本函数 body 读:
    // body 订阅它的话每按一格整个轮播作用域重启, LazyRow content lambda (捕获 Map 等
    // 不稳定值) 随之换新实例 → 全部可见卡全新重建 (2026-07-31 实测). 消费者各自收窄:
    // 信息行在 [FocusEpisodeInfoRow] 里读, 兜底下标走 derivedStateOf, 回调走快照观察
    val onDisplayedChangedState = rememberUpdatedState(onDisplayedChanged)
    if (onDisplayedChanged != null) {
        LaunchedEffect(episodes, currentEpisodeId) {
            snapshotFlow {
                episodes.firstOrNull { it.episodeId == (focusedEpisodeId ?: currentEpisodeId) }
                    ?: episodes.firstOrNull()
            }.collect { onDisplayedChangedState.value?.invoke(it) }
        }
    }

    val currentIndex = remember(episodes, currentEpisodeId) {
        if (currentEpisodeId == null) -1 else episodes.indexOfFirst { it.episodeId == currentEpisodeId }
    }
    // 初始滚动位置直接建在当前集 (而非从 0 起再靠效应滚动): 播放器选集条每次展开
    // 都是全新组合, 若初始在 0, 焦点解析会赶在滚动前落到第一张卡 —— 当前集卡尚未
    // 组合, 进行落点的请求器没挂上, 落点就错了. 初始即在当前集,
    // 当前集卡首帧组合, 落焦必中.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0),
    )
    // 停靠一律 scrollToItem(index, 0): 每张卡 (含首卡) 停靠后左边缘都在 horizontalPadding,
    // 聚焦框恒定不动; 非首卡停靠时上一张卡在屏幕左缘自然露出 horizontalPadding - cellSpacing
    // 宽的切边. 详情页与播放器选集条完全同一套几何, 没有任何按调用方分叉的停靠逻辑.
    // 曾有过 focusedCardPeek 参数 (播放器条首卡停 horizontalPadding、其余卡停偏左 peek 处,
    // 用 scrollOffset=±peek 表达): TvScrollAnimator 与原生实现的 scrollOffset 符号语义相反
    // + 多条滚动路径混用, 真机上出过"每次右导航停靠位漂移", 且聚焦框要在两个停靠位之间
    // 跳 —— 整个删除, 别再引入非零 scrollOffset 或分叉停靠位.
    // 数据异步到达后补一次"滚到当前集" (进页首帧 episodes 常为空, currentIndex = -1).
    //
    // 用户已在轮播里定位过 (聚焦过卡片 / 在简介或弹窗上左右切过) 之后就不再跟随: 停留期间
    // "当前集"变化几乎只由用户自己长按标记看过引起 —— 标记当前集看过会让当前集顺延到下一集,
    // 跟过去就是"瞬移一格, 再被聚焦卡吸附动画滑回来"的抖动 (焦点此刻正被还给刚标记的那张卡).
    // [focusedEpisodeId] 只在效应体内读, 不作 key, 不会让本函数 body 订阅热状态
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && focusedEpisodeId == null) {
            listState.scrollToItem(currentIndex)
        }
    }

    // 焦点停在简介块上时左右键切换聚焦集: 展示的集号/标题/简介随之更新, 卡片行同步
    // 滑动一格 (此时没有卡片真正聚焦, 滚动不能靠 activeFocusEpisodeId 驱动), 到两端无效果
    val scope = rememberCoroutineScope()
    // 焦点不在卡片上时的两处滚动 (简介块左右切换 / 弹窗左右切换) 共用: 同一 listState 的
    // 动画互相取消时速度经它继承, 连发左右键时轮播连续流动. 卡片自己的吸附不走这里 ——
    // 那是焦点驱动的, 交给 pivot 式 BringIntoViewSpec (见下方 LazyRow)
    val scrollAnimator = remember { TvScrollAnimator() }
    val moveDisplayedBy: (Int) -> Unit = moveDisplayed@{ delta ->
        val displayedId = focusedEpisodeId ?: currentEpisodeId
        val index = episodes.indexOfFirst { it.episodeId == displayedId }.coerceAtLeast(0)
        val target = index + delta
        if (target !in episodes.indices) return@moveDisplayed
        focusedEpisodeId = episodes[target].episodeId
        scope.launch { scrollAnimator.animateScrollToItem(listState, target) }
    }

    // 跳到指定集 (选集网格菜单关闭后): 先滚动让目标卡片进入组合, 再复用 actionRestore 的下一帧聚焦机制.
    // 网格菜单的长按跳转在"按住途中"就触发 (不等松开), 同一次按住残余的确认键事件会落到跳转后
    // 聚焦的卡片上 —— 卡片的 tvLongPressKey 只认从自己起手的手势, 残余天然被吞掉, 无需额外防护
    LaunchedEffect(revealEpisodeId) {
        if (revealEpisodeId != null) {
            val index = episodes.indexOfFirst { it.episodeId == revealEpisodeId }
            if (index >= 0) {
                // 焦点归还的落点 = 跳转前"最后聚焦"的那张卡, 记下来供恢复解析放行 (不算用户介入)
                actionRestoreYieldEpisodeId = focusedEpisodeId
                // "最后聚焦"立刻改为目标集: 信息行 / 进行落点请求器 (fallbackIndex)
                // 都读它, 不改的话它们仍指着旧卡 —— 于是归还与显式聚焦一起把焦点往旧卡上拉,
                // 表现为"长按 N 却停在旧卡上" (旧卡滚出组合的远距离跳转才碰巧正常)
                focusedEpisodeId = revealEpisodeId
                listState.scrollToItem(index)
                actionRestoreEpisodeId = revealEpisodeId
            }
            onRevealConsumed()
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (header != null) {
            Box(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
                header()
            }
        }
        if (showEpisodeInfo) {
            FocusEpisodeInfoRow(
                episodes = episodes,
                currentEpisodeId = currentEpisodeId,
                focusedEpisodeId = { focusedEpisodeId },
                episodeOverviews = episodeOverviews,
                episodeRuntimes = episodeRuntimes,
                descContent = descContent,
                moveDisplayedBy = moveDisplayedBy,
                horizontalPadding = horizontalPadding,
                endPadding = endPadding,
            )
        }
        // 长按卡片打开的「本集详情」弹窗: 剧照 + 完整简介 (上下键滚动) + 单个可执行的操作按钮.
        //
        // 从原来锚在卡片下方的下拉菜单改成弹窗, 因为集简介在信息行里只有固定几行且不可聚焦,
        // 全文需要一个归宿 —— 而「想知道这一集讲什么」时用户的动作恰好就是聚焦这张卡, 所以
        // 长按它是最自然的入口. 弹窗只放一个按钮 (按当前状态给出唯一可执行的那个):
        // 已看过就只有「取消看过」, 反之只有「标记看过」—— 两个都摆着必有一个是空操作.
        //
        // 只组合一个实例 (而非每张卡各挂一个): 弹窗是全屏模态, 不需要锚定到卡片.
        actionTarget?.let { target ->
            if (onSetEpisodeCollectionType != null) {
                val watched = target.isDoneOrDropped
                val close = {
                    actionTarget = null
                    // 关闭后焦点回到弹窗当前显示的集 (弹窗关闭不会自动归还). 弹窗内切换过的话
                    // 显示的集 != 打开时长按的旧卡, 而系统归还的落点还是旧卡 —— 登记放行
                    actionRestoreYieldEpisodeId = actionOpenedOnEpisodeId
                    actionRestoreEpisodeId = target.episodeId
                }
                val still = episodeStills[target.episodeId]
                // 剧照做满幅背景 (弹窗自带遮罩), 不做正文上方的图块 —— 后者按宽高比铺开会吃掉
                // 大半高度, 把正文和按钮挤出布局. 显式标注类型: let 返回的 lambda 推不出 @Composable
                val stillBackground: (@Composable BoxScope.() -> Unit)? = if (still == null) {
                    null
                } else {
                    {
                        AsyncImage(
                            episodeStillImageRequest(LocalPlatformContext.current, still),
                            contentDescription = null,
                            Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                AniScrollableTextDialog(
                    title = "${target.sort}. ${target.nameCn.ifBlank { target.name }}",
                    text = mergedEpisodeDesc(episodeOverviews[target.episodeId], target.desc)
                        .ifBlank { stringResource(Lang.subject_details_no_summary) },
                    onDismissRequest = close,
                    // 左右键把弹窗原地切到相邻集的详情 (标题/剧照/简介/按钮全换), 背后的
                    // 卡片行同步滑动一格 —— 信息行与关闭后的焦点恢复目标都跟着
                    // [focusedEpisodeId] 走, 无需额外同步. 到两端无效果
                    onHorizontalNav = { delta ->
                        val index = episodes.indexOfFirst { it.episodeId == target.episodeId }
                        val next = index + delta
                        if (index >= 0 && next in episodes.indices) {
                            val nextEpisode = episodes[next]
                            actionTarget = nextEpisode
                            focusedEpisodeId = nextEpisode.episodeId
                            scope.launch { scrollAnimator.animateScrollToItem(listState, next) }
                        }
                    },
                    background = stillBackground,
                    // 面板与剧照同比例: 图铺满时不裁上下或左右. 无剧照的集也用同一比例,
                    // 否则弹窗尺寸会随 TMDB 有没有图而变
                    aspectRatio = EPISODE_STILL_ASPECT_RATIO,
                    action = { modifier ->
                        Button(
                            onClick = {
                                onSetEpisodeCollectionType.invoke(
                                    target,
                                    if (watched) {
                                        UnifiedCollectionType.NOT_COLLECTED
                                    } else {
                                        UnifiedCollectionType.DONE
                                    },
                                )
                                close()
                            },
                            modifier = modifier,
                        ) {
                            Text(
                                stringResource(
                                    if (watched) {
                                        Lang.subject_episode_unwatch
                                    } else {
                                        Lang.subject_episode_mark_watched
                                    },
                                ),
                            )
                        }
                    },
                )
            }
        }

        // 焦点从行外进入时的落点: 一律"展示中的那张卡" (上次聚焦的卡, 或在简介上左右切过之后
        // 的那张; 首次进入是当前集), 而不是交给空间焦点搜索 —— 否则从右上角的按钮向下导航会
        // 命中右缘刚组合出来的卡片, pivot 随即把整行往左拽.
        //
        // 用 onEnter 改道到这个请求器, **不用 `focusRestorer`**: 它记的是节点引用, 卡片滚出
        // 组合后引用失效就退化成"进组第一个可聚焦项"= 右缘那张 (探索页同一个坑); 而且它记的
        // "上次聚焦卡"与"展示中的集"会分叉 —— 在简介上左右切过之后, 上次聚焦卡可能还组合着,
        // 于是进行时焦点回到它、整行又滑回去一格.
        val restoreFocus = remember { FocusRequester() }
        // 落点下标跟随"展示中的集": 热状态推导包进 derivedStateOf, body 不读值 ——
        // 每个 item 自己再包一层"我是不是落点卡"的布尔 (见 LazyRow 内), 变化只重组进出的两张卡
        val fallbackIndexState = remember(episodes, currentEpisodeId) {
            derivedStateOf {
                val displayedId = focusedEpisodeId ?: currentEpisodeId
                val idx = episodes.indexOfFirst { it.episodeId == displayedId }
                if (idx >= 0) idx else 0
            }
        }

        // 固定锚点轮播: 聚焦卡片始终吸附在停靠位 (= contentPadding start), 按左右键时焦点的
        // 视觉位置不动, 卡片列表整体平滑滑过 (最后一集也一样 —— 行尾留出整行空白让末卡也能
        // 吸附到停靠位).
        //
        // 滚动交给官方 pivot 式 BringIntoViewSpec (与探索页卡片区同一套, 见
        // [tvAnchorBringIntoViewSpec]): 卡片一聚焦, 框架就把它滚到锚位, 每帧重算目标、速度
        // 连续. 此前是"no-op spec + 快照观察聚焦下标 + 手动 animateScrollToItem": 那条路要自己
        // 处理连发取消/速度继承, 且比焦点晚一帧, 焦点恢复期间还得额外躲开系统归还给旧卡的那
        // 一两帧 (否则"滑向旧卡又滑回来"). pivot 下这些都不存在 —— 归还与显式聚焦发生在同一
        // 帧序列里, 框架只会朝最后一个焦点滚, 速度连续所以中途改向也不跳.
        //
        // 焦点没在卡片上的两条路 (简介块左右键 / 数据到达后对齐当前集) 仍显式滚动, 见
        // moveDisplayedBy 与上面的 LaunchedEffect(currentIndex).
        val density = LocalDensity.current
        // 锚位 = 停靠位, 无需补偏差: 卡片的可聚焦节点就是卡片外框本身 (聚焦框是行层的
        // overlay, 向外探出而不内缩卡片), 焦点目标矩形与卡片外框一致
        val bringIntoViewSpec = remember(density, horizontalPadding) {
            tvAnchorBringIntoViewSpec(with(density) { horizontalPadding.toPx() })
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
            // 高度锁死在卡片高: 聚焦框是向外探出的 (offset 只挪位置不改上报尺寸, 见
            // [FocusEpisodeAnchorRing]), 它上报的高度是 cellHeight + 2*FOCUS_RING_GAP, 而框只在
            // 有卡片持焦时才组合 —— 不锁高度的话本 Box 会跟着焦点进出行在 144/150dp 之间来回跳,
            // 详情页里整行选集连带下方简介行硬跳 6dp. 锁了之后框照旧画到行外留白里 (父不裁剪).
            BoxWithConstraints(Modifier.height(cellHeight)) {
                LazyRow(
                    Modifier
                        .then(rowFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                        // 长按左右键的移动频率上限: 系统连发 ~20 次/秒, 每发都换卡的话滑动动画
                        // 不断被打断, 卡片是闪过去而不是滑过去 (与探索页卡片区同一个限流器)
                        .tvFocusMoveRateLimit()
                        // 进行落点改道 (见上方 restoreFocus). onEnter 只在**焦点组**节点上生效,
                        // 少一个 focusGroup 就完全不触发 (探索页真机踩过)
                        .focusProperties { onEnter = { runCatching { restoreFocus.requestFocus() } } }
                        .focusGroup(),
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                    // start: 停靠时聚焦卡片与其他区块左对齐; end: 行尾留出整行空白让末卡也能
                    // 吸附到行首. 都只是滚动停靠位, 卡片仍可画到全宽容器右边缘 (出血)
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        end = (this.maxWidth - horizontalPadding - cellWidth).coerceAtLeast(0.dp),
                    ),
                ) {
                    itemsIndexed(episodes, key = { _, item -> item.episodeId }) { index, item ->
                        // "我是不是XX"的判断一律包 derivedStateOf: 源状态每次导航都变
                        // (activeFocusEpisodeId 还会 置null→写新值 变两次), 直读让全部可见卡
                        // 每键陪跑重组两遍; 包过之后只有布尔真正翻转的两张卡重组.
                        // 聚焦外圈不在卡片上画 —— 由行叠放的固定锚位聚焦框统一画 (见 LazyRow 之后)
                        val isFallback by remember(index) {
                            derivedStateOf { index == fallbackIndexState.value }
                        }
                        val isActionRestoreTarget by remember(item.episodeId) {
                            derivedStateOf { item.episodeId == actionRestoreEpisodeId }
                        }
                        // Prime Video 式左侧压暗: 聚焦卡左侧的卡片 (停靠线左边露出的切边)
                        // 压暗, 全亮只留给聚焦卡及其右侧; 向右导航时离场卡滑进暗区的过程
                        // 即自然变暗, 穿过聚焦框轮廓的圆角错位也被压得看不出.
                        // 状态读在 graphicsLayer 的 lambda 里, 渐变过程只失效图层不重组
                        val dimmedPast by remember(index) {
                            derivedStateOf { dimPivotIndex > index }
                        }
                        val dimAlpha = animateFloatAsState(
                            if (dimmedPast) EPISODE_PAST_CARD_DIM_ALPHA else 1f,
                            tween(EPISODE_DIM_FADE_MILLIS),
                            label = "pastCardDim",
                        )
                        Box(
                            Modifier.graphicsLayer {
                                // 逐绘制指令调制 alpha, 不进离屏缓冲: 默认 Auto 档遇 alpha < 1 会把
                                // 整张卡先画进离屏缓冲再合成 (saveLayerAlpha), 而卡内容 (剧照 + 贴底
                                // 的字/进度条) 不重叠, 调制的结果与合成一致
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                alpha = dimAlpha.value
                            },
                        ) {
                            FocusEpisodeCard(
                                item,
                                stillUrl = episodeStills[item.episodeId],
                                isPlaying = item.episodeId == currentEpisodeId,
                                onClick = { onEpisodeClick(item) },
                                monochrome = monochrome,
                                glass = glass,
                                modifier = Modifier.width(cellWidth)
                                    .then(if (isFallback) Modifier.focusRequester(restoreFocus) else Modifier)
                                    .then(
                                        if (isActionRestoreTarget) {
                                            Modifier.focusRequester(actionRestoreFocus)
                                        } else Modifier,
                                    )
                                    .then(
                                        if (upFocus != null || downFocus != null) {
                                            Modifier.focusProperties {
                                                if (upFocus != null) up = upFocus
                                                if (downFocus != null) down = downFocus
                                            }
                                        } else Modifier,
                                    )
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            focusedEpisodeId = item.episodeId
                                            activeFocusEpisodeId = item.episodeId
                                            dimPivotIndex = index
                                        } else if (activeFocusEpisodeId == item.episodeId) {
                                            // 焦点离开整行时熄灭外圈; 行内移动会先聚焦新卡再走到这里, 不受影响.
                                            // dimPivotIndex 刻意不动: 行没滚回去, 压暗的分界线就还在原处 (见其声明)
                                            activeFocusEpisodeId = null
                                        }
                                    },
                                height = cellHeight,
                                progress = playProgress[item.episodeId],
                                onLongClick = if (onSetEpisodeCollectionType == null) null else {
                                    {
                                        actionOpenedOnEpisodeId = item.episodeId
                                        actionTarget = item
                                    }
                                },
                                onPressingChanged = { pressingCard = it },
                                // 边按方向键边按住确认键时, 卡片还在滑向固定聚焦框 —— 等它停进框里
                                // 才触发 (计时照常从按下算, 不会因此要按更久); 这种按住不出缩放反馈
                                pressReady = { !listState.isScrollInProgress },
                            )
                        }
                    }
                }
                // 固定锚位聚焦框 (Prime Video 式): 框钉在吸附停靠位不动, 左右导航时只有
                // 卡片列表在框下滑动; 焦点离开整行 (含移到简介上) 时熄灭, 语义同原先画在
                // 聚焦卡上的外圈. 热状态 (是否有聚焦卡) 经 provider lambda 传入, 只在子组件里读
                FocusEpisodeAnchorRing(
                    visible = { activeFocusEpisodeId != null },
                    pressed = { pressingCard },
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    horizontalPadding = horizontalPadding,
                    monochrome = monochrome,
                )
            }
        }
    }
}

/**
 * 选集轮播的固定锚位聚焦框: 钉在吸附停靠位不随卡片动. 样式与探索页竖版卡的聚焦框同款 ——
 * 描边画在卡片轮廓之外, 与卡片之间留一圈空隙 (框比卡大一圈, 圆角 = 卡片圆角 + 空隙).
 * [visible] 的源状态每次左右导航都变 (热状态), 在本组件内经 derivedStateOf 收窄成布尔,
 * 只在焦点进出行时翻转 (上层 BoxWithConstraints 作用域不订阅, 否则它的 LazyRow content
 * lambda 捕获不稳定引用, 每次进出行全部可见卡重建).
 */
@Composable
private fun FocusEpisodeAnchorRing(
    visible: () -> Boolean,
    pressed: () -> Boolean,
    cellWidth: Dp,
    cellHeight: Dp,
    horizontalPadding: Dp,
    monochrome: Boolean,
) {
    // 上层重组可能换 provider lambda 实例, 缓存进 derivedStateOf 前必须经 rememberUpdatedState
    // 读最新实例 —— 直接捕获会永久留住首帧那个 (真机踩过: 捕获了空 episodes 的旧 lambda
    // 让框永远不亮)
    val currentVisible by rememberUpdatedState(visible)
    val show by remember { derivedStateOf { currentVisible() } }
    val currentPressed by rememberUpdatedState(pressed)
    val pressedNow by remember { derivedStateOf { currentPressed() } }
    // 与卡片同一个比例、同一条默认曲线 -> 按住时框与卡一起缩, 不脱开
    val pressScale by animateFloatAsState(if (pressedNow) EPISODE_CARD_PRESS_SCALE else 1f)
    if (show) {
        Box(
            Modifier
                // 框比卡大一圈: 向左上各偏一个空隙宽度使卡片仍停在原位, 四边各探出一个
                // 空隙 (offset 允许负值; 父容器不裁剪, 探出到行距/留白里没关系)
                .padding(start = horizontalPadding)
                .offset {
                    val gap = FOCUS_RING_GAP.roundToPx()
                    IntOffset(-gap, -gap)
                }
                .size(cellWidth + FOCUS_RING_GAP * 2, cellHeight + FOCUS_RING_GAP * 2)
                // 定尺寸之后缩放 = 绕框自身中心缩, 与卡片 (绕卡中心缩) 同心
                .scale(pressScale)
                .border(
                    FOCUS_RING_WIDTH,
                    if (monochrome) {
                        // 黑白态: 纯白描边 (同播放器面板条目)
                        SolidColor(Color.White)
                    } else {
                        // 主题动态色渐变 (左上 primary → 右下 secondary)
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                            ),
                        )
                    },
                    RoundedCornerShape(EPISODE_CARD_CORNER + FOCUS_RING_GAP),
                ),
        )
    }
}

/**
 * 按住确认键时卡片与固定聚焦框的缩放比例 ("缩下去又弹回来"= 长按已触发).
 * 两者必须同值同曲线, 否则按住时框与卡脱开一圈.
 */
private const val EPISODE_CARD_PRESS_SCALE = 0.94f

/** TV 选集聚焦框的描边宽度 (同探索页竖版卡聚焦框). */
private val FOCUS_RING_WIDTH = 2.5.dp

/**
 * 聚焦卡左侧卡片的压暗亮度 (Prime Video 逐帧实测约四成亮): 左侧切边是"已经过去的内容",
 * 压暗与聚焦卡拉开主次; 离场卡滑进暗区时随 [EPISODE_DIM_FADE_MILLIS] 渐暗.
 */
private const val EPISODE_PAST_CARD_DIM_ALPHA = 0.45f

/** 左侧压暗的渐变时长 (与单格滚动 260ms 大致同步, 边滑边暗). */
private const val EPISODE_DIM_FADE_MILLIS = 200

/** TV 选集聚焦框与卡片轮廓之间的空隙 (框圆角 = 卡片圆角 + 此值, 同探索页竖版卡聚焦框). */
private val FOCUS_RING_GAP = 3.dp

/**
 * 玻璃态无图卡片的墨色浓度 (见 [FocusEpisodeCard] 的 `glass`).
 *
 * 比同页标签/按钮那档 ([GLASS_CONTAINER_ALPHA]) 略浓: 卡片面积大得多, 太淡就跟背景糊在一起,
 * 一排卡片看不出边界. 看过的再减一档, 与不透明那套里 `surfaceContainerLow` 的"退到后面"同义.
 */
private const val GLASS_CARD_ALPHA = 0.14f
private const val GLASS_CARD_WATCHED_ALPHA = 0.07f

/** 玻璃态"正在播放"卡片的底色不透明度: 比墨色档高, 主色调要压得住背景图才认得出是在播的那集. */
private const val GLASS_CARD_PLAYING_ALPHA = 0.55f

/**
 * 弹窗关闭后把焦点交还给指定卡片时, 目标须连续持有焦点的帧数才算稳.
 *
 * 不是"到位即收手": 系统把焦点还给弹窗打开前那张卡是异步的, 可能晚于我们抢到的那一刻,
 * 收手太早就被它抢回去 (表现为停在旧卡上). 多押几帧, 归还之后还能再抢回来.
 */
private const val FOCUS_HOLD_FRAMES = 3

/**
 * 详情页选集卡片的圆角半径 (网格卡 / TV 卡片本体 / TV 聚焦外圈描边共用, 三者必须一致).
 * 调小让卡片棱角更硬朗, 调大更圆润.
 */
internal val EPISODE_CARD_CORNER = 6.dp

/** 剧照宽高比 (TMDB still 均为 16:9), 也是本集详情弹窗的面板比例. */
private const val EPISODE_STILL_ASPECT_RATIO = 16f / 9f

/**
 * 分集剧照的图片请求: 显式按**源图尺寸**解码, 不跟随卡片的实际布局尺寸.
 *
 * Coil 的内存缓存 key 含请求尺寸, 而同一张剧照有三个尺寸各不相同的消费端 (详情页卡片 /
 * 播放器选集条卡片 / 长按弹窗的满幅背景). 跟随各自布局尺寸就是三份 key: 互相命中不了,
 * 每处都要从磁盘重读重解码; 而且谁先加载谁定分辨率, 小的先到时另一处还得升采样.
 *
 * 钉在源图尺寸不会解出更大的图 —— URL 已经降到 w780 档 (780x439), ORIGINAL 就是它的上限;
 * 在高密度 TV 上卡片本来就比源图大, 等于维持现状. 另一个好处是请求不再依赖布局测量,
 * 别处 (播放器进屏预取) 能构造出逐字段相同的请求, 预取才真的能被显示端命中.
 */
fun episodeStillImageRequest(context: PlatformContext, stillUrl: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(tmdbStillCardSizeUrl(stillUrl))
        .size(Size.ORIGINAL)
        .build()

/** 聚焦集简介的行数 (全文在长按卡片的本集详情弹窗里). */
internal const val EPISODE_FOCUSED_DESC_LINES = 3

/**
 * 时长/日期的行高 (字号仍是 bodyLarge 的 16sp, 只收紧行盒).
 *
 * 收紧是为了给"一个顶上边界、一个顶下边界"腾出中间的空档: 两行按标称行高 (24sp) 摊开正好等于
 * 左侧三行简介的高度, 上下贴边后中间一点缝都不剩, 看起来仍像挤在一起的两行.
 */
private val EPISODE_META_LINE_HEIGHT = 20.sp

/**
 * 详情页正文块 (作品简介 / 集简介) 的内边距.
 *
 * 集简介右侧的时长/日期按同一值上下内收, 两行才分别与正文的首行/末行对齐 —— 正文缩在自己的
 * 内边距里, 元数据若从块的边界起排, 会比正文高出/低出正好这一截, 看着就是对不上.
 */
val DETAILS_TEXT_CONTENT_PADDING = 8.dp

/**
 * 详情页正文块 (作品简介 / 集简介) 尾部的恒定预留宽度.
 *
 * 两块**共用同一个值**, 正文宽度才完全一致: 作品简介用它给右下角的「显示更多」按钮让位,
 * 集简介用它给右侧的时长/日期让位. 各自按实际内容宽度让位的话, 两块正文一宽一窄,
 * 上下叠在一页里很显眼.
 *
 * 恒定 (不随内容长短变化) 还有一层必要: 作品简介的"是否被截断"由排版决定, 而预留宽度又会
 * 改变排版, 按需预留会互为因果抖动.
 *
 * 取值贴着两件东西里较宽的那个, 不留富余 —— 正文越宽越好读:
 * * 「显示更多」按钮 ≈ 76dp (labelLarge 四个汉字 56dp + 左右内边距各 10dp)
 * * 播出日期 `yyyy-MM-dd` ≈ 82dp (bodyLarge 16sp, 数字字宽 0.556em)
 */
val DETAILS_TEXT_END_RESERVE = 88.dp

/** TV 详情页弹出菜单容器的不透明度: 半透明, 隐约透出下层内容 (全部菜单统一用此值). */
const val MENU_CONTAINER_ALPHA = 0.95f

/**
 * TV 选集卡片 (大卡): 有 TMDB 分集缩略图时图占满卡片, 集号/集名压在
 * 底部 scrim 上; 无图时回退 [EpisodeGridCell] 的纯文字样式, 尺寸一致. 聚焦时集名跑马灯.
 */
@Composable
fun FocusEpisodeCard(
    item: EpisodeListItem,
    stillUrl: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 144.dp,
    /** 播放进度 (0..1); 非 null 时卡片底部画进度条. */
    progress: Float? = null,
    /** 非 null 时支持长按确认键 (按住 OK) 触发, 用于打开单集操作菜单. */
    onLongClick: (() -> Unit)? = null,
    /** 按住确认键的状态变化上报 (行层的固定聚焦框据此同步缩放). */
    onPressingChanged: ((Boolean) -> Unit)? = null,
    /** 长按触发的闸门 (见 [tvLongPressKey] 的 `readyToFire`): 卡片还在滑向聚焦框时先别触发. */
    pressReady: (() -> Boolean)? = null,
    /**
     * true 时改用纯黑白配色 (半透明白底 + 白字, 聚焦即白底黑字), 与播放器控制层的胶囊按钮一致.
     *
     * 给播放器选集条用: 那片卡片浮在视频画面上, 主题色会跟画面本身抢注意力.
     */
    monochrome: Boolean = false,
    /**
     * true 时无图卡片改用半透明玻璃底 ([glassContainerColor]) 而不是实心 `surfaceContainer*`.
     *
     * 给 TV 详情页用: 那页底下压着 backdrop 背景图, 实心底色会把图整块盖掉, 一排无图卡片
     * 看起来就是几个色块; 玻璃底透出背景, 与同页的标签/按钮 (它们本来就是这个底) 一致.
     * 有图的卡片不受影响 —— 图本身就铺满了整张卡.
     */
    glass: Boolean = false,
) {
    val isWatched = item.isDoneOrDropped
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val containerColor = when {
        // 黑白态: 底一律半透明白 (与胶囊按钮同一档 alpha), 聚焦即实心白
        monochrome -> when {
            focused -> Color.White
            isPlaying -> Color.White.copy(alpha = 0.28f)
            isWatched -> Color.White.copy(alpha = 0.08f)
            else -> Color.White.copy(alpha = 0.14f)
        }

        // 玻璃态: 与不透明那套一一对应 (在播=主色调, 看过=更淡, 其余=基准), 只是都透出背景.
        // 在播那档保留 primaryContainer 的色相 (它是"正在播放"的既有语义色), 只压透明度
        glass -> when {
            isPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = GLASS_CARD_PLAYING_ALPHA)
            isWatched -> glassContainerColor(GLASS_CARD_WATCHED_ALPHA)
            else -> glassContainerColor(GLASS_CARD_ALPHA)
        }

        isPlaying -> MaterialTheme.colorScheme.primaryContainer
        isWatched -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val dimmed = if (monochrome) {
        Color.White.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val sortColor = when {
        // 白底上一律黑字 (反色示焦, 同胶囊按钮)
        monochrome && focused -> Color.Black
        monochrome -> if (isWatched) dimmed else Color.White
        isPlaying -> MaterialTheme.colorScheme.primary
        isWatched -> dimmed
        else -> LocalContentColor.current
    }
    val nameColor = when {
        monochrome && focused -> Color.Black.copy(alpha = 0.75f)
        monochrome -> if (isWatched) dimmed else Color.White.copy(alpha = 0.85f)
        isWatched -> dimmed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 进度条: 黑白态下白底上用黑, 否则白 (图上) / 主色 (纯文字卡)
    val progressColor = when {
        monochrome && focused -> Color.Black
        monochrome -> Color.White
        else -> MaterialTheme.colorScheme.primary
    }
    // 已看的集固定满条 ("看过"由进度条表达, 图不再压暗); 未看完的显示续播点
    val effectiveProgress = if (isWatched) 1f else progress

    val name = item.nameCn.ifBlank { item.name }

    // 图标尺寸用 sp (跟随字体缩放), 并按矢量内部留白补偿, 使可见图形高度 ≈ 集号数字
    // (titleSmall 14sp) 的大写高度 ~10sp: PlayArrow 三角占 24 视口的 14 (58%) → 17sp;
    // GraphicEq 占 16/24 (67%) → 15sp
    val playIconSize = with(LocalDensity.current) { 17.sp.toDp() }
    val playingIconSize = with(LocalDensity.current) { 15.sp.toDp() }

    // 长按确认键 (同网格方块, 共用实现见 tvLongPressKey): 按住到阈值当场触发, 不等松开.
    // 网格菜单长按跳转把焦点送到本卡时, 那次按住的残余按键不是从本卡起手的手势, 会被它吞掉
    val longPressState = rememberTvLongPressKeyState()
    val longPressModifier = if (onLongClick == null) {
        Modifier
    } else {
        Modifier.tvLongPressKey(
            onLongPress = onLongClick,
            onShortPress = onClick,
            state = longPressState,
            readyToFire = pressReady,
        )
    }

    // 按住确认键的视觉反馈 (同网格方块): 按住期间轻微缩小, 达到长按阈值后弹回 —
    // "缩下去又弹回来" = 长按已经触发
    val pressing = onLongClick != null && longPressState.pressing
    val pressScale by animateFloatAsState(if (pressing) EPISODE_CARD_PRESS_SCALE else 1f)
    // 上报给行: 固定锚位聚焦框在行层, 拿不到本卡的按住状态, 要靠它跟着缩. 同一时刻只有聚焦卡
    // 收得到按键, 所以行里一个布尔就够
    if (onPressingChanged != null) {
        LaunchedEffect(pressing) { onPressingChanged(pressing) }
    }
    Surface(
        onClick = onClick,
        // scale 放链最外层: 按住缩小时整张卡 (含调用方 modifier 里的装饰) 一起缩;
        // 行层的固定锚位聚焦框按同一比例同步缩 —— 框不缩的话按住时卡与框脱开一圈, 像焦点掉了
        modifier = Modifier.scale(pressScale).then(modifier).height(height).then(longPressModifier),
        shape = RoundedCornerShape(EPISODE_CARD_CORNER),
        color = containerColor,
        interactionSource = interactionSource,
    ) {
        if (stillUrl != null) {
            Box(Modifier.fillMaxSize()) {
                val context = LocalPlatformContext.current
                // 快速滑过的并发洪峰会让个别请求失败并卡在 Error (见 rememberAsyncImageRetryState)
                val retry = rememberAsyncImageRetryState(stillUrl)
                AsyncImage(
                    // 卡片不用 original 档原图 (那是给全屏 hero 的), 降到 w780 省下载/解码;
                    // 请求尺寸固定为源图尺寸, 与另外两个消费端共用一条缓存 (见 episodeStillImageRequest).
                    // remember: 卡片重组极频繁 (聚焦/跑马灯/进度), 每次新建请求对象没必要
                    if (retry.suppressed) null else remember(context, stillUrl) { episodeStillImageRequest(context, stillUrl) },
                    contentDescription = null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { retry.onError() },
                )
                // 底部 scrim 保证集号/集名可读
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                )
                Row(
                    Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        // 底部留出进度条区域 (空隙 4dp + 条 4dp), 避免文字与进度条重叠
                        .padding(horizontal = 10.dp)
                        .padding(top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 聚焦时文字前显示播放三角; 未聚焦的播放中卡片仍显示声浪图标
                    if (focused) {
                        Icon(
                            rememberVectorPainter(Icons.Rounded.PlayArrow),
                            contentDescription = null,
                            Modifier.size(playIconSize),
                            tint = Color.White,
                        )
                    } else if (isPlaying) {
                        Icon(
                            rememberVectorPainter(Icons.Rounded.GraphicEq),
                            contentDescription = null,
                            Modifier.size(playingIconSize),
                            tint = Color.White,
                        )
                    }
                    // 集号与集名字号不同 (titleSmall/bodySmall), 用基线对齐而非盒子居中,
                    // 否则行高差会让小字看起来上下飘
                    Text(
                        item.sort.toString(),
                        Modifier.alignByBaseline(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        name,
                        Modifier.alignByBaseline()
                            .then(if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                    )
                }
                FocusEpisodeProgressBar(
                    effectiveProgress,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    Modifier.align(Alignment.BottomStart),
                    // 图上一律白 (scrim 之上), 黑白态也一样
                    progressColor = if (monochrome) Color.White else MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // 聚焦时文字前显示播放三角; 未聚焦的播放中卡片仍显示声浪图标
                    if (focused) {
                        Icon(
                            rememberVectorPainter(Icons.Rounded.PlayArrow),
                            contentDescription = null,
                            Modifier.size(playIconSize),
                            tint = sortColor,
                        )
                    } else if (isPlaying) {
                        Icon(
                            rememberVectorPainter(Icons.Rounded.GraphicEq),
                            contentDescription = null,
                            Modifier.size(playingIconSize),
                            tint = sortColor,
                        )
                    }
                    Text(
                        item.sort.toString(),
                        color = sortColor,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    name,
                    if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                    color = nameColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
                }
                FocusEpisodeProgressBar(
                    effectiveProgress,
                    trackColor = if (monochrome && focused) {
                        Color.Black.copy(alpha = 0.2f)
                    } else if (monochrome) {
                        Color.White.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    },
                    Modifier.align(Alignment.BottomStart),
                    progressColor = progressColor,
                )
            }
        }
    }
}

/** 展示用分集简介: TMDB 本地化简介 (跟随 APP 语言) 在前, Bangumi 简介 (多为日文) 跟在后面. */
fun mergedEpisodeDesc(tmdbOverview: String?, bangumiDesc: String): String =
    listOfNotNull(
        tmdbOverview?.takeIf { it.isNotBlank() },
        bangumiDesc.takeIf { it.isNotBlank() },
    ).joinToString("\n\n")

/**
 * 卡片底部的播放进度条 (细条). [progress] 为 null 或 0 时不绘制;
 * 已看完的集也会有满条 (进度记录保留到结尾).
 * 与卡片边缘留出间距: 贴死底边会被卡片圆角与聚焦描边裁掉一截.
 */
@Composable
private fun FocusEpisodeProgressBar(
    progress: Float?,
    trackColor: Color,
    modifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (progress == null || progress <= 0f) return
    Box(
        modifier
            // 尺寸 (960px 卡 = 240dp): 条厚 8px=2dp,
            // 左右内缩 ~20px=5dp (避开圆角), 离底边一点点空隙
            .padding(horizontal = 10.dp)
            .padding(bottom = 4.dp)
            .fillMaxWidth()
            .height(2.5.dp)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            Modifier.fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(progressColor),
        )
    }
}

/**
 * 聚焦集信息行右侧的元数据列: 上行时长 (分钟, 来自 TMDB), 下行播出日期 (Bangumi).
 * 两者都缺失时不占位. 字号与左侧标题块对齐 (时长同标题, 日期略小).
 */
@Composable
fun FocusEpisodeMetaColumn(
    runtimeMinutes: Int?,
    airDate: PackedDate,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
) {
    val dateText = formatAirDate(airDate)
    if (runtimeMinutes == null && dateText == null) return
    Column(
        modifier,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.End,
    ) {
        if (runtimeMinutes != null) {
            Text(
                stringResource(Lang.subject_episode_duration_minutes, runtimeMinutes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
        if (dateText != null) {
            Text(
                dateText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
    }
}

/**
 * [FocusEpisodeGridDropdown] 里聚焦集的元数据: 时长与播出日期**并作一行**
 * ("24 分钟 · 2024-01-15"), 字号与左边的标题一致.
 *
 * 不用 [FocusEpisodeMetaColumn] 那种上下两行: 那一行左边只有一行标题, 右边一旦是两行就由它
 * 决定行高 —— 而时长来自 TMDB, 异步到达且不是每集都有, 于是在"有时长"与"没时长"的集之间移动
 * 焦点时整行会在一行与两行之间跳. 菜单是从锚点**向上**弹的 (见上面的 positionProvider),
 * 高度一变整个面板就跟着上下抖.
 *
 * 播放器选集条那边不受影响, 不必跟着改: 它左侧是固定三行的简介, 行高由简介决定,
 * 右边两行还是一行都撑不动它.
 */
@Composable
private fun FocusEpisodeMetaLine(
    runtimeMinutes: Int?,
    airDate: PackedDate,
    modifier: Modifier = Modifier,
) {
    val text = listOfNotNull(
        runtimeMinutes?.let { stringResource(Lang.subject_episode_duration_minutes, it) },
        formatAirDate(airDate),
    ).joinToString(EPISODE_META_SEPARATOR)
    if (text.isEmpty()) return
    Text(
        text,
        modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        softWrap = false,
    )
}

/** 时长与播出日期之间的分隔. */
private const val EPISODE_META_SEPARATOR = " · "

private fun formatAirDate(date: PackedDate): String? {
    if (date == PackedDate.Invalid) return null
    val month = date.month.toString().padStart(2, '0')
    val day = date.day.toString().padStart(2, '0')
    return "${date.year}-$month-$day"
}

/**
 * TV 选集快速跳转子菜单: 从 Hero "选集"圆钮上方弹出 (信息带贴屏幕底部), 内容为数字方块网格
 * (沿用旧版选集对话框 [me.him188.ani.app.ui.subject.episode.list.EpisodeListDialog] 的形态).
 * 轮播是主体, 这里是辅助入口 —— 上千集 (如 ONE PIECE) 时逐格横向导航不现实,
 * 网格配合 D-pad 纵向移动一排跳十几集. 上千集必须懒加载, 用 LazyVerticalGrid
 * 而非旧版的 FlowRow. 打开时自动滚动到当前集并聚焦.
 *
 * 用裸 [Popup] 而非 material3 DropdownMenu: 后者的内容列带 width(IntrinsicSize.Max),
 * 内在尺寸测量会穿透到 LazyVerticalGrid (SubcomposeLayout 不支持内在测量, 直接崩溃).
 *
 * 需组合在锚点 (入口圆钮) 所在的 Box 内, 菜单弹出位置跟随锚点.
 */
@Composable
fun FocusEpisodeGridDropdown(
    expanded: Boolean,
    episodes: List<EpisodeListItem>,
    currentEpisodeId: Int?,
    onEpisodeClick: (EpisodeListItem) -> Unit,
    /** 返回键/点击外部关闭菜单 (不跳转轮播, 调用方把焦点还给入口圆钮). */
    onDismissRequest: () -> Unit,
    /**
     * 特别篇 (SP/OVA/OAD 等非正片, 即 `EpisodeListUiState.otherEpisodes`): 网格里排在正片
     * 之后, 中间隔一整行分隔线 —— 数字方块是快速跳转用的, 分组比混排好定位.
     *
     * 与 [FocusEpisodeCarousel] 的排法不同: 那边收 `allEpisodes` (特别篇按序号插在正片之间,
     * 与播放器选集列表一致), 因为卡片带剧照与简介, 按播出顺序读才连贯.
     */
    specialEpisodes: List<EpisodeListItem> = emptyList(),
    /** episodeId -> 分集时长 (分钟, TMDB), 显示在聚焦集标题右侧; 缺失则不显示. */
    episodeRuntimes: Map<Int, Int> = emptyMap(),
    /** 非 null 时标题行右侧显示缓存入口 (跳转本条目缓存页), 对应旧版选集对话框右上角的下载按钮. */
    onCacheClick: (() -> Unit)? = null,
    /** 非 null 时方格支持长按确认键 (按住 OK): 由调用方关闭菜单并让轮播跳到该集. */
    onEpisodeLongClick: ((EpisodeListItem) -> Unit)? = null,
) {
    if (!expanded) return
    // 从入口圆钮上方弹出 (信息带贴屏幕底部, 向下没有空间; 观感对齐收藏按钮的菜单):
    // 菜单底缘在按钮顶缘上方 8dp, 左缘对齐按钮左缘, 越界时收回窗口内
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
        // 方块里只有集数, 聚焦集的标题展示在网格上方这一行 (整个内容随关闭离开组合, 状态自动重置)
        var focusedEpisodeId by remember { mutableStateOf<Int?>(null) }
        val gridState = rememberLazyGridState()
        val currentFocus = remember { FocusRequester() }
        // 打开时滚到当前集: 当前集必是正片, 下标直接在 episodes 里算 (特别篇排在其后,
        // 不影响正片下标). 上方的聚焦集信息行则要能显示特别篇, 用合并后的列表查.
        val currentIndex = remember(episodes, currentEpisodeId) {
            if (currentEpisodeId == null) -1 else episodes.indexOfFirst { it.episodeId == currentEpisodeId }
        }
        val allEpisodes = remember(episodes, specialEpisodes) {
            if (specialEpisodes.isEmpty()) episodes else episodes + specialEpisodes
        }
        LaunchedEffect(currentIndex) {
            if (currentIndex >= 0) {
                gridState.scrollToItem(currentIndex)
                // 到位确认 (聚焦格的 onFocusChanged 会置 focusedEpisodeId): 弹出窗口的
                // 异步焦点分配可能覆盖单次请求
                // 起点快照 + 放弃判据: 用户在这 20 帧内自己移到别的格就让路, 不抢回来
                val startEpisodeId = focusedEpisodeId
                resolveFocusRepeatedly(
                    attempts = 20, delayMillis = 0,
                    arrived = { focusedEpisodeId == currentEpisodeId },
                    abandon = {
                        focusedEpisodeId.let { it != null && it != startEpisodeId && it != currentEpisodeId }
                    },
                ) {
                    runCatching { currentFocus.requestFocus() }
                }
            }
        }
        // 详情页页面级禁用了 BringIntoView (区块吸附需要), Popup 内容继承了该设置,
        // 会导致网格不跟随焦点滚动 (焦点走出视口后卡在最后一批已组合的格子上).
        // 这里恢复接口默认行为 (最小滚动保持聚焦格可见).
        val defaultBringIntoView = remember { object : BringIntoViewSpec {} }
        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoView) {
        Surface(
            Modifier.width(560.dp).heightIn(max = 480.dp),
            shape = RoundedCornerShape(16.dp),
            // 半透明容器 (详情页所有弹出菜单统一), 隐约透出下层内容
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MENU_CONTAINER_ALPHA),
            shadowElevation = 8.dp,
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Lang.subject_details_episodes),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (onCacheClick != null) {
                    IconButton(onCacheClick) {
                        Icon(
                            rememberVectorPainter(Icons.Rounded.Download),
                            contentDescription = stringResource(Lang.subject_episode_cache),
                        )
                    }
                }
            }
            FocusEpisodeGridHeaderLine(
                allEpisodes = allEpisodes,
                currentEpisodeId = currentEpisodeId,
                focusedEpisodeId = { focusedEpisodeId },
                episodeRuntimes = episodeRuntimes,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(56.dp),
                modifier = Modifier.weight(1f, fill = false),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(episodes, key = { _, item -> item.episodeId }) { index, item ->
                    FocusEpisodeSortCell(
                        item,
                        isPlaying = item.episodeId == currentEpisodeId,
                        onClick = { onEpisodeClick(item) },
                        modifier = (if (index == currentIndex) Modifier.focusRequester(currentFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused) focusedEpisodeId = item.episodeId },
                        onLongClick = onEpisodeLongClick?.let { longClick -> { longClick(item) } },
                    )
                }
                // 正片与特别篇之间隔一整行分隔线 (对应旧版选集对话框里那条 HorizontalDivider).
                // 独占一行不可聚焦, D-pad 从正片末行下移直接落到特别篇首格.
                if (specialEpisodes.isNotEmpty() && episodes.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                }
                items(specialEpisodes, key = { it.episodeId }) { item ->
                    FocusEpisodeSortCell(
                        item,
                        isPlaying = item.episodeId == currentEpisodeId,
                        onClick = { onEpisodeClick(item) },
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) focusedEpisodeId = item.episodeId },
                        onLongClick = onEpisodeLongClick?.let { longClick -> { longClick(item) } },
                    )
                }
            }
            }
        }
        }
    }
}

/**
 * 轮播上方的"聚焦集"信息行: 左 = 简介 (固定行数截断), 右 = 时长 / 播出日期上下堆叠.
 *
 * 单独成组件: "展示中的集"由 [focusedEpisodeId] (每次左右键都变) 推导, 本行是它在轮播里
 * 唯一的组合期读者 —— 读收在这里, 左右键只重组本行, 不重启整个轮播作用域 (那会让 LazyRow
 * content lambda 换新实例、全部可见卡全新重建; 2026-07-31 实测).
 *
 * 不再单列"集号 + 标题"一行: 卡片上已经有同样的集号与标题, 重复占掉一行高度;
 * 时长与日期是次要元数据 (labelMedium + onSurfaceVariant), 堆到简介右侧、
 * 顶对齐, 读起来像简介的附注而不是标题的一部分.
 *
 * 行宽以 [endPadding] 收边, 与上方大标题/简介共用同一右边界 (不与封面重叠).
 * 简介高度不写死: 由简介组件自己按 minLines 预留固定行数 —— 切集时长短不同,
 * 不预留会让下方卡片行跳动. (写死 dp 的老做法只要比 行高x行数 差几像素末行就被裁掉:
 * 排版真正的约束是容器高度而不是 maxLines, 而标称行高又摊不平首行的字体内衬.)
 *
 * 用 Box + matchParentSize 而不是 Row: 元数据列不参与测量, 于是
 *  1) 集简介的正文宽度只由尾部预留决定, 与上方作品简介**完全一致** (两块共用
 *     [DETAILS_TEXT_END_RESERVE]) —— 用 Row 的话正文还要减去元数据的实测宽度,
 *     两块正文宽窄不一;
 *  2) 元数据列直接拿到简介块的高度做 SpaceBetween, 不必用 height(IntrinsicSize.Min)
 *     反推行高 (内在测量会穿透到子树, 是个容易踩崩的约束).
 */
@Composable
private fun FocusEpisodeInfoRow(
    episodes: List<EpisodeListItem>,
    currentEpisodeId: Int?,
    focusedEpisodeId: () -> Int?,
    episodeOverviews: Map<Int, String>,
    episodeRuntimes: Map<Int, Int>,
    descContent: (@Composable ColumnScope.(desc: String, onHorizontalNav: (delta: Int) -> Unit) -> Unit)?,
    moveDisplayedBy: (Int) -> Unit,
    horizontalPadding: Dp,
    endPadding: Dp,
) {
    val displayed = episodes.firstOrNull { it.episodeId == (focusedEpisodeId() ?: currentEpisodeId) }
        ?: episodes.firstOrNull()
        ?: return
    Box(Modifier.fillMaxWidth().padding(start = horizontalPadding, end = endPadding)) {
        val desc = mergedEpisodeDesc(episodeOverviews[displayed.episodeId], displayed.desc)
        Column(Modifier.fillMaxWidth().padding(end = DETAILS_TEXT_END_RESERVE)) {
            if (descContent != null) {
                // 简介为空也保持组合: 左右键切到无简介的集时占位仍在, 高度不跳
                descContent(desc, moveDisplayedBy)
            } else if (desc.isNotBlank()) {
                Text(
                    desc,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = EPISODE_FOCUSED_DESC_LINES,
                    minLines = EPISODE_FOCUSED_DESC_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 时长 / 播出日期: 落在简介的尾部预留里, 一个顶着简介块上边界、一个顶着下边界
        // (SpaceBetween 撑在 matchParentSize 拿到的简介高度上), 行高不必再跟简介凑总高.
        //
        // 弱化靠 onSurfaceVariant + 常规字重, 不靠缩小字号 —— 10 英尺距离下字号再小就
        // 读不清了. 用 bodyLarge 而不是 titleMedium: 同为 16sp, 但不带 Medium 字重.
        val metaStyle = MaterialTheme.typography.bodyLarge
            .copy(lineHeight = EPISODE_META_LINE_HEIGHT)
        Column(
            // 上下按正文的内边距内收: 时长与正文首行对齐, 日期与正文末行对齐.
            // 不内收就会从块的边界起排, 比正文高出/低出这一截
            Modifier.matchParentSize().padding(vertical = DETAILS_TEXT_CONTENT_PADDING),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            episodeRuntimes[displayed.episodeId]?.let { runtimeMinutes ->
                Text(
                    stringResource(Lang.subject_episode_duration_minutes, runtimeMinutes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = metaStyle,
                    maxLines = 1,
                )
            }
            formatAirDate(displayed.airDate)?.let { dateText ->
                Text(
                    dateText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = metaStyle,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 选集网格弹窗顶部的"聚焦集"信息行: 集号+标题 (太长时跑马灯, 不溢出则静止) + 右侧时长/日期.
 *
 * 单独成组件: [focusedEpisodeId] 每次格间移动都变, 唯一的组合期读者就是本行 —— 读收在
 * 这里, 格间移动只重组这一行; 读在 Popup 内容作用域的话, 每按一格 ~56 个数字方块全量
 * 重建 (2026-07-31 实测 epGridCell 每 15s 段 104 次, 是详情页最大的重组消费者).
 *
 * 恒为单行: 左标题与右侧元数据同字号、同 maxLines = 1, 行高与"有没有时长/日期"无关.
 * 菜单从锚点向上弹, 内容高度一变整个面板就上下抖 (见 [FocusEpisodeMetaLine]).
 */
@Composable
private fun FocusEpisodeGridHeaderLine(
    allEpisodes: List<EpisodeListItem>,
    currentEpisodeId: Int?,
    focusedEpisodeId: () -> Int?,
    episodeRuntimes: Map<Int, Int>,
) {
    val displayed = allEpisodes.firstOrNull { it.episodeId == (focusedEpisodeId() ?: currentEpisodeId) }
        ?: allEpisodes.firstOrNull()
        ?: return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${displayed.sort}. ${displayed.nameCn.ifBlank { displayed.name }}",
            Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        FocusEpisodeMetaLine(
            runtimeMinutes = episodeRuntimes[displayed.episodeId],
            airDate = displayed.airDate,
        )
    }
}

/**
 * [FocusEpisodeGridDropdown] 里的数字方块. 着色沿用 [EpisodeGridCell] 规则, 未开播的集置灰.
 * [onLongClick] 非 null 时支持长按确认键 (按住 OK) 触发: 检测方式同 [FocusEpisodeCard],
 * 但按住计数一到阈值就立即触发 (不等松开) —— 跳转类操作即时反馈更顺手.
 */
@Composable
private fun FocusEpisodeSortCell(
    item: EpisodeListItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val longPressState = rememberTvLongPressKeyState()
    val longPressModifier = if (onLongClick == null) {
        Modifier
    } else {
        Modifier.tvLongPressKey(
            onLongPress = onLongClick,
            onShortPress = onClick,
            state = longPressState,
        )
    }
    val isWatched = item.isDoneOrDropped
    val containerColor = when {
        isPlaying -> MaterialTheme.colorScheme.primaryContainer
        isWatched || !item.isBroadcast -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val sortColor = when {
        isPlaying -> MaterialTheme.colorScheme.primary
        !item.isBroadcast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isWatched -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    // 按住确认键的视觉反馈: 按住期间方块轻微缩小; 达到长按阈值后恢复原状 —
    // "缩下去又弹回来" = 长按已经触发
    val pressing = onLongClick != null && longPressState.pressing
    val pressScale by animateFloatAsState(if (pressing) 0.88f else 1f)
    Surface(
        onClick = onClick,
        // scale 放链最外层: 调用方 modifier 里可能带描边/底色, 按住缩小时一起缩
        modifier = Modifier.scale(pressScale).then(modifier).height(48.dp).then(longPressModifier),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                item.sort.toString(),
                color = sortColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
    }
}
