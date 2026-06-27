/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.settings.framework.ConnectionTestResult
import me.him188.ani.app.ui.settings.framework.ConnectionTester
import me.him188.ani.app.ui.settings.framework.DefaultConnectionTesterRunner
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.platform.Uuid
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds


class MediaSourceLoader(
    private val mediaSourceManager: MediaSourceManager,
    subscriptions: Flow<List<MediaSourceSubscription>>,
    parentCoroutineContext: CoroutineContext,
) {
    private val scope = parentCoroutineContext.childScope()
    private val connectionTesters = MediaSourceConnectionTesterRegistry()

    val mediaSourcesFlow = mediaSourceManager.allInstances
        .combine(subscriptions) { instances, subscriptions ->
            val presentations = instances.mapNotNull { instance ->
                val factory = findFactory(instance.factoryId) ?: return@mapNotNull null
                MediaSourcePresentation(
                    instanceId = instance.instanceId,
                    isEnabled = instance.isEnabled,
                    mediaSourceId = instance.source.mediaSourceId,
                    factoryId = instance.factoryId,
                    info = instance.source.info,
                    parameters = factory.parameters,
                    connectionTester = connectionTesters.getOrCreate(instance),
                    instance,
                    ownerSubscriptionUrl = instance.config.subscriptionId?.let { subscriptionId ->
                        subscriptions.find { it.subscriptionId == subscriptionId }?.url
                    },
                )
            }
            connectionTesters.retain(presentations.mapTo(mutableSetOf()) { it.instanceId })
            presentations
            // 不能 sort, 会用来 reorder
        }
        .shareIn(scope, started = SharingStarted.WhileSubscribed(), replay = 1)

    val availableMediaSourceTemplates = mediaSourcesFlow.map { mediaSources ->
        mediaSourceManager.allFactories.mapNotNull { factory ->
            if (!factory.allowMultipleInstances && mediaSources.any { it.factoryId == factory.factoryId }) {
                return@mapNotNull null
            }
            MediaSourceTemplate(
                factoryId = factory.factoryId,
                info = factory.info,
                parameters = factory.parameters,
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())

    private fun findFactory(factoryId: FactoryId): MediaSourceFactory? {
        return mediaSourceManager.allFactories.find { it.factoryId == factoryId }
    }
}

private class MediaSourceConnectionTesterRegistry {
    private val holders = mutableMapOf<String, MediaSourceConnectionTesterHolder>()

    fun getOrCreate(instance: MediaSourceInstance): ConnectionTester {
        return holders.getOrPut(instance.instanceId) {
            MediaSourceConnectionTesterHolder(instance)
        }.also {
            it.update(instance)
        }.connectionTester
    }

    fun retain(instanceIds: Set<String>) {
        holders.keys.retainAll(instanceIds)
    }
}

private class MediaSourceConnectionTesterHolder(
    instance: MediaSourceInstance,
) {
    private var latestInstance = instance
    private var fingerprint = instance.connectionTestFingerprint()

    var connectionTester: ConnectionTester = createConnectionTester(instance)
        private set

    fun update(instance: MediaSourceInstance) {
        latestInstance = instance

        val newFingerprint = instance.connectionTestFingerprint()
        if (fingerprint != newFingerprint) {
            fingerprint = newFingerprint
            connectionTester = createConnectionTester(instance)
        }
    }

    private fun createConnectionTester(instance: MediaSourceInstance): ConnectionTester {
        return ConnectionTester(
            id = instance.instanceId,
            testConnection = {
                when (latestInstance.source.checkConnection()) {
                    ConnectionStatus.SUCCESS -> ConnectionTestResult.SUCCESS
                    ConnectionStatus.FAILED -> ConnectionTestResult.FAILED
                }
            },
        )
    }
}

private data class MediaSourceConnectionTestFingerprint(
    val mediaSourceId: String,
    val factoryId: FactoryId,
    val config: MediaSourceConfig,
)

private fun MediaSourceInstance.connectionTestFingerprint(): MediaSourceConnectionTestFingerprint {
    return MediaSourceConnectionTestFingerprint(
        mediaSourceId = mediaSourceId,
        factoryId = factoryId,
        config = config,
    )
}

class MediaSourceGroupState(
    mediaSourcesState: State<List<MediaSourcePresentation>>,
    availableMediaSourceTemplatesState: State<List<MediaSourceTemplate>>,
    private val onReorder: suspend (newOrder: List<String>) -> Unit,
    private val backgroundScope: CoroutineScope,
) {
    val mediaSources by mediaSourcesState
    val availableMediaSourceTemplates by availableMediaSourceTemplatesState

    val mediaSourceTesters by derivedStateOf {
        DefaultConnectionTesterRunner(
            mediaSources.map { it.connectionTester },
            backgroundScope,
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // Sorting media source
    ///////////////////////////////////////////////////////////////////////////

    var isCompletingReorder by mutableStateOf(false)
        private set

    private val reorderTasker = MonoTasker(backgroundScope)
    fun reorderMediaSources(newOrder: List<String>) {
        reorderTasker.launch {
            isCompletingReorder = true
            try {
                onReorder(newOrder)
            } finally {
                delay(0.5.seconds)
                isCompletingReorder = false
            }
        }
    }
}

class EditMediaSourceState(
    private val getConfigFlow: (instanceId: String) -> Flow<MediaSourceConfig>,
    private val onAdd: suspend (factoryId: FactoryId, instanceId: String, config: MediaSourceConfig) -> Unit,
    private val onEdit: suspend (instanceId: String, config: MediaSourceConfig) -> Unit,
    private val onDelete: suspend (instanceId: String) -> Unit,
    private val onSetEnabled: suspend (instanceId: String, enabled: Boolean) -> Unit,
    private val backgroundScope: CoroutineScope,
) {
    var editMediaSourceState by mutableStateOf<EditingMediaSource?>(null)
        private set

    fun startAdding(template: MediaSourceTemplate): EditingMediaSource {
        cancelEdit()
        val state = EditingMediaSource(
            editingMediaSourceId = Uuid.randomString(),
            factoryId = template.factoryId,
            info = template.info,
            parameters = template.parameters,
            persistedArguments = flowOf(MediaSourceConfig()),
            editMediaSourceMode = EditMediaSourceMode.Add(template.factoryId),
            onSave = { confirmEdit(it) },
            backgroundScope.coroutineContext, // TODO: this can be a memory leak
        )
        editMediaSourceState = state
        return state
    }

    fun startEditing(presentation: MediaSourcePresentation) {
        cancelEdit()
        editMediaSourceState = EditingMediaSource(
            editingMediaSourceId = presentation.mediaSourceId,
            factoryId = presentation.factoryId,
            info = presentation.info,
            parameters = presentation.parameters,
            persistedArguments = getConfigFlow(presentation.instanceId),
            editMediaSourceMode = EditMediaSourceMode.Edit(presentation.instanceId),
            onSave = {
                editTasker.launch {
                    confirmEditImpl(it)
                }.join()
            },
            backgroundScope.coroutineContext, // TODO: this can be a memory leak
        )
    }

    private val editTasker = MonoTasker(backgroundScope)

    fun confirmEdit(state: EditingMediaSource): Job {
        return editTasker.launch {
            confirmEditImpl(state)
        }
    }

    private suspend fun confirmEditImpl(state: EditingMediaSource) {
        when (state.editMediaSourceMode) {
            is EditMediaSourceMode.Add -> {
                onAdd(
                    state.factoryId,
                    state.editingMediaSourceId,
                    state.createConfig(),
                )
                withContext(Dispatchers.Main) { cancelEdit() }
            }

            is EditMediaSourceMode.Edit -> {
                onEdit(
                    state.editMediaSourceMode.instanceId,
                    state.createConfig(),
                )
                withContext(Dispatchers.Main) { cancelEdit() }
            }
        }
    }

    fun cancelEdit() {
        editMediaSourceState?.close()
        editMediaSourceState = null
    }

    fun deleteMediaSource(item: MediaSourcePresentation) {
        editTasker.launch {
            onDelete(item.instanceId)
        }
    }

    fun toggleMediaSourceEnabled(item: MediaSourcePresentation, enabled: Boolean) {
        editTasker.launch {
            onSetEnabled(item.instanceId, enabled)
        }
    }
}


/**
 * @see MediaSourceInstance
 */
@Stable
class MediaSourcePresentation(
    val instanceId: String,
    val isEnabled: Boolean,
    val mediaSourceId: String,
    val factoryId: FactoryId,
    val info: MediaSourceInfo,
    val parameters: MediaSourceParameters,
    val connectionTester: ConnectionTester,
    val instance: MediaSourceInstance,

    val ownerSubscriptionUrl: String?,
)

/**
 * 对应一个 Factory
 */
@Immutable
class MediaSourceTemplate(
    val factoryId: FactoryId,
    val info: MediaSourceInfo,
    val parameters: MediaSourceParameters
)

fun EditingMediaSource.createConfig(): MediaSourceConfig {
    return MediaSourceConfig(
        arguments = arguments.associate { it.name to it.toPersisted() },
    )
}
