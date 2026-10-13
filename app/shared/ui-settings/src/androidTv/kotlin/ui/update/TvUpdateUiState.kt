/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.update

import me.him188.ani.app.domain.foundation.VersionExpiryState
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.ui.update.AppUpdatePresentation
import me.him188.ani.app.ui.update.AppUpdateState

data class TvUpdateUiState(
    val visible: Boolean = false,
    val expired: Boolean = false,
    val expiredLatestVersion: String? = null,
    val phase: TvUpdatePhase = TvUpdatePhase.Checking,
    val newVersion: String? = null,
    val changes: List<String> = emptyList(),
    val downloadProgress: Float = 0f,
)

enum class TvUpdatePhase { Checking, CheckFailed, UpToDate, Available, Downloading, DownloadFailed, Downloaded, Installing }

sealed interface TvUpdateIntent {
    data object AutoCheck : TvUpdateIntent
    data object Check : TvUpdateIntent
    data object Download : TvUpdateIntent
    data object CancelDownload : TvUpdateIntent
    data class Install(val context: ContextMP) : TvUpdateIntent
    data object Dismiss : TvUpdateIntent
}

private val promptPhases = setOf(
    TvUpdatePhase.Available, TvUpdatePhase.Downloading, TvUpdatePhase.DownloadFailed,
    TvUpdatePhase.Downloaded, TvUpdatePhase.Installing,
)

internal fun AppUpdatePresentation.toTvUpdateUiState(
    expiry: VersionExpiryState?,
    requested: Boolean,
    dismissedVersion: String?,
): TvUpdateUiState {
    val phase = when (state) {
        is AppUpdateState.Downloading -> TvUpdatePhase.Downloading
        is AppUpdateState.DownloadFailed -> TvUpdatePhase.DownloadFailed
        is AppUpdateState.Downloaded -> TvUpdatePhase.Downloaded
        is AppUpdateState.Installing -> TvUpdatePhase.Installing
        else -> when {
            isCheckingUpdate -> TvUpdatePhase.Checking
            checkUpdateError != null -> TvUpdatePhase.CheckFailed
            newVersion != null -> TvUpdatePhase.Available
            state == AppUpdateState.AlreadyUpToDate -> TvUpdatePhase.UpToDate
            else -> TvUpdatePhase.Checking
        }
    }
    val versionName = newVersion?.name
    val prompted = phase in promptPhases && versionName != null && versionName != dismissedVersion
    return TvUpdateUiState(
        visible = expiry != null || requested || prompted,
        expired = expiry != null,
        expiredLatestVersion = expiry?.latestVersion,
        phase = phase,
        newVersion = versionName,
        changes = newVersion?.majorChanges.orEmpty(),
        downloadProgress = fileDownloaderStats.progress,
    )
}
