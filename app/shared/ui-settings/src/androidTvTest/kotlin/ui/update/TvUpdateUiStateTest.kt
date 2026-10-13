/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.update

import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.foundation.VersionExpiryState
import me.him188.ani.app.tools.update.FileDownloaderState
import me.him188.ani.app.ui.update.AppUpdatePresentation
import me.him188.ani.app.ui.update.AppUpdateState
import me.him188.ani.app.ui.update.Changelog
import me.him188.ani.app.ui.update.FileDownloaderStats
import me.him188.ani.app.ui.update.NewVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvUpdateUiStateTest {
    private val version = NewVersion(
        "5.4.0",
        listOf(Changelog("5.4.0", "", "- Feature A\n- Feature B")),
        listOf("https://example.com/ani-tv-5.4.0-arm64-v8a.apk"),
        "2026-10-01",
    )
    private val idleDownloader = FileDownloaderStats(0f, FileDownloaderState.Idle)

    private fun presentation(
        state: AppUpdateState,
        newVersion: NewVersion? = version,
        downloader: FileDownloaderStats = idleDownloader,
        isChecking: Boolean = false,
        error: LoadError? = null,
    ) = AppUpdatePresentation(
        newVersion = newVersion,
        state = state,
        fileDownloaderStats = downloader,
        isCheckingUpdate = isChecking,
        checkUpdateError = error,
    )

    @Test
    fun `new version is prompted until dismissed`() {
        val available = presentation(AppUpdateState.HasUpdate(version))
        val prompted = available.toTvUpdateUiState(expiry = null, requested = false, dismissedVersion = null)
        assertTrue(prompted.visible)
        assertEquals(TvUpdatePhase.Available, prompted.phase)
        assertEquals("5.4.0", prompted.newVersion)
        assertEquals(listOf("Feature A", "Feature B"), prompted.changes)

        val dismissed = available.toTvUpdateUiState(expiry = null, requested = false, dismissedVersion = "5.4.0")
        assertFalse(dismissed.visible)

        val newer = available.toTvUpdateUiState(expiry = null, requested = false, dismissedVersion = "5.3.0")
        assertTrue(newer.visible)
    }

    @Test
    fun `background check results stay hidden unless requested`() {
        val upToDate = presentation(AppUpdateState.AlreadyUpToDate, newVersion = null)
        assertFalse(upToDate.toTvUpdateUiState(null, requested = false, dismissedVersion = null).visible)

        val requested = upToDate.toTvUpdateUiState(null, requested = true, dismissedVersion = null)
        assertTrue(requested.visible)
        assertEquals(TvUpdatePhase.UpToDate, requested.phase)

        val failed = presentation(AppUpdateState.AlreadyUpToDate, newVersion = null, error = LoadError.NetworkError)
        assertFalse(failed.toTvUpdateUiState(null, requested = false, dismissedVersion = null).visible)
        assertEquals(TvUpdatePhase.CheckFailed, failed.toTvUpdateUiState(null, requested = true, dismissedVersion = null).phase)

        val checking = presentation(AppUpdateState.ClickToCheck, newVersion = null, isChecking = true)
        assertEquals(TvUpdatePhase.Checking, checking.toTvUpdateUiState(null, requested = true, dismissedVersion = null).phase)
    }

    @Test
    fun `expired version always shows the dialog`() {
        val expiry = VersionExpiryState("5.4.0")
        val checking = presentation(AppUpdateState.ClickToCheck, newVersion = null)
            .toTvUpdateUiState(expiry, requested = false, dismissedVersion = null)
        assertTrue(checking.visible)
        assertTrue(checking.expired)
        assertEquals("5.4.0", checking.expiredLatestVersion)
        assertEquals(TvUpdatePhase.Checking, checking.phase)

        val dismissed = presentation(AppUpdateState.HasUpdate(version))
            .toTvUpdateUiState(expiry, requested = false, dismissedVersion = "5.4.0")
        assertTrue(dismissed.visible)
    }

    @Test
    fun `download progress takes precedence over check state`() {
        val downloading = presentation(
            AppUpdateState.Downloading(version, FileDownloaderStats(.4f, FileDownloaderState.Downloading)),
            downloader = FileDownloaderStats(.4f, FileDownloaderState.Downloading),
            isChecking = true,
        ).toTvUpdateUiState(null, requested = false, dismissedVersion = null)
        assertEquals(TvUpdatePhase.Downloading, downloading.phase)
        assertEquals(.4f, downloading.downloadProgress)
        assertTrue(downloading.visible)
    }

    @Test
    fun `cancelled download offers the known version again`() {
        val cancelled = presentation(AppUpdateState.ClickToCheck)
            .toTvUpdateUiState(VersionExpiryState(null), requested = false, dismissedVersion = "5.4.0")
        assertEquals(TvUpdatePhase.Available, cancelled.phase)
        assertTrue(cancelled.visible)
    }
}
