/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.domain.settings.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import me.him188.ani.app.domain.mediasource.MediaSourceConfigurationEditor
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.remote.settings.RemoteSettingsProtocol

/**
 * 编辑器自动保存基于打开时或上次保存成功时确认的配置；请求发送后不会被下一次输入取消。
 *
 * 该数据源在电视上被其他途径修改或删除后，保存会因 revision 冲突失败，不会覆盖外部修改。
 * 列表中其他数据源的变化不影响保存。
 */
class RemoteMediaSourceEditor(
    private val session: RemoteSettingsSession,
    private val instanceId: String,
    scope: CoroutineScope,
) : MediaSourceConfigurationEditor {
    private fun SettingsSnapshot.source() =
        mediaSources.value.firstOrNull { it.instanceId == instanceId }

    private var revision = session.snapshot.value.mediaSources.revision

    /**
     * Changes to other sources only advance the list revision, so a draft of an unchanged source
     * is sent with [snapshot]'s revision. Otherwise the confirmed revision makes the TV reject it.
     */
    private fun baseRevision(snapshot: SettingsSnapshot): String =
        if (snapshot.source()?.config == confirmedConfig.value) {
            snapshot.mediaSources.revision
        } else {
            revision
        }
    private val confirmedConfig =
        MutableStateFlow(
            checkNotNull(session.snapshot.value.source()) { "Missing media source $instanceId" }
                .config
        )
    override val config = confirmedConfig.asStateFlow()

    private class Change(val config: MediaSourceConfig)

    private val pending = MutableStateFlow<Change?>(null)
    override val isSaving = pending.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)
    private val changes = Channel<Change>(Channel.CONFLATED)

    init {
        scope.launch {
            for (change in changes) {
                delay(500)
                val latest = changes.tryReceive().getOrNull() ?: change
                try {
                    session.mediaSource(
                        MediaSourceCommand.Edit(instanceId, latest.config),
                        ::baseRevision,
                    )
                    val snapshot = session.snapshot.value
                    snapshot.source()?.let { confirmedConfig.value = it.config }
                    revision = snapshot.mediaSources.revision
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    scope.coroutineContext[CoroutineExceptionHandler]?.handleException(
                        scope.coroutineContext,
                        e,
                    ) ?: throw e
                } finally {
                    pending.compareAndSet(latest, null)
                }
            }
        }
    }

    override fun <T : MediaSourceArguments> saveArguments(serializer: KSerializer<T>, arguments: T) {
        val change =
            Change(
                confirmedConfig.value.copy(
                    serializedArguments =
                        RemoteSettingsProtocol.json.encodeToJsonElement(serializer, arguments)
                )
            )
        pending.value = change
        if (changes.trySend(change).isFailure) {
            pending.compareAndSet(change, null)
            error("Source editor is closed")
        }
    }

    override fun close() {
        changes.close()
    }
}
