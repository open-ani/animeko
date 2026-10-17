/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.qrlogin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.login_sign_in
import me.him188.ani.app.ui.lang.qr_login_already_handled
import me.him188.ani.app.ui.lang.qr_login_approved_description
import me.him188.ani.app.ui.lang.qr_login_approved_title
import me.him188.ani.app.ui.lang.qr_login_close
import me.him188.ani.app.ui.lang.qr_login_confirm_approve
import me.him188.ani.app.ui.lang.qr_login_confirm_as
import me.him188.ani.app.ui.lang.qr_login_confirm_as_current
import me.him188.ani.app.ui.lang.qr_login_confirm_device_requesting
import me.him188.ani.app.ui.lang.qr_login_confirm_headline
import me.him188.ani.app.ui.lang.qr_login_confirm_reject
import me.him188.ani.app.ui.lang.qr_login_confirm_title
import me.him188.ani.app.ui.lang.qr_login_confirm_unknown_device
import me.him188.ani.app.ui.lang.qr_login_confirm_warning
import me.him188.ani.app.ui.lang.qr_login_done
import me.him188.ani.app.ui.lang.qr_login_expired
import me.him188.ani.app.ui.lang.qr_login_failed
import me.him188.ani.app.ui.lang.qr_login_rejected_title
import me.him188.ani.app.ui.lang.qr_login_requires_login
import me.him188.ani.app.ui.lang.qr_login_retry
import org.jetbrains.compose.resources.stringResource

@Composable
fun QrLoginConfirmScreen(
    vm: QrLoginConfirmViewModel,
    onNavigateBack: () -> Unit,
    onNavigateLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nickname by vm.selfNickname.collectAsStateWithLifecycle(null)
    QrLoginConfirmScreen(
        state = state,
        selfNickname = nickname,
        onConfirm = vm::confirm,
        onRetry = vm::load,
        onNavigateBack = onNavigateBack,
        onNavigateLogin = onNavigateLogin,
        modifier = modifier,
    )
}

@Composable
fun QrLoginConfirmScreen(
    state: QrLoginConfirmUiState,
    selfNickname: String?,
    onConfirm: (approve: Boolean) -> Unit,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Lang.qr_login_confirm_title)) },
                navigationIcon = {
                    IconButton(onNavigateBack) { Icon(Icons.Rounded.Close, stringResource(Lang.qr_login_close)) }
                },
                windowInsets = AniWindowInsets.forTopAppBar(),
            )
        },
        contentWindowInsets = AniWindowInsets.forPageContent(),
    ) { contentPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 480.dp)
                .padding(contentPadding)
                .padding(horizontal = 24.dp),
        ) {
            when (state) {
                QrLoginConfirmUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                is QrLoginConfirmUiState.Confirming -> ConfirmingContent(state, selfNickname, onConfirm)

                is QrLoginConfirmUiState.Approved -> ResultContent(
                    Icons.Rounded.Check,
                    QrLoginConfirmDefaults.SuccessContainerColor,
                    QrLoginConfirmDefaults.SuccessContentColor,
                    title = stringResource(Lang.qr_login_approved_title),
                    description = stringResource(
                        Lang.qr_login_approved_description,
                        state.deviceName ?: stringResource(Lang.qr_login_confirm_unknown_device),
                    ),
                ) { PrimaryButton(stringResource(Lang.qr_login_done), onNavigateBack) }

                QrLoginConfirmUiState.Rejected -> ResultContent(
                    Icons.Rounded.Block,
                    title = stringResource(Lang.qr_login_rejected_title),
                ) { PrimaryButton(stringResource(Lang.qr_login_done), onNavigateBack) }

                QrLoginConfirmUiState.Expired -> ResultContent(
                    Icons.Rounded.ErrorOutline,
                    title = stringResource(Lang.qr_login_expired),
                ) { PrimaryButton(stringResource(Lang.qr_login_close), onNavigateBack) }

                QrLoginConfirmUiState.AlreadyHandled -> ResultContent(
                    Icons.Rounded.ErrorOutline,
                    title = stringResource(Lang.qr_login_already_handled),
                ) { PrimaryButton(stringResource(Lang.qr_login_close), onNavigateBack) }

                QrLoginConfirmUiState.RequiresLogin -> ResultContent(
                    Icons.Rounded.ErrorOutline,
                    title = stringResource(Lang.qr_login_requires_login),
                ) { PrimaryButton(stringResource(Lang.login_sign_in), onNavigateLogin) }

                is QrLoginConfirmUiState.Failed -> ResultContent(
                    Icons.Rounded.ErrorOutline,
                    title = stringResource(Lang.qr_login_failed),
                ) { PrimaryButton(stringResource(Lang.qr_login_retry), onRetry) }
            }
        }
    }
}

@Composable
private fun ColumnScope.ConfirmingContent(
    state: QrLoginConfirmUiState.Confirming,
    selfNickname: String?,
    onConfirm: (approve: Boolean) -> Unit,
) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusIcon(
            Icons.Rounded.Tv,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            stringResource(Lang.qr_login_confirm_headline),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            if (selfNickname != null) stringResource(Lang.qr_login_confirm_as, selfNickname)
            else stringResource(Lang.qr_login_confirm_as_current),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Tv, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        state.deviceName ?: stringResource(Lang.qr_login_confirm_unknown_device),
                        Modifier.testTag("qr-login-device-name"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(Lang.qr_login_confirm_device_requesting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Outlined.Shield, null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Lang.qr_login_confirm_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.error != null) {
            Text(
                stringResource(Lang.qr_login_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            { onConfirm(true) },
            Modifier.fillMaxWidth().testTag("qr-login-approve"),
            enabled = !state.submitting,
        ) { Text(stringResource(Lang.qr_login_confirm_approve)) }
        OutlinedButton(
            { onConfirm(false) },
            Modifier.fillMaxWidth().testTag("qr-login-reject"),
            enabled = !state.submitting,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(Lang.qr_login_confirm_reject)) }
    }
}

/** 流程结束或无法继续时的居中提示, 底部是唯一的操作. */
@Composable
private fun ColumnScope.ResultContent(
    icon: ImageVector,
    iconContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    iconContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    title: String,
    description: String? = null,
    action: @Composable () -> Unit,
) {
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusIcon(icon, iconContainerColor, iconContentColor)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp)) { action() }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().testTag("qr-login-primary-action")) { Text(text) }
}

@Composable
private fun StatusIcon(icon: ImageVector, containerColor: Color, contentColor: Color) {
    Box(Modifier.size(96.dp).background(containerColor, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(48.dp), tint = contentColor)
    }
}

private object QrLoginConfirmDefaults {
    val SuccessContainerColor = Color(0xFFD7EFD9)
    val SuccessContentColor = Color(0xFF1E6B2B)
}
