/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.settings.mediasource.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import me.him188.ani.app.ui.lang.settings_mediasource_copied_to_clipboard
import me.him188.ani.app.ui.lang.settings_mediasource_export
import me.him188.ani.app.ui.lang.settings_mediasource_import_from_clipboard
import me.him188.ani.app.ui.lang.settings_mediasource_selector_test
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
    val root = apiEditorJson.parseToJsonElement(text)
    val arguments = if (root is JsonObject && "mediaSources" in root) {
        val data = requireNotNull(codecs.decodeFromStringOrNull(text)).mediaSources.single()
        require(data.factoryId == ApiMediaSource.FactoryId) { "Expected a json-api source" }
        codecs.decode(data) as ApiMediaSourceArguments
    } else apiEditorJson.decodeFromJsonElement(ApiMediaSourceArguments.serializer(), root)
    arguments.validate()
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
    var keyword by remember { mutableStateOf("") }
    var subjects by remember { mutableStateOf(emptyList<BrowseSubject>()) }
    var selected by remember { mutableStateOf<BrowseSubject?>(null) }
    var channels by remember { mutableStateOf(emptyList<BrowseChannel>()) }
    var source by remember { mutableStateOf<ApiMediaSource?>(null) }
    var busy by remember { mutableStateOf(false) }
    var testError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val copied = stringResource(Lang.settings_mediasource_copied_to_clipboard)
    val resolved = stringResource(Lang.settings_api_video_resolved)
    LaunchedEffect(vm.text) {
        subjects = emptyList()
        channels = emptyList()
        selected = null
        source = null
        testError = null
        message = null
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
    Scaffold(
        modifier,
        contentWindowInsets = windowInsets,
        topBar = {
            TopAppBar(title = { Text("JSON API") }, navigationIcon = navigationIcon, actions = {
                ApiIconButton(Icons.Rounded.ContentPaste, stringResource(Lang.settings_mediasource_import_from_clipboard), vm.allowEdit && !vm.isSaving && !busy) {
                    scope.launch { clipboard.getClipEntryText()?.let(vm::edit) }
                }
                ApiIconButton(Icons.Rounded.ContentCopy, stringResource(Lang.settings_mediasource_export), vm.text != null && !busy) {
                    test {
                        val args = decodeApiConfiguration(vm.text.orEmpty(), vm.codecs)
                        clipboard.setClipEntryText(vm.codecs.serializeToString(listOf(args)))
                        message = copied
                    }
                }
            })
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val text = vm.text
            if (text == null) item { CircularProgressIndicator() } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ApiConfigurationEditor(text, vm::edit, vm.allowEdit && !vm.isSaving && !busy, vm.codecs, vm.isSaving) { args ->
                            scope.launch { vm.save(args) }
                        }
                    }
                }
                vm.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (vm.saved) item { Text(stringResource(Lang.settings_api_saved)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(keyword, { keyword = it }, Modifier.weight(1f), singleLine = true,
                            label = { Text(stringResource(Lang.settings_api_search)) }, enabled = !busy)
                        Button(onClick = {
                            test {
                                val newSource = vm.testSource(decodeApiConfiguration(text, vm.codecs))
                                subjects = emptyList()
                                channels = emptyList()
                                selected = null
                                source = newSource
                                subjects = withContext(Dispatchers.Default) { newSource.searchSubjects(keyword) }
                            }
                        }, enabled = !busy && keyword.isNotBlank()) {
                            Icon(Icons.Rounded.Search, null)
                            Text(stringResource(Lang.settings_mediasource_selector_test))
                        }
                    }
                }
                if (busy) item { CircularProgressIndicator() }
                testError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                message?.let { item { Text(it) } }
                items(subjects) { subject ->
                    TextButton(onClick = {
                        test {
                            selected = subject
                            channels = emptyList()
                            channels = withContext(Dispatchers.Default) { source!!.browseSubject(subject) }
                        }
                    }, enabled = !busy) { Text(subject.name) }
                }
                channels.forEach { channel ->
                    item { Text(channel.name.orEmpty(), style = MaterialTheme.typography.titleMedium) }
                    items(channel.episodes) { episode ->
                        TextButton(onClick = {
                            test {
                                val video = withContext(Dispatchers.Default) {
                                    source!!.resolveVideo(source!!.createMedia(selected!!, channel.name, episode, episode.episodeSort))
                                }
                                message = "$resolved: ${Url(video.m3u8Url).host}"
                            }
                        }, enabled = !busy) { Text(episode.name) }
                    }
                }
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
    onSave: (ApiMediaSourceArguments) -> Unit,
) {
    val parsed = remember(text) { runCatching { decodeApiConfiguration(text, codecs) } }
    OutlinedTextField(text, onTextChange, Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 500.dp).testTag("api-json"),
        enabled = enabled, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        label = { Text(stringResource(Lang.settings_api_json)) }, isError = parsed.isFailure)
    if (parsed.isFailure) Text(parsed.exceptionOrNull()?.message.orEmpty().take(200), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("api-error"))
    Button({ parsed.getOrNull()?.let(onSave) }, Modifier.testTag("api-save"), enabled = enabled && !isSaving && parsed.isSuccess) {
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
