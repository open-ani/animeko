/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.download.DownloadManagementTestTags
import me.him188.ani.app.ui.download.components.DownloadSelectionToolbarTestTags
import me.him188.ani.app.ui.download.components.DownloadStatus
import me.him188.ani.app.ui.download.components.createTestDownloadItem
import me.him188.ani.app.ui.download.components.rememberDownloadSelectionState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_management_episode_label
import me.him188.ani.app.ui.lang.cache_management_select_all_action
import me.him188.ani.app.ui.lang.cache_management_selected_count
import me.him188.ani.app.ui.lang.cache_subject_cache
import me.him188.ani.app.ui.lang.cache_subject_pause_all
import me.him188.ani.app.ui.mediafetch.createTestMediaSourceInfoProvider
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString

@OptIn(TestOnly::class)
class SubjectDownloadsPageTest {
    private val finished = createTestDownloadItem(1, progress = 1f.toProgress(), initialState = DownloadStatus.COMPLETED)
    private val downloading = createTestDownloadItem(2, initialState = DownloadStatus.IN_PROGRESS)
    private val paused = createTestDownloadItem(3, initialState = DownloadStatus.PAUSED)
    private val downloads = listOf(finished, downloading, paused)
    private val episode = EpisodeDownloadItem(4, EpisodeSort(4), "Episode 4", UnifiedCollectionType.DOING, true)

    @Test
    fun `shows downloads and new episode and dispatches download and pause all`() = runAniComposeUiTest {
        var requested: Int? = null
        var pauseAll = false
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectDownloadsPage(
                    state = SubjectDownloadsUiState(
                        title = "Subject",
                        items = buildSubjectDownloadItems(listOf(episode), downloads),
                        downloads = downloads,
                        episodesLoading = false,
                        downloadsLoading = false,
                    ),
                    selection = rememberDownloadSelectionState(),
                    actions = actions(download = { requested = it }, pauseAll = { pauseAll = true }),
                    sourceInfoProvider = createTestMediaSourceInfoProvider(),
                    onPlay = {},
                    onViewDetail = null,
                )
            }
        }
        onNodeWithText(runBlocking { getString(Lang.cache_management_episode_label, episode.sort, "Episode 4") }).assertExists()
        onNodeWithContentDescription(runBlocking { getString(Lang.cache_subject_cache) }).performClick()
        onNodeWithText(runBlocking { getString(Lang.cache_subject_pause_all) }).performClick()
        runOnIdle { assertEquals(4, requested); assertEquals(true, pauseAll) }
    }

    @Test
    fun `consecutive batch actions keep selection while displayed states remain unchanged`() = runAniComposeUiTest {
        val commands = mutableListOf<Pair<String, Set<String>>>()
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectDownloadsPage(
                    state = SubjectDownloadsUiState(
                        title = "Subject",
                        items = buildSubjectDownloadItems(emptyList(), downloads),
                        downloads = downloads,
                        episodesLoading = false,
                        downloadsLoading = false,
                    ),
                    selection = rememberDownloadSelectionState(),
                    actions = actions(
                        pause = { commands += "pause" to it },
                        resume = { commands += "resume" to it },
                    ),
                    sourceInfoProvider = createTestMediaSourceInfoProvider(),
                    onPlay = {},
                    onViewDetail = null,
                )
            }
        }
        onNodeWithText(runBlocking { getString(Lang.cache_management_episode_label, finished.sort, finished.displayName) })
            .performTouchInput { longClick() }
        onNodeWithText(runBlocking { getString(Lang.cache_management_select_all_action) }).performClick()
        onNodeWithTag(DownloadSelectionToolbarTestTags.PAUSE).performClick()
        onNodeWithTag(DownloadSelectionToolbarTestTags.RESUME).performClick()
        runOnIdle {
            val ids: Set<String> = downloads.mapTo(hashSetOf()) { it.id }
            assertEquals(listOf("pause" to ids, "resume" to ids), commands)
        }
    }

    @Test
    fun `partial deletion keeps failed item selected and completed deletion exits selection`() = runAniComposeUiTest {
        var currentDownloads by mutableStateOf(downloads)
        var requestedIds: Set<String>? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectDownloadsPage(
                    state = SubjectDownloadsUiState(
                        title = "Subject",
                        items = buildSubjectDownloadItems(emptyList(), currentDownloads),
                        downloads = currentDownloads,
                        episodesLoading = false,
                        downloadsLoading = false,
                    ),
                    selection = rememberDownloadSelectionState(),
                    actions = actions(delete = { requestedIds = it; currentDownloads = listOf(paused) }),
                    sourceInfoProvider = createTestMediaSourceInfoProvider(),
                    onPlay = {},
                    onViewDetail = null,
                )
            }
        }
        onNodeWithText(runBlocking { getString(Lang.cache_management_episode_label, finished.sort, finished.displayName) })
            .performTouchInput { longClick() }
        onNodeWithText(runBlocking { getString(Lang.cache_management_select_all_action) }).performClick()
        onNodeWithTag(DownloadSelectionToolbarTestTags.DELETE).performClick()
        onNodeWithTag(DownloadManagementTestTags.DELETE_CONFIRM_BUTTON).performClick()
        runOnIdle { assertEquals(downloads.mapTo(hashSetOf()) { it.id }, requestedIds) }
        onNodeWithText(runBlocking { getString(Lang.cache_management_selected_count, 1) }).assertExists()
        runOnIdle { currentDownloads = emptyList() }
        onNodeWithText(runBlocking { getString(Lang.cache_management_selected_count, 1) }).assertDoesNotExist()
    }

    private fun actions(
        download: (Int) -> Unit = {},
        pause: (Set<String>) -> Unit = {},
        resume: (Set<String>) -> Unit = {},
        delete: (Set<String>) -> Unit = {},
        pauseAll: () -> Unit = {},
    ) = SubjectDownloadActions(download, {}, pause, resume, delete, pauseAll, {}, {})
}
