/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import me.him188.ani.app.data.models.subject.displayName
import me.him188.ani.app.domain.episode.EpisodeWithAiringTime
import me.him188.ani.tv.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvPosterCard

/** 时间表焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvScheduleFocus : TvFocusKey {
    /** 「今天」日期胶囊 (进页初始焦点). */
    Today,
}

/**
 * TV 新番时间表 (atv-architecture.md §7.2, M2 精简版):
 * 日期胶囊行 (聚焦即换天, PR 正交模型) + 当天网格.
 */
@Composable
fun TvScheduleScreen(
    viewModel: TvScheduleViewModel,
    onClickSubject: (subjectId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val schedule by viewModel.schedule.collectAsState()

    Box(modifier.fillMaxSize()) {
        val days = schedule
        if (days == null) {
            Text(
                "加载中…",
                Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
            )
            return@Box
        }

        val todayIndex = days.indexOfFirst { it.date == viewModel.today }.coerceAtLeast(0)
        var selectedDayIndex by rememberSaveable { mutableIntStateOf(todayIndex) }
        val chipRowState = rememberLazyListState(initialFirstVisibleItemIndex = todayIndex)

        // 统一焦点框架: 进页初始焦点落「今天」胶囊 (轮询 + 到位确认)
        val focus = rememberTvFocusScope()
        focus.Resolver()
        focus.InitialFocus(TvScheduleFocus.Today)

        Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
            // 日期胶囊行: 聚焦即换天 (正交按键模型, §7.2)
            LazyRow(
                state = chipRowState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp),
            ) {
                itemsIndexed(days, key = { _, d -> d.date.toString() }) { index, day ->
                    DayChip(
                        date = day.date,
                        isToday = day.date == viewModel.today,
                        selected = selectedDayIndex == index,
                        onFocused = { selectedDayIndex = index },
                        modifier = if (index == todayIndex) {
                            Modifier.tvFocusAnchor(focus, TvScheduleFocus.Today)
                        } else Modifier,
                    )
                }
            }

            val current = days.getOrNull(selectedDayIndex)
            if (current == null || current.list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "这一天没有新番",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(124.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(current.list.size, key = { current.list[it].episode.episodeId }) { index ->
                        val item = current.list[index]
                        ScheduleCard(item, onClick = { onClickSubject(item.subject.subjectId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChip(
    date: LocalDate,
    isToday: Boolean,
    selected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = {},
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusDefaults.FocusedScale),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (isToday) "今天" else "${date.monthNumber}/${date.dayOfMonth}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                weekdayText(date),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun ScheduleCard(
    item: EpisodeWithAiringTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(124.dp)) {
        TvPosterCard(
            imageUrl = item.subject.imageLarge,
            title = item.subject.displayName,
            onClick = onClick,
            width = 124.dp,
        )
        Text(
            "第 ${item.episode.sort} 话",
            Modifier.padding(start = 5.dp, top = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun weekdayText(date: LocalDate): String = when (date.dayOfWeek.isoDayNumber) {
    1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"; 6 -> "周六"; else -> "周日"
}
