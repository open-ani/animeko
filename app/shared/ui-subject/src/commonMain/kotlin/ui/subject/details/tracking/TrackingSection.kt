package me.him188.ani.app.ui.subject.details.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SettingsTab
import me.him188.ani.app.ui.foundation.icons.AniListIcon
import me.him188.ani.app.ui.foundation.icons.BangumiNext
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingDate
import me.him188.ani.tracking.api.TrackingDateField
import me.him188.ani.tracking.api.TrackingEdit
import me.him188.ani.tracking.api.TrackingMedia
import me.him188.ani.tracking.api.TrackingMediaId
import me.him188.ani.tracking.api.TrackingProviderId
import me.him188.ani.tracking.api.TrackingRegistry
import me.him188.ani.tracking.api.TrackingScore
import me.him188.ani.tracking.api.TrackingSnapshot
import me.him188.ani.tracking.api.TrackingStatus
import kotlin.time.Instant

private enum class Field { STATUS, PROGRESS, SCORE, START_DATE, FINISH_DATE }

@Composable
internal fun TrackingSection(subjectId: Int, modifier: Modifier = Modifier) {
    val coordinator = remember { GlobalKoin.get<TrackingCoordinator>() }
    val cards by remember(coordinator, subjectId) { coordinator.observe(subjectId) }
        .collectAsStateWithLifecycle(emptyList())
    val titleRepository = remember { GlobalKoin.get<SubjectCollectionRepository>() }
    val subjectTitle by remember(subjectId) {
        titleRepository.subjectCollectionFlow(subjectId).map { it.subjectInfo.name.ifBlank { it.subjectInfo.nameCn } }
    }.collectAsStateWithLifecycle("")
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Pair<TrackingProviderId, Field>?>(null) }
    var searching by remember { mutableStateOf<TrackingProviderId?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TrackingMedia>>(emptyList()) }
    var busy by remember { mutableStateOf<TrackingProviderId?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<TrackingProviderId, Boolean>?>(null) }
    var markAllPrompt by remember { mutableStateOf<TrackingProviderId?>(null) }
    val connectedCards = cards.filter {
        it.account is TrackingAccountState.LoggedIn || it.account is TrackingAccountState.Refreshing
    }
    val trackedCount = connectedCards.count { it.isTracked }

    fun runEdit(id: TrackingProviderId, edit: TrackingEdit, onSuccess: () -> Unit = {}) {
        scope.launch {
            busy = id
            error = null
            try { coordinator.edit(subjectId, id, edit); onSuccess() }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { error = "Tracking update failed. Try again." }
            finally { busy = null }
        }
    }

    fun runSearch() {
        val id = searching ?: return
        if (query.isBlank()) return
        scope.launch {
            busy = id
            error = null
            try { results = coordinator.search(id, query.trim()) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { error = "Search failed. Try again." }
            finally { busy = null }
        }
    }

    if (trackedCount == 0) {
        Button(onClick = { showSheet = true }, modifier = modifier.testTag("trackingAction")) {
            Icon(Icons.Outlined.Sync, contentDescription = null)
            Text("Track")
        }
    } else {
        OutlinedButton(onClick = { showSheet = true }, modifier = modifier.testTag("trackingAction")) {
            Icon(Icons.Outlined.Sync, contentDescription = null)
            Text(if (trackedCount == 1) "1 tracker" else "$trackedCount trackers")
        }
    }

    if (showSheet) {
        LaunchedEffect(subjectId) { coordinator.refresh() }
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tracking", style = MaterialTheme.typography.titleLarge)
                connectedCards.forEach { card ->
                    TrackingCard(card, busy == card.providerId,
                        onField = { editor = card.providerId to it },
                        onSearch = {
                            searching = card.providerId
                            query = (card.load as? TrackingLoad.Ready)?.snapshot?.media?.title ?: subjectTitle
                            results = emptyList()
                            error = null
                        },
                        onRefresh = { coordinator.refresh() },
                        onAdd = { mediaId ->
                            scope.launch {
                                busy = card.providerId
                                try { coordinator.bind(subjectId, card.providerId, mediaId); error = null }
                                catch (failure: CancellationException) { throw failure }
                                catch (_: Exception) { error = "Tracking could not be added. Try again." }
                                finally { busy = null }
                            }
                        },
                        onOpen = { uriHandler.openUri(it) },
                        onUnlink = { confirm = card.providerId to false },
                        onDelete = { confirm = card.providerId to true },
                        onPrivacy = { runEdit(card.providerId, TrackingEdit.Privacy(it)) },
                    )
                }
                if (connectedCards.isEmpty()) Text("No tracking accounts connected.")
                TextButton(onClick = { showSheet = false; navigator.navigateSettings(SettingsTab.TRACKING) }) {
                    Text("Tracking accounts")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    searching?.let { id ->
        Dialog(onDismissRequest = { searching = null }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Find tracking title", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(query, { query = it; results = emptyList(); error = null },
                            modifier = Modifier.weight(1f), placeholder = { Text("Search anime") }, singleLine = true,
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { query = ""; results = emptyList(); error = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (busy == null) runSearch() }),
                        )
                        IconButton(onClick = { runSearch() }, enabled = busy == null && query.isNotBlank()) {
                            Icon(Icons.Default.Search, contentDescription = "Search tracking titles")
                        }
                    }
                    if (busy == id) CircularProgressIndicator(Modifier.size(20.dp))
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(results, key = { it.id.value }) { media ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = busy == null) {
                                scope.launch {
                                    busy = id
                                    try { coordinator.bind(subjectId, id, media.id); searching = null; error = null }
                                    catch (failure: CancellationException) { throw failure }
                                    catch (_: Exception) { error = "Linking failed. Try again." }
                                    finally { busy = null }
                                }
                            }.padding(12.dp)) {
                                Text(media.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    editor?.let { (id, field) ->
        val card = cards.firstOrNull { it.providerId == id }
        val snapshot = (card?.load as? TrackingLoad.Ready)?.snapshot
        val entry = snapshot?.entry
        if (card != null && snapshot != null && entry != null) {
            if (field == Field.START_DATE || field == Field.FINISH_DATE) {
                val dateField = if (field == Field.START_DATE) TrackingDateField.STARTED else TrackingDateField.COMPLETED
                TrackingDateEditor(if (field == Field.START_DATE) "Start date" else "Finish date",
                    if (field == Field.START_DATE) entry.startedAt else entry.completedAt,
                    onDismiss = { editor = null }, onSelect = { date ->
                        editor = null
                        runEdit(id, TrackingEdit.Date(dateField, date))
                    })
            } else {
                TrackingChoiceDialog(card, snapshot, field, onDismiss = { editor = null }) { edit ->
                    editor = null
                    runEdit(id, edit) {
                        if (edit == TrackingEdit.Status(TrackingStatus.COMPLETED) && snapshot.episodes?.any { !it.watched } == true) {
                            markAllPrompt = id
                        }
                    }
                }
            }
        } else editor = null
    }

    confirm?.let { (id, deleteRemote) ->
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text(if (deleteRemote) "Delete tracking entry?" else "Unlink tracking title?") },
            text = { Text(if (deleteRemote) "This deletes the remote list entry." else "The remote list entry stays online.") },
            confirmButton = { TextButton(onClick = {
                confirm = null
                if (deleteRemote) runEdit(id, TrackingEdit.DeleteRemoteEntry)
                else scope.launch {
                    try { coordinator.unlink(subjectId, id) }
                    catch (failure: CancellationException) { throw failure }
                    catch (_: Exception) { error = "Unlink failed. Try again." }
                }
            }) { Text(if (deleteRemote) "Delete" else "Unlink") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }

    markAllPrompt?.let { id ->
        AlertDialog(onDismissRequest = { markAllPrompt = null },
            title = { Text("Mark all episodes watched?") },
            text = { Text("The completed status is saved. You can also mark every episode watched.") },
            confirmButton = { TextButton(onClick = {
                markAllPrompt = null
                runEdit(id, TrackingEdit.MarkAllEpisodesWatched)
            }) { Text("Mark all") } },
            dismissButton = { TextButton(onClick = { markAllPrompt = null }) { Text("Keep episodes") } },
        )
    }
}

@Composable
private fun TrackingCard(
    card: TrackingCardModel,
    updating: Boolean,
    onField: (Field) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: (TrackingMediaId) -> Unit,
    onOpen: (String) -> Unit,
    onUnlink: () -> Unit,
    onDelete: () -> Unit,
    onPrivacy: (Boolean) -> Unit,
) {
    val snapshot = (card.load as? TrackingLoad.Ready)?.snapshot
    val entry = snapshot?.entry
    var menuOpen by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrackingBrandIcon(card.iconKey)
                Column(Modifier.weight(1f)) {
                    Text(card.providerName, style = MaterialTheme.typography.titleMedium)
                    Text(snapshot?.media?.title ?: if (card.capabilities.needsMatchSearch) "Add tracking" else "Collection",
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (card.load is TrackingLoad.Loading || updating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Tracking options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        snapshot?.media?.siteUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            DropdownMenuItem(text = { Text("Open on ${card.providerName}") }, onClick = { menuOpen = false; onOpen(url) })
                        }
                        DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; onRefresh() })
                        if (card.capabilities.needsMatchSearch) {
                            DropdownMenuItem(text = { Text("Change match") }, onClick = { menuOpen = false; onSearch() })
                            if (snapshot != null) DropdownMenuItem(text = { Text("Unlink") }, onClick = { menuOpen = false; onUnlink() })
                        }
                        if (entry != null && card.capabilities.canEditPrivacy) {
                            DropdownMenuItem(text = { Text(if (entry.isPrivate) "Make public" else "Make private") },
                                onClick = { menuOpen = false; onPrivacy(!entry.isPrivate) })
                        }
                        if (entry != null && card.capabilities.canDeleteRemoteEntry) {
                            DropdownMenuItem(text = { Text("Delete remote entry") }, onClick = { menuOpen = false; onDelete() })
                        }
                    }
                }
            }
            when {
                card.load is TrackingLoad.Failed -> Text((card.load as TrackingLoad.Failed).message, color = MaterialTheme.colorScheme.error)
                snapshot == null && card.capabilities.needsMatchSearch -> TextButton(onClick = onSearch) { Icon(Icons.Default.Add, null); Text("Add tracking") }
                entry == null -> TextButton(onClick = { snapshot?.media?.id?.let(onAdd) ?: onSearch() }) {
                    Text("Add tracking")
                }
                else -> {
                    Row(Modifier.fillMaxWidth()) {
                        if (card.capabilities.canEditStatus) TrackingFieldCell(
                            card.statusOptions.firstOrNull { it.status == entry.status }?.displayName ?: entry.status.name,
                            Modifier.weight(1f), !updating) { onField(Field.STATUS) }
                        if (card.capabilities.canEditProgress) TrackingFieldCell(
                            "${entry.progress}${snapshot.media.totalEpisodes?.let { " / $it" } ?: ""}",
                            Modifier.weight(1f), !updating) { onField(Field.PROGRESS) }
                        if (card.capabilities.canEditScore) TrackingFieldCell(
                            if (entry.score.value == 0) "Score" else "${(entry.score.value + 5) / 10} / 10",
                            Modifier.weight(1f), !updating) { onField(Field.SCORE) }
                    }
                    if (card.capabilities.canEditDates) Row(Modifier.fillMaxWidth()) {
                        TrackingFieldCell(entry.startedAt.dateLabel("Start date"), Modifier.weight(1f), !updating) { onField(Field.START_DATE) }
                        TrackingFieldCell(entry.completedAt.dateLabel("Finish date"), Modifier.weight(1f), !updating) { onField(Field.FINISH_DATE) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingBrandIcon(iconKey: String) {
    when (iconKey) {
        "anilist" -> AniListIcon()
        "bangumi" -> androidx.compose.foundation.Image(Icons.Default.BangumiNext, null, Modifier.size(32.dp))
        else -> Text(iconKey.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun TrackingFieldCell(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(modifier.height(48.dp).clickable(enabled = enabled, onClick = onClick)) {
        Text(label, Modifier.align(Alignment.Center), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TrackingChoiceDialog(card: TrackingCardModel, snapshot: TrackingSnapshot, field: Field,
    onDismiss: () -> Unit, onSelect: (TrackingEdit) -> Unit) {
    val entry = checkNotNull(snapshot.entry)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (field) { Field.STATUS -> "Watch status"; Field.PROGRESS -> "Episodes watched"; else -> "Score" },
                    style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    when (field) {
                        Field.STATUS -> items(card.statusOptions) { option ->
                            TrackingChoice(option.displayName, entry.status == option.status) { onSelect(TrackingEdit.Status(option.status)) }
                        }
                        Field.PROGRESS -> {
                            val episodes = snapshot.episodes
                            if (episodes != null) items(episodes) { episode ->
                                TrackingChoice("Episode ${episode.number}", episode.watched) {
                                    onSelect(TrackingEdit.Episode(episode.id, !episode.watched))
                                }
                            } else items((0..(snapshot.media.totalEpisodes ?: maxOf(entry.progress + 20, 100))).toList()) { count ->
                                TrackingChoice(count.toString(), entry.progress == count) { onSelect(TrackingEdit.Progress(count)) }
                            }
                        }
                        Field.SCORE -> {
                            items((10 downTo 1).toList()) { score ->
                                TrackingChoice(score.toString(), (entry.score.value + 5) / 10 == score) {
                                    onSelect(TrackingEdit.Score(TrackingScore(score * 10)))
                                }
                            }
                            item { TrackingChoice("Clear score", entry.score == TrackingScore.Unrated) {
                                onSelect(TrackingEdit.Score(TrackingScore.Unrated))
                            } }
                        }
                        else -> Unit
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun TrackingChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Text(label, Modifier.fillMaxWidth().padding(12.dp))
    }
}

private fun TrackingDate?.dateLabel(placeholder: String): String =
    this?.let { listOfNotNull(year?.toString(), month?.toString()?.padStart(2, '0'), day?.toString()?.padStart(2, '0'))
        .joinToString("-").ifEmpty { placeholder } } ?: placeholder

@Composable
private fun TrackingDateEditor(title: String, date: TrackingDate?, onDismiss: () -> Unit, onSelect: (TrackingDate?) -> Unit) {
    val initial = remember(date) {
        runCatching { LocalDate(requireNotNull(date?.year), requireNotNull(date.month), requireNotNull(date.day))
            .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }.getOrNull()
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            val selected = state.selectedDateMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
            onSelect(selected?.let { TrackingDate(it.year, it.monthNumber, it.dayOfMonth) })
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) { DatePicker(state) }
}
