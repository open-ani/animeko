/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Subject
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.subject_details_no_summary
import me.him188.ani.app.ui.lang.subject_details_show_more
import me.him188.ani.app.ui.lang.subject_details_summary
import me.him188.ani.leanback.ui.foundation.widgets.TvPlaceholderBlock
import org.jetbrains.compose.resources.stringResource

/** Shared introduction entry for subjects, characters, voice actors and staff. */
@Composable
internal fun TvDetailsDescriptionCard(
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    title: String = stringResource(Lang.subject_details_summary),
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val loadingDescription = stringResource(Lang.foundation_loading)
    Surface(
        modifier = modifier.heightIn(min = TvSubjectDetailsDefaults.DescriptionCardHeight)
            .then(if (loading) Modifier.progressSemantics().semantics { contentDescription = "$title, $loadingDescription" } else Modifier).clickable(
            interactionSource = interaction, indication = null, enabled = interactive,
            role = Role.Button, onClick = onClick,
        ),
        shape = TvSubjectDetailsDefaults.DescriptionCardShape,
        color = if (focused) Color.White.copy(alpha = .18f) else Color.Black.copy(alpha = .28f),
        border = BorderStroke(if (focused) 2.dp else 1.dp, Color.White.copy(alpha = if (focused) .95f else .38f)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) {
                    TvPlaceholderBlock(Modifier.size(24.dp))
                    TvDetailsTextPlaceholder(Modifier.weight(1f), lines = 1, fontSize = 16.sp,
                        lineHeight = MaterialTheme.typography.titleMedium.lineHeight)
                    TvPlaceholderBlock(Modifier.size(24.dp), CircleShape)
                } else {
                    Icon(Icons.AutoMirrored.Rounded.Subject, null, Modifier.size(24.dp), tint = TvSubjectDetailsDefaults.Content)
                    Text(title, Modifier.weight(1f), color = TvSubjectDetailsDefaults.Content,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null,
                        Modifier.size(24.dp).background(
                            if (focused) TvSubjectDetailsDefaults.Content else Color.White.copy(alpha = .08f),
                            CircleShape,
                        ).padding(4.dp),
                        tint = if (focused) TvSubjectDetailsDefaults.Background else TvSubjectDetailsDefaults.Content)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (loading) TvDetailsTextPlaceholder(Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp)
                else Text(summary.ifBlank { stringResource(Lang.subject_details_no_summary) }, Modifier.weight(1f),
                    color = TvSubjectDetailsDefaults.Content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (loading) TvDetailsTextPlaceholder(Modifier.width(56.dp), lines = 1, fontSize = 14.sp,
                    lineHeight = 20.sp, lastLineFraction = 1f)
                else Text(stringResource(Lang.subject_details_show_more), color = TvSubjectDetailsDefaults.Content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    textDecoration = TextDecoration.Underline)
            }
        }
    }
}
