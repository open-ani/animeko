/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.exploration

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectAiringKind
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.tv.ui.foundation.theme.AniTvTheme
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
class TvWatchingProgressThemeUiTest {
    @Test
    fun changingTheApplicationSeedRecolorsTheExistingProgressBarWithoutChangingProgress() = runAniComposeUiTest {
        var seed by mutableStateOf(Color(0xFF6750A4))
        val base = TestSubjectCollections.first()
        val collection = base.copy(
            episodes = (1..12).map { number ->
                EpisodeCollectionInfo(
                    EpisodeInfo(episodeId = number, type = EpisodeType.MainStory, name = "Episode $number", sort = EpisodeSort(number)),
                    if (number <= 4) UnifiedCollectionType.DONE else UnifiedCollectionType.NOT_COLLECTED,
                )
            },
            airingInfo = base.airingInfo.copy(kind = SubjectAiringKind.ON_AIR, mainEpisodeCount = 12, latestSort = EpisodeSort(8)),
        )
        setContent {
            AniTvTheme(seed) {
                Box(Modifier.fillMaxSize().padding(40.dp)) {
                    HomeWatchingProgress("Watching", collection, null, Modifier.width(500.dp), animate = false)
                }
            }
        }
        val node = onNodeWithTag("tv-exploration-watching-watched")
        val originalBounds = node.fetchSemanticsNode().boundsInRoot
        val purple = node.captureToImage().asAndroidBitmap()
        runOnIdle { seed = Color(0xFF238C45) }
        waitForIdle()
        val green = node.captureToImage().asAndroidBitmap()
        assertEquals(originalBounds, node.fetchSemanticsNode().boundsInRoot)
        var changed = 0
        for (y in 0 until green.height) for (x in 0 until green.width) {
            if (purple.getPixel(x, y) != green.getPixel(x, y)) changed++
        }
        assertTrue(changed > green.width * green.height * .8f, "The gradient must use the current theme")
        listOf("purple" to purple, "green" to green).forEach { (name, bitmap) ->
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "watching-progress-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
