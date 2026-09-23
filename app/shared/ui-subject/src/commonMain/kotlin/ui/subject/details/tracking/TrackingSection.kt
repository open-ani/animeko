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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
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
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.preferredDisplayName
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SettingsTab
import me.him188.ani.app.ui.foundation.icons.TrackingIconRegistry
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.LocalSubjectAppearanceSettings
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
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private enum class Field { STATUS, PROGRESS, SCORE, START_DATE, FINISH_DATE }

@Composable
internal fun TrackingSection(subjectId: Int, modifier: Modifier = Modifier, onClickLogin: (() -> Unit)? = null) {
    val coordinator = remember { GlobalKoin.get<TrackingCoordinator>() }
    val cards by remember(coordinator, subjectId) { coordinator.observe(subjectId) }
        .collectAsStateWithLifecycle(emptyList())
    val titleRepository = remember { GlobalKoin.get<SubjectCollectionRepository>() }
    val subjectInfo by remember(subjectId) {
        titleRepository.subjectCollectionFlow(subjectId).map { it.subjectInfo }
    }.collectAsStateWithLifecycle(null as SubjectInfo?)
    val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
    val preferredTitle = subjectInfo?.preferredDisplayName(useOriginalTitle).orEmpty()
    val originalTitle = subjectInfo?.name.orEmpty()
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Pair<TrackingProviderId, Field>?>(null) }
    var searching by remember { mutableStateOf<TrackingProviderId?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TrackingMedia>>(emptyList()) }
    var searchRevision by remember { mutableStateOf(0) }
    var initialSearchTitle by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<TrackingProviderId?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<TrackingProviderId, Boolean>?>(null) }
    var markAllPrompt by remember { mutableStateOf<TrackingProviderId?>(null) }
    val connectedCards = cards.filter {
        it.account is TrackingAccountState.LoggedIn || it.account is TrackingAccountState.Refreshing
    }
    val trackedCount = connectedCards.count { it.isTracked }

    val updateFailedText = stringResource(Lang.tracking_error_update_failed)
    val searchFailedText = stringResource(Lang.tracking_error_search_failed)
    val addFailedText = stringResource(Lang.tracking_error_add_failed)
    val linkFailedText = stringResource(Lang.tracking_error_link_failed)
    val unlinkFailedText = stringResource(Lang.tracking_error_unlink_failed)
    val startDateTitle = stringResource(Lang.tracking_field_start_date)
    val finishDateTitle = stringResource(Lang.tracking_field_finish_date)

    fun runEdit(id: TrackingProviderId, edit: TrackingEdit, onSuccess: () -> Unit = {}) {
        scope.launch {
            busy = id
            error = null
            try { coordinator.edit(subjectId, id, edit); onSuccess() }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { error = updateFailedText }
            finally { busy = null }
        }
    }

    fun runSearch() {
        val id = searching ?: return
        if (query.isBlank()) return
        val requestedQuery = query.trim()
        val requestedRevision = searchRevision
        val shouldFallback = requestedQuery == initialSearchTitle && originalTitle.isNotBlank() && originalTitle != initialSearchTitle
        scope.launch {
            busy = id
            error = null
            try {
                val found = coordinator.search(id, requestedQuery)
                if (requestedRevision != searchRevision || searching != id) return@launch
                val matches = if (found.isEmpty() && shouldFallback) {
                    coordinator.search(id, originalTitle)
                } else found
                if (requestedRevision == searchRevision && searching == id) results = matches
            }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) {
                if (requestedRevision == searchRevision && searching == id) error = searchFailedText
            }
            finally { busy = null }
        }
    }

    if (trackedCount == 0) {
        Button(onClick = { showSheet = true }, modifier = modifier.testTag("trackingAction")) {
            Icon(Icons.Outlined.Sync, contentDescription = null)
            Text(stringResource(Lang.tracking_action_track))
        }
    } else {
        OutlinedButton(onClick = { showSheet = true }, modifier = modifier.testTag("trackingAction")) {
            Icon(Icons.Outlined.Sync, contentDescription = null)
            Text(
                if (trackedCount == 1) stringResource(Lang.tracking_action_tracked_one, trackedCount)
                else stringResource(Lang.tracking_action_tracked_many, trackedCount)
            )
        }
    }

    if (showSheet) {
        LaunchedEffect(subjectId) { coordinator.refresh() }
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Lang.tracking_sheet_title), style = MaterialTheme.typography.titleLarge)
                connectedCards.forEach { card ->
                    TrackingCard(card, busy == card.providerId,
                        onField = { editor = card.providerId to it },
                        onSearch = {
                            searchRevision++
                            searching = card.providerId
                            query = preferredTitle.ifBlank { (card.load as? TrackingLoad.Ready)?.snapshot?.media?.title.orEmpty() }
                            initialSearchTitle = query
                            results = emptyList()
                            error = null
                            runSearch()
                        },
                        onRefresh = { coordinator.refresh() },
                        onAdd = { mediaId ->
                            scope.launch {
                                busy = card.providerId
                                try { coordinator.bind(subjectId, card.providerId, mediaId); error = null }
                                catch (failure: CancellationException) { throw failure }
                                catch (_: Exception) { error = addFailedText }
                                finally { busy = null }
                            }
                        },
                        onOpen = { uriHandler.openUri(it) },
                        onUnlink = { confirm = card.providerId to false },
                        onDelete = { confirm = card.providerId to true },
                        onPrivacy = { runEdit(card.providerId, TrackingEdit.Privacy(it)) },
                    )
                }
                if (connectedCards.isEmpty()) {
                    Text(stringResource(Lang.tracking_no_accounts_connected))
                }
                if (onClickLogin != null) TextButton(onClick = { showSheet = false; onClickLogin() }) {
                    Text(stringResource(Lang.tracking_sign_in_animeko))
                }
                TextButton(onClick = { showSheet = false; navigator.navigateSettings(SettingsTab.TRACKING) }) {
                    Text(stringResource(Lang.tracking_manage_accounts))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    searching?.let { id ->
        Dialog(onDismissRequest = { searchRevision++; searching = null }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Lang.tracking_search_dialog_title), style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(query, { searchRevision++; query = it; results = emptyList(); error = null },
                            modifier = Modifier.weight(1f), placeholder = { Text(stringResource(Lang.tracking_search_placeholder)) }, singleLine = true,
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { searchRevision++; query = ""; results = emptyList(); error = null }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(Lang.tracking_search_clear_description))
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (busy == null) runSearch() }),
                        )
                        IconButton(onClick = { runSearch() }, enabled = busy == null && query.isNotBlank()) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(Lang.tracking_search_icon_description))
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
                                    catch (_: Exception) { error = linkFailedText }
                                    finally { busy = null }
                                }
                            }.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                media.coverImageUrl?.takeIf { it.isNotBlank() }?.let { cover ->
                                    AsyncImage(cover, contentDescription = null, modifier = Modifier.width(44.dp).height(62.dp),
                                        contentScale = ContentScale.Crop)
                                }
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
                TrackingDateEditor(if (field == Field.START_DATE) startDateTitle else finishDateTitle,
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
            title = {
                Text(
                    if (deleteRemote) stringResource(Lang.tracking_confirm_delete_title)
                    else stringResource(Lang.tracking_confirm_unlink_title)
                )
            },
            text = {
                Text(
                    if (deleteRemote) stringResource(Lang.tracking_confirm_delete_message)
                    else stringResource(Lang.tracking_confirm_unlink_message)
                )
            },
            confirmButton = { TextButton(onClick = {
                confirm = null
                if (deleteRemote) runEdit(id, TrackingEdit.DeleteRemoteEntry)
                else scope.launch {
                    try { coordinator.unlink(subjectId, id) }
                    catch (failure: CancellationException) { throw failure }
                    catch (_: Exception) { error = unlinkFailedText }
                }
            }) {
                Text(
                    if (deleteRemote) stringResource(Lang.tracking_action_delete)
                    else stringResource(Lang.tracking_menu_unlink)
                )
            } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(Lang.subject_collection_cancel)) } },
        )
    }

    markAllPrompt?.let { id ->
        AlertDialog(onDismissRequest = { markAllPrompt = null },
            title = { Text(stringResource(Lang.tracking_confirm_mark_all_title)) },
            text = { Text(stringResource(Lang.tracking_confirm_mark_all_message)) },
            confirmButton = { TextButton(onClick = {
                markAllPrompt = null
                runEdit(id, TrackingEdit.MarkAllEpisodesWatched)
            }) { Text(stringResource(Lang.tracking_confirm_mark_all_action)) } },
            dismissButton = { TextButton(onClick = { markAllPrompt = null }) { Text(stringResource(Lang.tracking_confirm_keep_episodes_action)) } },
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
                val icon = remember(card.providerId) {
                    GlobalKoin.get<TrackingIconRegistry>().find(card.providerId)
                }
                if (icon != null) icon.Icon() else Text(card.providerName.take(1), style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f)) {
                    Text(card.providerName, style = MaterialTheme.typography.titleMedium)
                    Text(snapshot?.media?.title ?: if (card.capabilities.needsMatchSearch) stringResource(Lang.tracking_card_not_matched) else stringResource(Lang.tracking_card_collection),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (card.load is TrackingLoad.Loading || updating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(Lang.tracking_options_menu_description)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        snapshot?.media?.siteUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            DropdownMenuItem(text = { Text(stringResource(Lang.tracking_menu_open_on_provider, card.providerName)) }, onClick = { menuOpen = false; onOpen(url) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(Lang.tracking_menu_refresh)) }, onClick = { menuOpen = false; onRefresh() })
                        if (card.capabilities.needsMatchSearch) {
                            DropdownMenuItem(text = { Text(stringResource(Lang.tracking_menu_change_match)) }, onClick = { menuOpen = false; onSearch() })
                            if (snapshot != null) DropdownMenuItem(text = { Text(stringResource(Lang.tracking_menu_unlink)) }, onClick = { menuOpen = false; onUnlink() })
                        }
                        if (entry != null && card.capabilities.canEditPrivacy) {
                            DropdownMenuItem(text = { Text(if (entry.isPrivate) stringResource(Lang.tracking_menu_make_public) else stringResource(Lang.tracking_menu_make_private)) },
                                onClick = { menuOpen = false; onPrivacy(!entry.isPrivate) })
                        }
                        if (entry != null && card.capabilities.canDeleteRemoteEntry) {
                            DropdownMenuItem(text = { Text(stringResource(Lang.tracking_menu_delete_remote)) }, onClick = { menuOpen = false; onDelete() })
                        }
                    }
                }
            }
            when {
                card.load is TrackingLoad.Failed ->
                    Text(stringResource(Lang.tracking_error_load_failed), color = MaterialTheme.colorScheme.error)
                snapshot == null && card.capabilities.needsMatchSearch -> TextButton(onClick = onSearch) { Icon(Icons.Default.Add, null); Text(stringResource(Lang.tracking_card_add_tracking)) }
                entry == null -> TextButton(onClick = { snapshot?.media?.id?.let(onAdd) ?: onSearch() }) {
                    Text(stringResource(Lang.tracking_card_add_tracking))
                }
                else -> {
                    Row(Modifier.fillMaxWidth()) {
                        if (card.capabilities.canEditStatus) TrackingFieldCell(
                            entry.status?.displayLabel() ?: stringResource(Lang.tracking_field_status),
                            Modifier.weight(1f), !updating) { onField(Field.STATUS) }
                        if (card.capabilities.canEditProgress) TrackingFieldCell(
                            "${entry.progress}${snapshot.media.totalEpisodes?.let { " / $it" } ?: ""}",
                            Modifier.weight(1f), !updating) { onField(Field.PROGRESS) }
                        if (card.capabilities.canEditScore) TrackingFieldCell(
                            if (entry.score.value == 0) stringResource(Lang.tracking_field_score) else "${(entry.score.value + 5) / 10} / 10",
                            Modifier.weight(1f), !updating) { onField(Field.SCORE) }
                    }
                    if (card.capabilities.canEditDates) Row(Modifier.fillMaxWidth()) {
                        TrackingFieldCell(entry.startedAt.dateLabel(stringResource(Lang.tracking_field_start_date)), Modifier.weight(1f), !updating) { onField(Field.START_DATE) }
                        TrackingFieldCell(entry.completedAt.dateLabel(stringResource(Lang.tracking_field_finish_date)), Modifier.weight(1f), !updating) { onField(Field.FINISH_DATE) }
                    }
                }
            }
        }
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
private fun TrackingStatus.displayLabel(): String = when (this) {
    TrackingStatus.PLANNING -> stringResource(Lang.tracking_status_planning)
    TrackingStatus.CURRENT -> stringResource(Lang.tracking_status_current)
    TrackingStatus.COMPLETED -> stringResource(Lang.tracking_status_completed)
    TrackingStatus.PAUSED -> stringResource(Lang.tracking_status_paused)
    TrackingStatus.DROPPED -> stringResource(Lang.tracking_status_dropped)
    TrackingStatus.REPEATING -> stringResource(Lang.tracking_status_repeating)
}

@Composable
private fun TrackingChoiceDialog(card: TrackingCardModel, snapshot: TrackingSnapshot, field: Field,
    onDismiss: () -> Unit, onSelect: (TrackingEdit) -> Unit) {
    val entry = checkNotNull(snapshot.entry)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (field) {
                    Field.STATUS -> stringResource(Lang.tracking_dialog_title_status)
                    Field.PROGRESS -> stringResource(Lang.tracking_dialog_title_progress)
                    else -> stringResource(Lang.tracking_dialog_title_score)
                }, style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    when (field) {
                        Field.STATUS -> items(card.statusOptions) { option ->
                            TrackingChoice(option.status.displayLabel(), entry.status == option.status) { onSelect(TrackingEdit.Status(option.status)) }
                        }
                        Field.PROGRESS -> {
                            val episodes = snapshot.episodes
                            if (episodes != null) items(episodes) { episode ->
                                TrackingChoice(stringResource(Lang.tracking_choice_episode_format, episode.number), episode.watched) {
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
                            item { TrackingChoice(stringResource(Lang.tracking_choice_clear_score), entry.score == TrackingScore.Unrated) {
                                onSelect(TrackingEdit.Score(TrackingScore.Unrated))
                            } }
                        }
                        else -> Unit
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Lang.subject_collection_cancel)) }
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
        }) { Text(stringResource(Lang.tracking_action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Lang.subject_collection_cancel)) } }) { DatePicker(state) }
}
