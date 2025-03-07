/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.BackgroundScope
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcon
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.parameter.BooleanParameter
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameter
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.datasources.api.source.parameter.SimpleEnumParameter
import me.him188.ani.datasources.api.source.parameter.StringParameter
import kotlin.coroutines.CoroutineContext


sealed class EditMediaSourceMode {
    data class Add(
        val factoryId: FactoryId,
    ) : EditMediaSourceMode()

    data class Edit(
        val instanceId: String,
    ) : EditMediaSourceMode()
}

@Stable
class EditingMediaSource(
    /**
     * 新的 (random) 或者已有的
     */
    val editingMediaSourceId: String,
    val factoryId: FactoryId,
    val info: MediaSourceInfo,
    val parameters: MediaSourceParameters,
    persistedArguments: Flow<MediaSourceConfig>, // 加载会有延迟
    val editMediaSourceMode: EditMediaSourceMode,
    private val onSave: suspend (EditingMediaSource) -> Unit, // background
    parentCoroutineContext: CoroutineContext,
) : HasBackgroundScope by BackgroundScope(parentCoroutineContext), Closeable {
    val arguments = parameters.list.map { param ->
        when (param) {
            is BooleanParameter -> BooleanArgumentState(param)
            is SimpleEnumParameter -> SimpleEnumArgumentState(param)
            is StringParameter -> StringArgumentState(param)
        }
    }

    private val persistLoader = persistedArguments.map { config ->
        for ((name, value) in config.arguments) {
            arguments.find { it.name == name }?.loadFromPersisted(value)
        }
    }.stateInBackground(null, started = SharingStarted.Eagerly)

    val isLoading = persistLoader.map { it != null }
    val hasError by derivedStateOf { arguments.any { it.isError } }

    override fun close() {
        backgroundScope.cancel()
    }

    private val saveTasker = MonoTasker(backgroundScope)
    val isSaving = saveTasker.isRunning
    fun save() {
        saveTasker.launch {
            onSave(this@EditingMediaSource)
        }
    }
}

@Stable
sealed class ArgumentState(
    private val parameter: MediaSourceParameter<*>,
) {
    val name: String get() = parameter.name
    val description: String? get() = parameter.description

    abstract val isError: Boolean

    abstract fun loadFromPersisted(value: String?)

    abstract fun toPersisted(): String?
}

@Stable
class StringArgumentState(
    val parameter: StringParameter,
) : ArgumentState(parameter) {
    var value: String by mutableStateOf(parameter.default())
    override val isError: Boolean by derivedStateOf { !parameter.validate(value) }

    override fun loadFromPersisted(value: String?) {
        this.value = value ?: ""
    }

    override fun toPersisted(): String = value
}

@Stable
class BooleanArgumentState(
    private val origin: BooleanParameter,
) : ArgumentState(origin) {
    var value: Boolean by mutableStateOf(origin.default())
    override val isError: Boolean get() = false

    override fun loadFromPersisted(value: String?) {
        this.value = value?.toBooleanStrictOrNull() ?: origin.default()
    }

    override fun toPersisted() = value.toString()
}

@Stable
class SimpleEnumArgumentState(
    private val origin: SimpleEnumParameter,
) : ArgumentState(origin) {
    val options get() = origin.oneOf
    var value: String by mutableStateOf(origin.default())
    override val isError: Boolean by derivedStateOf { value !in origin.oneOf }

    override fun loadFromPersisted(value: String?) {
        this.value = value.takeIf { it in origin.oneOf } ?: origin.default()
    }

    override fun toPersisted() = value
}

// TODO: remove or replace with non-dialog one (dedicated page)
@Composable
internal fun EditMediaSourceDialog(
    state: EditingMediaSource,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: check changed 
//    val backHandler = LocalBackHandler.current
//    val confirmDiscardDialog = rememberConfirmDiscardChangeDialogState {
//        state.isChanged = false
//        backHandler.onBackPressed()
//    }
//    ConfirmDiscardChangeDialog(confirmDiscardDialog)
//
//    BackHandler(enabled = state.isChanged) {
//        confirmDiscardDialog.show()
//    }

//    Scaffold(
//        modifier,
//        topBar = {
//            TopAppBar(
//                title = {
//                    when (state.editMediaSourceMode) {
//                        is EditMediaSourceMode.Edit -> Text(state.info.displayName)
//                        EditMediaSourceMode.Add -> Text("新建数据源")
//                    }
//                },
//                navigationIcon = { TopAppBarGoBackButton() },
//                actions = {
//                    IconButton({ state.save() }, enabled = !state.hasError) {
//                        Icon(Icons.Rounded.Save, contentDescription = "保存")
//                    }
//                },
//            )
//        },
//    ) { paddingValues ->

    AlertDialog(
        onDismissRequest,
        title = {
            Text(state.info.displayName)
        },
        icon = {
            Box(Modifier.clip(MaterialTheme.shapes.extraSmall).size(24.dp)) {
                MediaSourceIcon(state.info, Modifier.size(24.dp))
            }
        },
        text = {
            if (state.arguments.isEmpty()) {
                Text("无配置项")
                return@AlertDialog
            }

            Column(Modifier.padding()) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (argument in state.arguments) {
                        when (argument) {
                            is BooleanArgumentState -> {
                                BooleanArgument(argument)
                            }

                            is SimpleEnumArgumentState -> {
                                SimpleEnumArgument(argument)
                            }

                            is StringArgumentState -> {
                                OutlinedTextField(
                                    value = argument.value,
                                    onValueChange = { argument.value = argument.parameter.sanitize(it) },
                                    label = { Text(argument.name) },
                                    placeholder = argument.parameter.placeholder?.let { { Text(it) } },
                                    supportingText = argument.description?.let { { Text(it) } },
                                    isError = argument.isError,
                                    shape = MaterialTheme.shapes.medium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canSave by remember(state) {
                derivedStateOf {
//                    !isLoading && todo 不知道为什么监听不到 isLoading, 但加载速度反正很快
                    !state.hasError
                }
            }
            when (state.editMediaSourceMode) {
                is EditMediaSourceMode.Add -> Button({ state.save() }, enabled = canSave) { Text("添加") }
                is EditMediaSourceMode.Edit -> Button({ state.save() }, enabled = canSave) { Text("保存") }
            }
        },
        dismissButton = {
            TextButton(onDismissRequest) {
                Text("取消")
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun SimpleEnumArgument(argument: SimpleEnumArgumentState, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier,
        content = {
            OutlinedTextField(
                // The `menuAnchor` modifier must be passed to the text field to handle
                // expanding/collapsing the menu on click. A read-only text field has
                // the anchor type `PrimaryNotEditable`.
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                value = argument.value,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(argument.name) },
                supportingText = argument.description?.let { { Text(it) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = MaterialTheme.shapes.medium,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                argument.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            argument.value = option
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        },
    )
}

@Composable
private fun BooleanArgument(argument: BooleanArgumentState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 8.dp).padding(end = 16.dp)) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                Text(
                    argument.name,
                )
            }
            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                argument.description?.let { desc ->
                    Text(
                        desc,
                        Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        Switch(
            checked = argument.value,
            onCheckedChange = {
                argument.value = it
            },
        )
    }
}
