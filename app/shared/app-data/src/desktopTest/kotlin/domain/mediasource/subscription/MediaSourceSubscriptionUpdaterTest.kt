/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.subscription

import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.persistent.DataStoreJson
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionRepository
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionsSaveData
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.mediasource.codec.ExportedMediaSourceDataList
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager

class MediaSourceSubscriptionUpdaterTest {
    private val subscription = MediaSourceSubscription("subscription", "https://old.example/sources.json")

    private fun repository() = MediaSourceSubscriptionRepository(
        MemoryDataStore(MediaSourceSubscriptionsSaveData.Default.copy(list = listOf(subscription))),
    )

    /** Any call means the updater reconciled sources for the subscription. */
    private val untouchedSources = Proxy.newProxyInstance(
        MediaSourceManager::class.java.classLoader,
        arrayOf(MediaSourceManager::class.java),
    ) { _, method, _ -> error("Unexpected MediaSourceManager.${method.name}") } as MediaSourceManager

    @Test
    fun subscriptionEditedDuringRequestKeepsTheEditedSubscription() = runTest {
        val repository = repository()
        val requested = CompletableDeferred<Unit>()
        val response = CompletableDeferred<SubscriptionUpdateData>()
        val updater = MediaSourceSubscriptionUpdater(
            repository,
            untouchedSources,
            MediaSourceCodecManager(),
        ) {
            requested.complete(Unit)
            response.await()
        }

        val update = launch { updater.updateAllOutdated(force = true) }
        requested.await()
        repository.update(subscription.subscriptionId) { it.copy(url = "https://new.example/sources.json") }
        response.complete(SubscriptionUpdateData(ExportedMediaSourceDataList(emptyList())))
        update.join()

        val current = repository.flow.first().single()
        assertEquals("https://new.example/sources.json", current.url)
        assertNull(current.lastUpdated)
    }

    @Test
    fun failedUpdateIsPersisted() = runTest {
        val repository = repository()
        val updater = MediaSourceSubscriptionUpdater(
            repository,
            untouchedSources,
            MediaSourceCodecManager(),
        ) { throw RepositoryNetworkException("offline") }

        updater.updateAllOutdated(force = true)

        val saved = MediaSourceSubscriptionsSaveData.Default.copy(list = repository.flow.first())
        val restored = DataStoreJson.decodeFromString(
            MediaSourceSubscriptionsSaveData.serializer(),
            DataStoreJson.encodeToString(MediaSourceSubscriptionsSaveData.serializer(), saved),
        )
        assertIs<ApiFailure.NetworkError>(restored.list.single().lastUpdated?.error?.failure)
    }
}
