/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_episodes
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionsRow
import me.him188.ani.leanback.ui.subject.components.TvDetailsActionPlaceholder
import me.him188.ani.leanback.ui.subject.components.TvDetailsBringIntoViewSpec
import me.him188.ani.leanback.ui.subject.components.TvDetailsBrowseRowLayout
import me.him188.ani.leanback.ui.subject.components.TvDetailsDescriptionCard
import me.him188.ani.leanback.ui.subject.components.TvDetailsEpisodePlaceholder
import me.him188.ani.leanback.ui.subject.components.TvDetailsHeroLayout
import me.him188.ani.leanback.ui.subject.components.TvDetailsScrollAnchors
import me.him188.ani.leanback.ui.subject.components.TvDetailsTextPlaceholder
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsPageLayout
import me.him188.ani.leanback.ui.subject.components.tvDetailsScrollSection
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvSubjectDetailsPlaceholder(focus: TvFocusScope, entryModifier: Modifier, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val anchors = remember { TvDetailsScrollAnchors() }
    val defaultSpec = LocalBringIntoViewSpec.current
    val scrollSpec = remember(scroll, defaultSpec) { TvDetailsBringIntoViewSpec(anchors, { scroll.value }, defaultSpec) { false } }
    TvSubjectDetailsPageLayout(focus, scroll, scrollSpec, Modifier,
        anchors, backdrop = {}, modifier = modifier.testTag("tv-details-placeholder")) { height ->
        TvDetailsHeroLayout(height,
            identity = { compact ->
                TvDetailsTextPlaceholder(Modifier.fillMaxWidth(if (compact) 1f else .62f).testTag("tv-details-title-placeholder"),
                    fontSize = if (compact) 32.sp else TvSubjectDetailsDefaults.TitleSize,
                    lineHeight = if (compact) 42.sp else TvSubjectDetailsDefaults.TitleLineHeight)
                TvDetailsTextPlaceholder(Modifier.fillMaxWidth(if (compact) .8f else .4f), lines = 1,
                    fontSize = 16.sp, lineHeight = 30.sp)
            },
            introduction = { TvDetailsDescriptionCard("", {}, it.testTag("tv-details-summary-placeholder"), interactive = false, loading = true) },
            actions = { compact ->
                TvOptionsRow {
                    TvDetailsActionPlaceholder(entryModifier.width(144.dp))
                    repeat(2) {
                        TvDetailsActionPlaceholder(Modifier.width(if (compact) 66.dp else 116.dp))
                    }
                }
            }, modifier = Modifier.tvDetailsScrollSection(anchors, "hero", 0f) { scroll.value })
        TvDetailsBrowseRowLayout(stringResource(Lang.subject_details_episodes), rememberLazyListState(), "episodes", focused = false) {
            item("loading") {
                Row(Modifier.progressSemantics(), horizontalArrangement = Arrangement.spacedBy(TvSubjectDetailsDefaults.RowSpacing)) {
                    repeat(3) { TvDetailsEpisodePlaceholder() }
                }
            }
        }
    }
}
