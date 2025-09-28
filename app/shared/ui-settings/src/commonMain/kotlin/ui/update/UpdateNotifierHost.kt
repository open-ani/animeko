/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.tools.update.InstallationResult
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun BoxScope.UpdateNotifier(
    viewModel: AppUpdateViewModel = viewModel { AppUpdateViewModel() },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    layoutKind: UpdateNotifierLayoutKind = UpdateNotifierDefaults.layoutKind(),
) {
    SideEffect {
        // 用户每次从别的页面回到主页, 都会触发一次检查.
        viewModel.startAutomaticCheckLatestVersion()
    }

    val uriHandler = LocalUriHandler.current

    val presentation by viewModel.presentationFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showInstallationError by rememberSaveable { mutableStateOf<InstallationResult.Failed?>(null) }

    UpdateNotifier(
        presentation = presentation,
        onStartUpdateClick = {
            presentation.newVersion?.let {
                viewModel.startDownload(it, uriHandler)
            }
        },
        onInstallClick = {
            viewModel.install(context)?.let {
                showInstallationError = it
            }
        },
        onCancelClick = {
            viewModel.cancelDownload()
        },
        onRetryClick = {
            viewModel.restartDownload(uriHandler)
        },
        snackbarHostState = snackbarHostState,
        layoutKind = layoutKind,
    )

    showInstallationError?.let {
        FailedToInstallDialog(
            it.reason.toString(),
            onDismissRequest = {
                showInstallationError = null
            },
            state = presentation.state,
        )
    }
}

/**
 * A one‑stop host that
 *  1. kicks off automatic update check on the first composition, and
 *  2. shows *either* a bottom‑right popup (desktop) *or* a snackbar (mobile)
 *     when a new version is available.
 */
@Composable
fun BoxScope.UpdateNotifier(
    presentation: AppUpdatePresentation,
    onStartUpdateClick: () -> Unit,
    onInstallClick: () -> Unit,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    layoutKind: UpdateNotifierLayoutKind = UpdateNotifierDefaults.layoutKind(),
) {
    val uriHandler = LocalUriHandler.current

    // Track whether the user dismisses the notification in this session
    var dismissedManually by rememberSaveable(
        presentation.newVersion?.name, // 当有新版本时, 重新显示
    ) {
        mutableStateOf(false)
    }

    val newVersion = presentation.newVersion
    if (newVersion != null) {
        if (presentation.isDownloading) {
            val positionModifiers = when (layoutKind) {
                UpdateNotifierLayoutKind.POPUP -> Modifier.padding(24.dp).align(Alignment.BottomEnd)
                UpdateNotifierLayoutKind.SNACKBAR -> Modifier.padding(16.dp).fillMaxWidth()
                    .align(Alignment.BottomCenter)
            }

            if (!dismissedManually) {
                DownloadingUpdatePopupCard(
                    version = presentation.newVersion,
                    fileDownloaderStats = presentation.fileDownloaderStats,
                    error = presentation.downloadError,
                    onInstallClick = onInstallClick,
                    onCancelClick = {
                        onCancelClick()
                        dismissedManually = true
                    },
                    onRetryClick = onRetryClick,
                    modifier = positionModifiers,
                )
            }
        } else {
            // 提示有新版本

            val onDetailsClick =
                { uriHandler.openUri("https://github.com/open-ani/animeko/releases/tag/v${newVersion.name}") }

            when (layoutKind) {
                UpdateNotifierLayoutKind.POPUP -> {
                    if (!dismissedManually) {
                        DesktopPopup(
                            version = newVersion,
                            onDismiss = { dismissedManually = true },
                            onDetailsClick,
                            onAutoUpdateClick = onStartUpdateClick,
                            modifier = Modifier.align(Alignment.BottomEnd)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(24.dp)
                                .shadow(4.dp, MaterialTheme.shapes.extraLarge),
                        )
                    }
                }

                UpdateNotifierLayoutKind.SNACKBAR -> {
                    // 点击 snackbar 后显示 popup
                    var showDetails by rememberSaveable { mutableStateOf(false) }

                    if (!dismissedManually) {
                        MobileSnackbar(
                            hostState = snackbarHostState,
                            version = newVersion,
                            onShowDetailsClick = { showDetails = true },
                            onDismissChanged = { dismissedManually = it },
                        )
                    }

                    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

                    // If snackbar action is clicked, we pop up a dialog containing the card.
                    if (showDetails && !dismissedManually) {
                        BasicAlertDialog(
                            { dismissedManually = true },
                        ) {
                            NewVersionPopupCard(
                                version = newVersion.name,
                                changes = newVersion.majorChanges,
                                onDetailsClick = onDetailsClick,
                                onAutoUpdateClick = onStartUpdateClick,
                                onDismissRequest = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class UpdateNotifierLayoutKind {
    POPUP,
    SNACKBAR,
}

object UpdateNotifierDefaults {
    @Composable
    fun layoutKind(windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass): UpdateNotifierLayoutKind {
        return if (windowSizeClass.isWidthAtLeastMedium && windowSizeClass.isHeightAtLeastMedium) {
            UpdateNotifierLayoutKind.POPUP
        } else {
            UpdateNotifierLayoutKind.SNACKBAR
        }
    }
}

// ───────────────────────── Desktop implementation ────────────────────────────
@Composable
private fun BoxScope.DesktopPopup(
    version: NewVersion,
    onDismiss: () -> Unit,
    onDetailsClick: () -> Unit,
    onAutoUpdateClick: () -> Unit,
    modifier: Modifier,
) {
    AniAnimatedVisibility(
        visible = true,
        modifier = modifier.align(Alignment.BottomEnd),
    ) {
        NewVersionPopupCard(
            version = version.name,
            changes = version.majorChanges,
            onDetailsClick = onDetailsClick,
            onAutoUpdateClick = onAutoUpdateClick,
            onDismissRequest = onDismiss,
        )
    }
}

// ───────────────────────── Mobile implementation ─────────────────────────────
@Composable
private fun MobileSnackbar(
    hostState: SnackbarHostState,
    version: NewVersion,
    onShowDetailsClick: () -> Unit,
    onDismissChanged: (Boolean) -> Unit,
) {
    LaunchedEffect(version.name) {
        val result = hostState.showSnackbar(
            message = buildString {
                append("新版本 ${version.name}")

                version.majorChanges.firstOrNull()?.let {
                    append("：$it")
                }
            },
            actionLabel = "查看",
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> {
                onShowDetailsClick()
            }

            SnackbarResult.Dismissed -> onDismissChanged(true) // user swiped it away / dismissed
        }
    }
}

///////////////////////////////////////////////////////////////////////////
// Previews
///////////////////////////////////////////////////////////////////////////

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewUpdateNotifierHostPopup() = ProvideCompositionLocalsForPreview {
    PreviewImpl(UpdateNotifierLayoutKind.POPUP)
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewUpdateNotifierHostPopupDownloading() = ProvideCompositionLocalsForPreview {
    PreviewImpl(
        UpdateNotifierLayoutKind.POPUP,
        state = TestAppUpdatePresentations.Downloading,
    )
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewUpdateNotifierHostSnackbar() = ProvideCompositionLocalsForPreview {
    PreviewImpl(UpdateNotifierLayoutKind.SNACKBAR)
}

@TestOnly
@Composable
private fun PreviewImpl(
    kind: UpdateNotifierLayoutKind,
    state: AppUpdatePresentation = TestAppUpdatePresentations.HasUpdate,
) {
    Box {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(10) {
                ListItem(headlineContent = { Text("Test $it") })
            }
        }

        UpdateNotifier(
            presentation = state,
            onStartUpdateClick = {},
            {},
            {},
            {},
            layoutKind = kind,
        )
    }
}
