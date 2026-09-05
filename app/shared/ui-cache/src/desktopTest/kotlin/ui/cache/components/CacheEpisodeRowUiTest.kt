/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_episode_watched_progress
import me.him188.ani.app.ui.lang.cache_filter_status_finished
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

@OptIn(TestOnly::class)
class CacheEpisodeRowUiTest {
    @Test
    fun `completed cache shows watched progress beside finished without progress semantics`() = runAniComposeUiTest {
        val episode = createTestCacheEpisode(
            sort = 1,
            initialState = CacheEpisodePaused.COMPLETED,
            progress = 1f.toProgress(),
            downloadSpeed = FileSize.Unspecified,
            playbackProgress = 0.5f.toProgress(),
        )
        val metadataText = runBlocking {
            "${episode.detailedSizeText} · ${getString(Lang.cache_filter_status_finished)} · " +
                    getString(Lang.cache_episode_watched_progress, "50.0%")
        }

        setContent {
            ProvideCompositionLocalsForPreview {
                CacheEpisodeRow(
                    episode = episode,
                    mediaSourceInfoProvider = null,
                    selectionMode = false,
                    selected = false,
                    onToggleSelected = {},
                    onEnterSelection = {},
                    onPlay = {},
                    onResume = {},
                    onPause = {},
                    onDelete = {},
                    onViewDetail = null,
                )
            }
        }

        onNodeWithText(metadataText, useUnmergedTree = true).assertExists()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(0)
    }
}
