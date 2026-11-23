/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.time

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.domain.settings.toTimeZoneOrNull
import me.him188.ani.app.platform.LocationFailureKind
import me.him188.ani.app.platform.LocationTimeZoneProvider
import me.him188.ani.app.platform.LocationTimeZoneUiState
import me.him188.ani.app.platform.UnsupportedLocationTimeZoneProvider
import me.him188.ani.app.platform.currentSystemTimeZone
import me.him188.ani.app.platform.toUiState
import me.him188.ani.app.tools.LocalTimeZone
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_friday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_monday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_saturday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_sunday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_thursday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_tuesday
import me.him188.ani.app.ui.lang.exploration_schedule_weekday_wednesday
import me.him188.ani.app.ui.lang.settings_time_zone_clock_date
import me.him188.ani.app.ui.lang.settings_time_zone_clock_weekday
import me.him188.ani.app.ui.lang.settings_time_zone_current
import me.him188.ani.app.ui.lang.settings_time_zone_follow_system
import me.him188.ani.app.ui.lang.settings_time_zone_follow_system_row
import me.him188.ani.app.ui.lang.settings_time_zone_id_with_offset
import me.him188.ani.app.ui.lang.settings_time_zone_locate
import me.him188.ani.app.ui.lang.settings_time_zone_locating
import me.him188.ani.app.ui.lang.settings_time_zone_location_failed
import me.him188.ani.app.ui.lang.settings_time_zone_match_failed
import me.him188.ani.app.ui.lang.settings_time_zone_search_hint
import me.him188.ani.app.ui.lang.settings_time_zone_unsupported_platform
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.rememberTestSettingsState
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * 时区设置页元素的测试标记.
 */
object TimeZoneSettingsTestTags {
    const val SEARCH = "time-zone-settings-search"
    const val CURRENT_ZONE = "time-zone-settings-current"
    const val CLOCK_AREA = "time-zone-settings-clock-area"
    const val CLOCK = "time-zone-settings-clock"
    const val CLOCK_DATE = "time-zone-settings-clock-date"
    const val CLOCK_TIME = "time-zone-settings-clock-time"
    const val LOCATE_BOX = "time-zone-settings-locate-box"
    const val LOCATE_LABEL = "time-zone-settings-locate-label"
    const val FOLLOW_SYSTEM_ROW = "time-zone-settings-follow-system-row"
    const val SELECTED_ROW = "time-zone-settings-selected-row"
    const val LIST = "time-zone-settings-list"

    fun zoneRow(zoneId: String) = "time-zone-settings-zone-$zoneId"

    fun sectionHeader(region: String) = "time-zone-settings-section-$region"
}

/**
 * 竖屏与宽屏时钟布局的宽度分界.
 */
private val ClockLayoutWidthThreshold = 600.dp
private val ClockSize = 168.dp
private val ItemHorizontalPadding = 16.dp

/**
 * 窄屏时钟头部折叠后的表盘尺寸.
 */
private val CollapsedClockSize = 72.dp

/**
 * 列表上滑超过该距离后, 窄屏时钟头部进入折叠态.
 */
private val ClockCollapseThreshold = 48.dp

/**
 * 时钟头部在展开与折叠之间的变形时长.
 */
private const val ClockCollapseDurationMillis = 300

/**
 * 窄屏展开态里表盘与日期、日期与时间之间的间距.
 */
private val ClockTextGap = 12.dp

/**
 * 宽屏时钟区右列里日期与时间之间的间距.
 */
private val ClockTimeGap = 8.dp

/**
 * 宽屏时钟区右列里表盘与文字之间的间距.
 */
private val ClockColumnSpacing = 32.dp

/**
 * 宽屏时钟区右列时间的可用高度下限, 相对表盘高度. 极端文字缩放下仍保证数字可见.
 */
private const val ClockTimeMinHeightFraction = 0.4f

/**
 * 宽屏右列时间字号的收缩余量, 避免亚像素误差让行盒比可用高度多出一点.
 */
private const val ClockTimeSafetyScale = 0.995f

/**
 * 折叠的分组在重组与配置变更后保持状态; 不写入设置, 重启页面即恢复全部分组展开.
 */
private val CollapsedRegionsSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

/**
 * 时区设置二级页.
 *
 * 页面自上而下是搜索框、「当前时区」小字、时钟、定位框与时区列表. 列表自带 [LazyColumn],
 * 因此本页面要放在不接受外层滚动的容器里.
 *
 * 显示用的时区读 [LocalTimeZone], 写入只改 [UISettings.timeZoneId].
 *
 * @param locationTimeZoneProvider 定位实现. 生产环境由 Koin 提供, 测试与预览应显式传入.
 */
@Composable
fun TimeZoneSettingsPage(
    state: SettingsState<UISettings>,
    modifier: Modifier = Modifier,
    locationTimeZoneProvider: LocationTimeZoneProvider = rememberDefaultLocationTimeZoneProvider(),
) {
    val uiSettings by state
    val selectedTimeZoneId = uiSettings.timeZoneId
    val timeZone = LocalTimeZone.current
    val systemTimeZone = remember { currentSystemTimeZone() }
    // 目录只用于排序与展示偏移, 打开页面时取一次, 避免每秒重算全部时区
    val catalogNow = remember { Clock.System.now() }
    val sections = remember(catalogNow, systemTimeZone) { timeZoneCatalog(catalogNow, systemTimeZone) }
    var query by rememberSaveable { mutableStateOf("") }
    var collapsedRegions by rememberSaveable(stateSaver = CollapsedRegionsSaver) {
        mutableStateOf(emptySet<String>())
    }

    val now = rememberTickingInstant()
    val localDateTime = remember(now, timeZone) { now.toLocalDateTime(timeZone) }

    val systemZoneOffsetLabel = remember(systemTimeZone, catalogNow) {
        formatUtcOffset(systemTimeZone.offsetAt(catalogNow).totalSeconds)
    }
    val selectedZone = selectedTimeZoneId.toTimeZoneOrNull()
    val currentZoneLabel = if (selectedZone == null) {
        stringResource(Lang.settings_time_zone_follow_system, systemTimeZone.id)
    } else {
        stringResource(
            Lang.settings_time_zone_id_with_offset,
            selectedZone.id,
            formatUtcOffset(selectedZone.offsetAt(catalogNow).totalSeconds),
        )
    }
    val selectedPinLabel = selectedZone?.let { zone ->
        stringResource(
            Lang.settings_time_zone_id_with_offset,
            zone.id,
            formatUtcOffset(zone.offsetAt(catalogNow).totalSeconds),
        )
    }
    val followSystemRowLabel = stringResource(
        Lang.settings_time_zone_follow_system_row,
        systemTimeZone.id,
        systemZoneOffsetLabel,
    )

    val rows = remember(
        sections,
        query,
        selectedTimeZoneId,
        selectedPinLabel,
        followSystemRowLabel,
        collapsedRegions,
    ) {
        buildTimeZoneRows(
            sections = filterTimeZoneSections(sections, query),
            query = query,
            collapsedRegions = collapsedRegions,
            selectedTimeZoneId = selectedTimeZoneId,
            selectedPinLabel = selectedPinLabel,
            systemZoneId = systemTimeZone.id,
            followSystemRowLabel = followSystemRowLabel,
        )
    }

    val selectTimeZone: (String?) -> Unit = { zoneId -> state.update(uiSettings.copy(timeZoneId = zoneId)) }

    val capability = rememberLocationTimeZoneCapability()
    var locateState by remember(capability) {
        mutableStateOf(initialLocateState(capability.supported))
    }
    val scope = rememberCoroutineScope()
    val locateTimeZone: () -> Unit = {
        locateState = LocateState.Locating
        capability.requestPermission { granted ->
            if (granted) {
                scope.launch {
                    locateState = LocateState.Result(locationTimeZoneProvider.locateTimeZone().toUiState())
                }
            } else {
                locateState = LocateState.Result(
                    LocationTimeZoneUiState.Failure(LocationFailureKind.PermissionDenied, canRetry = true),
                )
            }
        }
    }

    // 窄屏时钟头部的两段式折叠: 列表上滑越过阈值即折叠, 回到顶部后继续下拉才恢复.
    val listState = rememberLazyListState()
    var clockCollapsed by rememberSaveable { mutableStateOf(false) }
    val collapseThresholdPx = with(LocalDensity.current) { ClockCollapseThreshold.toPx() }
    LaunchedEffect(listState, collapseThresholdPx) {
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > collapseThresholdPx
        }.filter { it }.collect { clockCollapsed = true }
    }
    val clockCollapseScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 只观察不消费, 列表与顶栏照常处理这次滚动.
                if (source == NestedScrollSource.UserInput && available.y > 0f && !listState.canScrollBackward) {
                    clockCollapsed = false
                }
                return Offset.Zero
            }
        }
    }
    // 一旦越过阈值就补间到终点, 不存在停在中间的表盘.
    val clockCollapsedFraction by animateFloatAsState(
        targetValue = if (clockCollapsed) 1f else 0f,
        animationSpec = tween(durationMillis = ClockCollapseDurationMillis, easing = FastOutSlowInEasing),
        label = "timeZoneClockCollapse",
    )

    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 窄屏的搜索框与两侧留白对齐; 宽屏保持满宽, 与时钟区、分割线同边.
            val searchHorizontalPadding =
                if (maxWidth < ClockLayoutWidthThreshold) ItemHorizontalPadding else 0.dp
            TimeZoneSearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = searchHorizontalPadding, vertical = 8.dp),
            )
        }
        Text(
            text = stringResource(Lang.settings_time_zone_current, currentZoneLabel),
            modifier = Modifier
                .padding(horizontal = ItemHorizontalPadding)
                .testTag(TimeZoneSettingsTestTags.CURRENT_ZONE),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TimeZoneClockArea(
            dateTime = localDateTime,
            collapsedFraction = clockCollapsedFraction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        )
        LocationTimeZoneBox(
            locateState = locateState,
            onLocate = locateTimeZone,
            onApply = { zoneId -> selectTimeZone(zoneId) },
            modifier = Modifier
                .padding(horizontal = ItemHorizontalPadding)
                .padding(bottom = 24.dp),
        )
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(clockCollapseScrollConnection)
                .testTag(TimeZoneSettingsTestTags.LIST),
            state = listState,
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                val showDivider = index != rows.lastIndex
                when (row) {
                    is TimeZoneListRow.SectionHeader -> TimeZoneSectionHeader(
                        header = row,
                        showDivider = showDivider,
                        onToggleExpand = {
                            collapsedRegions = if (row.region in collapsedRegions) {
                                collapsedRegions - row.region
                            } else {
                                collapsedRegions + row.region
                            }
                        },
                    )

                    is TimeZoneListRow.Entry -> TimeZoneEntryRow(
                        row = row,
                        showDivider = showDivider,
                        onClick = { selectTimeZone(row.selectedZoneId) },
                    )
                }
            }
        }
    }
}

/**
 * 定位框的请求状态. 只有 [Result] 保存定位结果, 请求进行中与未请求是两个独立状态.
 */
private sealed interface LocateState {
    data object Idle : LocateState
    data object Locating : LocateState
    data class Result(val state: LocationTimeZoneUiState) : LocateState
}

private fun initialLocateState(supported: Boolean): LocateState {
    return if (supported) {
        LocateState.Idle
    } else {
        LocateState.Result(LocationTimeZoneUiState.Failure(LocationFailureKind.UnsupportedPlatform, canRetry = false))
    }
}

private sealed interface TimeZoneListRow {
    val key: String

    /**
     * 分组标题. [expanded] 为 false 时该组只保留标题行.
     */
    data class SectionHeader(val region: String, val expanded: Boolean) : TimeZoneListRow {
        override val key: String get() = "header:$region"
    }

    /**
     * @param selectedZoneId 点击后写入的值. 「跟随系统」为 null.
     * @param label 置顶项的完整文案; 分组项为 null, 直接显示时区 ID.
     */
    data class Entry(
        override val key: String,
        val selectedZoneId: String?,
        val zoneId: String,
        val offsetLabel: String,
        val selected: Boolean,
        val pinned: Boolean,
        val testTag: String,
        val label: String? = null,
    ) : TimeZoneListRow
}

/**
 * 置顶区在前 (选中时区、跟随系统), 之后是分组列表. 置顶项与时区项都参与 [query] 过滤.
 *
 * 置顶区不属于任何分组, 不受 [collapsedRegions] 影响; [query] 非空时忽略折叠状态, 强制展开每个分组.
 */
private fun buildTimeZoneRows(
    sections: List<TimeZoneSection>,
    query: String,
    collapsedRegions: Set<String>,
    selectedTimeZoneId: String?,
    selectedPinLabel: String?,
    systemZoneId: String,
    followSystemRowLabel: String,
): List<TimeZoneListRow> {
    val rows = mutableListOf<TimeZoneListRow>()

    if (selectedTimeZoneId != null && selectedPinLabel != null &&
        timeZoneQueryMatches(query, selectedPinLabel, selectedTimeZoneId)
    ) {
        rows += TimeZoneListRow.Entry(
            key = "pin:selected",
            selectedZoneId = selectedTimeZoneId,
            zoneId = selectedTimeZoneId,
            offsetLabel = "",
            selected = true,
            pinned = true,
            testTag = TimeZoneSettingsTestTags.SELECTED_ROW,
            label = selectedPinLabel,
        )
    }
    if (timeZoneQueryMatches(query, followSystemRowLabel, systemZoneId)) {
        rows += TimeZoneListRow.Entry(
            key = "pin:follow-system",
            selectedZoneId = null,
            zoneId = systemZoneId,
            offsetLabel = "",
            selected = selectedTimeZoneId == null,
            pinned = true,
            testTag = TimeZoneSettingsTestTags.FOLLOW_SYSTEM_ROW,
            label = followSystemRowLabel,
        )
    }

    val forceExpand = query.isNotBlank()
    for (section in sections) {
        val expanded = forceExpand || section.region !in collapsedRegions
        rows += TimeZoneListRow.SectionHeader(section.region, expanded)
        if (!expanded) continue
        for (option in section.options) {
            rows += TimeZoneListRow.Entry(
                key = "zone:${option.zoneId}",
                selectedZoneId = option.zoneId,
                zoneId = option.zoneId,
                offsetLabel = option.offsetLabel,
                selected = option.zoneId == selectedTimeZoneId,
                pinned = false,
                testTag = TimeZoneSettingsTestTags.zoneRow(option.zoneId),
            )
        }
    }
    return rows
}

@Composable
private fun rememberTickingInstant(): Instant {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1.seconds)
            now = Clock.System.now()
        }
    }
    return now
}

@Composable
private fun TimeZoneSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.testTag(TimeZoneSettingsTestTags.SEARCH),
        placeholder = { Text(stringResource(Lang.settings_time_zone_search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * 时钟区. 宽度小于 [ClockLayoutWidthThreshold] 时, 表盘、日期与时间在展开态里纵向居中排列,
 * 在折叠态里排成左上角小表盘加右侧上下两行, 两种状态之间由 [collapsedFraction] 连续插值.
 * 宽度足够时表盘在左, 右侧一列等高排布日期与时间, 两行的行高受表盘高度约束,
 * 因此始终不重叠也不越出表盘.
 */
@Composable
private fun TimeZoneClockArea(
    dateTime: LocalDateTime,
    collapsedFraction: Float,
    modifier: Modifier = Modifier,
) {
    val dateLabel = stringResource(
        Lang.settings_time_zone_clock_date,
        dateTime.month.number,
        dateTime.day,
    )
    val weekdayLabel = stringResource(Lang.settings_time_zone_clock_weekday, weekdayName(dateTime.dayOfWeek))
    val dateWeekdayLabel = "$dateLabel $weekdayLabel"
    val timeLabel = formatClockTime(dateTime)
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(modifier.testTag(TimeZoneSettingsTestTags.CLOCK_AREA)) {
        if (maxWidth < ClockLayoutWidthThreshold) {
            NarrowClockArea(
                time = dateTime.time,
                dateText = dateWeekdayLabel,
                timeText = timeLabel,
                secondaryColor = secondaryColor,
                collapsedFraction = collapsedFraction,
                availableWidth = maxWidth,
            )
        } else {
            val dateStyle = MaterialTheme.typography.bodyMedium
            // 两行共用表盘高度: 日期贴表盘顶边, 时间贴表盘底边. 时间一行的字号由可用高度换算,
            // 因此数字占满日期下方到表盘底边之间的空间; 右列放不下时再按宽度收缩.
            val density = LocalDensity.current
            val dateHeight = with(density) { dateStyle.lineHeight.toDp() }
            val timeHeight = (ClockSize - dateHeight - ClockTimeGap)
                .coerceAtLeast(ClockSize * ClockTimeMinHeightFraction)
            val wideTimeStyle = rememberClockTimeStyle(
                text = timeLabel,
                height = timeHeight,
                width = maxWidth - ClockSize - ClockColumnSpacing,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                AnalogClock(
                    time = dateTime.time,
                    size = ClockSize,
                    modifier = Modifier.testTag(TimeZoneSettingsTestTags.CLOCK),
                )
                Spacer(Modifier.width(ClockColumnSpacing))
                Column(
                    Modifier.height(ClockSize).weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = dateWeekdayLabel,
                        modifier = Modifier.testTag(TimeZoneSettingsTestTags.CLOCK_DATE),
                        style = dateStyle,
                        color = secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = timeLabel,
                        modifier = Modifier.testTag(TimeZoneSettingsTestTags.CLOCK_TIME),
                        style = wideTimeStyle,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 窄屏时钟区. [collapsedFraction] 为 0 时表盘居中、日期与时间依次排在其下; 为 1 时表盘缩到左上角,
 * 日期与时间变成表盘右侧的上下两行, 两行的行高合计等于表盘高度.
 *
 * 三个元素都按插值后的位置摆放, 因此变形过程中表盘与文字不会停在中间状态.
 */
@Composable
private fun NarrowClockArea(
    time: LocalTime,
    dateText: String,
    timeText: String,
    secondaryColor: Color,
    collapsedFraction: Float,
    availableWidth: Dp,
) {
    val dateStyle = MaterialTheme.typography.bodyMedium
    val expandedTimeStyle = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum")
    val dateHeight = with(LocalDensity.current) { dateStyle.lineHeight.toDp() }
    // 折叠后两行合计占满表盘高度: 时间一行吃掉日期之外的全部空间.
    val collapsedTimeHeight = (CollapsedClockSize - dateHeight)
        .coerceAtLeast(CollapsedClockSize * ClockTimeMinHeightFraction)
    val collapsedTimeStyle = rememberClockTimeStyle(
        text = timeText,
        height = collapsedTimeHeight,
        width = availableWidth - CollapsedClockSize - ClockColumnSpacing,
    )
    val timeStyle = expandedTimeStyle.lerpTo(collapsedTimeStyle, collapsedFraction)
    val clockSize = lerp(ClockSize, CollapsedClockSize, collapsedFraction)

    Layout(
        content = {
            AnalogClock(
                time = time,
                size = clockSize,
                modifier = Modifier.testTag(TimeZoneSettingsTestTags.CLOCK),
            )
            Text(
                text = dateText,
                modifier = Modifier.testTag(TimeZoneSettingsTestTags.CLOCK_DATE),
                style = dateStyle,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timeText,
                modifier = Modifier.testTag(TimeZoneSettingsTestTags.CLOCK_TIME),
                style = timeStyle,
                maxLines = 1,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val clock = measurables[0].measure(Constraints())
        val date = measurables[1].measure(Constraints(maxWidth = width))
        val time = measurables[2].measure(Constraints(maxWidth = width))

        val fraction = collapsedFraction.coerceIn(0f, 1f)
        // 抬升晚于收缩: 日期与时间先跟着表盘底边留在下方, 等表盘缩小、右列让出横向空间后再上移,
        // 变形过程中文字不会从表盘上滑过.
        val lift = fraction * fraction
        val textGap = ClockTextGap.toPx()
        val clockBottom = clock.height.toFloat()
        val centeredLeft = ((width - ClockSize.toPx()) / 2f) * (1f - fraction)
        // 折叠完成时表盘左端与「当前时区」文案对齐; 日期与时间的左边界保持折叠前的位置.
        val clockLeft = centeredLeft + ItemHorizontalPadding.toPx() * fraction
        val columnLeft = centeredLeft + clock.width + ClockColumnSpacing.toPx()
        val dateTop = lerp(clockBottom + textGap, 0f, lift)
        val timeTop = lerp(dateTop + date.height + textGap, clockBottom - time.height, lift)
        val dateLeft = lerp((width - date.width) / 2f, columnLeft, fraction)
        val timeLeft = lerp((width - time.width) / 2f, columnLeft, fraction)
        val height = maxOf(clockBottom, dateTop + date.height, timeTop + time.height).roundToInt()

        layout(width, height) {
            clock.place(clockLeft.roundToInt(), 0)
            date.place(dateLeft.roundToInt(), dateTop.roundToInt())
            time.place(timeLeft.roundToInt(), timeTop.roundToInt())
        }
    }
}

@Composable
private fun weekdayName(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> stringResource(Lang.exploration_schedule_weekday_monday)
    DayOfWeek.TUESDAY -> stringResource(Lang.exploration_schedule_weekday_tuesday)
    DayOfWeek.WEDNESDAY -> stringResource(Lang.exploration_schedule_weekday_wednesday)
    DayOfWeek.THURSDAY -> stringResource(Lang.exploration_schedule_weekday_thursday)
    DayOfWeek.FRIDAY -> stringResource(Lang.exploration_schedule_weekday_friday)
    DayOfWeek.SATURDAY -> stringResource(Lang.exploration_schedule_weekday_saturday)
    DayOfWeek.SUNDAY -> stringResource(Lang.exploration_schedule_weekday_sunday)
}

private fun formatClockTime(dateTime: LocalDateTime): String = buildString(8) {
    append(dateTime.hour.toString().padStart(2, '0'))
    append(':')
    append(dateTime.minute.toString().padStart(2, '0'))
    append(':')
    append(dateTime.second.toString().padStart(2, '0'))
}

/**
 * 宽屏右列时间一行的样式: 字号按 [height] 定, 使一整行（含字体自身的上下留白）正好占满 [height];
 * [width] 放不下这一行时再按同一比例缩小, 保证不横向溢出, 也不换行.
 *
 * 行盒高度与文字宽度都与字号成正比, 因此先按参考字号量一次, 再按比例换算即可一步到位.
 */
@Composable
private fun rememberClockTimeStyle(
    text: String,
    height: Dp,
    width: Dp,
): TextStyle {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.displaySmall
    return remember(text, height, width, baseStyle, density, textMeasurer) {
        val referenceSize = with(density) { height.toSp() }
        val style = baseStyle.copy(
            fontSize = referenceSize,
            lineHeight = referenceSize,
            fontFeatureSettings = "tnum",
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        )
        val measured = textMeasurer.measure(text, style).size
        val heightScale = if (measured.height == 0) 1f else with(density) { height.toPx() } / measured.height
        val widthScale = if (measured.width == 0) 1f else with(density) { width.toPx() } / measured.width
        // 留一点余量: 行盒若比可用高度多出不足一像素, 列布局会把它压扁并吃掉间距
        val scale = minOf(heightScale, widthScale) * ClockTimeSafetyScale
        style.copy(fontSize = referenceSize * scale, lineHeight = referenceSize * scale)
    }
}

/**
 * 在 [this] 与 [target] 之间按 [fraction] 插值字号与行高, 其余样式沿用 [this].
 *
 * 两个样式的字号都是同一密度下的 sp, 单位一致, 因此可以直接按数值插值.
 */
private fun TextStyle.lerpTo(target: TextStyle, fraction: Float): TextStyle {
    if (fraction <= 0f) return this
    if (fraction >= 1f) return target
    return copy(
        fontSize = lerpTextUnit(fontSize, target.fontSize, fraction),
        lineHeight = lerpTextUnit(lineHeight, target.lineHeight, fraction),
    )
}

private fun lerpTextUnit(start: TextUnit, stop: TextUnit, fraction: Float): TextUnit =
    (start.value + (stop.value - start.value) * fraction).sp

/**
 * 整块填充的定位框. 无描边; 平台不支持定位时置灰且不可点.
 *
 * 定位成功后框内显示匹配到的时区 ID, 再点一次才写入偏好.
 */
@Composable
private fun LocationTimeZoneBox(
    locateState: LocateState,
    onLocate: () -> Unit,
    onApply: (zoneId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val label = when (locateState) {
        LocateState.Idle -> stringResource(Lang.settings_time_zone_locate)
        LocateState.Locating -> stringResource(Lang.settings_time_zone_locating)
        is LocateState.Result -> when (val result = locateState.state) {
            is LocationTimeZoneUiState.Success -> result.zoneId
            is LocationTimeZoneUiState.Failure -> when (result.kind) {
                LocationFailureKind.PermissionDenied -> stringResource(Lang.settings_time_zone_location_failed)
                LocationFailureKind.LocateFailed -> stringResource(Lang.settings_time_zone_location_failed)
                LocationFailureKind.ZoneNotFound -> stringResource(Lang.settings_time_zone_match_failed)
                LocationFailureKind.UnsupportedPlatform ->
                    stringResource(Lang.settings_time_zone_unsupported_platform)
            }
        }
    }
    val enabled = when (locateState) {
        LocateState.Idle -> true
        LocateState.Locating -> false
        is LocateState.Result -> when (val result = locateState.state) {
            is LocationTimeZoneUiState.Success -> true
            is LocationTimeZoneUiState.Failure -> result.canRetry
        }
    }
    val result = (locateState as? LocateState.Result)?.state
    val containerColor = when {
        result is LocationTimeZoneUiState.Success -> scheme.primaryContainer
        result is LocationTimeZoneUiState.Failure && result.kind == LocationFailureKind.UnsupportedPlatform ->
            scheme.surfaceContainer

        else -> scheme.surfaceContainerHighest
    }
    val contentColor = when {
        result is LocationTimeZoneUiState.Success -> scheme.onPrimaryContainer
        result is LocationTimeZoneUiState.Failure && result.kind == LocationFailureKind.UnsupportedPlatform ->
            scheme.onSurfaceVariant.copy(alpha = 0.6f)

        result is LocationTimeZoneUiState.Failure -> scheme.error
        else -> scheme.onSurface
    }
    val iconColor = when {
        result is LocationTimeZoneUiState.Success -> scheme.primary
        result is LocationTimeZoneUiState.Failure -> contentColor
        else -> scheme.primary
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(enabled = enabled) {
                when (val current = locateState) {
                    LocateState.Idle -> onLocate()
                    LocateState.Locating -> Unit
                    is LocateState.Result -> when (val state = current.state) {
                        is LocationTimeZoneUiState.Success -> onApply(state.zoneId)
                        is LocationTimeZoneUiState.Failure -> onLocate()
                    }
                }
            }
            .testTag(TimeZoneSettingsTestTags.LOCATE_BOX)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = iconColor)
        Spacer(Modifier.width(12.dp))
        if (locateState == LocateState.Locating) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = iconColor)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            modifier = Modifier.testTag(TimeZoneSettingsTestTags.LOCATE_LABEL),
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
        )
    }
}

/**
 * 可点击的分组标题: 点击折叠或展开该组. 右侧箭头指向当前状态, 展开朝下, 折叠朝右.
 *
 * @param header 该组的标题行状态, 折叠时列表里不会出现该组的条目.
 */
@Composable
private fun TimeZoneSectionHeader(
    header: TimeZoneListRow.SectionHeader,
    showDivider: Boolean,
    onToggleExpand: () -> Unit,
) {
    val indicatorRotation by animateFloatAsState(
        targetValue = if (header.expanded) 0f else -90f,
        label = "timeZoneSectionIndicator",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .testTag(TimeZoneSettingsTestTags.sectionHeader(header.region))
                .padding(horizontal = ItemHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = header.region,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(indicatorRotation),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * 一行时区. 相邻两行共用一条分割线, 因此分割线画在行下方, 列表最后一行不画.
 */
@Composable
private fun TimeZoneEntryRow(
    row: TimeZoneListRow.Entry,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = row.selected, onClick = onClick)
                .testTag(row.testTag)
                .padding(horizontal = ItemHorizontalPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.label ?: row.zoneId,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.pinned) FontWeight.Medium else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!row.pinned) {
                Text(
                    text = row.offsetLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (row.selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Spacer(Modifier.size(20.dp))
            }
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * 生产环境的定位实现由 Koin 提供.
 */
@Composable
private fun rememberDefaultLocationTimeZoneProvider(): LocationTimeZoneProvider {
    return remember { KoinPlatform.getKoin().get<LocationTimeZoneProvider>() }
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewTimeZoneSettingsPage() = ProvideCompositionLocalsForPreview {
    TimeZoneSettingsPage(
        rememberTestSettingsState(UISettings.Default),
        locationTimeZoneProvider = UnsupportedLocationTimeZoneProvider,
    )
}
