/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.CanonicalTagKind
import me.him188.ani.app.data.network.BangumiSummaryService
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.repository.subject.SetSubjectCollectionTypeOrDeleteUseCase
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.search.RatingRange
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.consumeHeldConfirmKey
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.session.TvNavigationRailDefaults
import me.him188.ani.app.ui.foundation.session.TvNavigationSideRail
import me.him188.ani.app.ui.foundation.session.buildTvRailItems
import me.him188.ani.app.ui.foundation.focus.GridFocusController
import me.him188.ani.app.ui.foundation.focus.TvScrollAnimator
import me.him188.ani.app.ui.foundation.tv.TvPageBackdropLayer
import me.him188.ani.app.ui.foundation.tv.TvPortraitCard
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
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.foundation.tv.tvHeroSecondaryContentColor
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_search_filter_audience
import me.him188.ani.app.ui.lang.exploration_search_filter_category
import me.him188.ani.app.ui.lang.exploration_search_filter_character
import me.him188.ani.app.ui.lang.exploration_search_filter_custom
import me.him188.ani.app.ui.lang.exploration_search_filter_emotion
import me.him188.ani.app.ui.lang.exploration_search_filter_genre
import me.him188.ani.app.ui.lang.exploration_search_filter_rating
import me.him188.ani.app.ui.lang.exploration_search_filter_region
import me.him188.ani.app.ui.lang.exploration_search_filter_series
import me.him188.ani.app.ui.lang.exploration_search_filter_setting
import me.him188.ani.app.ui.lang.exploration_search_filter_source
import me.him188.ani.app.ui.lang.exploration_search_filter_technology
import me.him188.ani.app.ui.lang.exploration_search_sort_collection
import me.him188.ani.app.ui.lang.exploration_search_sort_date
import me.him188.ani.app.ui.lang.exploration_search_sort_match
import me.him188.ani.app.ui.lang.exploration_search_sort_rank
import me.him188.ani.app.ui.lang.search_tv_empty
import me.him188.ani.app.ui.lang.search_tv_filter
import me.him188.ani.app.ui.lang.search_tv_filter_any
import me.him188.ani.app.ui.lang.search_tv_filter_confirm
import me.him188.ani.app.ui.lang.search_tv_filter_rating_min
import me.him188.ani.app.ui.lang.search_tv_filter_sort
import me.him188.ani.app.ui.lang.search_tv_input_hint
import me.him188.ani.app.ui.lang.search_tv_remote_hint
import me.him188.ani.app.ui.lang.search_tv_results_all
import me.him188.ani.app.ui.lang.search_tv_results_title
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.collectItemsWithLifecycle
import me.him188.ani.app.ui.search.isLoadingFirstPageOrRefreshing
import me.him188.ani.app.ui.subject.collection.components.EditCollectionTypeDropDown
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.stringResource

/**
 * TV 沉浸式搜索页 (交互参考 Crunchyroll TV 搜索 + 本 fork 的沉浸式追番页):
 * 两个形态渐隐切换 ——
 * - 输入态: 顶部居中一个搜索框 (聚焦自动弹系统键盘), 下方候选列表 (空文本 = 搜索历史,
 *   有文本 = 补全建议); 确认提交后整个输入 UI 消失;
 * - 结果态: 全屏沉浸展示 (骨架同追番页): 顶部为搜索词 + 筛选按钮, Hero 区显示聚焦条目的
 *   标题/评分/元信息/简介, TMDB backdrop 渐隐背景, 下方 2:3 海报网格 (行吸顶/播放键直达).
 *
 * 返回分层: 网格非首卡 -> 回首卡; 结果态其余位置 -> 回输入态 (保留文字与光标, 自动弹键盘);
 * 输入态 -> 退出搜索页. 进详情/播放返回本页恢复焦点到原卡片.
 */
@Composable
fun TvSearchPage(
    state: SearchPageState,
    onIntent: (SearchPageIntent) -> Unit,
    suggestionsPager: (String) -> Flow<PagingData<String>>,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    // 输入框内容与光标位置 (跨形态与跨导航保留: 从结果态返回可原样继续编辑)
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.query.keywords, TextRange(state.query.keywords.length)))
    }
    // 进页时若带初始查询 (如详情页点标签跳转) 直接落在结果态
    var showResults by rememberSaveable { mutableStateOf(state.query.hasSearchRequest()) }
    // 带初始查询进入时, 结果态按返回直接退出本页 (回详情页), 想改词要点顶部搜索词文字;
    // 用户在本页手动提交过搜索后恢复"返回回输入态"
    var backGoesToInput by rememberSaveable { mutableStateOf(!state.query.hasSearchRequest()) }
    // 网格滚动与最后聚焦卡片下标提到页面级: 跨形态切换与跨导航 (进详情返回) 都要保留.
    // 传 State 而非取值: 结果面板里的协程 (落点轮询/吸顶 snapshotFlow) 要能观察到实时变化
    val gridState = rememberLazyGridState()
    val lastFocusedCard = rememberSaveable { mutableIntStateOf(-1) }
    // 进页那一刻的恢复目标快照 (从详情/播放器返回时恢复焦点); 只在结果态首次组合时消费一次
    val restoreCardIndex = remember { lastFocusedCard.intValue }
    var restoreConsumed by remember { mutableStateOf(false) }

    val submit: (String) -> Unit = submit@{ text ->
        val newQuery = state.query.copy(keywords = text).normalized()
        if (!newQuery.hasSearchRequest()) return@submit // 空关键词且无筛选: 不发起搜索
        query = TextFieldValue(text, TextRange(text.length))
        keyboard?.hide()
        onIntent(SearchPageIntent.UpdateQuery(newQuery, submit = true))
        lastFocusedCard.intValue = -1
        backGoesToInput = true
        showResults = true
    }

    // 内容区焦点入口: 从页面外进来的焦点 (导航兜底的无方向 enter) 一律先送进内容区而非侧边栏
    // (同主页外壳 TvMainScreenLayout 的做法); 侧边栏靠内容区里按左键进入
    val contentFocus = remember { FocusRequester() }
    // 侧边栏右键/返回退出时的焦点还原: 结果面板在此注册"回上次聚焦卡片"的处理 (走带
    // 到位确认+重试的落点解析器). 未注册 (输入态) 或没有可回的卡时退回 contentFocus
    // 进组默认落点. 不还原会落到左上角搜索词文字上, 且直连 requestFocus 偶发被焦点系统
    // 静默拒绝时会看起来"按下键没反应"
    val railExitRestore = remember { mutableStateOf<(() -> Boolean)?>(null) }
    val navigator = LocalNavigator.current
    Box(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .focusProperties { onEnter = { contentFocus.requestFocus() } }
            .focusGroup(),
    ) {
        Box(
            Modifier.fillMaxSize()
                // 让开左缘侧边栏, 使本页内容左边界与探索/追番页一致
                .padding(start = TvNavigationRailDefaults.CollapsedWidth)
                .focusRequester(contentFocus)
                .focusGroup(),
        ) {
            AnimatedContent(
                targetState = showResults,
                transitionSpec = {
                    fadeIn(tween(TV_SEARCH_MODE_FADE_MILLIS)) togetherWith
                            fadeOut(tween(TV_SEARCH_MODE_FADE_MILLIS))
                },
                label = "searchMode",
            ) { results ->
                if (results) {
                    TvSearchResultsPane(
                        state = state,
                        onIntent = onIntent,
                        gridState = gridState,
                        lastFocusedCard = lastFocusedCard,
                        restoreCardIndex = if (restoreConsumed) -1 else restoreCardIndex,
                        onRestoreConsumed = { restoreConsumed = true },
                        onBackToInput = { showResults = false },
                        backGoesToInput = backGoesToInput,
                        railExitRestore = railExitRestore,
                    )
                } else {
                    TvSearchInputPane(
                        query = query,
                        onQueryChange = { query = it },
                        historyPager = state.searchHistoryPager,
                        suggestionsPager = suggestionsPager,
                        onSubmit = submit,
                    )
                }
            }
        }
        // 左缘 overlay 侧边栏: 与主页/详情页完全同一实现. 本页不显示头像信息 (selfInfo = null,
        // 保留槽位使其余按钮位置不变); "搜索"项 = 回输入态改词
        TvNavigationSideRail(
            selfInfo = null,
            onAvatarClick = {},
            onExitFocus = {
                // 结果态优先回上次聚焦的卡片 (与进页恢复一致), 其余情况进内容区默认落点
                if (railExitRestore.value?.invoke() != true) {
                    runCatching { contentFocus.requestFocus() }
                }
            },
            items = buildTvRailItems(
                onSearch = { showResults = false },
                onNavigateToPage = { navigator.popBackOrNavigateToMain(it) },
                onSettings = { navigator.navigateSettings() },
            ),
            modifier = Modifier.fillMaxHeight(),
        )
    }
}

// ============================ 输入态 ============================

@Composable
private fun TvSearchInputPane(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    historyPager: Flow<PagingData<String>>,
    suggestionsPager: (String) -> Flow<PagingData<String>>,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var fieldFocused by remember { mutableStateOf(false) }

    // 进入输入态自动聚焦输入框并弹键盘 (返回本形态时可直接改内容); 轮询等挂载
    LaunchedEffect(Unit) {
        if (resolveFocusRepeatedly(attempts = 20, arrived = { fieldFocused }) {
                runCatching { fieldFocusRequester.requestFocus() }
            }
        ) {
            keyboard?.show()
        }
    }

    Column(
        modifier.fillMaxSize().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(TV_SEARCH_INPUT_TOP_PAD))
        // 搜索框 (参考 Crunchyroll: 顶部居中单框; 聚焦高亮描边)
        Surface(
            Modifier.fillMaxWidth(TV_SEARCH_INPUT_WIDTH_FRACTION)
                .ifThen(fieldFocused) {
                    border(
                        2.5.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(TV_SEARCH_INPUT_CORNER),
                    )
                },
            shape = RoundedCornerShape(TV_SEARCH_INPUT_CORNER),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val textStyle = MaterialTheme.typography.titleMedium
                BasicTextField(
                    value = query,
                    onValueChange = { onQueryChange(it.copy(text = it.text.trim('\n'))) },
                    modifier = Modifier.weight(1f)
                        .focusRequester(fieldFocusRequester)
                        .onFocusChanged { fieldFocused = it.isFocused },
                    textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit(query.text) }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.text.isEmpty()) {
                                Text(
                                    stringResource(Lang.search_tv_input_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = textStyle,
                                    maxLines = 1,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }

        // 候选列表: 空文本显示搜索历史, 有文本显示防抖后的补全建议; 确认即提交
        var debounced by remember { mutableStateOf(query.text) }
        LaunchedEffect(query.text) {
            delay(TV_SEARCH_SUGGESTION_DEBOUNCE_MILLIS)
            debounced = query.text
        }
        val isHistory = debounced.isEmpty()
        val values = remember(debounced) {
            if (debounced.isEmpty()) historyPager else suggestionsPager(debounced)
        }.collectAsLazyPagingItemsWithLifecycle()
        LazyColumn(
            Modifier.fillMaxWidth(TV_SEARCH_INPUT_WIDTH_FRACTION)
                .padding(top = 12.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(
                count = values.itemCount,
                key = values.itemKey { "tv-search-suggestion-$it" },
                contentType = values.itemContentType { 1 },
            ) { index ->
                val text = values[index] ?: return@items
                TvSearchSuggestionRow(
                    text = text,
                    isHistory = isHistory,
                    onClick = { onSubmit(text) },
                )
            }
        }
    }
}

@Composable
private fun TvSearchSuggestionRow(
    text: String,
    isHistory: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (focused) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        interactionSource = interactionSource,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (isHistory) Icons.Default.History else Icons.Default.Search,
                contentDescription = null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ============================ 结果态 ============================

@Composable
private fun TvSearchResultsPane(
    state: SearchPageState,
    onIntent: (SearchPageIntent) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    lastFocusedCard: MutableIntState,
    restoreCardIndex: Int,
    onRestoreConsumed: () -> Unit,
    onBackToInput: () -> Unit,
    backGoesToInput: Boolean,
    /** 侧边栏右键退出时的焦点还原注册槽 (见页面级声明); 本面板在位时写入, 离场清空. */
    railExitRestore: MutableState<(() -> Boolean)?>,
    modifier: Modifier = Modifier,
) {
    val tmdb = remember { GlobalKoin.get<TmdbImageService>() }
    val bangumiSummaryService = remember { GlobalKoin.get<BangumiSummaryService>() }
    val collectionRepo = remember { GlobalKoin.get<SubjectCollectionRepository>() }
    val setCollectionTypeUseCase = remember { GlobalKoin.get<SetSubjectCollectionTypeOrDeleteUseCase>() }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val items = state.searchState.collectItemsWithLifecycle()

    // Hero 数据源: 聚焦卡片驱动; 默认当前列表第一项, 列表确认为空才清
    var heroItem by remember { mutableStateOf<SubjectPreviewItemInfo?>(null) }
    LaunchedEffect(items.itemCount > 0, items.isLoadingFirstPageOrRefreshing) {
        val cur = heroItem
        if (items.itemCount > 0) {
            if (cur == null || items.itemSnapshotList.items.none { it.subjectId == cur.subjectId }) {
                heroItem = runCatching { items.peek(0) }.getOrNull()
            }
        } else if (!items.isLoadingFirstPageOrRefreshing) {
            heroItem = null
        }
    }

    // subjectId -> TMDB backdrop URL (null = 已查过没有); 搜索结果没有 summary 字段,
    // 简介一律按聚焦条目异步向 bgm.tv 取 ("" = 查过没有); 网络错误都不写缓存, 下次聚焦重试.
    // 收藏状态供长按菜单高亮当前项, 聚焦时顺带拉取, 菜单操作成功后本地覆盖
    val backdropCache = remember { mutableStateMapOf<Int, String?>() }
    val summaryCache = remember { mutableStateMapOf<Int, String>() }
    val collectionTypeCache = remember { mutableStateMapOf<Int, UnifiedCollectionType>() }
    LaunchedEffect(items) {
        snapshotFlow { heroItem }.filterNotNull().collectLatest { info ->
            delay(TV_HERO_MEDIA_DEBOUNCE_MILLIS) // 防抖: 网格快速划过时不发请求
            if (info.subjectId !in backdropCache) {
                runCatching {
                    // 官方主背景图 (与详情页 hero 同源, 进详情零跳变).
                    // 必须用原名 (日文) 匹配 TMDB: 中文译名命中率低, 且失败会写持久负缓存
                    tmdb.getBackdropUrl(
                        info.subjectId,
                        info.originalName.ifBlank { info.title },
                    )
                }.onSuccess { url -> backdropCache[info.subjectId] = url }
            }
            if (info.subjectId !in summaryCache) {
                runCatching { bangumiSummaryService.getSummary(info.subjectId) }
                    .onSuccess { summaryCache[info.subjectId] = it.orEmpty() }
            }
            if (info.subjectId !in collectionTypeCache) {
                runCatching { collectionRepo.subjectCollectionFlow(info.subjectId).first() }
                    .onSuccess { collectionTypeCache[info.subjectId] = it.collectionType }
            }
        }
    }

    // 卡片长按弹出的收藏下拉 (与探索页/追番页一致); 打开后短暂吞掉长按残余的确认键, 避免误触第一项.
    // remember: 工厂被网格 items 内容 lambda 捕获, 每次新实例都会让所有可见卡片跟着重组
    val collectionMenuFor: (Int) -> @Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit = remember {
        { subjectId ->
            { expanded, onDismiss ->
                EditCollectionTypeDropDown(
                    currentType = collectionTypeCache[subjectId] ?: UnifiedCollectionType.NOT_COLLECTED,
                    expanded = expanded,
                    onDismissRequest = onDismiss,
                    onClick = { action ->
                        scope.launch {
                            runCatching { setCollectionTypeUseCase(subjectId, action.type) }
                                .onSuccess { collectionTypeCache[subjectId] = action.type }
                                .onFailure { toaster.showLoadError(LoadError.fromException(it)) }
                        }
                    },
                    // 卡片的菜单只有长按一个入口, 恒吞掉那次长按残余的确认键
                    modifier = Modifier.consumeHeldConfirmKey(),
                )
            }
        }
    }

    // 焦点动线锚点与网格落点解析 (与追番页共用 [GridFocusController] 落点机制:
    // 轮询等滚动/组合完成再聚焦, 卡片 onFocused 确认到位)
    val titleFocusRequester = remember { FocusRequester() }
    val errorCardFocusRequester = remember { FocusRequester() }
    val gridFocus = remember { GridFocusController() }
    var gridHasFocus by remember { mutableStateOf(false) }
    var gridColumns by remember { mutableIntStateOf(1) }

    LaunchedEffect(items) {
        gridFocus.runResolveLoop(
            gridState = gridState,
            columns = { gridColumns },
            itemCount = { items.itemCount },
            attempts = 40,
        )
    }

    // 侧边栏右键退出 → 回上次聚焦的卡片 (与进页恢复一致), 而不是空间焦点搜索/进组默认
    // 落到左上角搜索词文字上. 走上面的落点解析器: 聚焦到位确认 + 重试, 直连 requestFocus
    // 偶发被焦点系统静默拒绝时不至于永久卡死
    DisposableEffect(Unit) {
        railExitRestore.value = restore@{
            val count = items.itemCount
            val last = lastFocusedCard.intValue
            if (count <= 0 || last < 0) return@restore false
            gridFocus.request(minOf(last, count - 1))
            true
        }
        onDispose { railExitRestore.value = null }
    }

    // 初始焦点: 返回本页恢复到此前聚焦的卡片, 新搜索聚焦第一张; 结果没到先等 (加载失败聚焦错误卡)
    LaunchedEffect(Unit) {
        val target = if (restoreCardIndex >= 0) restoreCardIndex else 0
        onRestoreConsumed()
        // 纯等数据: attempt 为空, 只轮询到"有结果或出错"再一次性分派落点
        resolveFocusRepeatedly(
            attempts = 100, delayMillis = 50,
            arrived = { items.itemCount > 0 || items.loadState.hasError },
        ) {}
        when {
            items.itemCount > 0 -> gridFocus.request(target)
            items.loadState.hasError -> runCatching { errorCardFocusRequester.requestFocus() }
        }
    }

    // 已选筛选项 (标签 / 最低评分 / 非默认排序): 顶部行下方一行胶囊, 点击取消该项.
    // remember: 列表被网格 items 内容 lambda 间接捕获 (见 onNavigateDown), 只在查询变化时重建
    val currentSortLabel = tvSearchSortLabel(state.query.sort)
    val activeFilters = remember(state.query, currentSortLabel) {
        buildList {
            state.query.tags.orEmpty().forEach { tag ->
                add(tag to state.query.copy(tags = (state.query.tags.orEmpty() - tag).ifEmpty { null }))
            }
            state.query.rating?.min?.let { min ->
                add("≥$min" to state.query.copy(rating = null))
            }
            if (state.query.sort != SearchSort.MATCH) {
                add(currentSortLabel to state.query.copy(sort = SearchSort.MATCH))
            }
        }
    }
    val chipsFocusRequester = remember { FocusRequester() }

    // 从上方 (顶部行/筛选行) 把焦点送进网格: 主走落点解析器聚焦当前视口首行行首 (到位
    // 确认 + 重试; 此前首选"直连首卡 requestFocus", 偶发被焦点系统静默拒绝时 runCatching
    // 照样报成功, 下键被吞且不再重试, 表现为卡在顶部行下不去); 网格空时退到错误横幅
    val focusGridFromAbove: () -> Boolean = {
        val firstVisible = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
        if (firstVisible != null) {
            gridFocus.request((firstVisible / gridColumns) * gridColumns)
            true
        } else {
            runCatching { errorCardFocusRequester.requestFocus() }.isSuccess
        }
    }

    // 返回分层: 网格非首卡 -> 回首卡; 其余 (首卡/顶部行) -> 回输入态改词.
    // 点标签深链进入 (backGoesToInput=false) 时后者不拦截, 返回直接退出本页回详情页.
    // derivedStateOf: 条件里的焦点下标每移一格都变, 直接读会让整个结果面板每格重组,
    // 收窄成布尔后只在 首卡<->非首卡 边界变化时才失效
    val backToFirstCard by remember {
        derivedStateOf { gridHasFocus && lastFocusedCard.intValue > 0 }
    }
    BackHandler(enabled = backToFirstCard) {
        gridFocus.request(0)
    }
    BackHandler(enabled = backGoesToInput && !backToFirstCard) {
        onBackToInput()
    }

    var showFilterDialog by remember { mutableStateOf(false) }
    if (showFilterDialog) {
        TvSearchFilterDialog(
            query = state.query,
            filterState = state.searchFilterState,
            onConfirm = { newQuery ->
                showFilterDialog = false
                onIntent(SearchPageIntent.UpdateQuery(newQuery, submit = true))
            },
            onDismiss = { showFilterDialog = false },
        )
    }

    Box(modifier.fillMaxSize()) {
        // 背景 backdrop 层: 同追番页 (16:9 贴右上角, 恒用卡片态渐变).
        // URL 用 lambda 传入: 聚焦条目状态在组件内部才读取, 换卡只重组这一小块
        TvPageBackdropLayer(
            backdropUrl = { heroItem?.let { backdropCache[it.subjectId] } },
            // 本页是独立页面, 图层正下方是页面根 Box 自铺的 colorScheme.background
            fadeColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopEnd),
        )

        Column(
            Modifier.fillMaxSize()
                .padding(start = TV_SEARCH_START_PAD, top = TV_SEARCH_TOP_PAD),
        ) {
            // 顶部行: 搜索词 (确认回输入态改词) + 筛选按钮
            TvSearchTopRow(
                keywords = state.query.keywords,
                hasFilters = state.query.hasFilters(),
                titleFocusRequester = titleFocusRequester,
                onEditQuery = onBackToInput,
                onOpenFilter = { showFilterDialog = true },
                onNavigateDown = {
                    // 有已选筛选项时先落到筛选行, 否则直接进网格
                    (activeFilters.isNotEmpty() &&
                            runCatching { chipsFocusRequester.requestFocus() }.isSuccess) ||
                            focusGridFromAbove()
                },
            )

            // 已选筛选项行 (点击取消; 超宽时吸左滚动): 占固定高度块 (上间距 + 行高 = 简介
            // 两行行距 40dp), 出现时下方 hero 信息块等量压缩 —— 简介少两行, 网格位置不动
            if (activeFilters.isNotEmpty()) {
                TvSearchActiveFiltersRow(
                    filters = activeFilters,
                    onRemove = { newQuery ->
                        onIntent(SearchPageIntent.UpdateQuery(newQuery, submit = true))
                    },
                    entryFocusRequester = chipsFocusRequester,
                    onNavigateUp = { runCatching { titleFocusRequester.requestFocus() }.isSuccess },
                    onNavigateDown = focusGridFromAbove,
                    onEmptied = { runCatching { titleFocusRequester.requestFocus() } },
                    modifier = Modifier.padding(top = TV_SEARCH_FILTERS_TOP_GAP)
                        .height(TV_SEARCH_FILTERS_ROW_HEIGHT),
                )
            }

            // Hero 信息块 (固定高度; 换条目整块文字渐隐渐现). 聚焦条目状态在子组件内部
            // 才读取, 遥控器换卡只重组信息块自身, 不连带整个结果面板
            TvSearchHeroInfoBlock(
                heroItemProvider = { heroItem },
                summaryCache = summaryCache,
                // end 留白与探索页 hero 块一致, 否则 fillMaxWidth(比例) 的基数比其他页宽.
                // 有筛选行时等量压缩高度, 保持网格位置不变
                modifier = Modifier.fillMaxWidth()
                    .padding(top = TV_SEARCH_TITLE_TO_HERO_GAP, end = TV_PAGE_END_PAD)
                    .height(
                        if (activeFilters.isEmpty()) TV_SEARCH_HERO_INFO_HEIGHT
                        else TV_SEARCH_HERO_INFO_HEIGHT - TV_SEARCH_FILTERS_TOP_GAP - TV_SEARCH_FILTERS_ROW_HEIGHT,
                    ),
            )

            // 竖版海报网格
            if (items.loadState.hasError) {
                LoadErrorCard(
                    LoadError.fromCombinedLoadStates(items.loadState),
                    onRetry = { items.refresh() },
                    Modifier.padding(top = TV_SEARCH_HERO_TO_GRID_GAP, end = TV_PAGE_END_PAD)
                        .focusRequester(errorCardFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                runCatching { titleFocusRequester.requestFocus() }.isSuccess
                            } else {
                                false
                            }
                        },
                )
            }
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth()
                    .padding(top = TV_SEARCH_HERO_TO_GRID_GAP)
                    .onFocusChanged { gridHasFocus = it.hasFocus },
            ) {
                // 复刻 GridCells.Adaptive 的列数算法 (整数 px 运算), 供行列换算
                val density = LocalDensity.current
                gridColumns = with(density) {
                    val available = (this@BoxWithConstraints.maxWidth - TV_PAGE_END_PAD).roundToPx()
                    val spacing = TV_PAGE_CARD_SPACING.roundToPx()
                    maxOf(1, (available + spacing) / (TV_PAGE_CARD_WIDTH.roundToPx() + spacing))
                }
                // 底部补白 = 视口高 - 一行卡高: 让最后一行也能吸到网格顶部
                // (内容不足一屏时 animateScrollToItem 滚不动, 接近底部的行会失去吸顶)
                val gridBottomPad = run {
                    val available = this@BoxWithConstraints.maxWidth - TV_PAGE_END_PAD
                    val cardWidth = (available - TV_PAGE_CARD_SPACING * (gridColumns - 1)) / gridColumns
                    val cardHeight = cardWidth / TV_PORTRAIT_CARD_COVER_RATIO
                    (this@BoxWithConstraints.maxHeight - cardHeight).coerceAtLeast(24.dp)
                }
                // 聚焦行吸顶 (同追番页): 关闭默认"刚好露出"式自动滚动, 聚焦行滚到网格顶部
                val noBringIntoView = remember {
                    object : BringIntoViewSpec {
                        override fun calculateScrollDistance(
                            offset: Float,
                            size: Float,
                            containerSize: Float,
                        ): Float = 0f
                    }
                }
                LaunchedEffect(gridState) {
                    // collectLatest + TvScrollAnimator: 连发按键取消进行中的滚动并继承速度,
                    // 列表连续流动 (原 collect 要等上一格动画跑完才响应下一个目标)
                    val scrollAnimator = TvScrollAnimator()
                    snapshotFlow { lastFocusedCard.intValue }.collectLatest { focused ->
                        if (focused >= 0) {
                            runCatching {
                                scrollAnimator.animateScrollToItem(gridState, (focused / gridColumns) * gridColumns)
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalBringIntoViewSpec provides noBringIntoView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(TV_PAGE_CARD_WIDTH),
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            // 同列上下导航 + 播放键直达 (与追番页共享实现, 理由见 [gridKeyNavigation])
                            .gridKeyNavigation(
                                gridFocus,
                                focusedIndex = { lastFocusedCard.intValue },
                                itemCount = { items.itemCount },
                                columns = { gridColumns },
                                // 顶行上键: 有筛选行先回筛选行, 否则回顶部行搜索词
                                onTopRowUp = {
                                    (activeFilters.isNotEmpty() &&
                                            runCatching { chipsFocusRequester.requestFocus() }.isSuccess) ||
                                            runCatching { titleFocusRequester.requestFocus() }.isSuccess
                                },
                                // 播放键: 聚焦卡直接进播放器 (右下角有提示)
                                onPlayKey = { focused ->
                                    val info = runCatching { items.peek(focused) }.getOrNull()
                                    if (info != null) {
                                        onIntent(SearchPageIntent.Play(info))
                                        true
                                    } else {
                                        false
                                    }
                                },
                            ),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(TV_PAGE_CARD_SPACING),
                        verticalArrangement = Arrangement.spacedBy(TV_PAGE_CARD_SPACING),
                        contentPadding = PaddingValues(end = TV_PAGE_END_PAD, bottom = gridBottomPad),
                    ) {
                        items(
                            count = items.itemCount,
                            // 搜索分页可能跨页返回重复条目, key 必须掺入 index (同原搜索页做法),
                            // 只用 subjectId 会因重复 key 直接崩溃
                            key = { index ->
                                val item = items.peek(index)
                                if (item == null) {
                                    "TvSearchPage-placeholder-$index"
                                } else {
                                    "TvSearchPage-$index-${item.subjectId}"
                                }
                            },
                        ) { index ->
                            val info = items[index]
                            // derivedStateOf: 落点下标每次上下键导航都会 设置->清除 变两次,
                            // 直接读会让所有可见卡片每次导航重组两遍; 收窄成布尔后只有
                            // 目标卡自己 (挂/摘请求器) 重组
                            val isGridTarget by remember(index) {
                                derivedStateOf { gridFocus.resolvedIndex == index }
                            }
                            TvPortraitCard(
                                // NSFW 模糊模式的条目不显示封面 (占位图), 隐藏条目同理
                                imageUrl = info?.takeIf { it.nsfwMode != NsfwMode.BLUR && !it.hide }?.imageUrl,
                                contentDescription = info?.title,
                                onClick = {
                                    info?.let { onIntent(SearchPageIntent.OpenSubjectDetails(index, it)) }
                                },
                                onFocused = {
                                    info?.let { heroItem = it }
                                    lastFocusedCard.intValue = index
                                    gridFocus.onCardFocused(index)
                                },
                                modifier = Modifier
                                    .ifThen(isGridTarget) {
                                        focusRequester(gridFocus.requester)
                                    },
                                menu = info?.let { collectionMenuFor(it.subjectId) },
                            )
                        }
                    }
                }
                // 空结果提示 / 首屏加载指示
                if (items.itemCount == 0 && !items.loadState.hasError) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (items.isLoadingFirstPageOrRefreshing) {
                            CircularProgressIndicator()
                        } else {
                            Text(
                                stringResource(Lang.search_tv_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }

        // 底缘弱渐变遮罩: 轻压被视口截断的下一行卡片, 保证右下角提示可读
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

        // 右下角遥控键提示
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
                stringResource(Lang.search_tv_remote_hint),
                color = tvHeroSecondaryContentColor(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** 顶部行: 搜索词 (可聚焦, 确认回输入态) + 筛选圆钮 (有筛选生效时角标小圆点). */
/**
 * Hero 信息块 (标题 + 评分/元信息行 + 简介): 换条目整块文字渐隐渐现 (contentKey=条目).
 * [heroItemProvider] 用 lambda 传入: 聚焦条目状态在本组件内部才读取, 遥控器每移一格
 * 只重组这一块, 不连带整个结果面板作用域.
 */
@Composable
private fun TvSearchHeroInfoBlock(
    heroItemProvider: () -> SubjectPreviewItemInfo?,
    summaryCache: Map<Int, String>,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = heroItemProvider(),
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(TV_HERO_TEXT_FADE_MILLIS)) togetherWith
                    fadeOut(tween(TV_HERO_TEXT_FADE_MILLIS))
        },
        contentKey = { it?.subjectId },
        label = "searchHeroInfo",
    ) { hero ->
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hero != null) {
                Text(
                    hero.title,
                    Modifier.fillMaxWidth(TV_HERO_TITLE_WIDTH_FRACTION),
                    color = tvHeroContentColor(),
                    style = MaterialTheme.typography.headlineLarge,
                    // 超长换行, 至多两行 (与探索页/追番页统一); 简介 weight 自动让出空间
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val score = hero.rating.score
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
                    // 元信息行 (开播季度 · 话数 · 类型标签, 见 SubjectPreviewItemInfo.compute)
                    Text(
                        hero.tags,
                        color = tvHeroSecondaryContentColor(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    summaryCache[hero.subjectId].orEmpty(),
                    Modifier.weight(1f).fillMaxWidth(TV_HERO_SUMMARY_WIDTH_FRACTION),
                    color = tvHeroContentColor(),
                    style = MaterialTheme.typography.bodyMedium,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TvSearchTopRow(
    keywords: String,
    hasFilters: Boolean,
    titleFocusRequester: FocusRequester,
    onEditQuery: () -> Unit,
    onOpenFilter: () -> Unit,
    onNavigateDown: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    // 关掉 48dp 最小交互尺寸 (TV 无触摸), 搜索词/筛选钮按真实内容高度排
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    Row(
        modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                onNavigateDown()
            } else {
                false
            }
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 搜索词: 聚焦时填充主题色圆角块. 只换字色在沉浸背景 (hero 大图) 上几乎看不出来,
        // 需要一个有面积的形状; 未聚焦时底透明, 不占视觉重量.
        run {
            val interactionSource = remember { MutableInteractionSource() }
            val focused by interactionSource.collectIsFocusedAsState()
            val color = if (focused) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Surface(
                onClick = onEditQuery,
                modifier = Modifier.focusRequester(titleFocusRequester),
                shape = RoundedCornerShape(8.dp),
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                interactionSource = interactionSource,
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        Modifier.size(20.dp),
                        tint = color,
                    )
                    Text(
                        if (keywords.isBlank()) {
                            stringResource(Lang.search_tv_results_all)
                        } else {
                            stringResource(Lang.search_tv_results_title, keywords)
                        },
                        color = color,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // 筛选圆钮
        run {
            val interactionSource = remember { MutableInteractionSource() }
            val focused by interactionSource.collectIsFocusedAsState()
            Box {
                Surface(
                    onClick = onOpenFilter,
                    shape = CircleShape,
                    // 常态不画圆底 (与搜索词一致, 只留图标), 聚焦时才填充主题色示焦
                    color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    interactionSource = interactionSource,
                ) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = stringResource(Lang.search_tv_filter),
                        Modifier.padding(7.dp).size(18.dp),
                        tint = if (focused) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                if (hasFilters) {
                    Box(
                        Modifier.align(Alignment.TopEnd)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
    }
}

/**
 * 已选筛选项行: 每项一个胶囊 (文字 + ✕), 点击取消该筛选并立即刷新结果.
 * 超宽时吸左滚动: 聚焦项滚到最左, 列表末端由 LazyRow 自然钳制 (露出最后一项即不再滚);
 * 全部放得下时滚不动, 表现为正常全显示 + 自由导航.
 */
@Composable
private fun TvSearchActiveFiltersRow(
    filters: List<Pair<String, SubjectSearchQuery>>,
    onRemove: (SubjectSearchQuery) -> Unit,
    entryFocusRequester: FocusRequester,
    onNavigateUp: () -> Boolean,
    onNavigateDown: () -> Boolean,
    onEmptied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var focusedChip by remember { mutableIntStateOf(-1) }
    // 移除一项后原聚焦胶囊销毁, 焦点悬空: 记住移除位置, 重组后聚焦相邻项; 删光交回上方标题
    var refocusAfterRemove by remember { mutableIntStateOf(-1) }
    val refocusRequester = remember { FocusRequester() }
    LaunchedEffect(listState) {
        snapshotFlow { focusedChip }.collect { chip ->
            if (chip >= 0) runCatching { listState.animateScrollToItem(chip) }
        }
    }
    LaunchedEffect(filters.size) {
        if (refocusAfterRemove < 0) return@LaunchedEffect
        if (filters.isEmpty()) {
            refocusAfterRemove = -1
            onEmptied()
            return@LaunchedEffect
        }
        // 胶囊是常驻焦点目标, 请求器一附着 (不抛异常) 即视为到位
        var requested = false
        resolveFocusRepeatedly(attempts = 20, arrived = { requested }) {
            runCatching { refocusRequester.requestFocus() }.onSuccess { requested = true }
        }
        refocusAfterRemove = -1
    }
    val noBringIntoView = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float = 0f
        }
    }
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides noBringIntoView,
        // 关掉 M3 可点击组件的 48dp 最小交互尺寸: TV 无触摸, 胶囊按真实内容高度排, 行才紧凑
        LocalMinimumInteractiveComponentSize provides 0.dp,
    ) {
        LazyRow(
            modifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> onNavigateUp()
                    Key.DirectionDown -> onNavigateDown()
                    else -> false
                }
            },
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(
                count = filters.size,
                key = { filters[it].first },
            ) { index ->
                val (label, removedQuery) = filters[index]
                val refocusIndex =
                    if (refocusAfterRemove >= 0) minOf(refocusAfterRemove, filters.lastIndex) else -1
                TvSearchActiveFilterChip(
                    label = label,
                    onClick = {
                        refocusAfterRemove = index
                        onRemove(removedQuery)
                    },
                    onFocused = { focusedChip = index },
                    modifier = Modifier
                        .ifThen(index == 0) { focusRequester(entryFocusRequester) }
                        .ifThen(index == refocusIndex) { focusRequester(refocusRequester) },
                )
            }
        }
    }
}

@Composable
private fun TvSearchActiveFilterChip(
    label: String,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val container = if (focused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (focused) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = CircleShape,
        color = container,
        interactionSource = interactionSource,
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                label,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Icon(
                Icons.Rounded.Close,
                contentDescription = null,
                Modifier.size(14.dp),
                tint = content,
            )
        }
    }
}

// ============================ 筛选面板 ============================

/**
 * 筛选弹窗: 排序 / 最低评分 / 各标签维度的胶囊选项. 改动先存本地, 确认才应用
 * (避免每碰一个选项就触发一次搜索), 取消/返回丢弃.
 */
@Composable
private fun TvSearchFilterDialog(
    query: SubjectSearchQuery,
    filterState: SearchFilterState,
    onConfirm: (SubjectSearchQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedTags = remember { mutableStateMapOf<String, Boolean>().apply { query.tags.orEmpty().forEach { put(it, true) } } }
    var sort by remember { mutableStateOf(query.sort) }
    var minRating by remember { mutableStateOf(query.rating?.min) }
    val firstChipFocusRequester = remember { FocusRequester() }

    // 弹窗打开自动聚焦第一个选项 (Dialog 独立焦点域, 不聚焦则方向键无处可去);
    // 首个 chip 常驻组合, 请求器一附着 (不抛异常) 即视为到位
    LaunchedEffect(Unit) {
        var requested = false
        resolveFocusRepeatedly(attempts = 20, arrived = { requested }) {
            runCatching { firstChipFocusRequester.requestFocus() }.onSuccess { requested = true }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.fillMaxWidth(TV_SEARCH_FILTER_DIALOG_WIDTH_FRACTION)
                .fillMaxHeight(TV_SEARCH_FILTER_DIALOG_HEIGHT_FRACTION),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(Lang.search_tv_filter),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                )
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                // 焦点进入某分区时该分区吸附到列表顶 (分区标题与胶囊行同属一个 item,
                // 默认 BringIntoView 只保证聚焦的胶囊可见, 上移导航时标题会留在视口外
                // 永远露不出来; 吸附后标题总是完整可见, 同详情页区块吸附的行为)
                val sectionSnap: (index: Int) -> Modifier = { index ->
                    Modifier.onFocusChanged {
                        if (it.hasFocus) {
                            scope.launch { runCatching { listState.animateScrollToItem(index) } }
                        }
                    }
                }
                LazyColumn(
                    Modifier.weight(1f).padding(top = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "sort") {
                        TvSearchFilterSection(
                            stringResource(Lang.search_tv_filter_sort),
                            modifier = sectionSnap(0),
                        ) {
                            SearchSort.entries.forEachIndexed { index, entry ->
                                TvSearchFilterChip(
                                    text = tvSearchSortLabel(entry),
                                    selected = sort == entry,
                                    onClick = { sort = entry },
                                    modifier = Modifier.ifThen(index == 0) {
                                        focusRequester(firstChipFocusRequester)
                                    },
                                )
                            }
                        }
                    }
                    item(key = "rating") {
                        TvSearchFilterSection(
                            stringResource(Lang.search_tv_filter_rating_min),
                            modifier = sectionSnap(1),
                        ) {
                            listOf(null, 7, 8, 9).forEach { min ->
                                TvSearchFilterChip(
                                    text = min?.let { "$it+" }
                                        ?: stringResource(Lang.search_tv_filter_any),
                                    selected = minRating == min,
                                    onClick = { minRating = min },
                                )
                            }
                        }
                    }
                    items(
                        filterState.chips.size,
                        key = { "chip-$it" },
                    ) { chipIndex ->
                        val chip = filterState.chips[chipIndex]
                        TvSearchFilterSection(
                            tvSearchFilterKindLabel(chip.kind),
                            modifier = sectionSnap(2 + chipIndex),
                        ) {
                            chip.values.forEach { value ->
                                TvSearchFilterChip(
                                    text = value,
                                    selected = selectedTags[value] == true,
                                    onClick = {
                                        selectedTags[value] = !(selectedTags[value] == true)
                                    },
                                )
                            }
                        }
                    }
                }
                // 只有"确认": 取消 = 返回键 (弹窗出口只留一个, 也不占焦点位)
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    TvSearchFilterChip(
                        text = stringResource(Lang.search_tv_filter_confirm),
                        selected = true,
                        onClick = {
                            onConfirm(
                                query.copy(
                                    tags = selectedTags.filterValues { it }.keys.toList().ifEmpty { null },
                                    sort = sort,
                                    rating = minRating?.let { RatingRange(it, null) },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSearchFilterSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun TvSearchFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val container = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        focused -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = container,
        interactionSource = interactionSource,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = content,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun tvSearchSortLabel(sort: SearchSort): String = when (sort) {
    SearchSort.MATCH -> stringResource(Lang.exploration_search_sort_match)
    SearchSort.RANK -> stringResource(Lang.exploration_search_sort_rank)
    SearchSort.COLLECTION -> stringResource(Lang.exploration_search_sort_collection)
    SearchSort.DATE -> stringResource(Lang.exploration_search_sort_date)
}

@Composable
private fun tvSearchFilterKindLabel(kind: CanonicalTagKind?): String = when (kind) {
    CanonicalTagKind.Audience -> stringResource(Lang.exploration_search_filter_audience)
    CanonicalTagKind.Category -> stringResource(Lang.exploration_search_filter_category)
    CanonicalTagKind.Character -> stringResource(Lang.exploration_search_filter_character)
    CanonicalTagKind.Emotion -> stringResource(Lang.exploration_search_filter_emotion)
    CanonicalTagKind.Genre -> stringResource(Lang.exploration_search_filter_genre)
    CanonicalTagKind.Rating -> stringResource(Lang.exploration_search_filter_rating)
    CanonicalTagKind.Region -> stringResource(Lang.exploration_search_filter_region)
    CanonicalTagKind.Series -> stringResource(Lang.exploration_search_filter_series)
    CanonicalTagKind.Setting -> stringResource(Lang.exploration_search_filter_setting)
    CanonicalTagKind.Source -> stringResource(Lang.exploration_search_filter_source)
    CanonicalTagKind.Technology -> stringResource(Lang.exploration_search_filter_technology)
    null -> stringResource(Lang.exploration_search_filter_custom)
}

// ============================ 常量 ============================

/** 输入态/结果态之间的渐隐切换时长. */
private const val TV_SEARCH_MODE_FADE_MILLIS = 500

/** 输入态: 搜索框距页面顶部的距离. */
private val TV_SEARCH_INPUT_TOP_PAD = 48.dp

/** 输入态: 搜索框与候选列表占屏宽比例. */
private const val TV_SEARCH_INPUT_WIDTH_FRACTION = 0.55f

/** 输入态: 搜索框圆角. */
private val TV_SEARCH_INPUT_CORNER = 12.dp

/** 输入态: 补全建议的防抖时长. */
private const val TV_SEARCH_SUGGESTION_DEBOUNCE_MILLIS = 300L

/** 结果态: 内容左侧留白 (外层已让开侧边栏 48dp, 总左缘 = 48 + 此值, 与探索/追番页一致). */
private val TV_SEARCH_START_PAD = 16.dp

/** 结果态: 页面顶部留白. */
private val TV_SEARCH_TOP_PAD = 24.dp

/** 结果态: 顶部行到 Hero 信息块的间距. */
private val TV_SEARCH_TITLE_TO_HERO_GAP = 4.dp

/**
 * Hero 信息块固定高度 (标题 + 评分/元信息行 + 简介), 切换聚焦条目时网格不跳动.
 * 简介用 weight 填满剩余空间, 调大 = 简介更多行, 网格更矮
 * (标题+元信息行 ≈ 80dp, 简介每行 ≈ 20dp).
 */
private val TV_SEARCH_HERO_INFO_HEIGHT = 230.dp

/**
 * 结果态: 已选筛选项行的固定行高. 与上间距 [TV_SEARCH_FILTERS_TOP_GAP] 相加恰为简介
 * 两行行距 (2×20dp): 筛选行出现时 hero 信息块等量压缩 (简介少两行), 网格位置不动
 * 且简介换行网格对齐不破.
 */
private val TV_SEARCH_FILTERS_ROW_HEIGHT = 30.dp

/** 结果态: 已选筛选项行与顶部行的间距. */
private val TV_SEARCH_FILTERS_TOP_GAP = 10.dp

/** Hero 信息块 (简介底部) 到网格的间距. */
private val TV_SEARCH_HERO_TO_GRID_GAP = 16.dp



/** 筛选弹窗宽/高占屏比例. */
private const val TV_SEARCH_FILTER_DIALOG_WIDTH_FRACTION = 0.62f
private const val TV_SEARCH_FILTER_DIALOG_HEIGHT_FRACTION = 0.8f
