/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.bangumi_merge_no_rating
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.rating_edit_title
import me.him188.ani.app.ui.lang.tv_rating_controls_hint
import me.him188.ani.app.ui.rating.FiveRatingStars
import me.him188.ani.app.ui.rating.rememberRatingScoreLabels
import me.him188.ani.app.ui.rating.renderScoreClass
import me.him188.ani.app.ui.search.renderLoadErrorMessage
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusLink
import me.him188.ani.leanback.ui.foundation.layout.TvAnchoredOptionLayout
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionPanel
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionStepper
import me.him188.ani.leanback.ui.subject.components.TvDetailsFullscreenOverlay
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsKey
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Unrated subjects start at the rounded average; an unavailable average remains unrated. */
internal fun initialTvRatingScore(selfScore: Int, averageScore: String): Int =
    if (selfScore > 0) selfScore.coerceAtMost(10)
    else averageScore.toFloatOrNull()?.takeIf { it.isFinite() }?.roundToInt()?.coerceIn(0, 10) ?: 0

/** Local score draft in an anchored panel; only confirmation persists it. */
@Composable
internal fun TvSubjectRatingPanel(
    initialScore: Int,
    backdrop: String,
    anchorBounds: Rect,
    anchorButton: @Composable (Modifier) -> Unit,
    busy: Boolean,
    error: LoadError?,
    onClose: () -> Unit,
    onSubmit: (Int) -> Unit,
) {
    var score by rememberSaveable { mutableIntStateOf(initialScore.coerceIn(0, 10)) }
    val labels = rememberRatingScoreLabels()
    val description = if (score == 0) stringResource(Lang.bangumi_merge_no_rating)
    else "$score · ${renderScoreClass(score.toFloat(), labels)}"
    TvDetailsFullscreenOverlay(backdrop, onClose, "rating-control") { focus ->
        TvAnchoredOptionLayout(
            anchorBounds,
            panelWidth = TvSubjectDetailsDefaults.RatingPanelWidth,
            panelMaxHeight = TvSubjectDetailsDefaults.RatingPanelMaxHeight,
            panelAnchorFraction = TvSubjectDetailsDefaults.RatingPanelAnchorFraction,
            anchor = {
                anchorButton(
                    Modifier.tvFocusAnchor(focus, TvDetailsKey("overlay-trigger"))
                        .tvFocusLink(focus, up = TvDetailsKey("rating-control"))
                        .testTag("tv-details-overlay-trigger"),
                )
            },
            panel = {
                TvOptionPanel(Modifier.testTag("tv-details-operation-panel")) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        FiveRatingStars(score, color = TvSubjectDetailsDefaults.Content, starSize = 48.dp,
                            modifier = Modifier.testTag("tv-rating-stars"))
                        TvOptionStepper(
                            label = stringResource(Lang.rating_edit_title),
                            valueDescription = description,
                            canDecrease = score > 0,
                            canIncrease = score < 10,
                            onStep = { score = (score + it).coerceIn(0, 10) },
                            inputEnabled = !busy,
                            onClick = { onSubmit(score) },
                            modifier = Modifier.tvFocusAnchor(focus, TvDetailsKey("rating-control"))
                                .tvFocusLink(focus, down = TvDetailsKey("overlay-trigger"))
                                .testTag("tv-details-panel-rating-control"),
                        ) {
                            Text(description, Modifier.testTag("tv-rating-description"),
                                color = LocalContentColor.current, textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp))
                        }
                        if (busy) Row(
                            Modifier.testTag("tv-rating-busy"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp),
                                color = TvSubjectDetailsDefaults.Content, strokeWidth = 2.dp)
                            Text(stringResource(Lang.foundation_loading), color = TvSubjectDetailsDefaults.SecondaryContent)
                        }
                        error?.let {
                            Text(renderLoadErrorMessage(it), color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("tv-rating-error"))
                        }
                        Text(stringResource(Lang.tv_rating_controls_hint),
                            modifier = Modifier.fillMaxWidth().testTag("tv-rating-controls-hint"),
                            color = TvSubjectDetailsDefaults.SecondaryContent, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
        )
    }
}
