/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.update

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.foundation.VersionExpiryService
import me.him188.ani.app.ui.update.AppUpdateViewModel

@Stable
class TvUpdateViewModel(
    versionExpiryService: VersionExpiryService,
) : AppUpdateViewModel() {
    private val expiry = versionExpiryService.state
    private val requested = MutableStateFlow(false)
    private val dismissedVersion = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TvUpdateUiState> = combine(
        presentationFlow, expiry, requested, dismissedVersion,
    ) { presentation, expiry, requested, dismissedVersion ->
        presentation.toTvUpdateUiState(expiry, requested, dismissedVersion)
    }.stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), TvUpdateUiState())

    init {
        backgroundScope.launch {
            expiry.filterNotNull().first()
            startCheckLatestVersion(null)
        }
    }

    fun onIntent(intent: TvUpdateIntent) {
        when (intent) {
            TvUpdateIntent.AutoCheck -> startAutomaticCheckLatestVersion()
            TvUpdateIntent.Check -> {
                requested.value = true
                startCheckLatestVersion(null)
            }
            TvUpdateIntent.Download -> presentationFlow.value.newVersion?.let { startDownload(it, null) }
            TvUpdateIntent.CancelDownload -> {
                cancelDownload()
                dismiss()
            }
            is TvUpdateIntent.Install -> install(intent.context)
            TvUpdateIntent.Dismiss -> dismiss()
        }
    }

    private fun dismiss() {
        requested.value = false
        dismissedVersion.value = presentationFlow.value.newVersion?.name
    }
}
