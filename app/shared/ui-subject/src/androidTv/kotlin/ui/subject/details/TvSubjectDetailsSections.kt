/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.subject_details_no_episodes
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.rememberSubjectStatusStrings
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionsRow
import me.him188.ani.leanback.ui.subject.TvSubjectDetailsContentState
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.components.TvDetailsDescriptionCard
import me.him188.ani.leanback.ui.subject.components.TvDetailsHeroLayout
import org.jetbrains.compose.resources.stringResource

/** Short titles preserve the card/action positions; small viewports can grow vertically. */
@Composable
internal fun TvDetailsHeroSection(
    details: TvSubjectDetailsContentState,
    height: Dp,
    interactive: Boolean,
    onPlay: () -> Unit,
    onSummary: () -> Unit,
    onComments: () -> Unit,
    onCollection: () -> Unit,
    onRating: () -> Unit,
    actionModifier: (String) -> Modifier,
    actionBoundsModifier: (String) -> Modifier,
    modifier: Modifier = Modifier,
) {
    val strings = rememberSubjectStatusStrings()
    val playLabel = details.progress?.buttonText(strings) ?: stringResource(
        if (details.episodesLoading) Lang.foundation_loading else Lang.subject_details_no_episodes,
    )
    TvDetailsHeroLayout(height, modifier = modifier,
        identity = { compact ->
            Text(
                details.info.displayName,
                Modifier.fillMaxWidth(if (compact) 1f else .62f).testTag("tv-details-title"),
                color = TvSubjectDetailsDefaults.Content,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = if (compact) 32.sp else TvSubjectDetailsDefaults.TitleSize,
                    lineHeight = if (compact) 42.sp else TvSubjectDetailsDefaults.TitleLineHeight,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TvDetailsMetadata(details.info, details.airing, onComments, actionModifier("bgm-rating"), interactive)
        },
        introduction = { cardModifier ->
            TvDetailsDescriptionCard(
                details.info.summary, onSummary,
                actionModifier("summary").then(cardModifier),
                interactive = interactive,
            )
        },
        actions = { compact ->
            TvOptionsRow {
                TvDetailsAction(playLabel, Icons.Rounded.PlayArrow, onPlay, actionModifier("play"),
                    available = details.playTargetId != null, blurBackground = true, glowOnFocus = true,
                    loading = details.episodesLoading && details.episodes.isEmpty())
                if (interactive) {
                    TvDetailsCollectionAction(details.collectionType, onCollection, actionModifier("collection"), compact,
                        boundsModifier = actionBoundsModifier("collection"))
                    TvDetailsRatingAction(details.selfRating.score, onRating, actionModifier("rating"), compact,
                        boundsModifier = actionBoundsModifier("rating"),
                        available = details.collectionType != UnifiedCollectionType.NOT_COLLECTED)
                }
            }
        },
    )
}

@Composable
private fun TvDetailsMetadata(
    info: SubjectInfo,
    airing: AiringLabelState?,
    onComments: () -> Unit,
    scoreModifier: Modifier,
    interactive: Boolean,
) {
    val strings = rememberSubjectStatusStrings()
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    FlowRow(
        Modifier.testTag("tv-details-metadata"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = scoreModifier.clickable(
                interactionSource = interaction, indication = null, enabled = interactive,
                role = Role.Button, onClick = onComments,
            ),
            shape = TvSubjectDetailsDefaults.ActionShape,
            color = if (focused) Color.White.copy(alpha = .12f) else Color.Transparent,
            border = if (focused) BorderStroke(2.dp, Color.White.copy(alpha = .65f)) else null,
        ) {
            Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Star, null, Modifier.size(19.dp), tint = TvSubjectDetailsDefaults.SecondaryContent)
                Text(info.ratingInfo.score.ifBlank { "–" }, color = TvSubjectDetailsDefaults.SecondaryContent, style = style)
            }
        }
        detailsMainTag(info)?.let {
            Text(it, color = TvSubjectDetailsDefaults.SecondaryContent, style = style, maxLines = 1,
                modifier = Modifier.widthIn(max = 180.dp), overflow = TextOverflow.Ellipsis)
        }
        if (info.airDate.isValid) Text(detailsAirMonth(info), color = TvSubjectDetailsDefaults.SecondaryContent, style = style)
        val progress = airing?.progressText(strings)
        val total = airing?.totalEpisodesText(strings)
        Row {
            if (progress != null) Text(progress, style = style,
                color = if (airing.highlightProgress) MaterialTheme.colorScheme.primary else TvSubjectDetailsDefaults.SecondaryContent)
            if (total != null) Text((if (progress != null) " · " else "") + total,
                style = style, color = TvSubjectDetailsDefaults.SecondaryContent)
        }
    }
}

internal fun detailsAirMonth(info: SubjectInfo): String =
    info.airDate.year.toString().padStart(4, '0') + "-" + info.airDate.month.toString().padStart(2, '0')

/** Bangumi usually puts the broadcast season before the main genre tag. */
internal fun detailsMainTag(info: SubjectInfo): String? =
    info.tags.getOrNull(1)?.name ?: info.tags.firstOrNull()?.name?.takeUnless { name ->
        name.startsWith(info.airDate.year.toString()) || name.matches(Regex("\\d{4}.*"))
    }
