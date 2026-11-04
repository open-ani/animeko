/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.auto

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.selector.PlaybackSourceMemory
import me.him188.ani.app.domain.media.selector.PlaybackAutoSelectionState
import me.him188.ani.app.domain.media.selector.MediaAutoSelectOutcome
import me.him188.ani.app.domain.media.selector.MediaAutoSelectExhaustCause
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.mediafetch.createTestMediaSelectorState
import me.him188.ani.datasources.api.WebMediaRoute
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class AutoMatchRuntimeMemoryTest {
    @Test
    fun `exhausted selection can retry when there are no cross episode route failures`() = runAniComposeUiTest {
        val selection = PlaybackAutoSelectionState()
        val memory = PlaybackSourceMemory()
        var retries = 0
        selection.finish(selection.begin(), MediaAutoSelectOutcome.Exhausted(MediaAutoSelectExhaustCause.NO_WEB_CANDIDATES))
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                val state = remember {
                    createTestMediaSelectorState(
                        scope, mediaList = emptyList(), playbackSourceMemory = memory, autoSelectionState = selection.state,
                        onRetryFailedPlaybackSources = {
                            retries++
                            selection.reset()
                        },
                    )
                }
                AutoMatchPage(state, onClickItem = {}, onRestartSource = {}, onRequestManualSearch = null)
            }
        }

        onNodeWithTag(AutoMatchPageTestTags.EXHAUSTED).assertIsDisplayed()
        onNodeWithTag(AutoMatchPageTestTags.RETRY_FAILED_ROUTES).assertIsDisplayed().performClick()
        waitUntil { retries == 1 }
        onNodeWithTag(AutoMatchPageTestTags.EXHAUSTED).assertDoesNotExist()
        onNodeWithTag(AutoMatchPageTestTags.RETRY_FAILED_ROUTES).assertDoesNotExist()
        assertTrue(memory.snapshot().failures.isEmpty())
    }

    @Test
    fun `exhaustion is visible without route failures and clears for another attempt`() = runAniComposeUiTest {
        val selection = PlaybackAutoSelectionState()
        selection.finish(selection.begin(), MediaAutoSelectOutcome.Exhausted(MediaAutoSelectExhaustCause.NO_WEB_CANDIDATES))
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                val state = remember {
                    createTestMediaSelectorState(scope, mediaList = emptyList(), autoSelectionState = selection.state)
                }
                AutoMatchPage(state, onClickItem = {}, onRestartSource = {}, onRequestManualSearch = null)
            }
        }

        onNodeWithTag(AutoMatchPageTestTags.EXHAUSTED).assertIsDisplayed()
        runOnIdle { selection.begin() }
        onNodeWithTag(AutoMatchPageTestTags.EXHAUSTED).assertDoesNotExist()
    }

    @Test
    fun `retry action clears session failures while retaining successful route`() = runAniComposeUiTest {
        val memory = PlaybackSourceMemory()
        val failed = TestMediaList[0].copy(
            kind = MediaSourceKind.WEB,
            properties = TestMediaList[0].properties.copy(webRoute = WebMediaRoute("https://site/subject/1", "line-1", "config")),
        )
        val successful = failed.copy(mediaId = "web-c.1", mediaSourceId = "web-c")
        memory.recordSuccess(memory.beginAttempt(1, successful))
        memory.recordFailure(memory.beginAttempt(1, failed), PlaybackSourceMemory.FailureReason.RESOLUTION)
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                val state = remember {
                    createTestMediaSelectorState(
                        scope,
                        mediaList = emptyList(),
                        playbackSourceMemory = memory,
                        onRetryFailedPlaybackSources = { memory.retryFailures() },
                    )
                }
                AutoMatchPage(state, onClickItem = {}, onRestartSource = {}, onRequestManualSearch = null)
            }
        }

        onNodeWithTag(AutoMatchPageTestTags.RETRY_FAILED_ROUTES).assertIsDisplayed().performClick()
        waitUntil { memory.snapshot().failures.isEmpty() }
        onNodeWithTag(AutoMatchPageTestTags.RETRY_FAILED_ROUTES).assertDoesNotExist()
        assertTrue(memory.snapshot().isRecentSuccess(successful))
    }
}
