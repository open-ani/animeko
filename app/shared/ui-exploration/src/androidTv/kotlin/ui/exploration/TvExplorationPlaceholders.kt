/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.exploration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_continue_watching
import me.him188.ani.app.ui.lang.exploration_for_you
import me.him188.ani.tv.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvLandscapeCardDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvPlaceholderBlock
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvExplorationHeroIdentityPlaceholder() {
    Column(
        Modifier.fillMaxWidth().testTag("tv-exploration-hero-loading").progressSemantics(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvPlaceholderBlock(Modifier.fillMaxWidth(.8f).height(36.dp))
        TvPlaceholderBlock(Modifier.fillMaxWidth(.55f).height(36.dp))
        TvPlaceholderBlock(Modifier.fillMaxWidth(.6f).height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TvPlaceholderBlock(Modifier.fillMaxWidth(.86f).height(12.dp))
            TvPlaceholderBlock(Modifier.fillMaxWidth(.66f).height(12.dp))
        }
    }
}

/** Each feed keeps its own heading and card geometry while its first page loads. */
@Composable
internal fun TvExplorationRowPlaceholder(continued: Boolean, columns: Int, modifier: Modifier = Modifier) {
    val section = if (continued) "followed" else "recommendations"
    Column(modifier.fillMaxWidth().testTag("tv-exploration-$section-loading").progressSemantics()) {
        Box(
            Modifier.height(if (continued) 21.dp else 24.dp).padding(start = TvExplorationDefaults.StartPadding),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                stringResource(if (continued) Lang.exploration_continue_watching else Lang.exploration_for_you),
                color = TvExplorationDefaults.Content, fontSize = 16.sp, lineHeight = 20.sp,
            )
        }
        if (continued) {
            LazyRow(
                contentPadding = PaddingValues(
                    start = TvExplorationDefaults.StartPadding, end = TvExplorationDefaults.EndPadding,
                    top = 12.dp, bottom = 12.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(TvExplorationDefaults.CardSpacing),
            ) {
                items(6) { index ->
                    TvPlaceholderBlock(
                        Modifier.width(TvExplorationDefaults.ImmersiveCardWidth)
                            .height(TvExplorationDefaults.ImmersiveCardHeight).padding(TvFocusDefaults.RingInset)
                            .testTag("tv-exploration-followed-placeholder-$index"),
                        RoundedCornerShape(12.dp),
                    )
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(
                    start = TvExplorationDefaults.StartPadding, end = TvExplorationDefaults.EndPadding,
                    top = 12.dp, bottom = 4.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(TvLandscapeCardDefaults.Spacing),
            ) {
                repeat(columns) { index ->
                    TvPlaceholderBlock(
                        Modifier.weight(1f).aspectRatio(TvLandscapeCardDefaults.AspectRatio)
                            .padding(TvFocusDefaults.RingInset).testTag("tv-exploration-recommendation-placeholder-$index"),
                        TvLandscapeCardDefaults.ImageShape,
                    )
                }
            }
        }
    }
}
