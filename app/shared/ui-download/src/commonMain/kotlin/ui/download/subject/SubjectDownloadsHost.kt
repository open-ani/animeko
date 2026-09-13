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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_subject_cancel
import me.him188.ani.app.ui.lang.downloads_operation_failed
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

/**
 * 条目下载页与详情栏共用: 挂接 [SubjectDownloadsPresenter] 的状态、选源弹窗、通知权限申请与失败提示, 由 [content] 渲染.
 * 只有开启了新的下载会话时才申请通知权限.
 */
@Composable
internal fun SubjectDownloadsHost(
    presenter: SubjectDownloadsPresenter,
    content: @Composable (SubjectDownloadsUiState, SubjectDownloadActions, MediaSourceInfoProvider) -> Unit,
) {
    val state by presenter.uiState.collectAsStateWithLifecycle()
    val request by presenter.requestDialogs.collectAsStateWithLifecycle()
    val failedOperations by presenter.operationFailures.collectAsStateWithLifecycle()
    var pickerVisible by remember(presenter, request) { mutableStateOf(true) }
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val permissionManager = remember { KoinPlatform.getKoin().get<PermissionManager>() }
    val actions = SubjectDownloadActions(
        download = { episodeId ->
            if (presenter.requestDownload(episodeId)) {
                uiScope.launch { permissionManager.requestNotificationPermission(context) }
            }
            pickerVisible = true
        },
        cancelRequest = presenter::cancelRequest,
        pause = presenter::pauseDownloads,
        resume = presenter::resumeDownloads,
        delete = presenter::deleteDownloads,
        pauseAll = presenter::pauseAll,
        resumeAll = presenter::resumeAll,
        reload = presenter::reload,
    )
    request?.let { dialogState ->
        key(dialogState) {
            SubjectDownloadRequestDialogs(
                state = dialogState,
                visible = pickerVisible,
                sourceInfoProvider = presenter.sourceInfoProvider,
                settings = presenter.selectorSettings,
                onHide = { pickerVisible = false },
                onSelectMedia = presenter::selectMedia,
                onCancel = presenter::cancelRequest,
            )
        }
    }
    if (failedOperations > 0) {
        AlertDialog(
            onDismissRequest = presenter::dismissOperationFailures,
            text = { Text(stringResource(Lang.downloads_operation_failed, failedOperations)) },
            confirmButton = { TextButton(onClick = presenter::dismissOperationFailures) { Text(stringResource(Lang.cache_subject_cancel)) } },
        )
    }
    content(state, actions, presenter.sourceInfoProvider)
}
