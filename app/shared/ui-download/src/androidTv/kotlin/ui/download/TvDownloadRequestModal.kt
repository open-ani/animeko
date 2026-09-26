/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.domain.media.download.DownloadEpisodeOption
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.ui.download.subject.DownloadEpisodePickerState
import me.him188.ani.app.ui.download.subject.DownloadMediaPickerState
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_subject_cache
import me.him188.ani.app.ui.lang.downloads_episode_picker_all
import me.him188.ani.app.ui.lang.downloads_episode_picker_back
import me.him188.ani.app.ui.lang.downloads_episode_picker_downloaded
import me.him188.ani.app.ui.lang.downloads_episode_picker_only_current
import me.him188.ani.app.ui.lang.downloads_episode_picker_selected_count
import me.him188.ani.app.ui.lang.downloads_episode_picker_title
import me.him188.ani.app.ui.lang.downloads_episode_picker_unmatched
import me.him188.ani.app.ui.lang.media_selector_no_resources
import me.him188.ani.app.ui.lang.media_selector_query_again
import me.him188.ani.app.ui.lang.media_selector_querying
import me.him188.ani.app.ui.lang.media_selector_show_excluded
import me.him188.ani.app.ui.lang.tv_downloads_choose_source
import me.him188.ani.datasources.api.Media
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(UnsafeOriginalMediaAccess::class)
internal fun TvDownloadRequestModal(
    selection: DownloadMediaPickerState,
    episodes: DownloadEpisodePickerState?,
    onCancel: () -> Unit,
    onSelect: (Media) -> Unit,
    onConfirm: (Set<Int>) -> Unit,
    onBack: () -> Unit,
) {
    if (episodes != null) {
        TvDownloadEpisodePicker(episodes, onCancel, onConfirm, onBack)
        return
    }
    val candidates by selection.selector.filteredCandidates.collectAsStateWithLifecycle(emptyList())
    val completed by selection.fetchSession.hasCompleted.collectAsStateWithLifecycle(null)
    var showExcluded by remember { mutableStateOf(false) }
    val visible = candidates.filterNot { it.exclusionReason is MediaExclusionReason.EpisodeMismatch }
        .filter { showExcluded || it.exclusionReason == null }
    val sourceNames = remember(selection) {
        selection.fetchSession.mediaSourceResults.associate { it.mediaSourceId to it.sourceInfo.displayName }
    }
    TvDownloadModal(stringResource(Lang.tv_downloads_choose_source), onCancel) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvOptionRow(stringResource(Lang.media_selector_query_again), modifier = Modifier.weight(1f)) {
                selection.fetchSession.restartAll()
            }
            TvOptionRow(stringResource(Lang.media_selector_show_excluded), checked = showExcluded,
                modifier = Modifier.weight(1f)) { showExcluded = !showExcluded }
        }
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (visible.isEmpty()) item("empty") {
                Text(stringResource(if (completed?.allCompleted() == true) Lang.media_selector_no_resources else Lang.media_selector_querying),
                    style = MaterialTheme.typography.bodyMedium)
            }
            items(visible, key = { it.original.mediaId }) { candidate ->
                val media = candidate.original
                TvOptionRow(
                    media.originalTitle,
                    supportingText = listOfNotNull(sourceNames[media.mediaSourceId], media.properties.alliance,
                        media.properties.resolution, media.properties.size.toString()).joinToString(" · "),
                    modifier = Modifier.testTag("tv-download-source-${media.mediaId}"),
                ) { onSelect(media) }
            }
        }
    }
}

@Composable
internal fun TvDownloadEpisodePicker(
    state: DownloadEpisodePickerState,
    onCancel: () -> Unit,
    onConfirm: (Set<Int>) -> Unit,
    onBack: () -> Unit,
) {
    val available = state.options.filter { it.availability == DownloadEpisodeOption.Availability.AVAILABLE }
    val all = available.mapTo(mutableSetOf()) { it.episodeId }
    val current = setOf(state.episodeId).intersect(all)
    val currentIndex = state.options.indexOfFirst { it.isCurrent }.coerceAtLeast(0)
    val onwards = state.options.drop(currentIndex).map { it.episodeId }.toSet().intersect(all)
    var selected by remember(state) { mutableStateOf(onwards) }
    TvDownloadModal(stringResource(Lang.downloads_episode_picker_title), onCancel) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvOptionRow(stringResource(Lang.downloads_episode_picker_back), modifier = Modifier.weight(1f), onClick = onBack)
            TvOptionRow(stringResource(Lang.downloads_episode_picker_all), modifier = Modifier.weight(1f)) { selected = all }
            TvOptionRow(stringResource(Lang.downloads_episode_picker_only_current), modifier = Modifier.weight(1f)) { selected = current }
        }
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.options, key = { it.episodeId }) { option ->
                TvOptionRow(
                    "${option.sort} · ${option.name}", checked = option.episodeId in selected,
                    toggleRole = Role.Checkbox, enabled = option.episodeId in all,
                    supportingText = when (option.availability) {
                        DownloadEpisodeOption.Availability.AVAILABLE -> null
                        DownloadEpisodeOption.Availability.ALREADY_DOWNLOADED -> stringResource(Lang.downloads_episode_picker_downloaded)
                        DownloadEpisodeOption.Availability.UNMATCHED -> stringResource(Lang.downloads_episode_picker_unmatched)
                    },
                    modifier = Modifier.testTag("tv-download-pick-${option.episodeId}"),
                ) { selected = if (option.episodeId in selected) selected - option.episodeId else selected + option.episodeId }
            }
        }
        TvOptionRow(
            stringResource(Lang.cache_subject_cache),
            value = stringResource(Lang.downloads_episode_picker_selected_count, selected.size),
            enabled = selected.isNotEmpty(), modifier = Modifier.testTag("tv-download-pick-confirm"),
        ) { onConfirm(selected) }
    }
}
