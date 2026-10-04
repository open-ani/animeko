/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.remotesettings

import androidx.datastore.core.DataStore
import io.ktor.http.Url
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.persistent.PlatformDataStoreManager
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.mediasource.codec.ExportedMediaSourceDataList
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.decodeFromStringOrNull
import me.him188.ani.app.domain.mediasource.codec.serializeToString
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionUpdater
import me.him188.ani.app.domain.settings.remote.BackupRequest
import me.him188.ani.app.domain.settings.remote.DanmakuFilterRequest
import me.him188.ani.app.domain.settings.remote.MediaSourceCommand
import me.him188.ani.app.domain.settings.remote.MediaSourceRequest
import me.him188.ani.app.domain.settings.remote.PreferenceRequest
import me.him188.ani.app.domain.settings.remote.RemoteBackupCommand
import me.him188.ani.app.domain.settings.remote.RemoteBackupPreview
import me.him188.ani.app.domain.settings.remote.RemoteBackupResult
import me.him188.ani.app.domain.settings.remote.RemoteOperationPayload
import me.him188.ani.app.domain.settings.remote.RemotePreferenceRegistry
import me.him188.ani.app.domain.settings.remote.RemotePreferencesSnapshot
import me.him188.ani.app.domain.settings.remote.RemoteSettingsBackup
import me.him188.ani.app.domain.settings.remote.RemoteSettingsException
import me.him188.ani.app.domain.settings.remote.RemoteSettingsRevision
import me.him188.ani.app.domain.settings.remote.RemoteSourceParameter
import me.him188.ani.app.domain.settings.remote.RemoteSourceTemplate
import me.him188.ani.app.domain.settings.remote.SettingsSnapshot
import me.him188.ani.app.domain.settings.remote.VersionedValue
import me.him188.ani.app.domain.settings.remote.checkRemote
import me.him188.ani.app.domain.settings.remote.key
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.parameter.BooleanParameter
import me.him188.ani.datasources.api.source.parameter.SimpleEnumParameter
import me.him188.ani.datasources.api.source.parameter.StringParameter
import me.him188.ani.remote.settings.RemoteSettingsProtocol
import me.him188.ani.remote.settings.generated.models.LogSnapshot
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.collections.partiallyReorderBy

interface RemoteSettingsBackend {
    suspend fun snapshot(): SettingsSnapshot

    suspend fun preference(request: PreferenceRequest): RemoteOperationPayload

    suspend fun mediaSource(request: MediaSourceRequest): RemoteOperationPayload

    suspend fun danmakuFilter(request: DanmakuFilterRequest): RemoteOperationPayload

    suspend fun backup(request: BackupRequest): RemoteOperationPayload

    suspend fun log(): LogSnapshot
}

/** Uses the same DataStore instances as the TV repositories. CAS checks run inside updateData. */
class LocalRemoteSettingsBackend(
    private val scope: CoroutineScope,
    settings: SettingsRepository,
    private val stores: PlatformDataStoreManager,
    private val sources: MediaSourceManager,
    private val codecs: MediaSourceCodecManager,
    private val updater: MediaSourceSubscriptionUpdater,
    private val revisions: RemoteSettingsRevision,
    private val readLog: suspend () -> LogSnapshot,
) : RemoteSettingsBackend {
    private val json = RemoteSettingsProtocol.json
    private val preferences = RemotePreferenceRegistry(settings, revisions)
    private val plans = mutableMapOf<String, RestorePlan>()
    private val planMutex = Mutex()
    private val mediaSources =
        Resource(
            "mediaSources",
            stores.mediaSourceSaveStore,
            ListSerializer(MediaSourceSave.serializer()),
            get = { it.instances },
            set = { saves, instances -> saves.copy(instances = instances) },
        )
    private val subscriptions =
        Resource(
            "subscriptions",
            stores.mediaSourceSubscriptionStore,
            ListSerializer(MediaSourceSubscription.serializer()),
            get = { it.list },
            set = { data, list -> data.copy(list = list) },
        )
    private val danmakuFilters =
        Resource(
            "danmakuFilters",
            stores.danmakuFilterStore,
            ListSerializer(DanmakuRegexFilter.serializer()),
            get = { it },
            set = { _, filters -> filters },
        )

    override suspend fun snapshot(): SettingsSnapshot =
        SettingsSnapshot(
            preferences = preferences.snapshot(),
            mediaSources = mediaSources.read(),
            subscriptions = subscriptions.read(),
            danmakuFilters = danmakuFilters.read(),
            templates =
                sources.allFactories
                    .filterNot { sources.isLocal(it.factoryId) }
                    .map { factory ->
                        RemoteSourceTemplate(
                            factory.factoryId.value,
                            factory.info.displayName,
                            factory.info.description.orEmpty(),
                            factory.allowMultipleInstances,
                            factory.parameters.list.map { parameter ->
                                RemoteSourceParameter(
                                    parameter.name,
                                    parameter.description.orEmpty(),
                                    when (parameter) {
                                        is BooleanParameter -> "boolean"
                                        is SimpleEnumParameter -> "enum"
                                        is StringParameter -> "string"
                                    },
                                    parameter.default().toString(),
                                    (parameter as? SimpleEnumParameter)?.oneOf.orEmpty(),
                                    (parameter as? StringParameter)?.isRequired == true,
                                    parameter.visibleWhen?.parameterName,
                                    parameter.visibleWhen?.acceptedValues.orEmpty(),
                                )
                            },
                            iconUrl = factory.info.iconUrl,
                        )
                    },
        )

    override suspend fun preference(request: PreferenceRequest): RemoteOperationPayload {
        preferences.write(request.baseRevision, request.value)
        return RemoteOperationPayload.Applied
    }

    override suspend fun mediaSource(request: MediaSourceRequest): RemoteOperationPayload {
        when (val command = request.command) {
            is MediaSourceCommand.Export -> return exportSources(command.ids)
            is MediaSourceCommand.SubscriptionRefresh ->
                refreshSubscriptions(command.id, request.baseRevision)
            is MediaSourceCommand.SubscriptionList ->
                subscriptions.update(request.baseRevision) { editSubscriptions(it, command) }
            is MediaSourceCommand.SourceList ->
                mediaSources.update(request.baseRevision) { editSources(it, command) }
        }
        return RemoteOperationPayload.Applied
    }

    private suspend fun exportSources(ids: List<String>): RemoteOperationPayload {
        val saves = mediaSources.read().value
        requireIds(saves, ids)
        return RemoteOperationPayload.SourceExport(
            codecs.serializeToString(
                ExportedMediaSourceDataList(
                    saves
                        .filter { it.instanceId in ids }
                        .map {
                            val arguments =
                                it.config.serializedArguments
                                    ?: throw RemoteSettingsException(
                                        "UNSUPPORTED_EXPORT",
                                        "This source requires backup export",
                                    )
                            codecs.serialize(it.factoryId, arguments)
                        }
                )
            )
        )
    }

    /** Refreshes subscription [id], or every enabled subscription when it is null. */
    private suspend fun refreshSubscriptions(id: String?, baseRevision: String?) {
        val before = subscriptions.read()
        subscriptions.checkRevision(baseRevision, before.value)
        if (id != null)
            checkRemote(
                before.value.any { it.subscriptionId == id && it.enabled },
                "NOT_FOUND",
                "Subscription missing or disabled",
            )
        updater.updateAllOutdated(force = true, subscriptionId = id)
        checkRemote(
            subscriptions
                .read()
                .value
                .filter { it.enabled && (id == null || it.subscriptionId == id) }
                .none { it.lastUpdated?.error != null },
            "SUBSCRIPTION_REFRESH_FAILED",
            "Some subscriptions failed to refresh",
        )
    }

    private suspend fun editSubscriptions(
        list: List<MediaSourceSubscription>,
        command: MediaSourceCommand.SubscriptionList,
    ): List<MediaSourceSubscription> =
        when (command) {
            is MediaSourceCommand.SubscriptionAdd -> {
                checkRemote(list.size < MAX_SUBSCRIPTIONS, message = "Subscription limit reached")
                validateSubscription(command.subscription)
                checkRemote(
                    list.none { it.subscriptionId == command.subscription.subscriptionId },
                    message = "Subscription already exists",
                )
                list + command.subscription.copy(lastUpdated = null)
            }
            is MediaSourceCommand.SubscriptionEdit -> {
                val edited = command.subscription
                validateSubscription(edited)
                val previous =
                    list.firstOrNull { it.subscriptionId == edited.subscriptionId }
                        ?: throw RemoteSettingsException("NOT_FOUND", "Subscription not found")
                if (previous.enabled != edited.enabled) {
                    sources.setEnabled(
                        sources.getListBySubscriptionId(previous.subscriptionId).map {
                            it.instanceId
                        },
                        edited.enabled,
                    )
                }
                // The result of the last update describes the URL it was fetched from.
                val lastUpdated = previous.lastUpdated.takeIf { previous.url == edited.url }
                list.map { if (it === previous) edited.copy(lastUpdated = lastUpdated) else it }
            }
            is MediaSourceCommand.SubscriptionDelete -> {
                checkRemote(
                    list.any { it.subscriptionId == command.id },
                    "NOT_FOUND",
                    "Subscription not found",
                )
                list.filterNot { it.subscriptionId == command.id }
            }
        }

    private fun editSources(
        list: List<MediaSourceSave>,
        command: MediaSourceCommand.SourceList,
    ): List<MediaSourceSave> =
        when (command) {
            is MediaSourceCommand.Add -> {
                checkRemote(list.size < MAX_SOURCES, message = "Source limit reached")
                validateSource(command.source)
                checkRemote(
                    list.none { it.instanceId == command.source.instanceId },
                    message = "Source already exists",
                )
                val factory = sources.allFactories.first { it.factoryId == command.source.factoryId }
                checkRemote(
                    factory.allowMultipleInstances ||
                        list.none { it.factoryId == factory.factoryId },
                    message = "Source can only be added once",
                )
                checkRemote(
                    command.source.config.subscriptionId == null,
                    message = "A new source cannot specify a subscription",
                )
                list + command.source
            }
            is MediaSourceCommand.Import -> {
                val data =
                    codecs.decodeFromStringOrNull(command.text)
                        ?: throw RemoteSettingsException(
                            "INVALID_ARGUMENT",
                            "Invalid source import format",
                        )
                checkRemote(
                    data.mediaSources.isNotEmpty() &&
                        data.mediaSources.size + list.size <= MAX_SOURCES,
                    message = "Invalid number of imported sources",
                )
                list +
                    data.mediaSources.map {
                        val arguments = codecs.encode(codecs.decode(it)).arguments
                        val id = Uuid.randomString()
                        MediaSourceSave(
                                id,
                                id,
                                it.factoryId,
                                true,
                                MediaSourceConfig(serializedArguments = arguments),
                            )
                            .also(::validateSource)
                    }
            }
            is MediaSourceCommand.Edit -> {
                checkRemote(
                    list.any { it.instanceId == command.instanceId },
                    "NOT_FOUND",
                    "Source not found",
                )
                list.map { source ->
                    if (source.instanceId != command.instanceId) source
                    else {
                        checkRemote(
                            command.config.subscriptionId == source.config.subscriptionId,
                            message = "Source subscription membership cannot be changed",
                        )
                        source.copy(config = command.config).also(::validateSource)
                    }
                }
            }
            is MediaSourceCommand.Delete -> {
                requireIds(list, command.ids)
                list.filterNot { it.instanceId in command.ids }
            }
            is MediaSourceCommand.Enable -> {
                requireIds(list, command.ids)
                list.map {
                    if (it.instanceId in command.ids) it.copy(isEnabled = command.enabled) else it
                }
            }
            is MediaSourceCommand.Reorder -> {
                requireIds(list, command.ids)
                list.partiallyReorderBy({ it.instanceId }, command.ids)
            }
        }

    override suspend fun danmakuFilter(request: DanmakuFilterRequest): RemoteOperationPayload {
        val filters = request.command.filters
        validateFilters(filters)
        danmakuFilters.update(request.baseRevision) { filters }
        return RemoteOperationPayload.Applied
    }

    override suspend fun log(): LogSnapshot = readLog()

    override suspend fun backup(request: BackupRequest): RemoteOperationPayload =
        when (val command = request.command) {
            RemoteBackupCommand.Export -> RemoteOperationPayload.BackupExport(exportBackup())
            is RemoteBackupCommand.Preview -> {
                validateBackup(command.backup)
                val id = Uuid.randomString()
                val plan =
                    RestorePlan(
                        command.backup,
                        preferences.snapshot(),
                        mediaSources.read().revision,
                        subscriptions.read().revision,
                        danmakuFilters.read().revision,
                    )
                planMutex.withLock {
                    checkRemote(plans.size < MAX_PLANS, "BUSY", "Too many pending restore plans")
                    plans[id] = plan
                }
                scope.launch {
                    delay(PLAN_TTL)
                    planMutex.withLock { plans.remove(id) }
                }
                RemoteOperationPayload.BackupPreview(
                    RemoteBackupPreview(
                        id,
                        command.backup.preferences.size,
                        command.backup.mediaSources.size,
                        command.backup.subscriptions.size,
                        command.backup.danmakuFilters.size,
                    )
                )
            }
            is RemoteBackupCommand.Apply -> {
                val plan =
                    planMutex.withLock { plans.remove(command.planId) }
                        ?: throw RemoteSettingsException(
                            "PLAN_EXPIRED",
                            "Restore plan is no longer valid",
                        )
                val applied = mutableListOf<String>()
                val failed = mutableMapOf<String, String>()
                suspend fun apply(name: String, action: suspend () -> Unit) {
                    try {
                        action()
                        applied += name
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed[name] = (e as? RemoteSettingsException)?.code ?: "APPLY_FAILED"
                    }
                }
                for (value in plan.backup.preferences) apply(value.key) {
                    preferences.write(plan.preferences.revisionOf(value), value)
                }
                // Cross-store restores report each committed resource; they do not promise
                // cross-store atomicity.
                apply(subscriptions.name) {
                    subscriptions.update(plan.subscriptionsRevision) {
                        plan.backup.subscriptions.map { it.copy(lastUpdated = null) }
                    }
                }
                apply(mediaSources.name) {
                    checkRemote(
                        subscriptions.name !in failed,
                        "DEPENDENCY_FAILED",
                        "Subscription restore failed",
                    )
                    mediaSources.update(plan.mediaSourcesRevision) {
                        plan.backup.mediaSources
                    }
                }
                apply(danmakuFilters.name) {
                    danmakuFilters.update(plan.danmakuFiltersRevision) {
                        plan.backup.danmakuFilters
                    }
                }
                RemoteOperationPayload.BackupApplied(RemoteBackupResult(applied, failed))
            }
        }

    private suspend fun exportBackup() =
        RemoteSettingsBackup(
            preferences = preferences.snapshot().values(),
            mediaSources = mediaSources.read().value,
            subscriptions = subscriptions.read().value.map { it.copy(lastUpdated = null) },
            danmakuFilters = danmakuFilters.read().value,
        )

    private suspend fun validateBackup(backup: RemoteSettingsBackup) {
        checkRemote(
            backup.schemaVersion == RemoteSettingsProtocol.SCHEMA_VERSION,
            "VERSION_MISMATCH",
            "Incompatible backup version",
        )
        checkRemote(
            backup.preferences.hasDistinct { it.key },
            message = "Duplicate preferences in backup",
        )
        for (value in backup.preferences) preferences.validate(value)
        checkRemote(
            backup.mediaSources.size <= MAX_SOURCES &&
                backup.mediaSources.hasDistinct { it.instanceId },
            message = "Invalid source list",
        )
        checkRemote(
            backup.subscriptions.size <= MAX_SUBSCRIPTIONS &&
                backup.subscriptions.hasDistinct { it.subscriptionId },
            message = "Invalid subscription list",
        )
        backup.mediaSources.forEach(::validateSource)
        backup.subscriptions.forEach(::validateSubscription)
        validateFilters(backup.danmakuFilters)
    }

    private fun validateSource(source: MediaSourceSave) {
        checkRemote(
            source.instanceId.length in 1..128 && source.mediaSourceId.length in 1..128,
            message = "Invalid source ID",
        )
        val factory =
            sources.allFactories.firstOrNull {
                it.factoryId == source.factoryId && !sources.isLocal(it.factoryId)
            } ?: throw RemoteSettingsException("UNSUPPORTED_FACTORY", "Unsupported source factory")
        source.config.serializedArguments?.let { codecs.deserializeArgument(source.factoryId, it) }
        factory.parameters.list.forEach { parameter ->
            val value = source.config.arguments[parameter.name] ?: parameter.default().toString()
            val visible =
                parameter.visibleWhen?.let {
                    source.config.arguments[it.parameterName] in it.acceptedValues
                } ?: true
            if (visible)
                when (parameter) {
                    is StringParameter ->
                        checkRemote(
                            parameter.validate(value),
                            message = "Invalid source parameter: ${parameter.name}",
                        )
                    is SimpleEnumParameter ->
                        checkRemote(
                            value in parameter.oneOf,
                            message = "Invalid source option: ${parameter.name}",
                        )
                    is BooleanParameter ->
                        checkRemote(
                            value in listOf("true", "false"),
                            message = "Invalid source switch: ${parameter.name}",
                        )
                }
        }
    }

    private fun validateSubscription(subscription: MediaSourceSubscription) {
        checkRemote(
            subscription.subscriptionId.length in 1..128 && subscription.url.length <= 4096,
            message = "Invalid subscription parameters",
        )
        val url = Url(subscription.url)
        checkRemote(
            url.protocol.name in listOf("http", "https") && url.host.isNotBlank(),
            message = "Subscription URL must use HTTP or HTTPS",
        )
        checkRemote(
            subscription.updatePeriod >= 1.minutes && subscription.updatePeriod.isFinite(),
            message = "Subscription interval must be at least one minute",
        )
    }

    private fun validateFilters(filters: List<DanmakuRegexFilter>) {
        checkRemote(
            filters.size <= MAX_FILTERS && filters.hasDistinct { it.id },
            message = "Invalid filter rule list",
        )
        filters.forEach {
            checkRemote(
                it.id.length in 1..128 && it.regex.length in 1..4096 && it.name.length <= 256,
                message = "Invalid filter rule",
            )
            try {
                Regex(it.regex)
            } catch (_: Exception) {
                throw RemoteSettingsException("INVALID_REGEX", "Invalid regular expression")
            }
        }
    }

    private fun requireIds(saves: List<MediaSourceSave>, ids: List<String>) {
        checkRemote(
            ids.isNotEmpty() &&
                ids.hasDistinct { it } &&
                ids.all { id -> saves.any { it.instanceId == id } },
            "NOT_FOUND",
            "Source list revision has changed",
        )
    }

    /**
     * A list kept in one of the TV stores. [update] compares the revision and writes the new list
     * in the same store transaction.
     */
    private inner class Resource<S, T>(
        val name: String,
        private val store: DataStore<S>,
        private val serializer: KSerializer<T>,
        private val get: (S) -> T,
        private val set: (S, T) -> S,
    ) {
        /** The last value and its revision. The store returns the same instance until it changes. */
        @Volatile private var cached: Pair<T, String>? = null

        private fun revision(value: T): String {
            cached?.let { (cachedValue, revision) -> if (cachedValue === value) return revision }
            return revisions.of(name, json.encodeToString(serializer, value)).also {
                cached = value to it
            }
        }

        suspend fun read(): VersionedValue<T> {
            val value = get(store.data.first())
            return VersionedValue(revision(value), value)
        }

        fun checkRevision(expected: String?, value: T) {
            checkRemote(
                expected != null && expected == revision(value),
                "REVISION_CONFLICT",
                "Settings revision has changed",
            )
        }

        suspend fun update(expected: String?, transform: suspend (T) -> T) {
            store.updateData { stored ->
                val current = get(stored)
                checkRevision(expected, current)
                set(stored, transform(current))
            }
        }
    }

    /** A validated backup and the revisions of the settings it was previewed against. */
    private class RestorePlan(
        val backup: RemoteSettingsBackup,
        val preferences: RemotePreferencesSnapshot,
        val mediaSourcesRevision: String,
        val subscriptionsRevision: String,
        val danmakuFiltersRevision: String,
    )

    private companion object {
        const val MAX_SOURCES = 1000
        const val MAX_SUBSCRIPTIONS = 100
        const val MAX_FILTERS = 1000
        const val MAX_PLANS = 8
        val PLAN_TTL = 5.minutes
    }
}

private inline fun <T, K> List<T>.hasDistinct(key: (T) -> K): Boolean =
    mapTo(HashSet(), key).size == size
