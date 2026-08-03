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
import androidx.compose.foundation.lazy.itemsIndexed
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
import me.him188.ani.app.ui.exploration.schedule.ScheduleViewModel
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.widgets.TvHeroButton
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvHeroSecondaryContentColor

/** 时间表焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvScheduleFocus : TvFocusKey {
    /** 「今天」日期胶囊 (进页初始焦点). */
    Today,
}

/*
 * TV 新番时间表: 手机端 ScheduleScreen 的布局搬运 (atv-architecture.md §7.2) ——
 * 顶部日期 tab 行 + 每天一列「时间线列表」(时间行 / 56dp 封面 / 标题 / 集数副行 /
 * 当前时间指示器 / 占位骨架), 状态层直接复用手机 ScheduleViewModel (D3).
 *
 * TV 化差异: tab 的「点击切页 + Pager 滑动」换成「聚焦即切天 + 内容 crossfade」;
 * 列表项聚焦用统一的色圈+留白视觉; 进页初始焦点落「今天」.
 */
@Composable
fun TvScheduleScreen(
    onClickSubject: (subjectId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 状态层复用手机 ScheduleViewModel (D3); UI 为 TV 自绘
    val viewModel = viewModel<ScheduleViewModel> { ScheduleViewModel() }
    val presentation by viewModel.presentationFlow.collectAsState()

    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvScheduleFocus.Today)

    val days = presentation.days
    val todayIndex = days.indexOfFirst { it.kind == ScheduleDay.Kind.TODAY }.coerceAtLeast(0)
    var selectedDayIndex by rememberSaveable { mutableIntStateOf(todayIndex) }
    val chipRowState = rememberLazyListState(initialFirstVisibleItemIndex = todayIndex)

    Column(modifier.fillMaxSize().padding(top = 24.dp)) {
        // ── 日期 tab 行: 聚焦即切天 (手机为点击 + pager 滑动); 今天主题色 ──
        LazyRow(
            state = chipRowState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = TV_SCHEDULE_START_PAD, end = 48.dp),
        ) {
            itemsIndexed(days, key = { _, d -> d.date.toString() }) { index, day ->
                TvScheduleDayChip(
                    day = day,
                    selected = selectedDayIndex == index,
                    onFocused = { selectedDayIndex = index },
                    modifier = if (index == todayIndex) {
                        Modifier.tvFocusAnchor(focus, TvScheduleFocus.Today)
                    } else Modifier,
                )
            }
        }

        // ── 错误态: 文本 + 重试 ──
        val error = presentation.error
        if (error != null) {
            Column(
                Modifier.fillMaxSize(),
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
            return@Column
        }

        // ── 当天时间线列表 (手机 ScheduleDayColumn 布局): 换天 crossfade ──
        val selectedDay = days.getOrNull(selectedDayIndex)
        val columnItems = presentation.airingSchedules
            .firstOrNull { it.date == selectedDay?.date }?.episodes.orEmpty()

        Crossfade(
            targetState = selectedDay?.date to columnItems,
            modifier = Modifier.fillMaxSize(),
            label = "scheduleDay",
        ) { (_, items) ->
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "这一天没有新番",
                        style = MaterialTheme.typography.titleMedium,
                        color = tvHeroSecondaryContentColor(),
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = TV_SCHEDULE_START_PAD, end = 48.dp,
                        top = 16.dp, bottom = 24.dp,
                    ),
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
}

/** 日期胶囊: 手机 renderScheduleDay 语义 (M/d + 周几, 今天主题色), TV 聚焦即切天. */
@Composable
private fun TvScheduleDayChip(
    day: ScheduleDay,
    selected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val isToday = day.kind == ScheduleDay.Kind.TODAY
    val container = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
    }
    val content = when {
        focused -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        isToday -> MaterialTheme.colorScheme.primary
        else -> tvHeroSecondaryContentColor()
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(container)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (isToday) "今天" else "${day.date.monthNumber}/${day.date.dayOfMonth}",
            style = MaterialTheme.typography.titleSmall,
            color = content,
        )
        Text(
            renderDayOfWeek(day),
            style = MaterialTheme.typography.titleSmall,
            color = content,
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
    Column(modifier.widthIn(max = TV_SCHEDULE_LIST_MAX_WIDTH)) {
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
                            2.5.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(TV_SCHEDULE_ITEM_CORNER + 3.dp),
                        )
                    } else Modifier,
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(3.dp)
                    .clip(RoundedCornerShape(TV_SCHEDULE_ITEM_CORNER))
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
            .widthIn(max = TV_SCHEDULE_LIST_MAX_WIDTH)
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
    Column(modifier.widthIn(max = TV_SCHEDULE_LIST_MAX_WIDTH)) {
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

private fun renderTime(time: LocalTime): String = timeFormatter.format(time)

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

private fun renderDayOfWeek(day: ScheduleDay): String = when (day.date.dayOfWeek.isoDayNumber) {
    1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"; 6 -> "周六"; else -> "周日"
}

/** 内容左侧留白 (外层已让开侧栏收起宽 48dp). */
private val TV_SCHEDULE_START_PAD = 16.dp

/** 时间线列表最大宽度 (手机为全宽单列; TV 屏宽, 限宽左对齐保持列表形态). */
private val TV_SCHEDULE_LIST_MAX_WIDTH = 640.dp

/** 列表项圆角. */
private val TV_SCHEDULE_ITEM_CORNER = 10.dp
