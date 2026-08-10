/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.subscription

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionRepository
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionsSaveData
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.mediasource.codec.ExportedMediaSourceDataList
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 覆盖 "启动头几秒设备网络还没就绪导致订阅更新失败" 之后的恢复行为: 失败后应当很快重试,
 * 而不是按正常的 [MediaSourceSubscription.updatePeriod] (默认 1 小时) 等待.
 */
class MediaSourceSubscriptionUpdaterTest {
    private val subscriptionId = "test-subscription"

    private var now = 1_000_000L

    private var requestCount = 0
    private var failRequest = true

    private val repository = MediaSourceSubscriptionRepository(
        MemoryDataStore(
            MediaSourceSubscriptionsSaveData(
                listOf(MediaSourceSubscription(subscriptionId, url = "https://localhost/sub.json")),
                version = 1,
            ),
        ),
    )

    private val updater = MediaSourceSubscriptionUpdater(
        subscriptions = repository,
        mediaSourceManager = NoopMediaSourceManager,
        codecManager = MediaSourceCodecManager(),
        requester = {
            requestCount++
            if (failRequest) throw RepositoryNetworkException("no network")
            SubscriptionUpdateData(ExportedMediaSourceDataList(emptyList()))
        },
        getCurrentTimeMillis = { now },
    )

    private suspend fun lastUpdated() = repository.flow.first().single().lastUpdated

    @Test
    fun `failure is retried within seconds instead of waiting a full update period`() = runTest {
        val firstDelay = updater.updateAllOutdated()
        assertEquals(1, requestCount)
        assertEquals(null, lastUpdated()?.mediaSourceCount) // null 表示失败
        assertTrue(firstDelay <= 1.minutes, "expected a short retry delay, got $firstDelay")

        // 20 秒后再来一次: 网络已就绪, 必须真的重试, 而不是被 1 小时的 updatePeriod 挡住
        now += 20_000
        failRequest = false
        updater.updateAllOutdated()
        assertEquals(2, requestCount)
        assertEquals(0, lastUpdated()?.mediaSourceCount)
    }

    @Test
    fun `retry delay grows with consecutive failures`() = runTest {
        val delays = ArrayList<kotlin.time.Duration>()
        repeat(6) {
            delays += updater.updateAllOutdated()
            now += 1.hours.inWholeMilliseconds // 保证每次都到期
        }
        assertEquals(6, requestCount)
        assertTrue(delays.first() <= 30.seconds, "first retry should be quick, got ${delays.first()}")
        assertEquals(delays.sorted(), delays, "retry delay should be non-decreasing: $delays")
        assertTrue(delays.last() < 1.hours, "retry delay should stay below updatePeriod, got ${delays.last()}")
    }

    @Test
    fun `success is not retried until update period elapses`() = runTest {
        failRequest = false
        val delay = updater.updateAllOutdated()
        assertEquals(1, requestCount)
        assertEquals(1.hours, delay)

        now += 20_000
        updater.updateAllOutdated()
        assertEquals(1, requestCount) // 没有到期, 不该再请求

        now += 1.hours.inWholeMilliseconds
        updater.updateAllOutdated()
        assertEquals(2, requestCount)
    }

    @Test
    fun `no subscription does not throw`() = runTest {
        val emptyUpdater = MediaSourceSubscriptionUpdater(
            subscriptions = MediaSourceSubscriptionRepository(
                MemoryDataStore(MediaSourceSubscriptionsSaveData(emptyList(), version = 1)),
            ),
            mediaSourceManager = NoopMediaSourceManager,
            codecManager = MediaSourceCodecManager(),
            requester = { throw AssertionError("should not request") },
            getCurrentTimeMillis = { now },
        )
        assertEquals(1.hours, emptyUpdater.updateAllOutdated())
    }
}

/**
 * 测试只走 "请求订阅" 这一步, 不涉及数据源实例管理.
 */
private object NoopMediaSourceManager : MediaSourceManager {
    override val allInstances: Flow<List<MediaSourceInstance>> get() = flowOf(emptyList())
    override val allFactories: List<MediaSourceFactory> get() = emptyList()
    override val allFactoryIds: List<FactoryId> get() = emptyList()
    override val mediaFetcher: Flow<MediaFetcher> get() = throw UnsupportedOperationException()
    override val webVideoMatcherLoader: MediaSourceWebVideoMatcherLoader
        get() = throw UnsupportedOperationException()

    override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> = flowOf(null)
    override suspend fun addInstance(
        instanceId: String,
        mediaSourceId: String,
        factoryId: FactoryId,
        config: MediaSourceConfig
    ) = Unit

    override suspend fun getListBySubscriptionId(subscriptionId: String): List<MediaSourceSave> = emptyList()
    override suspend fun partiallyReorderInstances(instanceIds: List<String>) = Unit
    override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig): Boolean = true
    override suspend fun setEnabled(instanceId: String, enabled: Boolean) = Unit
    override suspend fun removeInstance(instanceId: String) = Unit
    override fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers> =
        flowOf(MediaSelectorSourceTiers.Empty)
}
