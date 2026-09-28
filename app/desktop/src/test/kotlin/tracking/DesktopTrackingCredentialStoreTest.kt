package me.him188.ani.app.desktop.tracking

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.tracking.api.TrackingLoginCredentials
import me.him188.ani.tracking.api.TrackingProviderId

class DesktopTrackingCredentialStoreTest {
    @Test
    fun storesCredentialsInPrivateDirectory() = runBlocking {
        val directory = Files.createTempDirectory("animeko-tracking-test").resolve("tracking")
        try {
            val store = DesktopTrackingCredentialStore(
                createDesktopTrackingCredentialDataStore(directory.toFile()),
                TrackingProviderId("anilist"),
            )
            store.save(TrackingLoginCredentials(secret = "synthetic-token"))
            assertEquals("synthetic-token", store.load()?.secret)
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
                Files.getPosixFilePermissions(directory),
            )
            assertEquals(true, Files.exists(directory.resolve("credentials")))
        } finally {
            directory.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun savesCredentialsByProviderAndClearsOnlySelectedProvider() = runBlocking {
        val dataStore = MemoryDataStore(emptyMap<String, String>())
        val aniList = DesktopTrackingCredentialStore(dataStore, TrackingProviderId("anilist"))
        val other = DesktopTrackingCredentialStore(dataStore, TrackingProviderId("other"))

        assertNull(aniList.load())
        aniList.save(TrackingLoginCredentials(secret = "synthetic-anilist"))
        other.save(TrackingLoginCredentials(secret = "synthetic-other"))
        assertEquals("synthetic-anilist", aniList.load()?.secret)
        assertEquals("synthetic-other", other.load()?.secret)

        aniList.clear()
        assertNull(aniList.load())
        assertEquals("synthetic-other", other.load()?.secret)
    }
}
