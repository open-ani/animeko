/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared heading animation and row geometry for episodes and paged browse sections. */
@Composable
internal fun TvDetailsBrowseRowLayout(
    title: String,
    listState: LazyListState,
    sectionId: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    headingAction: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val headingProgress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(180),
        label = "details-$sectionId-heading",
    )
    Column(modifier.padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy((16 + 10 * headingProgress).dp)) {
        Row(
            Modifier.padding(horizontal = TvSubjectDetailsDefaults.HorizontalPadding).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, color = TvSubjectDetailsDefaults.Content,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = (16 + 10 * headingProgress).sp, lineHeight = (20 + 12 * headingProgress).sp,
                ), modifier = Modifier.weight(1f).testTag("tv-details-$sectionId-heading"),
            )
            headingAction()
        }
        LazyRow(
            state = listState, modifier = rowModifier,
            contentPadding = PaddingValues(horizontal = TvSubjectDetailsDefaults.HorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(TvSubjectDetailsDefaults.RowSpacing),
            content = content,
        )
    }
}
