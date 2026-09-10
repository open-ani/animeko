/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.ui.cache.CacheManagementScreen
import me.him188.ani.app.ui.cache.CacheManagementState
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_filter_download_status
import me.him188.ani.app.ui.lang.cache_filter_status_downloading
import me.him188.ani.app.ui.lang.cache_filter_status_finished
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

class CacheDownloadingFilterUiTest {
    @Test
    fun `downloading filter includes pending playability and reacts to completion`() = runAniComposeUiTest {
        val statusText = runBlocking { getString(Lang.cache_filter_download_status) }
        val downloadingText = runBlocking { getString(Lang.cache_filter_status_downloading) }
        val finishedText = runBlocking { getString(Lang.cache_filter_status_finished) }
        val cache = TestCacheWithPendingPlayability(1)
        val groupMatcher = hasText("subject") and hasClickAction()

        setContent {
            val coroutineScope = rememberCoroutineScope()
            val scope = remember {
                object : HasBackgroundScope {
                    override val backgroundScope: CoroutineScope = coroutineScope
                }
            }
            val groupsFlow = remember {
                scope.createCacheEpisodeStateFlow(
                    groupId = "1",
                    mediaCache = CacheWithEngine(cache, MediaCacheEngineKey.WebM3u),
                    subjectCollectionType = flowOf(UnifiedCollectionType.DOING),
                    playbackHistoriesByEpisodeId = flowOf(emptyMap()),
                ).map { entry ->
                    listOf(CacheGroupState(1, "subject", listOf(entry), UnifiedCollectionType.DOING))
                }
            }
            val groups by groupsFlow.collectAsState(emptyList())
            ProvideCompositionLocalsForPreview {
                CacheManagementScreen(
                    state = CacheManagementState(MediaStats.Zero, groups),
                    selfInfo = null,
                    onPlay = {},
                    onResume = {},
                    onPause = {},
                    onViewDetail = {},
                    onDelete = {},
                    onClickLogin = {},
                )
            }
        }

        onNodeWithText(statusText).performScrollTo().performClick()
        onNodeWithText(downloadingText).performClick()
        waitUntil { onAllNodes(groupMatcher).fetchSemanticsNodes().isNotEmpty() }
        onNode(groupMatcher).assertExists()

        runOnIdle { cache.state.value = MediaCacheState.COMPLETED }
        waitUntil { onAllNodes(groupMatcher).fetchSemanticsNodes().isEmpty() }
        onNode(groupMatcher).assertDoesNotExist()

        // Clearing the active filter and choosing Finished must reveal the same cache.
        onNodeWithText(downloadingText).performClick()
        onNodeWithText(statusText).performClick()
        onNode(hasText(finishedText) and hasAnyAncestor(isPopup())).performClick()
        onNode(groupMatcher).assertExists()
    }
}
