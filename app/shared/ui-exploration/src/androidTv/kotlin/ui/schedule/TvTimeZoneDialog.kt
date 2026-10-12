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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import me.him188.ani.app.ui.exploration.schedule.ScheduleSelectableTimeZones
import me.him188.ani.app.ui.exploration.schedule.formatUtcOffset
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_schedule_time_zone
import me.him188.ani.app.ui.lang.exploration_schedule_time_zone_system
import me.him188.ani.tv.ui.foundation.widgets.TvOptionModal
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

/**
 * TV 新番时间表的时区选择弹窗.
 *
 * 选项与手机端 [me.him188.ani.app.ui.exploration.schedule.ScheduleTimeZoneSelector] 一致
 * (同一份 [ScheduleSelectableTimeZones] 与 [formatUtcOffset]), 但用 TV 的单选面板呈现:
 * 遥控器上下移动焦点, 确认即选择, 返回键关闭.
 *
 * 手机端的 `DropdownMenu` 依赖指针输入, 在遥控器上无法操作, 因此 TV 侧不复用该 composable,
 * 只复用共享的状态层与时区列表.
 *
 * @param timeZone 当前生效的时区
 * @param onDismiss 关闭弹窗 (返回键或选中后)
 * @param onSelectTimeZone 用户选择了某个时区; 传 `null` 表示跟随系统时区
 */
@Composable
internal fun TvTimeZoneDialog(
    timeZone: TimeZone,
    onDismiss: () -> Unit,
    onSelectTimeZone: (TimeZone?) -> Unit,
) {
    val title = stringResource(Lang.exploration_schedule_time_zone)
    val systemLabel = stringResource(Lang.exploration_schedule_time_zone_system)

    // 偏移只用于展示, 每次组合按当前时刻重新计算, 夏令时切换后无需重启页面即可更新.
    val now = Clock.System.now()
    val systemTimeZone = TimeZone.currentSystemDefault()

    val options = remember(systemLabel) {
        buildList {
            add(
                TvTimeZoneOption(
                    id = null,
                    label = systemLabel,
                    description = "${systemTimeZone.id} · ${formatUtcOffset(systemTimeZone.offsetAt(now))}",
                ),
            )
            for (id in ScheduleSelectableTimeZones) {
                // 时区数据库理论上可能缺少某个 ID; 缺失时跳过, 而不是让时间表页面崩溃.
                val zone = runCatching { TimeZone.of(id) }.getOrNull() ?: continue
                add(
                    TvTimeZoneOption(
                        id = id,
                        label = id,
                        description = formatUtcOffset(zone.offsetAt(now)),
                    ),
                )
            }
        }
    }

    // 跟随系统时系统时区不出现在列表里, 因此此时落到"系统"一项上.
    val selectedId = timeZone.id
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = options.indexOfFirst { it.id == selectedId }.coerceAtLeast(0),
    )

    TvOptionModal(title, width = 520.dp) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().testTag("tv-time-zone-options"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(options, key = { it.testTagSuffix }) { option ->
                TvOptionRow(
                    title = option.label,
                    value = option.description,
                    selected = option.id == selectedId,
                ) {
                    onSelectTimeZone(option.id?.let { runCatching { TimeZone.of(it) }.getOrNull() })
                    onDismiss()
                }
            }
        }
    }
}

private data class TvTimeZoneOption(
    /** `null` 表示跟随系统时区. */
    val id: String?,
    val label: String,
    val description: String,
) {
    val testTagSuffix: String get() = id ?: "system"
}
