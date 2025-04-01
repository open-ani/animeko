/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.tools.update.InstallationResult
import me.him188.ani.app.ui.foundation.text.toPercentageString

@Composable
fun UpdateLogoLabel(state: UpdateLogoState) {
    when (state) {
        UpdateLogoState.ClickToCheck -> Text(text = "检查更新")
        is UpdateLogoState.DownloadFailed -> Text(
            text = "下载失败",
            color = MaterialTheme.colorScheme.error,
        )

        is UpdateLogoState.Downloaded -> Text(text = "重启更新", color = MaterialTheme.colorScheme.error)
        is UpdateLogoState.Downloading -> Text(text = "下载中 ${state.progress.toPercentageString()}")
        is UpdateLogoState.HasUpdate -> Text(text = "有新版本", color = MaterialTheme.colorScheme.error)
        UpdateLogoState.UpToDate -> Text(text = "已是最新")
    }
}

@Composable
fun UpdateLogoIcon(state: UpdateLogoState) {
    when (state) {
        UpdateLogoState.ClickToCheck,
        is UpdateLogoState.HasUpdate,
            -> Icon(Icons.Rounded.Update, null, tint = MaterialTheme.colorScheme.error)

        is UpdateLogoState.DownloadFailed,
            -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)

        is UpdateLogoState.Downloaded
            -> Icon(Icons.Rounded.RestartAlt, null, tint = MaterialTheme.colorScheme.error)

        is UpdateLogoState.Downloading
            -> Icon(Icons.Rounded.Downloading, null)

        UpdateLogoState.UpToDate
            -> Icon(Icons.Rounded.CheckCircleOutline, null)
    }
}


@Composable
fun TextButtonUpdateLogo(
    state: AutoUpdateViewModel = viewModel { AutoUpdateViewModel() },
    modifier: Modifier = Modifier,
) {
    SideEffect {
        state.startAutomaticCheckLatestVersion()
    }

    var showDialog by rememberSaveable { mutableStateOf(false) }
    val context by rememberUpdatedState(LocalContext.current)
    val uriHandler = LocalUriHandler.current
    if (showDialog) {
        state.latestVersion?.let {
            ChangelogDialog(
                latestVersion = it,
                onDismissRequest = { showDialog = false },
                onStartDownload = {
                    state.startDownload(it, uriHandler)
                },
                currentVersion = state.currentVersion,
            )
        }
    }
    if (state.hasUpdate) {
        var installationError by remember { mutableStateOf<InstallationResult.Failed?>(null) }
        if (installationError != null) {
            FailedToInstallDialog(
                message = installationError.toString(),
                onDismissRequest = { installationError = null },
                logoState = { state.logoState },
            )
        }

        TextButton(
            {
                state.handleClickLogo(
                    context,
                    uriHandler,
                    onInstallationError = { installationError = it },
                    showChangelogDialog = { showDialog = true },
                )
            },
            modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                    if (state.logoState !is UpdateLogoState.HasUpdate) {
                        UpdateLogoLabel(state.logoState)
                    }
                }
                UpdateLogoIcon(state.logoState)
            }
        }
    }
}
