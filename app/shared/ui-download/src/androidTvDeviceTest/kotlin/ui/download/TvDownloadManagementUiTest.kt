/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.download

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Surface
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.download.DownloadEpisodeOption
import me.him188.ani.app.domain.media.download.DownloadEpisodeOption.Availability
import me.him188.ani.app.ui.download.DownloadManagementUiState
import me.him188.ani.app.ui.download.components.DownloadStatus
import me.him188.ani.app.ui.download.components.SubjectDownloadGroup
import me.him188.ani.app.ui.download.components.createTestDownloadItem
import me.him188.ani.app.ui.download.subject.DownloadEpisodePickerState
import me.him188.ani.app.ui.download.subject.SubjectDownloadListItem
import me.him188.ani.app.ui.download.subject.SubjectDownloadsUiState
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.tv.ui.foundation.theme.TvApplicationTheme
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
class TvDownloadManagementUiTest {
    private val intents = mutableListOf<TvDownloadIntent>()
    private var selected by mutableStateOf<Int?>(null)
    private var downloads by mutableStateOf(listOf(
        createTestDownloadItem(1).copy(id = "one"),
        createTestDownloadItem(2, initialState = DownloadStatus.PAUSED).copy(id = "two"),
    ))
    private fun AniComposeUiTest.mount() {
        setContent {
            TvApplicationTheme(ThemeSettings.Default.seedColor, "zh-CN") {
                Surface(Modifier.fillMaxSize()) {
                    TvDownloadManagementScreen(
                        DownloadManagementUiState(MediaStats.Zero, if (downloads.isEmpty()) emptyList() else listOf(
                            SubjectDownloadGroup(1, "孤独摇滚", downloads, null),
                        )), selected,
                        SubjectDownloadsUiState("孤独摇滚", downloads.map { SubjectDownloadListItem.Download(it) }, downloads,
                            episodesLoading = false, downloadsLoading = false),
                        { intent ->
                            intents += intent
                            when (intent) {
                                is TvDownloadIntent.SelectSubject -> selected = intent.id
                                is TvDownloadIntent.Pause -> downloads = downloads.map {
                                    if (it.id in intent.ids) it.copy(status = DownloadStatus.PAUSED) else it
                                }
                                is TvDownloadIntent.Delete -> downloads = downloads.filterNot { it.id in intent.ids }
                                else -> Unit
                            }
                        },
                    )
                }
            }
        }
        awaitFocus("tv-download-add")
    }

    @Test
    fun remoteNavigationPausesAndRestoresTheDownloadRow() = runAniComposeUiTest {
        mount()
        key(Key.DirectionDown)
        awaitFocus("tv-download-subject-1")
        key(Key.DirectionRight)
        awaitFocus("tv-download-detail-back")
        key(Key.DirectionDown)
        awaitFocus("tv-download-download-one")
        capture("list")
        key(Key.DirectionCenter)
        awaitFocus("tv-download-modal-cancel")
        key(Key.DirectionDown)
        awaitFocus("tv-download-play")
        key(Key.DirectionDown)
        awaitFocus("tv-download-pause")
        key(Key.DirectionCenter)
        awaitFocus("tv-download-download-one")
        assertEquals(setOf("one"), assertIs<TvDownloadIntent.Pause>(intents.last()).ids)
        key(Key.Back)
        awaitFocus("tv-download-subject-1")
    }

    @Test
    fun deleteRequiresConfirmationAndLeavesFocusOnAStableControl() = runAniComposeUiTest {
        mount()
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        awaitFocus("tv-download-detail-back")
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        awaitFocus("tv-download-modal-cancel")
        navigateTo("tv-download-delete")
        key(Key.DirectionCenter)
        awaitFocus("tv-download-modal-cancel")
        capture("delete")
        key(Key.Back)
        awaitFocus("tv-download-download-one")
        assertTrue(intents.none { it is TvDownloadIntent.Delete })
        key(Key.DirectionCenter)
        awaitFocus("tv-download-modal-cancel")
        navigateTo("tv-download-delete")
        key(Key.DirectionCenter)
        awaitFocus("tv-download-modal-cancel")
        navigateTo("tv-download-confirm-delete")
        key(Key.DirectionCenter)
        awaitFocus("tv-download-detail-back")
        assertEquals(listOf("two"), downloads.map { it.id })
    }

    @Test
    fun emptyLibraryStillOffersAddingDownloads() = runAniComposeUiTest {
        downloads = emptyList()
        mount()
        capture("empty")
        key(Key.DirectionCenter)
        assertEquals(TvDownloadIntent.Add, intents.last())
    }

    @Test
    fun episodePickerOnlyConfirmsAvailableSelectedEpisodes() = runAniComposeUiTest {
        var confirmed: Set<Int>? = null
        val options = listOf(
            DownloadEpisodeOption(1, EpisodeSort(1), "第 1 集", Availability.AVAILABLE, "合集", isCurrent = true),
            DownloadEpisodeOption(2, EpisodeSort(2), "第 2 集", Availability.AVAILABLE, "合集", isCurrent = false),
            DownloadEpisodeOption(3, EpisodeSort(3), "第 3 集", Availability.ALREADY_DOWNLOADED, null, isCurrent = false),
            DownloadEpisodeOption(4, EpisodeSort(4), "第 4 集", Availability.UNMATCHED, null, isCurrent = false),
        )
        val picker = DownloadEpisodePickerState(1, TestMediaList.first(), options)
        setContent {
            TvApplicationTheme(ThemeSettings.Default.seedColor, "zh-CN") {
                Surface(Modifier.fillMaxSize()) {
                    TvDownloadEpisodePicker(picker, {}, { confirmed = it }, {})
                }
            }
        }
        awaitFocus("tv-download-modal-cancel")
        onNodeWithTag("tv-download-pick-1").assertIsOn()
        onNodeWithTag("tv-download-pick-2").assertIsOn()
        onNodeWithTag("tv-download-pick-3").assertIsNotEnabled()
        onNodeWithTag("tv-download-pick-4").assertIsNotEnabled()
        navigateTo("tv-download-pick-2")
        key(Key.DirectionCenter)
        capture("episodes", "tv-download-modal")
        navigateTo("tv-download-pick-confirm")
        key(Key.DirectionCenter)
        assertEquals(setOf(1), confirmed)
    }

    private fun AniComposeUiTest.navigateTo(tag: String) {
        repeat(12) {
            if (onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()) return
            key(Key.DirectionDown)
        }
        error("Unable to reach $tag with the remote")
    }

    private fun AniComposeUiTest.key(key: Key) {
        onAllNodes(isRoot() and hasAnyDescendant(isFocused())).onLast().performKeyInput { pressKey(key) }
        mainClock.advanceTimeByFrame()
    }
    private fun AniComposeUiTest.awaitFocus(tag: String) {
        waitUntil(timeoutMillis = 5_000) {
            mainClock.advanceTimeByFrame()
            onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(tag).assertIsFocused()
    }
    private fun AniComposeUiTest.capture(name: String, tag: String = "tv-downloads") {
        mainClock.advanceTimeBy(400)
        onNodeWithTag(tag).assertScreenshot("tv-downloads/$name")
        val path = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "tv-downloads-$name.png")
        path.outputStream().use { onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
