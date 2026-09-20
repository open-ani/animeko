/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.repository.user.DeveloperVerificationInfo
import me.him188.ani.app.data.repository.user.DeveloperVerificationRepository
import me.him188.ani.app.data.repository.user.DeveloperVerificationRequestStatus
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.icons.OAuthPlatformIcon
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_account_developer_apply
import me.him188.ani.app.ui.lang.settings_account_developer_apply_limit
import me.him188.ani.app.ui.lang.settings_account_developer_description
import me.him188.ani.app.ui.lang.settings_account_developer_group
import me.him188.ani.app.ui.lang.settings_account_developer_last_result
import me.him188.ani.app.ui.lang.settings_account_developer_load_failed
import me.him188.ani.app.ui.lang.settings_account_developer_next_apply_at
import me.him188.ani.app.ui.lang.settings_account_developer_result_failed
import me.him188.ani.app.ui.lang.settings_account_developer_result_rejected
import me.him188.ani.app.ui.lang.settings_account_developer_status
import me.him188.ani.app.ui.lang.settings_account_developer_status_certified
import me.him188.ani.app.ui.lang.settings_account_developer_status_not_certified
import me.him188.ani.app.ui.lang.settings_account_developer_status_pending
import me.him188.ani.app.ui.lang.settings_account_developer_status_unavailable
import me.him188.ani.app.ui.lang.settings_account_developer_valid_until
import me.him188.ani.app.ui.lang.settings_account_developer_view_pull_request
import me.him188.ani.app.ui.lang.settings_account_loading
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.utils.logging.warn
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Immutable
data class GithubAccountUiState(
    /**
     * 绑定的 GitHub 用户名
     */
    val username: String?,
    /**
     * `null` 表示还没有加载到
     */
    val verification: DeveloperVerificationInfo?,
    val loadFailed: Boolean,
) {
    companion object {
        val Loading = GithubAccountUiState(username = null, verification = null, loadFailed = false)
    }
}

@Stable
class GithubAccountViewModel : AbstractViewModel(), KoinComponent {
    private val repository: DeveloperVerificationRepository by inject()
    private val selfInfoStateProvider = SelfInfoStateProducer(koin = getKoin())

    private val _state = MutableStateFlow(GithubAccountUiState.Loading)
    val state: StateFlow<GithubAccountUiState> = _state.asStateFlow()

    init {
        backgroundScope.launch {
            selfInfoStateProvider.flow.collect { selfInfo ->
                val username = selfInfo.selfInfo?.externalAccounts.orEmpty()
                    .firstOrNull { it.provider == OAuthPlatform.GITHUB.id }?.username
                _state.update { it.copy(username = username) }
            }
        }
        reload()
    }

    fun reload() {
        backgroundScope.launch {
            _state.update { it.copy(loadFailed = false) }
            try {
                pollWhilePending(repository.getState())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to load developer verification state" }
                _state.update { it.copy(loadFailed = true) }
            }
        }
    }

    /**
     * 提交申请, 并等待服务器给出判定结果.
     */
    suspend fun apply() {
        pollWhilePending(repository.apply())
    }

    private suspend fun pollWhilePending(initial: DeveloperVerificationInfo) {
        var info = initial
        while (true) {
            _state.update { it.copy(verification = info) }
            if (!info.isPending) return
            delay(POLL_INTERVAL)
            info = repository.getState()
        }
    }

    private companion object {
        val POLL_INTERVAL = 3.seconds
    }
}

/**
 * 已绑定的 GitHub 账号的详情页: 账号信息与开发者认证.
 */
@Composable
fun GithubAccountTab(
    vm: GithubAccountViewModel = viewModel<GithubAccountViewModel> { GithubAccountViewModel() },
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val asyncHandler = rememberAsyncHandler()
    val uriHandler = LocalUriHandler.current
    GithubAccountTabImpl(
        state,
        onApply = { asyncHandler.launch { vm.apply() } },
        onRetry = { vm.reload() },
        onOpenUrl = { uriHandler.openUri(it) },
        isApplying = asyncHandler.isWorking,
        modifier = modifier,
    )
}

@Composable
internal fun GithubAccountTabImpl(
    state: GithubAccountUiState,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit,
    isApplying: Boolean,
    modifier: Modifier = Modifier,
) = SettingsTab(modifier) {
    Group(title = { Text(OAuthPlatform.GITHUB.displayName) }) {
        TextItem(
            title = { Text(state.username?.let { "@$it" }.orEmpty()) },
            icon = { OAuthPlatformIcon(OAuthPlatform.GITHUB, Modifier.size(24.dp)) },
        )
    }

    Group(
        title = { Text(stringResource(Lang.settings_account_developer_group)) },
        description = { Text(stringResource(Lang.settings_account_developer_description)) },
    ) {
        val verification = state.verification
        when {
            verification == null -> TextItem(
                title = {
                    Text(
                        stringResource(
                            if (state.loadFailed) Lang.settings_account_developer_load_failed
                            else Lang.settings_account_loading,
                        ),
                    )
                },
                onClick = if (state.loadFailed) onRetry else null,
                modifier = Modifier.testTag("developerVerification-loading"),
            )

            !verification.enabled && !verification.isDeveloper -> TextItem(
                title = { Text(stringResource(Lang.settings_account_developer_status_unavailable)) },
            )

            else -> DeveloperVerificationItems(verification, onApply, onOpenUrl, isApplying)
        }
    }
}

@Composable
private fun SettingsScope.DeveloperVerificationItems(
    verification: DeveloperVerificationInfo,
    onApply: () -> Unit,
    onOpenUrl: (String) -> Unit,
    isApplying: Boolean,
) {
    val isPending = verification.isPending || isApplying
    TextItem(
        title = { Text(stringResource(Lang.settings_account_developer_status)) },
        description = {
            Text(
                when {
                    verification.isDeveloper -> stringResource(Lang.settings_account_developer_status_certified)
                    isPending -> stringResource(Lang.settings_account_developer_status_pending)
                    else -> stringResource(Lang.settings_account_developer_status_not_certified)
                },
                color = if (verification.isDeveloper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        action = when {
            verification.isDeveloper -> null
            isPending -> {
                { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            }

            else -> {
                {
                    Button(
                        onClick = onApply,
                        enabled = verification.canApply,
                        modifier = Modifier.testTag("developerVerification-apply"),
                    ) { Text(stringResource(Lang.settings_account_developer_apply)) }
                }
            }
        },
        modifier = Modifier.testTag("developerVerification-status"),
    )

    if (verification.isDeveloper) {
        verification.validUntil?.let { validUntil ->
            TextItem(
                title = {
                    Text(
                        stringResource(Lang.settings_account_developer_valid_until, formatDate(validUntil)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                modifier = Modifier.testTag("developerVerification-validUntil"),
            )
        }
        return
    }

    // 上次申请的结果
    val latest = verification.latestRequest
    if (!isPending && latest != null && latest.status != DeveloperVerificationRequestStatus.PENDING) {
        TextItem(
            title = { Text(stringResource(Lang.settings_account_developer_last_result)) },
            description = {
                Text(
                    when (latest.status) {
                        DeveloperVerificationRequestStatus.FAILED -> stringResource(Lang.settings_account_developer_result_failed)
                        else -> latest.message ?: stringResource(Lang.settings_account_developer_result_rejected)
                    },
                )
            },
            action = latest.pullRequestUrl?.let { url ->
                {
                    TextButton({ onOpenUrl(url) }) {
                        Text(stringResource(Lang.settings_account_developer_view_pull_request))
                    }
                }
            },
            modifier = Modifier.testTag("developerVerification-lastResult"),
        )
    }

    TextItem(
        title = {
            val nextApplyAt = verification.nextApplyAt
            Text(
                if (nextApplyAt != null && !isPending) {
                    stringResource(Lang.settings_account_developer_next_apply_at, formatDateTime(nextApplyAt))
                } else {
                    stringResource(Lang.settings_account_developer_apply_limit)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.testTag("developerVerification-limit"),
    )
}

/**
 * `yyyy-MM-dd`. 有效期只需要精确到天, 而且总是在未来, 不适合用相对时间.
 */
private fun formatDate(timestampMillis: Long): String =
    Instant.fromEpochMilliseconds(timestampMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
