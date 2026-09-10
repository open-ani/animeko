/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person.discussion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.comment.CommentReportReason
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.UICommentVote
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_dislike
import me.him188.ani.app.ui.lang.comment_hide_hidden
import me.him188.ani.app.ui.lang.comment_like
import me.him188.ani.app.ui.lang.comment_open_in_bangumi
import me.him188.ani.app.ui.lang.comment_preview_image
import me.him188.ani.app.ui.lang.comment_report_reason_harassment
import me.him188.ani.app.ui.lang.comment_report_reason_illegal
import me.him188.ani.app.ui.lang.comment_report_reason_nsfw
import me.him188.ani.app.ui.lang.comment_report_reason_other
import me.him188.ani.app.ui.lang.comment_report_reason_spam
import me.him188.ani.app.ui.lang.comment_report_reason_spoiler
import me.him188.ani.app.ui.lang.comment_report_submit
import me.him188.ani.app.ui.lang.comment_report_title
import me.him188.ani.app.ui.lang.comment_show_hidden
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.components.detailsHasMask
import me.him188.ani.leanback.ui.subject.components.detailsImages
import me.him188.ani.leanback.ui.subject.components.detailsLinks
import me.him188.ani.leanback.ui.subject.details.TvDetailsAction
import org.jetbrains.compose.resources.stringResource

private class PeopleCommentAction(val key: String, val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit)

@Composable
internal fun TvPeopleCommentActions(
    comment: UIComment,
    elements: List<UIRichElement>,
    revealed: Boolean,
    onReveal: () -> Unit,
    actionModifier: (String) -> Modifier,
    onVote: (UICommentVote) -> Unit,
    onOpenOriginal: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onReport: () -> Unit,
    onImage: (String) -> Unit,
) {
    val actions = buildList {
        if (elements.detailsHasMask() || revealed) add(PeopleCommentAction("reveal",
            stringResource(if (revealed) Lang.comment_hide_hidden else Lang.comment_show_hidden),
            if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, revealed, onReveal))
        if (comment.source == UICommentSource.ANI) {
            add(PeopleCommentAction("like", "${stringResource(Lang.comment_like)} ${comment.likeCount}", Icons.Rounded.ThumbUp,
                comment.selfVote == UICommentVote.LIKE) { onVote(UICommentVote.LIKE) })
            add(PeopleCommentAction("dislike", stringResource(Lang.comment_dislike), Icons.Rounded.ThumbDown,
                comment.selfVote == UICommentVote.DISLIKE) { onVote(UICommentVote.DISLIKE) })
        }
        add(PeopleCommentAction("report", stringResource(Lang.comment_report_title), Icons.Rounded.Flag, false, onReport))
        if (comment.source == UICommentSource.BANGUMI) add(PeopleCommentAction("original", stringResource(Lang.comment_open_in_bangumi),
            Icons.AutoMirrored.Rounded.Launch, false, onOpenOriginal))
        elements.detailsLinks().forEach { url -> add(PeopleCommentAction("link:$url", url, Icons.Rounded.Link, false) { onOpenUrl(url) }) }
        val imageLabel = stringResource(Lang.comment_preview_image)
        elements.detailsImages().forEach { url -> add(PeopleCommentAction("image:$url", imageLabel, Icons.Rounded.Image, false) { onImage(url) }) }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("tv-people-comment-actions")) {
        items(actions, key = { it.key }) { action ->
            TvDetailsAction(action.label, action.icon, action.onClick,
                Modifier.widthIn(max = 280.dp).then(actionModifier(action.key)).testTag("tv-people-action:${action.key}"),
                compact = true, active = action.selected)
        }
    }
}

@Composable
internal fun TvPeopleReport(
    commentId: String?,
    busy: Boolean,
    onSubmit: (CommentReportReason) -> Unit,
    anchor: (String) -> Modifier,
) {
    var reason by rememberSaveable(commentId) { mutableStateOf(CommentReportReason.SPAM) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("heading") { Text(stringResource(Lang.comment_report_title), color = TvSubjectDetailsDefaults.Content) }
        items(CommentReportReason.entries, key = { it.name }) { option ->
            val text = stringResource(when (option) {
                CommentReportReason.SPAM -> Lang.comment_report_reason_spam
                CommentReportReason.HARASSMENT -> Lang.comment_report_reason_harassment
                CommentReportReason.SPOILER -> Lang.comment_report_reason_spoiler
                CommentReportReason.NSFW -> Lang.comment_report_reason_nsfw
                CommentReportReason.ILLEGAL -> Lang.comment_report_reason_illegal
                CommentReportReason.OTHER -> Lang.comment_report_reason_other
            })
            TvOptionRow(text, selected = reason == option, enabled = !busy, modifier = anchor("reason:${option.name}")) { reason = option }
        }
        item("submit") {
            TvOptionRow(stringResource(Lang.comment_report_submit), enabled = !busy && commentId != null, modifier = anchor("report-submit")) {
                onSubmit(reason)
            }
        }
    }
}
