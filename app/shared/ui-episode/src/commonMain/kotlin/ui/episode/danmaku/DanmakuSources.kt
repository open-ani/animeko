/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.episode.danmaku

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import me.him188.ani.app.ui.foundation.Res
import me.him188.ani.app.ui.foundation.a
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_danmaku_rematch
import me.him188.ani.app.ui.lang.subject_episode_danmaku_service_baha_short
import me.him188.ani.app.ui.lang.subject_episode_danmaku_service_bilibili_short
import me.him188.ani.app.ui.lang.subject_episode_danmaku_service_dandanplay_short
import me.him188.ani.app.ui.lang.subject_episode_danmaku_time_shift_item
import me.him188.ani.app.ui.lang.subject_episode_disable
import me.him188.ani.app.ui.lang.subject_episode_enable
import me.him188.ani.app.ui.lang.subject_episode_more_options
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.utils.platform.format1f
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 弹幕源选择项数据类
 */
data class DanmakuSourceItem(
    val serviceId: DanmakuServiceId,
    val enabled: Boolean,
    val matchMethod: DanmakuMatchMethod,
    val shiftMillis: Long,
    val count: Int,
) {
    val isExactMatch: Boolean
        get() = matchMethod is DanmakuMatchMethod.Exact || matchMethod is DanmakuMatchMethod.ExactId
}

/**
 * 弹幕源选择器组件，以FlowRow布局显示所有可用的弹幕源。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DanmakuSourceChips(
    sourceItems: List<DanmakuSourceItem>,
    onToggleSource: (DanmakuServiceId, Boolean) -> Unit,
    onManualMatch: (DanmakuServiceId) -> Unit,
    onAdjustShift: (DanmakuServiceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sourceItems.forEach { sourceItem ->
            key(sourceItem.serviceId) {
                DanmakuSourceMenuAnchor(
                    sourceItem = sourceItem,
                    onToggle = { onToggleSource(sourceItem.serviceId, !sourceItem.enabled) },
                    onManualMatch = { onManualMatch(sourceItem.serviceId) },
                    onAdjustShift = { onAdjustShift(sourceItem.serviceId) },
                )
            }
        }
    }
}

@Composable
private fun DanmakuSourceMenuAnchor(
    sourceItem: DanmakuSourceItem,
    onToggle: () -> Unit,
    onManualMatch: () -> Unit,
    onAdjustShift: () -> Unit,
) {
    var showDropdown by rememberSaveable { mutableStateOf(false) }
    val isAnimeko = sourceItem.serviceId == DanmakuServiceId.Animeko
    val moreOptionsText = stringResource(Lang.subject_episode_more_options)
    Box {
        FilterChip(
            selected = sourceItem.enabled,
            onClick = onToggle,
            modifier = Modifier
                .testTag("danmaku-source-${sourceItem.serviceId.value}"),
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isAnimeko) 4.dp else (-4).dp),
                ) {
                    Text(if (sourceItem.count == 0) renderDanmakuServiceId(sourceItem.serviceId) else "${sourceItem.count}")

                    Icon(
                        Icons.Outlined.ArrowDropDown,
                        contentDescription = moreOptionsText,
                        modifier = Modifier
                            .offset(x = 8.dp)
                            .testTag("danmaku-source-menu-${sourceItem.serviceId.value}")
                            .clickable { showDropdown = true },
                    )
                }
            },
            leadingIcon = {
                DanmakuServiceIcon(
                    serviceId = sourceItem.serviceId,
                    size = 24,
                )
            },
            colors = if (sourceItem.enabled && !sourceItem.isExactMatch) {
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            } else {
                FilterChipDefaults.filterChipColors()
            },
        )

        DanmakuSourceSettingsDropdown(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            enabled = sourceItem.enabled,
            onSetEnabled = { onToggle() },
            currentShiftMillis = sourceItem.shiftMillis,
            onClickAdjustShift = onAdjustShift,
            onClickChange = onManualMatch.takeUnless { isAnimeko },
            serviceId = sourceItem.serviceId,
        )
    }
}

/** 来源操作菜单；可选的匹配操作由调用方决定是否提供。 */
@Composable
fun DanmakuSourceSettingsDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    enabled: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    currentShiftMillis: Long,
    onClickAdjustShift: () -> Unit,
    onClickChange: (() -> Unit)?,
    modifier: Modifier = Modifier,
    serviceId: DanmakuServiceId? = null,
    changeText: String = stringResource(Lang.subject_episode_danmaku_rematch),
) {
    DropdownMenu(expanded, onDismissRequest, modifier) {
        if (serviceId != null) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DanmakuServiceIcon(serviceId, size = 24)
                Text(renderDanmakuServiceId(serviceId), style = MaterialTheme.typography.titleSmall)
            }
            HorizontalDivider()
        }
        DropdownMenuItem(
            modifier = Modifier.testTag("danmaku-source-toggle"),
            text = { Text(stringResource(if (enabled) Lang.subject_episode_disable else Lang.subject_episode_enable)) },
            leadingIcon = { Icon(if (enabled) Icons.Outlined.Close else Icons.Outlined.CheckCircle, null) },
            onClick = {
                onSetEnabled(!enabled)
                onDismissRequest()
            },
        )
        if (onClickChange != null) {
            DropdownMenuItem(
                modifier = Modifier.testTag("danmaku-source-rematch"),
                text = { Text(changeText) },
                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                onClick = {
                    onClickChange()
                    onDismissRequest()
                },
            )
        }
        DropdownMenuItem(
            modifier = Modifier.testTag("danmaku-source-shift"),
            text = {
                Text(stringResource(Lang.subject_episode_danmaku_time_shift_item, formatDanmakuShiftMillis(currentShiftMillis)))
            },
            leadingIcon = { Icon(Icons.Outlined.Schedule, null) },
            onClick = {
                onClickAdjustShift()
                onDismissRequest()
            },
        )
    }
}

@Composable
fun DanmakuServiceIcon(
    serviceId: DanmakuServiceId,
    size: Int,
    modifier: Modifier = Modifier,
) {
    when (serviceId) {
        DanmakuServiceId.Animeko -> {
            Image(
                painter = painterResource(Res.drawable.a),
                contentDescription = renderDanmakuServiceId(serviceId),
                modifier = modifier
                    .size(size.dp)
                    .clip(CircleShape),
            )
        }

        else -> {
            val text = getDanmakuServiceIconInfo(serviceId)
            Box(
                modifier = modifier
                    .size(size.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = (size * 0.6).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * 弹幕源的显示文字
 */
@Composable
private fun getDanmakuServiceIconInfo(serviceId: DanmakuServiceId): String {
    return when (serviceId) {
        DanmakuServiceId.Bilibili -> stringResource(Lang.subject_episode_danmaku_service_bilibili_short)
        DanmakuServiceId.Dandanplay -> stringResource(Lang.subject_episode_danmaku_service_dandanplay_short)
        DanmakuServiceId.AcFun -> "Ac"
        DanmakuServiceId.Baha -> stringResource(Lang.subject_episode_danmaku_service_baha_short)
        DanmakuServiceId.Tucao -> "TC"
        else -> "?"
    }
}

fun formatDanmakuShiftMillis(shiftMillis: Long): String {
    if (shiftMillis == 0L) return "0 ms"
    val sign = if (shiftMillis > 0) "+" else "-"
    val absMillis = abs(shiftMillis)
    return if (absMillis >= 1_000) {
        val seconds = absMillis / 1_000.0
        "$sign${String.format1f(seconds)} s"
    } else {
        "$sign$absMillis ms"
    }
}
