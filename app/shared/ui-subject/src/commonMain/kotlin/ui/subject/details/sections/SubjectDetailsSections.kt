/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.ui.foundation.OutlinedTag
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_air_date
import me.him188.ani.app.ui.lang.subject_details_air_date_format
import me.him188.ani.app.ui.lang.subject_details_aliases
import me.him188.ani.app.ui.lang.subject_details_info
import me.him188.ani.app.ui.lang.subject_details_show_less
import me.him188.ani.app.ui.lang.subject_details_show_more
import me.him188.ani.app.ui.lang.subject_details_total_episodes
import me.him188.ani.datasources.api.PackedDate
import org.jetbrains.compose.resources.stringResource

/**
 * 区块标题行: 标题 (titleMedium) + 右侧任意 [action] 内容 (对齐 Figma `SectionHeader`).
 *
 * 常见 action 为 [SectionHeaderActionButton]; 选集区块在此槽位放分页控件/集数文案.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        action()
    }
}

/** 便捷重载: 右侧为 "label ›" 文本按钮 (对齐 Figma `SectionHeader` 链接变体). */
@Composable
fun SectionHeader(
    title: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionHeader(title, modifier) {
        if (actionLabel != null) {
            SectionHeaderActionButton(onAction) { Text(actionLabel) }
        }
    }
}

/** 区块标题行右侧的 "content ›" 文本按钮. */
@Composable
fun SectionHeaderActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    TextButton(onClick) {
        content()
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            Modifier.size(18.dp),
        )
    }
}

/**
 * 简介区块: 默认 5 行折叠, 溢出时显示右对齐的 "显示更多/收起".
 */
@Composable
fun SubjectSummarySection(
    summary: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 5,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                summary,
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) hasOverflow = it.hasVisualOverflow },
            )
        }
        if (hasOverflow || expanded) {
            TextButton(
                { expanded = !expanded },
                Modifier.align(Alignment.End),
            ) {
                Text(stringResource(if (expanded) Lang.subject_details_show_less else Lang.subject_details_show_more))
            }
        }
    }
}

private const val ALWAYS_SHOW_TAGS_COUNT = 8

/**
 * 标签区块 (对齐定稿: 全部 outline 样式). 折叠规则见 01 §6: ≤6 全显;
 * 否则热门(count>100)满 8 条则取热门, 不足则 take(8); 仅当总数 > 8 时显示展开/收起.
 */
@Composable
fun SubjectTagsSection(
    tags: List<Tag>,
    onClickTag: (Tag) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val presentTags = remember(tags, expanded) {
        when {
            expanded -> tags
            tags.size <= 6 -> tags
            else -> {
                val hot = tags.filter { it.count > 100 }
                if (hot.size < ALWAYS_SHOW_TAGS_COUNT) tags.take(ALWAYS_SHOW_TAGS_COUNT) else hot
            }
        }
    }
    val hasMore = tags.size > ALWAYS_SHOW_TAGS_COUNT
    Column(modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presentTags.forEach { tag ->
                OutlinedTag(Modifier.clickable { onClickTag(tag) }) {
                    Text(tag.name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (hasMore) {
            TextButton(
                { expanded = !expanded },
                Modifier.align(Alignment.End),
            ) {
                Text(stringResource(if (expanded) Lang.subject_details_show_less else Lang.subject_details_show_more))
            }
        }
    }
}

/**
 * 作品信息表. 键值对左右排布, 键固定宽 [labelWidth].
 *
 * 注意: 数据源限制 —— 平台/原作/动画制作/放送星期 需要 infobox/recurrence 数据, 当前 [SubjectInfo]
 * 未直接提供; 已提供的字段 ([SubjectInfo.airDate]/[mainEpisodeCount]/[SubjectInfo.aliases]) 先行展示.
 * 后续可扩充数据层再补全其余行 (放送星期 key `subject_details_air_weekday` 已备好).
 */
@Composable
fun SubjectInfoTable(
    info: SubjectInfo,
    mainEpisodeCount: Int?,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 78.dp,
    rowSpacing: Dp = 12.dp,
) {
    val rows = buildList {
        if (info.airDate.isValid) {
            add(stringResource(Lang.subject_details_air_date) to formatAirDate(info.airDate))
        }
        if (mainEpisodeCount != null && mainEpisodeCount > 0) {
            add(stringResource(Lang.subject_details_total_episodes) to mainEpisodeCount.toString())
        }
        if (info.aliases.isNotEmpty()) add(stringResource(Lang.subject_details_aliases) to info.aliases.joinToString(" / "))
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    label,
                    Modifier.width(labelWidth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current,
                )
            }
        }
    }
}

@Composable
private fun formatAirDate(date: PackedDate): String = stringResource(
    Lang.subject_details_air_date_format,
    date.year.toString(),
    date.month.toString(),
    date.day.toString(),
)
