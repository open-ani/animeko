/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.featureflag

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.data.network.AniFeatureFlagService
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * 功能开关的当前值.
 *
 * 启动时先用上次拉取并保存的 [FeatureFlagSnapshot], 然后在登录状态确定后 (token 刷新完成, 请求能带上正确的身份)
 * 从服务端拉取一次; 之后每当登录身份变化 (登录、退出、换用户) 再拉取一次, 因为固定分桶和名单按用户 id 求值,
 * 未登录时按安装 ID. 拉取失败按 [retryDelays] 重试, 用尽后沿用快照或默认值, 直到下次身份变化.
 *
 * [isEnabled] 是同步读取, 任意线程可调. 一次启动内服务端的值只在拉取成功时变化, 消费者应在用到时读取, 或用 [flow] 观察;
 * 一个播放会话这样的短生命周期对象可以在创建时读一次, 让一次会话内的行为一致.
 *
 * "客户端随机" 策略的开关 (服务端给的是概率 [FeatureFlagValue.threshold] 而不是定值) 在这里抽签: [isEnabled] 每次调用都按概率
 * 独立抽一次, [flow] 在服务端的值每次变化时抽一次. 随机的单位由调用方决定 (例如每个播放会话读一次), 业务代码不自己抽签.
 *
 * @param currentUserId 当前登录的用户 id, 未登录时为 `null`.
 */
class FeatureFlagManager(
    private val store: DataStore<FeatureFlagSnapshot>,
    private val service: AniFeatureFlagService,
    private val sessionStateProvider: SessionStateProvider,
    private val currentUserId: Flow<String?>,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val retryDelays: List<Duration> = DEFAULT_RETRY_DELAYS,
    private val random: Random = Random.Default,
) {
    private val logger = logger<FeatureFlagManager>()

    private val values = MutableStateFlow<Map<String, FeatureFlagValue>>(emptyMap())

    /**
     * 服务端返回的全部开关 (包括本版本不认识的), 还没有任何值时为空.
     */
    val valuesFlow: StateFlow<Map<String, FeatureFlagValue>> = values.asStateFlow()

    /**
     * [flag] 的当前值; 服务端没有配置或还没有拉取到时是 [FeatureFlag.default]. 客户端随机策略的开关每次调用独立抽签.
     */
    fun isEnabled(flag: FeatureFlag): Boolean = evaluate(values.value[flag.key], flag)

    /**
     * [flag] 的值, 服务端的值变化时更新. 客户端随机策略的开关在每次更新时抽一次签.
     */
    fun flow(flag: FeatureFlag): Flow<Boolean> = values.map { evaluate(it[flag.key], flag) }.distinctUntilChanged()

    /**
     * 客户端的判断: `enabled && (threshold == null || random() < threshold)`. 确定的值不抽签.
     */
    private fun evaluate(value: FeatureFlagValue?, flag: FeatureFlag): Boolean {
        if (value == null) return flag.default
        if (!value.enabled) return false
        val threshold = value.threshold ?: return true
        return random.nextFloat() < threshold
    }

    /**
     * 读取保存的快照, 然后跟随登录身份拉取. 应用启动时调用一次.
     */
    fun start() {
        scope.launch(CoroutineName("FeatureFlagManager")) {
            loadSnapshot()
            identityFlow().collectLatest { identity ->
                refreshWithRetry(identity)
            }
        }
    }

    /**
     * 立即从服务端拉取一次并更新值. 失败时抛出 [RepositoryException], 已有的值不变.
     */
    suspend fun refresh() {
        val userId = currentUserId.first()
        val flags = service.getFeatureFlags()
        values.value = flags
        try {
            store.updateData {
                FeatureFlagSnapshot(flags = flags, userId = userId, fetchedAtMillis = clock.now().toEpochMilliseconds())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to save feature flags" }
        }
        logger.info { "Feature flags refreshed: ${flags.size} flags, userId=$userId" }
    }

    private suspend fun loadSnapshot() {
        val snapshot = try {
            store.data.first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load saved feature flags" }
            return
        }
        // 拉取完成前先用上次的值: 启动时通常还是同一个用户 (或同一台设备), 与服务端的结果一致
        if (values.value.isEmpty() && snapshot.flags.isNotEmpty()) {
            values.value = snapshot.flags
        }
    }

    /**
     * 请求的身份: 登录时是用户 id, 未登录时是 `null` (服务端按安装 ID). 登录状态确定后才发出第一个值.
     * 用户信息可能比登录状态晚一步到达, 这时会先按 "已登录但 id 未知" 拉一次, id 到达后再拉一次, 后一次的结果为准.
     */
    private fun identityFlow(): Flow<String?> = combine(
        sessionStateProvider.stateFlow.map { it is SessionState.Valid },
        currentUserId,
    ) { loggedIn, userId ->
        if (loggedIn) userId ?: LOGGED_IN_UNKNOWN_USER else null
    }.distinctUntilChanged()

    private suspend fun refreshWithRetry(identity: String?) {
        for (attempt in 0..retryDelays.size) {
            try {
                refresh()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val delayFor = retryDelays.getOrNull(attempt)
                if (delayFor == null) {
                    logger.warn(e) { "Failed to fetch feature flags for identity=$identity, giving up until the session changes" }
                    return
                }
                logger.warn { "Failed to fetch feature flags (attempt ${attempt + 1}): ${e.message}, retrying in $delayFor" }
                delay(delayFor)
            }
        }
    }

    companion object {
        val DEFAULT_RETRY_DELAYS: List<Duration> = listOf(5.seconds, 30.seconds, 2.minutes)

        private const val LOGGED_IN_UNKNOWN_USER = "logged-in"
    }
}
