/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.manual

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.layout.Zero
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_load_failed
import me.him188.ani.app.ui.lang.media_selector_manual_back
import me.him188.ani.app.ui.lang.media_selector_manual_channels
import me.him188.ani.app.ui.lang.media_selector_mode_manual
import me.him188.ani.app.ui.lang.media_selector_sources
import me.him188.ani.app.ui.mediafetch.TestBrowseSubjects
import me.him188.ani.app.ui.mediafetch.rememberTestManualBrowseState
import me.him188.ani.app.ui.mediaselect.MediaSelectorLayoutDefaults
import me.him188.ani.app.ui.mediaselect.WatchingEpisode
import me.him188.ani.app.ui.mediaselect.common.WatchingEpisodeCard
import me.him188.ani.app.ui.mediaselect.common.WatchingEpisodeText
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

/**
 * 手动查找页. `BoxWithConstraints`: maxWidth ≥ [MediaSelectorLayoutDefaults.WideContentMinWidth] → 双栏, 否则堆叠两页 (搜索 → 线路与剧集).
 *
 * 顶栏规则:
 * - 堆叠第一页: 渲染宿主给的 [topBar] (宿主的标题 + 模式 chip [+ 关闭]), 其下 WatchingEpisodeCard(watching).
 * - 堆叠第二页: 页面自绘 `TopAppBar(navigationIcon = BackNavigationIconButton(closeSubject), title = Column(条目名 titleLarge / 源名 bodySmall), actions = closeButton, windowInsets = WindowInsets.Zero)`,
 *   不渲染 [topBar]; 其下 WatchingEpisodeCard(watching). 系统返回键等价于返回箭头 (closeSubject), 只在第二页拦截; 第一页与双栏版式把返回键留给宿主关闭容器.
 * - 双栏且 [inlineTitle] != null: 页面自绘单行 `Row(Modifier.fillMaxWidth()) { inlineTitle(); Spacer(weight); WatchingEpisodeText(watching) }`, 不渲染 [topBar].
 * - 双栏且 [inlineTitle] == null: 渲染 [topBar], 其下 WatchingEpisodeCard(watching), 再是两栏.
 * 布局约束: 顶栏 / 卡片 / 列表 / 底部面板在同一 Column 里, Lazy 列表与网格必须 `Modifier.weight(1f)`.
 * 剧集网格: LazyVerticalGrid, 堆叠 Fixed(4), 双栏 Fixed(9); 按钮高 44dp 圆角 10; 选中 primaryContainer + 2dp primary 边框; 头部 item(span = maxLineSpan) 放「剧集 N 项」.
 * 底部面板: 堆叠 = 「播放「name」」titleMedium + 副文 + 填充按钮「播放并记住」+ 文本按钮「仅临时播放，不记忆」;
 *           双栏 = 单行: 副文 weight(1f) + 文本按钮「仅临时播放」+ 填充按钮「播放并记住」.
 * 按钮 enabled = selectedEpisode != null && target != null && !isPlaying.
 * 点击 → scope.launch { when (state.play(remember)) { true -> onPlayed(); false -> toaster.toast(media_selector_load_failed); null -> 忽略 } }:
 * null 表示已有进行中的播放 (快速双击), 不是失败, 不提示.
 *
 * @param onPlayed `state.play(...)` 返回 true 后调用; 宿主关闭所有容器.
 */
@Composable
fun ManualBrowsePage(
    state: ManualBrowseState,
    watching: WatchingEpisode?,
    onPlayed: () -> Unit,
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    closeButton: (@Composable () -> Unit)? = null,
    inlineTitle: (@Composable RowScope.() -> Unit)? = null,
) {
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val loadFailedText = stringResource(Lang.media_selector_load_failed)
    val currentOnPlayed by rememberUpdatedState(onPlayed)
    val play: (remember: Boolean) -> Unit = { remember ->
        scope.launch {
            when (state.play(remember)) {
                true -> currentOnPlayed()
                false -> toaster.toast(loadFailedText)
                null -> Unit
            }
        }
    }

    BoxWithConstraints(modifier.testTag(ManualBrowsePageTestTags.ROOT)) {
        if (maxWidth >= MediaSelectorLayoutDefaults.WideContentMinWidth) {
            ManualBrowseWideLayout(
                state, presentation, watching, play,
                topBar = topBar,
                inlineTitle = inlineTitle,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ManualBrowseStackedLayout(
                state, presentation, watching, play,
                topBar = topBar,
                closeButton = closeButton,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 堆叠两页: 第一页搜索, 第二页线路与剧集; 以 [ManualBrowsePresentation.openedSubject] 是否为 null 切换.
 * 退出中的第二页仍拿着自己的条目, 淡出期间不会因为状态已清空而闪成空白.
 * 返回键按 [presentation] 而不是 AnimatedContent 里的旧值判断: 状态一清空就停止拦截, 退出动画中的第二页不会再吃掉一次返回.
 */
@Composable
private fun ManualBrowseStackedLayout(
    state: ManualBrowseState,
    presentation: ManualBrowsePresentation,
    watching: WatchingEpisode?,
    play: (remember: Boolean) -> Unit,
    topBar: @Composable () -> Unit,
    closeButton: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = presentation.openedSubject != null, onBack = state::closeSubject)
    AnimatedContent(
        targetState = presentation.openedSubject,
        modifier = modifier,
        transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
        contentKey = { it != null },
    ) { openedSubject ->
        if (openedSubject == null) {
            Column(Modifier.fillMaxSize()) {
                topBar()
                WatchingEpisodeCard(
                    watching,
                    Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth(),
                )
                ManualSectionLabel(
                    stringResource(Lang.media_selector_sources),
                    Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
                )
                ManualSourceChips(
                    presentation.sources,
                    presentation.selectedSourceId,
                    onSelect = state::selectSource,
                    Modifier.fillMaxWidth(),
                    isPlaceholder = presentation.isPlaceholder,
                )
                ManualSearchField(
                    presentation.keyword,
                    onKeywordChange = state::setKeyword,
                    onSearch = state::search,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                )
                ManualResultsList(
                    presentation.results,
                    openedSubject = null,
                    onOpen = state::openSubject,
                    onRetry = state::retry,
                    Modifier.weight(1f).fillMaxWidth(),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ManualSubjectTopBar(
                    openedSubject,
                    sourceName = presentation.selectedSource?.info?.displayName,
                    onBack = state::closeSubject,
                    closeButton = closeButton,
                )
                WatchingEpisodeCard(
                    watching,
                    Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth(),
                )
                ManualChannelsContent(state, presentation, columns = 4) {
                    ManualPlayPanelStacked(presentation, play, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * 第二页的线路行 + 剧集网格 + 底部面板, 三态都在同一 Column 里, 网格 weight(1f).
 */
@Composable
private fun ColumnScope.ManualChannelsContent(
    state: ManualBrowseState,
    presentation: ManualBrowsePresentation,
    columns: Int,
    gridContentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    channelRow: @Composable (channels: List<BrowseChannel>) -> Unit = { channels ->
        ManualSectionLabel(
            stringResource(Lang.media_selector_manual_channels),
            Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
        )
        ManualChannelChips(
            channels,
            presentation.selectedChannelIndex,
            onSelect = state::selectChannel,
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
    },
    playPanel: @Composable () -> Unit,
) {
    when (val channels = presentation.channels) {
        ManualLoadState.Idle -> Spacer(Modifier.weight(1f))
        ManualLoadState.Loading -> ManualLoadingBox(Modifier.weight(1f))
        is ManualLoadState.Failed -> ManualFailedBox(channels, onRetry = state::retry, Modifier.weight(1f))
        is ManualLoadState.Success -> {
            if (shouldShowChannelRow(channels.value)) {
                channelRow(channels.value)
            }
            ManualEpisodeGrid(
                presentation.selectedChannel?.episodes.orEmpty(),
                presentation.selectedEpisodeIndex,
                onSelect = state::selectEpisode,
                columns = columns,
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = gridContentPadding,
            )
            playPanel()
        }
    }
}

/**
 * 第二页顶栏: 返回 + 条目名 / 源名 + 宿主的关闭按钮. 容器色透明, 跟随宿主容器.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualSubjectTopBar(
    subject: BrowseSubject,
    sourceName: String?,
    onBack: () -> Unit,
    closeButton: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val backText = stringResource(Lang.media_selector_manual_back)
    TopAppBar(
        title = {
            Column {
                Text(
                    subject.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sourceName != null) {
                    Text(
                        sourceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        modifier = modifier,
        navigationIcon = {
            BackNavigationIconButton(
                onBack,
                Modifier.testTag(ManualBrowsePageTestTags.BACK).semantics { contentDescription = backText },
            )
        },
        actions = { closeButton?.invoke() },
        windowInsets = WindowInsets.Zero,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

/**
 * 双栏: 左栏 300dp 搜索, 右栏线路与剧集; 右栏在没有打开条目时留空.
 */
@Composable
private fun ManualBrowseWideLayout(
    state: ManualBrowseState,
    presentation: ManualBrowsePresentation,
    watching: WatchingEpisode?,
    play: (remember: Boolean) -> Unit,
    topBar: @Composable () -> Unit,
    inlineTitle: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (inlineTitle != null) {
            Row(
                Modifier.fillMaxWidth().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                inlineTitle()
                Spacer(Modifier.weight(1f))
                WatchingEpisodeText(watching)
            }
        } else {
            topBar()
            WatchingEpisodeCard(
                watching,
                Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth(),
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.width(300.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ManualSourceChips(
                    presentation.sources,
                    presentation.selectedSourceId,
                    onSelect = state::selectSource,
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
                    isPlaceholder = presentation.isPlaceholder,
                )
                ManualSearchField(
                    presentation.keyword,
                    onKeywordChange = state::setKeyword,
                    onSearch = state::search,
                    Modifier.padding(start = 16.dp, end = 12.dp).fillMaxWidth(),
                )
                ManualResultsList(
                    presentation.results,
                    openedSubject = presentation.openedSubject,
                    onOpen = state::openSubject,
                    onRetry = state::retry,
                    Modifier.weight(1f).fillMaxWidth(),
                )
            }
            VerticalDivider()
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp)) {
                val subject = presentation.openedSubject
                if (subject != null) {
                    ManualChannelsContent(
                        state, presentation,
                        columns = 9,
                        gridContentPadding = PaddingValues(0.dp),
                        channelRow = { channels ->
                            ManualWideSubjectHeader(subject, channels, presentation.selectedChannelIndex, state::selectChannel)
                        },
                    ) {
                        ManualPlayPanelInline(presentation, play, Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    }
                }
            }
        }
    }
}

/**
 * 双栏右栏的标题行: 条目名 + 「线路」+ 线路 chips (右对齐).
 * 只有一条无名线路时 [ManualChannelsContent] 不会调用它, 标题行也随之省略 (右栏直接从网格开始).
 */
@Composable
private fun ManualWideSubjectHeader(
    subject: BrowseSubject,
    channels: List<BrowseChannel>,
    selectedChannelIndex: Int,
    onSelectChannel: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            subject.name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ManualSectionLabel(stringResource(Lang.media_selector_manual_channels))
        ManualChannelChips(
            channels,
            selectedChannelIndex,
            onSelect = onSelectChannel,
            Modifier.weight(1f),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        )
    }
}

object ManualBrowsePageTestTags {
    const val ROOT = "manual_browse_page"
    fun sourceChip(instanceId: String) = "manual_source_$instanceId"
    const val SEARCH_FIELD = "manual_search_field"
    fun result(index: Int) = "manual_result_$index"
    const val BACK = "manual_back"
    fun channelChip(index: Int) = "manual_channel_$index"
    fun episode(index: Int) = "manual_episode_$index"
    const val PLAY_REMEMBER = "manual_play_remember"
    const val PLAY_TEMPORARY = "manual_play_temporary"
    const val RETRY = "manual_retry"
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewManualBrowsePageStacked() {
    ProvideCompositionLocalsForPreview {
        Surface {
            val state = rememberTestManualBrowseState()
            LaunchedEffect(state) { state.search() }
            ManualBrowsePage(
                state,
                watching = WatchingEpisode("25", "OVA"),
                onPlayed = {},
                topBar = { Text(stringResource(Lang.media_selector_sources), Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge) },
                Modifier.size(390.dp, 844.dp),
            )
        }
    }
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewManualBrowsePageStackedEpisodes() {
    ProvideCompositionLocalsForPreview {
        Surface {
            val state = rememberTestManualBrowseState()
            LaunchedEffect(state) {
                state.search()
                state.openSubject(TestBrowseSubjects.first())
            }
            ManualBrowsePage(
                state,
                watching = WatchingEpisode("25", "OVA"),
                onPlayed = {},
                topBar = {},
                Modifier.size(390.dp, 844.dp),
            )
        }
    }
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewManualBrowsePageWide() {
    ProvideCompositionLocalsForPreview {
        Surface {
            val state = rememberTestManualBrowseState()
            LaunchedEffect(state) {
                state.search()
                state.openSubject(TestBrowseSubjects.first())
            }
            ManualBrowsePage(
                state,
                watching = WatchingEpisode("25", "OVA"),
                onPlayed = {},
                topBar = {},
                Modifier.size(844.dp, 390.dp),
                inlineTitle = { Text(stringResource(Lang.media_selector_mode_manual), Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge) },
            )
        }
    }
}
