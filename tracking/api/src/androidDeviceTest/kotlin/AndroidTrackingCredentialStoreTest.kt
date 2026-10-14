/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.tracking.api

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AndroidTrackingCredentialStoreTest {
    @Test
    fun savesOverwritesRestoresAndClearsCredentials() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val providerId = TrackingProviderId("credential-store-test")
        val store = AndroidTrackingCredentialStore(context, providerId)
        store.clear()
        try {
            assertNull(store.load())
            store.save(TrackingLoginCredentials("first", "first-test-secret"))
            assertEquals(
                TrackingLoginCredentials("first", "first-test-secret"),
                AndroidTrackingCredentialStore(context, providerId).load(),
            )
            store.save(TrackingLoginCredentials("second", "second-test-secret"))
            assertEquals(
                TrackingLoginCredentials("second", "second-test-secret"),
                AndroidTrackingCredentialStore(context, providerId).load(),
            )
            store.clear()
            assertNull(AndroidTrackingCredentialStore(context, providerId).load())
        } finally {
            store.clear()
        }
    }

    @Test
    fun discardsTamperedCiphertext() = assertUnreadableFileIsDiscarded("credential-store-tamper-test") { bytes ->
        bytes.also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
    }

    @Test
    fun discardsTruncatedFile() = assertUnreadableFileIsDiscarded("credential-store-truncate-test") { bytes ->
        bytes.copyOf(3)
    }

    private fun assertUnreadableFileIsDiscarded(id: String, corrupt: (ByteArray) -> ByteArray) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val providerId = TrackingProviderId(id)
        val store = AndroidTrackingCredentialStore(context, providerId)
        val file = context.noBackupFilesDir.resolve("tracking-${providerId.value}.credentials")
        store.clear()
        try {
            store.save(TrackingLoginCredentials("account", "test-secret"))
            file.writeBytes(corrupt(file.readBytes()))

            assertNull(store.load())
            assertFalse(file.exists())
            store.save(TrackingLoginCredentials("account", "new-secret"))
            assertEquals("new-secret", store.load()?.secret)
        } finally {
            store.clear()
        }
    }
}
