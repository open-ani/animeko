/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.remote

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.settings.remote.MediaSourceCommand
import me.him188.ani.app.domain.settings.remote.RemoteMediaSourceEditor
import me.him188.ani.app.domain.settings.remote.RemoteSettingsSession
import me.him188.ani.app.domain.settings.remote.RemoteSourceTemplate
import me.him188.ani.app.ui.foundation.produceState
import me.him188.ani.app.ui.settings.tabs.media.source.EditMediaSourceState
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceGroupState
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourcePresentation
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceSubscriptionGroupState
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceTemplate
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.parameter.BooleanParameter
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameterVisibilityCondition
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.datasources.api.source.parameter.SimpleEnumParameter
import me.him188.ani.datasources.api.source.parameter.StringParameter

/** 把电视快照和命令适配为本地设置页已有的状态模型，不创建数据源引擎。 */
internal class RemoteMediaSourcesState(
    val session: RemoteSettingsSession,
    private val scope: CoroutineScope,
) {
    private val snapshot by session.snapshot.produceState(session.snapshot.value, scope)
    val sources by derivedStateOf { snapshot.mediaSources.value }
    private val subscriptions by derivedStateOf { snapshot.subscriptions.value }
    val templates by derivedStateOf { snapshot.templates }

    val group =
        MediaSourceGroupState(
            derivedStateOf {
                sources.map { source ->
                    val template = templates.find { it.factoryId == source.factoryId.value }
                    val arguments = source.config.serializedArguments as? JsonObject
                    MediaSourcePresentation(
                        instanceId = source.instanceId,
                        mediaSourceId = source.mediaSourceId,
                        factoryId = source.factoryId,
                        isEnabled = source.isEnabled,
                        info =
                            MediaSourceInfo(
                                displayName =
                                    arguments?.get("name")?.jsonPrimitive?.contentOrNull
                                        ?: template?.name
                                        ?: source.factoryId.value,
                                description = template?.description,
                                iconUrl =
                                    arguments?.get("iconUrl")?.jsonPrimitive?.contentOrNull
                                        ?: template?.iconUrl,
                            ),
                        parameters = template?.toParameters() ?: MediaSourceParameters.Empty,
                        ownerSubscriptionUrl =
                            subscriptions
                                .find { it.subscriptionId == source.config.subscriptionId }
                                ?.url,
                    )
                }
            },
            derivedStateOf {
                templates
                    .filter { template ->
                        template.allowMultiple ||
                            sources.none { it.factoryId.value == template.factoryId }
                    }
                    .map {
                        MediaSourceTemplate(
                            FactoryId(it.factoryId),
                            MediaSourceInfo(it.name, it.description, iconUrl = it.iconUrl),
                            it.toParameters(),
                        )
                    }
            },
            onReorder = { session.mediaSource(MediaSourceCommand.Reorder(it)) },
            scope,
        )
    val subscriptionsGroup =
        MediaSourceSubscriptionGroupState(
            derivedStateOf { subscriptions },
            onUpdateAll = { session.mediaSource(MediaSourceCommand.SubscriptionRefresh()) },
            onAdd = { session.mediaSource(MediaSourceCommand.SubscriptionAdd(it)) },
            onDelete = { subscription ->
                scope.launch {
                    session.mediaSource(
                        MediaSourceCommand.SubscriptionDelete(subscription.subscriptionId)
                    )
                }
            },
            onExportLocalChangesToString = { subscription ->
                session.exportSources(
                    sources
                        .filter { it.config.subscriptionId == subscription.subscriptionId }
                        .map { it.instanceId }
                )
            },
            scope,
        )

    fun editor(instanceId: String) = RemoteMediaSourceEditor(session, instanceId, scope)

    private val editingConfigs = mutableMapOf<String, Pair<MediaSourceConfig, String>>()

    val edit =
        EditMediaSourceState(
            getConfigFlow = { id ->
                val config = sources.first { it.instanceId == id }.config
                editingConfigs[id] = config to session.snapshot.value.mediaSources.revision
                flowOf(config)
            },
            onAdd = { factory, id, config ->
                val template = templates.first { it.factoryId == factory.value }
                session.mediaSource(
                    MediaSourceCommand.Add(
                        MediaSourceSave(
                            id,
                            if (template.allowMultiple) id else factory.value,
                            factory,
                            true,
                            config,
                        )
                    )
                )
            },
            onEdit = { id, config ->
                val (original, revision) = editingConfigs.getValue(id)
                session.mediaSource(
                    MediaSourceCommand.Edit(id, original.copy(arguments = config.arguments)),
                    revision,
                )
                editingConfigs.remove(id)
            },
            onDelete = { session.mediaSource(MediaSourceCommand.Delete(it)) },
            onSetEnabled = { ids, enabled ->
                session.mediaSource(MediaSourceCommand.Enable(ids, enabled))
            },
            backgroundScope = scope,
        )
}

internal fun RemoteSourceTemplate.toParameters() =
    MediaSourceParameters(
        parameters.map { parameter ->
            val visibleWhen =
                parameter.visibleWhen?.let {
                    MediaSourceParameterVisibilityCondition(it, parameter.acceptedValues)
                }
            when (parameter.kind) {
                "boolean" ->
                    BooleanParameter(
                        parameter.name,
                        parameter.description,
                        { parameter.defaultValue.toBoolean() },
                        visibleWhen,
                    )
                "enum" ->
                    SimpleEnumParameter(
                        parameter.name,
                        parameter.choices,
                        parameter.description,
                        { parameter.defaultValue },
                        visibleWhen,
                    )
                else ->
                    StringParameter(
                        parameter.name,
                        parameter.description,
                        { parameter.defaultValue },
                        isRequired = parameter.required,
                        visibleWhen = visibleWhen,
                    )
            }
        }
    )
