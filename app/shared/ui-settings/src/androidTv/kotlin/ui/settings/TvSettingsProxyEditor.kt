/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import me.him188.ani.app.data.models.preference.ProxyAuthorization
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_network_proxy_address
import me.him188.ani.app.ui.lang.settings_network_proxy_address_example
import me.him188.ani.app.ui.lang.settings_network_proxy_custom
import me.him188.ani.app.ui.lang.settings_network_proxy_disabled
import me.him188.ani.app.ui.lang.settings_network_proxy_optional
import me.him188.ani.app.ui.lang.settings_network_proxy_password
import me.him188.ani.app.ui.lang.settings_network_proxy_system
import me.him188.ani.app.ui.lang.settings_network_proxy_title
import me.him188.ani.app.ui.lang.settings_network_proxy_username
import me.him188.ani.app.ui.lang.tv_settings_proxy_address_invalid
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.tv.ui.foundation.layout.TvModalOverlay
import me.him188.ani.tv.ui.foundation.widgets.TvOptionModal
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow
import me.him188.ani.utils.ktor.ClientProxyConfigValidator
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvSettingsItems.network() {
    val proxy = state.proxy.default
    val title = stringResource(Lang.settings_network_proxy_title)
    action(
        "proxy", title, value = proxy.mode.title(),
        description = proxy.customConfig.url.takeIf { proxy.mode == ProxyMode.CUSTOM },
    ) { open(TvSettingsDialog.Proxy("proxy", title, proxy)) }
}

@Composable
private fun ProxyMode.title(): String = stringResource(when (this) {
    ProxyMode.DISABLED -> Lang.settings_network_proxy_disabled
    ProxyMode.SYSTEM -> Lang.settings_network_proxy_system
    ProxyMode.CUSTOM -> Lang.settings_network_proxy_custom
})

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun TvSettingsProxyEditor(
    dialog: TvSettingsDialog.Proxy,
    focus: TvFocusScope,
    onIntent: (TvSettingsIntent) -> Unit,
    onClose: () -> Unit,
) {
    var mode by remember(dialog) { mutableStateOf(dialog.config.mode) }
    var address by remember(dialog) { mutableStateOf(dialog.config.customConfig.url) }
    var username by remember(dialog) { mutableStateOf(dialog.config.customConfig.authorization?.username.orEmpty()) }
    var password by remember(dialog) { mutableStateOf(dialog.config.customConfig.authorization?.password.orEmpty()) }
    val validAddress = ClientProxyConfigValidator.isValidProxy(address.trim())
    val canSave = mode != ProxyMode.CUSTOM || validAddress
    val actionKey = editorKey(if (canSave) "save" else "cancel")
    LaunchedEffect(dialog) { focus.request(editorKey("proxy-mode-${dialog.config.mode}")) }

    TvModalOverlay(onClose = onClose, background = {}) {
        TvOptionModal(
            dialog.title, Modifier.testTag("tv-settings-proxy-editor"),
            footer = {
                EditorActions(
                    canSave,
                    save = {
                        onIntent(TvSettingsIntent.SaveProxy(
                            mode,
                            ProxyConfig(
                                address.trim(),
                                if (username.isEmpty() && password.isEmpty()) null else ProxyAuthorization(username, password),
                            ),
                        ))
                        onClose()
                    },
                    close = onClose,
                    modifier = Modifier.tvFocusHotkey(
                        focus, Key.DirectionUp to editorKey(if (mode == ProxyMode.CUSTOM) "proxy-password" else "proxy-mode-CUSTOM"),
                    ),
                    focus = focus,
                )
            },
        ) {
            // Fields and their carets scroll only when they leave the viewport.
            CompositionLocalProvider(LocalBringIntoViewSpec provides ProxyFormBringIntoViewSpec) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProxyMode.entries.forEach { option ->
                        TvOptionRow(
                            option.title(), selected = mode == option, showSelectionIndicator = false,
                            trailingIcon = if (mode == option) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                            modifier = Modifier.testTag("tv-settings-proxy-mode-$option")
                                .semantics { role = Role.RadioButton }
                                .tvFocusAnchor(focus, editorKey("proxy-mode-$option"))
                                .then(if (option == ProxyMode.CUSTOM) Modifier.tvFocusHotkey(
                                    focus, Key.DirectionDown to if (mode == ProxyMode.CUSTOM) editorKey("proxy-address") else actionKey,
                                ) else Modifier),
                        ) { mode = option }
                    }
                    if (mode == ProxyMode.CUSTOM) {
                        OutlinedTextField(
                            address, { address = it },
                            Modifier.fillMaxWidth().testTag("tv-settings-proxy-address")
                                .tvFocusAnchor(focus, editorKey("proxy-address"))
                                .tvFocusHotkey(
                                    focus, Key.DirectionUp to editorKey("proxy-mode-CUSTOM"),
                                    Key.DirectionDown to editorKey("proxy-username"),
                                ),
                            label = { Text(stringResource(Lang.settings_network_proxy_address)) },
                            supportingText = { Text(stringResource(
                                if (validAddress) Lang.settings_network_proxy_address_example else Lang.tv_settings_proxy_address_invalid,
                            )) },
                            isError = !validAddress, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focus.request(editorKey("proxy-username")) }),
                        )
                        OutlinedTextField(
                            username, { username = it },
                            Modifier.fillMaxWidth().testTag("tv-settings-proxy-username")
                                .tvFocusAnchor(focus, editorKey("proxy-username"))
                                .tvFocusHotkey(
                                    focus, Key.DirectionUp to editorKey("proxy-address"),
                                    Key.DirectionDown to editorKey("proxy-password"),
                                ),
                            label = { Text(stringResource(Lang.settings_network_proxy_username)) },
                            placeholder = { Text(stringResource(Lang.settings_network_proxy_optional)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focus.request(editorKey("proxy-password")) }),
                        )
                        OutlinedTextField(
                            password, { password = it },
                            Modifier.fillMaxWidth().testTag("tv-settings-proxy-password")
                                .tvFocusAnchor(focus, editorKey("proxy-password"))
                                .tvFocusHotkey(
                                    focus, Key.DirectionUp to editorKey("proxy-username"), Key.DirectionDown to actionKey,
                                ),
                            label = { Text(stringResource(Lang.settings_network_proxy_password)) },
                            placeholder = { Text(stringResource(Lang.settings_network_proxy_optional)) },
                            singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.request(actionKey) }),
                        )
                    }
                }
            }
        }
    }
}

private object ProxyFormBringIntoViewSpec : BringIntoViewSpec
