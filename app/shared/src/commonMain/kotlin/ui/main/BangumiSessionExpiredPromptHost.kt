/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.bangumi_session_expired
import me.him188.ani.app.ui.lang.bangumi_session_expired_login
import me.him188.ani.app.ui.lang.bangumi_session_expired_login_again
import me.him188.ani.app.ui.lang.bangumi_session_expired_unbind
import me.him188.ani.app.ui.lang.bangumi_session_expired_view
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BoxScope.BangumiSessionExpiredPromptHost(
    viewModel: AniAppViewModel,
    enabled: Boolean,
    onLogin: () -> Unit,
) {
    val expired by viewModel.bangumiSessionExpired.collectAsStateWithLifecycle(false)

    BangumiSessionExpiredPromptHost(
        expired = enabled && expired,
        onLogin = onLogin,
        onUnbindBangumi = {
            viewModel.unbindBangumi()
        },
    )
}

@Composable
internal fun BoxScope.BangumiSessionExpiredPromptHost(
    expired: Boolean,
    onLogin: () -> Unit,
    onUnbindBangumi: suspend () -> Unit,
) {
    var snackbarDismissedInThisSession by rememberSaveable { mutableStateOf(false) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val asyncHandler = rememberAsyncHandler()

    LaunchedEffect(expired) {
        if (!expired) {
            snackbarDismissedInThisSession = false
            showDialog = false
        }
    }

    if (expired && !snackbarDismissedInThisSession && !showDialog) {
        BangumiSessionExpiredSnackbar(
            onView = {
                snackbarDismissedInThisSession = true
                showDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
                .fillMaxWidth(),
        )
    }

    if (!expired || !showDialog) {
        return
    }

    BangumiSessionExpiredDialog(
        onDismissRequest = {
            showDialog = false
        },
        onLogin = {
            showDialog = false
            onLogin()
        },
        onUnbindBangumi = {
            asyncHandler.launch {
                onUnbindBangumi()
                showDialog = false
            }
        },
        unbindEnabled = !asyncHandler.isWorking,
    )
}

@Composable
private fun BangumiSessionExpiredSnackbar(
    onView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Snackbar(
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Lang.bangumi_session_expired),
                Modifier.weight(1f),
            )
            TextButton(onClick = onView) {
                Text(stringResource(Lang.bangumi_session_expired_view))
            }
        }
    }
}

@Composable
private fun BangumiSessionExpiredDialog(
    onDismissRequest: () -> Unit,
    onLogin: () -> Unit,
    onUnbindBangumi: () -> Unit,
    unbindEnabled: Boolean,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = {
            Text(stringResource(Lang.bangumi_session_expired_login_again))
        },
        confirmButton = {
            TextButton(onClick = onLogin) {
                Text(stringResource(Lang.bangumi_session_expired_login))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onUnbindBangumi,
                enabled = unbindEnabled,
            ) {
                Text(
                    stringResource(Lang.bangumi_session_expired_unbind),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun BangumiSessionExpiredSnackbarPreviewContent() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        BangumiSessionExpiredSnackbar(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            onView = {},
        )
    }
}

@Preview
@Composable
private fun PreviewBangumiSessionExpiredSnackbar() = ProvideCompositionLocalsForPreview {
    BangumiSessionExpiredSnackbarPreviewContent()
}

@Preview
@Composable
private fun PreviewBangumiSessionExpiredDialog() = ProvideCompositionLocalsForPreview {
    BangumiSessionExpiredDialog(
        onDismissRequest = {},
        onLogin = {},
        onUnbindBangumi = {},
        unbindEnabled = true,
    )
}
