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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import me.him188.ani.app.data.network.AniFeatureFlagService
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.utils.coroutines.runCatchingCancellable
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 功能开关的当前配置.
 *
 * 启动时先用上次拉取并保存的 [FeatureFlagSnapshot], 然后在登录状态确定后 (token 刷新完成, 请求能带上正确的身份)
 * 把 [FeatureFlag] 里的每个开关各拉取一次; 之后每当登录身份变化 (登录、退出、换用户) 再拉取一次, 因为固定分桶和名单
 * 按用户 id 求值, 未登录时按安装 ID. 拉取失败按 [retryDelays] 重试, 用尽后沿用快照或空配置, 直到下次身份变化.
 *
 * 每个开关的配置是服务端为本次请求命中的取值配置的属性表 ([FeatureFlagConfig]); 没有命中、服务端没有这个开关或还没拉取到
 * 都是 [FeatureFlagConfig.Empty], 业务代码读属性时给的默认值就是关闭时的行为.
 *
 * [config] 是同步读取, 任意线程可调, 一次启动内只在拉取成功时变化; 要跟随变化用 [configFlow]. 以业务单位 (例如一次播放)
 * 抽样的开关在每个单位开始时 [consume] 一次: 拿到当前配置供这个单位使用, 同时后台再拉一次供下一个单位用, 服务端
 * "每次请求随机" 策略下每个单位就是一次独立的抽签.
 *
 * @param currentUserId 当前登录的用户 id, 未登录时为 `null`.
 */
class FeatureFlagManager(
    private val store: DataStore<FeatureFlagSnapshot>,
    private val service: AniFeatureFlagService,
    private val sessionStateProvider: SessionStateProvider,
    private val currentUserId: Flow<String?>,
    private val scope: CoroutineScope,
    private val retryDelays: List<Duration> = DEFAULT_RETRY_DELAYS,
) {
    private val logger = logger<FeatureFlagManager>()

    /** 各开关最近一次拉取到的配置, 按开关名; 还没有任何值时为空 */
    private val configs = MutableStateFlow<Map<String, JsonObject>>(emptyMap())

    /**
     * [flag] 当前的配置; 还没有拉取到, 或服务端没有这个开关时是 [FeatureFlagConfig.Empty].
     */
    fun config(flag: FeatureFlag): FeatureFlagConfig = configOf(configs.value, flag)

    /**
     * [flag] 的配置, 拉取成功后更新.
     */
    fun configFlow(flag: FeatureFlag): Flow<FeatureFlagConfig> = configs.map { configOf(it, flag) }.distinctUntilChanged()

    /**
     * 一个业务单位 (例如一次播放) 开始时调用一次: 返回当前配置供这个单位使用, 并在后台再拉一次供下一个单位用, 失败只记日志.
     * 服务端 "每次请求随机" 策略下, 每个单位得到的就是一次独立的抽签, 而一个单位内的行为一致.
     */
    fun consume(flag: FeatureFlag): FeatureFlagConfig {
        val current = config(flag)
        scope.launch(CoroutineName("FeatureFlagManager.consume")) {
            runCatchingCancellable { refresh(flag) }
                .onFailure { logger.warn { "Failed to refresh feature flag ${flag.key} after consume: ${it.message}" } }
        }
        return current
    }

    /**
     * 立即拉取 [flag] 并更新配置. 失败时抛出 [RepositoryException], 已有的配置不变.
     */
    suspend fun refresh(flag: FeatureFlag): FeatureFlagConfig {
        val properties = service.getFeatureFlag(flag.key)
        update(mapOf(flag.key to properties))
        return FeatureFlagConfig(properties)
    }

    /**
     * 立即拉取全部已知的开关并更新配置. 任一失败时抛出 [RepositoryException], 这一轮的结果整体不采用.
     */
    suspend fun refreshAll() {
        val fetched = coroutineScope {
            FeatureFlag.entries.map { flag -> async { flag.key to service.getFeatureFlag(flag.key) } }.map { it.await() }
        }.toMap()
        update(fetched)
        logger.info { "Feature flags refreshed: ${fetched.size} flags" }
    }

    private suspend fun update(fetched: Map<String, JsonObject>) {
        val merged = configs.value + fetched
        configs.value = merged
        runCatchingCancellable { store.updateData { FeatureFlagSnapshot(merged) } }
            .onFailure { logger.warn(it) { "Failed to save feature flags" } }
    }

    /**
     * 读取保存的快照, 然后跟随登录身份拉取. 应用启动时调用一次.
     */
    fun start() {
        scope.launch(CoroutineName("FeatureFlagManager")) {
            loadSnapshot()
            identityFlow().collectLatest { identity ->
                // 登录状态比用户信息先到: 等 id 一会儿, 它到了就只按 id 拉一次, 而不是先按 "id 未知" 拉一次再按 id 拉一次
                if (identity == LOGGED_IN_UNKNOWN_USER) delay(UNKNOWN_USER_GRACE)
                refreshWithRetry(identity)
            }
        }
    }

    private suspend fun loadSnapshot() {
        val snapshot = runCatchingCancellable { store.data.first() }.getOrElse {
            logger.warn(it) { "Failed to load saved feature flags" }
            return
        }
        // 拉取完成前先用上次的值: 启动时通常还是同一个用户 (或同一台设备), 与服务端的结果一致
        if (configs.value.isEmpty() && snapshot.flags.isNotEmpty()) {
            configs.value = snapshot.flags
        }
    }

    /**
     * 请求的身份: 登录时是用户 id, 未登录时是 `null` (服务端按安装 ID). 登录状态确定后才发出第一个值.
     * 用户信息可能比登录状态晚到达, 这时先发出 "已登录但 id 未知", id 到达后再发出 id.
     */
    private fun identityFlow(): Flow<String?> = combine(
        sessionStateProvider.stateFlow.map { it is SessionState.Valid },
        currentUserId,
    ) { loggedIn, userId ->
        if (loggedIn) userId ?: LOGGED_IN_UNKNOWN_USER else null
    }.distinctUntilChanged()

    private suspend fun refreshWithRetry(identity: String?) {
        for (attempt in 0..retryDelays.size) {
            val e = runCatchingCancellable { refreshAll() }.exceptionOrNull() ?: return
            val delayFor = retryDelays.getOrNull(attempt)
            if (delayFor == null) {
                logger.warn(e) { "Failed to fetch feature flags for identity=$identity, giving up until the session changes" }
                return
            }
            logger.warn { "Failed to fetch feature flags (attempt ${attempt + 1}): ${e.message}, retrying in $delayFor" }
            delay(delayFor)
        }
    }

    private fun configOf(all: Map<String, JsonObject>, flag: FeatureFlag): FeatureFlagConfig =
        all[flag.key]?.let { FeatureFlagConfig(it) } ?: FeatureFlagConfig.Empty

    companion object {
        val DEFAULT_RETRY_DELAYS: List<Duration> = listOf(5.seconds, 30.seconds, 2.minutes)

        /** 登录状态已确定但用户 id 还没到时, 最多等这么久再拉取 */
        val UNKNOWN_USER_GRACE: Duration = 5.seconds

        private const val LOGGED_IN_UNKNOWN_USER = "logged-in"
    }
}
