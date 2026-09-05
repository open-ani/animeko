/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.schedule

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format.char
import kotlinx.datetime.isoDayNumber
import me.him188.ani.app.ui.exploration.schedule.AiringScheduleColumnItem
import me.him188.ani.app.ui.exploration.schedule.AiringScheduleItemPresentation
import me.him188.ani.app.ui.exploration.schedule.ScheduleDay
import me.him188.ani.app.ui.exploration.schedule.ScheduleScreenState
import me.him188.ani.app.ui.exploration.schedule.ScheduleViewModel
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.tv.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.widgets.TvHeroButton
import me.him188.ani.tv.ui.foundation.widgets.TvPageDefaults
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvHeroSecondaryContentColor

/** 时间表焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvScheduleFocus : TvFocusKey {
    /** 「今天」日期胶囊 (进页初始焦点). */
    Today,
}

/*
 * TV 新番时间表: 手机端 ScheduleScreen 宽屏 (Medium) 布局的搬运 (atv-architecture.md §7.2) ——
 * 多天并排的固定宽列 (360dp) 横向排列, 列头为「M/d + 周几」(今天主题色 + 圆头分隔线),
 * 每列一条时间线列表 (时间行 / 56dp 封面 / 标题 / 集数副行 / 当前时间指示器 / 占位骨架).
 * 状态层直接复用手机 ScheduleViewModel + ScheduleScreenState (D3).
 *
 * 滚动控制: 手机 desktop 用 HorizontalScrollControlScaffoldOnDesktop (鼠标悬停出滚动按钮,
 * 修 CMP 的 LazyRow 无法鼠标拖动); TV 上它是 Platform.Desktop 分支的空透传, 且 hover/click
 * 输入源都不存在 —— 跨列横向滚动由焦点系统天然承担 (聚焦移到视口外的列, BringIntoView
 * 自动滚动 LazyRow), 与 desktop 滚动按钮等效.
 */
@Composable
fun TvScheduleScreen(
    onClickSubject: (subjectId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 状态层复用手机 ScheduleViewModel (D3); UI 为 TV 自绘
    val viewModel = viewModel<ScheduleViewModel> { ScheduleViewModel() }
    val presentation by viewModel.presentationFlow.collectAsState()
    // 复用手机 ScheduleScreenState: days + 初始滚动到今天列的 lazyListState + 各列独立列表状态
    val state = remember { ScheduleScreenState { viewModel.pageState.days } }

    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvScheduleFocus.Today)

    // ── 错误态: 文本 + 重试 ──
    val error = presentation.error
    if (error != null) {
        Column(
            modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
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
                onClick = { viewModel.refresh() },
                onFocused = {},
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        return
    }

    // ── 多列并排 (手机 Medium 档): 固定 360dp 列宽, 列间 16dp, 初始滚动到今天列 ──
    LazyRow(
        modifier.fillMaxSize().tvFocusNavSignal(focus),
        state = state.lazyListState,
        horizontalArrangement = Arrangement.spacedBy(TvScheduleDefaults.PageSpacing),
        contentPadding = PaddingValues(start = TvScheduleDefaults.StartPadding, end = TvPageDefaults.EndPadding),
    ) {
        items(state.days, key = { it.date.toString() }) { day ->
            val columnItems = presentation.airingSchedules
                .firstOrNull { it.date == day.date }?.episodes.orEmpty()
            TvScheduleDayColumn(
                day = day,
                items = columnItems,
                onClickSubject = onClickSubject,
                focus = focus,
                modifier = Modifier.width(TvScheduleDefaults.PageWidth).fillParentMaxHeight(),
            )
        }
    }
}

/**
 * 单天列 (手机 ScheduleDayColumn 布局): 列头 [TvDayOfWeekHeadline] + 时间线 LazyColumn.
 * 今天列的第一条数据挂 [TvScheduleFocus.Today] 锚点 (进页初始焦点).
 */
@Composable
private fun TvScheduleDayColumn(
    day: ScheduleDay,
    items: List<AiringScheduleColumnItem>,
    onClickSubject: (subjectId: Int) -> Unit,
    focus: me.him188.ani.tv.ui.foundation.focus.TvFocusScope,
    modifier: Modifier = Modifier,
) {
    val isToday = day.kind == ScheduleDay.Kind.TODAY
    Column(modifier.padding(top = 20.dp)) {
        TvDayOfWeekHeadline(day)

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "这一天没有新番",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tvHeroSecondaryContentColor(),
                )
            }
        } else {
            // 今天列的第一条数据 = 初始焦点锚点
            val firstDataIndex = items.indexOfFirst { it is AiringScheduleColumnItem.Data }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items.size,
                    key = { index ->
                        when (val item = items[index]) {
                            is AiringScheduleColumnItem.Data ->
                                "data-${item.item.subjectId}-${item.item.episodeId}"

                            is AiringScheduleColumnItem.CurrentTimeIndicator -> "indicator"
                            is AiringScheduleColumnItem.PlaceholderData -> "placeholder-${item.id}"
                        }
                    },
                ) { index ->
                    when (val item = items[index]) {
                        is AiringScheduleColumnItem.Data -> TvScheduleItem(
                            item = item.item,
                            showTime = item.showTime,
                            onClick = { onClickSubject(item.item.subjectId) },
                            modifier = if (isToday && index == firstDataIndex) {
                                Modifier.tvFocusAnchor(focus, TvScheduleFocus.Today)
                            } else Modifier,
                        )

                        is AiringScheduleColumnItem.CurrentTimeIndicator ->
                            TvScheduleCurrentTimeIndicator(item.currentTime)

                        is AiringScheduleColumnItem.PlaceholderData -> TvScheduleItemSkeleton(
                            showTime = item.showTime,
                        )
                    }
                }
            }
        }
    }
}

/** 列头 (手机 DayOfWeekHeadline 复刻): 「M/d 周几」headlineSmall, 今天主题色, 下缘 2dp 圆头线. */
@Composable
private fun TvDayOfWeekHeadline(
    day: ScheduleDay,
    modifier: Modifier = Modifier,
) {
    val isToday = day.kind == ScheduleDay.Kind.TODAY
    Column(modifier.padding(horizontal = 16.dp)) {
        Text(
            "${day.date.monthNumber}/${day.date.dayOfMonth} ${renderDayOfWeek(day)}",
            style = MaterialTheme.typography.headlineSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else tvHeroContentColor(),
            softWrap = false,
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/**
 * 时间线列表项 (手机 ScheduleItem 布局): 时间行 (仅该时段首条显示) +
 * [56dp 封面圆角图 | 标题 / 集数副行]. 聚焦: 统一的色圈+留白.
 */
@Composable
private fun TvScheduleItem(
    item: AiringScheduleItemPresentation,
    showTime: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(modifier) {
        if (showTime) {
            Text(
                renderTime(item.time),
                Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = tvHeroContentColor(),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (focused) {
                        Modifier.border(
                            TvFocusDefaults.RingWidth,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(TvScheduleDefaults.ItemCornerRadius + TvFocusDefaults.RingInset),
                        )
                    } else Modifier,
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(TvFocusDefaults.RingInset)
                    .clip(RoundedCornerShape(TvScheduleDefaults.ItemCornerRadius))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    item.imageUrl,
                    "${item.subjectTitle} 封面",
                    Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.subjectTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = tvHeroContentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        renderEpisode(item.episodeSort, item.episodeEp, item.episodeName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tvHeroSecondaryContentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 当前时间指示器 (手机同款语义): 主题色圆点 + 横线 + 时间. */
@Composable
private fun TvScheduleCurrentTimeIndicator(
    currentTime: LocalTime,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        )
        Box(
            Modifier
                .weight(1f)
                .height(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            renderTime(currentTime),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 占位骨架 (手机 PlaceholderData 对应): 灰块. */
@Composable
private fun TvScheduleItemSkeleton(
    showTime: Boolean,
    modifier: Modifier = Modifier,
) {
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier) {
        if (showTime) {
            Box(
                Modifier
                    .padding(start = 16.dp, top = 8.dp, bottom = 2.dp)
                    .size(width = 48.dp, height = 16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(block),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(block))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(width = 220.dp, height = 16.dp)
                        .clip(RoundedCornerShape(4.dp)).background(block),
                )
                Box(
                    Modifier.size(width = 140.dp, height = 13.dp)
                        .clip(RoundedCornerShape(4.dp)).background(block),
                )
            }
        }
    }
}

// ── 渲染 (语义对齐手机 ScheduleItemDefaults / renderScheduleDay, TV 侧自绘) ──

private val timeFormatter = LocalTime.Format {
    hour()
    char(':')
    minute()
}

private fun renderTime(time: LocalTime?): String =
    time?.let { timeFormatter.format(it) } ?: "时间未定"

/** 集数渲染: 手机 ScheduleItemDefaults.Episode 同语义 ("第 N 话 [名称]" / ep(sort) 特殊形). */
private fun renderEpisode(
    episodeSort: EpisodeSort,
    episodeEp: EpisodeSort?,
    episodeName: String?,
): String {
    val epText = episodeEp?.toString()?.removePrefix("0")
    val sortText = episodeSort.toString().removePrefix("0")
    val sortDisplay = if (episodeEp == null || episodeEp == episodeSort) {
        if (episodeSort is EpisodeSort.Normal) "第 $sortText 话" else sortText
    } else {
        checkNotNull(epText)
        if (episodeSort is EpisodeSort.Normal && episodeEp is EpisodeSort.Normal) {
            "第 $epText ($sortText) 话"
        } else {
            "$epText ($sortText)"
        }
    }
    return if (episodeName.isNullOrBlank()) sortDisplay else "$sortDisplay  $episodeName"
}

/** 周几渲染 (手机 renderDayOfWeek 同语义: 上周%s / 周%s / 下周%s). */
private fun renderDayOfWeek(day: ScheduleDay): String {
    val weekday = when (day.date.dayOfWeek.isoDayNumber) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日"
    }
    return when (day.kind) {
        ScheduleDay.Kind.LAST_WEEK -> "上周$weekday"
        ScheduleDay.Kind.THIS_WEEK, ScheduleDay.Kind.TODAY -> "周$weekday"
        ScheduleDay.Kind.NEXT_WEEK -> "下周$weekday"
    }
}

/** 时间表页默认值/调参 (布局度量对齐手机 Medium 档). */
private object TvScheduleDefaults {
    /** 内容左侧留白 (外层已让开侧栏收起宽 48dp). */
    val StartPadding = 16.dp

    /** 单天列宽 (手机 Medium 档 PageSize.Fixed(360.dp) 同值). */
    val PageWidth = 360.dp

    /** 列间距 (手机 Medium 档 pageSpacing 同值). */
    val PageSpacing = 16.dp

    /** 列表项圆角. */
    val ItemCornerRadius = 10.dp
}
