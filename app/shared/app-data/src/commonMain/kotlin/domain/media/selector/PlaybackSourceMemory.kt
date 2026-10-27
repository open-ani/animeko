/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.him188.ani.app.platform.NetworkEnvironment
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.WebMediaRoute
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 同一播放页的在线线路运行记录。切集保留，关闭页面释放；不写入手动偏好、数据源开关或订阅配置。
 * 只有带条目与线路身份的在线资源参与跨集记录，单集资源黑名单由错误处理器单独维护。
 */
class PlaybackSourceMemory(private val clock: Clock = Clock.System) {
    data class Route(val sourceId: String, val web: WebMediaRoute)

    enum class FailureReason { RESOLUTION, PLAYBACK }
    data class Failure(val reason: FailureReason, val recordedAt: Instant)

    /** 剧集、资源和尝试序号一起绑定到环境代次，旧加载回调只能更新自己的尝试。 */
    class Attempt internal constructor(
        val episodeId: Int,
        val mediaId: String,
        val generation: Long,
        val sequence: Long,
        val route: Route?,
    )

    data class Snapshot(
        val generation: Long = 0,
        val recentSuccess: Route? = null,
        val failures: Map<Route, Failure> = emptyMap(),
        val networkAvailable: Boolean? = null,
    ) {
        fun allows(media: Media): Boolean = routeOf(media) !in failures
        fun isRecentSuccess(media: Media): Boolean = routeOf(media)?.let { it == recentSuccess } == true
    }

    private data class State(
        val snapshot: Snapshot = Snapshot(),
        val sequence: Long = 0,
        val active: Attempt? = null,
        val renewable: Attempt? = null,
        val networkIdentity: String? = null,
    )
    private val state = MutableStateFlow(State())
    val snapshots: Flow<Snapshot> = state.map { it.snapshot }.distinctUntilChanged()
    fun snapshot(): Snapshot = state.value.snapshot

    fun beginAttempt(episodeId: Int, media: Media): Attempt {
        while (true) {
            val before = state.value
            val attempt = Attempt(episodeId, media.mediaId, before.snapshot.generation, before.sequence + 1, routeOf(media))
            if (state.compareAndSet(before, before.copy(sequence = attempt.sequence, active = attempt, renewable = null))) return attempt
        }
    }

    fun finishAttempt(attempt: Attempt) {
        state.update {
            it.copy(active = it.active.takeUnless { active -> active == attempt }, renewable = it.renewable.takeUnless { old -> old == attempt })
        }
    }

    /** 正常播放在环境变化后继续观察新的进度证据，不沿用旧环境的回调或累计时长。 */
    fun renewAttempt(attempt: Attempt): Attempt? {
        while (true) {
            val before = state.value
            if (before.renewable != attempt || before.active != null) return null
            val renewed = Attempt(attempt.episodeId, attempt.mediaId, before.snapshot.generation, before.sequence + 1, attempt.route)
            if (state.compareAndSet(before, before.copy(sequence = renewed.sequence, active = renewed, renewable = null))) return renewed
        }
    }

    fun recordFailure(attempt: Attempt, reason: FailureReason) {
        val route = attempt.route ?: return
        state.update {
            if (it.active != attempt || it.snapshot.generation != attempt.generation || it.snapshot.networkAvailable == false) it
            else it.copy(snapshot = it.snapshot.copy(
                failures = it.snapshot.failures + (route to Failure(reason, clock.now())),
                recentSuccess = it.snapshot.recentSuccess.takeUnless { success -> success == route },
            ))
        }
    }

    /** 手动选择可以绕过失败记录，但只有真实播放成功才恢复该线路的自动选择资格。 */
    fun recordSuccess(attempt: Attempt) {
        val route = attempt.route ?: return
        state.update {
            if (it.active != attempt || it.snapshot.generation != attempt.generation) it
            else it.copy(snapshot = it.snapshot.copy(recentSuccess = route, failures = it.snapshot.failures - route))
        }
    }

    /** 明确重新检测失败线路时清除失败记录，保留成功经验、持久化偏好与禁用状态。 */
    fun retryFailures() {
        state.update {
            it.copy(
                snapshot = it.snapshot.copy(generation = it.snapshot.generation + 1, failures = emptyMap()),
                active = null,
                renewable = it.active ?: it.renewable,
            )
        }
    }

    /** 只在已知的默认网络身份发生变化时清理失败记录；短暂掉线保留记录，正常播放保持不变。 */
    fun updateEnvironment(environment: NetworkEnvironment?) {
        if (environment == null) return
        state.update {
            val changed = environment.isConnected && environment.identity != null &&
                it.networkIdentity != null && it.networkIdentity != environment.identity
            it.copy(
                snapshot = it.snapshot.copy(
                    generation = it.snapshot.generation + if (changed) 1 else 0,
                    failures = if (changed) emptyMap() else it.snapshot.failures,
                    networkAvailable = environment.isConnected,
                ),
                networkIdentity = if (environment.isConnected) environment.identity else it.networkIdentity,
                active = if (changed) null else it.active,
                renewable = if (changed) it.active ?: it.renewable else it.renewable,
            )
        }
    }

    /** 删除或变更解析配置的源，其旧线路身份不能继续参与决策。启用开关不影响运行记录。 */
    fun retainConfigurations(configurations: Map<String, String?>) {
        state.update { state ->
            fun valid(route: Route) = route.sourceId in configurations &&
                (configurations[route.sourceId] == null || configurations[route.sourceId] == route.web.configuration)
            state.copy(snapshot = state.snapshot.copy(
                recentSuccess = state.snapshot.recentSuccess?.takeIf(::valid),
                failures = state.snapshot.failures.filterKeys(::valid),
            ), active = state.active?.takeIf { it.route == null || valid(it.route) },
                renewable = state.renewable?.takeIf { it.route == null || valid(it.route) })
        }
    }

    fun clear() {
        state.update { State(snapshot = Snapshot(generation = it.snapshot.generation + 1), sequence = it.sequence) }
    }

    companion object {
        fun routeOf(media: Media): Route? =
            if (media.kind == MediaSourceKind.WEB) media.properties.webRoute?.let { Route(media.mediaSourceId, it) }
            else null
    }
}
