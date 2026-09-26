/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.download

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.ui.download.DownloadManagementViewModel
import me.him188.ani.app.ui.download.components.DownloadItem
import me.him188.ani.app.ui.download.subject.DownloadRequestDialogState
import me.him188.ani.app.ui.download.subject.SubjectDownloadsUiState
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.downloads_create_failed
import me.him188.ani.app.ui.lang.downloads_operation_failed
import me.him188.ani.tv.ui.foundation.focus.tvBackKey
import org.jetbrains.compose.resources.stringResource

sealed interface TvDownloadIntent {
    data object Add : TvDownloadIntent
    data object Reload : TvDownloadIntent
    data object CancelRequest : TvDownloadIntent
    data class SelectSubject(val id: Int, val name: String) : TvDownloadIntent
    data class Download(val episodeId: Int) : TvDownloadIntent
    data class Pause(val ids: Set<String>) : TvDownloadIntent
    data class Resume(val ids: Set<String>) : TvDownloadIntent
    data class Delete(val ids: Set<String>) : TvDownloadIntent
    data class Play(val item: DownloadItem) : TvDownloadIntent
}

/** The shared ViewModel owns downloads and selection sessions across shell and player navigation. */
@Composable
fun TvDownloadManagementRoute(
    viewModel: DownloadManagementViewModel,
    onPlay: (DownloadItem) -> Unit,
    searchContent: @Composable (onChoose: (Int, String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onModalChanged: (Boolean) -> Unit = {},
    navigationRailInsets: PaddingValues = PaddingValues(0.dp),
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presenter by viewModel.subjectPresenter.collectAsStateWithLifecycle()
    val detail = key(presenter) {
        presenter?.uiState?.collectAsStateWithLifecycle()?.value ?: SubjectDownloadsUiState()
    }
    val request = key(presenter) {
        presenter?.requestDialogs?.collectAsStateWithLifecycle()?.value ?: DownloadRequestDialogState()
    }
    val failures by viewModel.operationFailures.collectAsStateWithLifecycle()
    val presenterFailures = key(presenter) { presenter?.operationFailures?.collectAsStateWithLifecycle()?.value ?: 0 }
    BackHandler(searching) { searching = false }
    if (searching) {
        Box(modifier.tvBackKey { searching = false }) {
            searchContent { id, name ->
                viewModel.selectSubject(id, name)
                searching = false
            }
        }
    } else {
        val error = when {
            request.failed -> stringResource(Lang.downloads_create_failed)
            failures + presenterFailures > 0 -> stringResource(Lang.downloads_operation_failed, failures + presenterFailures)
            else -> null
        }
        TvDownloadManagementScreen(
            state, presenter?.subjectId, detail,
            onIntent = { intent ->
                when (intent) {
                    TvDownloadIntent.Add -> searching = true
                    TvDownloadIntent.Reload -> presenter?.reload()
                    TvDownloadIntent.CancelRequest -> presenter?.cancelRequest()
                    is TvDownloadIntent.SelectSubject -> viewModel.selectSubject(intent.id, intent.name)
                    is TvDownloadIntent.Download -> presenter?.requestDownload(intent.episodeId)
                    is TvDownloadIntent.Pause -> viewModel.pauseDownloads(intent.ids)
                    is TvDownloadIntent.Resume -> viewModel.resumeDownloads(intent.ids)
                    is TvDownloadIntent.Delete -> viewModel.deleteDownloads(intent.ids)
                    is TvDownloadIntent.Play -> onPlay(intent.item)
                }
            },
            modifier = modifier,
            navigationRailInsets = navigationRailInsets,
            requestActive = request.selection != null,
            onModalChanged = onModalChanged,
            error = error,
            onDismissError = {
                viewModel.dismissOperationFailures()
                presenter?.dismissOperationFailures()
                if (request.failed) presenter?.cancelRequest()
            },
            requestContent = {
                request.selection?.let { selection ->
                    key(selection) {
                        TvDownloadRequestModal(
                            selection, request.episodePicker,
                            onCancel = { presenter?.cancelRequest() },
                            onSelect = { presenter?.selectMedia(selection.episodeId, it) },
                            onConfirm = { presenter?.confirmEpisodes(it) },
                            onBack = { presenter?.backToMediaSelection() },
                        )
                    }
                }
            },
        )
    }
}
