/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.domain.mediasource.test.RefreshResult
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_cancel
import me.him188.ani.app.ui.lang.settings_mediasource_copied
import me.him188.ani.app.ui.lang.settings_mediasource_copy
import me.him188.ani.app.ui.lang.settings_mediasource_error_info
import me.him188.ani.app.ui.lang.settings_mediasource_error_title
import me.him188.ani.app.ui.lang.settings_mediasource_invalid_config
import me.him188.ani.app.ui.lang.settings_mediasource_network_error
import me.him188.ani.app.ui.lang.settings_mediasource_rate_limited
import me.him188.ani.app.ui.lang.settings_mediasource_refresh
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.settings_mediasource_server_error
import me.him188.ani.app.ui.lang.settings_mediasource_unauthorized
import me.him188.ani.app.ui.lang.settings_mediasource_unknown_error
import org.jetbrains.compose.resources.stringResource


/**
 * 包含一个 Text, 一个刷新按钮, 一个错误提示的 [Row].
 *
 * 刷新按钮一直显示. 错误提示只在 [result] 为 [RefreshResult.Failed] 时显示.
 *
 * @param result null 表示查询中
 * @see RefreshIndicationDefaults
 */
@Composable
fun RefreshIndicatedHeadlineRow(
    headline: @Composable () -> Unit,
    onRefresh: () -> Unit,
    result: RefreshResult?,
    modifier: Modifier = Modifier,
    refreshIcon: @Composable () -> Unit = { RefreshIndicationDefaults.RefreshIconButton(onRefresh) },
    style: TextStyle = MaterialTheme.typography.titleLarge,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ProvideTextStyle(style) {
            headline()
        }

        refreshIcon()

        AniAnimatedVisibility(result is RefreshResult.Failed, modifier) {
            RefreshIndicationDefaults.RefreshResultTextButton(result, onRefresh)
        }
    }
}

@Stable
object RefreshIndicationDefaults {
    @Composable
    fun RefreshIconButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            Icon(Icons.Rounded.Refresh, stringResource(Lang.settings_mediasource_refresh))
        }
    }

    @Composable
    fun RefreshResultTextButton(
        result: RefreshResult?,
        onRefresh: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        if (result !is RefreshResult.Failed) return
        var showErrorDialog by remember { mutableStateOf(false) }
        TextButton(
            onClick = {
                if (result is RefreshResult.UnknownError) {
                    showErrorDialog = true
                } else {
                    onRefresh()
                }
            },
            modifier,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(Icons.Rounded.Error, null, Modifier.align(Alignment.CenterVertically))
            Text(
                when (result) {
                    is RefreshResult.ApiError -> {
                        when (val ex = result.exception) {
                            is RepositoryAuthorizationException -> stringResource(Lang.settings_mediasource_unauthorized)
                            is RepositoryNetworkException -> stringResource(Lang.settings_mediasource_network_error)
                            is RepositoryRateLimitedException -> stringResource(Lang.settings_mediasource_rate_limited)
                            is RepositoryServiceUnavailableException -> stringResource(Lang.settings_mediasource_server_error)
                            is RepositoryUnknownException -> stringResource(
                                Lang.settings_mediasource_unknown_error,
                                result.exception.toString(),
                            )

                            is RepositoryRequestError -> ex.localizedMessage
                        }
                    }

                    is RefreshResult.UnknownError -> stringResource(
                        Lang.settings_mediasource_unknown_error,
                        result.exception.toString(),
                    )

                    is RefreshResult.InvalidConfig -> stringResource(Lang.settings_mediasource_invalid_config)
                },
                Modifier.padding(start = 8.dp).align(Alignment.CenterVertically),
            )
        }
        if (showErrorDialog) {
            AlertDialog(
                { showErrorDialog = false },
                title = { Text(stringResource(Lang.settings_mediasource_error_title)) },
                text = {
                    val clipboard = LocalClipboardManager.current
                    val text by derivedStateOf {
                        (result as? RefreshResult.UnknownError)?.exception?.stackTraceToString() ?: ""
                    }
                    val toaster = LocalToaster.current
                    OutlinedTextField(
                        value = text,
                        onValueChange = {},
                        label = { Text(stringResource(Lang.settings_mediasource_error_info)) },
                        readOnly = true,
                        maxLines = 2,
                        trailingIcon = {
                            val copied = stringResource(Lang.settings_mediasource_copied)
                            IconButton(
                                {
                                    clipboard.setText(AnnotatedString(text))
                                    toaster.toast(copied)
                                },
                            ) {
                                Icon(Icons.Rounded.ContentCopy, stringResource(Lang.settings_mediasource_copy))
                            }
                        },
                    )
                },
                confirmButton = {
                    TextButton(onRefresh) {
                        Text(stringResource(Lang.settings_mediasource_retry))
                    }
                },
                dismissButton = {
                    TextButton({ showErrorDialog = false }) {
                        Text(stringResource(Lang.settings_mediasource_cancel))
                    }
                },
            )
        }
    }

}
