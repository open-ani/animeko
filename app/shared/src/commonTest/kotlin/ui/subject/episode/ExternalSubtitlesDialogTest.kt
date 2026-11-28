/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.OpenSubtitlesSettings
import me.him188.ani.app.data.network.OpenSubtitleFile
import me.him188.ani.app.data.network.OpenSubtitlePage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.videoplayer.subtitle.ExternalSubtitleController
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer

class ExternalSubtitlesDialogTest {
    @Test
    fun `anonymous search and local import remain available without an API key`() = runAniComposeUiTest {
        var searches = 0
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                val player = remember {
                    TestMediampPlayer(scope.coroutineContext).also { player ->
                        scope.launch { player.setMediaData(UriMediaData("https://test/video.mp4")) }
                    }
                }
                val controller = remember { ExternalSubtitleController(player, scope) }
                ExternalSubtitlesDialog(
                    player, controller, OpenSubtitlesSettings.Default, "Anime", "3",
                    search = { _, _, _, _ -> searches++; OpenSubtitlePage(emptyList(), 1, 1) },
                    download = { error("No download expected") },
                    onDismissRequest = {},
                )
            }
        }
        onNodeWithTag("ImportLocalSubtitle").assertIsDisplayed().assertIsEnabled()
        onNodeWithTag("SearchOpenSubtitles").performScrollTo().assertIsEnabled().performClick()
        runOnIdle { assertEquals(1, searches) }
    }

    @Test
    fun `search only downloads the explicitly chosen result and loads its captions`() = runAniComposeUiTest {
        val requests = mutableListOf<String>()
        val downloads = mutableListOf<Int>()
        lateinit var controller: ExternalSubtitleController
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                val player = remember {
                    TestMediampPlayer(scope.coroutineContext).also { player ->
                        scope.launch { player.setMediaData(UriMediaData("https://test/video.mp4")) }
                    }
                }
                controller = remember { ExternalSubtitleController(player, scope) }
                ExternalSubtitlesDialog(
                    player, controller, OpenSubtitlesSettings(apiKey = "user-provided"), "Anime", "3",
                    search = { query, episode, season, page ->
                        requests += "$query/$episode/$season/$page"
                        OpenSubtitlePage(listOf(OpenSubtitleFile(9, "chosen.srt", "en", "")), 1, 1)
                    },
                    download = {
                        downloads += it.id
                        "1\n00:00:01,000 --> 00:00:03,000\nCaption".encodeToByteArray()
                    },
                    onDismissRequest = {},
                )
            }
        }
        onNodeWithTag("SubtitleSearchQuery").performTextReplacement("Another Anime")
        onNodeWithTag("SearchOpenSubtitles").performScrollTo().performClick()
        onNodeWithText("[en] chosen.srt").performScrollTo().assertIsDisplayed()
        runOnIdle {
            assertEquals(listOf("Another Anime/3/null/1"), requests)
            assertEquals(emptyList(), downloads)
        }
        onNodeWithText("[en] chosen.srt").performClick()
        waitUntil { controller.selected.value != null }
        runOnIdle {
            assertEquals(listOf(9), downloads)
            assertEquals("chosen.srt", controller.selected.value?.name)
        }
    }
}
