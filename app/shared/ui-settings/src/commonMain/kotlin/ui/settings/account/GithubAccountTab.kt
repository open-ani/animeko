/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
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
import me.him188.ani.app.ui.lang.settings_account_developer_badge_certified
import me.him188.ani.app.ui.lang.settings_account_developer_badge_pending
import me.him188.ani.app.ui.lang.settings_account_developer_benefit
import me.him188.ani.app.ui.lang.settings_account_developer_description
import me.him188.ani.app.ui.lang.settings_account_developer_group
import me.him188.ani.app.ui.lang.settings_account_developer_load_failed
import me.him188.ani.app.ui.lang.settings_account_developer_next_apply_at
import me.him188.ani.app.ui.lang.settings_account_developer_requirement_code
import me.him188.ani.app.ui.lang.settings_account_developer_requirement_docs
import me.him188.ani.app.ui.lang.settings_account_developer_requirement_merged
import me.him188.ani.app.ui.lang.settings_account_developer_result_failed
import me.him188.ani.app.ui.lang.settings_account_developer_result_failed_title
import me.him188.ani.app.ui.lang.settings_account_developer_result_rejected
import me.him188.ani.app.ui.lang.settings_account_developer_result_rejected_encouragement
import me.him188.ani.app.ui.lang.settings_account_developer_status_certified
import me.him188.ani.app.ui.lang.settings_account_developer_status_not_certified
import me.him188.ani.app.ui.lang.settings_account_developer_status_pending
import me.him188.ani.app.ui.lang.settings_account_developer_status_unavailable
import me.him188.ani.app.ui.lang.settings_account_developer_valid_until
import me.him188.ani.app.ui.lang.settings_account_github_bound
import me.him188.ani.app.ui.lang.settings_account_loading
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.utils.logging.warn
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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
    GithubAccountTabImpl(
        state,
        onApply = { asyncHandler.launch { vm.apply() } },
        onRetry = { vm.reload() },
        isApplying = asyncHandler.isWorking,
        modifier = modifier,
    )
}

@Composable
internal fun GithubAccountTabImpl(
    state: GithubAccountUiState,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    isApplying: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AccountHero(state.username)

        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val verification = state.verification
            val isPending = verification != null && (verification.isPending || isApplying)

            DeveloperCertificationCard(
                status = when {
                    verification == null || (!verification.enabled && !verification.isDeveloper) -> null
                    verification.isDeveloper -> CertificationStatus.CERTIFIED
                    isPending -> CertificationStatus.PENDING
                    else -> CertificationStatus.NOT_CERTIFIED
                },
            )

            when {
                verification == null -> LoadingCard(state.loadFailed, onRetry)

                !verification.enabled && !verification.isDeveloper -> Banner(
                    icon = Icons.Outlined.Info,
                    title = stringResource(Lang.settings_account_developer_status_unavailable),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                verification.isDeveloper -> Banner(
                    icon = Icons.Rounded.Verified,
                    title = stringResource(Lang.settings_account_developer_status_certified),
                    text = verification.validUntil?.let {
                        stringResource(Lang.settings_account_developer_valid_until, formatDate(it))
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("developerVerification-validUntil"),
                )

                else -> ApplySection(verification, isPending, onApply)
            }
        }
    }
}

@Composable
private fun AccountHero(username: String?) {
    Column(
        Modifier.padding(top = 24.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                OAuthPlatformIcon(OAuthPlatform.GITHUB, Modifier.size(44.dp))
            }
        }
        Text(
            username?.let { "@$it" }.orEmpty(),
            Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(Lang.settings_account_github_bound),
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class CertificationStatus { NOT_CERTIFIED, PENDING, CERTIFIED }

/**
 * 说明开发者认证是什么: 标题, 当前状态, 认证条件与权益. [status] 为 `null` 时不显示状态.
 */
@Composable
private fun DeveloperCertificationCard(status: CertificationStatus?) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Lang.settings_account_developer_group),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (status != null) StatusBadge(status)
            }
            Text(
                stringResource(Lang.settings_account_developer_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Requirement(met = true, stringResource(Lang.settings_account_developer_requirement_merged))
                Requirement(met = true, stringResource(Lang.settings_account_developer_requirement_code))
                Requirement(met = false, stringResource(Lang.settings_account_developer_requirement_docs))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Block, null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(Lang.settings_account_developer_benefit),
                    Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: CertificationStatus) {
    val colors = MaterialTheme.colorScheme
    val (container, content, text) = when (status) {
        CertificationStatus.CERTIFIED ->
            Triple(colors.primary, colors.onPrimary, Lang.settings_account_developer_badge_certified)

        CertificationStatus.PENDING ->
            Triple(colors.tertiaryContainer, colors.onTertiaryContainer, Lang.settings_account_developer_badge_pending)

        CertificationStatus.NOT_CERTIFIED ->
            Triple(colors.surfaceContainerHighest, colors.onSurfaceVariant, Lang.settings_account_developer_status_not_certified)
    }
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier.testTag("developerVerification-status"),
    ) {
        Text(
            stringResource(text),
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun Requirement(met: Boolean, text: String) {
    Row { // 图标与第一行文字对齐: 文字可能换行
        Icon(
            if (met) Icons.Rounded.Check else Icons.Rounded.Close, null,
            Modifier.size(20.dp),
            tint = if (met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Text(
            text,
            Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (met) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 还不是开发者: 上次申请的结果, 申请按钮, 以及申请频率的说明.
 */
@Composable
private fun ApplySection(
    verification: DeveloperVerificationInfo,
    isPending: Boolean,
    onApply: () -> Unit,
) {
    val latest = verification.latestRequest
    if (!isPending && latest != null) {
        when (latest.status) {
            // 不展示服务器给出的具体原因: 那是对用户的 PR 的评价. 只告知结果, 并鼓励继续贡献后再申请
            DeveloperVerificationRequestStatus.REJECTED -> Banner(
                icon = Icons.Outlined.Info,
                title = stringResource(Lang.settings_account_developer_result_rejected),
                text = stringResource(Lang.settings_account_developer_result_rejected_encouragement),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("developerVerification-lastResult"),
            )

            // 服务器自己的问题, 与用户的 PR 无关
            DeveloperVerificationRequestStatus.FAILED -> Banner(
                icon = Icons.Rounded.ErrorOutline,
                title = stringResource(Lang.settings_account_developer_result_failed_title),
                text = stringResource(Lang.settings_account_developer_result_failed),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                iconTint = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("developerVerification-lastResult"),
            )

            DeveloperVerificationRequestStatus.PENDING,
            DeveloperVerificationRequestStatus.APPROVED -> {}
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isPending) {
            Button(onClick = {}, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = false) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
                Text(stringResource(Lang.settings_account_developer_status_pending), Modifier.padding(start = 12.dp))
            }
        } else {
            Button(
                onClick = onApply,
                Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("developerVerification-apply"),
                enabled = verification.canApply,
            ) {
                Text(stringResource(Lang.settings_account_developer_apply))
            }
        }
        val nextApplyAt = verification.nextApplyAt
        Text(
            if (nextApplyAt != null && !isPending) {
                stringResource(Lang.settings_account_developer_next_apply_at, formatDateTime(nextApplyAt))
            } else {
                stringResource(Lang.settings_account_developer_apply_limit)
            },
            Modifier.padding(top = 12.dp).testTag("developerVerification-limit"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingCard(loadFailed: Boolean, onRetry: () -> Unit) {
    Surface(
        onClick = onRetry,
        enabled = loadFailed,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().testTag("developerVerification-loading"),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!loadFailed) CircularProgressIndicator(Modifier.padding(end = 12.dp).size(18.dp), strokeWidth = 2.dp)
            Text(
                stringResource(
                    if (loadFailed) Lang.settings_account_developer_load_failed else Lang.settings_account_loading,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Banner(
    icon: ImageVector,
    title: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    text: String? = null,
    iconTint: Color = contentColor,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = iconTint)
            Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * `yyyy-MM-dd`. 有效期只需要精确到天, 而且总是在未来, 不适合用相对时间.
 */
private fun formatDate(timestampMillis: Long): String =
    Instant.fromEpochMilliseconds(timestampMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
