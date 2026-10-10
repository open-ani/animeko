/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.install

import androidx.datastore.core.DataStore
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.platform.getAniUserAgent
import me.him188.ani.client.apis.UserProfileAniApi
import me.him188.ani.client.models.AniReportInstallRequest
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 登录后向服务器上报本机的安装信息 (`PUT /users/me/install-report`), 用来区分全新的用户和在本机未登录使用了一段时间才登录的用户.
 *
 * 每个 (安装, 用户) 只上报一次. 从未登录状态登录时 ([beforeNewLogin]) 生成报告并保存为待上报, 当前用户确认是报告的用户后发送,
 * 成功后清除. 发送失败时保留, 下次启动时如果登录的还是这个用户就重试; 换了用户登录则丢弃旧的报告.
 * 启动时已经登录的用户没有经历登录, 不会生成报告.
 *
 * 上报是统计用途, 任何失败都只记录日志, 不影响登录.
 *
 * @param currentUserId 当前登录的用户 ID, 未登录时为 `null`.
 */
class InstallReporter(
    private val store: DataStore<InstallInfo>,
    private val playHistoryRepository: EpisodePlayHistoryRepository,
    private val api: ApiInvoker<UserProfileAniApi>,
    private val sessionStateProvider: SessionStateProvider,
    private val currentUserId: Flow<String?>,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) : SessionManager.NewLoginListener {
    private val logger = logger<InstallReporter>()

    /**
     * 初始化 [InstallInfo.firstLaunchAtMillis], 然后在有待上报的报告时发送.
     */
    fun start() {
        scope.launch(CoroutineName("InstallReporter")) {
            try {
                ensureFirstLaunchInitialized(hasSession = { sessionStateProvider.hasSession() })
            } catch (e: Exception) {
                RepositoryException.wrapOrThrowCancellation(e)
                logger.warn(e) { "Failed to initialize install info" }
            }

            store.data
                .map { it.pendingReport }
                .distinctUntilChanged()
                .collectLatest { pending ->
                    if (pending == null) return@collectLatest
                    // 登录的不是报告的用户时一直等待, 直到下次登录替换掉这个报告
                    currentUserId.first { it == pending.userId }
                    sendCatching(pending)
                }
        }
    }

    /**
     * 统计登录前本机的数据并保存为待上报. 由 [SessionManager] 在保存新会话之前调用,
     * 这时播放记录还没有和新用户的云端记录合并, 统计到的就是本机登录前的记录.
     */
    override suspend fun beforeNewLogin(userId: String) {
        val firstLaunchAtMillis = ensureFirstLaunchInitialized(hasSession = { false })
        val localEpisodes = playHistoryRepository.countAllRecords()
        val updated = store.updateData { info ->
            val pending = when {
                userId in info.reportedUserIds -> null
                // 同一个用户重新登录, 保留第一次登录时的统计
                info.pendingReport?.userId == userId -> info.pendingReport
                else -> PendingInstallReport(
                    userId = userId,
                    firstLaunchAtMillis = firstLaunchAtMillis,
                    localEpisodesBeforeLogin = localEpisodes,
                    hadPreviousLogin = info.hasLoggedIn,
                )
            }
            info.copy(hasLoggedIn = true, pendingReport = pending)
        }
        logger.info { "New login, pending install report: ${updated.pendingReport}" }
    }

    /**
     * 第一次调用时估计并保存首次启动时间, 之后直接返回已保存的值.
     *
     * @param hasSession 本机当前是否有登录的会话.
     */
    private suspend fun ensureFirstLaunchInitialized(hasSession: suspend () -> Boolean): Long {
        store.data.first().firstLaunchAtMillis?.let { return it }

        val hadLogin = hasSession() || playHistoryRepository.lastSyncAtMillisFlow.first() > 0
        val estimated = estimateFirstLaunchAtMillis(hadLogin)
        val info = store.updateData { info ->
            if (info.firstLaunchAtMillis != null) return@updateData info
            logger.info { "Initialized install info, firstLaunchAtMillis=$estimated, hadLogin=$hadLogin" }
            info.copy(
                firstLaunchAtMillis = estimated,
                hasLoggedIn = info.hasLoggedIn || hadLogin,
            )
        }
        return checkNotNull(info.firstLaunchAtMillis)
    }

    /**
     * 估计本机的首次启动时间. 新安装第一次启动时就是现在.
     *
     * 早于 [InstallInfo.firstLaunchAtMillis] 的安装不知道真实值, 取现在和最早一条播放记录时间中较早的一个.
     * 记录的时间是最后一次更新的时间, 不早于第一次播放, 所以估计值可能比真实的首次启动晚, 但不会更早.
     * 本机登录过时, 记录可能是从其他设备同步来的, 时间可能早于本机安装, 所以不使用.
     */
    private suspend fun estimateFirstLaunchAtMillis(hadLogin: Boolean): Long {
        val now = clock.now().toEpochMilliseconds()
        if (hadLogin) return now
        val earliestRecord = playHistoryRepository.getEarliestRecordTimeMillis() ?: return now
        return minOf(now, earliestRecord)
    }

    private suspend fun sendCatching(pending: PendingInstallReport) {
        try {
            send(pending)
        } catch (e: Exception) {
            RepositoryException.wrapOrThrowCancellation(e)
            logger.info { "Failed to send install report, will retry on next start: ${e.message}" }
        }
    }

    private suspend fun send(pending: PendingInstallReport) {
        try {
            api {
                reportInstall(
                    userAgent = getAniUserAgent(),
                    aniReportInstallRequest = AniReportInstallRequest(
                        firstLaunchAt = Instant.fromEpochMilliseconds(pending.firstLaunchAtMillis).toString(),
                        localEpisodesBeforeLogin = pending.localEpisodesBeforeLogin,
                        hadPreviousLogin = pending.hadPreviousLogin,
                    ),
                )
            }
            logger.info { "Sent install report: $pending" }
        } catch (e: ClientRequestException) {
            // 400 表示数据超出服务器接受的范围 (例如时钟错误), 重试也不会成功
            if (e.response.status != HttpStatusCode.BadRequest) throw e
            logger.warn { "Server rejected install report, dropping: $pending" }
        }
        store.updateData { info ->
            info.copy(
                pendingReport = info.pendingReport.takeUnless { it == pending },
                reportedUserIds = info.reportedUserIds + pending.userId,
            )
        }
    }

    private suspend fun SessionStateProvider.hasSession(): Boolean {
        return when (val state = stateFlow.first()) {
            is SessionState.Valid -> true
            is SessionState.Invalid -> state.reason != InvalidSessionReason.NO_TOKEN
        }
    }
}
