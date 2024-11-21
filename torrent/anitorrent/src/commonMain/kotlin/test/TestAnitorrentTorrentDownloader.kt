/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent.test

import kotlinx.io.files.Path
import me.him188.ani.app.torrent.anitorrent.AnitorrentTorrentDownloader
import me.him188.ani.app.torrent.anitorrent.HandleId
import me.him188.ani.app.torrent.anitorrent.session.AnitorrentDownloadSession
import me.him188.ani.app.torrent.anitorrent.session.TorrentAddInfo
import me.him188.ani.app.torrent.anitorrent.session.TorrentDescriptor
import me.him188.ani.app.torrent.anitorrent.session.TorrentFileInfo
import me.him188.ani.app.torrent.anitorrent.session.TorrentHandle
import me.him188.ani.app.torrent.anitorrent.session.TorrentHandleState
import me.him188.ani.app.torrent.anitorrent.session.TorrentManagerSession
import me.him188.ani.app.torrent.anitorrent.session.TorrentResumeData
import me.him188.ani.app.torrent.anitorrent.session.TorrentStats
import me.him188.ani.app.torrent.api.HttpFileDownloader
import me.him188.ani.app.torrent.api.TorrentDownloaderConfig
import me.him188.ani.app.torrent.api.TorrentDownloaderFactory
import me.him188.ani.app.torrent.api.TorrentLibraryLoader
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.peer.PeerInfo
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.writeBytes
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmField
import kotlin.random.Random

@TestOnly
class TestTorrentAddInfo : TorrentAddInfo {
    @JvmField
    var magnetUri: String = ""

    @JvmField
    var torrentFilePath: String = ""


    @JvmField
    var resumeDataPath: String = ""

    override fun setMagnetUri(uri: String) {
        magnetUri = uri
    }

    override fun setTorrentFilePath(absolutePath: String) {
        torrentFilePath = absolutePath
    }

    override fun setResumeDataPath(absolutePath: String) {
        resumeDataPath = absolutePath
    }
}

@TestOnly
open class TestTorrentManagerSession(
    private val dispatchEvent: (id: HandleId, block: (AnitorrentDownloadSession) -> Unit) -> Unit
) : TorrentManagerSession<TestTorrentHandle, TestTorrentAddInfo> {
    override fun createTorrentHandle(): TestTorrentHandle = TestTorrentHandle(dispatchEvent)
    override fun createTorrentAddInfo(): TestTorrentAddInfo = TestTorrentAddInfo()

    override fun resume() {
    }

    override fun applyConfig(config: TorrentDownloaderConfig) {
    }

    override fun releaseHandle(handle: TestTorrentHandle) {
    }

    override fun startDownload(handle: TestTorrentHandle, addInfo: TestTorrentAddInfo, saveDir: Path): Boolean {
        check(!handle.started) { "handle already started" }
        handle.started = true
        handle.addInfo = addInfo
        handle.saveDir = saveDir
        return true
    }
}

@TestOnly
class TestTorrentResumeData : TorrentResumeData {
    override fun saveToPath(path: Path) {
        return path.inSystem.writeBytes(byteArrayOf(1))
    }
}

@TestOnly
data class TestTorrentStats(
    override val total: Long,
    override val totalDone: Long,
    override val allTimeUpload: Long,
    override val allTimeDownload: Long,
    override val downloadPayloadRate: Long,
    override val uploadPayloadRate: Long,
    override val progress: Float,
    override val totalPayloadDownload: Long,
    override val totalPayloadUpload: Long,
) : TorrentStats

@TestOnly
class TestTorrentDescriptor(
    override val name: String,
    override val numPieces: Int,
    override val lastPieceSize: Long,
    override val pieceLength: Long,
    val files: MutableList<TorrentFileInfo> = mutableListOf(),
) : TorrentDescriptor {
    override val fileCount: Int get() = files.size
    override fun fileAtOrNull(index: Int): TorrentFileInfo? = files.getOrNull(index)
}

@TestOnly
class TestPeerInfo(
    override val handle: HandleId,
    override val id: CharArray,
    override val client: String,
    override val ipAddr: String,
    override val ipPort: Int,
    override val progress: Float,
    override val totalDownload: FileSize,
    override val totalUpload: FileSize,
    override val flags: Long
) : PeerInfo

@TestOnly
data class TestTorrentFileInfo(
    override val name: String,
    override val path: String,
    override val size: Long,
) : TorrentFileInfo

@TestOnly
open class TestTorrentHandle(
    private val dispatchEvent: (id: HandleId, block: (AnitorrentDownloadSession) -> Unit) -> Unit
) : TorrentHandle {
    override val id: HandleId = Random.nextLong()
    override var isValid: Boolean = true
    var started: Boolean = false
    lateinit var addInfo: TestTorrentAddInfo
    lateinit var saveDir: Path

    @JvmField // clash
    var state: TorrentHandleState = TorrentHandleState.DOWNLOADING

    private fun dispatchEvent(block: (AnitorrentDownloadSession) -> Unit) {
        dispatchEvent(id, block)
    }

    override fun postStatusUpdates() {
        dispatchEvent(
            block = {
                it.onStatsUpdate(TestTorrentStats(0, 0, 0, 0, 0, 0, 0f, 0, 0))
            },
        )
    }

    override fun postSaveResume() {
        dispatchEvent(
            block = {
                it.onSaveResumeData(TestTorrentResumeData())
            },
        )
    }

    override fun resume() {
    }

    override fun setFilePriority(index: Int, priority: FilePriority) {
    }

    override fun getState(): TorrentHandleState? = state

    protected var descriptor: TestTorrentDescriptor = TestTorrentDescriptor("test", 1024_000 / 1024, 1024, 1024).apply {
        files.add(TestTorrentFileInfo("test.mkv", "test.mkv", 1024_000))
    }

    override fun reloadFile(): TorrentDescriptor {
        return descriptor
    }

    override fun getPeers(): List<PeerInfo> {
        return emptyList()
    }

    val pieceDeadlines: MutableList<Int?> = MutableList(descriptor.numPieces) { null }
    override fun setPieceDeadline(index: Int, deadline: Int) {
        pieceDeadlines[index] = deadline
    }

    override fun clearPieceDeadlines() {
        pieceDeadlines.fill(null)
    }

    override fun addTracker(tracker: String, tier: Short, failLimit: Short) {
    }

    override fun getMagnetUri(): String? = null
}

@TestOnly
open class TestAnitorrentTorrentDownloader(
    rootDataDirectory: SystemPath,
    httpFileDownloader: HttpFileDownloader,
    parentCoroutineContext: CoroutineContext
) : AnitorrentTorrentDownloader<TestTorrentHandle, TestTorrentAddInfo>(
    rootDataDirectory, httpFileDownloader, parentCoroutineContext,
) {
    object Factory : TorrentDownloaderFactory {
        override val name: String get() = "Test"
        override val libraryLoader: TorrentLibraryLoader get() = TorrentLibraryLoader.Noop

        override fun createDownloader(
            rootDataDirectory: SystemPath,
            httpFileDownloader: HttpFileDownloader,
            torrentDownloaderConfig: TorrentDownloaderConfig,
            parentCoroutineContext: CoroutineContext
        ): AnitorrentTorrentDownloader<*, *> {
            return TestAnitorrentTorrentDownloader(
                rootDataDirectory,
                httpFileDownloader,
                parentCoroutineContext,
            )
        }

    }


    override val native: TestTorrentManagerSession = TestTorrentManagerSession(
        dispatchEvent = { id, block ->
            dispatchToSession1(id, block)
        },
    )

    fun dispatchToSession1(
        id: HandleId,
        block: (AnitorrentDownloadSession) -> Unit // will be inlined twice, for good amortized performance
    ) {
        return super.dispatchToSession(id, block)
    }

    fun findSessionById(id: HandleId): AnitorrentDownloadSession? {
        return super.openSessions.value.entries.find { it.value.handleId == id }?.value
    }

    fun firstSession(): AnitorrentDownloadSession = super.openSessions.value.entries.first().value

    fun dispatchToFirstSession(block: (AnitorrentDownloadSession) -> Unit) {
        return dispatchToSession1(firstSession().handleId, block)
    }
}
