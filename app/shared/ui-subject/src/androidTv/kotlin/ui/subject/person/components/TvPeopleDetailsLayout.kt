/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.subject.components.TvDetailsScrollAnchors
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsPageLayout

internal object TvPeopleDefaults {
    const val HeroHeightFraction = .74f
    val PortraitWidth = 210.dp
    val PortraitCorner = 22.dp
    val HeroGap = 32.dp
    val CardGap = 16.dp
}

/** All three profiles use these slots. Relationship types and navigation belong to the caller. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvPeopleDetailsLayout(
    focus: TvFocusScope,
    scroll: ScrollState,
    bringIntoViewSpec: BringIntoViewSpec,
    scrollAnchors: TvDetailsScrollAnchors,
    scrollContentModifier: Modifier,
    heroModifier: Modifier,
    backdrop: @Composable BoxScope.() -> Unit,
    identity: @Composable () -> Unit,
    portrait: @Composable (Modifier) -> Unit,
    introduction: @Composable (Modifier) -> Unit,
    discussion: @Composable (Modifier) -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    sections: @Composable ColumnScope.() -> Unit,
) {
    TvSubjectDetailsPageLayout(
        focus, scroll, bringIntoViewSpec, scrollContentModifier, scrollAnchors, backdrop, modifier,
        overviewHeightFraction = TvPeopleDefaults.HeroHeightFraction,
    ) { heroHeight ->
        BoxWithConstraints(heroModifier.fillMaxWidth().heightIn(min = heroHeight)
            .padding(start = TvSubjectDetailsDefaults.HorizontalPadding, end = TvSubjectDetailsDefaults.HorizontalPadding,
                top = 32.dp, bottom = 24.dp)
            .testTag("tv-people-hero")) {
            val imageWidth = (maxWidth * .25f).coerceAtMost(TvPeopleDefaults.PortraitWidth)
            val cardsSideBySide = maxWidth - imageWidth - TvPeopleDefaults.HeroGap >= 450.dp
            Row(horizontalArrangement = Arrangement.spacedBy(TvPeopleDefaults.HeroGap)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    identity()
                    if (cardsSideBySide) {
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TvPeopleDefaults.CardGap)) {
                            introduction(Modifier.weight(1f).fillMaxHeight())
                            discussion(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        introduction(Modifier.fillMaxWidth())
                        discussion(Modifier.fillMaxWidth())
                    }
                    actions()
                }
                portrait(Modifier.width(imageWidth))
            }
        }
        sections()
    }
}
