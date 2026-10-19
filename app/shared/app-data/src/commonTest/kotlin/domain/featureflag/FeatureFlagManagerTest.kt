/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.featureflag

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import me.him188.ani.app.data.network.AniFeatureFlagService
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.FeatureFlagsAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FeatureFlagManagerTest {
    private val nowMillis = 1_800_000_000_000L
    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
    }

    private val store = MemoryDataStore(FeatureFlagSnapshot.Empty)
    private val session = FakeSession()
    private val currentUserId = MutableStateFlow<String?>(null)
    private val service = FakeService()
    private val random = FakeRandom()
    private val on = FeatureFlagValue.On
    private val off = FeatureFlagValue.Off

    /** 启动时登录状态还没有确定: [stateFlow] 在发出第一个值之前不 emit. */
    private class FakeSession : SessionStateProvider {
        override val stateFlow = MutableSharedFlow<SessionState>(replay = 1)
        override val eventFlow: Flow<SessionEvent> = emptyFlow()
    }

    /** 按 [results] 依次返回或抛出, 用完后返回空 map. */
    private class FakeService : AniFeatureFlagService(UnusedInvoker, EmptyCoroutineContext) {
        val results = ArrayDeque<Result<Map<String, FeatureFlagValue>>>()
        var calls = 0

        override suspend fun getFeatureFlags(): Map<String, FeatureFlagValue> {
            calls++
            return (results.removeFirstOrNull() ?: Result.success(emptyMap())).getOrThrow()
        }
    }

    private object UnusedInvoker : ApiInvoker<FeatureFlagsAniApi> {
        override suspend fun <R> invoke(action: suspend FeatureFlagsAniApi.() -> R): R = error("not used")
    }

    /** 按 [floats] 依次返回 nextFloat, 用完后返回 0; 管理器只应该用 nextFloat */
    private class FakeRandom : Random() {
        val floats = ArrayDeque<Float>()
        var draws = 0

        override fun nextBits(bitCount: Int): Int = error("only nextFloat is expected")

        override fun nextFloat(): Float {
            draws++
            return floats.removeFirstOrNull() ?: 0f
        }
    }

    private fun TestScope.manager() = FeatureFlagManager(
        store = store,
        service = service,
        sessionStateProvider = session,
        currentUserId = currentUserId,
        scope = backgroundScope,
        clock = clock,
        random = random,
    )

    private val flag = FeatureFlag.PLAY_FLOW_ANALYTICS

    @Test
    fun `default applies before anything is known`() = runTest {
        val manager = manager()
        assertEquals(flag.default, manager.isEnabled(flag))
        manager.start()
        runCurrent()
        assertEquals(flag.default, manager.isEnabled(flag))
        assertEquals(0, service.calls)
    }

    @Test
    fun `saved snapshot is used before the first fetch`() = runTest {
        store.updateData { FeatureFlagSnapshot(flags = mapOf(flag.key to off), userId = "u1", fetchedAtMillis = 1) }
        val manager = manager()
        manager.start()
        runCurrent()

        assertFalse(manager.isEnabled(flag))
        assertEquals(0, service.calls) // 登录状态还没有确定, 不请求
    }

    @Test
    fun `fetches once the session state is known and saves the snapshot`() = runTest {
        service.results += Result.success(mapOf(flag.key to off, "other" to on))
        val manager = manager()
        manager.start()
        runCurrent()

        session.stateFlow.emit(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        runCurrent()

        assertEquals(1, service.calls)
        assertFalse(manager.isEnabled(flag))
        assertEquals(mapOf(flag.key to off, "other" to on), manager.valuesFlow.value)
        assertEquals(
            FeatureFlagSnapshot(flags = mapOf(flag.key to off, "other" to on), userId = null, fetchedAtMillis = nowMillis),
            store.data.value,
        )
    }

    @Test
    fun `refetches when the identity changes`() = runTest {
        val manager = manager()
        manager.start()
        session.stateFlow.emit(SessionState.Valid(bangumiConnected = false))
        runCurrent()
        assertEquals(1, service.calls) // 已登录, 用户 id 未知

        currentUserId.value = "u1"
        runCurrent()
        assertEquals(2, service.calls)
        assertEquals("u1", store.data.value.userId)

        // 同一个用户的登录状态刷新 (StateFlow 去重) 不再请求
        session.stateFlow.emit(SessionState.Valid(bangumiConnected = true))
        runCurrent()
        assertEquals(2, service.calls)

        currentUserId.value = "u2"
        runCurrent()
        assertEquals(3, service.calls)

        // 退出登录: 身份变为设备
        session.stateFlow.emit(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        currentUserId.value = null
        runCurrent()
        assertEquals(4, service.calls)
        assertEquals(null, store.data.value.userId)
    }

    @Test
    fun `retries with increasing delays and keeps the previous values meanwhile`() = runTest {
        store.updateData { FeatureFlagSnapshot(flags = mapOf(flag.key to off)) }
        service.results += Result.failure(IOException("offline"))
        service.results += Result.failure(IOException("offline"))
        service.results += Result.success(mapOf(flag.key to on))
        val manager = manager()
        manager.start()
        session.stateFlow.emit(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        runCurrent()
        assertEquals(1, service.calls)
        assertFalse(manager.isEnabled(flag)) // 失败时沿用快照

        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(2, service.calls)
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(3, service.calls)
        assertTrue(manager.isEnabled(flag))

        // 成功后不再重试
        advanceTimeBy(1.hours)
        runCurrent()
        assertEquals(3, service.calls)
    }

    @Test
    fun `gives up after the retries until the identity changes`() = runTest {
        repeat(10) { service.results += Result.failure(IOException("offline")) }
        val manager = manager()
        manager.start()
        session.stateFlow.emit(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        runCurrent()
        advanceTimeBy(5.seconds + 30.seconds + 2.minutes + 10.hours)
        runCurrent()
        assertEquals(4, service.calls) // 首次 + 3 次重试
        assertEquals(flag.default, manager.isEnabled(flag))

        session.stateFlow.emit(SessionState.Valid(bangumiConnected = false))
        runCurrent()
        assertEquals(5, service.calls)
    }

    @Test
    fun `flow follows the fetched value and falls back to the default`() = runTest {
        service.results += Result.success(mapOf(flag.key to off))
        service.results += Result.success(emptyMap())
        val manager = manager()
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { manager.flow(flag).collect { seen += it } }
        manager.start()
        runCurrent()
        assertEquals(listOf(true), seen)

        session.stateFlow.emit(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        runCurrent()
        assertEquals(listOf(true, false), seen)

        // 服务端删除了这个开关: 回到默认值
        session.stateFlow.emit(SessionState.Valid(bangumiConnected = false))
        runCurrent()
        assertEquals(listOf(true, false, true), seen)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 客户端随机
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    fun `client random flags are drawn on every read with the server threshold`() = runTest {
        service.results += Result.success(mapOf(flag.key to FeatureFlagValue(enabled = true, threshold = 0.3f)))
        val manager = manager()
        manager.start()
        session.stateFlow.emit(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        runCurrent()
        assertEquals(0, random.draws) // 拉取本身不抽签

        random.floats += listOf(0.29f, 0.3f, 0.99f, 0f)
        assertTrue(manager.isEnabled(flag)) // 0.29 < 0.3
        assertFalse(manager.isEnabled(flag)) // 0.3 不小于 0.3
        assertFalse(manager.isEnabled(flag))
        assertTrue(manager.isEnabled(flag))
        assertEquals(4, random.draws)
        // 快照里保存的是概率, 不是抽签结果
        assertEquals(FeatureFlagValue(enabled = true, threshold = 0.3f), store.data.value.flags[flag.key])
    }

    @Test
    fun `definite values never draw and disabled wins over a threshold`() = runTest {
        val manager = manager()
        random.floats += List(10) { 0f } // 若抽签, 0 一定命中
        suspend fun check(value: FeatureFlagValue?, expected: Boolean) {
            service.results += Result.success(if (value == null) emptyMap() else mapOf(flag.key to value))
            manager.refresh()
            assertEquals(expected, manager.isEnabled(flag), "$value")
        }
        check(on, true)
        check(off, false)
        check(FeatureFlagValue(enabled = false, threshold = 0.9f), false)
        check(null, flag.default)
        assertEquals(0, random.draws)

        check(FeatureFlagValue(enabled = true, threshold = 1f), true)
        assertEquals(1, random.draws)
        check(FeatureFlagValue(enabled = true, threshold = 0f), false) // 0% 永不命中
        assertEquals(2, random.draws)
    }

    @Test
    fun `flow draws once per server update for client random flags`() = runTest {
        service.results += Result.success(mapOf(flag.key to FeatureFlagValue(enabled = true, threshold = 0.5f)))
        service.results += Result.success(mapOf(flag.key to FeatureFlagValue(enabled = true, threshold = 0.6f)))
        random.floats += listOf(0.7f, 0.2f)
        val manager = manager()
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { manager.flow(flag).collect { seen += it } }
        manager.start()
        session.stateFlow.emit(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        runCurrent()
        assertEquals(listOf(true, false), seen) // 默认值, 然后第一次抽 0.7 未命中
        assertEquals(1, random.draws)

        manager.refresh()
        runCurrent()
        assertEquals(listOf(true, false, true), seen) // 服务端的值变了, 再抽一次: 0.2 命中
        assertEquals(2, random.draws)
    }
}
