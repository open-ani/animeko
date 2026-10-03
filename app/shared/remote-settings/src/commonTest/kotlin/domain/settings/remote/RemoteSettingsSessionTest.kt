/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings.remote

import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.encodeToString
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.remote.settings.RemoteSettingsLink
import me.him188.ani.remote.settings.RemoteSettingsProtocol
import me.him188.ani.remote.settings.generated.models.PingResponse
import me.him188.ani.remote.settings.generated.models.RemoteError

class RemoteSettingsSessionTest {
    @Test
    fun serializesEditsAndFinishesSubmittedWritesWhenTheCallerLeaves() = runTest {
        val fixture = Fixture().apply { writeGate = CompletableDeferred() }
        val session = fixture.connect(backgroundScope)
        try {
            val first = launch {
                session.preferences.videoScaffoldConfig.update { copy(autoPlayNext = false) }
            }
            fixture.writeStarted.await()
            val second = launch {
                session.preferences.watchTogetherSettings.update { copy(followHost = false) }
            }
            val cancelled = launch {
                session.preferences.videoScaffoldConfig.update { copy(autoMarkDone = false) }
            }
            runCurrent()
            assertEquals(1, fixture.writes)
            // The newest edit is visible before the TV confirms it.
            val submitted = session.preferences.videoScaffoldConfig.flow.first()
            assertFalse(submitted.autoPlayNext)
            assertFalse(submitted.autoMarkDone)
            cancelled.cancel()
            first.cancel()
            fixture.writeGate!!.complete(Unit)
            first.join()
            second.join()
            cancelled.join()
            // The edit whose caller left is still written, after the ones submitted before it.
            val confirmed =
                session.snapshot
                    .first { !it.preferences.videoScaffoldConfig.value.autoMarkDone }
                    .preferences
                    .videoScaffoldConfig
                    .value
            assertFalse(confirmed.autoPlayNext)
            assertEquals(3, fixture.writes)
            assertFalse(session.preferences.watchTogetherSettings.flow.first().followHost)
            session.busy.first { !it }
            assertNull(session.error.value)
        } finally {
            session.close()
        }
    }

    @Test
    fun lostWriteResponseQueriesTheOriginalOperationWithoutResubmitting() = runTest {
        val fixture = Fixture().apply { loseResponse = true }
        val session = fixture.connect(backgroundScope)
        try {
            session.preferences.videoScaffoldConfig.update { copy(autoPlayNext = false) }
            assertEquals(1, fixture.writes)
            assertEquals(1, fixture.queries)
            assertFalse(session.snapshot.value.preferences.videoScaffoldConfig.value.autoPlayNext)
        } finally {
            session.close()
        }
    }

    @Test
    fun pendingWriteIsPolledWithoutResubmitting() = runTest {
        val fixture = Fixture().apply { pending = true }
        val session = fixture.connect(backgroundScope)
        try {
            session.preferences.videoScaffoldConfig.update { copy(autoPlayNext = false) }
            assertEquals(1, fixture.writes)
            assertEquals(1, fixture.queries)
            assertFalse(session.busy.value)
        } finally {
            session.close()
        }
    }

    @Test
    fun failedSnapshotAfterAcknowledgementBlocksWritesUntilRefreshed() = runTest {
        val fixture = Fixture()
        val session = fixture.connect(backgroundScope)
        try {
            fixture.failSnapshot = true
            assertFailsWith<IOException> {
                session.preferences.videoScaffoldConfig.update { copy(autoPlayNext = false) }
            }
            assertNotNull(session.error.value)
            assertFalse(session.busy.value)
            assertEquals(
                "REFRESH_REQUIRED",
                assertFailsWith<RemoteSettingsException> {
                        session.preferences.videoScaffoldConfig.update {
                            copy(autoMarkDone = false)
                        }
                    }
                    .code,
            )
            assertEquals(1, fixture.writes)
            fixture.failSnapshot = false
            session.refresh()
            session.preferences.videoScaffoldConfig.update { copy(autoMarkDone = false) }
            assertEquals(2, fixture.writes)
        } finally {
            session.close()
        }
    }

    @Test
    fun protocolFailureDoesNotQueryOrRetryAWrite() = runTest {
        val fixture = Fixture().apply { rejectWrite = true }
        val session = fixture.connect(backgroundScope)
        try {
            assertEquals(
                "SERVER_RESTARTED",
                assertFailsWith<RemoteSettingsException> {
                        session.preferences.videoScaffoldConfig.update {
                            copy(autoPlayNext = false)
                        }
                    }
                    .code,
            )
            assertEquals(1, fixture.writes)
            assertEquals(0, fixture.queries)
            assertEquals(RemoteSettingsFailure.SERVER_RESTARTED, session.error.value)
        } finally {
            session.close()
        }
    }

    @Test
    fun editBuiltFromTheValueBeforeAnEarlierWriteIsRejected() = runTest {
        val fixture = Fixture().apply { writeGate = CompletableDeferred() }
        val session = fixture.connect(backgroundScope)
        try {
            val base = session.snapshot.value.preferences.videoScaffoldConfig.value
            val first = launch {
                session
                    .submitPreference(
                        RemotePreference.VideoScaffold(base.copy(autoPlayNext = false)),
                        RemotePreference.VideoScaffold(base),
                    )
                    .await()
            }
            fixture.writeStarted.await()
            val second = async {
                runCatching {
                    session
                        .submitPreference(
                            RemotePreference.VideoScaffold(base.copy(autoMarkDone = false)),
                            RemotePreference.VideoScaffold(base),
                        )
                        .await()
                }
            }
            runCurrent()
            fixture.writeGate!!.complete(Unit)
            first.join()
            assertEquals(
                "REVISION_CONFLICT",
                (second.await().exceptionOrNull() as RemoteSettingsException).code,
            )
            val config = session.preferences.videoScaffoldConfig.flow.first()
            assertFalse(config.autoPlayNext)
            assertEquals(base.autoMarkDone, config.autoMarkDone)
            // A rejected edit can be repeated on the reloaded value without waiting for a refresh.
            session.preferences.videoScaffoldConfig.update { copy(autoMarkDone = false) }
            assertFalse(session.preferences.videoScaffoldConfig.flow.first().autoMarkDone)
        } finally {
            session.close()
        }
    }

    @Test
    fun consecutiveFilterEditsApplyOnTopOfEachOther() = runTest {
        val fixture = Fixture().apply { writeGate = CompletableDeferred() }
        val session = fixture.connect(backgroundScope)
        try {
            val first = launch {
                session.editDanmakuFilters { it + DanmakuRegexFilter("a", regex = "a") }
            }
            fixture.writeStarted.await()
            val second = launch {
                session.editDanmakuFilters { it + DanmakuRegexFilter("b", regex = "b") }
            }
            runCurrent()
            fixture.writeGate!!.complete(Unit)
            first.join()
            second.join()
            assertEquals(listOf("filters-0", "filters-1"), fixture.filterRevisions)
            assertEquals(
                listOf("a", "b"),
                session.snapshot.value.danmakuFilters.value.map { it.id },
            )
        } finally {
            session.close()
        }
    }

    private class Fixture {
        var writes = 0
        var queries = 0
        var loseResponse = false
        var pending = false
        var failSnapshot = false
        var rejectWrite = false
        var writeGate: CompletableDeferred<Unit>? = null
        val writeStarted = CompletableDeferred<Unit>()
        val filterRevisions = mutableListOf<String>()
        private var filters = VersionedValue("filters-0", emptyList<DanmakuRegexFilter>())
        private val json = RemoteSettingsProtocol.json
        private val registry =
            RemotePreferenceRegistry(
                PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences())),
                RemoteSettingsRevision { resource, value -> "$resource:$value" },
            )
        private val key = "820f62ee-3a31-4491-b4b7-1e91f6bb12dd"
        private var result: OperationResult? = null

        suspend fun connect(scope: CoroutineScope): RemoteSettingsSession {
            val client =
                HttpClient(
                    MockEngine { request ->
                        assertEquals("Bearer $key", request.headers[HttpHeaders.Authorization])
                        val path = request.url.encodedPath
                        if (path != "/ping")
                            assertEquals("process", request.headers["X-Ani-Server-Instance"])
                        val body =
                            when {
                                path == "/ping" ->
                                    json.encodeToString(
                                        PingResponse("process", "4.9", "电视", 1, 1, emptyList())
                                    )
                                path == "/state" -> {
                                    if (failSnapshot) throw IOException("Connection lost")
                                    json.encodeToString(
                                        SettingsSnapshot(
                                            registry.snapshot(),
                                            VersionedValue("empty", emptyList()),
                                            VersionedValue("empty", emptyList()),
                                            filters,
                                            emptyList(),
                                        )
                                    )
                                }
                                path == "/preference" -> {
                                    writes++
                                    writeStarted.complete(Unit)
                                    writeGate?.await()
                                    if (rejectWrite)
                                        return@MockEngine respond(
                                            json.encodeToString(
                                                RemoteError("SERVER_RESTARTED", "请重新扫码")
                                            ),
                                            HttpStatusCode.Conflict,
                                            headersOf(HttpHeaders.ContentType, "application/json"),
                                        )
                                    val command =
                                        json.decodeFromString<PreferenceRequest>(
                                            (request.body as TextContent).text
                                        )
                                    try {
                                        registry.write(command.baseRevision, command.value)
                                    } catch (e: RemoteSettingsException) {
                                        return@MockEngine respond(
                                            json.encodeToString(
                                                RemoteError(e.code, e.message.orEmpty())
                                            ),
                                            HttpStatusCode.Conflict,
                                            headersOf(HttpHeaders.ContentType, "application/json"),
                                        )
                                    }
                                    result =
                                        OperationResult(
                                            command.operationId,
                                            "succeeded",
                                            RemoteOperationPayload.Applied,
                                        )
                                    if (loseResponse)
                                        throw IOException("Response lost after commit")
                                    json.encodeToString(
                                        if (pending) OperationResult(command.operationId, "pending")
                                        else result!!
                                    )
                                }
                                path == "/danmaku-filter" -> {
                                    writeStarted.complete(Unit)
                                    writeGate?.await()
                                    val command =
                                        json.decodeFromString<DanmakuFilterRequest>(
                                            (request.body as TextContent).text
                                        )
                                    val revision = requireNotNull(command.baseRevision)
                                    filterRevisions += revision
                                    assertEquals(filters.revision, revision)
                                    filters =
                                        VersionedValue(
                                            "filters-${filterRevisions.size}",
                                            command.command.filters,
                                        )
                                    json.encodeToString(
                                        OperationResult(
                                            command.operationId,
                                            "succeeded",
                                            RemoteOperationPayload.Applied,
                                        )
                                    )
                                }
                                path.startsWith("/operations/") -> {
                                    queries++
                                    assertEquals(result!!.operationId, path.substringAfterLast('/'))
                                    json.encodeToString(result!!)
                                }
                                else -> error("Unexpected request")
                            }
                        respond(
                            body,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                ) {
                    install(ContentNegotiation) { json(json) }
                }
            return RemoteSettingsSession.connect(
                RemoteSettingsLink("192.168.1.2", 12345, key, "4.9", ""),
                "4.9",
                scope,
                client,
            )
        }
    }
}
