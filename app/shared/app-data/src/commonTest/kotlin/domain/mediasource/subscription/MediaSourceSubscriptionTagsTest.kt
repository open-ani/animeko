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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionRepository
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionsSaveData
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.mediasource.MediaSourceTags
import me.him188.ani.app.domain.mediasource.codec.createTestMediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.serializeSubscriptionToString
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaSourceSubscriptionTagsTest {
    private val codecManager = createTestMediaSourceCodecManager()

    private fun subscription(tags: String) = codecManager.decodeSubscriptionFromString(
        """{"mediaSources":[{"id":"mikan","tags":$tags,"factoryId":"rss","version":1,"arguments":{"name":"Mikan","description":"","iconUrl":""}}]}""",
    )

    @Test
    fun `reads tags`() {
        assertEquals(listOf(MediaSourceTags.HLS_ADS, "future"), subscription("""["hls-ads","future"]""").mediaSources.single().tags)
    }

    @Test
    fun `new and updated media sources take tags from subscription`() = runTest {
        val sources = RecordingSources()
        var remote = subscription("""["hls-ads"]""")
        val updater = MediaSourceSubscriptionUpdater(
            MediaSourceSubscriptionRepository(
                MemoryDataStore(
                    MediaSourceSubscriptionsSaveData.Default.copy(
                        list = listOf(MediaSourceSubscription("group", "https://example.com/sources.json")),
                    ),
                ),
            ),
            sources,
            codecManager,
        ) { remote }

        updater.update("group")
        assertEquals(listOf(MediaSourceTags.HLS_ADS), sources.saves.single().config.tags)

        remote = subscription("[]")
        updater.update("group")
        assertEquals(emptyList(), sources.saves.single().config.tags)
    }

    @Test
    fun `exports tags of media sources`() {
        val save = MediaSourceSave(
            "instance", "instance", FactoryId("rss"), true,
            MediaSourceConfig(
                serializedArguments = subscription("[]").mediaSources.single().arguments,
                subscriptionId = "group",
                tags = listOf(MediaSourceTags.HLS_ADS),
            ),
        )
        val exported = Json.parseToJsonElement(codecManager.serializeSubscriptionToString(listOf(save))).jsonObject
        val tags = exported.getValue("mediaSources").jsonArray.single().jsonObject.getValue("tags").jsonArray
        assertEquals(listOf(MediaSourceTags.HLS_ADS), tags.map { it.jsonPrimitive.content })
    }

    private class RecordingSources : MediaSourceManager {
        var saves: List<MediaSourceSave> = emptyList()

        override val allInstances: Flow<List<MediaSourceInstance>> = flowOf(emptyList())
        override val allFactories: List<MediaSourceFactory> = emptyList()
        override val allFactoryIds: List<FactoryId> = emptyList()
        override val mediaFetcher: Flow<MediaFetcher> = flowOf()
        override val webVideoMatcherLoader = MediaSourceWebVideoMatcherLoader(flowOf(emptyList()))
        override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> = flowOf(null)
        override fun mediaSourceTiersFlow() = flowOf(MediaSelectorSourceTiers.Empty)
        override suspend fun addInstance(instanceId: String, mediaSourceId: String, factoryId: FactoryId, config: MediaSourceConfig) {
            saves = saves + MediaSourceSave(instanceId, mediaSourceId, factoryId, true, config)
        }

        override suspend fun getListBySubscriptionId(subscriptionId: String) = saves.filter { it.config.subscriptionId == subscriptionId }
        override suspend fun partiallyReorderInstances(instanceIds: List<String>) {}
        override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig): Boolean {
            saves = saves.map { if (it.instanceId == instanceId) it.copy(config = config) else it }
            return true
        }

        override suspend fun setEnabled(instanceId: String, enabled: Boolean) = error("Not used")
        override suspend fun removeInstance(instanceId: String) {
            saves = saves.filter { it.instanceId != instanceId }
        }
    }
}
