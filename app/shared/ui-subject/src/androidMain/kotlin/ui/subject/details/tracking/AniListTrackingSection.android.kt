/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.ui.subject.details.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Sync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.ui.foundation.icons.AniListIcon
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SettingsTab
import me.him188.ani.app.tracking.anilist.AniListTrackingProvider
import me.him188.ani.app.tracking.anilist.createAniListHttpClient
import me.him188.ani.tracking.api.AndroidTrackingCredentialStore
import me.him188.ani.tracking.api.TrackingListEntry
import me.him188.ani.tracking.api.TrackingMedia
import me.him188.ani.tracking.api.TrackingMediaId
import me.him188.ani.tracking.api.TrackingMediaWithEntry
import me.him188.ani.tracking.api.TrackingProviderException
import me.him188.ani.tracking.api.TrackingProviderId
import me.him188.ani.tracking.api.TrackingScore
import me.him188.ani.tracking.api.TrackingStatus
import me.him188.ani.utils.ktor.getPlatformKtorEngine
import kotlin.math.roundToInt

@Composable
internal actual fun AniListTrackingSection(info: SubjectInfo, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { context.getSharedPreferences("anilist-bindings", 0) }
    val client = remember { createAniListHttpClient(getPlatformKtorEngine()) }
    DisposableEffect(client) { onDispose { client.close() } }
    val provider = remember(context, client) {
        AniListTrackingProvider(client, AndroidTrackingCredentialStore(context, TrackingProviderId("anilist")))
    }
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current
    var accountId by remember(info.subjectId) { mutableStateOf<String?>(null) }
    var linked by remember(info.subjectId) { mutableStateOf<TrackingMediaWithEntry?>(null) }
    var loading by remember(info.subjectId) { mutableStateOf(true) }
    var updating by remember(info.subjectId) { mutableStateOf(false) }
    var error by remember(info.subjectId) { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchBusy by remember { mutableStateOf(false) }
    var query by remember(info.subjectId) { mutableStateOf(info.name.ifBlank { info.nameCn }) }
    var results by remember { mutableStateOf<List<TrackingMedia>>(emptyList()) }
    var searchSelection by remember { mutableStateOf<TrackingMedia?>(null) }
    var selected by remember { mutableStateOf<TrackingMediaWithEntry?>(null) }
    var editing by remember { mutableStateOf<EditField?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingUnbind by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }

    fun bindingKey(id: String) = "$id:${info.subjectId}"

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                val account = provider.refreshAccount()
                accountId = account.remoteId
                val mediaId = preferences.getString(bindingKey(account.remoteId), null)
                linked = mediaId?.let { provider.refresh(TrackingMediaId(it)) }
                if (mediaId != null && linked == null) error = "AniList title is unavailable. Retry or unlink it."
            } catch (_: TrackingProviderException.Unauthorized) {
                accountId = null
                linked = null
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                error = "AniList could not be loaded. Retry."
            } finally {
                loading = false
            }
        }
    }

    fun search() {
        if (query.isBlank()) return
        scope.launch {
            searchBusy = true
            error = null
            searchSelection = null
            try {
                results = provider.search(query.trim())
                if (results.isEmpty()) error = "No AniList matches. Try another title."
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                error = "AniList search failed. Retry."
            } finally {
                searchBusy = false
            }
        }
    }

    LaunchedEffect(info.subjectId) { reload() }
    LaunchedEffect(searching) {
        if (searching) search()
    }

    TextButton(onClick = { showSheet = true }, modifier = modifier.testTag("trackingAction")) {
        Icon(
            if (linked == null) Icons.Outlined.Sync else Icons.Default.Check,
            contentDescription = null,
            tint = if (linked == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        )
        Text(if (linked == null) "Tracking" else "1 tracker")
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tracking", style = MaterialTheme.typography.titleLarge)
                if (updating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                when {
                    loading && accountId == null -> CircularProgressIndicator()
                    accountId == null -> {
                        Text("Connect AniList to track this anime.")
                        TextButton(onClick = { showSheet = false; navigator.navigateSettings(SettingsTab.TRACKING) }) {
                            Text("Tracking accounts")
                        }
                    }
                    loading && linked == null -> CircularProgressIndicator()
                    linked == null -> {
                        Row(Modifier.fillMaxWidth().clickable { searching = true; results = emptyList(); error = null }.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            AniListIcon()
                            Text("Add tracking", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    else -> {
                        val current = linked!!
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AniListIcon()
                            Column(Modifier.weight(1f).clickable {
                                query = current.media.title
                                results = emptyList()
                                searching = true
                            }) {
                                Text(current.media.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh tracking") }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Tracking options") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("Change match") }, onClick = {
                                        menuOpen = false
                                        query = current.media.title
                                        results = emptyList()
                                        searching = true
                                    })
                                    DropdownMenuItem(text = { Text("Unlink from Animeko") }, onClick = { menuOpen = false; confirmingUnbind = true })
                                    if (current.listEntry != null) {
                                        DropdownMenuItem(text = { Text("Delete AniList entry") }, onClick = { menuOpen = false; confirmingDelete = true })
                                    }
                                }
                            }
                        }
                        current.listEntry?.let { entry ->
                            val scoreLabel = provider.scoreOptions.firstOrNull { it.score == entry.score }?.displayValue ?: "${entry.score.value}/100"
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { error = null; editing = EditField.STATUS }, enabled = !updating) {
                                    Text(provider.statusOptions.first { it.status == entry.status }.displayName)
                                }
                                TextButton(onClick = { error = null; editing = EditField.PROGRESS }, enabled = !updating) {
                                    Text("${entry.progress}${current.media.totalEpisodes?.let { "/$it" } ?: ""} eps")
                                }
                                TextButton(onClick = { error = null; editing = EditField.SCORE }, enabled = !updating) { Text("Score $scoreLabel") }
                            }
                        } ?: run {
                            TextButton(onClick = { error = null; editing = EditField.STATUS }, enabled = !updating) { Text("Add AniList entry") }
                        }
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    if (!it.startsWith("AniList update failed")) {
                        TextButton(onClick = { reload() }) { Text("Retry") }
                    }
                }
            }
        }
    }

    if (searching) {
        Dialog(
            onDismissRequest = { searching = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(0.94f), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Find AniList anime", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { searching = false }) { Text("Close") }
                    }
                    OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                    TextButton(onClick = { search() }, enabled = query.isNotBlank() && !searchBusy) { Text("Search") }
                    if (searchBusy) CircularProgressIndicator()
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(results, key = { it.id.value }) { media ->
                            Surface(
                                onClick = { searchSelection = media },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                border = if (searchSelection?.id == media.id) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            ) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AsyncImage(media.coverImageUrl, null, Modifier.size(64.dp, 88.dp), contentScale = ContentScale.Crop)
                                    Column {
                                        Text(media.title, style = MaterialTheme.typography.titleMedium)
                                        Text("Anime${media.totalEpisodes?.let { " · $it episodes" } ?: ""}")
                                        if (searchSelection?.id == media.id) Text("Selected", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val media = searchSelection ?: return@Button
                            scope.launch {
                                loading = true
                                try {
                                    selected = provider.prepareBinding(media.id)
                                    searching = false
                                } catch (failure: CancellationException) {
                                    throw failure
                                } catch (_: Exception) {
                                    error = "Could not load this AniList title."
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = searchSelection != null && !loading,
                    ) { Text("Track") }
                }
            }
        }
    }

    selected?.let { candidate ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Link ${candidate.media.title}?") },
            text = {
                Column {
                    Text(candidate.listEntry?.let {
                        "AniList already has ${it.progress} episodes, ${provider.statusOptions.first { option -> option.status == it.status }.displayName.lowercase()}, and score ${it.score.value}/100. Keep that entry and link this Animeko title to it?"
                    } ?: "This title has no AniList entry. Linking will add it as Planning with zero progress and no score.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        loading = true
                        try {
                            val account = accountId ?: return@launch
                            val saved = if (candidate.listEntry == null) {
                                provider.bind(TrackingListEntry(candidate.media.id, TrackingStatus.PLANNING, 0))
                            } else candidate.listEntry
                            preferences.edit().putString(bindingKey(account), candidate.media.id.value).apply()
                            linked = candidate.copy(listEntry = saved)
                            selected = null
                            error = null
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (_: Exception) {
                            error = "Linking failed. Retry."
                        } finally {
                            loading = false
                        }
                    }
                }) { Text(if (candidate.listEntry == null) "Add and link" else "Keep and link") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } },
        )
    }

    if (editing != null && linked != null) {
        val current = linked!!
        EntryEditor(current, provider.statusOptions.map { it.status to it.displayName }, provider.scoreOptions.map { it.score to it.displayValue },
            field = editing!!,
            onDismiss = { editing = null },
            onSelect = { entry ->
                editing = null
                scope.launch {
                    updating = true
                    try {
                        val saved = if (current.listEntry == null) provider.bind(entry) else provider.update(entry)
                        linked = current.copy(listEntry = saved)
                        error = null
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        error = "AniList update failed. Choose a value to try again."
                    } finally {
                        updating = false
                    }
                }
            },
        )
    }

    if (confirmingUnbind) {
        AlertDialog(
            onDismissRequest = { confirmingUnbind = false },
            title = { Text("Unlink AniList title?") },
            text = { Text("This removes the link on this device. Your AniList entry stays online.") },
            confirmButton = { TextButton(onClick = {
                accountId?.let { preferences.edit().remove(bindingKey(it)).apply() }
                linked = null
                confirmingUnbind = false
            }) { Text("Unlink") } },
            dismissButton = { TextButton(onClick = { confirmingUnbind = false }) { Text("Cancel") } },
        )
    }
    if (confirmingDelete && linked != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete AniList entry?") },
            text = {
                Column {
                    Text("This deletes the list entry from AniList. The title will remain linked here.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    loading = true
                    try {
                        provider.delete(linked!!.media.id)
                        linked = provider.refresh(linked!!.media.id)
                        confirmingDelete = false
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        error = "AniList deletion failed. Retry."
                    } finally {
                        loading = false
                    }
                }
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

private enum class EditField { STATUS, PROGRESS, SCORE }

@Composable
private fun EntryEditor(
    current: TrackingMediaWithEntry,
    statuses: List<Pair<TrackingStatus, String>>,
    scores: List<Pair<TrackingScore, String>>,
    field: EditField,
    onDismiss: () -> Unit,
    onSelect: (TrackingListEntry) -> Unit,
) {
    val entry = current.listEntry ?: TrackingListEntry(current.media.id, TrackingStatus.PLANNING, 0)
    val maxProgress = maxOf(current.media.totalEpisodes ?: 10_000, entry.progress)
    val progressListState = rememberLazyListState(initialFirstVisibleItemIndex = (entry.progress - 2).coerceIn(0, maxProgress))
    var scoreIndex by remember(entry.score) { mutableIntStateOf(scores.indexOfFirst { it.first == entry.score }.coerceAtLeast(0)) }
    val statusListState = rememberLazyListState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (field) {
            EditField.STATUS -> "Watch status"
            EditField.PROGRESS -> "Episodes watched"
            EditField.SCORE -> "Score"
        }) },
        text = {
            Column {
                if (field == EditField.SCORE) {
                    Text(scores[scoreIndex].second, style = MaterialTheme.typography.headlineMedium)
                    Slider(
                        value = scoreIndex.toFloat(),
                        onValueChange = { scoreIndex = it.roundToInt().coerceIn(0, scores.lastIndex) },
                        onValueChangeFinished = { onSelect(entry.copy(score = scores[scoreIndex].first)) },
                        valueRange = 0f..scores.lastIndex.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().height(300.dp), state = if (field == EditField.PROGRESS) progressListState else statusListState) {
                        when (field) {
                            EditField.STATUS -> items(statuses.size) { index ->
                                val (option, label) = statuses[index]
                                TextButton(onClick = { onSelect(entry.copy(status = option)) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                            }
                            EditField.PROGRESS -> items(maxProgress + 1) { progress ->
                                TextButton(onClick = { onSelect(entry.copy(progress = progress)) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("$progress${current.media.totalEpisodes?.let { " / $it" } ?: ""} episodes")
                                }
                            }
                            EditField.SCORE -> Unit
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
