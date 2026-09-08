/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.download.AddDownloadState
import me.him188.ani.app.domain.media.download.DownloadMediaSelection
import me.him188.ani.app.domain.media.fetch.MediaSourceResultsFilterer
import me.him188.ani.app.domain.media.fetch.restart
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_subject_cancel
import me.him188.ani.app.ui.lang.cache_subject_select_storage
import me.him188.ani.app.ui.lang.downloads_create_failed
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.mediafetch.MediaSelectorView
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresenter
import me.him188.ani.app.ui.mediafetch.ViewKind
import me.him188.ani.app.ui.mediafetch.rememberMediaSelectorState
import me.him188.ani.datasources.api.Media
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SubjectDownloadRequestDialogs(
    state: AddDownloadState,
    visible: Boolean,
    sourceInfoProvider: MediaSourceInfoProvider,
    settings: Flow<MediaSelectorSettings>,
    onHide: () -> Unit,
    onSelectMedia: (Long, Media) -> Unit,
    onSelectStorage: (Long, MediaCacheStorage) -> Unit,
    onBackToMedia: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (Long) -> Unit,
) {
    val selection = when (state) {
        is AddDownloadState.ChoosingMedia -> state.selection
        is AddDownloadState.ChoosingStorage -> state.selection
        else -> null
    }
    if (visible && selection != null) {
        val requestId = (state as AddDownloadState.Active).requestId
        key(requestId) {
            DownloadMediaPicker(selection, sourceInfoProvider, settings, onHide) { onSelectMedia(requestId, it) }
        }
    }
    if (state is AddDownloadState.ChoosingStorage) {
        AlertDialog(
            onDismissRequest = { onBackToMedia(state.requestId) },
            title = { Text(stringResource(Lang.cache_subject_select_storage)) },
            text = {
                Column {
                    state.storages.forEach { storage ->
                        OutlinedButton(onClick = { onSelectStorage(state.requestId, storage) }) {
                            Text(storage.cacheMediaSource.info.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onBackToMedia(state.requestId) }) { Text(stringResource(Lang.cache_subject_cancel)) }
            },
        )
    }
    if (state is AddDownloadState.Failed) {
        AlertDialog(
            onDismissRequest = { onCancel(state.requestId) },
            text = { Text(stringResource(Lang.downloads_create_failed)) },
            confirmButton = {
                TextButton(onClick = { onRetry(state.requestId) }) { Text(stringResource(Lang.settings_mediasource_retry)) }
            },
            dismissButton = {
                TextButton(onClick = { onCancel(state.requestId) }) { Text(stringResource(Lang.cache_subject_cancel)) }
            },
        )
    }
}

@Composable
private fun DownloadMediaPicker(
    selection: DownloadMediaSelection,
    sourceInfoProvider: MediaSourceInfoProvider,
    settings: Flow<MediaSelectorSettings>,
    onDismiss: () -> Unit,
    onSelect: (Media) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val filteredResults = remember(selection, settings) {
        MediaSourceResultsFilterer(flowOf(selection.fetchSession.mediaSourceResults), settings, scope).filteredSourceResults
    }
    val presentation by remember(filteredResults) {
        MediaSourceResultListPresenter(filteredResults).presentationFlow.map { MediaSourceResultListPresentation(it) }
    }.collectAsStateWithLifecycle(MediaSourceResultListPresentation.Empty)
    val selectorState = rememberMediaSelectorState(sourceInfoProvider, filteredResults) { selection.selector }
    val fetchRequest by selection.fetchSession.request.collectAsStateWithLifecycle(null)
    var viewKind by rememberSaveable { mutableStateOf(ViewKind.WEB) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
        contentWindowInsets = { BottomSheetDefaults.windowInsets.add(WindowInsets.desktopTitleBar()) },
    ) {
        MediaSelectorView(
            state = selectorState,
            viewKind = viewKind,
            onViewKindChange = { viewKind = it },
            fetchRequest = fetchRequest,
            onFetchRequestChange = selection.fetchSession::setFetchRequest,
            sourceResults = presentation,
            onRestartSource = { selection.fetchSession.restart(it) },
            onRefresh = selection.fetchSession::restartAll,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                .navigationBarsPadding().fillMaxHeight().fillMaxWidth(),
            stickyHeaderBackgroundColor = BottomSheetDefaults.ContainerColor,
            onClickItem = onSelect,
        )
    }
}
