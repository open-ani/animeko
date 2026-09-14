/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.details

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import me.him188.ani.app.data.models.person.PersonSubjectSummary
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.comment.CommentReportReason
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.UICommentVote
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_dislike
import me.him188.ani.app.ui.lang.comment_hide_hidden
import me.him188.ani.app.ui.lang.comment_like
import me.him188.ani.app.ui.lang.comment_review_hidden
import me.him188.ani.app.ui.lang.comment_open_in_bangumi
import me.him188.ani.app.ui.lang.comment_preview_image
import me.him188.ani.app.ui.lang.comment_report_reason_harassment
import me.him188.ani.app.ui.lang.comment_report_reason_illegal
import me.him188.ani.app.ui.lang.comment_report_reason_nsfw
import me.him188.ani.app.ui.lang.comment_report_reason_other
import me.him188.ani.app.ui.lang.comment_report_reason_spam
import me.him188.ani.app.ui.lang.comment_report_reason_spoiler
import me.him188.ani.app.ui.lang.comment_report_subtitle
import me.him188.ani.app.ui.lang.comment_report_submit
import me.him188.ani.app.ui.lang.comment_report_title
import me.him188.ani.app.ui.lang.comment_show_hidden
import me.him188.ani.app.ui.lang.foundation_anonymous
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.rating_edit_title
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.subject_collection_page_title
import me.him188.ani.app.ui.lang.subject_details_characters
import me.him188.ani.app.ui.lang.subject_details_empty
import me.him188.ani.app.ui.lang.subject_details_episodes
import me.him188.ani.app.ui.lang.subject_details_info
import me.him188.ani.app.ui.lang.subject_details_login_to_collect
import me.him188.ani.app.ui.lang.subject_details_main_episodes
import me.him188.ani.app.ui.lang.subject_details_no_episodes
import me.him188.ani.app.ui.lang.subject_details_no_summary
import me.him188.ani.app.ui.lang.subject_details_other_episodes
import me.him188.ani.app.ui.lang.subject_details_staff
import me.him188.ani.app.ui.lang.subject_details_summary
import me.him188.ani.app.ui.lang.subject_details_tab_comments
import me.him188.ani.app.ui.lang.subject_details_tab_details
import me.him188.ani.app.ui.lang.subject_details_tags
import me.him188.ani.app.ui.lang.subject_episode_long_press_mark_watched
import me.him188.ani.app.ui.lang.subject_episode_watched
import me.him188.ani.app.ui.search.renderLoadErrorMessage
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.foundation.focus.tvLongPressKey
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.subject.TvSubjectDetailsIntent
import me.him188.ani.leanback.ui.subject.TvSubjectDetailsUiState
import me.him188.ani.leanback.ui.subject.collection.TvCollectionPrompt
import me.him188.ani.leanback.ui.subject.collection.tvCollectionOptions
import me.him188.ani.leanback.ui.subject.components.TvDetailsPanelEntry
import me.him188.ani.leanback.ui.subject.components.TvDetailsPanelLayout
import me.him188.ani.leanback.ui.subject.components.TvDetailsReader
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.components.asDetailsText
import me.him188.ani.leanback.ui.subject.components.detailsHasMask
import me.him188.ani.leanback.ui.subject.components.detailsImages
import me.him188.ani.leanback.ui.subject.components.detailsLinks
import me.him188.ani.leanback.ui.subject.components.detailsRevealMasks
import me.him188.ani.leanback.ui.subject.components.detailsRedactMasks
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsPanelKind
import me.him188.ani.leanback.ui.subject.presentation.TvSubjectPresentationState
import org.jetbrains.compose.resources.stringResource

internal class TvDetailsLists(
    val characters: LazyPagingItems<RelatedCharacterInfo>,
    val staff: LazyPagingItems<RelatedPersonInfo>,
    val related: LazyPagingItems<RelatedSubjectInfo>,
    val comments: LazyPagingItems<UIComment>,
)

@Composable
internal fun TvSubjectDetailsPanels(
    state: TvSubjectDetailsUiState,
    presentation: TvSubjectPresentationState,
    lists: TvDetailsLists,
    onIntent: (TvSubjectDetailsIntent) -> Unit,
    onOpenUrl: (String) -> Unit,
    anchorBounds: Rect? = null,
    anchorButton: @Composable (Modifier) -> Unit = {},
) {
    val panel = presentation.panel ?: return
    val details = state.content ?: return
    val backdrop = state.images.backdrop?.url ?: details.info.imageLarge
    if (panel.kind == TvDetailsPanelKind.Comments) {
        return
    }
    if (panel.kind == TvDetailsPanelKind.Summary) {
        TvSubjectDescription(details, backdrop, presentation::close) { tag ->
            presentation.open(TvDetailsPanelKind.TagResults, tag, "summary-text")
            onIntent(TvSubjectDetailsIntent.SearchTag(tag))
        }
        return
    }
    if (panel.kind == TvDetailsPanelKind.Rating && details.collectionType == UnifiedCollectionType.NOT_COLLECTED) {
        LaunchedEffect(panel.key) { presentation.close() }
        return
    }
    if (panel.kind == TvDetailsPanelKind.Rating && state.loggedIn == true) {
        val operation = state.operation.takeIf { it.requestId == presentation.generation }
        TvSubjectRatingPanel(initialTvRatingScore(details.selfRating.score, details.info.ratingInfo.score),
            backdrop, anchorBounds ?: return, anchorButton,
            operation?.busy == true, operation?.error,
            presentation::close) { score ->
            onIntent(TvSubjectDetailsIntent.SetScore(score, presentation.generation))
        }
        return
    }
    val compact = anchorBounds != null
    val entries = mutableListOf<TvDetailsPanelEntry>()
    val actions = mutableListOf<TvDetailsPanelEntry>()
    val busy = state.operation.busy
    fun action(key: String, label: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
        actions += TvDetailsPanelEntry(key) { modifier ->
            TvDetailsAction(label, icon, onClick, modifier.widthIn(max = 240.dp), active = selected, busy = busy, compact = true)
        }
    }
    fun row(key: String, title: String, selected: Boolean = false, click: () -> Unit) {
        entries += TvDetailsPanelEntry(key) { modifier ->
            TvOptionRow(title, selected = selected, modifier = modifier, compact = compact,
                leadingContent = if (busy) ({ CircularProgressIndicator(Modifier.size(20.dp)) }) else null,
                onClick = { if (!busy) click() })
        }
    }
    fun paragraph(key: String, text: String) {
        entries += TvDetailsPanelEntry(key) { TvDetailsReader(text.asDetailsText(), it) }
    }
    fun open(kind: TvDetailsPanelKind, argument: String = "", origin: String) =
        presentation.open(kind, argument, origin)
    fun subjectRows(items: LazyPagingItems<PersonSubjectSummary>) {
        repeat(items.itemCount) { index ->
            val item = items.peek(index) ?: return@repeat
            entries += TvDetailsPanelEntry("subject:${item.subjectId}") { modifier ->
                items[index]?.let {
                    TvOptionRow(it.displayName, modifier = modifier) {
                        onIntent(TvSubjectDetailsIntent.OpenRelatedSubject(it.subjectId))
                    }
                }
            }
        }
    }
    var title = stringResource(Lang.subject_details_tab_details)
    var initialKey: String? = null
    when (panel.kind) {
        TvDetailsPanelKind.Summary -> {
            title = stringResource(Lang.subject_details_summary)
            paragraph("summary-text", details.info.summary.ifBlank { stringResource(Lang.subject_details_no_summary) })
        }
        TvDetailsPanelKind.Image -> {
            title = stringResource(Lang.comment_preview_image)
            entries += TvDetailsPanelEntry("image") {
                AsyncImage(panel.argument, title, it.fillMaxWidth().height(360.dp).focusable(), contentScale = ContentScale.Fit)
            }
        }
        TvDetailsPanelKind.Collection, TvDetailsPanelKind.RemoveCollection, TvDetailsPanelKind.MarkAllWatched -> {
            title = stringResource(Lang.subject_collection_page_title)
            if (state.loggedIn != true) {
                row("login", stringResource(Lang.subject_details_login_to_collect)) { onIntent(TvSubjectDetailsIntent.Login) }
            } else {
                val prompt = when (panel.kind) {
                    TvDetailsPanelKind.RemoveCollection -> TvCollectionPrompt.Remove
                    TvDetailsPanelKind.MarkAllWatched -> TvCollectionPrompt.MarkAllWatched
                    else -> null
                }
                val options = tvCollectionOptions(details.collectionType, busy, prompt,
                    onPromptChange = { next ->
                        when (next) {
                            TvCollectionPrompt.Remove -> open(TvDetailsPanelKind.RemoveCollection,
                                origin = "collection:${UnifiedCollectionType.NOT_COLLECTED.name}")
                            TvCollectionPrompt.MarkAllWatched -> open(TvDetailsPanelKind.MarkAllWatched, origin = "collection")
                            null -> presentation.close()
                        }
                    },
                    onSetCollection = { onIntent(TvSubjectDetailsIntent.SetCollection(it, presentation.generation)) },
                    onMarkAllWatched = { onIntent(TvSubjectDetailsIntent.MarkAllWatched(presentation.generation)) },
                )
                initialKey = options.first { it.isEntry }.key
                entries += options.map { TvDetailsPanelEntry(it.key, it.focusable, it.content) }
            }
        }
        TvDetailsPanelKind.Rating -> {
            title = stringResource(Lang.rating_edit_title)
            row("login", stringResource(Lang.subject_details_login_to_collect)) { onIntent(TvSubjectDetailsIntent.Login) }
        }
        TvDetailsPanelKind.Episodes -> {
            title = stringResource(Lang.subject_details_episodes)
            initialKey = panel.argument.takeIf { it.isNotEmpty() }?.let { "episode:$it" }
                ?: details.playTargetId?.let { "episode:$it" }
            listOf(true, false).forEach { main ->
                val episodes = details.episodes.filter { (it.episodeId in details.mainEpisodeIds) == main }
                if (episodes.isNotEmpty()) {
                    val heading = stringResource(if (main) Lang.subject_details_main_episodes else Lang.subject_details_other_episodes)
                    entries += TvDetailsPanelEntry("heading:$main", focusable = false) { Text(heading, it,
                        color = TvOptionDefaults.Content, style = MaterialTheme.typography.titleMedium) }
                }
                episodes.forEach { episode ->
                    entries += TvDetailsPanelEntry("episode:${episode.episodeId}") { modifier ->
                        val play = { onIntent(TvSubjectDetailsIntent.PlayEpisode(episode.episodeId)) }
                        TvOptionRow(
                            "${episode.sort} · ${episode.nameCn.ifBlank { episode.name }}",
                            value = if (episode.isDoneOrDropped) stringResource(Lang.subject_episode_watched) else "",
                            selected = episode.episodeId == details.playTargetId,
                            supportingText = stringResource(Lang.subject_episode_long_press_mark_watched),
                            modifier = modifier.tvLongPressKey(
                                onShortPress = play,
                                onLongPress = { onIntent(TvSubjectDetailsIntent.ToggleEpisode(episode.episodeId, presentation.generation)) },
                            ),
                            onClick = play,
                        )
                    }
                }
            }
            if (entries.isEmpty()) paragraph("no-episodes", stringResource(if (details.episodesLoading) Lang.foundation_loading else Lang.subject_details_no_episodes))
        }
        TvDetailsPanelKind.Characters -> {
            title = stringResource(Lang.subject_details_characters)
            repeat(lists.characters.itemCount) { index ->
                val item = lists.characters.peek(index) ?: return@repeat
                entries += TvDetailsPanelEntry("character:${item.character.id}") { modifier ->
                    lists.characters[index]?.let {
                        TvOptionRow(it.character.displayName, supportingText = it.character.actors.joinToString { it.displayName }, modifier = modifier) {
                            onIntent(TvSubjectDetailsIntent.OpenCharacter(it.character.id))
                        }
                    }
                }
            }
            entries += pagingStatus(lists.characters, lists.characters.itemCount == 0)
        }
        TvDetailsPanelKind.Staff -> {
            title = stringResource(Lang.subject_details_staff)
            repeat(lists.staff.itemCount) { index ->
                val item = lists.staff.peek(index) ?: return@repeat
                entries += TvDetailsPanelEntry("person:${item.personInfo.id}:${item.position}") { modifier ->
                    lists.staff[index]?.let {
                        TvOptionRow(it.personInfo.displayName, modifier = modifier) {
                            onIntent(TvSubjectDetailsIntent.OpenStaff(it.personInfo.id))
                        }
                    }
                }
            }
            entries += pagingStatus(lists.staff, lists.staff.itemCount == 0)
        }
        TvDetailsPanelKind.Tags -> {
            title = stringResource(Lang.subject_details_tags)
            details.info.tags.forEach { tag ->
                row("tag:${tag.name}", tag.name) { open(TvDetailsPanelKind.TagResults, tag.name, "tag:${tag.name}") }
            }
        }
        TvDetailsPanelKind.TagResults -> {
            title = panel.argument
            if (state.tagResults?.tag == panel.argument) {
                val items = state.tagResults.subjects.collectAsLazyPagingItems()
                subjectRows(items)
                entries += pagingStatus(items, items.itemCount == 0)
            } else paragraph("tag-loading", stringResource(Lang.foundation_loading))
        }
        TvDetailsPanelKind.Comments -> Unit // Retained by the screen beneath its child overlays.
        TvDetailsPanelKind.Comment, TvDetailsPanelKind.Report -> {
            title = stringResource(Lang.subject_details_tab_comments)
            val rawComment = lists.comments.itemSnapshotList.items.find { it.stableId == panel.argument }
            if (rawComment == null) paragraph("comment-missing", stringResource(Lang.subject_details_empty))
            else {
                val comment = details.commentPresentation(rawComment)
                if (panel.kind == TvDetailsPanelKind.Report) {
                    title = stringResource(Lang.comment_report_title)
                    paragraph("report-info", stringResource(Lang.comment_report_subtitle))
                    val selectedReason = state.reportDraft?.takeIf { it.commentId == comment.stableId }?.reason
                    CommentReportReason.entries.forEach { reason ->
                        val text = stringResource(when (reason) {
                            CommentReportReason.SPAM -> Lang.comment_report_reason_spam
                            CommentReportReason.HARASSMENT -> Lang.comment_report_reason_harassment
                            CommentReportReason.SPOILER -> Lang.comment_report_reason_spoiler
                            CommentReportReason.NSFW -> Lang.comment_report_reason_nsfw
                            CommentReportReason.ILLEGAL -> Lang.comment_report_reason_illegal
                            CommentReportReason.OTHER -> Lang.comment_report_reason_other
                        })
                        row("report:${reason.name}", text, selected = selectedReason == reason) {
                            onIntent(TvSubjectDetailsIntent.ChooseReportReason(comment.stableId, reason))
                        }
                    }
                    if (selectedReason != null) row("report-submit", stringResource(Lang.comment_report_submit)) {
                        onIntent(TvSubjectDetailsIntent.Report(comment, selectedReason, presentation.generation))
                    }
                } else {
                    title = comment.author?.nickname ?: stringResource(Lang.foundation_anonymous)
                    var reveal by rememberSaveable(comment.stableId) { mutableStateOf(false) }
                    val elements = if (reveal) comment.content.elements.detailsRevealMasks()
                        else comment.content.elements.detailsRedactMasks(stringResource(Lang.comment_review_hidden))
                    entries += TvDetailsPanelEntry("comment-text") { TvDetailsReader(elements, it) }
                    if (elements.detailsHasMask() || reveal) action("reveal",
                        stringResource(if (reveal) Lang.comment_hide_hidden else Lang.comment_show_hidden),
                        if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, selected = reveal) { reveal = !reveal }
                    elements.detailsLinks().forEach { url -> action("link:$url", url, Icons.Rounded.Link) { onOpenUrl(url) } }
                    elements.detailsImages().forEach { url ->
                        action("image:$url", stringResource(Lang.comment_preview_image), Icons.Rounded.Image) { open(TvDetailsPanelKind.Image, url, "image:$url") }
                    }
                    if (comment.source == UICommentSource.ANI) {
                        action("like", "${stringResource(Lang.comment_like)} ${comment.likeCount}", Icons.Rounded.ThumbUp, comment.selfVote == UICommentVote.LIKE) {
                            onIntent(TvSubjectDetailsIntent.Vote(comment, UICommentVote.LIKE))
                        }
                        action("dislike", stringResource(Lang.comment_dislike), Icons.Rounded.ThumbDown, comment.selfVote == UICommentVote.DISLIKE) {
                            onIntent(TvSubjectDetailsIntent.Vote(comment, UICommentVote.DISLIKE))
                        }
                    }
                    if (details.canReport) action("report", stringResource(Lang.comment_report_title), Icons.Rounded.Flag) {
                        if (state.loggedIn == true) open(TvDetailsPanelKind.Report, comment.stableId, "report")
                        else onIntent(TvSubjectDetailsIntent.Login)
                    }
                    action("original", stringResource(Lang.comment_open_in_bangumi), Icons.AutoMirrored.Rounded.Launch) { onOpenUrl("https://bgm.tv/subject/${details.info.subjectId}") }
                }
            }
        }
    }
    if (state.operation.requestId == presentation.generation) {
        state.operation.error?.let { error ->
            val text = renderLoadErrorMessage(error)
            entries += TvDetailsPanelEntry("operation-error", focusable = false) { Text(text, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (entries.isEmpty()) paragraph("empty", stringResource(Lang.subject_details_empty))
    TvDetailsPanelLayout(title, entries, backdrop, presentation::close, initialKey = initialKey,
        focusedKey = panel.focusedItem, onFocused = presentation::rememberPanelFocus, compact = compact,
        showHeader = panel.kind !in setOf(TvDetailsPanelKind.Collection, TvDetailsPanelKind.RemoveCollection,
            TvDetailsPanelKind.MarkAllWatched) || state.loggedIn != true,
        modal = panel.kind == TvDetailsPanelKind.Comment,
        actions = actions,
        maxWidth = if (panel.kind == TvDetailsPanelKind.Episodes) TvSubjectDetailsDefaults.EpisodesPanelMaxWidth else TvSubjectDetailsDefaults.PanelMaxWidth,
        anchorBounds = anchorBounds, anchorButton = anchorButton)
}

@Composable
private fun pagingStatus(items: LazyPagingItems<*>, empty: Boolean): List<TvDetailsPanelEntry> {
    val error = (items.loadState.refresh as? LoadState.Error) ?: (items.loadState.append as? LoadState.Error)
    return when {
        error != null -> {
            val message = renderLoadErrorMessage(LoadError.fromException(error.error))
            listOf(TvDetailsPanelEntry("paging-error") { modifier ->
                TvOptionRow(stringResource(Lang.settings_mediasource_retry), supportingText = message, modifier = modifier) { items.retry() }
            })
        }
        items.loadState.refresh is LoadState.Loading || items.loadState.append is LoadState.Loading ->
            listOf(TvDetailsPanelEntry("paging-loading", focusable = empty) { modifier ->
                Text(stringResource(Lang.foundation_loading), if (empty) modifier.focusable() else modifier, color = TvOptionDefaults.Muted)
            })
        empty -> listOf(TvDetailsPanelEntry("paging-empty") { TvDetailsReader(stringResource(Lang.subject_details_empty).asDetailsText(), it) })
        else -> emptyList()
    }
}
