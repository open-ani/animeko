/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.download.components.DownloadItem
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_management_downloading_count
import me.him188.ani.app.ui.lang.cache_management_finished_count
import me.him188.ani.app.ui.lang.cache_management_selection_downloading_count
import me.him188.ani.app.ui.lang.cache_management_selection_summary
import me.him188.ani.app.ui.lang.cache_subject_pause_all
import me.him188.ani.app.ui.lang.cache_subject_resume_all
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import org.jetbrains.compose.resources.stringResource

object SubjectDownloadsTestTags {
    const val SUMMARY_ROW = "subject_downloads_summary"
    const val PAUSE_ALL = "subject_downloads_pause_all"
    const val RESUME_ALL = "subject_downloads_resume_all"
}

@Composable
fun SubjectDownloadsSummaryRow(
    downloads: List<DownloadItem>,
    totalEpisodeCount: Int?,
    inSelection: Boolean,
    selectedEntries: List<DownloadItem>,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .testTag(SubjectDownloadsTestTags.SUMMARY_ROW)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val summaryText = if (inSelection) {
            selectionSummaryText(selectedEntries)
        } else {
            downloadSummaryText(downloads, totalEpisodeCount)
        }
        Text(
            summaryText,
            Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!inSelection) {
            PauseOrResumeAllTextButton(downloads, onPauseAll, onResumeAll)
        }
    }
}

/**
 * "全部暂停" / "全部继续" 按钮. 无可操作缓存时不显示.
 */
@Composable
private fun PauseOrResumeAllTextButton(
    downloads: List<DownloadItem>,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anyDownloading = downloads.any {
        !it.isFinished && !it.isPaused && !it.isFailed
    }
    val anyPaused = downloads.any { it.isPaused }
    when {
        anyDownloading -> {
            TextButton(onClick = onPauseAll, modifier = modifier.testTag(SubjectDownloadsTestTags.PAUSE_ALL)) {
                Text(stringResource(Lang.cache_subject_pause_all))
            }
        }

        anyPaused -> {
            TextButton(onClick = onResumeAll, modifier = modifier.testTag(SubjectDownloadsTestTags.RESUME_ALL)) {
                Text(stringResource(Lang.cache_subject_resume_all))
            }
        }
    }
}

@Composable
private fun downloadSummaryText(
    downloads: List<DownloadItem>,
    totalEpisodeCount: Int?,
): String {
    val finishedCount = downloads.filter { it.isFinished }.map { it.episodeId }.distinct().size
    val downloadedEpisodeCount = downloads.map { it.episodeId }.distinct().size
    val totalCount = totalEpisodeCount?.coerceAtLeast(downloadedEpisodeCount) ?: downloadedEpisodeCount
    // 设计稿: "2 个下载中" 统计所有未完成的下载任务 (含暂停).
    val downloadingCount = downloads.count { !it.isFinished && !it.isFailed }

    val parts = buildList {
        add(stringResource(Lang.cache_management_finished_count, finishedCount, totalCount))
        totalDownloadSize(downloads)?.let { add("$it") }
        if (downloadingCount > 0) {
            add(stringResource(Lang.cache_management_downloading_count, downloadingCount))
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun selectionSummaryText(selectedEntries: List<DownloadItem>): String {
    val totalSize = totalDownloadSize(selectedEntries) ?: 0.bytes
    val downloadingCount = selectedEntries.count { !it.isFinished && !it.isPaused && !it.isFailed }
    val summary = stringResource(Lang.cache_management_selection_summary, selectedEntries.size, "$totalSize")
    return if (downloadingCount > 0) {
        "$summary · ${stringResource(Lang.cache_management_selection_downloading_count, downloadingCount)}"
    } else {
        summary
    }
}

private fun totalDownloadSize(episodes: List<DownloadItem>): FileSize? {
    var sum = 0L
    var any = false
    episodes.forEach { episode ->
        if (episode.totalSize != FileSize.Unspecified) {
            sum += episode.totalSize.inBytes
            any = true
        }
    }
    return if (any) sum.bytes else null
}

@Composable
fun SubjectDownloadsHeader(
    title: String?,
    downloads: List<DownloadItem>,
    totalEpisodeCount: Int?,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 两个文本均可收缩, 长条目名不会把汇总和按钮挤出屏幕.
            Text(
                title.orEmpty(),
                Modifier.weight(1f, fill = false).placeholder(title == null),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                downloadSummaryText(downloads, totalEpisodeCount),
                Modifier.padding(start = 12.dp).weight(1f, fill = false),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PauseOrResumeAllTextButton(downloads, onPauseAll, onResumeAll)
    }
}
