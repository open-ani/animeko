/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import me.him188.ani.app.domain.media.player.TorrentMediaCacheProgressProvider
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.app.torrent.api.pieces.forEach
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressSlider
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import kotlin.time.Duration.Companion.seconds

// Try interactive preview to see cache progress change
@Preview
@Composable
fun PreviewMediaProgressSliderInteractive() = ProvideCompositionLocalsForPreview {
    var currentPositionMillis by remember { mutableLongStateOf(2000) }
    val totalDurationMillis by remember { mutableLongStateOf(30_000) }
    val pieces = remember {
        PieceList.create(16, 0) {
            1_000
        }
    }
    var isFinished by remember {
        mutableStateOf(false)
    }

    val cacheProgressFlow = remember {
        TorrentMediaCacheProgressProvider(
            pieces,
        ).flow
    }
    LaunchedEffect(true) {
        pieces.forEach { piece ->
            delay(2.seconds)
            piece.state = PieceState.DOWNLOADING
            delay(2.seconds)
            piece.state = PieceState.FINISHED
        }
        isFinished = true
    }
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val cacheProgressInfo by cacheProgressFlow.collectAsStateWithLifecycle(null)
        MediaProgressSlider(
            remember {
                PlayerProgressSliderState(
                    currentPositionMillis = { currentPositionMillis },
                    totalDurationMillis = { totalDurationMillis },
                    chapters = { persistentListOf() },
                    onPreview = {},
                    onPreviewFinished = { currentPositionMillis = it },
                )
            },
            { cacheProgressInfo },
        )
    }
}

private fun buildPiecesWithStep(
    state: PieceState
): PieceList = PieceList.create(16, 0) { 1_000 }.apply {
    // simulate non-consecutive cache
    for (i in initialPieceIndex until endPieceIndex step 2) {
        this.getByPieceIndex(i).state = state
    }
}

@Composable
fun PreviewMediaProgressSliderNonConsecutiveCacheImpl(
    pieces: PieceList,
) = ProvideFoundationCompositionLocalsForPreview {
    val cacheProgress = remember {
        TorrentMediaCacheProgressProvider(
            pieces,
        ).run {
            runPass()
            createInfo()
        }
    }
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        MediaProgressSlider(
            remember {
                PlayerProgressSliderState(
                    currentPositionMillis = { 2000 },
                    totalDurationMillis = { 30_000 },
                    chapters = { persistentListOf() },
                    onPreview = {},
                    onPreviewFinished = { },
                )
            },
            { cacheProgress },
        )
    }
}


@Preview
@Composable
fun PreviewMediaProgressSliderDownloading() =
    PreviewMediaProgressSliderNonConsecutiveCacheImpl(buildPiecesWithStep(PieceState.DOWNLOADING))

@Preview
@Composable
fun PreviewMediaProgressSliderDone() =
    PreviewMediaProgressSliderNonConsecutiveCacheImpl(buildPiecesWithStep(PieceState.FINISHED))

@Preview
@Composable
fun PreviewMediaProgressSliderNotAvailable() =
    PreviewMediaProgressSliderNonConsecutiveCacheImpl(buildPiecesWithStep(PieceState.NOT_AVAILABLE))

@Preview
@Composable
fun PreviewMediaProgressSlider() =
    PreviewMediaProgressSliderNonConsecutiveCacheImpl(
        PieceList.create(16, 0) { 1_000 }.apply {
            // simulate non-consecutive cache
            for (i in 0..3) {
                this.getByPieceIndex(i).state = PieceState.FINISHED
            }
            for (i in 4..5) {
                this.getByPieceIndex(i).state = PieceState.DOWNLOADING
            }
            for (i in 7..7) {
                this.getByPieceIndex(i).state = PieceState.DOWNLOADING
            }
            for (i in 8..9) {
                this.getByPieceIndex(i).state = PieceState.FINISHED
            }
            for (i in 10..13) {
                this.getByPieceIndex(i).state = PieceState.DOWNLOADING
            }
        },
    )
