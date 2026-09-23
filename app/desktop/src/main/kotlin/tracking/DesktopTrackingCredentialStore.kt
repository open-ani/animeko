package me.him188.ani.app.desktop.tracking

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import me.him188.ani.app.data.persistent.asDataStoreSerializer
import me.him188.ani.tracking.api.TrackingCredentialStore
import me.him188.ani.tracking.api.TrackingLoginCredentials
import me.him188.ani.tracking.api.TrackingProviderId

/** Provider-scoped desktop credentials, kept outside settings and backup snapshots. */
class DesktopTrackingCredentialStore(
    private val dataStore: DataStore<Map<String, String>>,
    providerId: TrackingProviderId,
) : TrackingCredentialStore {
    private val credentialKey = "credential:${providerId.value}"

    override suspend fun load(): TrackingLoginCredentials? =
        dataStore.data.first()[credentialKey]?.let { TrackingLoginCredentials(secret = it) }

    override suspend fun save(credentials: TrackingLoginCredentials) {
        dataStore.updateData { it + (credentialKey to credentials.secret) }
    }

    override suspend fun clear() {
        dataStore.updateData { it - credentialKey }
    }
}

fun createDesktopTrackingCredentialDataStore(directory: File): DataStore<Map<String, String>> {
    Files.createDirectories(directory.toPath())
    Files.setPosixFilePermissions(directory.toPath(), setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE,
    ))
    return DataStoreFactory.create(
        serializer = MapSerializer(String.serializer(), String.serializer()).asDataStoreSerializer({ emptyMap() }),
        produceFile = { directory.resolve("credentials") },
    )
}
