/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import me.him188.ani.app.domain.mediasource.codec.FactoryNotFoundException
import me.him188.ani.app.domain.mediasource.codec.InvalidMediaSourceContentException
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.MediaSourceDecodeException
import me.him188.ani.app.domain.mediasource.codec.UnsupportedVersionException
import me.him188.ani.app.domain.mediasource.codec.decodeFromStringOrNull
import me.him188.ani.app.domain.mediasource.codec.serializeToString
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.foundation.isInDebugMode
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_cancel
import me.him188.ani.app.ui.lang.settings_mediasource_clipboard_empty
import me.him188.ani.app.ui.lang.settings_mediasource_close
import me.him188.ani.app.ui.lang.settings_mediasource_copied_to_clipboard
import me.him188.ani.app.ui.lang.settings_mediasource_export
import me.him188.ani.app.ui.lang.settings_mediasource_export_failed
import me.him188.ani.app.ui.lang.settings_mediasource_export_single
import me.him188.ani.app.ui.lang.settings_mediasource_import_from_clipboard
import me.him188.ani.app.ui.lang.settings_mediasource_import_title
import me.him188.ani.app.ui.lang.settings_mediasource_import_warning
import me.him188.ani.app.ui.lang.settings_mediasource_invalid_content
import me.him188.ani.app.ui.lang.settings_mediasource_multiple_configs
import me.him188.ani.app.ui.lang.settings_mediasource_override
import me.him188.ani.app.ui.lang.settings_mediasource_unsupported_factory
import me.him188.ani.app.ui.lang.settings_mediasource_unsupported_version
import org.jetbrains.compose.resources.stringResource

@Stable
object MediaSourceConfigurationDefaults {
    val outlinedTextFieldShape
        @Composable
        get() = MaterialTheme.shapes.medium
}

class ImportMediaSourceState<T : MediaSourceArguments>(
    private val codecManager: MediaSourceCodecManager,
    private val onImport: (T) -> Unit,
) {
    internal var parseResult by mutableStateOf<ParseResult?>(null)
        private set

    internal val error by derivedStateOf {
        parseResult as? ParseResult.Error
    }
    internal val showOverrideDialog by derivedStateOf {
        parseResult is ParseResult.Success<*>
    }

    fun parseContent(string: String?) {
        if (string.isNullOrBlank()) {
            parseResult = ParseResult.EmptyContent
            return
        }
        val list = codecManager.decodeFromStringOrNull(string)
        if (list == null) {
            parseResult = ParseResult.InvalidContent
            return
        }
        if (list.mediaSources.isEmpty()) {
            parseResult = ParseResult.EmptyContent
            return
        }
        if (list.mediaSources.size > 1) {
            parseResult = ParseResult.HasMoreThanOneArgument
            return
        }
        val data = list.mediaSources.single()

        val argument = try {
            codecManager.decode(data)
        } catch (e: MediaSourceDecodeException) {
            parseResult = when (e) {
                is UnsupportedVersionException -> ParseResult.UnsupportedVersion
                is FactoryNotFoundException -> ParseResult.UnsupportedFactory
                is InvalidMediaSourceContentException -> ParseResult.InvalidContent
            }
            return
        }
        parseResult = ParseResult.Success(argument)
    }

    fun cancelOverride() {
        parseResult = null
    }

    fun dismissError() {
        parseResult = null
    }

    fun confirmImport() {
        (parseResult as? ParseResult.Success<*>)?.let {
            @Suppress("UNCHECKED_CAST")
            onImport(it.argument as T)
        }
        parseResult = null
    }
}

@Immutable
internal sealed class ParseResult {
    @Immutable
    sealed class Error : ParseResult()

    @Immutable
    data object EmptyContent : Error()

    @Immutable
    data object InvalidContent : Error()

    @Immutable
    data object HasMoreThanOneArgument : Error()

    @Immutable
    data object UnsupportedFactory : Error()

    @Immutable
    data object UnsupportedVersion : Error()

    @Immutable
    data class Success<T>(
        val argument: T,
    ) : ParseResult()
}

/**
 * 点击后从剪贴板导入配置
 *
 * 配置有效时, 将会弹出一个对话框让用户确认覆盖现有配置.
 * 配置无效时, 将会显示一个错误提示.
 */
@Suppress("UnusedReceiverParameter")
@Composable
fun <T : MediaSourceArguments> MediaSourceConfigurationDefaults.DropdownMenuImport(
    state: ImportMediaSourceState<T>,
    onImported: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val clipboard = LocalClipboard.current
    val asyncHandler = rememberAsyncHandler()
    DropdownMenuItem(
        text = { Text(stringResource(Lang.settings_mediasource_import_from_clipboard)) },
        onClick = {
            asyncHandler.launch {
                state.parseContent(clipboard.getClipEntryText())
            }
        },
        modifier,
        leadingIcon = { Icon(Icons.Rounded.ContentPaste, null) },
        enabled = enabled,
    )
    if (state.showOverrideDialog) {
        val toaster = LocalToaster.current
        AlertDialog(
            onDismissRequest = { state.cancelOverride() },
            icon = { Icon(Icons.Rounded.ContentPaste, null) },
            title = { Text(stringResource(Lang.settings_mediasource_import_title)) },
            text = { Text(stringResource(Lang.settings_mediasource_import_warning)) },
            confirmButton = {
                val copied = stringResource(Lang.settings_mediasource_copied_to_clipboard)
                TextButton(
                    {
                        state.confirmImport()
                        toaster.toast(copied)
                        onImported()
                    },
                ) {
                    Text(stringResource(Lang.settings_mediasource_override), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton({ state.cancelOverride() }) {
                    Text(stringResource(Lang.settings_mediasource_cancel))
                }
            },
        )
    }
    val error = state.error
    if (error != null) {
        AlertDialog(
            { state.dismissError() },
            icon = { Icon(Icons.Rounded.Error, null) },
            title = {
                when (error) {
                    ParseResult.EmptyContent -> Text(stringResource(Lang.settings_mediasource_clipboard_empty))
                    ParseResult.HasMoreThanOneArgument -> Text(stringResource(Lang.settings_mediasource_multiple_configs))
                    ParseResult.InvalidContent -> Text(stringResource(Lang.settings_mediasource_invalid_content))
                    ParseResult.UnsupportedFactory -> Text(stringResource(Lang.settings_mediasource_unsupported_factory))
                    ParseResult.UnsupportedVersion -> Text(stringResource(Lang.settings_mediasource_unsupported_version))
                }
            },
            confirmButton = {
                TextButton({ state.dismissError() }) {
                    Text(stringResource(Lang.settings_mediasource_close))
                }
            },
        )
    }
}

class ExportMediaSourceState(
    private val codecManager: MediaSourceCodecManager,
    private val onExport: () -> MediaSourceArguments?,
) {
    fun serializeToString(): String? {
        return onExport()?.let {
            codecManager.serializeToString(listOf(it))
        }
    }

    fun serializeSingleToString(): String? {
        return onExport()?.let {
            codecManager.serializeSingleToString(it)
        }
    }
}

@Suppress("UnusedReceiverParameter")
@Composable
fun MediaSourceConfigurationDefaults.DropdownMenuExport(
    state: ExportMediaSourceState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val copiedToClipboard = stringResource(Lang.settings_mediasource_copied_to_clipboard)
    val cannotExport = stringResource(Lang.settings_mediasource_export_failed)
    val asyncHandler = rememberAsyncHandler()
    DropdownMenuItem(
        text = { Text(stringResource(Lang.settings_mediasource_export)) },
        onClick = {
            asyncHandler.launch {
                val serialized = state.serializeToString()
                if (serialized != null) {
                    clipboard.setClipEntryText(serialized)
                    toaster.toast(copiedToClipboard)
                } else {
                    toaster.toast(cannotExport)
                }
                onDismissRequest()
            }
        },
        modifier,
        leadingIcon = { Icon(Icons.Rounded.Share, null) },
        enabled = enabled,
    )
    if (isInDebugMode()) {
        DropdownMenuItem(
            text = { Text(stringResource(Lang.settings_mediasource_export_single)) },
            onClick = {
                asyncHandler.launch {
                    val serialized = state.serializeSingleToString()
                    if (serialized != null) {
                        clipboard.setClipEntryText(serialized)
                        toaster.toast(copiedToClipboard)
                    } else {
                        toaster.toast(cannotExport)
                    }
                    onDismissRequest()
                }
            },
            modifier,
            leadingIcon = { Icon(Icons.Rounded.Share, null) },
            enabled = enabled,
        )
    }
}
