/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.remote.settings.RemoteSettingsLink
import me.him188.ani.remote.settings.RemoteSettingsProtocol
import me.him188.ani.remote.settings.generated.models.PingResponse
import me.him188.ani.remote.settings.generated.models.RemoteError

class RemoteMediaSourceEditorTest {
    private fun encodedArguments(name: String) =
        RemoteSettingsProtocol.json.encodeToJsonElement(
            SelectorMediaSourceArguments.serializer(),
            SelectorMediaSourceArguments.Default.copy(name = name),
        )

    @Test
    fun coalescesTypingAndSavesTheLastEditAfterTheEditorCloses() = runTest {
        val fixture = Fixture()
        val session = fixture.connect(backgroundScope)
        try {
            val editor = RemoteMediaSourceEditor(session, "source", backgroundScope)
            editor.saveArguments(
                SelectorMediaSourceArguments.serializer(),
                SelectorMediaSourceArguments.Default.copy(name = "first"),
            )
            editor.saveArguments(
                SelectorMediaSourceArguments.serializer(),
                SelectorMediaSourceArguments.Default.copy(name = "latest"),
            )
            editor.close()
            advanceTimeBy(501)
            editor.config.first { it.serializedArguments == encodedArguments("latest") }
            editor.isSaving.first { !it }
            assertEquals(listOf("revision-0"), fixture.revisions.toList())
            assertEquals(encodedArguments("latest"), fixture.source.config.serializedArguments)
        } finally {
            session.close()
        }
    }

    @Test
    fun typingDuringAWriteKeepsItRunningAndUsesItsAcknowledgedRevision() = runTest {
        val fixture = Fixture().apply { gate = CompletableDeferred() }
        val session = fixture.connect(backgroundScope)
        try {
            val editor = RemoteMediaSourceEditor(session, "source", backgroundScope)
            editor.saveArguments(
                SelectorMediaSourceArguments.serializer(),
                SelectorMediaSourceArguments.Default.copy(name = "first"),
            )
            advanceTimeBy(501)
            fixture.started.await()
            editor.saveArguments(
                SelectorMediaSourceArguments.serializer(),
                SelectorMediaSourceArguments.Default.copy(name = "second"),
            )
            runCurrent()
            assertTrue(editor.isSaving.value)
            assertEquals(listOf("revision-0"), fixture.revisions.toList())
            fixture.gate!!.complete(Unit)
            editor.config.first { it.serializedArguments == encodedArguments("first") }
            advanceTimeBy(501)
            editor.config.first { it.serializedArguments == encodedArguments("second") }
            editor.isSaving.first { !it }
            assertEquals(listOf("revision-0", "revision-1"), fixture.revisions.toList())
            editor.close()
        } finally {
            session.close()
        }
    }

    @Test
    fun pollingDoesNotRebaseAnOpenEditorsDraftOntoExternalChanges() = runTest {
        val errors = mutableListOf<Throwable>()
        val scope =
            CoroutineScope(
                backgroundScope.coroutineContext +
                    CoroutineExceptionHandler { _, error -> errors += error }
            )
        val fixture = Fixture()
        val session = fixture.connect(scope)
        try {
            val editor = RemoteMediaSourceEditor(session, "source", scope)
            fixture.source =
                fixture.source.copy(
                    config =
                        fixture.source.config.copy(
                            serializedArguments = encodedArguments("external")
                        )
                )
            fixture.revision++
            session.refresh()
            editor.saveArguments(
                SelectorMediaSourceArguments.serializer(),
                SelectorMediaSourceArguments.Default.copy(name = "stale draft"),
            )
            advanceTimeBy(501)
            session.error.first { it != null }
            editor.isSaving.first { !it }
            assertEquals("REVISION_CONFLICT", (errors.single() as RemoteSettingsException).code)
            assertEquals(encodedArguments("external"), fixture.source.config.serializedArguments)
            assertFalse(session.busy.value)
            editor.close()
        } finally {
            session.close()
        }
    }

    @Test
    fun changesToOtherSourcesDoNotRejectTheDraft() = runTest {
        val fixture = Fixture()
        val session = fixture.connect(backgroundScope)
        try {
            val editor = RemoteMediaSourceEditor(session, "source", backgroundScope)
            fixture.other =
                fixture.other.copy(
                    config =
                        fixture.other.config.copy(serializedArguments = encodedArguments("other"))
                )
            fixture.revision++
            session.refresh()
            editor.saveArguments(
                SelectorMediaSourceArguments.serializer(),
                SelectorMediaSourceArguments.Default.copy(name = "draft"),
            )
            advanceTimeBy(501)
            editor.config.first { it.serializedArguments == encodedArguments("draft") }
            editor.isSaving.first { !it }
            assertEquals(listOf("revision-1"), fixture.revisions.toList())
            assertEquals(encodedArguments("draft"), fixture.source.config.serializedArguments)
            editor.close()
        } finally {
            session.close()
        }
    }

    private class Fixture {
        val revisions = mutableListOf<String>()
        var revision = 0
        var source =
            MediaSourceSave(
                "source",
                "source",
                FactoryId("web-selector"),
                true,
                MediaSourceConfig.Default,
            )
        var other =
            MediaSourceSave(
                "other",
                "other",
                FactoryId("web-selector"),
                true,
                MediaSourceConfig.Default,
            )
        var gate: CompletableDeferred<Unit>? = null
        val started = CompletableDeferred<Unit>()
        private val json = RemoteSettingsProtocol.json
        private val registry =
            RemotePreferenceRegistry(
                PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences())),
                RemoteSettingsRevision { name, value -> "$name:$value" },
            )

        suspend fun connect(scope: CoroutineScope): RemoteSettingsSession {

            val client =
                HttpClient(
                    MockEngine { request ->
                        val body =
                            when (request.url.encodedPath) {
                                "/ping" ->
                                    json.encodeToString(
                                        PingResponse.serializer(),
                                        PingResponse("tv", "6.1", "电视", 1, 1, emptyList()),
                                    )
                                "/state" ->
                                    json.encodeToString(
                                        SettingsSnapshot.serializer(),
                                        SettingsSnapshot(
                                            registry.snapshot(),
                                            VersionedValue(
                                                "revision-$revision",
                                                listOf(source, other),
                                            ),
                                            VersionedValue("empty", emptyList()),
                                            VersionedValue("empty", emptyList()),
                                            emptyList(),
                                        ),
                                    )
                                "/media-source" -> {
                                    val command =
                                        json.decodeFromString(
                                            MediaSourceRequest.serializer(),
                                            (request.body as TextContent).text,
                                        )
                                    revisions += requireNotNull(command.baseRevision)
                                    started.complete(Unit)
                                    gate?.await()
                                    if (command.baseRevision != "revision-$revision")
                                        return@MockEngine respond(
                                            json.encodeToString(
                                                RemoteError.serializer(),
                                                RemoteError("REVISION_CONFLICT", "电视设置已变化"),
                                            ),
                                            HttpStatusCode.Conflict,
                                            headersOf(HttpHeaders.ContentType, "application/json"),
                                        )
                                    val edit = command.command as MediaSourceCommand.Edit
                                    source = source.copy(config = edit.config)
                                    revision++
                                    json.encodeToString(
                                        OperationResult.serializer(),
                                        OperationResult(
                                            command.operationId,
                                            "succeeded",
                                            RemoteOperationPayload.Applied,
                                        ),
                                    )
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
                RemoteSettingsLink(
                    "192.168.1.2",
                    12345,
                    "820f62ee-3a31-4491-b4b7-1e91f6bb12dd",
                    "6.1",
                    "",
                ),
                "6.1",
                scope,
                client,
            )
        }
    }
}
