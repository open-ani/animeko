/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.remotesettings

import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.app.domain.settings.remote.BackupRequest
import me.him188.ani.app.domain.settings.remote.DanmakuFilterRequest
import me.him188.ani.app.domain.settings.remote.MediaSourceCommand
import me.him188.ani.app.domain.settings.remote.MediaSourceRequest
import me.him188.ani.app.domain.settings.remote.PreferenceRequest
import me.him188.ani.app.domain.settings.remote.RemoteBackupCommand
import me.him188.ani.app.domain.settings.remote.RemoteBackupPreview
import me.him188.ani.app.domain.settings.remote.RemoteBackupResult
import me.him188.ani.app.domain.settings.remote.RemoteOperationPayload
import me.him188.ani.app.domain.settings.remote.RemotePreference
import me.him188.ani.app.domain.settings.remote.RemotePreferenceRegistry
import me.him188.ani.app.domain.settings.remote.RemoteSettingsBackup
import me.him188.ani.app.domain.settings.remote.RemoteSettingsException
import me.him188.ani.app.domain.settings.remote.ReplaceDanmakuFilters
import me.him188.ani.app.domain.settings.remote.SettingsSnapshot
import me.him188.ani.app.domain.settings.remote.VersionedValue
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.remote.settings.RemoteSettingsProtocol
import me.him188.ani.remote.settings.generated.apis.RemoteSettingsApi
import me.him188.ani.remote.settings.generated.models.LogSnapshot
import me.him188.ani.remote.settings.generated.models.PingResponse

class RemoteSettingsServerTest {
    private val key = UUID.randomUUID().toString()
    private val preferences =
        RemotePreferenceRegistry(
            PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences())),
            HmacSettingsRevision(),
        )
    private val receivedSources = mutableListOf<MediaSourceCommand>()
    private val receivedFilters = mutableListOf<ReplaceDanmakuFilters>()
    private val backend =
        object : RemoteSettingsBackend {
            override suspend fun snapshot() =
                SettingsSnapshot(
                    preferences.snapshot(),
                    VersionedValue("1", emptyList()),
                    VersionedValue(
                        "1",
                        listOf(
                            MediaSourceSubscription(
                                "sub",
                                "https://example.com/sub.json",
                                lastUpdated =
                                    MediaSourceSubscription.LastUpdated(
                                        123,
                                        null,
                                        MediaSourceSubscription.UpdateError(
                                            "unavailable",
                                            ApiFailure.NetworkError,
                                        ),
                                    ),
                            )
                        ),
                    ),
                    VersionedValue("1", emptyList()),
                    emptyList(),
                )

            override suspend fun preference(
                request: PreferenceRequest,
                sent: JsonObject?,
            ): RemoteOperationPayload {
                preferences.write(request.baseRevision, request.value, sent)
                return RemoteOperationPayload.Applied
            }

            override suspend fun mediaSource(request: MediaSourceRequest): RemoteOperationPayload {
                receivedSources += request.command
                return if (request.command is MediaSourceCommand.Export)
                    RemoteOperationPayload.SourceExport("source export")
                else RemoteOperationPayload.Applied
            }

            override suspend fun danmakuFilter(
                request: DanmakuFilterRequest
            ): RemoteOperationPayload {
                receivedFilters += request.command
                return RemoteOperationPayload.Applied
            }

            override suspend fun backup(request: BackupRequest): RemoteOperationPayload =
                when (val command = request.command) {
                    RemoteBackupCommand.Export ->
                        RemoteOperationPayload.BackupExport(
                            RemoteSettingsBackup(
                                preferences = preferences.snapshot().values(),
                                mediaSources = emptyList(),
                                subscriptions = emptyList(),
                                danmakuFilters = emptyList(),
                            )
                        )
                    is RemoteBackupCommand.Preview ->
                        RemoteOperationPayload.BackupPreview(
                            RemoteBackupPreview("plan", command.backup.preferences.size, 0, 0, 0)
                        )
                    is RemoteBackupCommand.Apply ->
                        RemoteOperationPayload.BackupApplied(
                            RemoteBackupResult(listOf("preferences"), emptyMap())
                        )
                }

            override suspend fun log() = LogSnapshot("tv-app.log", "TV log", false)
        }

    @Test
    fun everyEndpointAuthenticatesBeforeReadingBodies() = testApplication {
        application {
            remoteSettingsRoutes(backend, key, "process", RemoteOperationLedger(this)) {
                PingResponse("process", "6.1", "TV", 1, 1, emptyList())
            }
        }
        for (path in listOf("ping", "preference", "media-source", "danmaku-filter", "backup")) {
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.post("/$path") { setBody("invalid") }.status,
            )
        }
        for (path in listOf("state", "log", "operations/id")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/$path").status)
        }
        assertEquals(HttpStatusCode.OK, client.get("/state") { bearerAuth(key) }.status)
        assertEquals(
            HttpStatusCode.Forbidden,
            client
                .get("/log") {
                    bearerAuth(key)
                    header("Origin", "https://example.com")
                }
                .status,
        )
        assertEquals(
            HttpStatusCode.Conflict,
            client
                .get("/state") {
                    bearerAuth(key)
                    header("X-Ani-Server-Instance", "previous-process")
                }
                .status,
        )
    }

    @Test
    fun incompatibleHandshakeIsRejected() = testApplication {
        application {
            remoteSettingsRoutes(backend, key, "process", RemoteOperationLedger(this)) {
                PingResponse("process", "6.1", "TV", 1, 1, emptyList())
            }
        }
        val response =
            client.post("/ping") {
                bearerAuth(key)
                contentType(ContentType.Application.Json)
                setBody("""{"appVersion":"6.1","protocolVersion":2,"schemaVersion":1}""")
            }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun repeatedOperationIsExecutedOnceAndCannotChangePayload() = runTest {
        val ledger = RemoteOperationLedger(backgroundScope)
        val id = UUID.randomUUID().toString()
        var count = 0
        val released = CompletableDeferred<Unit>()
        val first =
            ledger.submit(id, "same") {
                released.await()
                count++
                RemoteOperationPayload.Applied
            }
        val retry =
            ledger.submit(id, "same") {
                count++
                RemoteOperationPayload.Applied
            }
        assertFailsWith<RemoteSettingsException> {
            ledger.submit(id, "different") { RemoteOperationPayload.Applied }
        }
        released.complete(Unit)
        assertEquals("succeeded", first.await().status)
        assertEquals(first.await(), retry.await())
        assertEquals(1, count)
    }

    @Test
    fun malformedAndOversizedBodiesAreRejected() = testApplication {
        application {
            remoteSettingsRoutes(backend, key, "process", RemoteOperationLedger(this)) {
                PingResponse("process", "6.1", "TV", 1, 1, emptyList())
            }
        }
        for ((body, status) in
            listOf(
                "invalid" to HttpStatusCode.BadRequest,
                " ".repeat(RemoteSettingsProtocol.MAX_REQUEST_BYTES + 1) to
                    HttpStatusCode.PayloadTooLarge,
            )) {
            assertEquals(
                status,
                client
                    .post("/preference") {
                        bearerAuth(key)
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                    .status,
            )
        }
    }

    @Test
    fun generatedClientCarriesTypedCommandsSnapshotsAndPolledResults() = testApplication {
        application {
            remoteSettingsRoutes(backend, key, "process", RemoteOperationLedger(this)) {
                PingResponse("process", "6.1", "TV", 1, 1, emptyList())
            }
        }
        val http = createClient {
            install(ContentNegotiation) { json(RemoteSettingsProtocol.json) }
        }
        val api = RemoteSettingsApi("http://localhost", http).apply { setBearerToken(key) }
        val state = api.getState("process").body()
        assertEquals(
            ApiFailure.NetworkError,
            state.subscriptions.value.single().lastUpdated?.error?.failure,
        )
        val preference = state.preferences.videoScaffoldConfig
        val changed = preference.value.copy(autoPlayNext = !preference.value.autoPlayNext)
        assertEquals(
            RemoteOperationPayload.Applied,
            api.setPreference(
                    PreferenceRequest(
                        UUID.randomUUID().toString(),
                        preference.revision,
                        RemotePreference.VideoScaffold(changed),
                    ),
                    "process",
                )
                .body()
                .result,
        )
        assertEquals(changed, api.getState("process").body().preferences.videoScaffoldConfig.value)

        val source =
            MediaSourceSave(
                "source",
                "source",
                FactoryId("web-selector"),
                true,
                MediaSourceConfig.Default,
            )
        val subscription =
            MediaSourceSubscription(subscriptionId = "sub", url = "https://example.com/sub.json")
        val commands: List<MediaSourceCommand> =
            listOf(
                MediaSourceCommand.Add(source),
                MediaSourceCommand.Edit(source.instanceId, source.config),
                MediaSourceCommand.Delete(listOf("source")),
                MediaSourceCommand.Reorder(listOf("source")),
                MediaSourceCommand.Enable(listOf("source"), false),
                MediaSourceCommand.Import("source text"),
                MediaSourceCommand.Export(listOf("source")),
                MediaSourceCommand.SubscriptionAdd(subscription),
                MediaSourceCommand.SubscriptionEdit(subscription),
                MediaSourceCommand.SubscriptionDelete("sub"),
                MediaSourceCommand.SubscriptionRefresh("sub"),
            )
        for (command in commands) {
            val id = UUID.randomUUID().toString()
            val result = api.mediaSource(MediaSourceRequest(id, command, "1"), "process").body()
            assertEquals("succeeded", result.status)
            assertEquals(result, api.getOperation(id, "process").body())
        }
        assertEquals(commands, receivedSources)
        val filters =
            ReplaceDanmakuFilters(
                listOf(
                    DanmakuRegexFilter(id = "filter", name = "test", regex = "spam", enabled = true)
                )
            )
        assertEquals(
            RemoteOperationPayload.Applied,
            api.danmakuFilter(
                    DanmakuFilterRequest(UUID.randomUUID().toString(), filters, "1"),
                    "process",
                )
                .body()
                .result,
        )
        assertEquals(listOf(filters), receivedFilters)

        suspend fun backup(command: RemoteBackupCommand) =
            api.backup(BackupRequest(UUID.randomUUID().toString(), command), "process").body()
        val exported =
            assertIs<RemoteOperationPayload.BackupExport>(backup(RemoteBackupCommand.Export).result)
                .value
        val preview =
            assertIs<RemoteOperationPayload.BackupPreview>(
                    backup(RemoteBackupCommand.Preview(exported)).result
                )
                .value
        assertEquals(8, preview.preferenceCount)
        val applied = backup(RemoteBackupCommand.Apply(preview.planId))
        assertIs<RemoteOperationPayload.BackupApplied>(applied.result)
        assertEquals(applied, api.getOperation(applied.operationId, "process").body())
        assertEquals("TV log", api.getLog("process").body().content)
    }

    @Test
    fun unknownCommandDiscriminatorIsRejectedBeforeInvokingBackend() = testApplication {
        application {
            remoteSettingsRoutes(backend, key, "process", RemoteOperationLedger(this)) {
                PingResponse("process", "6.1", "TV", 1, 1, emptyList())
            }
        }
        val response =
            client.post("/media-source") {
                bearerAuth(key)
                contentType(ContentType.Application.Json)
                setBody("""{"operationId":"${UUID.randomUUID()}","command":{"type":"unknown"}}""")
            }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(emptyList(), receivedSources)
    }

    @Test
    fun revisionSecretsAreProcessScoped() {
        val first = HmacSettingsRevision()
        assertEquals(first.of("player", "1"), first.of("player", "1"))
        assertNotEquals(
            first.of("player", "1"),
            HmacSettingsRevision().of("player", "1"),
        )
    }
}
