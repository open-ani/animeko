/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_artifact
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_cancel
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_close
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_confirm_download
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_confirm_install
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_confirm_message_android
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_confirm_message_desktop
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_confirm_message_manual
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_confirm_title
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_current_version
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_download
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_downloading
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_empty
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_extracting
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_failure_package_not_found
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_failure_token_required
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_install
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_installing
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_load_failed
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_manual_install_hint
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_manual_message
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_manual_reveal
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_manual_title
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_package_available
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_package_unavailable
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_rate_limited
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_refresh
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_retry
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_running
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_status_cancelled
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_status_failure
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_status_in_progress
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_status_no_build
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_status_other
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_status_queued
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_status_success
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_token
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_token_description
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_token_placeholder
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_token_not_set
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_token_save
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_token_set
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_unauthorized
import me.him188.ani.app.ui.lang.settings_debug_dev_builds_unsupported_platform
import me.him188.ani.app.ui.update.FailedToInstallDialog
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.utils.io.absolutePath
import org.jetbrains.compose.resources.stringResource

object DevBuildsTestTags {
    const val REFRESH_BUTTON = "dev_builds_refresh"
    const val TOKEN_BUTTON = "dev_builds_token_button"
    const val TOKEN_FIELD = "dev_builds_token"
    const val TOKEN_SAVE_BUTTON = "dev_builds_token_save"
    const val RETRY_BUTTON = "dev_builds_retry"

    /**
     * 后接 commit sha
     */
    const val COMMIT_PREFIX = "dev_builds_commit_"

    /**
     * 后接 commit sha
     */
    const val INSTALL_BUTTON_PREFIX = "dev_builds_install_"

    /**
     * 后接 commit sha
     */
    const val CANCEL_BUTTON_PREFIX = "dev_builds_cancel_"
    const val CONFIRM_BUTTON = "dev_builds_confirm"
    const val CONFIRM_CANCEL_BUTTON = "dev_builds_confirm_cancel"
}

/**
 * 开发者功能「安装 main 分支的指定 commit」页面.
 */
@Composable
fun DevBuildsTab(
    modifier: Modifier = Modifier,
    vm: DevBuildsViewModel = viewModel { DevBuildsViewModel() },
) {
    val state = vm.state
    if (state == null) {
        Box(modifier.padding(horizontal = 16.dp, vertical = 16.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                stringResource(Lang.settings_debug_dev_builds_unsupported_platform),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val token by vm.gitHubToken.collectAsStateWithLifecycle()
    DevBuildsTabContent(
        state = state,
        token = token,
        onTokenChange = vm::setGitHubToken,
        modifier = modifier,
    )
}

/**
 * @param token 已保存的 GitHub token. 用户编辑完成 (失焦或按下完成) 时通过 [onTokenChange] 提交.
 */
@Composable
fun DevBuildsTabContent(
    state: DevBuildsState,
    token: String,
    onTokenChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentVersion: String = currentAniBuildConfig.versionName,
) {
    LaunchedEffect(state) {
        if (state.listState.value is DevBuildListState.Idle) {
            state.refresh()
        }
    }
    val listState by state.listState.collectAsStateWithLifecycle()
    val isRefreshing by state.isRefreshing.collectAsStateWithLifecycle()
    val installState by state.installState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val timeFormatter = remember { TimeFormatter() }
    var pendingInstall by remember { mutableStateOf<DevBuildCommit?>(null) }

    // 列表最多几十项, 不需要懒加载; 由外层的设置页容器提供滚动
    Column(
        modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DevBuildsHeader(
            spec = state.spec,
            currentVersion = currentVersion,
            token = token,
            onTokenChange = onTokenChange,
            isRefreshing = isRefreshing,
            onRefresh = state::refresh,
        )
        when (val list = listState) {
            DevBuildListState.Idle -> {}
            is DevBuildListState.Failed -> DevBuildsErrorCard(list.throwable, onRetry = state::refresh)

            is DevBuildListState.Loaded -> {
                if (list.commits.isEmpty()) {
                    Text(
                        stringResource(Lang.settings_debug_dev_builds_empty),
                        Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column {
                            list.commits.forEachIndexed { index, commit ->
                                key(commit.sha) {
                                    if (index > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                    DevBuildCommitRow(
                                        commit = commit,
                                        isCurrent = state.isCurrentCommit(commit),
                                        supportsAutomaticInstall = state.spec.kind.supportsAutomaticInstall,
                                        installState = installState,
                                        timeFormatter = timeFormatter,
                                        onClickInstall = { pendingInstall = commit },
                                        onClickCancel = state::cancelInstall,
                                        onClick = {
                                            uriHandler.openUri(commit.build?.htmlUrl?.ifBlank { null } ?: commit.htmlUrl)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingInstall?.let { commit ->
        ConfirmInstallDevBuildDialog(
            commit = commit,
            kind = state.spec.kind,
            onConfirm = {
                pendingInstall = null
                state.install(commit, context)
            },
            onDismissRequest = { pendingInstall = null },
        )
    }

    when (val install = installState) {
        is DevBuildInstallState.Failed -> FailedToInstallDialog(
            message = installFailureMessage(install.failure, state.spec.kind),
            onDismissRequest = state::dismissInstallResult,
            file = install.file,
        )

        is DevBuildInstallState.ReadyForManualInstall -> AlertDialog(
            onDismissRequest = state::dismissInstallResult,
            confirmButton = {
                TextButton({ scope.launch { state.revealPackage(install.file, context) } }) {
                    Text(stringResource(Lang.settings_debug_dev_builds_manual_reveal))
                }
            },
            dismissButton = {
                TextButton(state::dismissInstallResult) {
                    Text(stringResource(Lang.settings_debug_dev_builds_close))
                }
            },
            title = { Text(stringResource(Lang.settings_debug_dev_builds_manual_title)) },
            text = { Text(stringResource(Lang.settings_debug_dev_builds_manual_message, install.file.absolutePath)) },
        )

        else -> {}
    }
}

@Composable
private fun DevBuildsHeader(
    spec: DevBuildPackageSpec,
    currentVersion: String,
    token: String,
    onTokenChange: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var editingToken by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOf(
                    stringResource(Lang.settings_debug_dev_builds_current_version, currentVersion),
                    stringResource(Lang.settings_debug_dev_builds_artifact, spec.artifactNames.joinToString(" / ")),
                    stringResource(
                        if (token.isBlank()) Lang.settings_debug_dev_builds_token_not_set
                        else Lang.settings_debug_dev_builds_token_set,
                    ),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            if (!spec.kind.supportsAutomaticInstall) {
                Text(
                    stringResource(Lang.settings_debug_dev_builds_manual_install_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        IconButton({ editingToken = true }, Modifier.testTag(DevBuildsTestTags.TOKEN_BUTTON)) {
            Icon(Icons.Rounded.Key, contentDescription = stringResource(Lang.settings_debug_dev_builds_token))
        }
        if (isRefreshing) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onRefresh, Modifier.testTag(DevBuildsTestTags.REFRESH_BUTTON)) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(Lang.settings_debug_dev_builds_refresh),
                )
            }
        }
    }
    if (editingToken) {
        GitHubTokenDialog(
            token = token,
            onConfirm = {
                onTokenChange(it)
                editingToken = false
            },
            onDismissRequest = { editingToken = false },
        )
    }
}

@Composable
private fun GitHubTokenDialog(
    token: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var draft by remember { mutableStateOf(token) }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton({ onConfirm(draft.trim()) }, Modifier.testTag(DevBuildsTestTags.TOKEN_SAVE_BUTTON)) {
                Text(stringResource(Lang.settings_debug_dev_builds_token_save))
            }
        },
        dismissButton = {
            TextButton(onDismissRequest) {
                Text(stringResource(Lang.settings_debug_dev_builds_cancel))
            }
        },
        title = { Text(stringResource(Lang.settings_debug_dev_builds_token)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().testTag(DevBuildsTestTags.TOKEN_FIELD),
                    placeholder = { Text(stringResource(Lang.settings_debug_dev_builds_token_placeholder)) },
                    trailingIcon = {
                        IconButton({ visible = !visible }) {
                            Icon(
                                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(draft.trim()) }),
                    singleLine = true,
                )
                Text(
                    stringResource(Lang.settings_debug_dev_builds_token_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun DevBuildsErrorCard(
    throwable: Throwable,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Lang.settings_debug_dev_builds_load_failed, loadErrorMessage(throwable)),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onRetry, Modifier.testTag(DevBuildsTestTags.RETRY_BUTTON)) {
                Text(stringResource(Lang.settings_debug_dev_builds_retry))
            }
        }
    }
}

@Composable
private fun loadErrorMessage(throwable: Throwable): String = when {
    throwable is GitHubApiException && throwable.isRateLimited -> stringResource(Lang.settings_debug_dev_builds_rate_limited)
    throwable is GitHubApiException && throwable.isUnauthorized -> stringResource(Lang.settings_debug_dev_builds_unauthorized)
    else -> throwable.message?.takeIf { it.isNotBlank() } ?: throwable.toString()
}

@Composable
private fun installFailureMessage(failure: DevBuildInstallFailure, kind: DevBuildPackageKind): String = when (failure) {
    DevBuildInstallFailure.TokenRequired -> stringResource(Lang.settings_debug_dev_builds_failure_token_required)
    DevBuildInstallFailure.PackageNotFound ->
        stringResource(Lang.settings_debug_dev_builds_failure_package_not_found, kind.packageExtension)

    is DevBuildInstallFailure.Installer -> failure.result.message ?: failure.result.reason.toString()
    is DevBuildInstallFailure.Error -> loadErrorMessage(failure.throwable)
}

@Composable
private fun DevBuildCommitRow(
    commit: DevBuildCommit,
    isCurrent: Boolean,
    supportsAutomaticInstall: Boolean,
    installState: DevBuildInstallState,
    timeFormatter: TimeFormatter,
    onClickInstall: () -> Unit,
    onClickCancel: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val busy = (installState as? DevBuildInstallState.Busy)?.takeIf { it.commit.sha == commit.sha }
    val anyBusy = installState is DevBuildInstallState.Busy
    val meta = remember(commit, timeFormatter) {
        listOfNotNull(
            commit.author.takeIf { it.isNotBlank() },
            commit.committedAt?.let { timeFormatter.format(it) },
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .testTag(DevBuildsTestTags.COMMIT_PREFIX + commit.sha)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    commit.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        commit.shortSha,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant,
                    )
                    BuildStatusChip(commit.build?.status)
                    if (isCurrent) {
                        Chip(
                            stringResource(Lang.settings_debug_dev_builds_running),
                            colors.secondaryContainer,
                            colors.onSecondaryContainer,
                        )
                    }
                    Text(
                        (meta + listOf(
                            commit.artifact?.let {
                                stringResource(
                                    Lang.settings_debug_dev_builds_package_available,
                                    it.sizeInBytes.bytes.toString(),
                                )
                            } ?: stringResource(Lang.settings_debug_dev_builds_package_unavailable),
                        )).joinToString(" · "),
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (busy != null) {
                TextButton(
                    onClickCancel,
                    Modifier.height(32.dp).testTag(DevBuildsTestTags.CANCEL_BUTTON_PREFIX + commit.sha),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(stringResource(Lang.settings_debug_dev_builds_cancel), maxLines = 1)
                }
            } else {
                FilledTonalButton(
                    onClickInstall,
                    Modifier.height(32.dp).testTag(DevBuildsTestTags.INSTALL_BUTTON_PREFIX + commit.sha),
                    enabled = commit.artifact != null && !anyBusy,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(
                        stringResource(
                            if (supportsAutomaticInstall) Lang.settings_debug_dev_builds_install
                            else Lang.settings_debug_dev_builds_download,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        if (busy != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val progress = (busy as? DevBuildInstallState.Downloading)?.progress
                if (progress != null) {
                    LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Text(
                    when (busy) {
                        is DevBuildInstallState.Downloading -> stringResource(
                            Lang.settings_debug_dev_builds_downloading,
                            busy.downloadedBytes.bytes.toString(),
                            busy.totalBytes?.bytes?.toString() ?: "?",
                        )

                        is DevBuildInstallState.Extracting -> stringResource(Lang.settings_debug_dev_builds_extracting)
                        is DevBuildInstallState.Installing -> stringResource(Lang.settings_debug_dev_builds_installing)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BuildStatusChip(status: DevBuildStatus?) {
    val colors = MaterialTheme.colorScheme
    val (text, container, content) = when (status) {
        DevBuildStatus.SUCCESS -> Triple(
            stringResource(Lang.settings_debug_dev_builds_status_success),
            colors.primaryContainer,
            colors.onPrimaryContainer,
        )

        DevBuildStatus.FAILURE -> Triple(
            stringResource(Lang.settings_debug_dev_builds_status_failure),
            colors.errorContainer,
            colors.onErrorContainer,
        )

        DevBuildStatus.IN_PROGRESS -> Triple(
            stringResource(Lang.settings_debug_dev_builds_status_in_progress),
            colors.tertiaryContainer,
            colors.onTertiaryContainer,
        )

        DevBuildStatus.QUEUED -> Triple(
            stringResource(Lang.settings_debug_dev_builds_status_queued),
            colors.tertiaryContainer,
            colors.onTertiaryContainer,
        )

        DevBuildStatus.CANCELLED -> Triple(
            stringResource(Lang.settings_debug_dev_builds_status_cancelled),
            colors.surfaceVariant,
            colors.onSurfaceVariant,
        )

        DevBuildStatus.OTHER -> Triple(
            stringResource(Lang.settings_debug_dev_builds_status_other),
            colors.surfaceVariant,
            colors.onSurfaceVariant,
        )

        null -> Triple(
            stringResource(Lang.settings_debug_dev_builds_status_no_build),
            colors.surfaceVariant,
            colors.onSurfaceVariant,
        )
    }
    Chip(text, container, content)
}

@Composable
private fun Chip(text: String, containerColor: Color, contentColor: Color) {
    Surface(shape = CircleShape, color = containerColor, contentColor = contentColor) {
        Text(
            text,
            Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConfirmInstallDevBuildDialog(
    commit: DevBuildCommit,
    kind: DevBuildPackageKind,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val artifact = commit.artifact ?: return
    val message = when (kind) {
        DevBuildPackageKind.WINDOWS_PORTABLE_ZIP, DevBuildPackageKind.MACOS_DMG ->
            Lang.settings_debug_dev_builds_confirm_message_desktop

        DevBuildPackageKind.ANDROID_APK -> Lang.settings_debug_dev_builds_confirm_message_android
        DevBuildPackageKind.LINUX_APPIMAGE -> Lang.settings_debug_dev_builds_confirm_message_manual
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(onConfirm, Modifier.testTag(DevBuildsTestTags.CONFIRM_BUTTON)) {
                Text(
                    stringResource(
                        if (kind.supportsAutomaticInstall) Lang.settings_debug_dev_builds_confirm_install
                        else Lang.settings_debug_dev_builds_confirm_download,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onDismissRequest, Modifier.testTag(DevBuildsTestTags.CONFIRM_CANCEL_BUTTON)) {
                Text(stringResource(Lang.settings_debug_dev_builds_cancel))
            }
        },
        title = { Text(stringResource(Lang.settings_debug_dev_builds_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        commit.shortSha,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        commit.title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(stringResource(message, artifact.name, artifact.sizeInBytes.bytes.toString()))
            }
        },
    )
}
