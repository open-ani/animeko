/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.remotesettings

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.him188.ani.app.domain.settings.remote.BackupRequest
import me.him188.ani.app.domain.settings.remote.DanmakuFilterRequest
import me.him188.ani.app.domain.settings.remote.MediaSourceRequest
import me.him188.ani.app.domain.settings.remote.OperationResult
import me.him188.ani.app.domain.settings.remote.PreferenceRequest
import me.him188.ani.app.domain.settings.remote.RemoteCommandRequest
import me.him188.ani.app.domain.settings.remote.RemoteOperationPayload
import me.him188.ani.app.domain.settings.remote.RemoteSettingsException
import me.him188.ani.app.domain.settings.remote.RemoteSettingsRevision
import me.him188.ani.remote.settings.RemoteSettingsProtocol
import me.him188.ani.remote.settings.generated.models.PingRequest
import me.him188.ani.remote.settings.generated.models.PingResponse
import me.him188.ani.remote.settings.generated.models.RemoteError

class RemoteSettingsServer(
    private val scope: CoroutineScope,
    private val backend: RemoteSettingsBackend,
    private val appVersion: String,
    private val deviceName: String,
    private val userUuid: suspend () -> String?,
) : AutoCloseable {
    val accessKey: String = UUID.randomUUID().toString()
    val instanceId: String = UUID.randomUUID().toString()
    private val operations = RemoteOperationLedger(scope)
    private val engine =
        embeddedServer(CIO, port = 0, host = "0.0.0.0") {
            remoteSettingsRoutes(backend, accessKey, instanceId, operations) {
                PingResponse(
                    instanceId,
                    appVersion,
                    deviceName,
                    RemoteSettingsProtocol.VERSION,
                    RemoteSettingsProtocol.SCHEMA_VERSION,
                    listOf(
                        "preferences",
                        "mediaSources",
                        "subscriptions",
                        "danmakuFilters",
                        "logs",
                        "backup",
                    ),
                    userUuid(),
                )
            }
        }

    suspend fun start(): Int {
        engine.start(wait = false)
        return engine.engine.resolvedConnectors().single().port
    }

    override fun close() {
        engine.stop(250, 1_000)
    }
}

/**
 * Canonical, process-scoped revision tokens reveal neither configuration contents nor credentials.
 */
class HmacSettingsRevision : RemoteSettingsRevision {
    private val key =
        SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "HmacSHA256")

    override fun of(resource: String, encodedValue: String): String {
        val value = RemoteSettingsProtocol.json.parseToJsonElement(encodedValue)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal((resource + "\n" + canonical(value)).toByteArray(Charsets.UTF_8)).toHexString()
    }
}

private fun canonical(value: JsonElement): JsonElement =
    when (value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(value.map(::canonical))
        else -> value
    }

/** How long a request waits for its operation before answering that it is still pending. */
private const val OPERATION_REPLY_TIMEOUT_MILLIS = 1_000L

private suspend fun Deferred<OperationResult>.awaitBriefly(): OperationResult? =
    withTimeoutOrNull(OPERATION_REPLY_TIMEOUT_MILLIS) { await() }

private fun Throwable.toRemoteError(invalidMessage: String, fallback: RemoteError): RemoteError =
    when (this) {
        is RemoteSettingsException -> RemoteError(code, message)
        // Includes SerializationException.
        is IllegalArgumentException -> RemoteError("INVALID_ARGUMENT", invalidMessage)
        else -> fallback
    }

/** Accepted operations belong to the application scope, independent of the requesting socket. */
class RemoteOperationLedger(private val scope: CoroutineScope, private val capacity: Int = 1024) {
    private class Entry(val fingerprint: String, val task: Deferred<OperationResult>)

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun submit(
        id: String,
        fingerprint: String,
        action: suspend () -> RemoteOperationPayload,
    ): Deferred<OperationResult> {
        if (!runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false)) {
            throw RemoteSettingsException("INVALID_OPERATION_ID", "Invalid operation ID")
        }
        entries[id]?.let {
            if (it.fingerprint != fingerprint)
                throw RemoteSettingsException(
                    "OPERATION_ID_REUSED",
                    "Operation ID already used for another request",
                )
            return it.task
        }
        if (entries.size >= capacity)
            throw RemoteSettingsException("BUSY", "Operation capacity reached")
        val task = scope.async {
            try {
                OperationResult(id, "succeeded", withTimeout(OPERATION_TIMEOUT_MILLIS) { action() })
            } catch (_: CancellationException) {
                failed(id, RemoteError("INTERRUPTED", "Operation interrupted"))
            } catch (e: Exception) {
                failed(
                    id,
                    e.toRemoteError(
                        invalidMessage = "Invalid settings",
                        fallback = RemoteError("APPLY_FAILED", "Operation failed"),
                    ),
                )
            }
        }
        entries[id] = Entry(fingerprint, task)
        scope.launch {
            task.join()
            delay(RESULT_TTL_MILLIS)
            forget(id)
        }
        return task
    }

    @Synchronized
    private fun forget(id: String) {
        entries.remove(id)
    }

    @Synchronized fun find(id: String): Deferred<OperationResult>? = entries[id]?.task

    private fun failed(id: String, error: RemoteError) = OperationResult(id, "failed", error = error)

    private companion object {
        const val OPERATION_TIMEOUT_MILLIS = 120_000L
        const val RESULT_TTL_MILLIS = 600_000L
    }
}

internal fun Application.remoteSettingsRoutes(
    backend: RemoteSettingsBackend,
    accessKey: String,
    instanceId: String,
    operations: RemoteOperationLedger,
    ping: suspend () -> PingResponse,
) {
    routing {
        route("/") {
            intercept(ApplicationCallPipeline.Call) {
                val instance = call.request.header("X-Ani-Server-Instance")
                val rejection =
                    when {
                        !call.hasAccessKey(accessKey) ->
                            RemoteError("UNAUTHORIZED", "Invalid access key") to
                                HttpStatusCode.Unauthorized
                        call.request.header(HttpHeaders.Origin) != null ->
                            RemoteError("FORBIDDEN", "Browser requests are not supported") to
                                HttpStatusCode.Forbidden
                        instance != null && instance != instanceId ->
                            RemoteError("SERVER_RESTARTED", "Server instance changed") to
                                HttpStatusCode.Conflict
                        else -> null
                    }
                if (rejection != null) {
                    call.reply(rejection.first, rejection.second)
                    finish()
                }
            }
            post("ping") {
                call.handle {
                    val request = body<PingRequest>()
                    if (
                        request.protocolVersion != RemoteSettingsProtocol.VERSION ||
                            request.schemaVersion != RemoteSettingsProtocol.SCHEMA_VERSION
                    ) {
                        reply(
                            RemoteError("VERSION_MISMATCH", "Incompatible versions"),
                            HttpStatusCode.Conflict,
                        )
                    } else reply(ping())
                }
            }
            get("state") { call.handle { reply(backend.snapshot()) } }
            get("log") { call.handle { reply(backend.log()) } }
            commandRoute<PreferenceRequest>("preference", operations, backend::preference)
            commandRoute<MediaSourceRequest>("media-source", operations, backend::mediaSource)
            commandRoute<DanmakuFilterRequest>(
                "danmaku-filter",
                operations,
                backend::danmakuFilter,
            )
            commandRoute<BackupRequest>("backup", operations, backend::backup)
            get("operations/{operationId}") {
                call.handle {
                    val id = parameters["operationId"].orEmpty()
                    val task = operations.find(id)
                    if (task == null)
                        reply(
                            RemoteError("OPERATION_UNKNOWN", "Operation result unknown"),
                            HttpStatusCode.NotFound,
                        )
                    else
                        reply(task.awaitBriefly() ?: OperationResult(id, "pending"))
                }
            }
        }
    }
}

private inline fun <reified T : RemoteCommandRequest> Route.commandRoute(
    path: String,
    operations: RemoteOperationLedger,
    crossinline handler: suspend (T) -> RemoteOperationPayload,
) {
    post(path) {
        call.handle {
            val request = body<T>()
            operation(
                operations,
                request.operationId,
                path + ":" + fingerprint(RemoteSettingsProtocol.json.encodeToString(request)),
            ) {
                handler(request)
            }
        }
    }
}

/** Compares in constant time. */
private fun ApplicationCall.hasAccessKey(accessKey: String): Boolean {
    val authorization = request.header(HttpHeaders.Authorization).orEmpty()
    return authorization.startsWith("Bearer ") &&
        MessageDigest.isEqual(
            authorization.removePrefix("Bearer ").toByteArray(Charsets.UTF_8),
            accessKey.toByteArray(Charsets.UTF_8),
        )
}

private fun fingerprint(value: String) =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).toHexString()

private suspend fun ApplicationCall.operation(
    ledger: RemoteOperationLedger,
    id: String,
    fingerprint: String,
    action: suspend () -> RemoteOperationPayload,
) {
    val task = ledger.submit(id, fingerprint, action)
    val result = task.awaitBriefly()
    reply(
        result ?: OperationResult(id, "pending"),
        if (result == null) HttpStatusCode.Accepted else HttpStatusCode.OK,
    )
}

private suspend inline fun <reified T> ApplicationCall.reply(
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    respondText(
        RemoteSettingsProtocol.json.encodeToString(value),
        ContentType.Application.Json,
        status,
    )
}

private suspend inline fun <reified T> ApplicationCall.body(): T {
    val contentType = request.header(HttpHeaders.ContentType).orEmpty().substringBefore(';')
    if (contentType != "application/json")
        throw RemoteSettingsException("INVALID_CONTENT_TYPE", "Request must use JSON")
    val bytes =
        withTimeout(10_000) {
            receiveChannel()
                .readRemaining(RemoteSettingsProtocol.MAX_REQUEST_BYTES.toLong() + 1)
                .readByteArray()
        }
    if (bytes.size > RemoteSettingsProtocol.MAX_REQUEST_BYTES)
        throw RemoteSettingsException("PAYLOAD_TOO_LARGE", "Payload too large")
    return RemoteSettingsProtocol.json.decodeFromString(
        bytes.decodeToString(throwOnInvalidSequence = true)
    )
}

private suspend fun ApplicationCall.handle(action: suspend ApplicationCall.() -> Unit) {
    try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val error =
            e.toRemoteError(
                invalidMessage = "Invalid request format",
                fallback = RemoteError("SERVER_ERROR", "Internal server error"),
            )
        val status =
            when (error.code) {
                "BUSY" -> HttpStatusCode.TooManyRequests
                "PAYLOAD_TOO_LARGE" -> HttpStatusCode.PayloadTooLarge
                "OPERATION_ID_REUSED" -> HttpStatusCode.Conflict
                "SERVER_ERROR" -> HttpStatusCode.InternalServerError
                else -> HttpStatusCode.BadRequest
            }
        reply(error, status)
    }
}
