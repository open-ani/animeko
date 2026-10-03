/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import me.him188.ani.app.data.models.bangumi.BangumiConflictResolution
import me.him188.ani.app.data.models.bangumi.BangumiMergeState
import me.him188.ani.app.data.models.bangumi.BangumiMergeSummary
import kotlinx.coroutines.Dispatchers
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import kotlin.coroutines.CoroutineContext

/**
 * Bangumi 收藏冲突 (合并收藏) 仓库.
 *
 * 冲突由服务端在全量同步 (对账) 时发现并保存; 客户端只负责展示与提交用户的选择.
 * 所有方法都是网络请求, 失败时抛出 [RepositoryException].
 */
abstract class BangumiMergeRepository(
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {
    /**
     * 获取冲突摘要 (数量与同步状态). 会触发服务端的 `ensureSynced` (首次绑定时开始全量同步).
     */
    abstract suspend fun getSummary(): BangumiMergeSummary

    /**
     * 获取全部冲突与上次全量同步的自动合并明细.
     */
    abstract suspend fun getMergeState(): BangumiMergeState

    /**
     * 提交用户的选择. 服务端逐条应用 (找不到的冲突忽略), 返回剩余状态.
     *
     * 成功后本地收藏缓存中涉及的条目会被失效, 下次进入时从服务端重新拉取.
     *
     * @throws BangumiMergeSyncInProgressException 服务端全量同步进行中 (HTTP 409), 稍后重试.
     */
    abstract suspend fun resolve(resolutions: List<BangumiConflictResolution>): BangumiMergeState
}

/**
 * 服务端全量同步进行中 (HTTP 409), 暂时不能解决冲突. 不是 [RepositoryException], 调用方应单独处理并提示用户稍后重试.
 */
class BangumiMergeSyncInProgressException(
    message: String = "Bangumi full sync is in progress",
    cause: Throwable? = null,
) : Exception(message, cause)
