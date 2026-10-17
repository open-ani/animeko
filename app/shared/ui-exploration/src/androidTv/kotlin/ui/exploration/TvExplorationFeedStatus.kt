/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.tv.ui.exploration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_load_failed
import me.him188.ani.app.ui.lang.exploration_loading
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.subject_details_empty
import org.jetbrains.compose.resources.stringResource

/** An in-page continuation target preserves navigation during first load, empty pages and retry. */
@Composable
internal fun TvExplorationFeedStatus(state: LoadState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val loading = state is LoadState.Loading
    Column(
        Modifier.fillMaxWidth()
            .padding(start = TvExplorationDefaults.StartPadding, end = TvExplorationDefaults.EndPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(
                when (state) {
                    is LoadState.Loading -> Lang.exploration_loading
                    is LoadState.Error -> Lang.exploration_load_failed
                    is LoadState.NotLoading -> Lang.subject_details_empty
                },
            ),
            color = TvExplorationDefaults.SecondaryContent,
        )
        Button(
            onClick = { if (!loading) onRetry() },
            modifier = modifier.testTag("tv-exploration-feed-status")
                .then(if (loading) Modifier.progressSemantics() else Modifier),
            scale = ButtonDefaults.scale(focusedScale = 1f),
        ) {
            Text(stringResource(if (loading) Lang.exploration_loading else Lang.settings_mediasource_retry))
        }
    }
}
