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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.widgets.SelectableDropdownMenuItem
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_schedule_time_zone
import me.him188.ani.app.ui.lang.exploration_schedule_time_zone_system_with_zone
import org.jetbrains.compose.resources.stringResource

/**
 * 时间表可选的 UTC 偏移, 单位为分钟, 按偏移由负到正升序 (-720 … +840, 即 -12:00 … +14:00).
 *
 * 包含实际在用的 30 / 45 分钟偏移 (例如 +05:30、+05:45、+08:45).
 *
 * 这些是**固定偏移**, 不随夏令时变化. 需要自动跟随夏令时的用户应选"系统时区"一项,
 * 它读取系统当前时区并随时区数据库调整.
 */
val ScheduleSelectableUtcOffsetMinutes: List<Int> = buildList {
    // 整点偏移
    for (hour in -12..14) add(hour * 60)
    // 半小时 / 45 分钟偏移
    addAll(
        listOf(
            -570, // -09:30
            -210, // -03:30
            210, // +03:30
            270, // +04:30
            330, // +05:30
            345, // +05:45
            390, // +06:30
            525, // +08:45
            570, // +09:30
            630, // +10:30
            765, // +12:45
            825, // +13:45
        ),
    )
}
    .distinct()
    .sorted()

/** 把分钟偏移写成 ISO-8601 形式, 例如 `+08:00` / `-05:30`. */
private fun isoOffsetId(totalMinutes: Int): String {
    val sign = if (totalMinutes < 0) "-" else "+"
    val absolute = if (totalMinutes < 0) -totalMinutes else totalMinutes
    val hours = absolute / 60
    val minutes = absolute % 60
    return "$sign${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}

/**
 * 固定偏移对应的 [TimeZone].
 *
 * 零偏移用 [TimeZone.UTC], 避免固定偏移时区在零偏移下取到 `Z` 这类需要额外解析规则的 ID.
 */
fun timeZoneOfUtcOffsetMinutes(totalMinutes: Int): TimeZone =
    if (totalMinutes == 0) TimeZone.UTC else TimeZone.of(isoOffsetId(totalMinutes))

/**
 * 把分钟偏移格式化为 `UTC+08:00` / `UTC-05:00` 形式.
 *
 * 不直接用 [UtcOffset.toString]: 它输出 ISO-8601 形式, 零偏移会变成 `Z` (即 `UTCZ`), 且缺少固定宽度,
 * 作为界面文案不可读.
 *
 * 手机端与 TV 端共用 ([me.him188.ani.tv.ui.schedule.TvTimeZoneDialog]), 因此不能是 internal.
 */
fun formatUtcOffset(totalMinutes: Int): String = "UTC${isoOffsetId(totalMinutes)}"

/** [formatUtcOffset] 的 [UtcOffset] 重载, 供已有调用点使用. */
fun formatUtcOffset(offset: UtcOffset): String = formatUtcOffset(offset.totalSeconds / 60)

/**
 * 顶栏上的时区选择按钮: 点击后列出系统时区与全部 UTC 偏移 ([ScheduleSelectableUtcOffsets]).
 *
 * 菜单高度上限按窗口高度的比例计算, 使菜单总能完整落在入口按钮下方; 否则菜单会被窗口约束
 * 向上挤压, 顶到标题栏的窗口按钮 (最小化 / 最大化 / 关闭) 上.
 *
 * @param selectedTimeZone 当前的时区偏好. **必须传原始偏好而不是解析后的时区**: `null` 表示"跟随系统时区",
 *   与"恰好等于系统时区的固定选择"是两种持久化行为不同的状态, 只有原始偏好能区分它们.
 * @param onSelectTimeZone 用户选择了某个时区; 传 `null` 表示跟随系统时区
 */
@Composable
fun ScheduleTimeZoneSelector(
    selectedTimeZone: TimeZone?,
    onSelectTimeZone: (TimeZone?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDropdown by rememberSaveable { mutableStateOf(false) }

    // 偏移只用于展示, 每次组合时按当前时刻重新计算, 因此夏令时切换后无需重启页面即可更新.
    val now = Clock.System.now()
    val menuLabel = stringResource(Lang.exploration_schedule_time_zone)

    // 跟随系统时系统时区不在列表中, 因此此时会落到"系统"一项上.
    val selectedId = selectedTimeZone?.id

    val systemTimeZone = TimeZone.currentSystemDefault()
    val systemDescription = "${systemTimeZone.id} · ${formatUtcOffset(systemTimeZone.offsetAt(now))}"
    // 第一项显示"跟随系统时区（<系统时区> <偏移>）"; 括号形态由各语言的字符串资源决定.
    val systemOptionLabel = stringResource(Lang.exploration_schedule_time_zone_system_with_zone, systemDescription)

    val options = remember(systemOptionLabel) {
        buildList {
            add(
                ScheduleTimeZoneOption(
                    id = null,
                    label = systemOptionLabel,
                    description = "",
                ),
            )
            for (minutes in ScheduleSelectableUtcOffsetMinutes) {
                add(
                    ScheduleTimeZoneOption(
                        id = timeZoneOfUtcOffsetMinutes(minutes).id,
                        label = formatUtcOffset(minutes),
                        description = "",
                    ),
                )
            }
        }
    }

    // 菜单高度按窗口高度限制, 保证它完整落在入口按钮下方, 不会向上顶到窗口标题栏按钮.
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current
    val menuMaxHeight = with(density) { (windowHeight * 0.55f).toDp() }.coerceIn(200.dp, 420.dp)

    Box(modifier) {
        IconButton(
            onClick = { showDropdown = true },
            modifier = Modifier.testTag("schedule-time-zone-selector"),
        ) {
            // 用默认的 LocalContentColor 而非主题主色: 顶栏上是主色时对比度不足.
            Icon(Icons.Rounded.Public, contentDescription = menuLabel)
        }

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            modifier = Modifier.heightIn(max = menuMaxHeight).testTag("schedule-time-zone-menu"),
            offset = DpOffset(0.dp, 4.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            for (option in options) {
                SelectableDropdownMenuItem(
                    selected = option.id == selectedId,
                    text = {
                        Text(
                            if (option.description.isEmpty()) {
                                option.label
                            } else {
                                "${option.label} · ${option.description}"
                            },
                        )
                    },
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
            selectedTimeZone = timeZoneOfUtcOffsetMinutes(9 * 60),
            onSelectTimeZone = {},
        )
    }
}

@Preview
@Composable
private fun PreviewScheduleTimeZoneSelectorFollowingSystem() {
    ProvideCompositionLocalsForPreview {
        ScheduleTimeZoneSelector(
            selectedTimeZone = null,
            onSelectTimeZone = {},
        )
    }
}
