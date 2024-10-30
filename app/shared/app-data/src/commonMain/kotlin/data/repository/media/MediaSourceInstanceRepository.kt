/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.media

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.mikan.MikanCNMediaSource
import me.him188.ani.utils.platform.Uuid

interface MediaSourceInstanceRepository : Repository {
    val flow: Flow<List<MediaSourceSave>>

    suspend fun clear()
    suspend fun remove(instanceId: String)
    suspend fun add(mediaSourceSave: MediaSourceSave)

    suspend fun updateSave(instanceId: String, config: MediaSourceSave.() -> MediaSourceSave): Boolean
    suspend fun reorder(newOrder: List<String>)
}

suspend inline fun MediaSourceInstanceRepository.updateConfig(instanceId: String, config: MediaSourceConfig): Boolean {
    return updateSave(instanceId) {
        copy(config = config)
    }
}

@Serializable
data class MediaSourceSaves(
    val instances: List<MediaSourceSave> = emptyList(),
) {
    companion object {
        val Empty = MediaSourceSaves(emptyList())
        val Default: MediaSourceSaves by lazy {
            fun createSave(
                sourceId: String,
                factoryId: FactoryId,
                isEnabled: Boolean
            ) = MediaSourceSave(
                instanceId = Uuid.randomString(),
                mediaSourceId = sourceId,
                factoryId = factoryId,
                isEnabled = isEnabled,
                config = MediaSourceConfig.Default,
            )

            val enabledBtSources: List<String> =
                listOf(MikanCNMediaSource.ID, "dmhy")
            val disabledBtSources: List<String> = listOf()

            MediaSourceSaves(
                buildList {
                    enabledBtSources.forEach { add(createSave(it, FactoryId(it), isEnabled = true)) }
                    disabledBtSources.forEach { add(createSave(it, FactoryId(it), isEnabled = false)) }
                },
            )
        }
    }
}

class MediaSourceInstanceRepositoryImpl(
    private val dataStore: DataStore<MediaSourceSaves>
) : MediaSourceInstanceRepository {
    override val flow: Flow<List<MediaSourceSave>> = dataStore.data.map { it.instances }
    override suspend fun clear() {
        dataStore.updateData { MediaSourceSaves.Empty }
    }

    override suspend fun remove(instanceId: String) {
        dataStore.updateData { current ->
            current.copy(instances = current.instances.filter { it.instanceId != instanceId })
        }
    }

    override suspend fun add(mediaSourceSave: MediaSourceSave) {
        dataStore.updateData { current ->
            if (current.instances.any { it.instanceId == mediaSourceSave.instanceId }) {
                error("Attempting to add a duplicated MediaSourceSave: $mediaSourceSave")
            }
            current.copy(instances = current.instances + mediaSourceSave)
        }
    }

    override suspend fun updateSave(instanceId: String, config: MediaSourceSave.() -> MediaSourceSave): Boolean {
        var found = false
        dataStore.updateData { current ->
            found = current.instances.any { it.instanceId == instanceId }
            if (found) {
                current.copy(
                    instances = current.instances.map { save ->
                        if (save.instanceId == instanceId) {
                            save.run(config)
                        } else {
                            save
                        }
                    },
                )
            } else {
                current
            }
        }
        return found
    }

    override suspend fun reorder(newOrder: List<String>) {
        dataStore.updateData { current ->
            val instances = current.instances.toMutableList()
            val newInstances = newOrder.mapNotNull { instanceId ->
                current.instances.find { it.instanceId == instanceId }
            }
            current.copy(instances = newInstances + instances.filter { it.instanceId !in newOrder })
        }
    }
}
