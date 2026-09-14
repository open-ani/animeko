/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.leanback.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvLandscapeCardDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvPlaceholderBlock

/** Keep text line boxes proportional to font scaling, without placing fake text in semantics. */
@Composable
internal fun TvDetailsTextPlaceholder(
    modifier: Modifier = Modifier,
    lines: Int = 2,
    fontSize: TextUnit = 16.sp,
    lineHeight: TextUnit = 24.sp,
    lastLineFraction: Float = .7f,
) {
    val density = LocalDensity.current
    Column(modifier) {
        repeat(lines) { index ->
            Box(Modifier.fillMaxWidth().height(with(density) { lineHeight.toDp() }), contentAlignment = Alignment.CenterStart) {
                TvPlaceholderBlock(Modifier.fillMaxWidth(if (index == lines - 1) lastLineFraction else 1f)
                    .height(with(density) { fontSize.toDp() }))
            }
        }
    }
}

/** Same image inset and two caption lines as TvDetailsPersonCard. */
@Composable
internal fun TvDetailsPersonPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier.width(TvSubjectDetailsDefaults.PersonCardWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        TvPlaceholderBlock(Modifier.size(TvSubjectDetailsDefaults.PersonCardWidth).padding(TvFocusDefaults.RingInset)
            .testTag("tv-details-person-placeholder-image"), CircleShape)
        TvDetailsTextPlaceholder(Modifier.fillMaxWidth(.8f).padding(top = 10.dp), lines = 1,
            fontSize = 13.sp, lineHeight = 20.sp, lastLineFraction = 1f)
        TvDetailsTextPlaceholder(Modifier.fillMaxWidth(.6f).padding(top = 6.dp), lines = 1,
            fontSize = 12.sp, lineHeight = 20.sp, lastLineFraction = 1f)
    }
}

@Composable
internal fun TvDetailsLandscapePlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.width(TvSubjectDetailsDefaults.RelatedCardWidth).padding(TvFocusDefaults.RingInset)) {
        TvDetailsMediaPlaceholder(Modifier.fillMaxWidth().aspectRatio(TvLandscapeCardDefaults.AspectRatio))
    }
}

@Composable
internal fun TvDetailsEpisodePlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.width(TvSubjectDetailsDefaults.EpisodeCardWidth)
        .aspectRatio(TvLandscapeCardDefaults.AspectRatio).padding(TvFocusDefaults.RingInset)) {
        TvDetailsMediaPlaceholder(Modifier.fillMaxSize())
    }
}

/** Match the real action's minimum interaction area as well as its visible pill. */
@Composable
internal fun TvDetailsActionPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        TvPlaceholderBlock(Modifier.fillMaxWidth().height(TvSubjectDetailsDefaults.ActionHeight)
            .testTag("tv-details-action-placeholder-pill"), TvSubjectDetailsDefaults.ActionShape)
    }
}

@Composable
private fun TvDetailsMediaPlaceholder(modifier: Modifier) {
    Box(modifier.clip(TvLandscapeCardDefaults.ImageShape).background(TvLandscapeCardDefaults.PlaceholderColor)
        .testTag("tv-details-media-placeholder-image")) {
        TvPlaceholderBlock(Modifier.fillMaxSize(), TvLandscapeCardDefaults.ImageShape)
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TvPlaceholderBlock(Modifier.fillMaxWidth(.4f).height(10.dp))
            TvPlaceholderBlock(Modifier.fillMaxWidth(.8f).height(14.dp))
        }
    }
}
