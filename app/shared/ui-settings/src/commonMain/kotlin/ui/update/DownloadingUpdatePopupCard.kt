/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.tools.update.FileDownloaderState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_update_popup_cancel
import me.him188.ani.app.ui.lang.settings_update_popup_cancel_download
import me.him188.ani.app.ui.lang.settings_update_popup_continue_download
import me.him188.ani.app.ui.lang.settings_update_popup_download_complete
import me.him188.ani.app.ui.lang.settings_update_popup_downloading
import me.him188.ani.app.ui.lang.settings_update_popup_restart_update
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadingUpdatePopupCard(
    version: NewVersion,
    fileDownloaderStats: FileDownloaderStats,
    error: LoadError?,
    onInstallClick: () -> Unit,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConfirmCancel by rememberSaveable { mutableStateOf(false) }
    val onRequestCancel = {
        when (fileDownloaderStats.state) {
            // 弹一个对话框问一下
            FileDownloaderState.Downloading -> showConfirmCancel = true

            // 直接关闭
            is FileDownloaderState.Succeed,
            is FileDownloaderState.Failed,
            is FileDownloaderState.Cancelled,
            FileDownloaderState.Idle -> onCancelClick()
        }

    }

    if (showConfirmCancel) {
        AlertDialog(
            onDismissRequest = { showConfirmCancel = false },
            text = { Text(stringResource(Lang.settings_update_popup_cancel_download)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCancelClick()
                        showConfirmCancel = false
                    },
                ) {
                    Text(stringResource(Lang.settings_update_popup_cancel))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmCancel = false },
                ) {
                    Text(stringResource(Lang.settings_update_popup_continue_download))
                }
            },
        )
    }

    BasicNotificationPopupCard(
        title = { Text(stringResource(Lang.settings_update_popup_downloading)) },
        modifier,
        dismissButton = {
            NotificationPopupDefaults.DismissButton(onRequestCancel)
        },
        subtitle = { Text(version.name) },
        actions = {
            if (fileDownloaderStats.state is FileDownloaderState.Succeed) {
                Button(
                    onClick = onInstallClick,
                ) {
                    Text(stringResource(Lang.settings_update_popup_restart_update))
                }
            }
        },
    ) {
        when {
            fileDownloaderStats.state is FileDownloaderState.Succeed -> {
                ListItem(
                    headlineContent = {
                        Text(stringResource(Lang.settings_update_popup_download_complete))
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.DownloadDone, null,
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )
            }

            error != null -> {
                LoadErrorCard(
                    error,
                    onRetry = onRetryClick,
                    elevation = CardDefaults.cardElevation(),
                )
//                ListItem(
//                    headlineContent = {
//                        Text("下载失败")
//                    },
//                    supportingContent = {
//                        Text(
//                            error ?: "未知错误",
//                            color = MaterialTheme.colorScheme.error,
//                        )
//                    },
//                    leadingContent = {
//                        Icon(
//                            Icons.Rounded.ErrorOutline, null,
//                            tint = MaterialTheme.colorScheme.error,
//                        )
//                    },
//                )
            }

            else -> {
                ListItem(
                    headlineContent = {
                        LinearProgressIndicator(
                            progress = { fileDownloaderStats.progress },
                            Modifier.weight(1f).heightIn(min = 8.dp).wrapContentHeight(Alignment.CenterVertically),
                        )
                    },
                    trailingContent = {
                        Box(Modifier.padding(start = 16.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                "${999}%",
                                Modifier.alpha(0f), // 占位
                            )
                            Text(
                                "${(fileDownloaderStats.progress * 100).fastRoundToInt()}%",
                                Modifier,
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewDownloadingUpdatePopupCard() = ProvideCompositionLocalsForPreview {
    DownloadingUpdatePopupCard(
        version = TestNewVersion,
        fileDownloaderStats = TestFileDownloaderStats.Downloading,
        error = null,
        {}, {}, {},
    )
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewDownloadingUpdatePopupCardError() = ProvideCompositionLocalsForPreview {
    DownloadingUpdatePopupCard(
        version = TestNewVersion,
        fileDownloaderStats = TestFileDownloaderStats.Failed,
        error = LoadError.NetworkError,
        {}, {}, {},
    )
}


@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewDownloadingUpdatePopupCardSuccess() = ProvideCompositionLocalsForPreview {
    DownloadingUpdatePopupCard(
        version = TestNewVersion,
        fileDownloaderStats = TestFileDownloaderStats.Succeed,
        error = null,
        {}, {}, {},
    )
}
