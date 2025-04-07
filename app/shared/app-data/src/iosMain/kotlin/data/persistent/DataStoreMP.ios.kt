/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent

import androidx.annotation.GuardedBy
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import androidx.datastore.core.createSingleProcessCoordinator
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.use
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.okio.asOkioSink
import kotlinx.io.okio.asOkioSource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.json.io.encodeToSink
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.bufferedSink
import me.him188.ani.utils.io.bufferedSource
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.moveTo
import okio.buffer

actual fun <T> ReplaceFileCorruptionHandler(produceNewData: (CorruptionException) -> T): ReplaceFileCorruptionHandler<T> {
    return ReplaceFileCorruptionHandler(produceNewData)
}

actual fun <T> KSerializer<T>.asDataStoreSerializer(
    defaultValue: () -> T,
    format: Json,
): DataStoreSerializer<T> {
    val serializer = this
    return object : DataStoreSerializer<T> {
        override val defaultValue: T by lazy(defaultValue)

        override suspend fun readFrom(input: Source): T {
            try {
                return format.decodeFromSource(serializer, input)
            } catch (e: Exception) {
                throw CorruptionException("Failed to decode data", e)
            }
        }

        override suspend fun writeTo(t: T, output: Sink) {
            format.encodeToSink(serializer, t, output)
        }
    }
}

actual interface DataStoreSerializer<T> {

    /**
     * Value to return if there is no data on disk.
     */
    val defaultValue: T

    /**
     * Unmarshal object from stream.
     *
     * @param input the InputStream with the data to deserialize
     */
    suspend fun readFrom(input: Source): T

    /**
     *  Marshal object to a stream. Closing the provided OutputStream is a no-op.
     *
     *  @param t the data to write to output
     *  @output the OutputStream to serialize data to
     */
    suspend fun writeTo(t: T, output: Sink)
}

/**
 * The Java IO File version of the Storage<T> interface. Is able to read and write [T] to a given
 * file location.
 *
 * @param serializer The serializer that can write [T] to and from a byte array.
 * @param coordinatorProducer The producer to provide [InterProcessCoordinator] that coordinates IO
 * operations across processes if needed. By default it provides single process coordinator, which
 * doesn't support cross process use cases.
 * @param produceFile The file producer that returns the file that will be read and written.
 */
class FileStorage<T>(
    private val serializer: DataStoreSerializer<T>,
    private val coordinatorProducer: (SystemPath) -> InterProcessCoordinator = {
        createSingleProcessCoordinator(it.toString())
    },
    private val produceFile: () -> SystemPath
) : Storage<T> {

    override fun createConnection(): StorageConnection<T> {
        val file = produceFile()

        synchronized(activeFilesLock) {
            val path = file.absolutePath
            check(!activeFiles.contains(path)) {
                "There are multiple DataStores active for the same file: $path. You should " +
                        "either maintain your DataStore as a singleton or confirm that there is " +
                        "no two DataStore's active on the same file (by confirming that the scope" +
                        " is cancelled)."
            }
            activeFiles.add(path)
        }

        return FileStorageConnection(file, serializer, coordinatorProducer(file)) {
            synchronized(activeFilesLock) {
                activeFiles.remove(file.absolutePath)
            }
        }
    }

    internal companion object {
        /**
         * Active files should contain the absolute path for which there are currently active
         * DataStores. A DataStore is active until the scope it was created with has been
         * cancelled. Files aren't added to this list until the first read/write because the file
         * path is computed asynchronously.
         */
        @GuardedBy("activeFilesLock")
        internal val activeFiles = mutableSetOf<String>()

        internal val activeFilesLock = SynchronizedObject()
    }
}

internal class FileStorageConnection<T>(
    private val file: SystemPath,
    private val serializer: DataStoreSerializer<T>,
    override val coordinator: InterProcessCoordinator,
    private val onClose: () -> Unit
) : StorageConnection<T> {

    private val closed = atomic(false)

    // TODO:(b/233402915) support multiple readers
    private val transactionMutex = Mutex()

    override suspend fun <R> readScope(
        block: suspend ReadScope<T>.(locked: Boolean) -> R
    ): R {
        checkNotClosed()

        val lock = transactionMutex.tryLock()
        try {
            return FileReadScope(file, serializer).use {
                block(it, lock)
            }
        } finally {
            if (lock) {
                transactionMutex.unlock()
            }
        }
    }

    override suspend fun writeScope(block: suspend WriteScope<T>.() -> Unit) {
        checkNotClosed()
        file.createParentDirectories()

        transactionMutex.withLock {
            val scratchFile = Path(file.absolutePath + ".tmp").inSystem
            try {
                FileWriteScope(scratchFile, serializer).use {
                    block(it)
                }
                if (scratchFile.exists()) {
                    scratchFile.moveTo(file)
                }
            } catch (ex: IOException) {
                if (scratchFile.exists()) {
                    scratchFile.delete() // Swallow failure to delete
                }
                throw ex
            }
        }
    }

    override fun close() {
        closed.value = true
        onClose()
    }

    private fun checkNotClosed() {
        check(!closed.value) { "StorageConnection has already been disposed." }
    }

    private fun SystemPath.createParentDirectories() {
        path.parent?.inSystem?.createDirectories()
    }
}

internal open class FileReadScope<T>(
    protected val file: SystemPath,
    protected val serializer: DataStoreSerializer<T>
) : ReadScope<T> {

    private val closed = atomic(false)

    override suspend fun readData(): T {
        checkNotClosed()
        return try {
            file.bufferedSource().use { stream ->
                serializer.readFrom(stream)
            }
        } catch (ex: FileNotFoundException) {
            if (file.exists()) {
                // Re-read to prevent throwing from a race condition where the file is created by
                // another process after the initial read attempt but before `file.exists()` is
                // called. Otherwise file exists but we can't read it; throw FileNotFoundException
                // because something is wrong.
                return file.bufferedSource().use { stream ->
                    serializer.readFrom(stream)
                }
            }
            return serializer.defaultValue
        }
    }

    override fun close() {
        closed.value = true
    }

    protected fun checkNotClosed() {
        check(!closed.value) { "This scope has already been closed." }
    }
}

internal class FileWriteScope<T>(file: SystemPath, serializer: DataStoreSerializer<T>) :
    FileReadScope<T>(file, serializer), WriteScope<T> {

    override suspend fun writeData(value: T) {
        checkNotClosed()
        val fos = file.bufferedSink()
        fos.use { stream ->
            serializer.writeTo(value, stream)
//            stream.fd.sync()
            // TODO(b/151635324): fsync the directory, otherwise a badly timed crash could
            //  result in reverting to a previous state.
        }
    }
}

actual fun <T> DataStoreFactory.create(
    serializer: DataStoreSerializer<T>,
    corruptionHandler: ReplaceFileCorruptionHandler<T>?,
    migrations: List<DataMigration<T>>,
    scope: CoroutineScope,
    produceFile: () -> SystemPath
): DataStore<T> {
    return create(
        storage = FileStorage(
            serializer = serializer,
            produceFile = produceFile,
        ),
        corruptionHandler = corruptionHandler,
        migrations = migrations,
        scope = scope,
    )
}

fun <T> OkioSerializer<T>.asDataStoreSerializer(defaultValue: T): DataStoreSerializer<T> {
    val serializer = this
    return object : DataStoreSerializer<T> {
        override val defaultValue: T = defaultValue

        override suspend fun readFrom(input: Source): T {
            return serializer.readFrom(input.asOkioSource().buffer())
        }

        override suspend fun writeTo(t: T, output: Sink) {
            serializer.writeTo(t, output.asOkioSink().buffer())
        }
    }
}
