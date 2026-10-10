/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.settings.mediasource.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.updateMediaSourceArguments
import me.him188.ani.app.domain.mediasource.api.ApiMediaSource
import me.him188.ani.app.domain.mediasource.api.ApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.decodeFromStringOrNull
import me.him188.ani.app.domain.mediasource.codec.serializeToString
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_api_json
import me.him188.ani.app.ui.lang.settings_api_save
import me.him188.ani.app.ui.lang.settings_api_saved
import me.him188.ani.app.ui.lang.settings_api_search
import me.him188.ani.app.ui.lang.settings_api_video_resolved
import me.him188.ani.app.ui.lang.settings_api_configuration
import me.him188.ani.app.ui.lang.settings_api_advanced
import me.him188.ani.app.ui.lang.settings_api_read_only
import me.him188.ani.app.ui.lang.settings_api_test_hint
import me.him188.ani.app.ui.lang.settings_api_no_results
import me.him188.ani.app.ui.lang.settings_api_choose_episode
import me.him188.ani.app.ui.lang.settings_api_subjects
import me.him188.ani.app.ui.lang.settings_mediasource_copied_to_clipboard
import me.him188.ani.app.ui.lang.settings_mediasource_export
import me.him188.ani.app.ui.lang.settings_mediasource_import_from_clipboard
import me.him188.ani.app.ui.lang.settings_mediasource_selector_test
import me.him188.ani.app.ui.settings.mediasource.rss.edit.MediaSourceHeadline
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.deserializeArgumentsOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

internal val apiEditorJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

internal fun decodeApiConfiguration(text: String, codecs: MediaSourceCodecManager): ApiMediaSourceArguments {
    return decodeApiArguments(text, codecs).also { it.validate() }
}

internal fun decodeApiArguments(text: String, codecs: MediaSourceCodecManager): ApiMediaSourceArguments {
    val root = apiEditorJson.parseToJsonElement(text)
    val arguments = if (root is JsonObject && "mediaSources" in root) {
        val data = requireNotNull(codecs.decodeFromStringOrNull(text)).mediaSources.single()
        require(data.factoryId == ApiMediaSource.FactoryId) { "Expected a json-api source" }
        codecs.decode(data) as ApiMediaSourceArguments
    } else apiEditorJson.decodeFromJsonElement(ApiMediaSourceArguments.serializer(), root)
    return arguments
}

class EditApiMediaSourceViewModel(private val instanceId: String) : AbstractViewModel(), KoinComponent {
    private val manager: MediaSourceManager by inject()
    private val clients: HttpClientProvider by inject()
    val codecs: MediaSourceCodecManager by inject()
    var text by mutableStateOf<String?>(null)
        private set
    var allowEdit by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        backgroundScope.launch {
            val config = manager.instanceConfigFlow(instanceId).first()
            val arguments = config?.deserializeArgumentsOrNull(ApiMediaSourceArguments.serializer()) ?: ApiMediaSourceArguments()
            withContext(Dispatchers.Main) {
                text = apiEditorJson.encodeToString(ApiMediaSourceArguments.serializer(), arguments)
                allowEdit = config != null && config.subscriptionId == null
            }
        }
    }

    fun edit(value: String) {
        text = value
        saved = false
        error = null
    }

    suspend fun save(arguments: ApiMediaSourceArguments) {
        if (!allowEdit || isSaving) return
        isSaving = true
        try {
            withContext(Dispatchers.Default) { manager.updateMediaSourceArguments(instanceId, ApiMediaSourceArguments.serializer(), arguments) }
            saved = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            isSaving = false
        }
    }

    fun testSource(arguments: ApiMediaSourceArguments): ApiMediaSource = ApiMediaSource(
        "api-test:$instanceId",
        MediaSourceConfig(serializedArguments = apiEditorJson.encodeToJsonElement(ApiMediaSourceArguments.serializer(), arguments)),
        clients.get(ScopedHttpClientUserAgent.BROWSER),
    )
}

@Composable
fun EditApiMediaSourceScreen(
    vm: EditApiMediaSourceViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets,
    navigationIcon: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val form = remember(vm) { ApiConfigurationFormState(vm.codecs) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var showTest by rememberSaveable { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var subjects by remember { mutableStateOf(emptyList<BrowseSubject>()) }
    var selected by remember { mutableStateOf<BrowseSubject?>(null) }
    var channels by remember { mutableStateOf(emptyList<BrowseChannel>()) }
    var source by remember { mutableStateOf<ApiMediaSource?>(null) }
    var busy by remember { mutableStateOf(false) }
    var testError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    var channelIndex by remember { mutableStateOf(0) }
    val copied = stringResource(Lang.settings_mediasource_copied_to_clipboard)
    val resolved = stringResource(Lang.settings_api_video_resolved)
    LaunchedEffect(vm.text) {
        vm.text?.let { if (form.text != it) form.load(it) }
    }
    LaunchedEffect(vm.text, form.value, form.fields.toMap()) {
        subjects = emptyList()
        channels = emptyList()
        selected = null
        source = null
        testError = null
        message = null
        searched = false
    }
    fun test(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            testError = null
            message = null
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                testError = e.message ?: "API error"
            } finally {
                busy = false
            }
        }
    }
    val configuration = runCatching { form.configuration() }
    val editable = vm.allowEdit && !vm.isSaving && !busy
    val configurationPane: @Composable (Modifier) -> Unit = { paneModifier ->
        LazyColumn(paneModifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (vm.text == null) item { CircularProgressIndicator() } else {
                item { MediaSourceHeadline(form.value?.iconUrl.orEmpty(), form.value?.name ?: "JSON API") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!advanced, { advanced = false }, label = { Text(stringResource(Lang.settings_api_configuration)) }, enabled = form.value != null)
                        FilterChip(advanced, { advanced = true }, label = { Text(stringResource(Lang.settings_api_advanced)) })
                    }
                }
                if (!vm.allowEdit) item { Text(stringResource(Lang.settings_api_read_only), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item {
                    if (advanced || form.value == null) {
                        ApiConfigurationEditor(vm.text.orEmpty(), vm::edit, editable, vm.codecs, vm.isSaving, showSave = false) { args ->
                            scope.launch { vm.save(args) }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { ApiConfigurationForm(form, editable, vm::edit) }
                    }
                }
                configuration.exceptionOrNull()?.let { error ->
                    item { Text(error.message.orEmpty().take(240), color = MaterialTheme.colorScheme.error) }
                }
                vm.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (vm.saved) item { Text(stringResource(Lang.settings_api_saved), color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
    val testPane: @Composable (Modifier) -> Unit = { paneModifier ->
        LazyColumn(paneModifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(Lang.settings_mediasource_selector_test), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Lang.settings_api_test_hint), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(keyword, { keyword = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text(stringResource(Lang.settings_api_search)) }, enabled = !busy, shape = MaterialTheme.shapes.medium)
                    Button(onClick = {
                        test {
                            val newSource = vm.testSource(form.configuration())
                            subjects = emptyList()
                            channels = emptyList()
                            selected = null
                            source = newSource
                            searched = true
                            subjects = withContext(Dispatchers.Default) { newSource.searchSubjects(keyword) }
                        }
                    }, enabled = !busy && keyword.isNotBlank() && configuration.isSuccess) { Icon(Icons.Rounded.Search, stringResource(Lang.settings_mediasource_selector_test)) }
                }
            }
            if (busy) item { CircularProgressIndicator() }
            testError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            if (searched && !busy && subjects.isEmpty() && testError == null) item { Text(stringResource(Lang.settings_api_no_results)) }
            if (selected == null) {
                items(subjects) { subject ->
                    OutlinedCard(onClick = {
                        test {
                            channels = withContext(Dispatchers.Default) { source!!.browseSubject(subject) }
                            channelIndex = 0
                            selected = subject
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(subject.name, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                item {
                    TextButton({ selected = null; channels = emptyList(); message = null }, enabled = !busy) { Text(stringResource(Lang.settings_api_subjects)) }
                    Text(selected!!.name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(Lang.settings_api_choose_episode), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (channels.isEmpty() && !busy) item { Text(stringResource(Lang.settings_api_no_results)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(channels.size) { index ->
                            val channel = channels[index]
                            FilterChip(channelIndex == index, { channelIndex = index; message = null },
                                label = { Text("${channel.name.orEmpty()} (${channel.episodes.size})") }, enabled = !busy)
                        }
                    }
                }
                val channel = channels.getOrNull(channelIndex)
                items(channel?.episodes.orEmpty()) { episode ->
                    OutlinedCard(onClick = {
                        test {
                            val video = withContext(Dispatchers.Default) {
                                source!!.resolveVideo(source!!.createMedia(selected!!, channel?.name, episode, episode.episodeSort))
                            }
                            message = "$resolved: ${Url(video.m3u8Url).host}"
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(episode.name, Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
    val wide = maxWidth >= 900.dp
    Scaffold(
        Modifier.fillMaxSize(),
        contentWindowInsets = windowInsets,
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        topBar = {
            TopAppBar(title = { Text(form.value?.name ?: "JSON API") }, navigationIcon = navigationIcon, actions = {
                if (!wide) TextButton({ showTest = !showTest }) {
                    Text(stringResource(if (showTest) Lang.settings_api_configuration else Lang.settings_mediasource_selector_test))
                }
                Button({ configuration.getOrNull()?.let { args -> scope.launch { vm.save(args) } } }, enabled = editable && configuration.isSuccess,
                    modifier = Modifier.testTag("api-form-save")) {
                    Icon(Icons.Rounded.Save, null)
                    Text(stringResource(Lang.settings_api_save))
                }
                ApiIconButton(Icons.Rounded.ContentPaste, stringResource(Lang.settings_mediasource_import_from_clipboard), vm.allowEdit && !vm.isSaving && !busy) {
                    scope.launch { clipboard.getClipEntryText()?.let(vm::edit) }
                }
                ApiIconButton(Icons.Rounded.ContentCopy, stringResource(Lang.settings_mediasource_export), vm.text != null && !busy) {
                    test {
                        val args = form.configuration()
                        clipboard.setClipEntryText(vm.codecs.serializeToString(listOf(args)))
                        message = copied
                    }
                }
            })
        },
    ) { padding ->
            if (wide) {
                Row(Modifier.padding(padding).fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    configurationPane(Modifier.weight(1f).fillMaxSize())
                    testPane(Modifier.weight(1f).fillMaxSize())
                }
            } else {
                if (showTest) testPane(Modifier.padding(padding).fillMaxSize()) else configurationPane(Modifier.padding(padding).fillMaxSize())
            }
    }
    }
}

@Composable
internal fun ApiConfigurationEditor(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    codecs: MediaSourceCodecManager,
    isSaving: Boolean,
    showSave: Boolean = true,
    onSave: (ApiMediaSourceArguments) -> Unit,
) {
    val parsed = remember(text) { runCatching { decodeApiConfiguration(text, codecs) } }
    OutlinedTextField(text, onTextChange, Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 500.dp).testTag("api-json"),
        enabled = enabled, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        label = { Text(stringResource(Lang.settings_api_json)) }, isError = parsed.isFailure)
    if (parsed.isFailure) Text(parsed.exceptionOrNull()?.message.orEmpty().take(200), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("api-error"))
    if (showSave) Button({ parsed.getOrNull()?.let(onSave) }, Modifier.testTag("api-save"), enabled = enabled && !isSaving && parsed.isSuccess) {
        Icon(Icons.Rounded.Save, null)
        Text(stringResource(Lang.settings_api_save))
    }
}

@Composable
private fun ApiIconButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above), tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()) {
        IconButton(onClick, enabled = enabled) { Icon(icon, label) }
    }
}
