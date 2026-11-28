/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.subtitle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.him188.ani.app.data.models.preference.SubtitleStyle
import me.him188.ani.app.data.models.preference.SubtitleTextConversion
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.openani.mediamp.metadata.SubtitleTrack
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer

class ExternalSubtitleOverlayTest {
    @Test
    fun `selected caption uses chosen color and moves within the player bounds`() = runAniComposeUiTest {
        var style by mutableStateOf(SubtitleStyle.Default)
        setContent {
            CompositionLocalProvider(LocalSubtitleAppearance provides SubtitleAppearance(style = style)) {
                Box(Modifier.requiredSize(640.dp, 360.dp)) { SubtitleTextOverlay("Caption") }
            }
        }
        val bottom = onNodeWithTag("ExternalSubtitleText").fetchSemanticsNode().boundsInRoot.top
        runOnIdle { style = style.copy(textColor = 0xFFFFFF00, verticalPosition = 0f, backgroundEnabled = true) }
        assertEquals(Color.Yellow, subtitleLayout().layoutInput.style.color)
        val bounds = onNodeWithTag("ExternalSubtitleText").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top < bottom)
        assertTrue(bounds.width > 0 && bounds.height > 0)
    }

    @Test
    fun `selected local and online subtitles convert from their originals and respect off and seek`() = runAniComposeUiTest {
        var conversion by mutableStateOf(SubtitleTextConversion.ORIGINAL)
        lateinit var controller: ExternalSubtitleController
        lateinit var player: TestMediampPlayer
        var candidates = emptyList<SubtitleTrack>()
        setContent {
            val scope = rememberCoroutineScope()
            player = remember { TestMediampPlayer(scope.coroutineContext) }
            controller = remember { ExternalSubtitleController(player, scope) }
            val tracks by controller.tracks.candidates.collectAsState(emptyList())
            candidates = tracks
            LaunchedEffect(Unit) {
                val media = UriMediaData("https://test/video.mp4")
                player.setMediaData(media)
                controller.select("local.srt", "1\n00:00:00,000 --> 00:00:03,000\n软件與網絡".encodeToByteArray(), media)
                controller.select("online.srt", "1\n00:00:00,000 --> 00:00:03,000\n汉语和漢語".encodeToByteArray(), media)
                player.injectPosition(1000)
            }
            CompositionLocalProvider(LocalSubtitleTextConversion provides conversion) {
                Box(Modifier.requiredSize(320.dp, 180.dp)) { ExternalSubtitleOverlay(controller) }
            }
        }
        waitUntil { onAllNodesWithTag("ExternalSubtitleText").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("汉语和漢語").fetchSemanticsNode()
        runOnIdle { conversion = SubtitleTextConversion.TRADITIONAL }
        waitUntil { onAllNodesWithText("漢語和漢語").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { conversion = SubtitleTextConversion.SIMPLIFIED }
        waitUntil { onAllNodesWithText("汉语和汉语").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { controller.tracks.select(null) }
        onNodeWithTag("ExternalSubtitleText").assertDoesNotExist()
        waitUntil { candidates.size == 2 }
        runOnIdle { controller.tracks.select(candidates.first()) }
        waitUntil { onAllNodesWithText("软件与网络").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { conversion = SubtitleTextConversion.ORIGINAL }
        onNodeWithText("软件與網絡").fetchSemanticsNode()
        runOnIdle { player.injectPosition(5000) }
        onNodeWithTag("ExternalSubtitleText").assertDoesNotExist()
        runOnIdle { player.injectPosition(1000) }
        onNodeWithText("软件與網絡").fetchSemanticsNode()
    }

    @Test
    fun `external subtitles scale with the player while controls preserve their size`() = runAniComposeUiTest {
        var height by mutableStateOf(360f)
        var scale by mutableStateOf(1f)
        var bottom by mutableStateOf(0.0)
        setContent { SubtitleFixture(height, scale, bottom) }
        waitUntil { onAllNodesWithTag("ExternalSubtitleText").fetchSemanticsNodes().isNotEmpty() }

        val large = subtitleLayout().fontSizePx()
        runOnIdle { height = 180f }
        assertEquals(large / 2, subtitleLayout().fontSizePx(), 0.01f)
        runOnIdle { height = 360f }
        assertEquals(19f, subtitleLayout().fontSizePx(), 0.01f)
        runOnIdle { bottom = 96.0 }
        assertEquals(large, subtitleLayout().fontSizePx(), 0.01f)
        runOnIdle { scale = 2f }
        assertEquals(large * 2, subtitleLayout().fontSizePx(), 0.01f)
        assertEquals(2, subtitleLayout().lineCount)
    }

    @Test
    fun `subtitle size relative to video is independent of display and interface font scaling`() = runAniComposeUiTest {
        var displayDensity by mutableStateOf(1f)
        var interfaceFontScale by mutableStateOf(1f)
        setContent {
            SubtitleFixture(240f, density = Density(displayDensity, interfaceFontScale))
        }
        waitUntil { onAllNodesWithTag("ExternalSubtitleText").fetchSemanticsNodes().isNotEmpty() }
        val normal = subtitleLayout().fontSizePx()
        runOnIdle { interfaceFontScale = 2f }
        assertEquals(normal, subtitleLayout().fontSizePx(), 0.01f)
        runOnIdle { displayDensity = 2f }
        assertEquals(normal * 2, subtitleLayout().fontSizePx(), 0.01f)
    }

    private fun AniComposeUiTest.subtitleLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithTag("ExternalSubtitleText").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun TextLayoutResult.fontSizePx(): Float = with(layoutInput.density) { layoutInput.style.fontSize.toPx() }
}

@Composable
private fun SubtitleFixture(
    height: Float,
    scale: Float = 1f,
    bottom: Double = 0.0,
    density: Density = Density(1f),
) {
    val scope = rememberCoroutineScope()
    val player = remember { TestMediampPlayer(scope.coroutineContext) }
    val controller = remember { ExternalSubtitleController(player, scope) }
    LaunchedEffect(Unit) {
        val media = UriMediaData("https://test/video.mp4")
        player.setMediaData(media)
        controller.select("online.srt", "1\n00:00:00,000 --> 00:00:03,000\n第一行\nSecond line".encodeToByteArray(), media)
        player.injectPosition(1000)
    }
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalSubtitleAppearance provides SubtitleAppearance(scale),
        LocalSubtitleSafeArea provides SubtitleSafeArea(bottom = bottom),
    ) {
        Box(Modifier.requiredSize((height * 16 / 9).dp, height.dp)) {
            ExternalSubtitleOverlay(controller)
        }
    }
}
