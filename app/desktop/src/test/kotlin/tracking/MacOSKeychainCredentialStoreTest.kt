package me.him188.ani.app.desktop.tracking

import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.him188.ani.tracking.api.TrackingLoginCredentials
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatformDesktop

class MacOSKeychainCredentialStoreTest {
    @Test
    fun savesReplacesAndClearsSyntheticCredential() = runBlocking {
        if (currentPlatformDesktop() !is Platform.MacOS) return@runBlocking
        val store = MacOSKeychainCredentialStore(
            serviceName = "me.him188.ani.tracking.test.${UUID.randomUUID()}",
            accountName = "synthetic",
        )
        try {
            assertNull(store.load())
            store.save(TrackingLoginCredentials(secret = "synthetic-one"))
            assertEquals("synthetic-one", store.load()?.secret)
            store.save(TrackingLoginCredentials(secret = "synthetic-two"))
            assertEquals("synthetic-two", store.load()?.secret)
        } finally {
            store.clear()
        }
        assertNull(store.load())
    }
}
