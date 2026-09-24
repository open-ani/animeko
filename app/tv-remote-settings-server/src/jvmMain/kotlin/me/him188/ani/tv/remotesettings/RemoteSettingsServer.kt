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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException
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
import me.him188.ani.app.domain.settings.remote.RemoteSettingsBackend
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
    private val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }

    override fun of(resource: String, encodedValue: String): String {
        val value = RemoteSettingsProtocol.json.parseToJsonElement(encodedValue)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal((resource + "\n" + canonical(value)).toByteArray(Charsets.UTF_8)).hex()
    }
}

private fun canonical(value: JsonElement): JsonElement =
    when (value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(value.map(::canonical))
        else -> value
    }

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** Accepted operations belong to the application scope, independent of the requesting socket. */
class RemoteOperationLedger(private val scope: CoroutineScope, private val capacity: Int = 1024) {
    private data class Entry(
        val fingerprint: String,
        val task: Deferred<OperationResult>,
        val createdAt: Long,
    )

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
        val now = System.nanoTime()
        entries.entries.removeIf {
            it.value.task.isCompleted && now - it.value.createdAt > 600_000_000_000L
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
                val result = withTimeout(120_000) { action() }
                OperationResult(id, "succeeded", result)
            } catch (e: CancellationException) {
                OperationResult(
                    id,
                    "failed",
                    error = RemoteError("INTERRUPTED", "Operation interrupted"),
                )
            } catch (e: RemoteSettingsException) {
                OperationResult(id, "failed", error = RemoteError(e.code, e.message))
            } catch (_: IllegalArgumentException) {
                OperationResult(
                    id,
                    "failed",
                    error = RemoteError("INVALID_ARGUMENT", "Invalid settings"),
                )
            } catch (_: Exception) {
                OperationResult(
                    id,
                    "failed",
                    error = RemoteError("APPLY_FAILED", "Operation failed"),
                )
            }
        }
        entries[id] = Entry(fingerprint, task, now)
        return task
    }

    @Synchronized fun find(id: String): Deferred<OperationResult>? = entries[id]?.task
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
                val authorization = call.request.header(HttpHeaders.Authorization).orEmpty()
                val token = authorization.removePrefix("Bearer ")
                when {
                    !authorization.startsWith("Bearer ") ||
                        !MessageDigest.isEqual(
                            token.toByteArray(Charsets.UTF_8),
                            accessKey.toByteArray(Charsets.UTF_8),
                        ) -> {
                        call.reply(
                            RemoteError("UNAUTHORIZED", "Invalid access key"),
                            HttpStatusCode.Unauthorized,
                        )
                        finish()
                    }
                    call.request.header(HttpHeaders.Origin) != null -> {
                        call.reply(
                            RemoteError("FORBIDDEN", "Browser requests are not supported"),
                            HttpStatusCode.Forbidden,
                        )
                        finish()
                    }
                    call.request.header("X-Ani-Server-Instance")?.let { it != instanceId } ==
                        true -> {
                        call.reply(
                            RemoteError("SERVER_RESTARTED", "Server instance changed"),
                            HttpStatusCode.Conflict,
                        )
                        finish()
                    }
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
            post("preference") {
                call.handle {
                    val request = body<PreferenceRequest>()
                    operation(
                        operations,
                        request.operationId,
                        "preference:" +
                            fingerprint(RemoteSettingsProtocol.json.encodeToString(request)),
                    ) {
                        backend.preference(request)
                    }
                }
            }
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
                        reply(
                            withTimeoutOrNull(1_000) { task.await() }
                                ?: OperationResult(id, "pending")
                        )
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

private fun fingerprint(value: String) =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).hex()

private suspend fun ApplicationCall.operation(
    ledger: RemoteOperationLedger,
    id: String,
    fingerprint: String,
    action: suspend () -> RemoteOperationPayload,
) {
    val task = ledger.submit(id, fingerprint, action)
    val result = withTimeoutOrNull(1_000) { task.await() }
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
    } catch (e: RemoteSettingsException) {
        val status =
            when (e.code) {
                "BUSY" -> HttpStatusCode.TooManyRequests
                "PAYLOAD_TOO_LARGE" -> HttpStatusCode.PayloadTooLarge
                "OPERATION_ID_REUSED" -> HttpStatusCode.Conflict
                else -> HttpStatusCode.BadRequest
            }
        reply(RemoteError(e.code, e.message), status)
    } catch (_: SerializationException) {
        reply(RemoteError("INVALID_ARGUMENT", "Invalid request format"), HttpStatusCode.BadRequest)
    } catch (_: IllegalArgumentException) {
        reply(RemoteError("INVALID_ARGUMENT", "Invalid request format"), HttpStatusCode.BadRequest)
    } catch (_: Exception) {
        reply(
            RemoteError("SERVER_ERROR", "Internal server error"),
            HttpStatusCode.InternalServerError,
        )
    }
}
