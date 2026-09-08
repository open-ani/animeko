/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.download.AddDownloadsState
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_subject_cancel
import me.him188.ani.app.ui.lang.downloads_operation_failed
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

/** Both hosts use this binding, so permission requests and picker behavior have one owner. */
@Composable
internal fun SubjectDownloadsFeature(
    subjectId: Int,
    content: @Composable (SubjectDownloadsUiState, SubjectDownloadActions, MediaSourceInfoProvider) -> Unit,
) {
    val vm = rememberSubjectDownloadsViewModel(subjectId)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val request by vm.requestState.collectAsStateWithLifecycle()
    val active = (request as? AddDownloadsState.Active)?.takeUnless { it is AddDownloadsState.Completed }
    var pickerVisible by remember(vm, active?.requestId) { mutableStateOf(true) }
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val permissionManager = remember { KoinPlatform.getKoin().get<PermissionManager>() }
    val actions = SubjectDownloadActions(
        download = { episodeId ->
            if (episodeId !in active?.episodeIds.orEmpty()) {
                vm.requestDownload(episodeId)
                uiScope.launch { permissionManager.requestNotificationPermission(context) }
            }
            pickerVisible = true
        },
        cancelRequest = { active?.let { vm.cancelRequest(it.requestId) } },
        pause = vm::pauseDownloads,
        resume = vm::resumeDownloads,
        delete = vm::deleteDownloads,
        pauseAll = vm::pauseAll,
        resumeAll = vm::resumeAll,
        reload = vm::reload,
    )
    SubjectDownloadRequestDialogs(
        state = request,
        visible = pickerVisible,
        sourceInfoProvider = vm.sourceInfoProvider,
        settings = vm.selectorSettings,
        onHide = { pickerVisible = false },
        onSelectMedia = vm::selectMedia,
        onRetry = vm::retryRequest,
        onCancel = vm::cancelRequest,
    )
    if (state.failedOperationCount > 0) {
        AlertDialog(
            onDismissRequest = vm::dismissOperationError,
            text = { Text(stringResource(Lang.downloads_operation_failed, state.failedOperationCount)) },
            confirmButton = { TextButton(onClick = vm::dismissOperationError) { Text(stringResource(Lang.cache_subject_cancel)) } },
        )
    }
    content(state, actions, vm.sourceInfoProvider)
}

@Composable
private fun rememberSubjectDownloadsViewModel(subjectId: Int): SubjectDownloadsViewModel {
    val owner = remember(subjectId) {
        object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    return viewModel(viewModelStoreOwner = owner) {
        val koin = KoinPlatform.getKoin()
        SubjectDownloadsViewModel(subjectId, koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
    }
}
