package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_watched
import org.jetbrains.compose.resources.stringResource


@Composable
fun EpisodeWatchStatusButton(
    isDone: Boolean,
    onUnmark: () -> Unit,
    onMarkAsDone: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val watchedText = stringResource(Lang.subject_episode_watched)
    Box(
        modifier,
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (isDone) {
            IconButton(
                onClick = onUnmark,
                Modifier.offset(x = 8.dp),
                enabled = enabled,
            ) {
                Icon(Icons.Outlined.TaskAlt, null)
            }
        } else {
            SuggestionChip(
                onClick = onMarkAsDone,
                label = {
                    Text(watchedText, softWrap = false)
                },
                icon = {
                    Icon(Icons.Outlined.AddTask, null)
                },
                enabled = enabled,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    labelColor = MaterialTheme.colorScheme.primary,
                    iconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}
