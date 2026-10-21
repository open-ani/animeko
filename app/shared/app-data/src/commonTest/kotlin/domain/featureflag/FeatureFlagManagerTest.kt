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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.him188.ani.app.data.network.AniFeatureFlagService
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.FeatureFlagsAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FeatureFlagManagerTest {
    private val store = MemoryDataStore(FeatureFlagSnapshot.Empty)
    private val session = FakeSession()
    private val currentUserId = MutableStateFlow<String?>(null)
    private val service = FakeService()
    private val on = buildJsonObject { put("enabled", true) }
    private val off = buildJsonObject { put("enabled", false) }
    private val empty = JsonObject(emptyMap())

    /** 启动时登录状态还没有确定: [stateFlow] 在发出第一个值之前不 emit. */
    private class FakeSession : SessionStateProvider {
        override val stateFlow = MutableSharedFlow<SessionState>(replay = 1)
        override val eventFlow: Flow<SessionEvent> = emptyFlow()
    }

    /** 每次请求按 [results] 依次返回或 (像真实的服务一样包成 [RepositoryException]) 抛出, 用完后返回 `{}`; 记录请求过的开关名. */
    private class FakeService : AniFeatureFlagService(UnusedInvoker, EmptyCoroutineContext) {
        val results = ArrayDeque<Result<JsonObject>>()
        val calls = mutableListOf<String>()

        override suspend fun getFeatureFlag(key: String): JsonObject {
            calls += key
            return (results.removeFirstOrNull() ?: Result.success(JsonObject(emptyMap())))
                .getOrElse { throw RepositoryException.wrapOrThrowCancellation(it) }
        }
    }

    private object UnusedInvoker : ApiInvoker<FeatureFlagsAniApi> {
        override suspend fun <R> invoke(action: suspend FeatureFlagsAniApi.() -> R): R = error("not used")
    }

    private fun TestScope.manager() = FeatureFlagManager(
        store = store,
        service = service,
        sessionStateProvider = session,
        currentUserId = currentUserId,
        scope = backgroundScope,
    )

    private val flag = FeatureFlag.PLAY_FLOW_REPORT

    private fun FeatureFlagManager.enabled() = config(flag).boolean("enabled", false)

    @Test
    fun `empty config applies before anything is known`() = runTest {
        val manager = manager()
        assertEquals(FeatureFlagConfig.Empty, manager.config(flag))
        assertFalse(manager.enabled())
        manager.start()
        runCurrent()
        assertEquals(FeatureFlagConfig.Empty, manager.config(flag))
        assertEquals(emptyList(), service.calls)
    }

    @Test
    fun `saved snapshot is used before the first fetch`() = runTest {
        store.updateData { FeatureFlagSnapshot(flags = mapOf(flag.key to on)) }
        val manager = manager()
        manager.start()
        runCurrent()

        assertEquals(FeatureFlagConfig(on), manager.config(flag))
        assertTrue(manager.enabled())
        assertEquals(emptyList(), service.calls) // 登录状态还没有确定, 不请求
    }

    @Test
    fun `fetches every known flag once the session state is known and saves the snapshot`() = runTest {
        service.results += Result.success(on)
        val manager = manager()
        manager.start()
        runCurrent()

        session.stateFlow.emit(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        runCurrent()

        // 每个已知的开关各请求一次
        assertEquals(FeatureFlag.entries.map { it.key }, service.calls)
        assertTrue(manager.enabled())
        assertEquals(FeatureFlagSnapshot(flags = mapOf(flag.key to on)), store.data.value)
    }

    @Test
    fun `refetches when the identity changes`() = runTest {
        val manager = manager()
        manager.start()
        session.stateFlow.emit(SessionState.Valid(bangumiConnected = false))
        runCurrent()
        assertEquals(0, service.calls.size) // 已登录但用户 id 还没到: 等 id

        currentUserId.value = "u1"
        runCurrent()
        assertEquals(1, service.calls.size) // id 到了只拉一次

        // 同一个用户的登录状态刷新 (StateFlow 去重) 不再请求
        session.stateFlow.emit(SessionState.Valid(bangumiConnected = true))
        runCurrent()
        assertEquals(1, service.calls.size)

        currentUserId.value = "u2"
        runCurrent()
        assertEquals(2, service.calls.size)

        // 退出登录: 身份变为设备
        session.stateFlow.emit(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        currentUserId.value = null
        runCurrent()
        assertEquals(3, service.calls.size)
    }

    @Test
    fun `fetches as logged in when the user id does not arrive in time`() = runTest {
        val manager = manager()
        manager.start()
        session.stateFlow.emit(SessionState.Valid(bangumiConnected = false))
        runCurrent()
        assertEquals(0, service.calls.size)

        // 等不到 id 也要拉: 请求带着 token, 服务端认得用户
        advanceTimeBy(FeatureFlagManager.UNKNOWN_USER_GRACE)
        runCurrent()
        assertEquals(1, service.calls.size)

        currentUserId.value = "u1"
        runCurrent()
        assertEquals(2, service.calls.size)
    }

    @Test
    fun `retries with increasing delays and keeps the previous values meanwhile`() = runTest {
        store.updateData { FeatureFlagSnapshot(flags = mapOf(flag.key to off)) }
        service.results += Result.failure(IOException("offline"))
        service.results += Result.failure(IOException("offline"))
        service.results += Result.success(on)
        val manager = manager()
        manager.start()
        session.stateFlow.emit(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        runCurrent()
        assertEquals(1, service.calls.size)
        assertEquals(FeatureFlagConfig(off), manager.config(flag)) // 失败时沿用快照

        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(2, service.calls.size)
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(3, service.calls.size)
        assertTrue(manager.enabled())

        // 成功后不再重试
        advanceTimeBy(1.hours)
        runCurrent()
        assertEquals(3, service.calls.size)
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
        assertEquals(4, service.calls.size) // 首次 + 3 次重试
        assertEquals(FeatureFlagConfig.Empty, manager.config(flag))

        // 登录: 身份变化, 重新拉
        currentUserId.value = "u1"
        session.stateFlow.emit(SessionState.Valid(bangumiConnected = false))
        runCurrent()
        assertEquals(5, service.calls.size)
    }

    @Test
    fun `config flow follows the fetched config and falls back to empty`() = runTest {
        service.results += Result.success(on)
        service.results += Result.success(empty)
        val manager = manager()
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { manager.configFlow(flag).collect { seen += it.boolean("enabled", false) } }
        manager.start()
        runCurrent()
        assertEquals(listOf(false), seen)

        session.stateFlow.emit(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        runCurrent()
        assertEquals(listOf(false, true), seen)

        // 登录后再拉一次, 服务端已删除这个开关 (404 → {}): 回到默认行为
        currentUserId.value = "u1"
        session.stateFlow.emit(SessionState.Valid(bangumiConnected = false))
        runCurrent()
        assertEquals(listOf(false, true, false), seen)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 按业务单位
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    fun `consume returns the current config and refreshes once in the background for the next unit`() = runTest {
        service.results += Result.success(on)
        val manager = manager()
        manager.start()
        session.stateFlow.emit(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        runCurrent()
        assertEquals(1, service.calls.size)

        // 第一次播放: 拿到启动时拉到的配置, 后台再拉一次 (服务端每次请求随机: 这次没抽中)
        service.results += Result.success(empty)
        assertTrue(manager.consume(flag).boolean("enabled", false))
        runCurrent()
        assertEquals(2, service.calls.size)
        assertEquals(listOf(flag.key, flag.key), service.calls)

        // 第二次播放: 拿到上一次后台拉到的结果; 这次后台拉取失败, 只记日志, 配置不变
        service.results += Result.failure(IOException("offline"))
        assertFalse(manager.consume(flag).boolean("enabled", false))
        runCurrent()
        assertEquals(3, service.calls.size)
        assertEquals(FeatureFlagConfig.Empty, manager.config(flag))

        // 快照里是最近一次成功拉到的
        assertEquals(FeatureFlagSnapshot(flags = mapOf(flag.key to empty)), store.data.value)
    }

    @Test
    fun `refresh of one flag throws on failure and keeps the cache`() = runTest {
        service.results += Result.success(on)
        val manager = manager()
        assertEquals(FeatureFlagConfig(on), manager.refresh(flag))
        assertEquals(FeatureFlagConfig(on), manager.config(flag))

        service.results += Result.failure(IOException("offline"))
        assertFailsWith<RepositoryException> { manager.refresh(flag) }
        assertEquals(FeatureFlagConfig(on), manager.config(flag))
        assertEquals(FeatureFlagSnapshot(flags = mapOf(flag.key to on)), store.data.value)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 配置的读取
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    fun `config accessors fall back to the default on missing keys and wrong types`() {
        val config = FeatureFlagConfig(
            buildJsonObject {
                put("enabled", true)
                put("name", "a")
                put("count", 3)
                put("big", 1L shl 40)
                put("ratio", 0.25)
                put("text_number", "7")
                put("nul", null as String?)
                putJsonObject("nested") { put("enabled", true) }
                put("list", buildJsonArray { add(JsonPrimitive(1)) })
            },
        )
        assertTrue(config.boolean("enabled", false))
        assertEquals("a", config.string("name", "x"))
        assertEquals(3, config.int("count", 0))
        assertEquals(1L shl 40, config.long("big", 0L))
        assertEquals(0.25, config.double("ratio", 0.0))
        assertEquals(3.0, config.double("count", 0.0)) // 整数也能按 double 读

        // 缺少的键
        assertFalse(config.boolean("missing", false))
        assertTrue(config.boolean("missing", true))
        assertEquals("x", config.string("missing", "x"))
        assertEquals(0, config.int("missing", 0))
        assertFalse(config.has("missing"))
        assertNull(config["missing"])

        // 类型不对: 给默认值, 不抛异常
        assertEquals(1, config.int("name", 1))
        assertEquals("d", config.string("count", "d")) // 数字不当作字符串
        assertEquals(0, config.int("text_number", 0)) // 字符串不当作数字
        assertFalse(config.boolean("count", false))
        assertFalse(config.boolean("nested", false)) // 嵌套对象不是标量
        assertEquals(0, config.int("list", 0))
        assertFalse(config.boolean("nul", false))
        assertTrue(config.has("nul"))
        assertTrue(config.has("nested"))

        assertEquals(FeatureFlagConfig(JsonObject(emptyMap())), FeatureFlagConfig.Empty)
        assertFalse(FeatureFlagConfig.Empty.boolean("enabled", false))
    }
}
