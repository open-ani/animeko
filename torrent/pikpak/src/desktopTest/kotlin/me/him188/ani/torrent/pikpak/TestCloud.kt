/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.InMemorySessionStore
import io.github.nihildigit.pikpak.PikPakClient
import io.github.nihildigit.pikpak.PikPakFileCache
import io.github.nihildigit.pikpak.PikPakStreamReader
import io.github.nihildigit.pikpak.RangeSource
import io.github.nihildigit.pikpak.fileCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.utils.io.SystemPath
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

/** A client that never reaches the network: what a file cache needs of one is its connection budgets. */
internal fun offlinePikPakClient(): PikPakClient = PikPakClient(
    account = "test@example.com",
    password = "unused",
    sessionStore = InMemorySessionStore(),
    httpClient = HttpClient(MockEngine { error("a test cache must not call the PikPak API") }),
)

/** A stream over [source] with a cache of its own, the shape the SDK's public reader constructor used to build. */
internal fun testReader(
    source: RangeSource,
    size: Long,
    concurrency: Int = PikPakClient.DEFAULT_CONNECTION_BUDGET,
    context: CoroutineContext = Dispatchers.IO,
): PikPakStreamReader = offlinePikPakClient()
    .fileCache(source, size, connectionBudget = concurrency, coroutineContext = context)
    .openStream()

/** The cloud side of an entry over [source], with a cache built the way [CloudFile] builds one. */
internal class FakeCloudSource(
    private val source: RangeSource,
    private val size: Long,
    private val storeProvider: () -> PikPakSparseStore,
    private val onPrepare: suspend () -> Unit = {},
    private val concurrency: Int = 2,
) : CloudSource {
    private val client = offlinePikPakClient()
    private val lock = Mutex()

    @Volatile
    private var current: PikPakFileCache? = null

    var prepareCalls = 0
        private set

    override suspend fun prepare() {
        prepareCalls++
        onPrepare()
    }

    override suspend fun cache(): PikPakFileCache = lock.withLock {
        current?.takeIf { !it.isClosed } ?: client.fileCache(
            source = source,
            size = size,
            storeKey = "test",
            blockStore = storeProvider(),
            connectionBudget = concurrency,
            coroutineContext = Dispatchers.IO,
        ).also { current = it }
    }

    override val deliveredBytes: Long get() = current?.deliveredBytes ?: 0L

    override suspend fun release() = lock.withLock {
        current?.close()
        current = null
    }
}

internal fun testEntry(
    saveDirectory: SystemPath,
    path: String,
    length: Long,
    source: RangeSource,
    gcid: String = "GCID-$path",
    onPrepare: suspend () -> Unit = {},
    onCloud: (FakeCloudSource) -> Unit = {},
    onHandleCountChanged: suspend () -> Unit = {},
    kept: Boolean = false,
    persistKept: suspend (Boolean) -> Unit = {},
) = PikPakFileEntry(
    index = 0,
    length = length,
    saveDirectory = saveDirectory,
    relativePath = path,
    torrentId = "test-source-key",
    parentCoroutineContext = Dispatchers.IO,
    meta = PikPakFileMeta(index = 0, pathInTorrent = path, gcid = gcid, length = length),
    cloudFactory = { storeProvider ->
        FakeCloudSource(source, length, storeProvider, onPrepare).also(onCloud)
    },
    onHandleCountChanged = onHandleCountChanged,
    kept = kept,
    persistKept = persistKept,
)
