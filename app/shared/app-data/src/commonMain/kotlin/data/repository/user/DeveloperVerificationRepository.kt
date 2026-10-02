/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.user

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.DeveloperVerificationAniApi
import me.him188.ani.client.models.AniDeveloperVerificationRequestStatus
import me.him188.ani.client.models.AniDeveloperVerificationState
import me.him188.ani.utils.ktor.ApiInvoker

/**
 * 开发者认证: 绑定了 GitHub 的用户可以申请, 服务器异步判定其是否给 Animeko 贡献过代码.
 */
interface DeveloperVerificationRepository {
    /**
     * @throws RepositoryException
     */
    suspend fun getState(): DeveloperVerificationInfo

    /**
     * 提交申请. 判定是异步的, 返回的状态通常是 [DeveloperVerificationRequestStatus.PENDING], 需要轮询 [getState].
     *
     * @throws RepositoryException 不满足申请条件 (见 [DeveloperVerificationInfo.canApply]) 时服务器会拒绝
     */
    suspend fun apply(): DeveloperVerificationInfo
}

data class DeveloperVerificationInfo(
    /**
     * 服务器是否启用了开发者认证
     */
    val enabled: Boolean,
    val isDeveloper: Boolean,
    /**
     * 开发者身份的有效期 (毫秒时间戳). 到期时服务器自动重新判定, 用户无需再申请.
     * 长期有效 ([isOutstandingContributor]) 时为 `null`.
     */
    val validUntil: Long?,
    /**
     * 开发者身份是作为卓越贡献者 (服务器维护的名单) 申请得到的: 申请后直接通过, 长期有效, 没有有效期.
     */
    val isOutstandingContributor: Boolean = false,
    val latestRequest: DeveloperVerificationRequestInfo?,
    /**
     * 因申请频率限制而不能申请时, 最早可以再次申请的时间 (毫秒时间戳)
     */
    val nextApplyAt: Long?,
) {
    val isPending: Boolean get() = latestRequest?.status == DeveloperVerificationRequestStatus.PENDING

    val canApply: Boolean get() = enabled && !isDeveloper && !isPending && nextApplyAt == null
}

data class DeveloperVerificationRequestInfo(
    val status: DeveloperVerificationRequestStatus,
    val createdAt: Long,
    /**
     * 服务器给出的判定说明, 可以直接展示给用户
     */
    val message: String?,
    val pullRequestUrl: String?,
)

enum class DeveloperVerificationRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,

    /**
     * 服务器的问题导致无法判定, 不计入申请次数
     */
    FAILED,
}

class DefaultDeveloperVerificationRepository(
    private val api: ApiInvoker<DeveloperVerificationAniApi>,
) : DeveloperVerificationRepository {
    override suspend fun getState(): DeveloperVerificationInfo = request { getDeveloperVerificationState().body() }

    override suspend fun apply(): DeveloperVerificationInfo = request { applyForDeveloperVerification().body() }

    private suspend fun request(
        block: suspend DeveloperVerificationAniApi.() -> AniDeveloperVerificationState,
    ): DeveloperVerificationInfo = withContext(Dispatchers.Default) {
        try {
            api.invoke(block).toInfo()
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

private fun AniDeveloperVerificationState.toInfo() = DeveloperVerificationInfo(
    enabled = enabled,
    isDeveloper = isDeveloper,
    validUntil = validUntil,
    isOutstandingContributor = outstandingContributor,
    latestRequest = latestRequest?.let {
        DeveloperVerificationRequestInfo(
            status = when (it.status) {
                AniDeveloperVerificationRequestStatus.PENDING -> DeveloperVerificationRequestStatus.PENDING
                AniDeveloperVerificationRequestStatus.APPROVED -> DeveloperVerificationRequestStatus.APPROVED
                AniDeveloperVerificationRequestStatus.REJECTED -> DeveloperVerificationRequestStatus.REJECTED
                AniDeveloperVerificationRequestStatus.FAILED -> DeveloperVerificationRequestStatus.FAILED
            },
            createdAt = it.createdAt,
            message = it.message,
            pullRequestUrl = it.pullRequestUrl,
        )
    },
    nextApplyAt = nextApplyAt,
)
