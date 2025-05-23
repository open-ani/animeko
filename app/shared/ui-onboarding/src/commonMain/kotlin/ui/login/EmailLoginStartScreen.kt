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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun EmailLoginStartScreen(
    onOtpSent: () -> Unit,
    onBangumiLoginClick: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: EmailLoginViewModel = viewModel<EmailLoginViewModel> { EmailLoginViewModel() },
) {
    val state = vm.state
    val asyncHandler = rememberAsyncHandler()
    EmailLoginStartScreenImpl(
        state.email,
        onEmailChange = { vm.setEmail(email = state.email) },
        onContinueClick = {
            asyncHandler.launch {
                vm.sendEmailOtp()
                onOtpSent()
            }
        },
        onBangumiLoginClick,
        onNavigateSettings,
        onNavigateBack,
        enabled = !asyncHandler.isWorking,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EmailLoginStartScreenImpl(
    email: String,
    onEmailChange: (String) -> Unit,
    onContinueClick: () -> Unit,
    onBangumiLoginClick: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    EmailLoginScreenLayout(
        onBangumiLoginClick,
        onNavigateSettings,
        onNavigateBack,
        modifier,
    ) {
        CenteredSectionHeader(
            title = { Text("你的邮箱地址") },
            description = { Text("我们将发送一封验证码邮件") },
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            email,
            { onEmailChange(it.trim()) },
            Modifier.fillMaxWidth(),
            label = {
                Text("邮箱")
            },
            isError = email.isNotEmpty() && !email.contains('@'),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions {
                onContinueClick()
            },
            trailingIcon = if (email.isNotEmpty()) {
                {
                    IconButton({ onEmailChange("") }) {
                        Icon(Icons.Outlined.Close, "清空")
                    }
                }
            } else null,
            enabled = enabled,
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onContinueClick,
            Modifier.align(Alignment.End),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            enabled = enabled,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("继续")
        }
    }
}

@Composable
@Preview
private fun PreviewEmailLoginStartScreen() = ProvideCompositionLocalsForPreview {
    EmailLoginStartScreenImpl(
        "test@openani.org",
        onEmailChange = {},
        {}, {}, {}, {},
    )
}
