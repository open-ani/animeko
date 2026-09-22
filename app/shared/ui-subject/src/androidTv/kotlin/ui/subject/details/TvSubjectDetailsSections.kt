/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.tv.ui.subject.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.subject_details_no_episodes
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.rememberSubjectStatusStrings
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.tv.ui.foundation.widgets.TvOptionsRow
import me.him188.ani.tv.ui.subject.TvSubjectDetailsContentState
import me.him188.ani.tv.ui.subject.components.TvDetailsDescriptionCard
import me.him188.ani.tv.ui.subject.components.TvDetailsHeroLayout
import me.him188.ani.tv.ui.subject.components.TvDetailsTextPlaceholder
import me.him188.ani.tv.ui.subject.components.TvSubjectDetailsDefaults
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
    TvDetailsHero(
        details.info, details.airing, height, modifier = modifier,
        onComments = onComments.takeIf { interactive }, scoreModifier = actionModifier("bgm-rating"),
        introduction = { cardModifier ->
            TvDetailsDescriptionCard(
                details.info.summary, onSummary,
                actionModifier("summary").then(cardModifier),
                interactive = interactive,
            )
        },
        actions = { compact ->
            TvOptionsRow {
                TvDetailsAction(
                    playLabel, Icons.Rounded.PlayArrow, onPlay, actionModifier("play"),
                    available = details.playTargetId != null, blurBackground = true, glowOnFocus = true,
                    loading = details.episodesLoading && details.episodes.isEmpty(),
                )
                if (interactive) {
                    TvDetailsCollectionAction(
                        details.collectionType, onCollection, actionModifier("collection"), compact,
                        boundsModifier = actionBoundsModifier("collection"),
                        loading = details.collectionLoading,
                    )
                    TvDetailsRatingAction(
                        details.selfRating.score, onRating, actionModifier("rating"), compact,
                        boundsModifier = actionBoundsModifier("rating"),
                        available = details.collectionType != UnifiedCollectionType.NOT_COLLECTED,
                        loading = details.ratingLoading,
                    )
                }
            }
        },
    )
}

/** Subject hero with identity, introduction and action slots; title and metadata components are shared with exploration. */
@Composable
fun TvDetailsHero(
    info: SubjectInfo,
    airing: AiringLabelState?,
    height: Dp,
    actions: @Composable (compact: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    introduction: (@Composable (Modifier) -> Unit)? = null,
    supportingContent: (@Composable (SubjectInfo, Modifier) -> Unit)? = null,
    onComments: (() -> Unit)? = null,
    scoreModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier.testTag("tv-details-title"),
    contentPadding: PaddingValues = PaddingValues(
        start = TvSubjectDetailsDefaults.HorizontalPadding, end = TvSubjectDetailsDefaults.HorizontalPadding,
        top = 36.dp, bottom = TvSubjectDetailsDefaults.OverviewBottomPadding,
    ),
    actionSpacing: Dp = 32.dp,
    titleSize: TextUnit = TvSubjectDetailsDefaults.TitleSize,
    titleLineHeight: TextUnit = TvSubjectDetailsDefaults.TitleLineHeight,
    titleMinLines: Int = 1,
    identityTransition: ContentTransform? = null,
    loading: Boolean = false,
) {
    TvDetailsHeroLayout(
        height, introduction = introduction, actions = actions, modifier = modifier,
        contentPadding = contentPadding, actionSpacing = actionSpacing,
        identity = { compact ->
            val identity: @Composable (SubjectInfo) -> Unit = { subject ->
                Column {
                    val textModifier = titleModifier.fillMaxWidth(if (compact) 1f else .62f)
                    val fontSize = if (compact) minOf(titleSize.value, 32f).sp else titleSize
                    val lineHeight = if (compact) minOf(titleLineHeight.value, 42f).sp else titleLineHeight
                    // Full lines need room for CJK fallback metrics as well as the primary font.
                    val reservedLineHeight = if (titleMinLines > 1) {
                        maxOf(lineHeight.value, fontSize.value * 1.5f).sp
                    } else lineHeight
                    if (loading) {
                        TvDetailsTextPlaceholder(textModifier, fontSize = fontSize, lineHeight = reservedLineHeight)
                        TvDetailsTextPlaceholder(
                            Modifier.fillMaxWidth(if (compact) .8f else .4f),
                            lines = 1, fontSize = 16.sp, lineHeight = 30.sp,
                        )
                    } else {
                        TvDetailsTitle(
                            subject.displayName, textModifier, fontSize, reservedLineHeight, titleMinLines,
                        )
                        TvDetailsMetadata(subject, airing, onComments ?: {}, scoreModifier, onComments != null)
                    }
                    supportingContent?.invoke(subject, Modifier.fillMaxWidth(if (compact) 1f else .62f))
                }
            }
            // Actions remain outside the changing identity, retaining one stable focus target.
            if (identityTransition == null) identity(info)
            else AnimatedContent(
                info, transitionSpec = { identityTransition },
                contentKey = { it.subjectId }, label = "details-hero-identity",
            ) { identity(it) }
        },
    )
}

@Composable
fun TvDetailsTitle(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TvSubjectDetailsDefaults.TitleSize,
    lineHeight: TextUnit = TvSubjectDetailsDefaults.TitleLineHeight,
    minLines: Int = 1,
) {
    val style = MaterialTheme.typography.displaySmall.copy(
        fontSize = fontSize, lineHeight = lineHeight, fontWeight = FontWeight.Normal,
    )
    Text(
        text, modifier, color = TvSubjectDetailsDefaults.Content,
        style = if (minLines > 1) style.copy(
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ) else style,
        minLines = minLines, maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun TvDetailsMetadata(
    info: SubjectInfo,
    airing: AiringLabelState?,
    onComments: () -> Unit = {},
    scoreModifier: Modifier = Modifier,
    interactive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    FlowRow(
        modifier.testTag("tv-details-metadata"),
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
            Row(
                Modifier.padding(start = 6.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Rounded.Star, null, Modifier.size(19.dp), tint = TvSubjectDetailsDefaults.SecondaryContent)
                Text(
                    info.ratingInfo.score.ifBlank { "–" },
                    color = TvSubjectDetailsDefaults.SecondaryContent,
                    style = style,
                )
            }
        }
        detailsMainTag(info)?.let {
            Text(
                it, color = TvSubjectDetailsDefaults.SecondaryContent, style = style, maxLines = 1,
                modifier = Modifier.widthIn(max = 180.dp), overflow = TextOverflow.Ellipsis,
            )
        }
        if (info.airDate.isValid) Text(
            detailsAirMonth(info),
            color = TvSubjectDetailsDefaults.SecondaryContent,
            style = style,
        )
        if (airing != null) TvDetailsAiringInfo(airing)
    }
}

@Composable
fun TvDetailsAiringInfo(airing: AiringLabelState, modifier: Modifier = Modifier) {
    val strings = rememberSubjectStatusStrings()
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
    val progress = airing.progressText(strings)
    val total = airing.totalEpisodesText(strings)
    Row(modifier) {
        Text(
            buildAnnotatedString {
                if (progress != null) withStyle(SpanStyle(
                    color = if (airing.highlightProgress) MaterialTheme.colorScheme.primary else TvSubjectDetailsDefaults.SecondaryContent,
                )) { append(progress) }
                if (total != null) {
                    if (progress != null) append(" · ")
                    append(total)
                }
            },
            style = style, color = TvSubjectDetailsDefaults.SecondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun detailsAirMonth(info: SubjectInfo): String =
    info.airDate.year.toString().padStart(4, '0') + "-" + info.airDate.month.toString().padStart(2, '0')

/** Bangumi usually puts the broadcast season before the main genre tag. */
internal fun detailsMainTag(info: SubjectInfo): String? =
    info.tags.getOrNull(1)?.name ?: info.tags.firstOrNull()?.name?.takeUnless { name ->
        name.startsWith(info.airDate.year.toString()) || name.matches(Regex("\\d{4}.*"))
    }
