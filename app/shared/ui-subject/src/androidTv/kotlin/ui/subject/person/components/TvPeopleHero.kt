/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.progressSemantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.person_details_meta
import me.him188.ani.leanback.ui.foundation.widgets.TvPlaceholderBlock
import me.him188.ani.leanback.ui.subject.components.TvDetailsTextPlaceholder
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.details.formatCount
import me.him188.ani.leanback.ui.subject.person.TvPeopleKind
import me.him188.ani.leanback.ui.subject.person.TvPeopleProfile
import me.him188.ani.leanback.ui.subject.person.peopleKindLabel
import me.him188.ani.leanback.ui.subject.person.peopleMetadata
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvPeopleIdentity(kind: TvPeopleKind, profile: TvPeopleProfile?, loading: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(peopleKindLabel(kind), color = TvSubjectDetailsDefaults.SecondaryContent,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp), modifier = Modifier.testTag("tv-people-kind"))
        if (loading) {
            Column(Modifier.fillMaxWidth().progressSemantics().testTag("tv-people-identity-loading"),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                TvDetailsTextPlaceholder(lines = 1, fontSize = 40.sp, lineHeight = 48.sp, lastLineFraction = .7f)
                TvDetailsTextPlaceholder(lines = 1, fontSize = 19.sp, lineHeight = 24.sp, lastLineFraction = .5f)
                TvDetailsTextPlaceholder(Modifier.padding(top = 4.dp), lines = 1, fontSize = 15.sp, lineHeight = 20.sp, lastLineFraction = .8f)
            }
        } else Text(profile?.name ?: peopleKindLabel(kind),
            color = TvSubjectDetailsDefaults.Content,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 48.sp),
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("tv-people-name"))
        profile?.originalName?.takeIf { it.isNotBlank() && it != profile.name }?.let {
            Text(it, color = TvSubjectDetailsDefaults.SecondaryContent,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 24.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (profile != null) {
            Text(stringResource(Lang.person_details_meta, peopleMetadata(kind, profile), formatCount(profile.collects)),
                color = TvSubjectDetailsDefaults.SecondaryContent,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun TvPeoplePortrait(profile: TvPeopleProfile?, modifier: Modifier = Modifier, loading: Boolean = false) {
    val url = profile?.image.orEmpty()
    var imageLoading by remember(url) { mutableStateOf(url.isNotBlank()) }
    Box(modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(TvPeopleDefaults.PortraitCorner))
        .background(Color.White.copy(alpha = .08f)).testTag("tv-people-portrait"), contentAlignment = Alignment.Center) {
        if (!loading && !imageLoading) Icon(Icons.Rounded.Person, null, Modifier.size(48.dp), tint = TvSubjectDetailsDefaults.SecondaryContent)
        if (url.isNotBlank()) AsyncImage(url, null, Modifier.fillMaxSize(),
            contentScale = if (profile?.portrait != false) ContentScale.Crop else ContentScale.Fit,
            alignment = if (profile?.portrait != false) Alignment.TopCenter else Alignment.Center,
            onLoading = { imageLoading = true }, onSuccess = { imageLoading = false }, onError = { imageLoading = false })
        if (loading || imageLoading) TvPlaceholderBlock(Modifier.fillMaxSize().progressSemantics().testTag("tv-people-portrait-loading"),
            RoundedCornerShape(TvPeopleDefaults.PortraitCorner))
    }
}

/** One focus target, including its decorative arrow. Focus changes the border, never the size. */
@Composable
internal fun TvPeopleDiscussionPreviewCard(
    title: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val loadingDescription = stringResource(Lang.foundation_loading)
    Surface(onClick, modifier.heightIn(min = TvSubjectDetailsDefaults.DescriptionCardHeight)
        .then(if (loading) Modifier.progressSemantics().semantics { contentDescription = "$title, $loadingDescription" } else Modifier),
        interactionSource = interaction, shape = TvSubjectDetailsDefaults.DescriptionCardShape,
        color = if (focused) Color.White.copy(alpha = .12f) else Color.Black.copy(alpha = .16f),
        contentColor = TvSubjectDetailsDefaults.Content,
        border = BorderStroke(if (focused) 2.dp else 1.dp, Color.White.copy(alpha = if (focused) .95f else .22f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) {
                    TvDetailsTextPlaceholder(Modifier.weight(1f), lines = 1, fontSize = 18.sp, lineHeight = 24.sp)
                    TvPlaceholderBlock(Modifier.size(26.dp), CircleShape)
                } else {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp))
                    Box(Modifier.size(26.dp).background(if (focused) TvSubjectDetailsDefaults.Content else Color.White.copy(alpha = .12f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(22.dp),
                            tint = if (focused) TvSubjectDetailsDefaults.Background else TvSubjectDetailsDefaults.Content)
                    }
                }
            }
            if (loading) TvDetailsTextPlaceholder(Modifier.fillMaxWidth().testTag("tv-people-discussion-preview-loading"),
                fontSize = 15.sp, lineHeight = 21.sp)
            else content()
            Spacer(Modifier.weight(1f))
            if (loading) TvDetailsTextPlaceholder(Modifier.align(Alignment.End).width(84.dp), lines = 1,
                fontSize = 12.sp, lineHeight = 16.sp, lastLineFraction = 1f)
            else Text(caption, Modifier.align(Alignment.End), color = TvSubjectDetailsDefaults.SecondaryContent,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp))
        }
    }
}
