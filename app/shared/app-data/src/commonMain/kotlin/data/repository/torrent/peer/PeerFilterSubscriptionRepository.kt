/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.torrent.peer

import androidx.datastore.core.DataStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource
import me.him188.ani.app.domain.torrent.peer.PeerFilterRule
import me.him188.ani.app.domain.torrent.peer.PeerFilterSubscription
import me.him188.ani.utils.coroutines.update
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.bufferedSource
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeText
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

class PeerFilterSubscriptionRepository(
    private val dataStore: DataStore<PeerFilterSubscriptionsSaveData>,
    private val ruleSaveDir: SystemPath,
    private val httpClient: Flow<HttpClient>
) {
    private val logger = logger<PeerFilterSubscriptionRepository>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // 已经加载到内存的订阅规则. 需要缓存防止重复加载.
    private val loadedSubRules: MutableStateFlow<PersistentMap<String, PeerFilterRule>> =
        MutableStateFlow(persistentMapOf())

    val presentationFlow get() = dataStore.data.map { it.list }
    val rulesFlow: Flow<List<PeerFilterRule>> get() = loadedSubRules.map { it.values.toList() } // to list 不会消耗太多时间

    suspend fun resolveAll() {
        dataStore.data.first().list.forEach { resolve(it.subscriptionId) }
    }

    suspend fun updateAll() {
        dataStore.data.first().list.forEach { update(it.subscriptionId) }
    }

    /**
     * 启用此订阅
     */
    suspend fun enable(subscriptionId: String) {
        if (updatePref(subscriptionId) { it.copy(enabled = true) }) {
            resolve(subscriptionId)
        }
    }

    /**
     * 禁用此订阅
     */
    suspend fun disable(subscriptionId: String) {
        if (updatePref(subscriptionId) { it.copy(enabled = false) }) {
            loadedSubRules.update { remove(subscriptionId) }
        }
    }

    /**
     * 解析此订阅规则, 若没有则会从订阅链接获取, 如果启用了会加载到内存
     */
    private suspend fun resolve(subscriptionId: String) {
        val sub = dataStore.data.first().list.firstOrNull { it.subscriptionId == subscriptionId }
        if (sub == null) {
            logger.warn { "Peer filter subscription $subscriptionId is not found." }
            return
        }

        val savedPath = ruleSaveDir.resolve("${subscriptionId}.json")
        if (!savedPath.exists()) {
            update(subscriptionId)
            return
        }

        try {
            val decoded = withContext(Dispatchers.IO) {
                json.decodeFromSource(PeerFilterRule.serializer(), savedPath.bufferedSource())
            }
            updateLoadResult(
                sub.subscriptionId,
                PeerFilterSubscription.LastLoaded(
                    ruleStat = PeerFilterSubscription.RuleStat(
                        ipRuleCount = decoded.blockedIpPattern.size,
                        idRuleCount = decoded.blockedIdRegex.size,
                        clientRuleCount = decoded.blockedClientRegex.size,
                    ),
                    error = null,
                ),
            )
            
            if (sub.enabled) {
                loadedSubRules.update { put(sub.subscriptionId, decoded) }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to resolve peer filter subscription $subscriptionId, deleting file" }

            withContext(Dispatchers.IO) { savedPath.delete() }
            updateLoadResult(
                sub.subscriptionId,
                PeerFilterSubscription.LastLoaded(
                    ruleStat = null,
                    error = e.toString(),
                ),
            )
        }
    }

    /**
     * 从订阅链接更新
     */
    suspend fun update(subscriptionId: String) {
        val sub = dataStore.data.first().list.firstOrNull { it.subscriptionId == subscriptionId }
        if (sub == null) {
            logger.warn { "Peer filter subscription $subscriptionId is not found." }
            return
        }

        try {
            val resp = httpClient.first().get(sub.url)

            val respText = resp.bodyAsText()
            ruleSaveDir.resolve("${subscriptionId}.json").writeText(respText)

            if (sub.enabled) {
                val decoded = json.decodeFromString(PeerFilterRule.serializer(), respText)
                loadedSubRules.update { put(sub.subscriptionId, decoded) }

                updateLoadResult(
                    sub.subscriptionId,
                    PeerFilterSubscription.LastLoaded(
                        ruleStat = PeerFilterSubscription.RuleStat(
                            ipRuleCount = decoded.blockedIpPattern.size,
                            idRuleCount = decoded.blockedIdRegex.size,
                            clientRuleCount = decoded.blockedClientRegex.size,
                        ),
                        error = null,
                    ),
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update peer filter subscription $subscriptionId" }
            updateLoadResult(
                sub.subscriptionId,
                PeerFilterSubscription.LastLoaded(
                    ruleStat = sub.lastLoaded?.ruleStat,
                    error = e.toString(),
                ),
            )
        }
    }

    private suspend fun updateLoadResult(
        id: String, value: PeerFilterSubscription.LastLoaded
    ) = updatePref(id) { it.copy(lastLoaded = value) }

    private suspend fun updatePref(
        id: String,
        update: suspend (PeerFilterSubscription) -> PeerFilterSubscription
    ): Boolean {
        var found = false
        dataStore.updateData { data ->
            data.copy(
                list = data.list.map { subscription ->
                    if (subscription.subscriptionId == id) {
                        found = true
                        update(subscription)
                    } else {
                        subscription
                    }
                },
            )
        }
        return found
    }

    init {
        ruleSaveDir.createDirectories()
    }
}


@Serializable
data class PeerFilterSubscriptionsSaveData(
    val list: List<PeerFilterSubscription>,
) {
    companion object {
        val Default = PeerFilterSubscriptionsSaveData(
            listOf(
                PeerFilterSubscription(
                    subscriptionId = PeerFilterSubscription.BUILTIN_SUBSCRIPTION_ID,
                    url = "", // TODO: creamy cake
                    enabled = true,
                    lastLoaded = null,
                ),
            ),
        )
    }
}