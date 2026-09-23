/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.ui.subject.details.tracking

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import me.him188.ani.app.ui.foundation.icons.BangumiNext
import androidx.compose.material.icons.outlined.Sync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.ui.foundation.icons.AniListIcon
import me.him188.ani.app.ui.foundation.icons.Animeko
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SettingsTab
import me.him188.ani.app.tracking.anilist.AniListTrackingProvider
import me.him188.ani.app.tracking.anilist.createAniListHttpClient
import me.him188.ani.tracking.api.AndroidTrackingCredentialStore
import me.him188.ani.tracking.api.TrackingListEntry
import me.him188.ani.tracking.api.TrackingDate
import me.him188.ani.tracking.api.TrackingDateField
import me.him188.ani.tracking.api.TrackingMedia
import me.him188.ani.tracking.api.TrackingMediaId
import me.him188.ani.tracking.api.TrackingMediaWithEntry
import me.him188.ani.tracking.api.TrackingProviderException
import me.him188.ani.tracking.api.TrackingProviderId
import me.him188.ani.tracking.api.TrackingScore
import me.him188.ani.tracking.api.TrackingStatus
import me.him188.ani.utils.ktor.getPlatformKtorEngine
import kotlin.math.roundToInt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
internal actual fun AniListTrackingSection(
    info: SubjectInfo,
    showCollection: Boolean,
    bangumiConnected: Boolean,
    collectionAction: @Composable () -> Unit,
    modifier: Modifier,
) {
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
    var linkingMediaId by remember { mutableStateOf<TrackingMediaId?>(null) }
    var editing by remember { mutableStateOf<EditField?>(null) }
    var editingDate by remember { mutableStateOf<TrackingDateField?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingUnbind by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    fun link(media: TrackingMedia) {
        if (linkingMediaId != null) return
        scope.launch {
            linkingMediaId = media.id
            error = null
            try {
                val account = accountId ?: throw TrackingProviderException.Unauthorized()
                val candidate = provider.prepareBinding(media.id)
                    ?: throw IllegalStateException("AniList title is unavailable")
                val entry = candidate.listEntry
                    ?: provider.bind(TrackingListEntry(candidate.media.id, TrackingStatus.PLANNING, 0))
                preferences.edit().putString(bindingKey(account), candidate.media.id.value).apply()
                linked = candidate.copy(listEntry = entry)
                searching = false
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                error = "Linking failed. Tap the title to try again."
            } finally {
                linkingMediaId = null
            }
        }
    }

    LaunchedEffect(info.subjectId, showSheet) { if (showSheet) reload() }
    LaunchedEffect(searching) {
        if (searching) search()
    }

    OutlinedButton(onClick = { showSheet = true }, modifier = modifier.testTag("trackingAction")) {
        Icon(Icons.Outlined.Sync, contentDescription = null)
        Text("Track")
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tracking", style = MaterialTheme.typography.titleLarge)
                if (showCollection) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (bangumiConnected) {
                                Image(Icons.Default.BangumiNext, contentDescription = null, modifier = Modifier.size(32.dp))
                            }
                            Text(if (bangumiConnected) "Bangumi" else "Collection", style = MaterialTheme.typography.titleMedium)
                        }
                        collectionAction()
                    }
                }
                if (updating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                when {
                    loading -> CircularProgressIndicator()
                    accountId == null -> {
                        if (!showCollection) Text("No tracking accounts connected.")
                        TextButton(onClick = { showSheet = false; navigator.navigateSettings(SettingsTab.TRACKING) }) {
                            Text("Tracking accounts")
                        }
                    }
                    linked == null -> {
                        Row(Modifier.fillMaxWidth().clickable { searching = true; results = emptyList(); error = null }.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                AniListIcon()
                                Text("AniList · Add tracking", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    else -> {
                        val current = linked!!
                        Text("AniList", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AniListIcon()
                            Column(Modifier.weight(1f).clickable {
                                query = current.media.title
                                results = emptyList()
                                searching = true
                            }) {
                                Text(current.media.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (current.listEntry?.isPrivate == true) {
                                    Text("Private", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Tracking options") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("Open on AniList") }, onClick = {
                                        menuOpen = false
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.media.siteUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    })
                                    DropdownMenuItem(text = { Text("Copy AniList link") }, onClick = {
                                        menuOpen = false
                                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                            .setPrimaryClip(ClipData.newPlainText("AniList", current.media.siteUrl))
                                    })
                                    DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; reload() })
                                    DropdownMenuItem(text = { Text("Change match") }, onClick = {
                                        menuOpen = false
                                        query = current.media.title
                                        results = emptyList()
                                        searching = true
                                    })
                                    DropdownMenuItem(text = { Text("Unlink from Animeko") }, onClick = { menuOpen = false; confirmingUnbind = true })
                                    current.listEntry?.let { entry ->
                                        DropdownMenuItem(text = { Text(if (entry.isPrivate) "Make public" else "Make private") },
                                            enabled = !updating,
                                            onClick = {
                                                menuOpen = false
                                                scope.launch {
                                                    updating = true
                                                    try {
                                                        linked = current.copy(listEntry = provider.updateVisibility(entry, !entry.isPrivate))
                                                        error = null
                                                    } catch (failure: CancellationException) {
                                                        throw failure
                                                    } catch (_: Exception) {
                                                        error = "AniList update failed. Try again from options."
                                                    } finally {
                                                        updating = false
                                                    }
                                                }
                                            })
                                        DropdownMenuItem(text = { Text("Delete AniList entry") }, onClick = { menuOpen = false; confirmingDelete = true })
                                    }
                                }
                            }
                        }
                        current.listEntry?.let { entry ->
                            val scoreLabel = if (entry.score == TrackingScore.Unrated) "Score"
                                else "${(entry.score.value / 10f).roundToInt().coerceIn(1, 10)} / 10"
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Column {
                                    Row(Modifier.fillMaxWidth()) {
                                        TrackingCell(provider.statusOptions.first { it.status == entry.status }.displayName,
                                            Modifier.weight(1f), !updating) { error = null; editing = EditField.STATUS }
                                        TrackingCell("${entry.progress}${current.media.totalEpisodes?.let { " / $it" } ?: ""}",
                                            Modifier.weight(1f), !updating) { error = null; editing = EditField.PROGRESS }
                                        TrackingCell(if (entry.score.value == 0) "Score" else scoreLabel,
                                            Modifier.weight(1f), !updating) { error = null; editing = EditField.SCORE }
                                    }
                                    if (provider.capabilities.supportsStartAndCompletionDates) {
                                        HorizontalDivider()
                                        Row(Modifier.fillMaxWidth()) {
                                            TrackingCell(entry.startedAt.displayOr("Start date"), Modifier.weight(1f), !updating) {
                                                error = null; editingDate = TrackingDateField.STARTED
                                            }
                                            TrackingCell(entry.completedAt.displayOr("Finish date"), Modifier.weight(1f), !updating) {
                                                error = null; editingDate = TrackingDateField.COMPLETED
                                            }
                                        }
                                    }
                                }
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
                    TextButton(onClick = { search() }, enabled = query.isNotBlank() && !searchBusy && linkingMediaId == null) { Text("Search") }
                    if (searchBusy) CircularProgressIndicator()
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(results, key = { it.id.value }) { media ->
                            Surface(
                                onClick = { link(media) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                enabled = linkingMediaId == null,
                            ) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AsyncImage(media.coverImageUrl, null, Modifier.size(64.dp, 88.dp), contentScale = ContentScale.Crop)
                                    Column {
                                        Text(media.title, style = MaterialTheme.typography.titleMedium)
                                        Text("Anime${media.totalEpisodes?.let { " · $it episodes" } ?: ""}")
                                        if (linkingMediaId == media.id) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editing != null && linked != null) {
        val current = linked!!
        key(current.media.id, editing, current.listEntry?.progress, current.listEntry?.score) {
            EntryEditor(current, provider.statusOptions.map { it.status to it.displayName },
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
    }

    editingDate?.let { field ->
        val current = linked?.listEntry
        if (current != null) {
            val date = if (field == TrackingDateField.STARTED) current.startedAt else current.completedAt
            TrackingDateEditor(
                title = if (field == TrackingDateField.STARTED) "Start date" else "Finish date",
                date = date,
                onDismiss = { editingDate = null },
                onSelect = { selectedDate ->
                    editingDate = null
                    scope.launch {
                        updating = true
                        try {
                            val saved = provider.updateDate(current, field, selectedDate)
                            linked = linked?.copy(listEntry = saved)
                            error = null
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (_: Exception) {
                            error = "AniList update failed. Choose a date to try again."
                        } finally {
                            updating = false
                        }
                    }
                },
            )
        }
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
private fun TrackingCell(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(modifier.clickable(enabled = enabled, onClick = onClick).height(48.dp).padding(horizontal = 8.dp)) {
        Text(label, modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun TrackingDate?.displayOr(placeholder: String): String {
    if (this == null) return placeholder
    return listOfNotNull(year?.toString(), month?.toString()?.padStart(2, '0'), day?.toString()?.padStart(2, '0'))
        .joinToString("-").ifEmpty { placeholder }
}

@Composable
private fun TrackingDateEditor(title: String, date: TrackingDate?, onDismiss: () -> Unit, onSelect: (TrackingDate?) -> Unit) {
    val initialDateMillis = remember(date) {
        runCatching {
            LocalDate.of(requireNotNull(date?.year), requireNotNull(date.month), requireNotNull(date.day))
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    }
    val picker = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let {
                    val selected = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    onSelect(TrackingDate(selected.year, selected.monthValue, selected.dayOfMonth))
                }
            }, enabled = picker.selectedDateMillis != null) { Text("OK") }
        },
        dismissButton = {
            Row {
                if (date != null) TextButton(onClick = { onSelect(null) }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) {
        Column {
            Text(title, Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.titleLarge)
            DatePicker(state = picker, title = null, headline = null, showModeToggle = false)
        }
    }
}

@Composable
private fun EntryEditor(
    current: TrackingMediaWithEntry,
    statuses: List<Pair<TrackingStatus, String>>,
    field: EditField,
    onDismiss: () -> Unit,
    onSelect: (TrackingListEntry) -> Unit,
) {
    val entry = current.listEntry ?: TrackingListEntry(current.media.id, TrackingStatus.PLANNING, 0)
    val maxProgress = maxOf(current.media.totalEpisodes ?: 10_000, entry.progress)
    val progressListState = rememberLazyListState(initialFirstVisibleItemIndex = (entry.progress - 2).coerceIn(0, maxProgress))
    val scoreListState = rememberLazyListState(initialFirstVisibleItemIndex = if (entry.score == TrackingScore.Unrated) 0
        else (10 - (entry.score.value / 10f).roundToInt() - 2).coerceIn(0, 9))
    val statusListState = rememberLazyListState()
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(when (field) {
                    EditField.STATUS -> "Watch status"
                    EditField.PROGRESS -> "Episodes watched"
                    EditField.SCORE -> "Score"
                }, style = MaterialTheme.typography.titleLarge)
                if (field == EditField.SCORE) {
                    LazyColumn(Modifier.fillMaxWidth().height(240.dp), state = scoreListState,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(10) { index ->
                            val score = 10 - index
                            PickerChoice(
                                label = score.toString(),
                                selected = entry.score.value != 0 && (entry.score.value / 10f).roundToInt().coerceIn(1, 10) == score,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onSelect(entry.copy(score = TrackingScore(score * 10))) },
                            )
                        }
                        if (entry.score.value != 0) {
                            item {
                                PickerChoice("Clear score", false, Modifier.fillMaxWidth()) {
                                    onSelect(entry.copy(score = TrackingScore.Unrated))
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().height(240.dp),
                        state = if (field == EditField.PROGRESS) progressListState else statusListState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        when (field) {
                            EditField.STATUS -> items(statuses.size) { index ->
                                val (option, label) = statuses[index]
                                PickerChoice(label, entry.status == option, Modifier.fillMaxWidth()) {
                                    onSelect(entry.copy(status = option))
                                }
                            }
                            EditField.PROGRESS -> items(maxProgress + 1) { progress ->
                                PickerChoice(progress.toString(), progress == entry.progress, Modifier.fillMaxWidth()) {
                                    onSelect(entry.copy(progress = progress))
                                }
                            }
                            EditField.SCORE -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.shapes.small)
            .selectable(selected = selected, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
    }
}
