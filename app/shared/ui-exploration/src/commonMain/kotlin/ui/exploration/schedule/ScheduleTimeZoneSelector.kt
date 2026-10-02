/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.widgets.SelectableDropdownMenuItem
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_schedule_time_zone
import me.him188.ani.app.ui.lang.exploration_schedule_time_zone_system
import org.jetbrains.compose.resources.stringResource

/**
 * 新番时间表可选时区. 系统时区单独处理 (见 [ScheduleTimeZoneSelector]), 因此不在此列表中.
 *
 * 顺序按放送时刻的常见关注度排列, 而不是按偏移量排序: 首先是中国与日本, 其余按偏移量由东向西.
 */
val ScheduleSelectableTimeZones: List<String> = listOf(
    "Asia/Shanghai",
    "Asia/Tokyo",
    "Asia/Seoul",
    "Asia/Taipei",
    "Asia/Hong_Kong",
    "Asia/Singapore",
    "Asia/Bangkok",
    "Asia/Kolkata",
    "Asia/Dubai",
    "Australia/Sydney",
    "Europe/Moscow",
    "Europe/Berlin",
    "Europe/Paris",
    "Europe/London",
    "UTC",
    "America/Sao_Paulo",
    "America/New_York",
    "America/Chicago",
    "America/Denver",
    "America/Los_Angeles",
)

/**
 * 把一个 UTC 偏移格式化为 `UTC+08:00` / `UTC-05:00` 形式.
 *
 * 不直接用 [UtcOffset.toString]: 它输出 ISO-8601 形式, 零偏移会变成 `Z` (即 `UTCZ`), 且缺少固定宽度,
 * 作为界面文案不可读.
 *
 * 手机端与 TV 端共用 ([me.him188.ani.tv.ui.schedule.TvTimeZoneDialog]), 因此不能是 internal.
 */
fun formatUtcOffset(offset: UtcOffset): String {
    val totalMinutes = offset.totalSeconds / 60
    val sign = if (totalMinutes < 0) "-" else "+"
    val absolute = if (totalMinutes < 0) -totalMinutes else totalMinutes
    val hours = absolute / 60
    val minutes = absolute % 60
    return "UTC$sign${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}

/**
 * 顶栏上的时区选择按钮: 点击后列出系统时区与 [ScheduleSelectableTimeZones].
 *
 * 每个选项显示时区 ID, 并附带该时区当前时刻的 UTC 偏移, 因此夏令时期间显示的偏移也是正确的.
 *
 * @param timeZone 当前生效的时区
 * @param onSelectTimeZone 用户选择了某个时区; 传 `null` 表示跟随系统时区
 */
@Composable
fun ScheduleTimeZoneSelector(
    timeZone: TimeZone,
    onSelectTimeZone: (TimeZone?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDropdown by rememberSaveable { mutableStateOf(false) }

    // 偏移只用于展示, 每次组合时按当前时刻重新计算, 因此夏令时切换后无需重启页面即可更新.
    val now = Clock.System.now()
    val systemLabel = stringResource(Lang.exploration_schedule_time_zone_system)
    val menuLabel = stringResource(Lang.exploration_schedule_time_zone)

    // 当前生效的时区 ID; 跟随系统时系统时区不在列表中, 因此会落到"系统"一项上.
    val selectedId = timeZone.id

    val options = remember(systemLabel) {
        buildList {
            val systemTimeZone = TimeZone.currentSystemDefault()
            add(
                ScheduleTimeZoneOption(
                    id = null,
                    label = systemLabel,
                    description = "${systemTimeZone.id} · ${formatUtcOffset(systemTimeZone.offsetAt(now))}",
                ),
            )
            for (id in ScheduleSelectableTimeZones) {
                // 时区数据库理论上可能缺少某个 ID; 缺失时跳过, 而不是让时间表页面崩溃.
                val zone = runCatching { TimeZone.of(id) }.getOrNull() ?: continue
                add(
                    ScheduleTimeZoneOption(
                        id = id,
                        label = id,
                        description = formatUtcOffset(zone.offsetAt(now)),
                    ),
                )
            }
        }
    }

    Box(modifier) {
        IconButton(
            onClick = { showDropdown = true },
            modifier = Modifier.testTag("schedule-time-zone-selector"),
        ) {
            // 用默认的 LocalContentColor 而非主题主色: 顶栏上是主色时对比度不足.
            Icon(Icons.Rounded.Public, contentDescription = menuLabel)
        }

        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
            for (option in options) {
                SelectableDropdownMenuItem(
                    selected = option.id == selectedId,
                    text = { Text("${option.label} · ${option.description}") },
                    onClick = {
                        showDropdown = false
                        onSelectTimeZone(option.id?.let { runCatching { TimeZone.of(it) }.getOrNull() })
                    },
                    modifier = Modifier.testTag("schedule-time-zone-option-${option.testTagSuffix}"),
                )
            }
        }
    }
}

private data class ScheduleTimeZoneOption(
    /** `null` 表示跟随系统时区. */
    val id: String?,
    val label: String,
    val description: String,
) {
    val testTagSuffix: String get() = id ?: "system"
}

@Preview
@Composable
private fun PreviewScheduleTimeZoneSelector() {
    ProvideCompositionLocalsForPreview {
        ScheduleTimeZoneSelector(
            timeZone = TimeZone.of("Asia/Tokyo"),
            onSelectTimeZone = {},
        )
    }
}
