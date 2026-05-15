/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.*

@Composable
fun EmailLoginStartScreen(
    onOtpSent: () -> Unit,
    onBangumiLoginClick: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: EmailLoginViewModel = viewModel<EmailLoginViewModel> { EmailLoginViewModel() },
) {
    val state by vm.state.collectAsStateWithLifecycle(EmailLoginUiState.Initial)
    val asyncHandler = rememberAsyncHandler()

    EmailLoginStartScreenImpl(
        state.email,
        onContinueClick = {
            asyncHandler.launch {
                vm.setEmail(it.trim())
                vm.sendEmailOtp()
                onOtpSent()
            }
        },
        onBangumiLoginClick,
        onNavigateSettings,
        onNavigateBack,
        enabled = !asyncHandler.isWorking,
        showThirdPartyLogin = state.mode == EmailLoginUiState.Mode.LOGIN,
        title = { EmailPageTitle(state.mode, state.isExistingAccount) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EmailLoginStartScreenImpl(
    email: String,
    onContinueClick: (currentEmail: String) -> Unit,
    onBangumiLoginClick: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit = { Text(stringResource(Lang.login_sign_in)) },
    enabled: Boolean = true,
    showThirdPartyLogin: Boolean = true,
) {
    EmailLoginScreenLayout(
        onBangumiLoginClick,
        onNavigateSettings,
        onNavigateBack,
        modifier,
        title,
        showThirdPartyLogin,
    ) { scrollState ->
        CenteredSectionHeader(
            title = { Text(stringResource(Lang.login_email_address_title)) },
            description = { Text(stringResource(Lang.login_email_address_description)) },
        )

        Spacer(Modifier.height(8.dp))

        var currentEmailContent by rememberSaveable { mutableStateOf(email) }
        OutlinedTextField(
            currentEmailContent,
            { currentEmailContent = it.trim() },
            Modifier.fillMaxWidth(),
            label = {
                Text(stringResource(Lang.login_email_label))
            },
            isError = currentEmailContent.isNotEmpty() &&
                    (!currentEmailContent.contains('@') || !currentEmailContent.contains('.')),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions {
                onContinueClick(currentEmailContent)
            },
            trailingIcon = if (currentEmailContent.isNotEmpty()) {
                {
                    IconButton({ currentEmailContent = "" }) {
                        Icon(Icons.Outlined.Close, stringResource(Lang.login_clear))
                    }
                }
            } else null,
            enabled = enabled,
        )

        Spacer(Modifier.height(16.dp))

        Button(
            { onContinueClick(currentEmailContent) },
            Modifier.align(Alignment.End),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            enabled = enabled,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(Lang.login_continue))
        }
    }
}

@Composable
internal fun EmailPageTitle(mode: EmailLoginUiState.Mode, isExistingAccount: Boolean?) {
    when (mode) {
        EmailLoginUiState.Mode.LOGIN -> when (isExistingAccount) {
            true -> Text(stringResource(Lang.login_sign_in))
            false -> Text(stringResource(Lang.login_sign_up))
            null -> Text(stringResource(Lang.login_sign_in_or_sign_up))
        }

        EmailLoginUiState.Mode.BIND -> Text(stringResource(Lang.login_bind_email))
        EmailLoginUiState.Mode.REBIND -> Text(stringResource(Lang.login_change_email))
    }
}

@Composable
@Preview
private fun PreviewEmailLoginStartScreen() = ProvideCompositionLocalsForPreview {
    EmailLoginStartScreenImpl(
        "test@openani.org",
        {}, {}, {}, {},
    )
}
