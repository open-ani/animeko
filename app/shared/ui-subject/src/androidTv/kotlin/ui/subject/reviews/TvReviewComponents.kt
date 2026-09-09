/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.reviews

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_preview_image
import me.him188.ani.app.ui.lang.comment_preview_quote
import me.him188.ani.app.ui.lang.comment_review_hidden
import me.him188.ani.app.ui.lang.foundation_anonymous
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.subject_details_overview
import me.him188.ani.app.ui.lang.subject_details_rating_summary
import me.him188.ani.app.ui.lang.subject_details_ratings_count
import me.him188.ani.app.ui.rating.FiveRatingStars
import me.him188.ani.app.ui.richtext.RichText
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.app.ui.subject.details.components.RatingHistogram
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.details.formatCount
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

internal object TvReviewDefaults {
    val VerticalPadding = 36.dp
    val ColumnGap = 36.dp
    val OverviewWidth = 284.dp
    val CardGap = 12.dp
    val CardShape = RoundedCornerShape(20.dp)
    val Accent = Color(0xFF80CEFA)
}

/** The coordinator scrolls to stable IDs before focusing. Child overlay focus must not scroll this list. */
@OptIn(ExperimentalFoundationApi::class)
internal object TvReviewBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@Composable
internal fun TvReviewOverview(rating: RatingInfo, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.testTag("tv-review-overview")) {
        val compact = maxWidth < 230.dp
        val chartHeight = if (compact) (maxHeight * .18f).coerceIn(24.dp, 56.dp)
        else (maxHeight * .28f).coerceIn(36.dp, 88.dp)
        val score = rating.scoreFloat.takeIf { it.isFinite() && it > 0f }
        Column(Modifier.fillMaxSize()) {
            Text(stringResource(Lang.subject_details_overview),
                color = TvSubjectDetailsDefaults.Content,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
            Spacer(Modifier.height(14.dp))
            if (compact) {
                ReviewScore(score?.let { rating.score } ?: "—", compact = true)
                ReviewRatingMeta(rating, score, compact = true)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ReviewScore(score?.let { rating.score } ?: "—", compact = false)
                    ReviewRatingMeta(rating, score, compact = false, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.weight(1f))
            RatingHistogram(rating, Modifier.fillMaxWidth().padding(top = 16.dp).testTag("tv-review-histogram"),
                barColor = TvReviewDefaults.Accent, trackColor = TvReviewDefaults.Accent.copy(alpha = .2f),
                labelColor = TvSubjectDetailsDefaults.SecondaryContent, barHeight = chartHeight,
                barSpacing = if (compact) 1.dp else 4.dp,
                labelStyle = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 11.sp else 14.sp, letterSpacing = 0.sp,
                ))
        }
    }
}

@Composable
private fun ReviewRatingMeta(rating: RatingInfo, score: Float?, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.testTag("tv-review-stars")) {
            FiveRatingStars(score?.roundToInt()?.coerceIn(0, 10) ?: 0, if (compact) 20.dp else 22.dp, TvReviewDefaults.Accent)
        }
        Text(
            if (rating.rank > 0) stringResource(Lang.subject_details_rating_summary, formatCount(rating.rank), formatCount(rating.total))
            else stringResource(Lang.subject_details_ratings_count, formatCount(rating.total)),
            color = TvSubjectDetailsDefaults.SecondaryContent,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            modifier = Modifier.testTag("tv-review-votes"),
        )
    }
}

@Composable
private fun ReviewScore(score: String, compact: Boolean) {
    Text(score, color = TvSubjectDetailsDefaults.Content,
        style = MaterialTheme.typography.displayLarge.copy(fontSize = if (compact) 48.sp else 60.sp, fontWeight = FontWeight.SemiBold),
        maxLines = 1, modifier = Modifier.testTag("tv-review-average"))
}

@Composable
internal fun TvReviewCard(comment: UIComment, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick, modifier.fillMaxWidth(), interactionSource = interaction,
        shape = TvReviewDefaults.CardShape,
        color = if (focused) Color.White.copy(alpha = .12f) else Color.Black.copy(alpha = .16f),
        contentColor = TvSubjectDetailsDefaults.Content,
        border = BorderStroke(if (focused) 2.dp else 1.dp, Color.White.copy(alpha = if (focused) .95f else .18f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvReviewAuthor(comment, Modifier.weight(1f))
                comment.rating?.takeIf { it in 1..10 }?.let { score ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Rounded.Star, null, Modifier.size(14.dp), tint = TvReviewDefaults.Accent)
                        Text("$score", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = TvReviewDefaults.Accent,
                            modifier = Modifier.testTag("tv-review-score:${comment.stableId}"))
                    }
                }
                Box(Modifier.size(30.dp).clip(CircleShape)
                    .background(if (focused) TvSubjectDetailsDefaults.Content else Color.White.copy(alpha = .08f))
                    .testTag("tv-review-arrow:${comment.stableId}"), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(21.dp),
                        tint = if (focused) Color(0xFF282629) else TvSubjectDetailsDefaults.Content)
                }
            }
            val quote = stringResource(Lang.comment_preview_quote)
            val image = stringResource(Lang.comment_preview_image)
            val hidden = stringResource(Lang.comment_review_hidden)
            val preview = remember(comment.content, quote, image, hidden) {
                reviewPreview(comment.content.elements, quote, image, hidden)
            }
            RichText(listOf(preview), interactionEnabled = false, color = TvSubjectDetailsDefaults.Content,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp))
        }
    }
}

@Composable
internal fun TvReviewAuthor(comment: UIComment, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Person, null, Modifier.size(20.dp), tint = TvSubjectDetailsDefaults.SecondaryContent)
            comment.author?.avatarUrl?.takeIf { it.isNotBlank() }?.let {
                AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(comment.author?.nickname?.takeIf { it.isNotBlank() } ?: stringResource(Lang.foundation_anonymous),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = TvSubjectDetailsDefaults.Content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatDateTime(comment.createdAt) + if (comment.source == UICommentSource.BANGUMI) " · Bangumi" else " · Animeko",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TvSubjectDetailsDefaults.SecondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Masked text is replaced before rendering so previews/accessibility never expose spoilers. */
internal fun reviewPreview(elements: List<UIRichElement>, quote: String, image: String, hidden: String) =
    UIRichElement.AnnotatedText(elements.flatMap { element ->
        when (element) {
            is UIRichElement.AnnotatedText -> element.slice.map { span ->
                if (span is UIRichElement.Annotated.Text) {
                    if (span.mask) UIRichElement.Annotated.Text(hidden, size = 16f)
                    else span.copy(size = 16f, color = Color.Unspecified, url = null)
                } else span
            }
            is UIRichElement.Image -> listOf(UIRichElement.Annotated.Text(image, size = 16f))
            is UIRichElement.Quote -> listOf(UIRichElement.Annotated.Text(quote, size = 16f))
        }
    }, maxLine = 2)

@Composable
internal fun TvReviewLoading(modifier: Modifier = Modifier) {
    val loading = stringResource(Lang.foundation_loading)
    Column(modifier.semantics { contentDescription = loading }, verticalArrangement = Arrangement.spacedBy(TvReviewDefaults.CardGap)) {
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(128.dp).clip(TvReviewDefaults.CardShape).placeholder(true))
        }
    }
}

internal fun Modifier.tvReviewEdges(list: LazyListState) = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = 18.dp.toPx().coerceAtMost(size.height / 2)
        if (list.canScrollBackward && (list.layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0) < list.layoutInfo.viewportStartOffset) drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = fade),
            size = Size(size.width, fade), blendMode = BlendMode.DstIn,
        )
        if (list.canScrollForward) drawRect(
            Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fade, endY = size.height),
            topLeft = Offset(0f, size.height - fade), size = Size(size.width, fade), blendMode = BlendMode.DstIn,
        )
    }

@Composable
internal fun TvReviewScrollbar(list: LazyListState, modifier: Modifier = Modifier) {
    Canvas(modifier.testTag("tv-review-scrollbar")) {
        if (!list.canScrollForward && !list.canScrollBackward) return@Canvas
        val layout = list.layoutInfo
        val first = layout.visibleItemsInfo.firstOrNull() ?: return@Canvas
        val averageHeight = layout.visibleItemsInfo.map { it.size }.average().toFloat() + layout.mainAxisItemSpacing
        val estimatedHeight = averageHeight * layout.totalItemsCount
        val viewport = (layout.viewportEndOffset - layout.viewportStartOffset).toFloat()
        val thumb = (size.height * viewport / estimatedHeight.coerceAtLeast(viewport)).coerceIn(24.dp.toPx(), size.height)
        val progress = if (!list.canScrollForward) 1f else
            ((first.index * averageHeight - first.offset) / (estimatedHeight - viewport).coerceAtLeast(1f)).coerceIn(0f, 1f)
        drawRoundRect(Color.White.copy(alpha = .1f), cornerRadius = CornerRadius(size.width))
        drawRoundRect(Color.White.copy(alpha = .6f), topLeft = Offset(0f, (size.height - thumb) * progress),
            size = Size(size.width, thumb), cornerRadius = CornerRadius(size.width))
    }
}
