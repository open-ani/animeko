/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.remote.settings.RemoteSettingsLink
import me.him188.ani.remote.settings.RemoteSettingsProtocol
import me.him188.ani.remote.settings.generated.apis.RemoteSettingsApi
import me.him188.ani.remote.settings.generated.infrastructure.HttpResponse
import me.him188.ani.remote.settings.generated.models.PingRequest
import me.him188.ani.remote.settings.generated.models.PingResponse
import me.him188.ani.remote.settings.generated.models.RemoteError
import me.him188.ani.utils.platform.Uuid

/** In-memory handoff from platform deep links. Navigation entries contain no access key. */
object RemoteSettingsConnectionRequests {
    private val pending = MutableStateFlow<RemoteSettingsLink?>(null)
    val requests = pending.asStateFlow()

    fun offer(uri: String) {
        pending.value = RemoteSettingsLink.parse(uri)
    }

    fun take(): RemoteSettingsLink? = pending.value?.also { pending.compareAndSet(it, null) }
}

/**
 * Owns one TV target. It never resolves a local SettingsRepository or changes global DI bindings.
 */
class RemoteSettingsSession
private constructor(
    val device: PingResponse,
    private val api: RemoteSettingsApi,
    private val client: HttpClient,
    initial: SettingsSnapshot,
    parentScope: CoroutineScope,
) : AutoCloseable {
    private val mutableSnapshot = MutableStateFlow(initial)
    val snapshot: StateFlow<SettingsSnapshot> = mutableSnapshot.asStateFlow()
    private val mutex = Mutex()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<RemoteSettingsFailure?>(null)
    val error = mutableError.asStateFlow()
    private var closed = false
    private var needsRefresh = false
    private val scope =
        CoroutineScope(
            parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
        )
    private val instance
        get() = device.serverInstanceId

    val preferences = RemotePreferenceRepository(this)

    suspend fun refresh() = mutex.withLock {
        try {
            checkOpen()
            mutableSnapshot.value = api.getState(instance).checked()
            needsRefresh = false
            mutableError.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            needsRefresh = true
            mutableError.value = RemoteSettingsFailure.from(e)
        }
    }

    /**
     * [value] is a whole preference that the caller built from [base]. It is sent after earlier
     * writes finish, with the revision the TV has at that time, as long as the TV still holds
     * [base]. Otherwise it is rejected instead of overwriting a change the caller has not seen.
     *
     * A later edit may be built on [value], so the write stays queued when its caller leaves.
     */
    fun submitPreference(value: RemotePreference, base: RemotePreference): Deferred<Unit> =
        scope.async(start = CoroutineStart.UNDISPATCHED) {
            mutate { id ->
                val current = snapshot.value.preferences
                checkRemote(
                    base in current.values(),
                    "REVISION_CONFLICT",
                    "Settings have changed since the edit was made",
                )
                api.setPreference(
                        PreferenceRequest(id, current.revisionOf(value), value),
                        instance,
                    )
                    .checked()
            }
        }

    suspend fun mediaSource(
        command: MediaSourceCommand,
        baseRevision: String? = null,
    ): RemoteOperationPayload = mediaSource(command) { baseRevision }

    /**
     * [baseRevision] runs after earlier writes finish, with the snapshot the request is based on.
     * Returning null uses that snapshot's revision of the affected list.
     */
    suspend fun mediaSource(
        command: MediaSourceCommand,
        baseRevision: (SettingsSnapshot) -> String?,
    ): RemoteOperationPayload = mutate { id ->
        val subscription =
            command is MediaSourceCommand.SubscriptionAdd ||
                command is MediaSourceCommand.SubscriptionEdit ||
                command is MediaSourceCommand.SubscriptionDelete ||
                command is MediaSourceCommand.SubscriptionRefresh
        val current = snapshot.value
        val revision =
            baseRevision(current)
                ?: if (subscription) current.subscriptions.revision
                else current.mediaSources.revision
        api.mediaSource(
                MediaSourceRequest(
                    id,
                    command,
                    revision,
                ),
                instance,
            )
            .checked()
    }

    suspend fun danmakuFilters(command: ReplaceDanmakuFilters): RemoteOperationPayload =
        editDanmakuFilters { command.filters }

    /**
     * [transform] runs after earlier writes finish, on the filters of the snapshot whose revision
     * the request carries, so consecutive edits apply on top of each other.
     */
    suspend fun editDanmakuFilters(
        transform: (List<DanmakuRegexFilter>) -> List<DanmakuRegexFilter>
    ): RemoteOperationPayload = mutate { id ->
        val current = snapshot.value.danmakuFilters
        api.danmakuFilter(
                DanmakuFilterRequest(
                    id,
                    ReplaceDanmakuFilters(transform(current.value)),
                    current.revision,
                ),
                instance,
            )
            .checked()
    }

    private suspend fun backup(command: RemoteBackupCommand): RemoteOperationPayload =
        mutate { id ->
            api.backup(BackupRequest(id, command), instance).checked()
        }

    suspend fun exportBackup(): RemoteSettingsBackup =
        (backup(RemoteBackupCommand.Export) as RemoteOperationPayload.BackupExport).value

    suspend fun previewBackup(value: RemoteSettingsBackup): RemoteBackupPreview =
        (backup(RemoteBackupCommand.Preview(value)) as RemoteOperationPayload.BackupPreview).value

    suspend fun applyBackup(planId: String): RemoteBackupResult =
        (backup(RemoteBackupCommand.Apply(planId)) as RemoteOperationPayload.BackupApplied).value

    suspend fun exportSources(ids: List<String>): String =
        (mediaSource(MediaSourceCommand.Export(ids)) as RemoteOperationPayload.SourceExport).text

    suspend fun log() = mutex.withLock {
        checkOpen()
        api.getLog(instance).checked()
    }

    private suspend fun mutate(send: suspend (String) -> OperationResult): RemoteOperationPayload =
        mutex.withLock {
            // Waiting edits remain cancellable. An accepted write belongs to the session and must
            // finish before another write or refresh can use its snapshot, even if its editor
            // closes.
            val operation = scope.async {
                try {
                    checkOpen()
                    checkRemote(
                        !needsRefresh,
                        "REFRESH_REQUIRED",
                        "Refresh required after interrupted connection",
                    )
                    mutableBusy.value = true
                    mutableError.value = null
                    val id = Uuid.randomString()
                    var result =
                        try {
                            send(id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (e is RemoteSettingsException) throw e
                            // A failed response does not prove a failed write. Only query the
                            // original operation ID.
                            try {
                                api.getOperation(id, instance).checked()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                throw RemoteSettingsException(
                                    "RESULT_UNKNOWN",
                                    "Operation result is unknown",
                                )
                            }
                        }
                    var attempts = 0
                    while (result.status == "pending" && attempts++ < 120) {
                        delay(1_000)
                        result = api.getOperation(id, instance).checked()
                    }
                    if (result.status != "succeeded") {
                        throw RemoteSettingsException(
                            result.error?.code ?: "RESULT_UNKNOWN",
                            result.error?.message ?: "Operation has not completed",
                        )
                    }
                    mutableSnapshot.value = api.getState(instance).checked()
                    checkNotNull(result.result) { "Missing operation result" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val failure = RemoteSettingsFailure.from(e)
                    // A rejection answered by the TV leaves nothing uncertain, so its current
                    // revisions are reloaded for the next edit. Any other failure blocks writes
                    // until a refresh succeeds.
                    needsRefresh =
                        e !is RemoteSettingsException ||
                            failure in UnconfirmedFailures ||
                            !reloadSnapshot()
                    mutableError.value = failure
                    throw e
                } finally {
                    mutableBusy.value = false
                }
            }
            withContext(NonCancellable) { operation.await() }
        }

    private suspend fun reloadSnapshot(): Boolean =
        try {
            mutableSnapshot.value = api.getState(instance).checked()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }

    private fun checkOpen() = checkRemote(!closed, "SESSION_CLOSED", "Session closed")

    override fun close() {
        closed = true
        scope.cancel()
        client.close()
    }

    companion object {
        private val UnconfirmedFailures =
            setOf(
                RemoteSettingsFailure.RESULT_UNKNOWN,
                RemoteSettingsFailure.REFRESH_REQUIRED,
                RemoteSettingsFailure.SESSION_CLOSED,
            )

        suspend fun connect(
            link: RemoteSettingsLink,
            appVersion: String,
            scope: CoroutineScope,
            client: HttpClient = createRemoteSettingsHttpClient(),
        ): RemoteSettingsSession {
            try {
                checkRemote(
                    link.protocolVersion == RemoteSettingsProtocol.VERSION,
                    "VERSION_MISMATCH",
                    "Incompatible QR protocol version",
                )
                val api =
                    RemoteSettingsApi(link.baseUrl, client).apply { setBearerToken(link.accessKey) }
                val device =
                    api.ping(
                            PingRequest(
                                appVersion,
                                RemoteSettingsProtocol.VERSION,
                                RemoteSettingsProtocol.SCHEMA_VERSION,
                            )
                        )
                        .checked()
                checkRemote(
                    device.protocolVersion == RemoteSettingsProtocol.VERSION &&
                        device.schemaVersion == RemoteSettingsProtocol.SCHEMA_VERSION,
                    "VERSION_MISMATCH",
                    "Incompatible protocol or schema version",
                )
                val snapshot = api.getState(device.serverInstanceId).checked()
                return RemoteSettingsSession(device, api, client, snapshot, scope)
            } catch (e: Exception) {
                client.close()
                throw e
            }
        }
    }
}

private suspend fun <T : Any> HttpResponse<T>.checked(): T {
    if (success) return body()
    val error =
        try {
            response.body<RemoteError>()
        } catch (_: Exception) {
            null
        }
    throw RemoteSettingsException(
        error?.code ?: "HTTP_ERROR",
        error?.message ?: "HTTP error ($status)",
    )
}

expect fun createRemoteSettingsHttpClient(): HttpClient
