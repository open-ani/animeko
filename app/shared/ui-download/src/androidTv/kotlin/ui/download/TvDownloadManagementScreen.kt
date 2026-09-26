/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.download

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.ui.download.DownloadManagementUiState
import me.him188.ani.app.ui.download.components.DownloadItem
import me.him188.ani.app.ui.download.components.DownloadStatus
import me.him188.ani.app.ui.download.subject.SubjectDownloadListItem
import me.him188.ani.app.ui.download.subject.SubjectDownloadsUiState
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_episode_download_completed
import me.him188.ani.app.ui.lang.cache_episode_download_failed
import me.him188.ani.app.ui.lang.cache_episode_downloading
import me.him188.ani.app.ui.lang.cache_episode_status_paused
import me.him188.ani.app.ui.lang.cache_management_delete_cache_title
import me.him188.ani.app.ui.lang.cache_management_more_actions
import me.him188.ani.app.ui.lang.cache_subject_cache
import me.him188.ani.app.ui.lang.cache_subject_cancel
import me.him188.ani.app.ui.lang.cache_subject_delete
import me.him188.ani.app.ui.lang.downloads_empty
import me.him188.ani.app.ui.lang.downloads_episode_picker_back
import me.him188.ani.app.ui.lang.downloads_load_failed
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.main_screen_page_cache_management
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.tv_downloads_add
import me.him188.ani.app.ui.lang.tv_downloads_busy
import me.him188.ani.app.ui.lang.tv_downloads_delete_confirm
import me.him188.ani.app.ui.lang.tv_downloads_empty
import me.him188.ani.app.ui.lang.tv_downloads_hint
import me.him188.ani.app.ui.lang.tv_downloads_running_hint
import me.him188.ani.app.ui.lang.tv_downloads_summary
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvBackKey
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusLink
import me.him188.ani.tv.ui.foundation.focus.tvFocusMemorable
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.layout.TvScreenScaffold
import me.him188.ani.tv.ui.foundation.layout.tvModalUnderlay
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

private fun downloadFocus(id: String) = TvFocusKey("downloads-$id")

/** Left/right selects the pane; up/down browses a single list. Confirm opens explicit actions. */
@Composable
fun TvDownloadManagementScreen(
    state: DownloadManagementUiState,
    subjectId: Int?,
    detail: SubjectDownloadsUiState,
    onIntent: (TvDownloadIntent) -> Unit,
    modifier: Modifier = Modifier,
    requestActive: Boolean = false,
    navigationRailInsets: PaddingValues = PaddingValues(0.dp),
    onModalChanged: (Boolean) -> Unit = {},
    error: String? = null,
    onDismissError: () -> Unit = {},
    requestContent: @Composable () -> Unit = {},
) {
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val add = downloadFocus("add")
    val detailEntry = downloadFocus("detail")
    var detailFocused by rememberSaveable { mutableStateOf(subjectId != null) }
    var lastRow by rememberSaveable { mutableStateOf("detail") }
    var menu by remember { mutableStateOf<Set<String>?>(null) }
    var deletion by remember { mutableStateOf<Set<String>?>(null) }
    val modal = menu != null || deletion != null || requestActive || error != null
    SideEffect { onModalChanged(modal) }
    DisposableEffect(Unit) { onDispose { onModalChanged(false) } }
    val groupKey = subjectId?.takeIf { id -> state.groups.any { it.subjectId == id } }
        ?.let { downloadFocus("subject-$it") } ?: add
    focus.InitialFocus(if (detailFocused) detailEntry else add)
    fun leaveDetail() {
        detailFocused = false
        focus.request(groupKey)
    }
    BackHandler(detailFocused && !modal, ::leaveDetail)
    var wasModal by remember { mutableStateOf(false) }
    LaunchedEffect(modal) {
        if (wasModal && !modal) {
            val target = lastRow.takeIf { key -> detail.items.any { it.key == key } } ?: "detail"
            focus.request(downloadFocus(target))
        }
        wasModal = modal
    }
    val allItems = state.groups.flatMap { it.entries }
    Box(modifier.fillMaxSize().testTag("tv-downloads").tvFocusNavSignal(focus)) {
        TvScreenScaffold(Modifier.padding(navigationRailInsets).tvModalUnderlay(modal).tvBackKey(detailFocused && !modal, ::leaveDetail)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(Lang.main_screen_page_cache_management), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "↓ ${state.overallStats.downloadSpeed}/s   ↑ ${state.overallStats.uploadSpeed}/s",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(Lang.tv_downloads_running_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    LazyColumn(
                        Modifier.weight(.36f).focusGroup().testTag("tv-download-subjects"),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item("add") {
                            TvOptionRow(
                                stringResource(Lang.tv_downloads_add), icon = Icons.Rounded.Add,
                                modifier = Modifier.testTag("tv-download-add").tvFocusAnchor(focus, add)
                                    .tvFocusMemorable("downloads-add")
                                    .onFocusChanged { if (it.isFocused) detailFocused = false },
                            ) { onIntent(TvDownloadIntent.Add) }
                        }
                        if (state.isLoading) item("loading") { Text(stringResource(Lang.foundation_loading)) }
                        items(state.groups, key = { it.subjectId }) { group ->
                            fun open() {
                                detailFocused = true
                                onIntent(TvDownloadIntent.SelectSubject(group.subjectId, group.subjectName))
                                focus.request(detailEntry)
                            }
                            TvOptionRow(
                                group.subjectName,
                                selected = group.subjectId == subjectId,
                                showSelectionIndicator = false,
                                supportingText = stringResource(Lang.tv_downloads_summary, group.entries.size, group.finishedCount),
                                modifier = Modifier.testTag("tv-download-subject-${group.subjectId}")
                                    .tvFocusAnchor(focus, downloadFocus("subject-${group.subjectId}"))
                                    .tvFocusMemorable("downloads-subject-${group.subjectId}")
                                    .onFocusChanged { if (it.isFocused) detailFocused = false }
                                    .onPreviewKeyEvent { event ->
                                        if (event.key != Key.DirectionRight) false else {
                                            if (event.type == KeyEventType.KeyUp) open()
                                            true
                                        }
                                    },
                                onClick = ::open,
                            )
                        }
                        if (!state.isLoading && allItems.isEmpty()) item("empty") {
                            Text(stringResource(Lang.tv_downloads_empty), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Column(
                        Modifier.weight(.64f).clip(RoundedCornerShape(16.dp)).focusGroup()
                            .onFocusChanged { if (it.hasFocus) detailFocused = true }
                            .testTag("tv-download-detail"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (subjectId == null) {
                            Text(stringResource(Lang.tv_downloads_hint), style = MaterialTheme.typography.titleMedium)
                        } else {
                            Text(detail.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TvOptionRow(
                                    stringResource(Lang.downloads_episode_picker_back),
                                    modifier = Modifier.weight(1f).tvFocusAnchor(focus, detailEntry)
                                        .tvFocusLink(focus, left = groupKey)
                                        .tvFocusMemorable("downloads-detail")
                                        .testTag("tv-download-detail-back"),
                                    onClick = ::leaveDetail,
                                )
                                TvOptionRow(
                                    stringResource(Lang.cache_management_more_actions),
                                    enabled = detail.downloads.any { !it.isBusy },
                                    modifier = Modifier.weight(1f).testTag("tv-download-bulk"),
                                ) {
                                    lastRow = "detail"
                                    menu = detail.downloads.mapTo(mutableSetOf()) { it.id }
                                }
                            }
                            if (detail.request.canCancel) {
                                TvOptionRow(
                                    stringResource(Lang.cache_subject_cancel),
                                    supportingText = stringResource(Lang.tv_downloads_busy),
                                    modifier = Modifier.testTag("tv-download-cancel-request"),
                                ) { onIntent(TvDownloadIntent.CancelRequest) }
                            }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (detail.episodesFailed || detail.downloadsFailed) item("retry") {
                                    TvOptionRow(stringResource(Lang.settings_mediasource_retry),
                                        supportingText = stringResource(Lang.downloads_load_failed)) { onIntent(TvDownloadIntent.Reload) }
                                }
                                if (detail.episodesLoading || detail.downloadsLoading) item("loading") {
                                    Text(stringResource(Lang.foundation_loading))
                                }
                                items(detail.items, key = { it.key }) { item ->
                                    val itemModifier = Modifier.testTag("tv-download-${item.key}")
                                        .tvFocusAnchor(focus, downloadFocus(item.key))
                                        .tvFocusMemorable("downloads-${subjectId}-${item.key}")
                                        .tvFocusLink(focus, left = groupKey)
                                    when (item) {
                                        is SubjectDownloadListItem.Download -> {
                                            val download = item.download
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                TvOptionRow(
                                                    "${download.sort} · ${download.displayName}",
                                                    supportingText = downloadDescription(download),
                                                    modifier = itemModifier,
                                                ) {
                                                    lastRow = item.key
                                                    menu = setOf(download.id)
                                                }
                                                download.progress.getOrNull()?.let { progress ->
                                                    LinearProgressIndicator(
                                                        progress = { progress.coerceIn(0f, 1f) },
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                        }
                                        is SubjectDownloadListItem.Episode -> {
                                            val episode = item.episode
                                            TvOptionRow(
                                                "${episode.sort} · ${episode.title}",
                                                value = stringResource(if (detail.request.busy) Lang.tv_downloads_busy else Lang.cache_subject_cache),
                                                icon = Icons.Rounded.Download,
                                                enabled = episode.hasPublished,
                                                modifier = itemModifier,
                                            ) {
                                                if (!detail.request.busy) {
                                                    lastRow = item.key
                                                    onIntent(TvDownloadIntent.Download(episode.episodeId))
                                                }
                                            }
                                        }
                                    }
                                }
                                if (detail.items.isEmpty() && !detail.episodesLoading && !detail.downloadsLoading) item("empty") {
                                    Text(stringResource(Lang.downloads_empty))
                                }
                            }
                        }
                    }
                }
            }
        }
        menu?.let { ids ->
            val current = detail.downloads.filter { it.id in ids }
            TvDownloadActionsModal(current,
                onClose = { menu = null },
                onAction = { intent ->
                    if (intent is TvDownloadIntent.Delete) deletion = intent.ids else onIntent(intent)
                    menu = null
                },
            )
        }
        deletion?.let { ids ->
            TvDownloadModal(stringResource(Lang.cache_management_delete_cache_title), onClose = { deletion = null }) {
                Text(stringResource(Lang.tv_downloads_delete_confirm), style = MaterialTheme.typography.bodyMedium)
                TvOptionRow(stringResource(Lang.cache_subject_delete), modifier = Modifier.testTag("tv-download-confirm-delete")) {
                    lastRow = "detail"
                    onIntent(TvDownloadIntent.Delete(ids))
                    deletion = null
                }
            }
        }
        error?.let {
            TvDownloadModal(it, onClose = onDismissError) {}
        }
        requestContent()
    }
}

@Composable
internal fun downloadDescription(item: DownloadItem): String = listOfNotNull(
    stringResource(when {
        item.isBusy -> Lang.tv_downloads_busy
        item.status == DownloadStatus.COMPLETED -> Lang.cache_episode_download_completed
        item.status == DownloadStatus.PAUSED -> Lang.cache_episode_status_paused
        item.status == DownloadStatus.FAILED -> Lang.cache_episode_download_failed
        else -> Lang.cache_episode_downloading
    }), item.progressText, item.detailedSizeText, item.speedText.takeIf { item.status == DownloadStatus.IN_PROGRESS },
).joinToString(" · ")
