/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.compose.collectWithLifecycle
import androidx.paging.compose.itemKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.ContinueWatchingStatus
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.toNavPlaceholder
import me.him188.ani.app.data.network.BangumiSummaryService
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.matchToEpisodes
import me.him188.ani.app.data.network.newestAiredDateStringOrNull
import me.him188.ani.app.data.network.tmdbStillHeroSizeUrl
import me.him188.ani.app.data.network.toTmdbLanguage
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.subject.SetSubjectCollectionTypeOrDeleteUseCase
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.tools.WeekFormatter
import me.him188.ani.app.ui.foundation.consumeHeldConfirmKey
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.app.ui.foundation.focus.GridFocusController
import me.him188.ani.app.ui.foundation.focus.GridFocusTransitAnchor
import me.him188.ani.app.ui.foundation.focus.TvScrollAnimator
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.tv.TvPageBackdropLayer
import me.him188.ani.app.ui.foundation.tv.TvPortraitCard
import me.him188.ani.app.ui.foundation.tv.tvPlayKeyForceRefresh
import me.him188.ani.app.ui.foundation.focus.gridKeyNavigation
import me.him188.ani.app.ui.foundation.tv.TV_HERO_MEDIA_DEBOUNCE_MILLIS
import me.him188.ani.app.ui.foundation.tv.TV_HERO_TEXT_FADE_MILLIS
import me.him188.ani.app.ui.foundation.tv.TV_HERO_TITLE_WIDTH_FRACTION
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_BOTTOM_SCRIM_HEIGHT
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_BOTTOM_SCRIM_MAX_ALPHA
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_CARD_SPACING
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_CARD_WIDTH
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_END_PAD
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_HINT_BOTTOM_PAD
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_HINT_ICON_SIZE
import me.him188.ani.app.ui.foundation.tv.TV_PORTRAIT_CARD_COVER_RATIO
import me.him188.ani.app.ui.foundation.tv.TV_HERO_SUMMARY_WIDTH_FRACTION
import me.him188.ani.app.ui.foundation.tv.tvHeroContentColor
import me.him188.ani.app.ui.foundation.tv.tvHeroMarqueeIterations
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.foundation.tv.tvHeroSecondaryContentColor
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.collection_tv_empty
import me.him188.ani.app.ui.lang.exploration_tv_air_date
import me.him188.ani.app.ui.lang.exploration_tv_all_caught_up
import me.him188.ani.app.ui.lang.exploration_tv_minutes_left
import me.him188.ani.app.ui.lang.exploration_tv_next_episode
import me.him188.ani.app.ui.lang.exploration_tv_watched_latest
import me.him188.ani.app.ui.lang.playback_history_episode_label
import me.him188.ani.app.ui.lang.subject_collection_doing
import me.him188.ani.app.ui.lang.subject_collection_done
import me.him188.ani.app.ui.lang.subject_collection_dropped
import me.him188.ani.app.ui.lang.subject_collection_on_hold
import me.him188.ani.app.ui.lang.subject_collection_uncollected
import me.him188.ani.app.ui.lang.subject_collection_wish
import me.him188.ani.app.ui.lang.subject_progress_continue_watching
import me.him188.ani.app.ui.lang.subject_progress_start_watching
import me.him188.ani.app.ui.lang.subject_progress_updates_on
import me.him188.ani.app.ui.lang.tv_card_remote_hint
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.isLoadingFirstPageOrRefreshing
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.collection.components.EditCollectionTypeDropDown
import me.him188.ani.datasources.api.toLocalDateOrNull
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.SubjectEnter
import me.him188.ani.utils.analytics.recordEvent
import org.jetbrains.compose.resources.stringResource

/**
 * TV 沉浸式追番页 (布局骨架参考 Prime Video 收藏页):
 * - 顶部悬浮收藏分类 Tab (透明底, 未选中降透明度, 选中高亮 + 平滑滑动指示条), 聚焦即切换;
 * - 上半区为 Hero 展示区: 全屏背景为聚焦条目的 TMDB backdrop (在看条目优先下一集单集剧照),
 *   显示标题 / 评分 / 连载信息 / 个人观看状态 (高亮) / 简介, 及动态主操作按钮
 *   (继续观看 第 X 集 / 开始观看 / 重温 / 更多详细内容);
 * - 下半区为 2:3 竖版海报网格, 卡片带播放进度条, 聚焦驱动 Hero, 短按进详情, 长按弹收藏菜单.
 *
 * 焦点动线: Tab 行 ↓ 主按钮 ↓ 网格; 网格首行 ↑ 回主按钮, 主按钮 ↑ 回选中 Tab.
 * 数据全部来自收藏分页列表自身 (条目信息完整, 无需二次请求); 仅 backdrop/单集剧照/简介兜底异步.
 */
@Composable
fun TvCollectionPage(
    state: UserCollectionsState,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val tmdb = remember { GlobalKoin.get<TmdbImageService>() }
    val bangumiSummaryService = remember { GlobalKoin.get<BangumiSummaryService>() }
    val settingsRepository = remember { GlobalKoin.get<SettingsRepository>() }
    val playHistoryRepository = remember { GlobalKoin.get<EpisodePlayHistoryRepository>() }
    val setCollectionTypeUseCase = remember { GlobalKoin.get<SetSubjectCollectionTypeOrDeleteUseCase>() }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    // 只取实例不再收集: 选中 tab 的网格 (下方 AnimatedContent 内) 已对同一缓存实例
    // collectWithLifecycle, 页面级再收集会有两个协程并发把每个分页 generation 灌进
    // 同一个 presenter —— 整表 diff 白做两遍, 还会互相竞争
    val items = remember(state.selectedTypeIndex) {
        state.getCollectionLazyPagingItems(state.selectedTypeIndex)
    }

    // 本页 tab 显示顺序与 state 的存储顺序不同 (见 TV_COLLECTION_TABS), 界面一律用类型换算下标
    val selectedType = COLLECTION_TABS_SORTED[state.selectedTypeIndex]
    val selectType: (UnifiedCollectionType) -> Unit = { type ->
        state.selectTypeIndex(COLLECTION_TABS_SORTED.indexOf(type))
    }
    // 统一网格落点协调器 (跨 tab 行对齐 / 网格内同列导航 / 返回回首卡 / 进页恢复焦点共用,
    // 机制见 [GridFocusController]); 声明在 hero 默认值效应之前, 后者要在解析期间让路
    val gridFocus = remember { GridFocusController() }
    // 焦点当前是否在网格卡片上. 卡片获焦置 true / 正常失焦 (去 tab/hero/侧边栏) 置 false;
    // 分页替换**销毁**聚焦卡时不会有失焦回调 —— 于是保持 true, 恰好是"焦点被销毁夺走而非
    // 用户离开"的判据 (下方塌缩恢复效应用)
    var gridRegionFocused by remember { mutableStateOf(false) }

    // Hero 数据源: 聚焦卡片时记录该条目快照; 展示时再按 subjectId 对回最新列表数据
    // (看完一集返回本页后分页已刷新, 快照里的进度是旧的)
    var heroItem by remember { mutableStateOf<SubjectCollectionInfo?>(null) }
    // 刚进页 / 切 tab 后还没聚焦过卡片: 默认展示当前列表第一项. 切 tab 数据加载期间保留
    // 旧 hero (信息块与主按钮不闪没, 新信息到了随渐隐换入); 确认新 tab 为空才清掉.
    // 落点解析期间不设默认 (否则先闪一下第一张卡的状态): 目标卡聚焦后由 onFocused
    // 设置 hero; 解析结束 (gridTarget 清空) 后本效应重跑, 只兜底解析失败的情况.
    // 观察值收进 snapshotFlow, 不当 effect key: pending 每次落点请求 设置→清除 变两次,
    // itemCount/加载态也是热读, 当 key 会让页面 body 作用域每键重组 (见 GridFocus.runResolveLoop)
    LaunchedEffect(state.selectedTypeIndex, items) {
        snapshotFlow {
            Triple(items.itemCount > 0, items.isLoadingFirstPageOrRefreshing, gridFocus.pending == null)
        }.collect { (hasItems, loadingFirstPage, pendingIdle) ->
            val cur = heroItem
            if (hasItems) {
                // 仅当当前 hero 不属于本 tab 列表时设默认, 不抢用户已聚焦的卡
                val curInList = cur != null && items.itemSnapshotList.items.any { it.subjectId == cur.subjectId }
                if (pendingIdle && !curInList) {
                    heroItem = items.peek(0)
                }
            } else if (!loadingFirstPage) {
                heroItem = null
            }
        }
    }
    // 状态化 (derivedStateOf) 而非普通局部值: 下方 LaunchedEffect 的 snapshotFlow 要能观察到变化
    val heroInfo by remember(items) {
        derivedStateOf {
            heroItem?.let { snapshot ->
                items.itemSnapshotList.items.firstOrNull { it.subjectId == snapshot.subjectId } ?: snapshot
            }
        }
    }

    // subjectId -> TMDB backdrop URL (null = 已查过但没有, 不再重查; 请求失败不缓存)
    val backdropCache = remember { mutableStateMapOf<Int, String?>() }
    // subjectId -> 观看中条目"下一集"的 TMDB 数据 (剧照 + 单集简介; 字段为 null = 查过没有)
    val episodeStillCache = remember { mutableStateMapOf<Int, TvCollectionNextEpisodeMedia>() }
    // subjectId -> bgm.tv 简介兜底 (Ani 服务器部分条目 summary 为空; "" = 也没有)
    val summaryFallbackCache = remember { mutableStateMapOf<Int, String>() }
    // 播放历史 (响应式): 卡片进度条与 hero 剩余分钟, 退出播放器回本页自动更新
    val playHistories by playHistoryRepository.flow.collectAsStateWithLifecycle(emptyList())

    // 异步补 Hero 媒体: 换聚焦条目时 collectLatest 取消在途请求; 条目信息本身来自列表, 无需请求.
    // 键到 items 上: 切 tab 换分页实例时重启, 不然闭包里捕获的是旧 tab 的 heroInfo 状态
    LaunchedEffect(items) {
        snapshotFlow { heroInfo }.filterNotNull().collectLatest { info ->
            delay(TV_HERO_MEDIA_DEBOUNCE_MILLIS) // 防抖: 网格快速划过时不发请求
            // 观看途中 (Continue/Watched): 背景优先用"下一集"的单集剧照, 直观提示进度节点.
            // 连载番的永久缓存可能不含新播集, 传已播出最新集日期触发陈旧重取 (服务层闸门限频).
            val nextEpisodeId = info.stillEpisodeIdOrNull()
            if (nextEpisodeId != null && episodeStillCache[info.subjectId]?.episodeId != nextEpisodeId) {
                runCatching {
                    val language = (settingsRepository.uiSettings.flow.first().appLanguage ?: Locale.current)
                        .toTmdbLanguage()
                    val stills = tmdb.getEpisodeStills(
                        info.subjectId, info.subjectInfo.name, language,
                        newestWantedAirDate = info.episodes.newestAiredDateStringOrNull(),
                    )
                    stills.matchToEpisodes(info.episodes)[nextEpisodeId]
                }.onSuccess { media ->
                    // 剧照存 w1280 档: 服务层存的是 original (偶有 4K 级原图), 当 hero 背景
                    // 解码 8-33MB 位图是低端盒子每次换卡的重锤; w1280 经渐隐压暗后无差
                    episodeStillCache[info.subjectId] =
                        // 存原图档 URL, 显示时才按设置降档 (见 TvPageBackdropLayer 调用处)
                        TvCollectionNextEpisodeMedia(nextEpisodeId, media?.stillUrl, media?.overview)
                }
            }
            // 整部 backdrop: 单集剧照缺失时的兜底 (以及未在看条目的主图).
            //
            // **有剧照也照拉** (与探索页同一判断): 详情页 Hero 用的一律是整部 backdrop,
            // 剧照只在本页当 hero 背景. 原先"拿到剧照就不再拉"省下的那次请求, 代价是本页
            // 观看中的条目 (恰恰是最常按进去的那批) 进详情页时 peekBackdropUrl 必然落空,
            // 首帧空白等解析 —— 同一部从探索页进有图、从这里进没图.
            // 正缓存永久有效, 每个条目全生命周期只真的请求一次.
            // 放在剧照之后: 本页 hero 要用的图先到, 这条不抢它的身位.
            if (info.subjectId !in backdropCache) {
                runCatching {
                    // 官方主背景图 (与详情页 hero 同源, 进详情零跳变); 屏保轮播才用全量列表.
                    // 传最新已播集日期: 新番刚播时 TMDB 往往还没有 backdrop, 负缓存据此限期失效
                    tmdb.getBackdropUrl(
                        info.subjectId,
                        info.subjectInfo.name,
                        activeAsOfDate = info.episodes.newestAiredDateStringOrNull(),
                    )
                }.onSuccess { url ->
                    backdropCache[info.subjectId] = url
                }
            }
            // Ani 服务器简介为空时直连 bgm.tv 补 (仅替代不合并); 网络错误不写缓存, 下次聚焦重试
            if (info.subjectInfo.summary.isBlank() && info.subjectId !in summaryFallbackCache) {
                runCatching { bangumiSummaryService.getSummary(info.subjectId) }
                    .onSuccess { summaryFallbackCache[info.subjectId] = it.orEmpty() }
            }
        }
    }

    val navigateToSubject: (SubjectCollectionInfo) -> Unit = { info ->
        Analytics.recordEvent(SubjectEnter) {
            put("source", "collection_card")
            put("subject_id", info.subjectId)
        }
        navigator.navigateSubjectDetails(
            subjectId = info.subjectId,
            placeholder = info.subjectInfo.toNavPlaceholder(),
        )
    }
    // 主按钮: 直接进播放页 —— 看完全部则从第一集重温, 其余接着播 nextEpisodeIdToPlay
    // (追平连载时它指回已看完的最新一集, 即重温最新一集); 无分集信息退化为进详情页
    val navigateToPlay: (SubjectCollectionInfo) -> Unit = { info ->
        val episodeId = when (info.progressInfo.continueWatchingStatus) {
            is ContinueWatchingStatus.Done -> info.episodes.firstOrNull()?.episodeId
            else -> info.progressInfo.nextEpisodeIdToPlay ?: info.episodes.firstOrNull()?.episodeId
        }
        if (episodeId != null) {
            Analytics.recordEvent(SubjectEnter) {
                put("source", "collection_play")
                put("subject_id", info.subjectId)
            }
            navigator.navigateEpisodeDetails(info.subjectId, episodeId)
        } else {
            navigateToSubject(info)
        }
    }

    // 焦点动线锚点: 每个 tab 标签一个请求器 (按 TV 显示顺序).
    //
    // 不给"选中的那个 tab"单独共享一个请求器: 那需要 `.then(if (selected) focusRequester(..))`
    // 这样的条件 modifier, 而条件元素位于 clickable (内含 focus target) 之前 —— 选中态一变,
    // 该 tab 后面的焦点节点就会被重建; 焦点恰好在这个 tab 上时会被丢掉, 焦点系统随即把默认
    // 焦点发回第一个可聚焦元素 (第一个 tab). 而"聚焦即选中"意味着每次焦点落到新 tab 都会触发
    // 一次选中态变化, 于是按住方向键快速移动时偶发被拉回最左标签.
    val tabFocusRequesters = remember { List(TV_COLLECTION_TABS.size) { FocusRequester() } }
    // 聚焦当前选中的 tab. 用函数而非捕获下标: 效应/按键回调里调用时要读到最新选中项
    val focusSelectedTab: () -> Boolean = {
        val tvIndex = TV_COLLECTION_TABS.indexOf(COLLECTION_TABS_SORTED[state.selectedTypeIndex])
        tvIndex >= 0 && runCatching { tabFocusRequesters[tvIndex].requestFocus() }.isSuccess
    }
    // 列表加载出错 (如未登录) 时的错误横幅: 挂请求器让 tab 下键能落到横幅里的按钮 (登录/重试)
    val errorCardFocusRequester = remember { FocusRequester() }
    // 当前 tab 内最后聚焦的卡片下标 (跨导航保存, 返回本页恢复焦点); 切 tab 重置
    var lastFocusedCard by rememberSaveable { mutableIntStateOf(-1) }
    // 收藏状态刚被改掉、正等着离开本 tab 的条目 (见下方等待效应); null = 没有
    var awaitingRemovalSubjectId by remember { mutableStateOf<Int?>(null) }
    var prevTabIndex by rememberSaveable { mutableIntStateOf(state.selectedTypeIndex) }
    if (prevTabIndex != state.selectedTypeIndex) {
        prevTabIndex = state.selectedTypeIndex
        lastFocusedCard = -1
        // 用户自己切了 tab: 旧 tab 的卡去哪已无关紧要, 别让等待效应在新 tab 里安排落点
        awaitingRemovalSubjectId = null
    }
    // 进入本页要恢复的目标卡片下标 (进页那一刻的快照; -1 = 聚焦选中 tab)
    val restoreCardIndex = remember { lastFocusedCard }
    var anyFocusObtained by remember { mutableStateOf(false) }
    // 恢复期间抑制标签的"聚焦即选中": 返回本页瞬间系统会把默认焦点塞给第一个可聚焦元素
    // (第一个 tab 标签), 若不抑制, 其"聚焦即选中"会把选中 tab 改掉, 恢复目标卡随之落进错误的 tab
    var restorePending by remember { mutableStateOf(restoreCardIndex >= 0) }
    LaunchedEffect(Unit) {
        // 初始焦点: 返回本页恢复到此前聚焦的卡片 (借统一落点解析: 等分页数据到达、卡片组合
        // 出来并确认拿到焦点; 中途系统把默认焦点塞给按钮/标签也会被解析器重试拉回),
        // 否则轮询聚焦选中 tab (requestFocus 未附着时静默失败, 重试).
        if (restoreCardIndex >= 0) {
            gridFocus.request(restoreCardIndex)
            // 解析结束 (成功或放弃) 才放开 tab 的"聚焦即选中"; attempts 120 覆盖
            // 落点解析器自身的等数据/滚动组合超时
            resolveFocusRepeatedly(attempts = 120, arrived = { gridFocus.pending == null }) {}
            restorePending = false
        } else {
            resolveFocusRepeatedly(attempts = 80, delayMillis = 50, arrived = { anyFocusObtained }) {
                focusSelectedTab()
            }
        }
    }

    // 从详情页/播放器返回本页时重新落点.
    //
    // 不能只靠上面那个 LaunchedEffect(Unit): 本页作为主页的一个 tab, 快速返回时整棵子树可能
    // 一直没被销毁 (TV 用 crossfade 过渡), 该效应不会再跑; 而网格项在离开期间被销毁, 焦点随之
    // 悬空 —— 表现为返回后看不到焦点圈, 按下键才落到首卡. 生命周期信号与组合是否存活无关,
    // 因此用它: 每次重新 RESUMED (首次除外, 首次由上面的效应处理) 都补发一次落点请求.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        var resumedBefore = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!resumedBefore) {
                resumedBefore = true
                return@repeatOnLifecycle
            }
            val card = lastFocusedCard
            if (card >= 0) gridFocus.request(card) else focusSelectedTab()
        }
    }

    // 焦点卡因收藏状态改变离开本 tab: 等它真的从列表里消失, 再安排落点.
    //
    // 不能在点下拉菜单那一刻就 request: 改收藏要一次网络往返, 那时卡还在, 落点解析第一帧就会
    // 把焦点聚焦回原卡并判定"到位"结束; 等卡真消失时已无人接管, 焦点悬空 —— Compose 会
    // clearFocus 整棵树并做一次初始焦点分配 (见 FocusTargetNode.onReset/onDetach, 源码明确
    // **不**把焦点交给焦点祖先, 那只是注释里的将来打算), 落点是遍历顺序第一个可聚焦元素 =
    // 第一个 tab 标签; 而标签的"聚焦即选中"刻意不认系统塞来的焦点 (见 selectByFocusArmed),
    // 于是高亮停在第一个标签而指示条还留在当前 tab 上.
    //
    // 一次 request 覆盖两种结局, 由 [GridFocusController.resolve] 自己分岔: 本 tab 还有卡 ->
    // 夹到相邻下标 (焦点留在原位置); 整个 tab 空了 -> onEmptyIdle 回到选中标签.
    LaunchedEffect(awaitingRemovalSubjectId) {
        val subjectId = awaitingRemovalSubjectId ?: return@LaunchedEffect
        // 超时兜底: 请求成功但列表迟迟不刷新时也要收尾, 否则隐形锚点一直可聚焦, 焦点就停在
        // 那个不可见节点上 (方向键还能走, 但看不到焦点圈)
        withTimeoutOrNull(TV_COLLECTION_AWAIT_REMOVAL_TIMEOUT_MILLIS) {
            snapshotFlow { items.itemSnapshotList.items.none { it.subjectId == subjectId } }
                .first { it }
        }
        awaitingRemovalSubjectId = null
        gridFocus.request(lastFocusedCard.coerceAtLeast(0))
    }

    // 网格通用返回规则: 不在首卡时按返回先回网格第一张卡 (借统一落点解析:
    // 轮询等滚动/组合完成再聚焦). 已在首卡时不启用, 返回交给上层 (回探索页).
    // derivedStateOf: 焦点下标每移一格都变, 直接读会让整页每格重组, 收窄成布尔
    var gridHasFocus by remember { mutableStateOf(false) }
    val backToFirstCard by remember {
        derivedStateOf { gridHasFocus && lastFocusedCard > 0 }
    }
    BackHandler(enabled = backToFirstCard) {
        gridFocus.request(0)
    }

    // 卡片长按弹出的收藏下拉 (与探索页一致); 打开后短暂吞掉长按残余的确认键, 避免误触第一项.
    // remember: 工厂被网格 items 内容 lambda 捕获, 每次新实例都会让所有可见卡片跟着重组
    val collectionMenuFor: (SubjectCollectionInfo) -> @Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit = remember {
        { info ->
            { expanded, onDismiss ->
                EditCollectionTypeDropDown(
                    currentType = info.collectionType,
                    expanded = expanded,
                    onDismissRequest = onDismiss,
                    onClick = { action ->
                        // 改成别的状态后本条目会离开当前 tab, 焦点此刻正在它的卡片上 (菜单是长按它
                        // 弹出的). 先把焦点钉到隐形锚点躲开即将到来的销毁 (同跨 tab 导航的做法),
                        // 再登记等待条目消失 —— 落点由上方的等待效应安排. 直接留在卡上等销毁的话
                        // 焦点会悬空并被系统重分配到第一个 tab 标签.
                        //
                        // 这里读 state 而非捕获外层的 selectedType: 本工厂 remember 无 key
                        // (避免每次重组换实例让所有可见卡片跟着重组), 捕获的值会停在首次组合那一刻.
                        if (action.type != COLLECTION_TABS_SORTED[state.selectedTypeIndex]) {
                            awaitingRemovalSubjectId = info.subjectId
                            gridFocus.parkFocusOnAnchor()
                        }
                        scope.launch {
                            runCatching { setCollectionTypeUseCase(info.subjectId, action.type) }
                                .onFailure {
                                    // 改失败, 条目不会离开列表: 立刻收尾并把焦点送回原卡,
                                    // 否则等待效应要空等到超时, 期间焦点停在不可见锚点上
                                    if (awaitingRemovalSubjectId == info.subjectId) {
                                        awaitingRemovalSubjectId = null
                                        gridFocus.request(lastFocusedCard.coerceAtLeast(0))
                                    }
                                    toaster.showLoadError(LoadError.fromException(it))
                                }
                        }
                    },
                    // 卡片的菜单只有长按一个入口, 恒吞掉那次长按残余的确认键
                    modifier = Modifier.consumeHeldConfirmKey(),
                )
            }
        }
    }

    // 播放键: 短按播聚焦条目的下一集, 长按强制重拉当前 tab 的收藏列表 (默认只在进页/一小时
    // 定时同步时刷新). 挂在页面根上而不是网格上: 焦点在 tab 行时也能刷
    val playKeyModifier = tvPlayKeyForceRefresh(
        onRefresh = { state.refreshSelectedPage() },
        onPlay = {
            val info = lastFocusedCard.takeIf { it >= 0 }
                ?.let { runCatching { items.peek(it) }.getOrNull() }
            if (info != null) {
                navigateToPlay(info)
                true
            } else {
                false
            }
        },
    )

    Box(modifier.fillMaxSize().then(playKeyModifier)) {
        // 背景 backdrop 层 (探索/搜索页同款, 恒用"卡片态"渐变): 观看途中优先下一集剧照,
        // 缺失回退整部官方主图. URL 用 lambda 传入: 聚焦条目状态在组件内部才读取,
        // 遥控器换卡只重组这一小块.
        // 剧照按设置降档 (默认 w1280, 完整视觉效果用原图); backdrop 那路服务层已是 w1280 档
        val fullVisualEffects = LocalThemeSettings.current.tvFullVisualEffects
        TvPageBackdropLayer(
            backdropUrl = {
                heroInfo?.let { info ->
                    (if (info.stillEpisodeIdOrNull() != null) {
                        episodeStillCache[info.subjectId]?.stillUrl?.let { tmdbStillHeroSizeUrl(it, fullVisualEffects) }
                    } else null)
                        ?: backdropCache[info.subjectId]
                }
            },
            // 本页在主壳内, 图层正下方是主壳铺的 shellBackgroundColor (见 TvMainScreenLayout)
            fadeColor = AniThemeDefaults.shellBackgroundColor,
            modifier = Modifier.align(Alignment.TopEnd),
        )

        Column(
            Modifier.fillMaxSize()
                .padding(start = TV_COLLECTION_START_PAD, top = TV_COLLECTION_TOP_PAD),
        ) {
            // 悬浮分类 Tab (透明底浮于 backdrop 上)
            TvCollectionTabRow(
                selectedType = selectedType,
                counts = { type -> state.collectionCounts?.getCount(type) },
                // 跨 tab 落点解析期间与进页恢复焦点期间抑制 tab 的"聚焦即选中" (兜底: 万一
                // 瞬时焦点飘到某个标签上, 不能让它改写目标 tab 的选择)
                onSelect = { type -> if (gridFocus.pending == null && !restorePending) selectType(type) },
                tabFocusRequesters = tabFocusRequesters,
                onAnyFocused = { anyFocusObtained = true },
                // 标签间导航也算用户接手: 取消挂起的网格落点解析, 否则它的 onEmptyIdle
                // 会把焦点拉回"选中的标签"(选中态比焦点滞后一帧, 于是像是被拉回上一个标签)
                onUserNavigation = gridFocus::onUserNavigation,
                onNavigateDown = {
                    // 主走统一落点解析聚焦当前视口首行行首 (到位确认 + 重试; 同搜索页:
                    // 直连首卡 requestFocus 偶发被焦点系统静默拒绝时 runCatching 照样报成功,
                    // 下键被吞且不重试); 网格空时退到错误横幅 (登录/重试按钮)
                    val firstVisibleRow = state.getGridState(state.selectedTypeIndex)
                        .layoutInfo.visibleItemsInfo.firstOrNull()?.row
                    if (firstVisibleRow != null) {
                        gridFocus.requestRow(firstVisibleRow, rowStart = true)
                        true
                    } else {
                        runCatching { errorCardFocusRequester.requestFocus() }.isSuccess
                    }
                },
            )

            // Hero 信息块 (固定高度, 切换聚焦条目时网格不跳动). 聚焦条目状态在子组件内部
            // 才读取, 遥控器换卡只重组信息块自身, 不连带整页作用域
            TvCollectionHeroBlock(
                heroInfoProvider = { heroInfo },
                episodeStillCache = episodeStillCache,
                summaryFallbackCache = summaryFallbackCache,
                remainingMinutesOf = { episodeId ->
                    playHistories.firstOrNull { it.episodeId == episodeId }?.let { history ->
                        val duration = history.durationMillis
                        if (duration != null && duration > 0 && history.positionMillis > 0) {
                            (((duration - history.positionMillis).coerceAtLeast(0L) + 59_999) / 60_000)
                                .toInt().coerceAtLeast(1)
                        } else null
                    }
                },
                // end 留白与探索页 hero 块一致, 否则 fillMaxWidth(比例) 的基数比其他页宽
                modifier = Modifier.fillMaxWidth()
                    .padding(top = TV_COLLECTION_TABS_TO_HERO_GAP, end = TV_PAGE_END_PAD)
                    .height(TV_COLLECTION_HERO_INFO_HEIGHT),
            )

            // 过渡期的隐形焦点驻留点 (跨 tab 换网格 / 改收藏状态让条目离开本 tab 时焦点先躲到这里,
            // 机制与摆放位置的讲究见 [GridFocusTransitAnchor]; 与时间表换天共用同一实现).
            // extraCanFocus: 改收藏状态那条路径上没有挂起的落点请求 (要等条目真的从列表消失才发),
            // 锚点得靠这个条件保持可聚焦
            GridFocusTransitAnchor(
                gridFocus,
                extraCanFocus = { awaitingRemovalSubjectId != null },
                // 落点解析放弃 / 等条目消失超时: 焦点还在锚点上而锚点即将不可聚焦, 补落点到选中标签
                onStranded = { focusSelectedTab() },
            )

            // 竖版海报网格
            if (items.loadState.hasError) {
                LoadErrorCard(
                    LoadError.fromCombinedLoadStates(items.loadState),
                    onRetry = { items.refresh() },
                    Modifier.padding(top = TV_COLLECTION_HERO_TO_GRID_GAP, end = TV_PAGE_END_PAD)
                        // 请求器挂在卡片容器上, requestFocus 委托给子树第一个焦点目标 (登录/重试按钮);
                        // 按上键显式送回选中 tab (跨层级的方向搜索不可靠)
                        .focusRequester(errorCardFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                focusSelectedTab()
                            } else {
                                false
                            }
                        },
                )
            }
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth()
                    .padding(top = TV_COLLECTION_HERO_TO_GRID_GAP)
                    .onFocusChanged { gridHasFocus = it.hasFocus },
            ) {
                // 复刻 GridCells.Adaptive 的列数算法 (整数 px 运算), 供跨 tab 导航的行列换算
                val density = LocalDensity.current
                val gridColumns = with(density) {
                    val available = (this@BoxWithConstraints.maxWidth - TV_PAGE_END_PAD).roundToPx()
                    val spacing = TV_PAGE_CARD_SPACING.roundToPx()
                    maxOf(1, (available + spacing) / (TV_PAGE_CARD_WIDTH.roundToPx() + spacing))
                }
                // 底部补白 = 视口高 - 一行卡高: 让最后一行也能吸到网格顶部
                // (内容不足一屏时 animateScrollToItem 滚不动, 接近底部的行会失去吸顶)
                val gridBottomPad = run {
                    val available = this@BoxWithConstraints.maxWidth - TV_PAGE_END_PAD
                    val cardWidth =
                        (available - TV_PAGE_CARD_SPACING * (gridColumns - 1)) / gridColumns
                    val cardHeight = cardWidth / TV_PORTRAIT_CARD_COVER_RATIO
                    (this@BoxWithConstraints.maxHeight - cardHeight).coerceAtLeast(24.dp)
                }
                // 跨 tab 网格过渡: 开了完整视觉效果 (设置项, 默认关) 才按 TV 顺序方向整体水平
                // 滑动, 滑出边界被裁掉; 否则降级为渐隐渐现 (静止渐隐比运动滑动更能掩盖低端机
                // 掉帧 —— 实测这段 560ms 双网格滑动是换 tab 那记 jank 的主要来源). 过渡期间
                // 新旧两个网格同时组合, 各自读自己 tab 的分页数据 (有缓存), 滚动位置按 tab 保留.
                val fullTransitions = LocalThemeSettings.current.tvFullVisualEffects
                AnimatedContent(
                    targetState = state.selectedTypeIndex,
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    transitionSpec = {
                        if (fullTransitions) {
                            val forward = TV_COLLECTION_TABS.indexOf(COLLECTION_TABS_SORTED[targetState]) >
                                    TV_COLLECTION_TABS.indexOf(COLLECTION_TABS_SORTED[initialState])
                            slideInHorizontally(tween(TV_COLLECTION_TAB_SLIDE_MILLIS)) { width ->
                                if (forward) width else -width
                            } togetherWith slideOutHorizontally(tween(TV_COLLECTION_TAB_SLIDE_MILLIS)) { width ->
                                if (forward) -width else width
                            }
                        } else {
                            fadeIn(tween(TV_COLLECTION_TAB_FADE_MILLIS)) togetherWith
                                    fadeOut(tween(TV_COLLECTION_TAB_FADE_MILLIS))
                        }
                    },
                    label = "collectionTabGrid",
                ) { tabIndex ->
                    val tabItems = remember(tabIndex) {
                        state.getCollectionLazyPagingItems(tabIndex)
                    }.collectWithLifecycle()
                    val gridState = remember(tabIndex) { state.getGridState(tabIndex) }
                    val isActiveTab = tabIndex == state.selectedTypeIndex
                    // 统一落点解析 (跨 tab / 同列导航 / 回首卡 / 进页恢复只是目标参数不同,
                    // 机制见 [GridFocusController.resolve]): 整个 tab 一张卡都没有 (且不在
                    // 加载) 则聚焦 tab 标签. 只在选中 tab 的网格实例上运行; 跨 tab 时目标先于
                    // selectType 设置, 新 tab 网格组合后由本效应接手解析 (滑动过渡中即聚焦,
                    // 焦点圈随网格滑入).
                    if (isActiveTab) LaunchedEffect(tabItems) {
                        gridFocus.runResolveLoop(
                            gridState = gridState,
                            columns = { gridColumns },
                            itemCount = { tabItems.itemCount },
                            isLoadingFirstPage = { tabItems.isLoadingFirstPageOrRefreshing },
                            onEmptyIdle = { focusSelectedTab() },
                        )
                    }
                    // 分页 generation 替换时的焦点抢救: 这套 pager 是 Room + RemoteMediator,
                    // 每次写库 (REFRESH 先清表回填 / append) 都换 generation, 重载窗口外的条目
                    // 退回 placeholder —— 聚焦卡的 key 从 subjectId 换成 placeholder key, 节点
                    // 被销毁, 焦点被系统重分配 (实测闪到顶部标签行, 其"聚焦即选中"还会误切 tab).
                    // 4K 视口 60+ 卡远超窗口, REFRESH 必现.
                    // 恢复三步: 钉锚点 (发出的落点请求同时抑制 tab 聚焦即选中) → 等聚焦下标回填
                    // 成真数据 (placeholder 卡虽可聚焦, 但回填时 key 替换又会销毁一次, 不能停在
                    // 它上面) → 送回原下标.
                    if (isActiveTab) LaunchedEffect(tabItems) {
                        snapshotFlow {
                            val focused = lastFocusedCard
                            val count = tabItems.itemCount
                            gridRegionFocused && focused >= 0 &&
                                    (count <= focused || tabItems.itemSnapshotList[focused] == null)
                        }.collect { collapsed ->
                            if (!collapsed || gridFocus.pending != null) return@collect
                            gridFocus.parkFocusOnAnchor()
                            gridFocus.request(lastFocusedCard) // 立即挂起请求, 抑制 tab 聚焦即选中
                            withTimeoutOrNull(8_000) {
                                snapshotFlow {
                                    val f = lastFocusedCard
                                    f in 0 until tabItems.itemCount && tabItems.itemSnapshotList[f] != null
                                }.first { it }
                            }
                            // 数据回填后重发一次: 首个请求可能已在 placeholder 卡上"到位"过
                            gridFocus.request(lastFocusedCard)
                        }
                    }
                    // 聚焦行吸顶 (同探索页): 关闭默认"刚好露出"式的自动滚动, 聚焦行直接滚到
                    // 网格顶部, 上方的行完全滚出视口
                    val noBringIntoView = remember {
                        object : BringIntoViewSpec {
                            override fun calculateScrollDistance(
                                offset: Float,
                                size: Float,
                                containerSize: Float,
                            ): Float = 0f
                        }
                    }
                    if (isActiveTab) {
                        LaunchedEffect(gridState) {
                            // collectLatest + TvScrollAnimator: 连发按键取消进行中的滚动并继承
                            // 速度, 列表连续流动 (原 collect 要等上一格动画跑完才响应下一个目标)
                            val scrollAnimator = TvScrollAnimator()
                            snapshotFlow { lastFocusedCard }.collectLatest { focused ->
                                if (focused >= 0) {
                                    runCatching {
                                        scrollAnimator.animateScrollToItem(gridState, (focused / gridColumns) * gridColumns)
                                    }
                                }
                            }
                        }
                    }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides noBringIntoView) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(TV_PAGE_CARD_WIDTH),
                            modifier = Modifier
                                .fillMaxSize()
                                // 同列上下导航 + 播放键直达 (共享实现, 理由见 [gridKeyNavigation]);
                                // extraKeys 处理跨 tab 行对齐导航: 行末右键 -> 右侧 tab 同一行最左卡,
                                // 行首左键对称 (第一个 tab 行首不消费, 交给焦点系统 -> 侧边栏).
                                // 落点无记忆: 回程按当前所在行对应端落点, 不回到来时的卡.
                                .gridKeyNavigation(
                                    gridFocus,
                                    focusedIndex = { lastFocusedCard },
                                    itemCount = { tabItems.itemCount },
                                    columns = { gridColumns },
                                    // 顶行上键回选中 tab (主按钮已移除)
                                    onTopRowUp = {
                                        focusSelectedTab()
                                    },
                                    // 播放键由页面根节点接管 (短按续播 / 长按强制刷新, 见
                                    // tvPlayKeyForceRefresh): 长按要靠 KeyUp 才能与短按区分,
                                    // 而本路由只处理 KeyDown
                                    onPlayKey = { false },
                                    enabled = { isActiveTab },
                                    extraKeys = { event, focused, cols, count ->
                                        val tvIndex = TV_COLLECTION_TABS.indexOf(selectedType)
                                        when (event.key) {
                                            Key.DirectionRight -> {
                                                val rowEnd = focused % cols == cols - 1 ||
                                                        focused == count - 1
                                                if (rowEnd && tvIndex in 0..<TV_COLLECTION_TABS.size - 1) {
                                                    gridFocus.requestRow(focused / cols, rowStart = true)
                                                    // 切 tab 前把焦点钉到隐形锚点: 原卡片随分页替换销毁后焦点
                                                    // 悬空会被系统重分配 (可能落到第一个 tab 标签, 其"聚焦即
                                                    // 选中"会把选择拽回去); 锚点不可见, 不产生聚焦样式闪烁
                                                    gridFocus.parkFocusOnAnchor()
                                                    selectType(TV_COLLECTION_TABS[tvIndex + 1])
                                                    true
                                                } else if (rowEnd) {
                                                    // 末 tab 的行末按右: 消费掉. 不消费会落到默认方向搜索,
                                                    // 右侧无目标时它会退出/重进内容焦点组, 被外壳的
                                                    // onEnter 送回首个可聚焦元素 (第一个 tab 标签)
                                                    true
                                                } else {
                                                    // 行内还有卡: 交给默认方向搜索横向移动
                                                    false
                                                }
                                            }

                                            Key.DirectionLeft -> {
                                                if (focused % cols == 0 && tvIndex > 0) {
                                                    gridFocus.requestRow(focused / cols, rowStart = false)
                                                    // 同右键分支: 防止焦点悬空被系统重分配
                                                    gridFocus.parkFocusOnAnchor()
                                                    selectType(TV_COLLECTION_TABS[tvIndex - 1])
                                                    true
                                                } else {
                                                    false
                                                }
                                            }

                                            else -> false
                                        }
                                    },
                                ),
                            state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(TV_PAGE_CARD_SPACING),
                            verticalArrangement = Arrangement.spacedBy(TV_PAGE_CARD_SPACING),
                            contentPadding = PaddingValues(end = TV_PAGE_END_PAD, bottom = gridBottomPad),
                        ) {
                            items(
                                tabItems.itemCount,
                                key = tabItems.itemKey { "TvCollectionPage-" + it.subjectId },
                            ) { index ->
                                val info = tabItems[index]
                                // derivedStateOf: 落点下标在每次导航期间都会 设置->清除 变两次,
                                // 直接读会让所有可见卡片跟着重组两遍; 收窄成布尔后只有
                                // 目标卡自己 (挂/摘请求器) 重组
                                val isGridTarget by remember(index) {
                                    derivedStateOf { gridFocus.resolvedIndex == index }
                                }
                                TvPortraitCard(
                                    imageUrl = info?.subjectInfo?.imageLarge,
                                    contentDescription = info?.subjectInfo?.displayName,
                                    onClick = { info?.let(navigateToSubject) },
                                    onFocused = {
                                        info?.let { heroItem = it }
                                        lastFocusedCard = index
                                        anyFocusObtained = true
                                        gridFocus.onCardFocused(index)
                                    },
                                    // 焦点在网格与否 (塌缩恢复的判据): 获焦 true / 正常失焦 false;
                                    // 节点被分页替换销毁时**不会**回调, true 得以保留
                                    onFocusChangedExtra = { gridRegionFocused = it },
                                    modifier = Modifier
                                        .ifThen(isActiveTab && isGridTarget) {
                                            focusRequester(gridFocus.requester)
                                        },
                                    menu = info?.let { collectionMenuFor(it) },
                                    // 下一集播放进度 (语义同探索页继续观看卡): 看到一半按播放位置;
                                    // 追平连载 (Watched) 满条; 其余 (想看/看完/未开始) 不显示
                                    progress = info?.progressInfo?.let { progressInfo ->
                                        when (progressInfo.continueWatchingStatus) {
                                            is ContinueWatchingStatus.Watched -> 1f
                                            is ContinueWatchingStatus.Continue -> progressInfo.nextEpisodeIdToPlay
                                                ?.let { nextId -> playHistories.firstOrNull { it.episodeId == nextId } }
                                                ?.let { history ->
                                                    val duration = history.durationMillis
                                                    if (duration != null && duration > 0) {
                                                        (history.positionMillis.toFloat() / duration).coerceIn(0f, 1f)
                                                    } else null
                                                }

                                            else -> null
                                        }
                                    },
                                )
                            }
                        }
                    }
                    // 空分类提示: 在内容区 (侧边栏右侧) 居中; 网格区偏页面下半,
                    // 上移一段让它视觉上接近整页居中
                    if (tabItems.itemCount == 0 && !tabItems.isLoadingFirstPageOrRefreshing && !tabItems.loadState.hasError) {
                        Box(
                            Modifier.fillMaxSize().offset(y = -TV_COLLECTION_EMPTY_HINT_RAISE),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(Lang.collection_tv_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }

        // 底缘弱渐变遮罩 (页面背景色, smoothstep 采样无折点): 轻压被视口截断的下一行卡片,
        // 保证右下角提示在滚动的海报上仍可读. 只绘制, 不参与点击/焦点.
        run {
            val bg = MaterialTheme.colorScheme.background
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(TV_PAGE_BOTTOM_SCRIM_HEIGHT)
                    .background(
                        Brush.verticalGradient(
                            *Array(11) { i ->
                                val f = i / 10f
                                val ease = f * f * (3f - 2f * f)
                                f to bg.copy(alpha = ease * TV_PAGE_BOTTOM_SCRIM_MAX_ALPHA)
                            },
                        ),
                    ),
            )
        }

        // 右下角遥控键提示 (参考 Prime 的同位置提示): 次要色低调常显
        Row(
            Modifier.align(Alignment.BottomEnd)
                .padding(end = TV_PAGE_END_PAD, bottom = TV_PAGE_HINT_BOTTOM_PAD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                Modifier.size(TV_PAGE_HINT_ICON_SIZE),
                tint = tvHeroSecondaryContentColor(),
            )
            Text(
                stringResource(Lang.tv_card_remote_hint),
                color = tvHeroSecondaryContentColor(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Hero 背景想用"下一集剧照"时返回该集 id (观看途中/追平连载); 其余状态用整部 backdrop. */
private fun SubjectCollectionInfo.stillEpisodeIdOrNull(): Int? {
    val status = progressInfo.continueWatchingStatus
    return if (status is ContinueWatchingStatus.Continue || status is ContinueWatchingStatus.Watched) {
        progressInfo.nextEpisodeIdToPlay
    } else null
}

/** 观看中条目"下一集"的 TMDB 数据; [episodeId] 用于看完一集后 (下一集变化) 失效重查. */
private data class TvCollectionNextEpisodeMedia(
    val episodeId: Int,
    val stillUrl: String?,
    val overview: String?,
)

/**
 * Hero 信息块 (含换条目渐隐渐现). [heroInfoProvider] 用 lambda 传入: 聚焦条目状态在
 * 本组件内部才读取, 遥控器每移一格只重组这一块, 不连带整页作用域. 退场内容读退场
 * 条目自己的数据 (contentKey=条目).
 */
@Composable
private fun TvCollectionHeroBlock(
    heroInfoProvider: () -> SubjectCollectionInfo?,
    episodeStillCache: Map<Int, TvCollectionNextEpisodeMedia>,
    summaryFallbackCache: Map<Int, String>,
    remainingMinutesOf: (Int) -> Int?,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = heroInfoProvider(),
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(TV_HERO_TEXT_FADE_MILLIS)) togetherWith
                    fadeOut(tween(TV_HERO_TEXT_FADE_MILLIS))
        },
        contentKey = { it?.subjectId },
        label = "collectionHeroInfo",
    ) { hero ->
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hero != null) {
                TvCollectionHeroInfo(
                    info = hero,
                    nextEpisodeOverview = hero.stillEpisodeIdOrNull()
                        ?.let { episodeStillCache[hero.subjectId]?.overview }
                        ?.takeIf { it.isNotBlank() },
                    summaryFallback = summaryFallbackCache[hero.subjectId],
                    remainingMinutesOf = remainingMinutesOf,
                )
            }
        }
    }
}

/**
 * Hero 信息块内容: 标题 / 评分 + 连载信息 + 开播年月 / 个人观看状态 (高亮) / 简介.
 * 结构与探索页 hero 一致, 个人状态行改用主题色高亮 (追番页的核心信息).
 */
@Composable
private fun ColumnScope.TvCollectionHeroInfo(
    info: SubjectCollectionInfo,
    nextEpisodeOverview: String?,
    summaryFallback: String?,
    remainingMinutesOf: (episodeId: Int) -> Int?,
) {
    Text(
        info.subjectInfo.displayName,
        Modifier.fillMaxWidth(TV_HERO_TITLE_WIDTH_FRACTION),
        color = tvHeroContentColor(),
        style = MaterialTheme.typography.headlineLarge,
        // 超长换行, 至多两行 (与探索页/搜索页统一); 简介 weight 自动让出空间
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val score = info.subjectInfo.ratingInfo.score
        if ((score.toFloatOrNull() ?: 0f) > 0f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "$score/10",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AiringLabel(
                remember(info) {
                    AiringLabelState(stateOf(info.airingInfo), stateOf(info.progressInfo))
                },
                style = MaterialTheme.typography.labelLarge,
                progressColor = tvHeroSecondaryContentColor(),
            )
            val airDate = info.subjectInfo.airDate
            if (airDate.isValid) {
                Text(
                    "    " + stringResource(Lang.exploration_tv_air_date, airDate.year, airDate.month),
                    color = tvHeroSecondaryContentColor(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
    // 个人观看状态行 (主题色高亮). 三态语义同探索页 (见 SubjectProgressInfo.compute):
    //  - 已看完最新一集/全部 (Watched/Done): "第 8 集 · 集名 · 已看完[最新一集 · 周几更新]"
    //  - 看到一半 (有播放记录): "第 4 集 · 集名 · 剩余 23 分钟"
    //  - 看完上一集且有新集 / 还没开始: "下一集: 第 4 集 · 集名".
    // 集号与尾段为固定段永不截断; 集名居中段, 超长跑马灯滚动. 未开播不显示本行.
    val status = info.progressInfo.continueWatchingStatus
    val nextEp = info.progressInfo.nextEpisodeIdToPlay?.let { id ->
        info.episodes.firstOrNull { it.episodeId == id }
    }
    if (nextEp != null && status !is ContinueWatchingStatus.NotOnAir) {
        val epLabel = stringResource(
            Lang.playback_history_episode_label,
            nextEp.episodeInfo.sort.toString(),
        )
        val epName = nextEp.episodeInfo.nameCn.ifBlank { nextEp.episodeInfo.name }
        val caughtUp = status is ContinueWatchingStatus.Watched || status is ContinueWatchingStatus.Done
        val remainingMinutes = if (caughtUp) null else remainingMinutesOf(nextEp.episodeId)
        Row(
            Modifier.fillMaxWidth(TV_HERO_SUMMARY_WIDTH_FRACTION),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val epInfoColor = MaterialTheme.colorScheme.primary
            val epInfoStyle = MaterialTheme.typography.labelLarge
            // 主按钮已移除 (处于焦点动线死角), 其动作语义并入本行头部: 继续观看/开始观看 + 集号;
            // 播放动作由遥控器播放键承担 (见网格键处理), 右下角有常显提示
            val head = when {
                caughtUp -> epLabel
                status is ContinueWatchingStatus.Continue ->
                    stringResource(Lang.subject_progress_continue_watching, epLabel)
                status is ContinueWatchingStatus.Start ->
                    stringResource(Lang.subject_progress_start_watching) + " · " + epLabel
                else -> stringResource(Lang.exploration_tv_next_episode, epLabel)
            }
            Text(
                head,
                color = epInfoColor,
                style = epInfoStyle,
                maxLines = 1,
            )
            if (epName.isNotBlank()) {
                Text(
                    " · $epName",
                    Modifier.weight(1f, fill = false)
                        .basicMarquee(iterations = tvHeroMarqueeIterations()),
                    color = epInfoColor,
                    style = epInfoStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
            if (caughtUp) {
                val watchedStatus = status as? ContinueWatchingStatus.Watched
                val updatesOn = watchedStatus?.nextEpisodeAirDate?.toLocalDateOrNull()?.let { date ->
                    stringResource(Lang.subject_progress_updates_on, WeekFormatter.System.format(date))
                }
                Text(
                    " · " + (
                            if (watchedStatus != null) {
                                stringResource(Lang.exploration_tv_watched_latest)
                            } else {
                                stringResource(Lang.exploration_tv_all_caught_up)
                            }
                            ) + (updatesOn?.let { " · $it" } ?: ""),
                    color = epInfoColor,
                    style = epInfoStyle,
                    maxLines = 1,
                )
            } else if (remainingMinutes != null) {
                Text(
                    " · " + stringResource(Lang.exploration_tv_minutes_left, remainingMinutes),
                    color = epInfoColor,
                    style = epInfoStyle,
                    maxLines = 1,
                )
            }
        }
    }
    // 简介: 观看途中优先展示下一集的 TMDB 单集简介 (回忆剧情起点), 缺失回退整部简介 + bgm.tv 兜底
    Text(
        nextEpisodeOverview
            ?: info.subjectInfo.summary.trim().ifBlank { summaryFallback.orEmpty() },
        Modifier.weight(1f).fillMaxWidth(TV_HERO_SUMMARY_WIDTH_FRACTION),
        color = tvHeroContentColor(),
        style = MaterialTheme.typography.bodyMedium,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 悬浮分类 Tab 行: 透明底, 未选中降透明度, 选中加粗 + 底部平滑滑动的主题色指示条; 聚焦即切换.
 * 数字统计以小号淡色跟在标签后. 按下键把焦点送入下方内容 (主按钮/网格).
 */
@Composable
private fun TvCollectionTabRow(
    selectedType: UnifiedCollectionType,
    counts: (UnifiedCollectionType) -> Int?,
    onSelect: (UnifiedCollectionType) -> Unit,
    tabFocusRequesters: List<FocusRequester>,
    onAnyFocused: () -> Unit,
    onUserNavigation: () -> Unit,
    onNavigateDown: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // 各 tab 在行内的 (x 偏移, 宽度), 驱动下方滑动指示条
    val tabBounds = remember {
        mutableStateListOf(*Array(TV_COLLECTION_TABS.size) { 0.dp to 0.dp })
    }
    // 左右键在标签间显式移动 (方向搜索偶尔会跳去侧边栏, 如从空 tab 标签按左);
    // 第一个标签按左不消费, 交给焦点系统 -> 侧边栏
    var focusedTabIndex by remember { mutableIntStateOf(-1) }
    // "聚焦即选中"只认由本行左右键引发的焦点移动. 页面切换 / 焦点悬空时焦点系统会把默认焦点
    // 塞给第一个标签, 那种情况绝不能改选中项 —— 否则从卡片进详情页再快速返回, 会被拽到第一个
    // tab 的第一张卡 (原卡片已随页面销毁, 焦点悬空).
    var selectByFocusArmed by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(
            Modifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                onUserNavigation()
                when (event.key) {
                    Key.DirectionDown -> onNavigateDown()

                    Key.DirectionLeft ->
                        focusedTabIndex > 0 && run {
                            selectByFocusArmed = true
                            runCatching { tabFocusRequesters[focusedTabIndex - 1].requestFocus() }.isSuccess
                        }

                    Key.DirectionRight -> when (focusedTabIndex) {
                        in 0..<TV_COLLECTION_TABS.size - 1 -> {
                            selectByFocusArmed = true
                            runCatching { tabFocusRequesters[focusedTabIndex + 1].requestFocus() }.isSuccess
                        }

                        // 末标签按右必须消费掉: 不消费就落到默认方向搜索, 而标签行右侧没有
                        // 可聚焦目标, 搜索会退化成按遍历顺序取首个 —— 绕回第一个标签
                        // (表现为按住右键在标签间无限循环)
                        TV_COLLECTION_TABS.size - 1 -> true

                        else -> false
                    }

                    else -> false
                }
            },
            horizontalArrangement = Arrangement.spacedBy(TV_COLLECTION_TAB_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TV_COLLECTION_TABS.forEachIndexed { index, type ->
                val interactionSource = remember { MutableInteractionSource() }
                val focused by interactionSource.collectIsFocusedAsState()
                val selected = type == selectedType
                Row(
                    Modifier
                        .onGloballyPositioned { coords ->
                            tabBounds[index] = with(density) {
                                coords.positionInParent().x.toDp() to coords.size.width.toDp()
                            }
                        }
                        // 无条件挂: 链上元素个数恒定, 选中态变化不会重建其后的焦点节点
                        .focusRequester(tabFocusRequesters[index])
                        .onFocusChanged {
                            if (it.isFocused) {
                                focusedTabIndex = index
                                onAnyFocused()
                                if (selectByFocusArmed) {
                                    selectByFocusArmed = false
                                    onSelect(type)
                                }
                            }
                        }
                        .clickable(interactionSource, indication = null) { onSelect(type) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 聚焦 (即选中) 时主题色示焦; 未选中降透明度
                    val labelColor = when {
                        focused -> MaterialTheme.colorScheme.primary
                        selected -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = TV_COLLECTION_TAB_UNSELECTED_ALPHA)
                    }
                    Text(
                        type.displayTextTv(),
                        color = labelColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                    )
                    counts(type)?.let { count ->
                        Text(
                            count.toString(),
                            color = labelColor.copy(alpha = labelColor.alpha * 0.7f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // 平滑滑动的选中指示条
        val (targetX, targetWidth) = tabBounds[TV_COLLECTION_TABS.indexOf(selectedType).coerceAtLeast(0)]
        TvCollectionTabIndicator(targetX, targetWidth)
    }
}

/**
 * tab 行的选中指示条, 单独成组件: 动画值的组合期读收在这里 —— 切 tab 的几百毫秒里
 * 每帧重组的只有这一个 Box, 不殃及整条 tab 行 (5 个 tab 的文字/计数); X 用 offset
 * 的布局期 lambda 读, 滑动过程连本组件的重组都省掉 (宽度动画仍会重组, 半径 = 1 Box).
 */
@Composable
private fun TvCollectionTabIndicator(
    targetX: Dp,
    targetWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val indicatorX by animateDpAsState(targetX, label = "tabIndicatorX")
    val indicatorWidth by animateDpAsState(targetWidth, label = "tabIndicatorWidth")
    Box(
        modifier
            .padding(top = 4.dp)
            .offset { IntOffset(indicatorX.roundToPx(), 0) }
            .width(indicatorWidth)
            .height(TV_COLLECTION_TAB_INDICATOR_HEIGHT)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun UnifiedCollectionType.displayTextTv(): String {
    return when (this) {
        UnifiedCollectionType.WISH -> stringResource(Lang.subject_collection_wish)
        UnifiedCollectionType.DOING -> stringResource(Lang.subject_collection_doing)
        UnifiedCollectionType.DONE -> stringResource(Lang.subject_collection_done)
        UnifiedCollectionType.ON_HOLD -> stringResource(Lang.subject_collection_on_hold)
        UnifiedCollectionType.DROPPED -> stringResource(Lang.subject_collection_dropped)
        UnifiedCollectionType.NOT_COLLECTED -> stringResource(Lang.subject_collection_uncollected)
    }
}

/**
 * TV 追番页的分类 tab 顺序: 想看 在看 搁置 看过 抛弃. 仅影响本页展示与左右导航次序;
 * [UserCollectionsState] 内部仍按 [COLLECTION_TABS_SORTED] 的下标存取, 使用处经类型换算.
 */
private val TV_COLLECTION_TABS = listOf(
    UnifiedCollectionType.WISH,
    UnifiedCollectionType.DOING,
    UnifiedCollectionType.ON_HOLD,
    UnifiedCollectionType.DONE,
    UnifiedCollectionType.DROPPED,
)

/**
 * 等"改了收藏状态的条目"从当前 tab 列表消失的上限 (毫秒). 需覆盖一次网络往返 + 分页刷新;
 * 超时只是收尾兜底 (把焦点从隐形锚点送走), 正常路径远早于此.
 */
private const val TV_COLLECTION_AWAIT_REMOVAL_TIMEOUT_MILLIS = 5000L

/** 跨 tab 网格滑动过渡时长 (完整动画档). */
private const val TV_COLLECTION_TAB_SLIDE_MILLIS = 560

/** 跨 tab 网格渐隐过渡时长 (降级档). */
private const val TV_COLLECTION_TAB_FADE_MILLIS = 500

/** 空分类提示相对网格区中心的上移量 (网格区偏页面下半, 上移后视觉上接近整页居中). */
private val TV_COLLECTION_EMPTY_HINT_RAISE = 200.dp

/** 内容左侧留白 (外层主壳已让开侧边栏 48dp, 总左缘 = 48 + 此值, 与探索页一致). */
private val TV_COLLECTION_START_PAD = 16.dp

/** 页面顶部留白 (tab 行之上). */
private val TV_COLLECTION_TOP_PAD = 24.dp

/** Tab 之间的间距. */
private val TV_COLLECTION_TAB_SPACING = 28.dp

/** 未选中 Tab 的文字不透明度. */
private const val TV_COLLECTION_TAB_UNSELECTED_ALPHA = 0.5f

/** Tab 选中指示条厚度. */
private val TV_COLLECTION_TAB_INDICATOR_HEIGHT = 3.dp

/** Tab 行到 Hero 信息块 (标题) 的间距. */
private val TV_COLLECTION_TABS_TO_HERO_GAP = 10.dp

/**
 * Hero 信息块固定高度 (标题 + 评分/连载行 + 个人状态行 + 简介). 简介用 weight 填满剩余空间;
 * 固定高度保证切换聚焦条目 (简介长短不同) 时网格不跳动. 调大 = 简介更多行, 网格更矮.
 * 当前取值让简介露出约 3 行 (标题+元信息+状态行 ≈ 108dp, 简介每行 ≈ 20dp).
 */
private val TV_COLLECTION_HERO_INFO_HEIGHT = 240.dp

/** Hero 信息块 (简介底部) 到网格的间距. */
private val TV_COLLECTION_HERO_TO_GRID_GAP = 8.dp
