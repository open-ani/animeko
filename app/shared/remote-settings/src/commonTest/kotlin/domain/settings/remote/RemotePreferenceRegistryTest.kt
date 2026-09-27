/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.domain.settings.remote

import androidx.datastore.preferences.core.emptyPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import me.him188.ani.app.data.models.preference.DanmakuCacheStrategy
import me.him188.ani.app.data.models.preference.RememberedRoomSession
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.remote.settings.RemoteSettingsProtocol

class RemotePreferenceRegistryTest {
    private val json = RemoteSettingsProtocol.json

    private fun repository() = PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences()))

    private val revisions = RemoteSettingsRevision { key, value -> "$key:$value" }

    @Test
    fun aLocalWriteInvalidatesTheRemoteRevision() = runTest {
        val repository = repository()
        val registry = RemotePreferenceRegistry(repository, revisions)
        val initial = registry.snapshot().videoScaffoldConfig
        val proposed = initial.value.copy(autoPlayNext = false)
        repository.videoScaffoldConfig.update { copy(autoMarkDone = false) }
        val error =
            assertFailsWith<RemoteSettingsException> {
                registry.write(initial.revision, RemotePreference.VideoScaffold(proposed))
            }
        assertEquals("REVISION_CONFLICT", error.code)
        assertTrue(repository.videoScaffoldConfig.flow.first().autoPlayNext)
        assertFalse(repository.videoScaffoldConfig.flow.first().autoMarkDone)
    }

    @Test
    fun concurrentWritersCannotBothCommitWithOneRevision() = runTest {
        val registry = RemotePreferenceRegistry(repository(), revisions)
        val initial = registry.snapshot().videoScaffoldConfig
        val value = RemotePreference.VideoScaffold(initial.value.copy(autoPlayNext = false))
        val first = async { runCatching { registry.write(initial.revision, value) } }
        val second = async { runCatching { registry.write(initial.revision, value) } }
        assertEquals(1, listOf(first.await(), second.await()).count { it.isSuccess })
    }

    @Test
    fun typedProjectionsKeepSecretsLocalAndPreserveThemOnWrite() = runTest {
        val repository = repository()
        val room = RememberedRoomSession("private room", "secret password", 123)
        repository.watchTogetherSettings.update {
            copy(lastRoomName = room.roomName, rememberedSession = room)
        }
        repository.mediaCacheSettings.update { copy(saveDir = "/private/data", enabled = true) }
        repository.playerKernelConfig.update { copy(mpvOptions = listOf("local-option")) }
        val registry = RemotePreferenceRegistry(repository, revisions)
        val snapshot = registry.snapshot()
        assertNull(snapshot.watchTogetherSettings.value.rememberedSession)
        assertEquals("", snapshot.watchTogetherSettings.value.lastRoomName)
        assertNull(snapshot.mediaCacheSettings.value.saveDir)
        assertTrue(snapshot.playerKernelConfig.value.mpvOptions.isEmpty())
        val encoded = json.encodeToString(RemotePreferencesSnapshot.serializer(), snapshot)
        for (secret in
            listOf(
                room.roomName,
                room.password,
                "/private/data",
                "local-option",
                "proxySettings",
            )) {
            assertFalse(secret in encoded)
        }
        registry.write(
            snapshot.watchTogetherSettings.revision,
            RemotePreference.WatchTogether(
                snapshot.watchTogetherSettings.value.copy(followHost = false)
            ),
        )
        registry.write(
            snapshot.mediaCacheSettings.revision,
            RemotePreference.MediaCache(
                snapshot.mediaCacheSettings.value.copy(
                    danmakuCacheStrategy = DanmakuCacheStrategy.DON_NOT_CACHE
                )
            ),
        )
        registry.write(
            snapshot.playerKernelConfig.revision,
            RemotePreference.PlayerKernel(
                snapshot.playerKernelConfig.value.copy(exoPlayerInitEffectGraphInAdvance = false)
            ),
        )
        assertEquals(room, repository.watchTogetherSettings.flow.first().rememberedSession)
        assertEquals(room.roomName, repository.watchTogetherSettings.flow.first().lastRoomName)
        assertFalse(repository.watchTogetherSettings.flow.first().followHost)
        assertEquals("/private/data", repository.mediaCacheSettings.flow.first().saveDir)
        assertTrue(repository.mediaCacheSettings.flow.first().enabled)
        assertEquals(listOf("local-option"), repository.playerKernelConfig.flow.first().mpvOptions)
    }

    @Test
    fun partialPreferenceCannotSmuggleUnsupportedFields() = runTest {
        val registry = RemotePreferenceRegistry(repository(), revisions)
        val initial = registry.snapshot().mediaCacheSettings
        assertFailsWith<RemoteSettingsException> {
            registry.write(
                initial.revision,
                RemotePreference.MediaCache(initial.value.copy(saveDir = "/private/data")),
            )
        }
    }

    @Test
    fun everyPreferenceWrapperRoundTripsWithItsExistingModel() = runTest {
        val snapshot = RemotePreferenceRegistry(repository(), revisions).snapshot()
        for (value in snapshot.values()) {
            val request = PreferenceRequest("operation", snapshot.revisionOf(value), value)
            assertEquals(
                request,
                json.decodeFromString<PreferenceRequest>(json.encodeToString(request)),
            )
        }
        assertEquals(
            snapshot,
            json.decodeFromString<RemotePreferencesSnapshot>(json.encodeToString(snapshot)),
        )
    }

    @Test
    fun unknownAndMismatchedPreferenceVariantsFailDuringDecoding() {
        for (body in
            listOf(
                """{"operationId":"op","baseRevision":"1","value":{"type":"proxySettings","value":{}}}""",
                """{"operationId":"op","baseRevision":"1","value":{"type":"watchTogetherSettings","value":{"playbackSpeed":1.5}}}""",
            )) assertFailsWith<SerializationException> {
            json.decodeFromString<PreferenceRequest>(body)
        }
    }
}
