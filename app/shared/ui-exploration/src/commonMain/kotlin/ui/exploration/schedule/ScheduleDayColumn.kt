/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalTime
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.layout.paddingIfNotEmpty
import me.him188.ani.app.ui.foundation.text.ProvideContentColor


/**
 * 新番时间表的单日视图, 例如周一.
 *
 * [Design](https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Animeko?node-id=349-9250&t=hBPSAEVlsmuEWPJt-0)
 */
@Composable
fun ScheduleDayColumn(
    items: List<ScheduleDayColumnItem>,
    dayOfWeek: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    layoutParams: ScheduleDayColumnLayoutParams = ScheduleDayColumnLayoutParams.Default,
    state: LazyListState = rememberLazyListState(),
    itemColors: ListItemColors = ListItemDefaults.colors(),
) {
    Column(modifier) {
        Row(Modifier.paddingIfNotEmpty(layoutParams.dayOfWeekPaddings)) {
            ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                dayOfWeek()
            }
        }

        LazyColumn(
            Modifier.padding(layoutParams.listPadding),
            state = state,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(layoutParams.listVerticalSpacing),
        ) {
            items(
                items,
                key = { item ->
                    when (item) {
                        is ScheduleDayColumnItem.Item -> item.item.subjectId
                        is ScheduleDayColumnItem.CurrentTimeIndicator -> item.hashCode()
                    }
                },
                contentType = { item ->
                    when (item) {
                        is ScheduleDayColumnItem.Item -> true
                        is ScheduleDayColumnItem.CurrentTimeIndicator -> false
                    }
                },
            ) { columnItem ->
                when (columnItem) {
                    is ScheduleDayColumnItem.CurrentTimeIndicator -> {
                        ScheduleCurrentTimeIndicator(columnItem, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }

                    is ScheduleDayColumnItem.Item -> {
                        val item = columnItem.item
                        ScheduleItem(
                            subjectTitle = { ScheduleItemDefaults.SubjectTitle(item.subjectTitle) },
                            episode = {
                                ScheduleItemDefaults.Episode(
                                    item.episodeSort,
                                    item.episodeEp,
                                    item.episodeName,
                                )
                            },
                            leadingImage = { AsyncImage(item.imageUrl, "${item.subjectTitle} 封面") },
                            time = {
                                if (columnItem.showTime) {
                                    ScheduleItemDefaults.Time(item.futureStartDate, item.time)
                                }
                            },
                            action = {
                                // TODO: 2025/1/14 新番时间表追番动作
                            },
                            colors = itemColors,
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun ScheduleCurrentTimeIndicator(
    columnItem: ScheduleDayColumnItem.CurrentTimeIndicator,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideContentColor(MaterialTheme.colorScheme.primary) {
            Row(
                Modifier.padding(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Alarm, null)
                Text(
                    ScheduleItemDefaults.renderTime(null, columnItem.currentTime),
                    softWrap = false,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider()
        }
    }
}

@Immutable
sealed class ScheduleDayColumnItem {
    @Immutable
    data class Item(
        val item: ScheduleItemPresentation,
        val showTime: Boolean,
    ) : ScheduleDayColumnItem()

    @Immutable
    data class CurrentTimeIndicator(
        val currentTime: LocalTime,
    ) : ScheduleDayColumnItem()
}

@Immutable
data class ScheduleDayColumnLayoutParams(
    val dayOfWeekPaddings: PaddingValues,
    val listVerticalSpacing: Dp,
    val listPadding: PaddingValues,
) {
    @Stable
    companion object {

        @Stable // Adaptive layout not needed by design.
        val Default = ScheduleDayColumnLayoutParams(
            dayOfWeekPaddings = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            listVerticalSpacing = 0.dp,
            listPadding = PaddingValues(0.dp),
        )
    }
}
