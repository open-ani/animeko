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
import kotlin.test.assertFails
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
    fun rejectsCorruptCiphertext() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val providerId = TrackingProviderId("credential-store-corruption-test")
        val store = AndroidTrackingCredentialStore(context, providerId)
        val file = context.noBackupFilesDir.resolve("tracking-${providerId.value}.credentials")
        store.clear()
        try {
            store.save(TrackingLoginCredentials("account", "test-secret"))
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertFails { store.load() }
            Unit
        } finally {
            store.clear()
        }
    }
}
