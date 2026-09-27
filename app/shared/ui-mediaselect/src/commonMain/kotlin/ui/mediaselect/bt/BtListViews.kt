/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.bt

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_bt_col_alliance
import me.him188.ani.app.ui.lang.media_selector_bt_col_published
import me.him188.ani.app.ui.lang.media_selector_bt_col_resolution
import me.him188.ani.app.ui.lang.media_selector_bt_col_size
import me.him188.ani.app.ui.lang.media_selector_bt_col_subtitle
import me.him188.ani.app.ui.lang.media_selector_bt_col_title
import me.him188.ani.app.ui.lang.media_selector_bt_collection
import me.him188.ani.app.ui.lang.media_selector_bt_empty
import me.him188.ani.app.ui.lang.media_selector_bt_reason_below_preference
import me.him188.ani.app.ui.lang.media_selector_bt_reason_episode_mismatch
import me.him188.ani.app.ui.lang.media_selector_bt_show_excluded
import me.him188.ani.app.ui.lang.media_selector_item_no_subtitle
import me.him188.ani.app.ui.lang.media_selector_item_season_mismatch
import me.him188.ani.app.ui.lang.media_selector_item_single_episode_resource
import me.him188.ani.app.ui.lang.media_selector_item_subject_title_mismatch
import me.him188.ani.app.ui.lang.media_selector_item_unsupported_playback
import me.him188.ani.app.ui.lang.subject_episode_cached
import me.him188.ani.app.ui.media.MediaDetailsRenderer
import me.him188.ani.app.ui.media.MediaDetailsStrings
import me.him188.ani.app.ui.media.renderSubtitleLanguage
import me.him188.ani.app.ui.media.rememberMediaDetailsStrings
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.isSingleEpisode
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * 表格列宽. 标题列占剩余宽度.
 */
@Immutable
internal data class BtTableColumns(
    val check: Dp,
    val alliance: Dp,
    val resolution: Dp,
    val subtitle: Dp,
    val size: Dp,
    val published: Dp,
) {
    companion object {
        val Default = BtTableColumns(28.dp, 150.dp, 72.dp, 96.dp, 80.dp, 64.dp)
        val Compact = BtTableColumns(28.dp, 130.dp, 64.dp, 84.dp, 76.dp, 56.dp)

        /**
         * 容器窄于此宽度时用 [Compact].
         */
        val CompactMaxWidth = 900.dp
    }
}

/**
 * 行内标签的文案, 一次解析供列表复用.
 */
@Immutable
internal class BtLabelStrings(
    val cached: String,
    val collection: String,
    val noSubtitle: String,
    val episodeMismatch: String,
    val belowPreference: String,
    val singleEpisode: String,
    val unsupportedPlayback: String,
    val seasonMismatch: String,
    val subjectTitleMismatch: String,
    val details: MediaDetailsStrings,
) {
    fun exclusion(exclusion: BtRowExclusion): String = when (exclusion) {
        BtRowExclusion.BelowPreference -> belowPreference
        is BtRowExclusion.Reason -> when (exclusion.reason) {
            is MediaExclusionReason.EpisodeMismatch -> episodeMismatch
            MediaExclusionReason.MediaWithoutSubtitle -> noSubtitle
            is MediaExclusionReason.SingleEpisodeForCompleteSubject -> singleEpisode
            MediaExclusionReason.UnsupportedByPlatformPlayer -> unsupportedPlayback
            MediaExclusionReason.FromSequelSeason, MediaExclusionReason.FromSeriesSeason -> seasonMismatch
            MediaExclusionReason.SubjectNameMismatch -> subjectTitleMismatch
        }
    }

    /**
     * 紧凑列表第二行的标签: 已缓存 → 原因 → 分辨率 → 字幕语言 → 字幕形式 → 合集.
     */
    fun compactLabels(row: BtRow): List<String> = buildList {
        val media = row.media
        if (row.isCached) add(cached)
        row.exclusion?.let { add(exclusion(it)) }
        media.properties.resolution.takeIf { it.isNotBlank() }?.let { add(it) }
        for (id in media.properties.subtitleLanguageIds) add(renderSubtitleLanguage(id, details))
        MediaDetailsRenderer.renderSubtitleKind(media.properties.subtitleKind, details)?.let { add(it) }
        if (media.episodeRange?.isSingleEpisode() == false) add(collection)
    }

    /**
     * 表格「字幕」列: 语言 + 形式; 没有字幕语言时显示「无字幕」.
     */
    fun subtitleCell(row: BtRow): String {
        val media = row.media
        val parts = buildList {
            for (id in media.properties.subtitleLanguageIds) add(renderSubtitleLanguage(id, details))
            MediaDetailsRenderer.renderSubtitleKind(media.properties.subtitleKind, details)?.let { add(it) }
        }
        return if (parts.isEmpty()) noSubtitle else parts.joinToString(" ")
    }
}

@Composable
internal fun rememberBtLabelStrings(): BtLabelStrings {
    val details = rememberMediaDetailsStrings()
    return BtLabelStrings(
        cached = stringResource(Lang.subject_episode_cached),
        collection = stringResource(Lang.media_selector_bt_collection),
        noSubtitle = stringResource(Lang.media_selector_item_no_subtitle),
        episodeMismatch = stringResource(Lang.media_selector_bt_reason_episode_mismatch),
        belowPreference = stringResource(Lang.media_selector_bt_reason_below_preference),
        singleEpisode = stringResource(Lang.media_selector_item_single_episode_resource),
        unsupportedPlayback = stringResource(Lang.media_selector_item_unsupported_playback),
        seasonMismatch = stringResource(Lang.media_selector_item_season_mismatch),
        subjectTitleMismatch = stringResource(Lang.media_selector_item_subject_title_mismatch),
        details = details,
    )
}

private val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/**
 * 紧凑列表: 56dp 平铺行, 无卡片. 选中行 primaryContainer 背景 + 前导 Check; 排除行降低不透明度.
 * [excludedRows] 非空且未 [revealExcluded] 时列表末尾是「显示已被排除的 N 条资源」按钮, 展开后追加排除行.
 *
 * @param isLoading 两个列表都空时: true → 居中加载指示 (占位 presentation / BT 源仍在查询), false → 「没有资源」. 有行时忽略.
 */
@Composable
internal fun BtCompactList(
    rows: List<BtRow>,
    excludedRows: List<BtRow>,
    revealExcluded: Boolean,
    onReveal: () -> Unit,
    onClick: (BtRow) -> Unit,
    onLongClick: (BtRow) -> Unit,
    timeZone: TimeZone,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    horizontalPadding: Dp = 20.dp,
    isLoading: Boolean = false,
) {
    val strings = rememberBtLabelStrings()
    if (rows.isEmpty() && excludedRows.isEmpty()) {
        if (isLoading) {
            BtLoadingState(modifier.testTag(BtResourcesPageTestTags.COMPACT_LIST))
        } else {
            BtEmptyState(modifier.testTag(BtResourcesPageTestTags.COMPACT_LIST))
        }
        return
    }
    LazyColumn(modifier.testTag(BtResourcesPageTestTags.COMPACT_LIST), state = listState) {
        items(rows, key = { it.id }) { row ->
            BtCompactRow(row, strings, timeZone, onClick, onLongClick, horizontalPadding)
        }
        excludedSection(excludedRows, revealExcluded, onReveal) { row ->
            BtCompactRow(row, strings, timeZone, onClick, onLongClick, horizontalPadding)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BtCompactRow(
    row: BtRow,
    strings: BtLabelStrings,
    timeZone: TimeZone,
    onClick: (BtRow) -> Unit,
    onLongClick: (BtRow) -> Unit,
    horizontalPadding: Dp,
) {
    val background = if (row.isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (row.isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val secondaryColor = if (row.isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .testTag(BtResourcesPageTestTags.row(row.id))
            .fillMaxWidth()
            .height(56.dp)
            .background(background)
            .combinedClickable(onClick = { onClick(row) }, onLongClick = { onLongClick(row) })
            .alpha(if (row.isExcluded) 0.6f else 1f)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (row.isSelected) {
                Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp), tint = contentColor)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                row.media.properties.alliance,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (label in strings.compactLabels(row)) {
                    Text(
                        label,
                        color = secondaryColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatBtSize(row.media.properties.size),
                color = secondaryColor,
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                maxLines = 1,
                softWrap = false,
            )
            Text(
                formatPublishedMMdd(row.media.publishedTime, timeZone),
                color = secondaryColor,
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * 表格: 表头 + 40dp 行. 列宽由 [columns] 给定, 标题列占剩余宽度. 排除行 onSurfaceVariant, 标题前加原因标签.
 *
 * @param isLoading 同 [BtCompactList].
 */
@Composable
internal fun BtTable(
    rows: List<BtRow>,
    excludedRows: List<BtRow>,
    revealExcluded: Boolean,
    onReveal: () -> Unit,
    onClick: (BtRow) -> Unit,
    onLongClick: (BtRow) -> Unit,
    timeZone: TimeZone,
    modifier: Modifier = Modifier,
    columns: BtTableColumns = BtTableColumns.Default,
    listState: LazyListState = rememberLazyListState(),
    horizontalPadding: Dp = 24.dp,
    isLoading: Boolean = false,
) {
    val strings = rememberBtLabelStrings()
    Column(modifier.testTag(BtResourcesPageTestTags.TABLE)) {
        BtTableHeader(columns, horizontalPadding)
        if (rows.isEmpty() && excludedRows.isEmpty()) {
            if (isLoading) {
                BtLoadingState(Modifier.fillMaxWidth().weight(1f))
            } else {
                BtEmptyState(Modifier.fillMaxWidth().weight(1f))
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            items(rows, key = { it.id }) { row ->
                BtTableRow(row, columns, strings, timeZone, onClick, onLongClick, horizontalPadding)
            }
            excludedSection(excludedRows, revealExcluded, onReveal) { row ->
                BtTableRow(row, columns, strings, timeZone, onClick, onLongClick, horizontalPadding)
            }
        }
    }
}

@Composable
private fun BtTableHeader(columns: BtTableColumns, horizontalPadding: Dp) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .height(36.dp)
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = MaterialTheme.typography.labelSmall
            Spacer(Modifier.width(columns.check))
            Text(stringResource(Lang.media_selector_bt_col_alliance), Modifier.width(columns.alliance), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Lang.media_selector_bt_col_title), Modifier.weight(1f), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Lang.media_selector_bt_col_resolution), Modifier.width(columns.resolution), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Lang.media_selector_bt_col_subtitle), Modifier.width(columns.subtitle), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(Lang.media_selector_bt_col_size), Modifier.width(columns.size),
                style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            )
            Text(
                stringResource(Lang.media_selector_bt_col_published), Modifier.width(columns.published),
                style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BtTableRow(
    row: BtRow,
    columns: BtTableColumns,
    strings: BtLabelStrings,
    timeZone: TimeZone,
    onClick: (BtRow) -> Unit,
    onLongClick: (BtRow) -> Unit,
    horizontalPadding: Dp,
) {
    val background = if (row.isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = when {
        row.isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        row.isExcluded -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            Modifier
                .testTag(BtResourcesPageTestTags.row(row.id))
                .fillMaxWidth()
                .height(40.dp)
                .background(background)
                .combinedClickable(onClick = { onClick(row) }, onLongClick = { onLongClick(row) })
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = MaterialTheme.typography.bodyMedium
            Box(Modifier.width(columns.check), contentAlignment = Alignment.Center) {
                if (row.isSelected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(16.dp))
                }
            }
            Text(
                row.media.properties.alliance, Modifier.width(columns.alliance),
                style = style, fontWeight = if (row.isSelected) FontWeight.Medium else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (row.isCached) BtInlineLabel(strings.cached)
                row.exclusion?.let { BtInlineLabel(strings.exclusion(it)) }
                Text(row.media.originalTitle, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                row.media.properties.resolution, Modifier.width(columns.resolution),
                style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                strings.subtitleCell(row), Modifier.width(columns.subtitle),
                style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatBtSize(row.media.properties.size), Modifier.width(columns.size),
                style = style.merge(TabularNumbers), maxLines = 1, textAlign = TextAlign.End,
            )
            Text(
                formatPublishedMMdd(row.media.publishedTime, timeZone), Modifier.width(columns.published),
                style = style.merge(TabularNumbers), maxLines = 1, textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * 标题列开头的小标签 (已缓存 / 排除原因).
 */
@Composable
private fun RowScope.BtInlineLabel(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun LazyListScope.excludedSection(
    excludedRows: List<BtRow>,
    revealExcluded: Boolean,
    onReveal: () -> Unit,
    row: @Composable (BtRow) -> Unit,
) {
    if (excludedRows.isEmpty()) return
    if (!revealExcluded) {
        item(key = "show_excluded") {
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = onReveal, Modifier.testTag(BtResourcesPageTestTags.SHOW_EXCLUDED)) {
                    Text(stringResource(Lang.media_selector_bt_show_excluded, excludedRows.size))
                }
            }
        }
    } else {
        items(excludedRows, key = { it.id }) { row(it) }
    }
}

@Composable
private fun BtEmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(Lang.media_selector_bt_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * 列表为空但查询尚未结束: 与手动查找页同一加载指示, 不显示「没有资源」.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BtLoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.testTag(BtResourcesPageTestTags.LOADING))
    }
}

private val MMdd = LocalDateTime.Format {
    monthNumber()
    char('-')
    day()
}

/**
 * 发布日期 `MM-dd`; 0 → "".
 */
internal fun formatPublishedMMdd(millis: Long, timeZone: TimeZone): String {
    if (millis == 0L) return ""
    return MMdd.format(Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone))
}

/**
 * Zero / Unspecified → ""; 否则 `toString()`.
 */
internal fun formatBtSize(size: FileSize): String {
    if (size == FileSize.Zero || size == FileSize.Unspecified) return ""
    return size.toString()
}
