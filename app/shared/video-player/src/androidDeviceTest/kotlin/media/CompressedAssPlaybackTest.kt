/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.videoplayer.media

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.io.files.Path
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.videoplayer.ui.VideoPlayer
import org.openani.mediamp.source.SystemFileMediaData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(UnstableApi::class)
class CompressedAssPlaybackTest {
    @Test
    fun rendersCompressedSubtitlesAndSwitchesToPlainTrack() = runAniComposeUiTest {
        lateinit var player: LibassExoPlayerMediampPlayer
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            player = remember { LibassExoPlayerMediampPlayer(context, scope.coroutineContext) }
            DisposableEffect(player) { onDispose { player.close() } }
            LaunchedEffect(player) {
                val file = File(context.cacheDir, "compressed-ass.mkv")
                InstrumentationRegistry.getInstrumentation().context.assets.open("compressed-ass.mkv").use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                player.setMediaData(SystemFileMediaData(Path(file.path)), playWhenReady = true, startPositionMillis = 1500)
            }
            ProvideCompositionLocalsForPreview {
                CompositionLocalProvider(LocalIsPreviewing provides false) {
                    Column {
                        Text("Embedded ASS subtitles: zlib and plain text")
                        VideoPlayer(player, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        Button(onClick = {
                            player.seekTo(6000)
                            player.play()
                        }) { Text("Seek to traditional Chinese") }
                        Button(onClick = {
                            val exo = player.exoMediampPlayer.impl
                            val group = exo.currentTracks.groups.last { it.type == C.TRACK_TYPE_TEXT }
                            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0)).build()
                            player.play()
                        }) { Text("Select plain ASS track") }
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 20_000) {
            player.assHandler.track?.getEvents()?.size == 2 && player.currentPositionMillis.value >= 2000
        }
        runOnIdle {
            player.pause()
            assertEquals("你好，世界！简体中文字幕", player.assHandler.track!!.getEvents()!!.first().text)
        }
        holdForVisualVerification()
        onNodeWithText("Seek to traditional Chinese").performClick()
        waitUntil(timeoutMillis = 10_000) { player.currentPositionMillis.value >= 6500 }
        runOnIdle { player.pause() }
        holdForVisualVerification()
        onNodeWithText("Select plain ASS track").performClick()
        waitUntil(timeoutMillis = 10_000) {
            player.assHandler.track?.getEvents()?.firstOrNull()?.text == "Plain ASS subtitles, punctuation intact!" &&
                player.currentPositionMillis.value >= 7000
        }
        runOnIdle { player.pause() }
        holdForVisualVerification()
    }

    private fun AniComposeUiTest.holdForVisualVerification() {
        // Optional pause for externally captured emulator screenshots; no evidence is stored by the test.
        val millis = InstrumentationRegistry.getArguments().getString("subtitleVerificationHoldMillis")?.toLong() ?: 0
        val deadline = System.nanoTime() + millis.coerceIn(0, 10_000) * 1_000_000
        while (System.nanoTime() < deadline) {
            waitForIdle()
            Thread.sleep(100)
        }
    }
}
