package me.him188.ani.app.data.persistent

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import me.him188.ani.utils.io.SystemPath

actual interface DataStoreSerializer<T> {
    val defaultValue: T
}

actual fun <T> KSerializer<T>.asDataStoreSerializer(
    defaultValue: () -> T,
    format: Json,
): DataStoreSerializer<T> = object : DataStoreSerializer<T> {
    override val defaultValue: T by lazy(defaultValue)
}

actual fun <T> ReplaceFileCorruptionHandler(produceNewData: (CorruptionException) -> T): ReplaceFileCorruptionHandler<T> =
    ReplaceFileCorruptionHandler(produceNewData)

actual fun <T> DataStoreFactory.create(
    serializer: DataStoreSerializer<T>,
    corruptionHandler: ReplaceFileCorruptionHandler<T>?,
    migrations: List<DataMigration<T>>,
    scope: CoroutineScope,
    produceFile: () -> SystemPath,
): DataStore<T> {
    val state = MutableStateFlow(serializer.defaultValue)
    return object : DataStore<T> {
        override val data: Flow<T> = state
        override suspend fun updateData(transform: suspend (t: T) -> T): T {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
