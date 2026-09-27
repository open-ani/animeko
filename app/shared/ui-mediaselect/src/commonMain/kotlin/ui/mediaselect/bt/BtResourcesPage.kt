/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.bt

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_bt_filter
import me.him188.ani.app.ui.lang.media_selector_bt_filter_count
import me.him188.ani.app.ui.lang.media_selector_episode_label
import me.him188.ani.app.ui.lang.media_selector_mode_bt
import me.him188.ani.app.ui.lang.media_selector_search_hint
import me.him188.ani.app.ui.lang.settings_debug_copied
import me.him188.ani.app.ui.mediafetch.MediaSelectorDebugTools
import me.him188.ani.app.ui.mediafetch.MediaSelectorFilters
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.TestMediaFetchRequest
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediaselect.MediaSelectorLayoutDefaults
import me.him188.ani.app.ui.mediaselect.WatchingEpisode
import me.him188.ani.app.ui.mediaselect.common.WatchingEpisodeCard
import me.him188.ani.app.ui.mediaselect.common.WatchingEpisodeText
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * BT 资源页. presentation by [MediaSelectorState.btPresentationFlow]; 开关经 [MediaSelectorState.btFilterState];
 * 偏好经 state.resolution / subtitleLanguageId / alliance.
 * 容器宽度 ≥ [MediaSelectorLayoutDefaults.WideContentMinWidth] → 表格, 否则紧凑列表.
 *
 * 头部:
 * - inlineTitle == null (宿主已画顶栏): if (showWatchingCard) WatchingEpisodeCard(watching) → 搜索框.
 * - inlineTitle != null (横屏全屏容器): 页面自绘单行 inlineTitle + 320dp 搜索框 + 右侧 WatchingEpisodeText.
 * 搜索框初值 = fetchRequest?.subjectNames?.firstOrNull() ?: ""; fetchRequest == null 时禁用;
 *   提交 = onFetchRequestChange(fetchRequest.copy(subjectNames = listOf(kw), subjectNameCN = kw)).
 * 筛选行 (下边框分割线, 不换行):
 * - 紧凑: [「第 N 话」][「<源名|数据源> ▾」→ [BtSourceSheet]][「筛选 (n)」→ [BtFilterSheet]]. 两块面板是页面根 Box 上的叠层 (不是 ModalBottomSheet), 返回键先关面板.
 * - 表格: [「第 N 话」][「<源名|数据源> ▾」→ [BtSourceDropdownChip]][MediaSelectorFilters].
 * 源过滤指向的源不在 [sourceResults] 的 BT 源里时重置为 null.
 * 列表末尾 excluded 非空且未展开 → 「显示已被排除的 N 条资源」; 展开后追加灰色行, 按钮消失 (只在本次打开期间有效).
 * 两个列表都空: presentation 仍是占位或任一 BT 源仍在查询 → 加载指示; 否则「没有资源」.
 * 选中项只在首次到达列表时定位一次; 之后筛选 / 换源改变它的下标时不滚动, 保留用户的滚动位置.
 * 点击行 = [onClickItem]; 长按 BT 行复制磁力链.
 * debug 构建下顶部有两个 [MediaSelectorDebugTools] 按钮.
 *
 * @param sourceResults 数据源面板的源状态来源 (btSources: 名称 / 查询中 / 查询失败 + 重试 / 禁用灰显点击 = onRestartSource).
 * @param watching null → 不画「第 N 话」chip 与观看卡片 (episodeFilterEnabled 仍按 state 生效).
 * @param showWatchingCard 下载对话框传 false: 有当前集 (chip 可用) 但不显示「正在观看」卡片.
 * @param timeZone 发布日期 MM-dd 的时区; 截图测试传 TimeZone.UTC.
 */
@Composable
fun BtResourcesPage(
    state: MediaSelectorState,
    sourceResults: MediaSourceResultListPresentation,
    watching: WatchingEpisode?,
    fetchRequest: MediaFetchRequest?,
    onFetchRequestChange: (MediaFetchRequest) -> Unit,
    onClickItem: (Media) -> Unit,
    onRestartSource: (instanceId: String) -> Unit,
    modifier: Modifier = Modifier,
    inlineTitle: (@Composable RowScope.() -> Unit)? = null,
    showWatchingCard: Boolean = true,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val presentation by state.btPresentationFlow.collectAsStateWithLifecycle()
    val episodeFilterEnabled by state.btFilterState.episodeFilterEnabled.collectAsStateWithLifecycle()
    val sourceFilter by state.btFilterState.sourceFilter.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val clipboard = LocalClipboard.current

    var revealExcluded by rememberSaveable { mutableStateOf(false) }
    var showSourceSheet by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    val btSourceIds = sourceResults.btSources.map { it.mediaSourceId }
    LaunchedEffect(btSourceIds, sourceFilter) {
        if (sourceFilter != null && sourceFilter !in btSourceIds) {
            state.btFilterState.sourceFilter.value = null
        }
    }

    val initialKeyword = fetchRequest?.subjectNames?.firstOrNull() ?: ""
    var keyword by rememberSaveable(initialKeyword) { mutableStateOf(initialKeyword) }
    val submitKeyword = {
        val request = fetchRequest
        val trimmed = keyword.trim()
        if (request != null && trimmed.isNotEmpty()) {
            onFetchRequestChange(request.copy(subjectNames = listOf(trimmed), subjectNameCN = trimmed))
        }
    }

    val listState = rememberLazyListState()
    val selectedIndex = presentation.included.indexOfFirst { it.isSelected }
    // 每个选中项只定位一次: 首次 presentation 仍是占位 (下标 -1) 时等列表到达再补一次; 之后筛选 / 换源改变下标时不滚动, 保留用户的滚动位置.
    var scrolledTo by remember { mutableStateOf<Media?>(null) }
    LaunchedEffect(presentation.selected, selectedIndex) {
        val selected = presentation.selected ?: return@LaunchedEffect
        if (selectedIndex >= 0 && selected != scrolledTo) {
            scrolledTo = selected
            listState.animateScrollToItem(selectedIndex)
        }
    }
    // 占位 presentation 与「BT 源仍在查询」都不是「没有资源」.
    val isLoading = presentation.isPlaceholder || sourceResults.btSources.any { it.isWorking }

    BackHandler(enabled = showSourceSheet || showFilterSheet) {
        showSourceSheet = false
        showFilterSheet = false
    }

    val onClickRow: (BtRow) -> Unit = { onClickItem(it.media) }
    val onLongClickRow: (BtRow) -> Unit = { row ->
        val download = row.media.download
        if (download is ResourceLocation.MagnetLink) {
            scope.launch {
                clipboard.setClipEntryText(download.uri)
                toaster.toast(getString(Lang.settings_debug_copied, download.uri))
            }
        }
    }

    BoxWithConstraints(modifier.testTag(BtResourcesPageTestTags.ROOT)) {
        val isTable = maxWidth >= MediaSelectorLayoutDefaults.WideContentMinWidth
        val columns = if (maxWidth < BtTableColumns.CompactMaxWidth) BtTableColumns.Compact else BtTableColumns.Default
        val horizontalPadding = when {
            !isTable -> 20.dp
            inlineTitle != null -> 16.dp
            else -> 24.dp
        }

        Column(Modifier.fillMaxSize()) {
            if (inlineTitle == null) {
                if (showWatchingCard) {
                    WatchingEpisodeCard(
                        watching,
                        Modifier.padding(horizontal = horizontalPadding).padding(bottom = 12.dp).fillMaxWidth(),
                    )
                }
                BtSearchField(
                    keyword = keyword,
                    onKeywordChange = { keyword = it },
                    onSubmit = submitKeyword,
                    enabled = fetchRequest != null,
                    modifier = Modifier.padding(horizontal = horizontalPadding).padding(bottom = 12.dp)
                        .fillMaxWidth().height(48.dp),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    inlineTitle()
                    Spacer(Modifier.width(16.dp))
                    BtSearchField(
                        keyword = keyword,
                        onKeywordChange = { keyword = it },
                        onSubmit = submitKeyword,
                        enabled = fetchRequest != null,
                        modifier = Modifier.width(320.dp).height(48.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    WatchingEpisodeText(watching, Modifier.padding(start = 16.dp))
                }
            }

            if (currentAniBuildConfig.isDebug) {
                val allMedia = { (presentation.included + presentation.excluded).map { it.media } }
                Row(
                    Modifier.padding(horizontal = horizontalPadding).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = { MediaSelectorDebugTools.dumpSubjectNames(allMedia()) }) {
                        Text("Dump Subject Names")
                    }
                    FilledTonalButton(onClick = { MediaSelectorDebugTools.dumpEpisodeRanges(allMedia()) }) {
                        Text("Dump Episode Ranges")
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (watching != null) {
                    FilterChip(
                        selected = episodeFilterEnabled,
                        onClick = { state.btFilterState.episodeFilterEnabled.value = !episodeFilterEnabled },
                        label = { Text(stringResource(Lang.media_selector_episode_label, watching.sort)) },
                        modifier = Modifier.testTag(BtResourcesPageTestTags.EPISODE_CHIP),
                    )
                }
                if (isTable) {
                    BtSourceDropdownChip(
                        presentation, sourceResults,
                        onSelect = { state.btFilterState.sourceFilter.value = it },
                        onRestartSource = onRestartSource,
                    )
                    MediaSelectorFilters(
                        resolution = state.resolution,
                        subtitleLanguageId = state.subtitleLanguageId,
                        alliance = state.alliance,
                        availableResolutions = presentation.availableResolutions,
                        availableSubtitleLanguageIds = presentation.availableSubtitleLanguageIds,
                        availableAlliances = presentation.availableAlliances,
                        singleLine = true,
                    )
                } else {
                    BtSourceChip(
                        presentation, sourceResults,
                        onClick = { showSourceSheet = true },
                        Modifier.testTag(BtResourcesPageTestTags.SOURCE_CHIP),
                    )
                    val activeFilterCount = presentation.activeFilterCount
                    FilterChip(
                        selected = activeFilterCount > 0,
                        onClick = { showFilterSheet = true },
                        label = {
                            Text(
                                if (activeFilterCount > 0) {
                                    stringResource(Lang.media_selector_bt_filter_count, activeFilterCount)
                                } else {
                                    stringResource(Lang.media_selector_bt_filter)
                                },
                            )
                        },
                        modifier = Modifier.testTag(BtResourcesPageTestTags.FILTER_CHIP),
                        leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null, Modifier.size(18.dp)) },
                    )
                }
            }
            HorizontalDivider()

            if (isTable) {
                BtTable(
                    rows = presentation.included,
                    excludedRows = presentation.excluded,
                    revealExcluded = revealExcluded,
                    onReveal = { revealExcluded = true },
                    onClick = onClickRow,
                    onLongClick = onLongClickRow,
                    timeZone = timeZone,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    columns = columns,
                    listState = listState,
                    horizontalPadding = horizontalPadding,
                    isLoading = isLoading,
                )
            } else {
                BtCompactList(
                    rows = presentation.included,
                    excludedRows = presentation.excluded,
                    revealExcluded = revealExcluded,
                    onReveal = { revealExcluded = true },
                    onClick = onClickRow,
                    onLongClick = onLongClickRow,
                    timeZone = timeZone,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    listState = listState,
                    horizontalPadding = horizontalPadding,
                    isLoading = isLoading,
                )
            }
        }

        // 两块面板始终组合, 由它们自己播放进出动画; 表格模式用下拉, 不显示面板.
        BtSourceSheet(
            visible = showSourceSheet && !isTable,
            presentation, sourceResults,
            onSelect = { state.btFilterState.sourceFilter.value = it },
            onRestartSource = onRestartSource,
            onDismiss = { showSourceSheet = false },
            modifier = Modifier.matchParentSize(),
        )
        BtFilterSheet(
            visible = showFilterSheet && !isTable,
            presentation,
            resolution = state.resolution,
            subtitleLanguageId = state.subtitleLanguageId,
            alliance = state.alliance,
            onDismiss = { showFilterSheet = false },
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * 关键字搜索框. 调用方固定宽高 (InputField 自带的最小尺寸接在其后, 不先固定会撑破窄容器).
 */
@Composable
private fun BtSearchField(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    SearchBarDefaults.InputField(
        query = keyword,
        onQueryChange = { onKeywordChange(it.trim('\n')) },
        onSearch = { onSubmit() },
        expanded = false,
        onExpandedChange = {},
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag(BtResourcesPageTestTags.SEARCH_FIELD),
        enabled = enabled,
        placeholder = { Text(stringResource(Lang.media_selector_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
    )
}

object BtResourcesPageTestTags {
    const val ROOT = "bt_page"
    const val SEARCH_FIELD = "bt_search_field"
    const val EPISODE_CHIP = "bt_episode_chip"
    const val SOURCE_CHIP = "bt_source_chip"
    const val FILTER_CHIP = "bt_filter_chip"
    const val COMPACT_LIST = "bt_compact_list"
    const val TABLE = "bt_table"
    const val LOADING = "bt_loading"
    fun row(mediaId: String) = "bt_row_$mediaId"
    const val SHOW_EXCLUDED = "bt_show_excluded"
    const val SOURCE_SHEET = "bt_source_sheet"
    fun sourceItem(mediaSourceId: String) = "bt_source_item_$mediaSourceId"
    const val FILTER_SHEET = "bt_filter_sheet"
    const val FILTER_RESET = "bt_filter_reset"
    const val FILTER_DONE = "bt_filter_done"
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewBtResourcesPageCompact() {
    ProvideCompositionLocalsForPreview {
        Surface {
            Box(Modifier.size(390.dp, 700.dp)) {
                BtResourcesPage(
                    state = rememberTestMediaSelectorState(),
                    sourceResults = TestMediaSourceResultListPresentation,
                    watching = WatchingEpisode("25", "OVA"),
                    fetchRequest = TestMediaFetchRequest,
                    onFetchRequestChange = {},
                    onClickItem = {},
                    onRestartSource = {},
                    timeZone = TimeZone.UTC,
                )
            }
        }
    }
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewBtResourcesPageTable() {
    ProvideCompositionLocalsForPreview {
        Surface {
            Box(Modifier.size(960.dp, 700.dp)) {
                BtResourcesPage(
                    state = rememberTestMediaSelectorState(),
                    sourceResults = TestMediaSourceResultListPresentation,
                    watching = WatchingEpisode("25", "OVA"),
                    fetchRequest = TestMediaFetchRequest,
                    onFetchRequestChange = {},
                    onClickItem = {},
                    onRestartSource = {},
                    timeZone = TimeZone.UTC,
                )
            }
        }
    }
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewBtResourcesPageInline() {
    ProvideCompositionLocalsForPreview {
        Surface {
            Box(Modifier.size(844.dp, 390.dp)) {
                BtResourcesPage(
                    state = rememberTestMediaSelectorState(),
                    sourceResults = TestMediaSourceResultListPresentation,
                    watching = WatchingEpisode("25", "OVA"),
                    fetchRequest = TestMediaFetchRequest,
                    onFetchRequestChange = {},
                    onClickItem = {},
                    onRestartSource = {},
                    inlineTitle = {
                        Text(stringResource(Lang.media_selector_mode_bt), style = MaterialTheme.typography.titleLarge)
                    },
                    timeZone = TimeZone.UTC,
                )
            }
        }
    }
}
