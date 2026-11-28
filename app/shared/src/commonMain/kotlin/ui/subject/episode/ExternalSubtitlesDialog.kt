/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.preference.OpenSubtitlesSettings
import me.him188.ani.app.data.network.OpenSubtitleFile
import me.him188.ani.app.data.network.OpenSubtitlePage
import me.him188.ani.app.data.network.OpenSubtitlesClient
import me.him188.ani.app.data.network.OpenSubtitlesException
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.external_subtitle_auth_error
import me.him188.ani.app.ui.lang.external_subtitle_disabled
import me.him188.ani.app.ui.lang.external_subtitle_done
import me.him188.ani.app.ui.lang.external_subtitle_earlier
import me.him188.ani.app.ui.lang.external_subtitle_empty
import me.him188.ani.app.ui.lang.external_subtitle_episode
import me.him188.ani.app.ui.lang.external_subtitle_formats
import me.him188.ani.app.ui.lang.external_subtitle_import
import me.him188.ani.app.ui.lang.external_subtitle_later
import me.him188.ani.app.ui.lang.external_subtitle_next
import me.him188.ani.app.ui.lang.external_subtitle_off
import me.him188.ani.app.ui.lang.external_subtitle_offset
import me.him188.ani.app.ui.lang.external_subtitle_previous
import me.him188.ani.app.ui.lang.external_subtitle_query
import me.him188.ani.app.ui.lang.external_subtitle_quota_error
import me.him188.ani.app.ui.lang.external_subtitle_rate_error
import me.him188.ani.app.ui.lang.external_subtitle_read_error
import me.him188.ani.app.ui.lang.external_subtitle_search
import me.him188.ani.app.ui.lang.external_subtitle_season
import me.him188.ani.app.ui.lang.external_subtitle_setup
import me.him188.ani.app.ui.lang.external_subtitle_title
import me.him188.ani.app.videoplayer.subtitle.ExternalSubtitleController
import me.him188.ani.utils.coroutines.IO_
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.MediampPlayer

@Composable
internal fun ExternalSubtitlesDialog(
    player: MediampPlayer,
    controller: ExternalSubtitleController,
    settings: OpenSubtitlesSettings,
    initialQuery: String,
    initialEpisode: String,
    search: suspend (query: String, episode: Int?, season: Int?, page: Int) -> OpenSubtitlePage,
    download: suspend (OpenSubtitleFile) -> ByteArray,
    onDismissRequest: () -> Unit,
) {
    val media by player.mediaData.collectAsState()
    var query by remember(media) { mutableStateOf(initialQuery) }
    var episode by remember(media) { mutableStateOf(initialEpisode.takeIf { it.toIntOrNull() != null }.orEmpty()) }
    var season by remember(media) { mutableStateOf("") }
    var result by remember(media) { mutableStateOf<OpenSubtitlePage?>(null) }
    var error by remember(media) { mutableStateOf<Int?>(null) }
    var busy by remember(media) { mutableStateOf(false) }
    val selected by controller.selected.collectAsState()
    val offset by controller.offsetMillis.collectAsState()
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(media) { onDispose { job?.cancel() } }

    fun runOperation(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            busy = true
            error = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = (e as? OpenSubtitlesException)?.status ?: -1
            } finally {
                busy = false
            }
        }
    }

    val pickerMedia = remember { mutableStateOf(media) }
    val picker = rememberFilePickerLauncher(type = FileKitType.File(listOf("srt", "vtt", "ass", "ssa"))) { file ->
        val target = pickerMedia.value
        if (file != null && target != null) runOperation {
            val bytes = withContext(Dispatchers.IO_) {
                require(file.size() <= OpenSubtitlesClient.MAX_SUBTITLE_BYTES)
                file.readBytes()
            }
            controller.select(file.name, bytes, target)
        }
    }
    fun searchPage(page: Int) {
        val title = query.trim()
        val ep = episode.toIntOrNull()
        val seasonNumber = season.toIntOrNull()
        runOperation { result = search(title, ep, seasonNumber, page) }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Lang.external_subtitle_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    enabled = media != null && !busy,
                    onClick = { pickerMedia.value = media; picker.launch() },
                    modifier = Modifier.testTag("ImportLocalSubtitle"),
                ) { Text(stringResource(Lang.external_subtitle_import)) }
                Text(stringResource(Lang.external_subtitle_formats), style = MaterialTheme.typography.bodySmall)
                selected?.let {
                    Text(it.name, maxLines = 2)
                    TextButton(onClick = { controller.clear() }) { Text(stringResource(Lang.external_subtitle_off)) }
                    Row {
                        TextButton(onClick = { controller.offsetMillis.value = (offset - 100).coerceAtLeast(-600_000) }) { Text(stringResource(Lang.external_subtitle_earlier)) }
                        TextButton(onClick = { controller.offsetMillis.value = (offset + 100).coerceAtMost(600_000) }) { Text(stringResource(Lang.external_subtitle_later)) }
                    }
                    Text(stringResource(Lang.external_subtitle_offset, (offset / 1000.0).toString()))
                }
                Text("OpenSubtitles", style = MaterialTheme.typography.titleMedium)
                if (!settings.enabled) {
                    Text(stringResource(Lang.external_subtitle_disabled))
                } else if (!settings.isConfigured) {
                    Text(stringResource(Lang.external_subtitle_setup))
                }
                OutlinedTextField(
                    query, { query = it; result = null },
                    label = { Text(stringResource(Lang.external_subtitle_query)) }, singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("SubtitleSearchQuery"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        season, { season = it.filter(Char::isDigit).take(3); result = null },
                        label = { Text(stringResource(Lang.external_subtitle_season)) }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        episode, { episode = it.filter(Char::isDigit).take(4); result = null },
                        label = { Text(stringResource(Lang.external_subtitle_episode)) }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    onClick = { searchPage(1) },
                    enabled = settings.enabled && query.isNotBlank() && !busy,
                    modifier = Modifier.testTag("SearchOpenSubtitles"),
                ) { Text(stringResource(Lang.external_subtitle_search)) }
                if (busy) CircularProgressIndicator()
                error?.let {
                    Text(
                        when (it) {
                            401, 403 -> stringResource(Lang.external_subtitle_auth_error)
                            406 -> stringResource(Lang.external_subtitle_quota_error)
                            429 -> stringResource(Lang.external_subtitle_rate_error)
                            else -> stringResource(Lang.external_subtitle_read_error)
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                result?.let { page ->
                    if (page.files.isEmpty()) Text(stringResource(Lang.external_subtitle_empty))
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(page.files, key = { it.id }) { file ->
                            TextButton(
                                enabled = !busy && media != null && settings.isConfigured,
                                onClick = {
                                    val target = media ?: return@TextButton
                                    runOperation {
                                        val bytes = download(file)
                                        controller.select(file.name, bytes, target)
                                    }
                                },
                            ) {
                                Text("[${file.language}] ${file.name}", maxLines = 3)
                            }
                        }
                    }
                    Row {
                        TextButton(enabled = !busy && page.page > 1, onClick = { searchPage(page.page - 1) }) { Text(stringResource(Lang.external_subtitle_previous)) }
                        Text("${page.page} / ${page.totalPages}")
                        TextButton(enabled = !busy && page.page < page.totalPages, onClick = { searchPage(page.page + 1) }) { Text(stringResource(Lang.external_subtitle_next)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(Lang.external_subtitle_done)) } },
    )
}
