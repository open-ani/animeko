/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.repository.subject.SetSubjectCollectionTypeOrDeleteUseCase
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.foundation.focus.GridFocusController
import me.him188.ani.app.ui.foundation.focus.GridFocusTransitAnchor
import me.him188.ani.app.ui.foundation.focus.TV_FOCUS_MOVE_MAX_PER_SECOND_HORIZONTAL
import me.him188.ani.app.ui.foundation.focus.TvScrollAnimator
import me.him188.ani.app.ui.foundation.focus.gridKeyNavigation
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.foundation.focus.tvFocusMoveRateLimit
import me.him188.ani.app.ui.foundation.consumeHeldConfirmKey
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.tv.TV_HERO_MEDIA_DEBOUNCE_MILLIS
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_CARD_SPACING
import me.him188.ani.app.ui.foundation.tv.TV_PORTRAIT_CARD_COVER_RATIO
import me.him188.ani.app.ui.foundation.tv.TvFullScreenBackdropLayer
import me.him188.ani.app.ui.foundation.tv.TvPortraitCard
import me.him188.ani.app.ui.foundation.tv.tvPlayKeyForceRefresh
import me.him188.ani.app.ui.foundation.tv.tvHeroContentColor
import me.him188.ani.app.ui.foundation.tv.tvHeroSecondaryContentColor
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_schedule_episode
import me.him188.ani.app.ui.lang.exploration_schedule_episode_ep_and_sort
import me.him188.ani.app.ui.lang.exploration_schedule_last_weekday
import me.him188.ani.app.ui.lang.exploration_schedule_next_weekday
import me.him188.ani.app.ui.lang.exploration_schedule_this_weekday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_friday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_monday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_saturday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_sunday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_thursday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_tuesday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_wednesday
import me.him188.ani.app.ui.lang.exploration_tv_schedule_empty
import me.him188.ani.app.ui.lang.exploration_tv_schedule_following
import me.him188.ani.app.ui.lang.exploration_tv_schedule_now
import me.him188.ani.app.ui.lang.exploration_tv_schedule_today
import me.him188.ani.app.ui.lang.exploration_tv_schedule_total
import me.him188.ani.app.ui.lang.exploration_tv_schedule_upcoming
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.subject.collection.components.EditCollectionTypeDropDown
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.SubjectEnter
import me.him188.ani.utils.analytics.recordEvent
import org.jetbrains.compose.resources.stringResource

/**
 * TV 新番时间表: 日期胶囊行 (常驻不滚) + 当天从早到晚的竖版海报网格.
 *
 * PC 端把 15 天做成 15 条并排的纵向列表, 靠横向翻页浏览; 遥控器上下键既要走列表内条目又要跨列
 * 换天, 方向语义打架. 这里把两个维度拆正交: **日期走胶囊行 (←→), 时间走网格 (网格内线性)**.
 *
 * 本页是**独立目的地** (探索页 hero 的"新番时间表"入口进来, 见 NavRoutes.Schedule), 整屏归自己,
 * 没有侧边栏也没有标签行 —— 出口只有返回键.
 *
 * 焦点动线: 进页落在选中的日期胶囊, ↓ 进网格首卡, 网格顶行 ↑ 回日期行.
 * 网格内左右键按时间线性移动 (时间是一条线, 不是二维表): 行末按右接下一行行首, 行首按左接
 * 上一行行末, 走到全天两端再跨天 (全天第一张按左 → 上一天最后一张, 最后一张按右 → 下一天第
 * 一张). 换天时焦点先钉到隐形锚点, 见 [GridFocusTransitAnchor].
 * 卡片区与日期行的边缘按键一律消费掉: 焦点一旦交回默认方向搜索, 落点就不可预测.
 * 返回键逐层往回: 网格非首卡 → 首卡 → 日期行 → 退出本页 (最后一步不消费, 交给导航返回).
 *
 * 信息呈现: 日期与周几常驻在胶囊上 (网格滚动不会把它带走), 播出时刻与集号在卡片下方第一行,
 * 番名第二行 (定高两行, 保证行网格对齐). 已播出的两行文字转次要色, 未播出用主题色 —— 边界不靠
 * 分隔线而靠颜色 (深夜时当天几乎全播完, 压暗海报会让整屏发灰). "现在几点 / 还剩几部" 放在
 * 日期行下方的概况行里.
 *
 * 背景为聚焦条目的全屏横版 backdrop (同详情页规则), 取图走与探索页一致的异步管线
 * (TMDB + 本地缓存 + 防抖); 没聚焦过卡片时用今天第一张有图的顶上, 全都没有才落回纯背景色.
 */
@Composable
fun TvSchedulePage(
    presentation: SchedulePagePresentation,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val tmdb = remember { GlobalKoin.get<TmdbImageService>() }
    val collectionRepo = remember { GlobalKoin.get<SubjectCollectionRepository>() }
    val setCollectionTypeUseCase = remember { GlobalKoin.get<SetSubjectCollectionTypeOrDeleteUseCase>() }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    val days = presentation.days
    val todayIndex = remember(days) {
        days.indexOfFirst { it.kind == ScheduleDay.Kind.TODAY }.coerceAtLeast(0)
    }
    var selectedDayIndex by rememberSaveable { mutableIntStateOf(todayIndex) }
    // 网格与概况行**实际显示**的那一天: 从日期行换天时它比 [selectedDayIndex] 落后一小段 ——
    // 先把当天内容淡出, 等按键静默下来才换、换完再淡入 (见 dayContentAlpha 那个效应).
    // 日期行是"聚焦即切换", 长按方向键一秒能过好几天, 每一天都当场换掉一整屏卡片就是用户报的
    // "日期上左右导航时下面的卡片闪得太快". 从网格跨天那条路不吃这个延迟 (同上处)。
    var displayedDayIndex by remember { mutableIntStateOf(selectedDayIndex) }
    val displayedDay = days.getOrNull(displayedDayIndex.coerceIn(0, days.lastIndex.coerceAtLeast(0)))
    // 换天过渡: 当天内容 (概况行 + 卡片网格) 的整体透明度. 读在 graphicsLayer 的 lambda 里,
    // 淡入淡出期间只失效图层、不重组 (同本页与探索页其它位置驱动效果的纪律).
    val dayContentAlpha = remember { Animatable(1f) }

    // 当天的卡片列表. 上游已把"当前时刻指示器"插在正确位置 (仅今天有), 它之前的即已播出;
    // 指示器不占卡片位 (会打乱网格的行列换算), 只用来划已播/未播的界与取"现在几点".
    // 过去的日子整天都已播出, 未来的日子一部都还没播.
    val dayItems = remember(presentation, displayedDay, displayedDayIndex, todayIndex) {
        val columnItems = displayedDay
            ?.let { day -> presentation.airingSchedules.firstOrNull { it.date == day.date }?.episodes }
            .orEmpty()
        val indicatorPos = columnItems.indexOfFirst { it is AiringScheduleColumnItem.CurrentTimeIndicator }
        val isPast = displayedDay != null && displayedDayIndex < todayIndex
        val cards = columnItems.mapIndexedNotNull { pos, columnItem ->
            when (columnItem) {
                is AiringScheduleColumnItem.Data -> TvScheduleCardData(
                    columnItem.item,
                    aired = isPast || (indicatorPos >= 0 && pos < indicatorPos),
                )

                is AiringScheduleColumnItem.PlaceholderData -> TvScheduleCardData(null, aired = false)
                is AiringScheduleColumnItem.CurrentTimeIndicator -> null
            }
        }
        TvScheduleDayItems(
            cards = cards,
            currentTime = (columnItems.getOrNull(indicatorPos) as? AiringScheduleColumnItem.CurrentTimeIndicator)
                ?.takeIf { !it.isPlaceholder }?.currentTime,
        )
    }

    // 收藏状态 (本地库, 无网络请求): 一个类型一条轻量 id 查询, 合成 subjectId -> 类型.
    // 卡片角标只认在看/想看 (时间表最实际的用法是"我追的番更没更"); 完整类型给长按菜单用.
    val collectionTypes by remember(collectionRepo) {
        flow<Map<Int, UnifiedCollectionType>> {
            val flows = TV_SCHEDULE_COLLECTION_TYPES.map { type ->
                collectionRepo.getSubjectIdsByCollectionType(listOf(type)).map { ids -> type to ids }
            }
            emitAll(
                combine(flows) { pairs ->
                    buildMap {
                        for ((type, ids) in pairs) for (id in ids) put(id, type)
                    }
                },
            )
        }
    }.collectAsStateWithLifecycle(emptyMap())

    // 聚焦卡片 -> 全屏 backdrop (TMDB, 异步); null = 查过没有, 不再重查
    var focusedTarget by remember { mutableStateOf<TvScheduleBackdropTarget?>(null) }
    val backdropCache = remember { mutableStateMapOf<Int, String?>() }
    LaunchedEffect(Unit) {
        snapshotFlow { focusedTarget }.filterNotNull().collectLatest { target ->
            if (target.subjectId in backdropCache) return@collectLatest
            delay(TV_HERO_MEDIA_DEBOUNCE_MILLIS) // 防抖: 快速划过卡片时不发请求
            runCatching {
                // 传该集的播出日期: 新番刚播时 TMDB 往往还没有 backdrop, 负缓存据此限期失效
                tmdb.getBackdropUrl(target.subjectId, target.originalName, activeAsOfDate = target.airDate)
            }.onSuccess { backdropCache[target.subjectId] = it } // 失败不写缓存, 下次聚焦重试
        }
    }

    // 还没聚焦过任何卡片时 (焦点停在日期行) 的默认背景: 从**今天**的卡片里依次试, 第一张
    // 有图的顶上, 全都没有才落回纯背景色. 一旦聚焦过卡片就不再用它 —— 那时该显示的是聚焦那部
    // 自己的图, 它确实没有 TMDB backdrop 就该是黑的, 拿别人的图顶上会误导.
    var defaultBackdrop by remember { mutableStateOf<String?>(null) }
    val todayItems = remember(presentation, days, todayIndex) {
        days.getOrNull(todayIndex)
            ?.let { day -> presentation.airingSchedules.firstOrNull { it.date == day.date } }
            ?.episodes.orEmpty()
            .filterIsInstance<AiringScheduleColumnItem.Data>()
            .map { it.item }
    }
    LaunchedEffect(todayItems) {
        if (defaultBackdrop != null) return@LaunchedEffect
        val airDate = days.getOrNull(todayIndex)?.date?.toString()
        for (item in todayItems) {
            if (focusedTarget != null) return@LaunchedEffect // 用户已经进卡片区, 不必再找默认图
            val url = if (item.subjectId in backdropCache) {
                backdropCache[item.subjectId] // 聚焦过的卡已经查过, 别重复请求
            } else {
                runCatching {
                    tmdb.getBackdropUrl(item.subjectId, item.subjectName.ifBlank { item.subjectTitle }, airDate)
                }.onSuccess { backdropCache[item.subjectId] = it }.getOrNull()
            }
            if (url != null) {
                defaultBackdrop = url
                return@LaunchedEffect
            }
        }
    }

    // ---- 焦点 ----
    val gridFocus = remember { GridFocusController() }
    val gridState = rememberLazyGridState()
    val dateListState = rememberLazyListState()
    val dateFocusRequesters = remember(days.size) { List(days.size.coerceAtLeast(1)) { FocusRequester() } }
    val errorCardFocusRequester = remember { FocusRequester() }
    var anyFocusObtained by remember { mutableStateOf(false) }
    // 当前聚焦的卡片下标 (跨导航保存: 从详情页返回本页时恢复)
    var lastFocusedCard by rememberSaveable { mutableIntStateOf(-1) }
    var gridHasFocus by remember { mutableStateOf(false) }
    // 焦点是否在本页内: 返回键分层的启用条件
    var pageHasFocus by remember { mutableStateOf(false) }
    // 网格顶部当前对齐到的行号 (整行滚动的锚点; 见下方滚动效应)
    var topRow by remember { mutableIntStateOf(0) }
    // 聚焦选中的那枚日期胶囊. 目标可能还在视野外 —— 日期行是 LazyRow, 只组合可见项, 而对未
    // 组合的目标 requestFocus 是**静默 no-op 却照样报成功**: 调用方拿着"成功"空转到超时, 焦点
    // 一步没动 (实测 index=0 而可见区间是 5..14, 却报 ok=true).
    // 因此先查视野: 不在就滚过去并报失败, 由调用方的重试循环下一轮再聚焦.
    val focusSelectedDate: () -> Boolean = {
        val index = selectedDayIndex
        if (dateListState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            // 滚过去再聚焦 (滚动挂起返回时目标已布局出来). 一次性调用方 (网格顶行上键/返回)
            // 没有重试循环, 靠这里补上; 有重试循环的调用方下一轮也会自己成功, 目标相同不冲突
            scope.launch {
                runCatching { dateListState.scrollToItem(dateListState.dateRailAnchorFor(index, days.size)) }
                runCatching { dateFocusRequesters.getOrNull(index)?.requestFocus() }
            }
            false
        } else {
            dateFocusRequesters.getOrNull(index)
                ?.let { requester -> runCatching { requester.requestFocus() }.isSuccess } == true
        }
    }
    // 进页焦点: 曾聚焦过卡片则恢复到那张 (借统一落点解析等数据/滚动/到位确认), 否则聚焦选中日期
    LaunchedEffect(Unit) {
        val restore = lastFocusedCard
        if (restore >= 0) {
            gridFocus.request(restore)
            // 解析失败 (数据没来/出错) 时兜底: 焦点不能悬空, 否则全局兜底会乱塞
            resolveFocusRepeatedly(attempts = 120, arrived = { gridFocus.pending == null }) {}
            if (!anyFocusObtained) focusSelectedDate()
        } else {
            resolveFocusRepeatedly(attempts = 80, delayMillis = 50, arrived = { anyFocusObtained }) {
                focusSelectedDate()
            }
        }
    }
    // 从详情页/播放器返回本页时重新落点: 本页子树可能一直没被销毁 (上面的 LaunchedEffect(Unit)
    // 不会重跑), 但网格项在离开期间被销毁, 焦点随之悬空 —— 表现为返回后看不到焦点圈.
    // 生命周期信号与组合是否存活无关, 用它补发落点.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        var resumedBefore = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!resumedBefore) {
                resumedBefore = true
                return@repeatOnLifecycle
            }
            // 焦点从来没进过本页就不补: 那是抢焦点, 不是恢复
            if (!anyFocusObtained) return@repeatOnLifecycle
            if (lastFocusedCard >= 0) gridFocus.request(lastFocusedCard) else focusSelectedDate()
        }
    }
    // 换天的过渡: 当天内容一路淡出, 淡到底即换成新一天, 紧接着淡入 (一进一出错开, 不是两份
    // 内容叠在一起互相穿透).
    //
    // 淡出时长**长于长按连发的间隔** (见 [TV_SCHEDULE_DAY_FADE_OUT_MILLIS]), 于是长按方向键
    // 期间一次都不换内容: 卡片安静地淡下去, 松手落在哪天就浮现哪天 —— 既没有"一秒闪十几屏",
    // 也顺带省掉了那十几次整屏卡片重建 (每一天都要重算 dayItems、换掉全部可见卡与图).
    //
    // **从网格跨天 (有挂起落点) 时不做过渡, 当场换**: 那条路焦点正被送往新一天的某张卡, 而
    // 落点解析一遇到用户的下一次按键就放弃 (见 GridFocusController.resolve), 拖这么一下就会
    // 把"长按右键顺着时间线走过整周"变成"焦点弹回日期行". 何况那时用户盯的是自己那张卡走到
    // 哪一格, 不是整屏换页.
    LaunchedEffect(selectedDayIndex) {
        if (displayedDayIndex == selectedDayIndex) {
            // 又回到了正在显示的那天 (先右再左这种): 别把内容留在半透明上
            if (dayContentAlpha.value != 1f) {
                dayContentAlpha.animateTo(1f, tween(TV_SCHEDULE_DAY_FADE_IN_MILLIS, easing = LinearEasing))
            }
            return@LaunchedEffect
        }
        if (gridFocus.pending != null) {
            displayedDayIndex = selectedDayIndex
            dayContentAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        // 淡出铺满静默期, 到底即换、紧接着淡入: 全程只有一条连续的 alpha 斜坡, 中间不停顿.
        // **两条斜坡都取 LinearEasing**: 标准的加速/减速曲线会把"看得出在变"的那一小段挤在
        // 斜坡的一头 (300ms 的 accelerate 里, alpha 从 0.6 掉到 0.1 只占 80ms), 观感就是
        // "在那儿好好的, 突然没了" —— 正是这次要修的毛病. 线性斜坡才是均匀的渐隐渐现.
        dayContentAlpha.animateTo(0f, tween(TV_SCHEDULE_DAY_FADE_OUT_MILLIS, easing = LinearEasing))
        displayedDayIndex = selectedDayIndex
        dayContentAlpha.animateTo(1f, tween(TV_SCHEDULE_DAY_FADE_IN_MILLIS, easing = LinearEasing))
    }
    // 换天: 网格回到顶部, 焦点与滚动簿记复位 (从日期行换天时焦点在胶囊上, 不受影响).
    // 已有挂起落点 (从网格跨天进来, 要落到上一天的最后一张) 时不复位滚动: 落点解析自己会滚到
    // 目标那一行, 先弹回顶部再滚下去会闪一下. topRow 留着旧值也无妨 —— 锚点逻辑会夹回来.
    // 键是**显示中**的那天而不是选中的那天: 过渡期间卡片还是旧那一天的, 此刻复位就等于在
    // 淡出途中把内容抽走.
    LaunchedEffect(displayedDayIndex) {
        lastFocusedCard = -1
        if (gridFocus.pending == null) {
            topRow = 0
            runCatching { gridState.scrollToItem(0) }
        }
    }
    // 日期行初始滚动: 把选中的那天摆到居中 (锚点见 [dateRailAnchorFor]).
    // 按 selectedDayIndex 而不是 todayIndex: 从详情页返回时本页可能是重建的, 而选中的天由
    // rememberSaveable 恢复 (可能是跨天走到的某一天); 一律滚到"今天"会把它留在视野外, 之后
    // 落点就再也打不中那枚胶囊 (LazyRow 未组合的目标 requestFocus 静默失败, 见 focusSelectedDate).
    // 等一帧再算: 首帧 layoutInfo 还是空的, 算不出一屏放几枚
    LaunchedEffect(days.size, todayIndex) {
        withFrameNanos { }
        runCatching {
            dateListState.scrollToItem(dateListState.dateRailAnchorFor(selectedDayIndex, days.size))
        }
    }
    // 返回键逐层往回, 一层不跳: 网格非首卡 → 首卡 → 日期行. 焦点已在日期行时**不启用**本处理,
    // 让返回穿到导航层去退出本页 —— 那是最后一层.
    // 换天过渡期焦点停在隐形锚点上 (不在网格内), 也按"在卡片区"算, 回日期行.
    // 单独成小组件: enabled 读的 pending 每次落点请求 设置→清除 变两次, 读在页面 body
    // 会让整页每键重组两遍, 收窄到空壳作用域里 (lambda 捕获的都是稳定引用, 可被记忆)
    ScheduleLayeredBackHandler(
        enabled = { pageHasFocus && (gridHasFocus || gridFocus.pending != null) },
        onBack = {
            if (gridHasFocus && lastFocusedCard > 0) gridFocus.request(0) else focusSelectedDate()
        },
    )

    val navigateToSubject: (AiringScheduleItemPresentation) -> Unit = { item ->
        Analytics.recordEvent(SubjectEnter) {
            put("source", "schedule_card")
            put("subject_id", item.subjectId)
        }
        navigator.navigateSubjectDetails(
            subjectId = item.subjectId,
            placeholder = SubjectDetailPlaceholder(
                id = item.subjectId,
                name = item.subjectTitle,
                coverUrl = item.imageUrl,
            ),
        )
    }
    // 播放键: 直接播这一集 (时间表给的就是某一集, 不用猜"下一集")
    val navigateToPlay: (AiringScheduleItemPresentation) -> Unit = { item ->
        Analytics.recordEvent(SubjectEnter) {
            put("source", "schedule_play")
            put("subject_id", item.subjectId)
        }
        navigator.navigateEpisodeDetails(item.subjectId, item.episodeId)
    }
    // 网格左右导航跨天 (同追番页的跨 tab 行对齐导航): 全天第一张按左 -> 上一天的最后一张;
    // 全天最后一张按右 -> 下一天的第一张. 时间表本来就是一条时间线, 一路按右能顺着播出顺序
    // 走过整周. 落点走统一解析 —— 换天后网格数据整批替换, 目标卡要等新数据组合出来才能聚焦.
    // 返回是否已处理 (首/末日越界时为 false, 由调用处决定消费与否).
    val switchDay: (delta: Int, toLastCard: Boolean) -> Boolean = { delta, toLastCard ->
        val target = selectedDayIndex + delta
        if (target in days.indices) {
            val targetCount = days.getOrNull(target)?.let { day ->
                presentation.airingSchedules.firstOrNull { it.date == day.date }?.episodes
                    ?.count { it is AiringScheduleColumnItem.Data }
            } ?: 0
            gridFocus.request(if (toLastCard) (targetCount - 1).coerceAtLeast(0) else 0)
            // 换天前先把焦点钉到隐形锚点 (同追番页跨 tab, 机制见 GridFocusTransitAnchor):
            // 当天卡片整批替换会销毁正聚焦的那张, 焦点悬空会被系统按遍历顺序重分配, 长按
            // 方向键的连发此刻也不再经过网格键路由. 顺序不能反 —— 锚点只在有挂起落点时可聚焦
            gridFocus.parkFocusOnAnchor()
            selectedDayIndex = target
            // 日期行不含焦点, 不会自己跟着滚: 显式把新选中的那天带回视野 (锚点同进页/左右键,
            // 见 [dateRailAnchorFor]), 否则之后按上键/返回回日期行时那枚胶囊没组合出来, 聚焦会打空
            scope.launch {
                runCatching {
                    dateListState.animateScrollToItem(dateListState.dateRailAnchorFor(target, days.size))
                }
            }
            true
        } else {
            false
        }
    }
    // 长按弹出的收藏下拉 (同探索页/追番页); 打开后短暂吞掉长按残余的确认键, 避免误触第一项.
    // remember 无 key: 工厂被网格 items 内容 lambda 捕获, 每次新实例都会让所有可见卡片重组
    val collectionMenuFor: (Int) -> @Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit = remember {
        { subjectId ->
            { expanded, onDismiss ->
                EditCollectionTypeDropDown(
                    currentType = collectionTypes[subjectId] ?: UnifiedCollectionType.NOT_COLLECTED,
                    expanded = expanded,
                    onDismissRequest = onDismiss,
                    onClick = { action ->
                        scope.launch {
                            runCatching { setCollectionTypeUseCase(subjectId, action.type) }
                                .onFailure { toaster.showLoadError(LoadError.fromException(it)) }
                        }
                    },
                    // 卡片的菜单只有长按一个入口, 恒吞掉那次长按残余的确认键
                    modifier = Modifier.consumeHeldConfirmKey(),
                )
            }
        }
    }

    // ---- 长按窥视 backdrop ----
    // 长按某张卡 (即收藏菜单弹出期间): 其余卡片整体淡到看不见, 被长按那张自己也淡一档 ——
    // 网格铺满全屏, 平时 backdrop 只能从卡片缝里看见一点, 长按顺手把它整张亮出来.
    // 只改绘制不动布局 (graphicsLayer 的 alpha), 焦点/滚动/行高一概不受影响.
    var menuExpandedCard by remember { mutableStateOf(-1) }
    // 两处刻意的性能安排:
    //  - 淡入淡出走 Animatable + snapshotFlow, 不用 animateFloatAsState: 后者要在页面 body 里
    //    读展开态, 一开菜单整页重组;
    //  - 卡片侧在 graphicsLayer 的 lambda 里读 [menuExpandedCard] 与动画值, 读取被推迟到绘制
    //    阶段, 十几张可见卡片一张都不用重组.
    // 动画量是"窥视进度" 0..1 而不是某个具体的 alpha: 两种卡片 (被长按的 / 其余的) 各自
    // 从 1f 插值到自己的目标值, 互不牵扯
    val peekProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { menuExpandedCard >= 0 }.collectLatest { peeking ->
            peekProgress.animateTo(if (peeking) 1f else 0f, tween(TV_SCHEDULE_PEEK_FADE_MILLIS))
        }
    }

    // 播放键: 短按播聚焦那一集, 长按强制重拉时间表 (数据默认一小时才刷一次).
    // 挂在页面根上而不是网格上: 焦点在日期行时也能刷
    val playKeyModifier = tvPlayKeyForceRefresh(
        onRefresh = onRetry,
        onPlay = {
            dayItems.cards.getOrNull(lastFocusedCard)?.item?.let { navigateToPlay(it); true } ?: false
        },
    )

    Box(
        modifier.fillMaxSize()
            .onFocusChanged { pageHasFocus = it.hasFocus }
            .then(playKeyModifier),
    ) {
        // 全屏 backdrop (聚焦条目; 无图时本层不绘制, 整页落在纯背景色上 —— 同详情页的 fallback).
        // 本页是独立目的地, 整屏归自己, 图直接铺到屏幕边缘, 不必再考虑主壳给侧边栏让出的那一条.
        // 地址用 lambda 传入: 状态在组件内部才读取, 遥控器每换一张卡只重组那一层, 不连带整页
        TvFullScreenBackdropLayer(
            backdropUrl = {
                val target = focusedTarget
                // 聚焦过卡片: 只认它自己的图 (没有就黑); 从没聚焦过: 用今天那张默认图
                if (target != null) backdropCache[target.subjectId] else defaultBackdrop
            },
        )

        Column(
            Modifier.fillMaxSize()
                .padding(start = TV_SCHEDULE_START_PAD, top = TV_SCHEDULE_CONTENT_TOP_PAD),
        ) {
            // 日期行的落位一律由它自己的显式滚动决定 (整项对齐 + 居中, 见 dateRailAnchorFor),
            // 关掉默认的"聚焦项滚进视野"; 胶囊宽度按可用宽度反推 (见 tvScheduleDateChipWidth)
            // 日期行一直铺到屏幕右缘 (不留右侧留白): 它是横向滚动的胶囊条, 留白会让行尾那半枚
            // 停在一条看不见的界线上被切掉, 像是被什么挡住了 —— 直接切在屏幕边缘才是"还有更多"
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val chipWidth = tvScheduleDateChipWidth(this@BoxWithConstraints.maxWidth)
                // 落到当天第一张卡 (最早播出的那部). 曾经落"下一部即将播出"那张, 但那样一进网格
                // 就把上午的番吸顶滚出视口, 且过去的日子 (整天都已播出) 会退化成跳到最后一张;
                // 首卡是唯一可预测的落点 —— 播出前后靠文字颜色区分, 待播几部看概况行.
                // 走统一落点解析 (等数据/滚动/到位确认, 直连 requestFocus 在目标未组合时会被静默拒绝)
                //
                // 换天过渡 (见 dayContentAlpha 那个效应) 还没走完时, 网格里仍是**上一天**的卡片:
                // 直接落点会把焦点送到一张马上就要消失的卡上 —— 淡出到点换成新一天时整批 item key
                // 全变, 持焦节点被销毁, 而那次落点请求此时早已消化完 (pending 已清), 没有任何东西
                // 会把焦点补回来 (解析循环只在 pending 非 null 时才重新落点). 所以这条路一旦撞上
                // 过渡期就把过渡**当场收尾**, 与"从网格跨天 (有挂起落点) 不做过渡"是同一个取舍:
                // 焦点要进网格时, 用户盯的是自己落在哪张卡上, 不是那半截淡出.
                val enterGrid: () -> Boolean = {
                    val switchingDay = selectedDayIndex != displayedDayIndex
                    // 过渡期内 dayItems 还是旧那天的, 要按**将要显示**的那天数卡片
                    val cardCount = if (switchingDay) {
                        days.getOrNull(selectedDayIndex)?.let { day ->
                            presentation.airingSchedules.firstOrNull { it.date == day.date }?.episodes
                                ?.count { it is AiringScheduleColumnItem.Data }
                        } ?: 0
                    } else {
                        dayItems.cards.size
                    }
                    if (cardCount > 0) {
                        gridFocus.request(0)
                        if (switchingDay) {
                            displayedDayIndex = selectedDayIndex
                            // snapTo 抢走 Animatable 的 mutex, 顺带取消还在淡出的那条过渡协程;
                            // displayedDayIndex 已由上一行落定, 那条协程剩下的两步都不必再跑
                            scope.launch { dayContentAlpha.snapTo(1f) }
                        }
                        true
                    } else {
                        runCatching { errorCardFocusRequester.requestFocus() }.isSuccess
                    }
                }
                CompositionLocalProvider(LocalBringIntoViewSpec provides TvScheduleNoBringIntoView) {
                    TvScheduleDateRail(
                        days = days,
                        selectedIndex = selectedDayIndex,
                        onSelect = { selectedDayIndex = it },
                        focusRequesters = dateFocusRequesters,
                        listState = dateListState,
                        chipWidth = chipWidth,
                        onAnyFocused = { anyFocusObtained = true },
                        onUserNavigation = gridFocus::onUserNavigation,
                        // 日期行已是最上面一层, 上键消费掉不动 —— 交回默认方向搜索会跳出内容区
                        onNavigateUp = { true },
                        onNavigateDown = enterGrid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 概况行: 今天显示"现在几点 · 待播几部", 其余显示总数; 都跟上"你追的几部".
            // 首屏占位期间不显示 (骨架卡的数量不是真数量, 报出来是假信息); 高度恒定占位, 数据到达不跳
            // 与卡片同步淡入淡出: 它也是"这一天的内容", 单独瞬切会跟下面的卡片脱节
            Box(
                Modifier.padding(top = TV_SCHEDULE_RAIL_TO_SUMMARY_GAP).height(TV_SCHEDULE_SUMMARY_HEIGHT)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = dayContentAlpha.value
                    },
            ) {
                if (!presentation.isPlaceholder && dayItems.cards.isNotEmpty()) {
                    TvScheduleSummaryLine(
                        dayItems = dayItems,
                        followedCount = dayItems.cards.count { card ->
                            card.item?.subjectId?.let { collectionTypes[it] in TV_SCHEDULE_FOLLOWED_TYPES } == true
                        },
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
            }

            val error = presentation.error
            if (error != null) {
                LoadErrorCard(
                    error,
                    onRetry = onRetry,
                    Modifier.padding(top = TV_SCHEDULE_SUMMARY_TO_GRID_GAP, end = TV_SCHEDULE_START_PAD)
                        // 请求器挂容器上, requestFocus 委托给子树第一个焦点目标 (重试按钮);
                        // 按上键显式送回日期行 (跨层级的方向搜索不可靠)
                        .focusRequester(errorCardFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                focusSelectedDate()
                            } else {
                                false
                            }
                        },
                )
            }

            // 换天过渡期的隐形焦点驻留点 (与追番页跨 tab 共用同一实现). 必须放在网格之外 ——
            // 行内左右键是交回默认方向搜索的, 压在首卡位置上的锚点会成为候选 (见其 KDoc)
            GridFocusTransitAnchor(
                gridFocus,
                // 落点解析放弃 (数据迟迟不来) 时焦点还停在锚点上, 锚点随即不可聚焦: 补落点到日期行
                onStranded = { focusSelectedDate() },
            )

            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth()
                    .padding(top = TV_SCHEDULE_SUMMARY_TO_GRID_GAP)
                    // 换天时整块淡出/淡入 (含空态提示). **必须 ModulateAlpha**: 默认 Auto 档遇
                    // alpha < 1 会把整块先画进离屏缓冲再合成, 而这块是整个视口大小 (4K UI 下
                    // 约 8MP/帧); 网格内卡片互不重叠, 逐绘制指令调制的结果一致
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = dayContentAlpha.value
                    }
                    .onFocusChanged { gridHasFocus = it.hasFocus },
            ) {
                // 卡片尺寸由可用高度反推, 使 [TV_SCHEDULE_GRID_VISIBLE_ROWS] 行正好放进视口 ——
                // 固定 112dp 宽时两行放不下, 焦点一进第二行整屏就被顶起来. 卡片窄一点, 每行多放
                // 几张, 一屏能看到的部数反而更多.
                // 右缘与左缘同宽 (都用 [TV_SCHEDULE_START_PAD]), 不用主壳内页面那套 48dp 的
                // TV_PAGE_END_PAD: 本页是独立目的地, 左边没有侧边栏顶着, 沿用 48 就成了左 16 右 48,
                // 最右那张卡明显离边界更远. 两边取齐顺带把省下的 32dp 摊给卡片
                val gridContentWidth = this@BoxWithConstraints.maxWidth - TV_SCHEDULE_START_PAD
                // 本页没有右下角提示, 视口整高都归网格 —— VISIBLE_ROWS 行按整高反推, 铺满为止
                val gridAvailableHeight = this@BoxWithConstraints.maxHeight.coerceAtLeast(1.dp)
                // 卡宽上限: 放得下 VISIBLE_ROWS 行所允许的最大值.
                // 上限用本页自己的 [TV_SCHEDULE_CARD_MAX_WIDTH] 而不是横排卡的 TV_PAGE_CARD_WIDTH:
                // 后者只有 112dp, 而按整高反推出来的值恰好在它上下浮动 —— 被夹掉半个 dp 就会
                // 多算一列, 卡片整体缩水一圈, 屏幕底下白留一条
                val maxCardWidth = run {
                    val rows = TV_SCHEDULE_GRID_VISIBLE_ROWS
                    val wantedRowHeight = (gridAvailableHeight - TV_SCHEDULE_GRID_ROW_SPACING * (rows - 1)) / rows
                    ((wantedRowHeight - TV_SCHEDULE_CARD_LABEL_HEIGHT) * TV_PORTRAIT_CARD_COVER_RATIO)
                        .coerceIn(TV_SCHEDULE_CARD_MIN_WIDTH, TV_SCHEDULE_CARD_MAX_WIDTH)
                }
                // 列数取"卡片铺满整行时仍不超过上限"的最少列数, 横向余量摊进卡片宽度而不是留在行尾 ——
                // 否则行尾会空出小半张卡的宽度, 看着像右边被一条边界挡住了
                val widthFor = { n: Int -> (gridContentWidth - TV_PAGE_CARD_SPACING * (n - 1)) / n }
                val gridColumns = run {
                    var n = ((gridContentWidth + TV_PAGE_CARD_SPACING) / (maxCardWidth + TV_PAGE_CARD_SPACING))
                        .toInt().coerceAtLeast(1)
                    // 容差: 差半个 dp 不值得多要一列 (多一列会让卡片明显变小)
                    if (widthFor(n) > maxCardWidth + 0.5.dp) n += 1
                    n
                }
                val cardWidth = widthFor(gridColumns)
                // 实际行高与真正放得下的行数 (卡宽被上下限夹住时会与 VISIBLE_ROWS 不同)
                val rowHeight = cardWidth / TV_PORTRAIT_CARD_COVER_RATIO + TV_SCHEDULE_CARD_LABEL_HEIGHT
                // 真正放得下的行数. 必须带容差: 卡宽正是按 VISIBLE_ROWS 反推的, 行高本就贴着
                // "整高 / 行数"这条线, 而列数取整时留了 0.5dp 容差 (见 gridColumns), 实际卡宽可能
                // 比上限大半个 dp, 行高随之大 0.7dp —— 不留容差就成了 floor(1.996) = 1, 两行被当成
                // 一行, 表现为"只有两行时第二行也被吸顶".
                // 容差不会让它多算一行: 多一行要求行高再小三成, 差得远
                val visibleRows = (
                        (gridAvailableHeight + TV_SCHEDULE_GRID_ROW_SPACING + TV_SCHEDULE_GRID_ROW_TOLERANCE) /
                                (rowHeight + TV_SCHEDULE_GRID_ROW_SPACING)
                        ).toInt().coerceAtLeast(1)
                // 底部补白 = 视口高 - 恰好 visibleRows 行占的高度. 这段零头 (行高只能取"列数
                // 整数化之后"的那一档, 与视口高除以行数总差着几个 dp) 就留在末行下面当页面底部
                // 留白 —— 曾经把它摊进行间距, 结果两行之间空出一大条而屏幕底下贴死, 上下不对称.
                // 它同时是滚动所必需的: 少了这段, 内容总高不足以让最后几行滚到整行边界上,
                // animateScrollToItem 会停在能滚到的极限处, 最后一屏又变成"某行露出一点点"
                val gridBottomPad = (
                        this@BoxWithConstraints.maxHeight -
                                (rowHeight * visibleRows + TV_SCHEDULE_GRID_ROW_SPACING * (visibleRows - 1))
                        ).coerceAtLeast(0.dp)
                val cards = dayItems.cards
                // 占位期间对落点解析报"仍在加载": 骨架卡不可聚焦, 解析要等真实数据到达再落点.
                // gridColumns 不作 key: 解析每轮经 lambda 现读, 不需要靠重启跟进列数变化
                LaunchedEffect(cards) {
                    gridFocus.runResolveLoop(
                        gridState = gridState,
                        columns = { gridColumns },
                        itemCount = { if (presentation.isPlaceholder) 0 else cards.size },
                        isLoadingFirstPage = { presentation.isPlaceholder },
                        onEmptyIdle = { focusSelectedDate() },
                    )
                }
                // 整行滚动: 时机同 bringIntoView (目标在视口内就一动不动), 粒度是整行 ——
                //  - 焦点在可见的那几行之间移动: 完全不滚, 整屏静止 (第一行仍在顶上);
                //  - 焦点落到视口下方那一行: 整体上移到"目标行恰好贴视口底"; 向上同理贴顶.
                // 不用默认的 bringIntoView (已在网格上关掉): 它按"刚好露出目标"的最小量滚,
                // 会把上一行切成露出一点点的样子; 这里每次都落在整行边界上.
                // [topRow] 是网格顶部当前对齐到的行号, 每次都显式滚到它 —— 落点解析为了让目标卡
                // 组合出来会自己 scrollToItem, 这一步顺带把那种临时滚动纠回整行.
                LaunchedEffect(gridState, gridColumns, visibleRows, cards.size) {
                    // collectLatest + TvScrollAnimator: 连发按键取消进行中的滚动并继承速度
                    val scrollAnimator = TvScrollAnimator()
                    snapshotFlow { lastFocusedCard }.collectLatest { focused ->
                        if (focused < 0) return@collectLatest
                        val row = focused / gridColumns
                        val totalRows = (cards.size + gridColumns - 1) / gridColumns
                        val maxTop = (totalRows - visibleRows).coerceAtLeast(0)
                        var target = topRow.coerceIn(0, maxTop)
                        if (row < target) target = row // 目标在视口上方: 它贴顶
                        if (row > target + visibleRows - 1) {
                            target = row - visibleRows + 1 // 目标在视口下方: 它贴底
                        }
                        target = target.coerceIn(0, maxTop)
                        topRow = target
                        runCatching { scrollAnimator.animateScrollToItem(gridState, target * gridColumns) }
                    }
                }
                CompositionLocalProvider(LocalBringIntoViewSpec provides TvScheduleNoBringIntoView) {
                    LazyVerticalGrid(
                        // Fixed(自算列数) 而非 Adaptive/FixedSize: 列数由上面按高度算好,
                        // Fixed 会把整行宽度均分给这些列 —— 单元格宽度必然等于 cardWidth,
                        // 行尾不留余量, 且导航用的行列换算与实际布局不可能对不上
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier.fillMaxSize().clipToBounds()
                            .gridKeyNavigation(
                                gridFocus,
                                focusedIndex = { lastFocusedCard },
                                itemCount = { cards.size },
                                columns = { gridColumns },
                                // 顶行上键回日期行. 无条件消费: 万一选中的胶囊此刻没组合出来
                                // (聚焦失败), 焦点留在原卡也比交回默认方向搜索乱跳强
                                onTopRowUp = {
                                    focusSelectedDate()
                                    true
                                },
                                // 播放键由页面根节点接管 (短按播放 / 长按强制刷新, 见
                                // tvPlayKeyForceRefresh): 长按要靠 KeyUp 才能与短按区分,
                                // 而本路由只处理 KeyDown
                                onPlayKey = { false },
                                // 左右键按时间线性移动: 行末接下一行行首, 行首接上一行行末;
                                // 走到全天两端再跨天 (同追番页跨 tab). 时间是一条线, 不是二维表 ——
                                // 一路按右就能顺着播出顺序走过整周
                                extraKeys = { event, focused, cols, count ->
                                    when (event.key) {
                                        Key.DirectionRight -> when {
                                            // 行内还有卡: 交给默认方向搜索横向移动
                                            focused % cols != cols - 1 && focused < count - 1 -> false
                                            // 行末: 接下一行行首
                                            focused < count - 1 -> {
                                                gridFocus.request(focused + 1)
                                                true
                                            }
                                            // 全天最后一张: 跳到下一天的第一张. 已是最后一天也要
                                            // 消费掉 —— 不消费会落到默认方向搜索, 右侧无目标时它
                                            // 按遍历顺序取首个, 表现为绕回首卡
                                            else -> {
                                                switchDay(1, false)
                                                true
                                            }
                                        }

                                        Key.DirectionLeft -> when {
                                            focused % cols != 0 -> false
                                            // 行首: 接上一行行末
                                            focused > 0 -> {
                                                gridFocus.request(focused - 1)
                                                true
                                            }
                                            // 全天第一张: 跳到上一天的最后一张. 已是第一天 (整条
                                            // 时间线的起点) 也要消费掉 —— 卡片区不做出口,
                                            // 交回默认空间搜索的落点不可预测
                                            else -> {
                                                switchDay(-1, true)
                                                true
                                            }
                                        }

                                        else -> false
                                    }
                                },
                            ),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(TV_PAGE_CARD_SPACING),
                        verticalArrangement = Arrangement.spacedBy(TV_SCHEDULE_GRID_ROW_SPACING),
                        // 底部补白让内容够长, 每一行都能真的吸到视口顶 (见 gridBottomPad);
                        // 滚到最后一屏时它就是末尾那 visibleRows 行下面的空白
                        contentPadding = PaddingValues(end = TV_SCHEDULE_START_PAD, bottom = gridBottomPad),
                    ) {
                        items(
                            count = cards.size,
                            key = { index ->
                                cards[index].item?.let { "TvSchedule-${it.subjectId}-${it.episodeId}" }
                                    ?: "TvSchedule-placeholder-$index"
                            },
                        ) { index ->
                            val card = cards[index]
                            val item = card.item
                            // derivedStateOf: 落点下标每次导航都会 设置->清除 变两次, 直接读会让
                            // 所有可见卡片重组两遍; 收窄成布尔后只有目标卡自己重组
                            val isGridTarget by remember(index) {
                                derivedStateOf { gridFocus.resolvedIndex == index }
                            }
                            TvScheduleCard(
                                card = card,
                                followed = item?.subjectId
                                    ?.let { collectionTypes[it] in TV_SCHEDULE_FOLLOWED_TYPES } == true,
                                onClick = { item?.let(navigateToSubject) },
                                onFocused = {
                                    lastFocusedCard = index
                                    anyFocusObtained = true
                                    gridFocus.onCardFocused(index)
                                    if (item != null && displayedDay != null) {
                                        focusedTarget = TvScheduleBackdropTarget(
                                            subjectId = item.subjectId,
                                            originalName = item.subjectName.ifBlank { item.subjectTitle },
                                            airDate = displayedDay.date.toString(),
                                        )
                                    }
                                },
                                menu = item?.let { collectionMenuFor(it.subjectId) },
                                onMenuExpandedChange = { expanded ->
                                    // 关闭只认自己那次: 换天/滚动让卡片重建时也会报 false,
                                    // 不加这层判断会把别人正开着的窥视态清掉
                                    if (expanded) menuExpandedCard = index
                                    else if (menuExpandedCard == index) menuExpandedCard = -1
                                },
                                modifier = Modifier.ifThen(isGridTarget) { focusRequester(gridFocus.requester) }
                                    // 窥视态: 被长按那张淡一档 (仍看得清, 但透出点 backdrop),
                                    // 其余淡到看不见. 无人长按时进度为 0, 两者都是 1f
                                    .graphicsLayer {
                                        val target = if (menuExpandedCard == index) {
                                            TV_SCHEDULE_PEEK_SELF_ALPHA
                                        } else {
                                            TV_SCHEDULE_PEEK_OTHERS_ALPHA
                                        }
                                        alpha = 1f + (target - 1f) * peekProgress.value
                                    },
                            )
                        }
                    }
                }
                // 空态: 这一天确实没有新番 (占位/出错各有自己的表现)
                if (cards.isEmpty() && !presentation.isPlaceholder && error == null) {
                    Box(
                        Modifier.fillMaxSize().offset(y = -TV_SCHEDULE_EMPTY_HINT_RAISE),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(Lang.exploration_tv_schedule_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        // 本页不放右下角遥控提示, 也就不需要托着它的底缘遮罩: 时间表是从探索页进来的二级页,
        // 到这一步"卡片按播放键直接播"已经在探索页公示过了, 每页再重复一遍纯属噪音.
        // 省下的这条高度归网格 —— 两行卡片正好铺满整屏, 底下不再露出被截断的第三行
    }
}

/**
 * 返回键分层处理的收窄壳: [enabled] 里的热状态读 (焦点态 / [GridFocusController.pending])
 * 发生在本空壳作用域, 不殃及页面 body. 零 UI.
 */
@Composable
private fun ScheduleLayeredBackHandler(
    enabled: () -> Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled()) { onBack() }
}

/**
 * 日期胶囊行: 15 天 (上周同日 ~ 下周同日), **聚焦即切换**. 常驻不随网格滚动 —— 几月几号和周几
 * 永远留在屏上.
 *
 * 左右键显式在胶囊间移动 (默认方向搜索会跳出本行), 首尾两端都消费掉 —— 本行不做出口.
 * 上键 ([onNavigateUp]) 同理: 本行已是最上面一层, 消费掉不动.
 */
@Composable
private fun TvScheduleDateRail(
    days: List<ScheduleDay>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    focusRequesters: List<FocusRequester>,
    listState: LazyListState,
    /** 每枚胶囊的宽度 (由调用处按可用宽度反推, 见 [tvScheduleDateChipWidth]). */
    chipWidth: Dp,
    onAnyFocused: () -> Unit,
    onUserNavigation: () -> Unit,
    onNavigateUp: () -> Boolean,
    onNavigateDown: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    var focusedDateIndex by remember { mutableIntStateOf(-1) }
    // "聚焦即选中"只认由本行左右键引发的焦点移动: 页面切换/焦点悬空时系统会把默认焦点塞给第一个
    // 胶囊, 那种情况不能改选中的天 (否则从卡片进详情页再返回会被拽到 15 天前那天)
    var selectByFocusArmed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 聚焦第 [target] 枚胶囊 (左右键用) 并把它滚到居中 (锚点见 [dateRailAnchorFor]).
    // 已可见: 先聚焦 (高亮立即跟手), 再动画滚过去; 不可见时反过来 —— 未组合的目标
    // requestFocus 是静默 no-op 却报成功, 按键会被消费而焦点不动, 所以先瞬时滚过去让它组合.
    val focusDate: (Int) -> Unit = { target ->
        selectByFocusArmed = true
        val anchor = listState.dateRailAnchorFor(target, days.size)
        if (listState.layoutInfo.visibleItemsInfo.any { it.index == target }) {
            runCatching { focusRequesters[target].requestFocus() }
            scope.launch { runCatching { listState.animateScrollToItem(anchor) } }
        } else {
            scope.launch {
                runCatching { listState.scrollToItem(anchor) }
                runCatching { focusRequesters[target].requestFocus() }
            }
        }
    }
    LazyRow(
        modifier
            // 长按方向键的移动频率上限 (同探索页/选集轮播/详情页选集条). 本行尤其需要: 它是
            // "聚焦即切换", 系统连发 ~20 次/秒的话一秒要翻十几天, 而每翻一天下面就是一整屏
            // 新卡片. 挂在限流之后的 onPreviewKeyEvent 只会收到放行的那几发
            .tvFocusMoveRateLimit()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                onUserNavigation()
                when (event.key) {
                    // 本行已是最上面一层, 上键消费掉不动: 交回默认方向搜索会跳出内容区
                    Key.DirectionUp -> {
                        onNavigateUp()
                        true
                    }

                    Key.DirectionDown -> onNavigateDown()

                    // 首项按左也消费掉: 日期行不做出口, 交回默认空间搜索的落点不可预测
                    Key.DirectionLeft -> {
                        if (focusedDateIndex > 0) focusDate(focusedDateIndex - 1)
                        true
                    }

                    Key.DirectionRight -> when (focusedDateIndex) {
                        in 0..<days.size - 1 -> {
                            focusDate(focusedDateIndex + 1)
                            true
                        }

                        days.size - 1 -> true // 末项按右: 必须消费, 否则方向搜索会绕回首项
                        else -> false
                    }

                    else -> false
                }
            },
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(TV_SCHEDULE_DATE_SPACING),
    ) {
        itemsIndexed(days, key = { _, day -> day.date.toString() }) { index, day ->
            TvScheduleDateChip(
                day = day,
                selected = index == selectedIndex,
                width = chipWidth,
                modifier = Modifier
                    .focusRequester(focusRequesters[index])
                    .onFocusChanged {
                        if (it.isFocused) {
                            focusedDateIndex = index
                            onAnyFocused()
                            if (selectByFocusArmed) {
                                selectByFocusArmed = false
                                onSelect(index)
                            }
                        }
                    },
                onClick = { onSelect(index) },
            )
        }
    }
}

/**
 * 一枚日期胶囊的宽度: 由可用宽度 [available] 反推 —— 行尾露出的那枚要么别露, 要么露**约半枚**
 * ([TV_SCHEDULE_DATE_PEEK_FRACTION]); 只露几个 dp 的窄缝最难看 (像没对齐), 而露半枚本身就是
 * "右边还有"的提示.
 *
 * 做法: 取"能让胶囊不窄于 [TV_SCHEDULE_DATE_MIN_WIDTH] 的最多枚数 n", 再把可用宽度按
 * "n 整枚 + 半枚" 摊开.
 */
private fun tvScheduleDateChipWidth(available: Dp): Dp {
    val peek = TV_SCHEDULE_DATE_PEEK_FRACTION
    val n = ((available - TV_SCHEDULE_DATE_MIN_WIDTH * peek) /
            (TV_SCHEDULE_DATE_MIN_WIDTH + TV_SCHEDULE_DATE_SPACING)).toInt().coerceAtLeast(1)
    return (available - TV_SCHEDULE_DATE_SPACING * n) / (n + peek)
}

/**
 * 日期行的滚动锚点: 返回"应当滚到的首个下标", 使第 [index] 枚胶囊尽量居中, 两端夹住 ——
 * 已经能看到头/尾时不再往外留空.
 *
 * 一律用 scrollToItem(首下标) 落位: 它把该项左缘对齐视口左缘, 于是**最左那枚胶囊永远完整露出**.
 * 默认的 bringIntoView 恰恰相反 —— 它按"最小滚动量"滚, 把聚焦项推到视口边缘, 最左侧留下半张
 * 被切掉的胶囊, 每次落位还取决于上一次滚到哪, 观感很随机; 所以日期行把它关掉
 * ([TvScheduleNoBringIntoView]), 全部走本锚点.
 */
private fun LazyListState.dateRailAnchorFor(index: Int, count: Int): Int {
    val info = layoutInfo
    val items = info.visibleItemsInfo
    // 一格 = 胶囊宽 + 间距 (取相邻两项的偏移差; 只有一项可见时退化成该项宽度)
    val step = if (items.size >= 2) items[1].offset - items[0].offset else items.firstOrNull()?.size ?: 0
    // 还没测量出来 (首帧): 无从得知一屏放几枚, 目标即首项
    if (step <= 0) return index.coerceAtLeast(0)
    // n 枚占 n*step - 间距 (末枚后面没有间距), 所以算枚数时要把这一段补回来 ——
    // 否则胶囊正好铺满一行时会少算一枚
    val spacing = (step - (items.firstOrNull()?.size ?: step)).coerceAtLeast(0)
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    val perScreen = ((viewport + spacing) / step).coerceAtLeast(1)
    return (index - (perScreen - 1) / 2).coerceIn(0, (count - perScreen).coerceAtLeast(0))
}

/**
 * 关闭"聚焦项自动滚进视野": 时间表的日期行与网格都自己决定落位 (日期行居中对齐整项,
 * 网格整行吸顶), 默认那套最小滚动量会与之打架.
 */
private val TvScheduleNoBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/** 一枚日期胶囊: 上行 "7/29" (今天为主题色), 下行 "上周三" / "今天" / "下周三". */
@Composable
private fun TvScheduleDateChip(
    day: ScheduleDay,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val isToday = day.kind == ScheduleDay.Kind.TODAY
    val container = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = TV_SCHEDULE_DATE_SELECTED_ALPHA)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TV_SCHEDULE_DATE_IDLE_ALPHA)
    }
    val primaryText = when {
        focused -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> tvHeroContentColor()
    }
    val secondaryText = if (focused) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
    } else {
        tvHeroSecondaryContentColor()
    }
    // 选中 (未聚焦) 的描边: 元素恒在, 只换颜色 —— 绝不能写成 ifThen 那样的条件 modifier.
    // 条件元素位于 clickable (内含焦点目标) 之前, 选中/聚焦态一变就会重建其后的焦点节点;
    // 而 requestFocus 到这枚胶囊恰好会让 focused 翻转 -> 焦点节点在焦点刚落下时被重建 ->
    // 焦点当场丢掉被系统重分配 (表现为按上键回不到日期行、进页焦点乱跑)
    val borderColor = if (selected && !focused) {
        MaterialTheme.colorScheme.primary.copy(alpha = TV_SCHEDULE_DATE_SELECTED_BORDER_ALPHA)
    } else {
        Color.Transparent
    }
    Column(
        modifier
            .width(width)
            .height(TV_SCHEDULE_DATE_CHIP_HEIGHT)
            .background(container, RoundedCornerShape(TV_SCHEDULE_DATE_CORNER))
            .border(TV_SCHEDULE_DATE_SELECTED_BORDER, borderColor, RoundedCornerShape(TV_SCHEDULE_DATE_CORNER))
            .clickable(interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        // 定高 + 两行居中: 高度是布局的输入量 (网格靠它反推卡片尺寸), 不能由文字行高说了算
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${day.date.month.number}/${day.date.day}",
            color = primaryText,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TV_SCHEDULE_TABULAR_NUMS),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            renderTvScheduleWeekday(day),
            color = secondaryText,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** 概况行: 今天 "现在 10:12 · 待播 6 部 · 你追的 3 部"; 其余 "共 12 部 · 你追的 3 部". */
@Composable
private fun TvScheduleSummaryLine(
    dayItems: TvScheduleDayItems,
    followedCount: Int,
    modifier: Modifier = Modifier,
) {
    val total = dayItems.cards.size
    val currentTime = dayItems.currentTime
    val parts = buildList {
        if (currentTime != null) {
            add(stringResource(Lang.exploration_tv_schedule_now, ScheduleItemDefaults.renderTime(null, currentTime)))
            add(stringResource(Lang.exploration_tv_schedule_upcoming, total - dayItems.firstUpcomingIndex))
        } else {
            add(stringResource(Lang.exploration_tv_schedule_total, total))
        }
        if (followedCount > 0) {
            add(stringResource(Lang.exploration_tv_schedule_following, followedCount))
        }
    }
    Text(
        parts.joinToString(" · "),
        modifier,
        color = tvHeroSecondaryContentColor(),
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TV_SCHEDULE_TABULAR_NUMS),
        maxLines = 1,
    )
}

/**
 * 一张时间表卡片: 竖版海报 (承载焦点/点击/长按) + 下方两行 —— "10:26 · 第 16 话" 与番名.
 *
 * 已播出 ([TvScheduleCardData.aired]) 时两行文字转次要色 (海报不压暗: 深夜时当天几乎全播完,
 * 压暗会让整屏发灰); 番名定高两行, 保证网格行高一致、切焦点不跳.
 */
@Composable
private fun TvScheduleCard(
    card: TvScheduleCardData,
    followed: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    menu: (@Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit)? = null,
    onMenuExpandedChange: ((Boolean) -> Unit)? = null,
) {
    val item = card.item
    Column(modifier) {
        Box {
            TvPortraitCard(
                imageUrl = item?.imageUrl,
                contentDescription = item?.subjectTitle,
                onClick = onClick,
                onFocused = onFocused,
                modifier = Modifier.fillMaxWidth(),
                menu = menu,
                onMenuExpandedChange = onMenuExpandedChange,
            )
            // 在追角标 (在看/想看): 时间表最实际的用法是"我追的番更没更", 一眼从几十部里挑出来.
            // 纯装饰, 不参与焦点; 内缩量对齐卡片自身的聚焦留白
            if (followed) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .padding(TV_SCHEDULE_BADGE_INSET)
                        .size(TV_SCHEDULE_BADGE_SIZE)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = TV_SCHEDULE_BADGE_BACKGROUND_ALPHA),
                            RoundedCornerShape(50),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        Modifier.size(TV_SCHEDULE_BADGE_ICON_SIZE),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (item != null) {
            // 整块定高 (而非由文字撑开): 网格行高 = 海报高 + 本块高, 是"两行正好放进视口"那套
            // 尺寸反推的输入, 必须精确
            Column(
                Modifier.height(TV_SCHEDULE_CARD_LABEL_HEIGHT)
                    .padding(top = TV_SCHEDULE_CARD_LABEL_TOP_GAP),
            ) {
                Text(
                    ScheduleItemDefaults.renderTime(null, item.time) + " · " + rememberEpisodeLabel(item),
                    color = if (card.aired) {
                        tvHeroSecondaryContentColor()
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.labelSmall
                        .copy(fontFeatureSettings = TV_SCHEDULE_TABULAR_NUMS),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.subjectTitle,
                    color = if (card.aired) tvHeroSecondaryContentColor() else tvHeroContentColor(),
                    style = MaterialTheme.typography.labelSmall,
                    // 定高两行: 长名放得下, 短名也占同样高度 -> 网格行高一致
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "第 16 话" / "第 16 (28) 话" (语义同 [ScheduleItemDefaults.Episode], 但只要集号不要集名). */
@Composable
private fun rememberEpisodeLabel(item: AiringScheduleItemPresentation): String {
    val sortText = item.episodeSort.toString().removePrefix("0")
    val epText = item.episodeEp?.toString()?.removePrefix("0")
    return if (item.episodeEp == null || item.episodeEp == item.episodeSort) {
        stringResource(Lang.exploration_schedule_episode, sortText)
    } else {
        stringResource(Lang.exploration_schedule_episode_ep_and_sort, epText!!, sortText)
    }
}

/** "上周三" / "今天" / "周三" / "下周三". */
@Composable
private fun renderTvScheduleWeekday(day: ScheduleDay): String {
    if (day.kind == ScheduleDay.Kind.TODAY) return stringResource(Lang.exploration_tv_schedule_today)
    val weekday = when (day.dayOfWeek) {
        kotlinx.datetime.DayOfWeek.MONDAY -> stringResource(Lang.exploration_schedule_weekday_monday)
        kotlinx.datetime.DayOfWeek.TUESDAY -> stringResource(Lang.exploration_schedule_weekday_tuesday)
        kotlinx.datetime.DayOfWeek.WEDNESDAY -> stringResource(Lang.exploration_schedule_weekday_wednesday)
        kotlinx.datetime.DayOfWeek.THURSDAY -> stringResource(Lang.exploration_schedule_weekday_thursday)
        kotlinx.datetime.DayOfWeek.FRIDAY -> stringResource(Lang.exploration_schedule_weekday_friday)
        kotlinx.datetime.DayOfWeek.SATURDAY -> stringResource(Lang.exploration_schedule_weekday_saturday)
        else -> stringResource(Lang.exploration_schedule_weekday_sunday)
    }
    return when (day.kind) {
        ScheduleDay.Kind.LAST_WEEK -> stringResource(Lang.exploration_schedule_last_weekday, weekday)
        ScheduleDay.Kind.NEXT_WEEK -> stringResource(Lang.exploration_schedule_next_weekday, weekday)
        else -> stringResource(Lang.exploration_schedule_this_weekday, weekday)
    }
}

/** 网格一格的数据; [item] 为 null 表示占位骨架 (首屏加载中). [aired] = 该集已播出. */
private data class TvScheduleCardData(
    val item: AiringScheduleItemPresentation?,
    val aired: Boolean,
)

/** 选中那天的卡片列表; [currentTime] 非 null 表示这天是今天 (上游插了当前时刻指示器). */
private data class TvScheduleDayItems(
    val cards: List<TvScheduleCardData>,
    val currentTime: LocalTime?,
) {
    /** 第一部尚未播出的下标; 全部播完时等于 [cards] 大小 (即"待播 0 部"). 只用于概况行计数. */
    val firstUpcomingIndex: Int
        get() = cards.indexOfFirst { !it.aired }.let { if (it < 0) cards.size else it }
}

/** 聚焦卡片 -> backdrop 取图请求 ([airDate] 供 TMDB 负缓存限期失效). */
private data class TvScheduleBackdropTarget(
    val subjectId: Int,
    val originalName: String,
    val airDate: String,
)


/** 需要查询本地收藏类型的全部类型 (供长按菜单显示当前状态). */
private val TV_SCHEDULE_COLLECTION_TYPES = listOf(
    UnifiedCollectionType.WISH,
    UnifiedCollectionType.DOING,
    UnifiedCollectionType.ON_HOLD,
    UnifiedCollectionType.DONE,
    UnifiedCollectionType.DROPPED,
)

/** 卡片角标与"你追的 N 部"计入的收藏类型. */
private val TV_SCHEDULE_FOLLOWED_TYPES = setOf(UnifiedCollectionType.DOING, UnifiedCollectionType.WISH)

/** 等宽数字: 时刻与集号是数字列, 卡片之间要竖着对齐. */
private const val TV_SCHEDULE_TABULAR_NUMS = "tnum"

/**
 * 内容左右留白. 本页是独立目的地 (没有侧边栏占掉左边那一条), 两侧同宽, 内容居中于整屏;
 * 日期行是例外 —— 它一直铺到右缘, 让行尾那半枚胶囊切在屏幕边上而不是切在一条看不见的界线上.
 */
private val TV_SCHEDULE_START_PAD = 16.dp

/**
 * 内容顶部留白. 与页面底部留白配平 —— 底部那条是网格的"零头"([gridBottomPad], 由卡片尺寸
 * 反推后剩下多少算多少), 本值就按它的量级取, 免得一头顶死一头空一条.
 * 三者 (本值 / 日期胶囊高 / 底部零头) 共分一块固定的高度: 调大任意一个, 另外的就变小.
 */
private val TV_SCHEDULE_CONTENT_TOP_PAD = 16.dp

/** 日期行到概况行的间距. */
private val TV_SCHEDULE_RAIL_TO_SUMMARY_GAP = 8.dp

/** 概况行的固定高度: 占位期间不显示文字但保留高度, 数据到达时网格不跳. */
private val TV_SCHEDULE_SUMMARY_HEIGHT = 18.dp

/** 概况行到网格的间距. */
private val TV_SCHEDULE_SUMMARY_TO_GRID_GAP = 8.dp

/**
 * 日期胶囊的**最小**宽度 (容得下 "上周三" 三字 + 左右内边距 24dp).
 * 实际宽度由可用宽度反推, 见 [tvScheduleDateChipWidth].
 */
private val TV_SCHEDULE_DATE_MIN_WIDTH = 72.dp

/** 行尾那枚胶囊露出的比例 (0.5 = 半枚): 既提示"右边还有", 又不是难看的一条窄缝. */
private const val TV_SCHEDULE_DATE_PEEK_FRACTION = 0.5f

/**
 * 一枚日期胶囊的**定高**. 文字自己撑出来是 46dp (两行行高 + 上下 5dp), 这里比它高一档:
 * 胶囊是页面上唯一一块"可以变高来占位"的元素, 顶部留白与底部零头都很薄, 高度给它比让它
 * 悬在一片空当中间好看.
 * 定高还有个硬性理由: 网格的卡片尺寸是从"视口高减去上面这些元素"反推的, 高度必须是输入量,
 * 不能由文字行高说了算.
 */
private val TV_SCHEDULE_DATE_CHIP_HEIGHT = 56.dp

/** 日期胶囊之间的间距. */
private val TV_SCHEDULE_DATE_SPACING = 10.dp

/** 日期胶囊圆角. */
private val TV_SCHEDULE_DATE_CORNER = 8.dp

/** 未选中日期胶囊的底色不透明度. */
private const val TV_SCHEDULE_DATE_IDLE_ALPHA = 0.45f

/** 选中 (但未聚焦) 日期胶囊的底色不透明度 (主题主色). */
private const val TV_SCHEDULE_DATE_SELECTED_ALPHA = 0.2f

/** 选中 (但未聚焦) 日期胶囊的描边. */
private val TV_SCHEDULE_DATE_SELECTED_BORDER = 1.5.dp

/** 选中 (但未聚焦) 日期胶囊描边的不透明度. */
private const val TV_SCHEDULE_DATE_SELECTED_BORDER_ALPHA = 0.55f

/** 卡片海报到下方文字的间距. */
private val TV_SCHEDULE_CARD_LABEL_TOP_GAP = 6.dp

/**
 * 卡片下方文字块的高度 (= 上间距 + 时刻行 + 定高两行番名, labelSmall 行高 16dp).
 * 该块**定高**: 网格行高 = 海报高 + 本值, 是"两行正好放进视口"那套尺寸反推的输入.
 */
private val TV_SCHEDULE_CARD_LABEL_HEIGHT = 54.dp

/** 网格行间距. */
private val TV_SCHEDULE_GRID_ROW_SPACING = 12.dp

/**
 * 视口内要放下的完整行数. 卡片宽度按这个值从可用高度反推 —— 调成 3 则卡片更小、每行更多.
 * 焦点在这几行之间移动时网格一动不动, 跑出去才整体 snap 一行.
 */
private const val TV_SCHEDULE_GRID_VISIBLE_ROWS = 2

/**
 * 判定"放得下几行"时的高度容差: 吸收列数取整带来的那零点几 dp 行高溢出 (见用处的注释).
 * 取值只要远小于一行的高度即可.
 */
private val TV_SCHEDULE_GRID_ROW_TOLERANCE = 4.dp

/**
 * 卡片宽度下限. 视口很矮时按高度反推会算出过小的卡片, 夹住它 —— 此时两行放不下,
 * 退化成"能放几行放几行" (visibleRows 按实际行高重算).
 */
private val TV_SCHEDULE_CARD_MIN_WIDTH = 64.dp

/**
 * 卡片宽度上限. 只是个兜底 (视口极高极窄时不让卡片胀到离谱), 正常 16:9 屏上够不着 ——
 * 真正决定卡宽的是"两行铺满整屏"那套反推.
 */
private val TV_SCHEDULE_CARD_MAX_WIDTH = 160.dp

// ---- 换天过渡 ----

/**
 * 换天时当天内容 (概况行 + 卡片网格) 的淡出时长, **同时就是换内容前要等的按键静默期** ——
 * 淡出正好铺满整个静默期, 中间不留"已经全透明但还没换"的空档.
 *
 * 两者合一是被观感逼出来的: 曾经是"淡出 110ms + 干等 190ms", 用户看到的是"直接消失、空屏
 * 一下、又出现" —— 短促的 alpha 斜坡根本读不出是渐变, 后面那段全透明就成了纯粹的空屏.
 *
 * 值必须长于长按方向键的连发间隔, 否则长按期间仍会在两发之间换掉一整屏卡片 (那正是要消除的
 * "闪"): 由限流上限推出, 免得两个常数各自漂移 (日期行的限流见 [tvFocusMoveRateLimit]),
 * 多留的那几十毫秒是"松手那一发"的余量.
 */
private const val TV_SCHEDULE_DAY_FADE_OUT_MILLIS =
    1000 / TV_FOCUS_MOVE_MAX_PER_SECOND_HORIZONTAL + 70

/**
 * 新一天内容的淡入时长. **用户手调过, 改前先问**.
 *
 * 比淡出短一档是刻意的: 淡出那条斜坡的长度被静默期定死 (见上), 而浮现只要读得出是"浮上来"
 * 就够 —— 拖长了反倒像在等加载.
 */
private const val TV_SCHEDULE_DAY_FADE_IN_MILLIS = 200

// ---- 长按窥视 backdrop ----

/** 窥视态下**其余**卡片的不透明度 (0 = 完全看不见, 只是不占位不动布局). */
private const val TV_SCHEDULE_PEEK_OTHERS_ALPHA = 0f

/** 窥视态下**被长按那张**的不透明度: 仍然认得出是哪张, 但透出一点后面的 backdrop. */
private const val TV_SCHEDULE_PEEK_SELF_ALPHA = 0.50f

/** 窥视态淡入/淡出时长 (毫秒). */
private const val TV_SCHEDULE_PEEK_FADE_MILLIS = 220

/** 在追角标相对卡片边角的内缩. */
private val TV_SCHEDULE_BADGE_INSET = 7.dp

/** 在追角标直径. */
private val TV_SCHEDULE_BADGE_SIZE = 18.dp

/** 在追角标内图标尺寸. */
private val TV_SCHEDULE_BADGE_ICON_SIZE = 11.dp

/** 在追角标底色不透明度 (压在海报上要托得住图标). */
private const val TV_SCHEDULE_BADGE_BACKGROUND_ALPHA = 0.75f

/** 空态提示相对网格区中心的上移量 (网格区偏页面下半, 上移后视觉上接近整页居中). */
private val TV_SCHEDULE_EMPTY_HINT_RAISE = 120.dp
