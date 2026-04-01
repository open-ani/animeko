/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.episode.danmaku

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.episode_danmaku_match_change
import me.him188.ani.app.ui.lang.episode_danmaku_match_loading_danmaku
import me.him188.ani.app.ui.lang.episode_danmaku_match_loading_subjects
import me.him188.ani.app.ui.lang.episode_danmaku_match_select_episode
import me.him188.ani.app.ui.lang.episode_danmaku_match_select_subject
import me.him188.ani.app.ui.lang.exploration_search
import me.him188.ani.app.ui.lang.settings_mediasource_cancel
import me.him188.ani.app.ui.lang.settings_mediasource_test_keyword
import me.him188.ani.danmaku.api.provider.DanmakuEpisode
import me.him188.ani.danmaku.api.provider.DanmakuFetchResult
import me.him188.ani.danmaku.api.provider.DanmakuSubject
import org.jetbrains.compose.resources.stringResource

@Composable
fun MatchingDanmakuDialog(
    onDismissRequest: () -> Unit,
    initialQuery: String,
    uiState: MatchingDanmakuUiState,
    onSubmitQuery: (String) -> Unit,
    onSelectSubject: (DanmakuSubject) -> Unit,
    onSelectEpisode: (DanmakuEpisode) -> Unit,
    onComplete: (List<DanmakuFetchResult>) -> Unit,
) {
    val changeDanmakuText = stringResource(Lang.episode_danmaku_match_change)
    val cancelText = stringResource(Lang.settings_mediasource_cancel)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
        },
        title = {
            Text(changeDanmakuText)
        },
        text = {
            MatchingDanmakuScreen(
                initialQuery = initialQuery,
                uiState = uiState,
                onSubmitQuery = onSubmitQuery,
                onSelectSubject = onSelectSubject,
                onSelectEpisode = onSelectEpisode,
                onComplete = onComplete,
            )
        },
        dismissButton = {
            TextButton(onDismissRequest) {
                Text(cancelText)
            }
        },
    )
}


@Composable
fun MatchingDanmakuScreen(
    initialQuery: String,
    uiState: MatchingDanmakuUiState,
    onSubmitQuery: (String) -> Unit,
    onSelectSubject: (DanmakuSubject) -> Unit,
    onSelectEpisode: (DanmakuEpisode) -> Unit,
    onComplete: (List<DanmakuFetchResult>) -> Unit,
) {
    val keywordText = stringResource(Lang.settings_mediasource_test_keyword)
    val searchText = stringResource(Lang.exploration_search)
    val loadingEpisodesText = stringResource(Lang.episode_danmaku_match_loading_subjects)
    val loadingDanmakuText = stringResource(Lang.episode_danmaku_match_loading_danmaku)
    if (uiState.isFlowComplete) {
        onComplete(uiState.danmakuFetchResults)
        return
    }

    // Track whether to show the dialogs
    var showSubjectDialog by rememberSaveable { mutableStateOf(false) }
    var showEpisodeDialog by rememberSaveable { mutableStateOf(false) }

    // Whenever new subjects are loaded (and not loading), show the Subject dialog.
    LaunchedEffect(uiState.subjects, uiState.isLoadingSubjects) {
        if (!uiState.isLoadingSubjects && uiState.subjects.isNotEmpty()) {
            showSubjectDialog = true
        }
    }

    // Whenever new episodes are loaded (and not loading), show the Episode dialog.
    LaunchedEffect(uiState.episodes, uiState.isLoadingEpisodes) {
        if (!uiState.isLoadingEpisodes && uiState.episodes.isNotEmpty()) {
            showEpisodeDialog = true
        }
    }

    // Main content: input field + "Search" button
    Column(
        modifier = Modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        // Input field for the danmaku query
        var query by rememberSaveable {
            mutableStateOf(initialQuery)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(keywordText) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Button to submit the query and fetch subject list
        Button(
            onClick = { onSubmitQuery(query) },
            enabled = query.isNotBlank() && !uiState.isLoadingSubjects,
        ) {
            Text(searchText)
        }

        // Show a loading spinner if subjects are loading
        if (uiState.isLoadingSubjects) {
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator()
        }

        // Error message for subject loading
        uiState.subjectError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = Color.Red)
        }

        // Loading/Errors for episodes and danmaku can also be displayed in-line, if desired.
        if (uiState.isLoadingEpisodes) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(loadingEpisodesText)
        }
        uiState.episodeError?.let { episodeErr ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(episodeErr, color = Color.Red)
        }

        if (uiState.isLoadingDanmaku) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(loadingDanmakuText)
        }
        uiState.danmakuError?.let { danmakuErr ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(danmakuErr, color = Color.Red)
        }
    }

    // Subject Picker Dialog
    if (showSubjectDialog) {
        SubjectPickerDialog(
            subjects = uiState.subjects,
            onSelect = { subject ->
                showSubjectDialog = false
                onSelectSubject(subject)
            },
            onDismissRequest = { showSubjectDialog = false },
        )
    }

    // Episode Picker Dialog
    if (showEpisodeDialog) {
        EpisodePickerDialog(
            episodes = uiState.episodes,
            onSelect = { episode ->
                showEpisodeDialog = false
                onSelectEpisode(episode)
            },
            onDismissRequest = { showEpisodeDialog = false },
        )
    }
}

@Composable
fun SubjectPickerDialog(
    subjects: List<DanmakuSubject>,
    onSelect: (DanmakuSubject) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val selectSubjectText = stringResource(Lang.episode_danmaku_match_select_subject)
    val cancelText = stringResource(Lang.settings_mediasource_cancel)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(selectSubjectText) },
        text = {
            LazyColumn {
                items(subjects, key = { "MatchingDanmakuDialog-" + it.id }, contentType = { 1 }) { subject ->
                    ListItem(
                        headlineContent = { Text(subject.name) },
                        Modifier.clickable { onSelect(subject) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = {
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(cancelText) }
        },
    )
}

@Composable
fun EpisodePickerDialog(
    episodes: List<DanmakuEpisode>,
    onSelect: (DanmakuEpisode) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val selectEpisodeText = stringResource(Lang.episode_danmaku_match_select_episode)
    val cancelText = stringResource(Lang.settings_mediasource_cancel)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(selectEpisodeText) },
        text = {
            LazyColumn {
                items(episodes, key = { "MatchingDanmakuDialog-" + it.id }, contentType = { 1 }) { episode ->
                    ListItem(
                        headlineContent = {
                            Text(episode.name)
                        },
                        Modifier.clickable { onSelect(episode) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = {
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(cancelText) }
        },
    )
}
//
//@Composable
//@Preview
//private fun PreviewMatchingDanmakuScreen() {
//    ProvideCompositionLocalsForPreview { 
//        MatchingDanmakuScreen(
//            initialQuery = "Test",
//            uiState = MatchingDanmakuUIState()
//        )
//    }
//}
