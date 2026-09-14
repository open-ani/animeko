/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.reviews

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.leanback.ui.foundation.widgets.TvPlaceholderBlock
import me.him188.ani.leanback.ui.subject.components.TvDetailsTextPlaceholder

/** Same author, metadata and two preview lines as TvReviewCard, without a click target. */
@Composable
internal fun TvReviewPlaceholder(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = TvReviewDefaults.CardShape, color = Color.Black.copy(alpha = .16f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .18f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvPlaceholderBlock(Modifier.size(32.dp), CircleShape)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvPlaceholderBlock(Modifier.fillMaxWidth(.5f).height(16.dp))
                    TvPlaceholderBlock(Modifier.fillMaxWidth(.7f).height(11.dp))
                }
                TvPlaceholderBlock(Modifier.size(30.dp), CircleShape)
            }
            TvDetailsTextPlaceholder()
        }
    }
}
