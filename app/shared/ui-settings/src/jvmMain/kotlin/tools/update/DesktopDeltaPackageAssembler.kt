/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.discard
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import java.io.File
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * 桌面的 [DeltaPackageAssembler]: 用 [installRoot] 里内容相同的文件和从发布包按 Range 取来的 entry, 在本地拼出与发布包等价的包.
 *
 * 发布包的清单 ([DesktopPackageManifest]) 列出每个 entry 的 sha256 和压缩数据的位置. [installRoot] 下 sha256
 * 相同的文件直接复用, 其余 entry 从发布包取原始压缩数据, 相隔不超过 [COALESCE_GAP_BYTES] 的合并成一个 Range
 * 请求. 拼出的包逐 entry 核对 sha256 后才返回, 安装流程与整包下载相同, 所以安装器不需要知道包是怎么来的.
 *
 * [output] 决定拼出来的是 zip 还是解压好的目录.
 */
class DesktopDeltaPackageAssembler(
    private val client: ScopedHttpClient,
    private val installRoot: File,
    private val output: PackageOutput = PackageOutput.Zip,
) : DeltaPackageAssembler {
    override suspend fun assemble(
        packageName: String,
        manifestUrl: String,
        packageUrl: suspend () -> String,
        saveDir: SystemPath,
        onProgress: (DeltaPackageProgress) -> Unit,
    ): SystemPath {
        val target = saveDir.resolve(output.targetName(packageName))
        withContext(Dispatchers.IO_) {
            assemble(packageName, manifestUrl, packageUrl, target.toFile(), onProgress)
        }
        return target
    }

    /**
     * 把发布包拼到 [targetFile]. 已存在且与清单一致的文件直接复用.
     */
    private suspend fun assemble(
        packageName: String,
        manifestUrl: String,
        packageUrl: suspend () -> String,
        targetFile: File,
        onProgress: (DeltaPackageProgress) -> Unit,
    ) {
        val startTime = TimeSource.Monotonic.markNow()
        val manifest = fetchManifest(manifestUrl)
        if (targetFile.exists()) {
            if (runCatching { output.verify(targetFile, manifest) }.isSuccess) {
                logger.info { "Package ${targetFile.name} already assembled and verified, skipping" }
                return
            }
            targetFile.deleteRecursively()
        }

        val index = LocalFileIndex.build(installRoot, manifest)
        val plan = plan(manifest, index)
        logger.info {
            "Assembling ${targetFile.name}: ${plan.localEntries} entries from $installRoot, " +
                    "${plan.remoteEntries} entries (${plan.remoteBytes} bytes) in ${plan.requestCount} requests from $packageName"
        }

        targetFile.parentFile?.mkdirs()
        val partFile = File(targetFile.path + ".part")
        partFile.deleteRecursively()
        try {
            output.createWriter(partFile).use { writer ->
                var done = 0L
                var downloaded = 0L
                fun reportProgress() {
                    onProgress(
                        DeltaPackageProgress(
                            downloadedBytes = downloaded,
                            totalDownloadBytes = plan.remoteBytes,
                            fraction = if (plan.totalBytes == 0L) 1f else (done.toFloat() / plan.totalBytes).coerceIn(0f, 1f),
                        ),
                    )
                }

                fun advance(bytes: Long, remote: Boolean) {
                    done += bytes
                    if (remote) downloaded += bytes
                    reportProgress()
                }
                reportProgress()
                for (segment in plan.segments) {
                    if (segment.range == null) {
                        for (step in segment.steps) writeLocalStep(writer, step, ::advance)
                    } else {
                        fetchRange(packageUrl(), packageName, manifest, segment.range) { channel ->
                            var position = segment.range.first
                            for (step in segment.steps) {
                                if (step is Step.Remote) {
                                    channel.discardExactly(step.entry.dataOffset - position)
                                    writer.writeRawEntry(step.entry) { out ->
                                        channel.copyExactly(step.entry.compressedSize, out)
                                    }
                                    position = step.entry.dataOffset + step.entry.compressedSize
                                    advance(step.entry.compressedSize, remote = true)
                                } else {
                                    writeLocalStep(writer, step, ::advance)
                                }
                            }
                        }
                    }
                }
                writer.finish()
            }
            output.verify(partFile, manifest)
            check(partFile.renameTo(targetFile)) { "Failed to move $partFile to $targetFile" }
        } finally {
            partFile.deleteRecursively()
        }
        logger.info {
            "Assembled ${targetFile.name} in ${startTime.elapsedNow()}, " +
                    "downloaded ${plan.remoteBytes} of ${manifest.packageSize} bytes"
        }
    }

    private fun writeLocalStep(writer: PackageWriter, step: Step, advance: (Long, Boolean) -> Unit) {
        when (step) {
            is Step.Directory -> writer.writeDirectory(step.entry)
            is Step.Local -> {
                writer.writeLocalEntry(step.entry, step.file)
                advance(step.entry.size, false)
            }

            is Step.Remote -> error("Remote entry outside a range segment: ${step.entry.name}")
        }
    }

    private suspend fun fetchManifest(manifestUrl: String): DesktopPackageManifest {
        val text = client.use {
            val response = get(manifestUrl)
            if (!response.status.isSuccess()) {
                throw IOException("Failed to fetch package manifest: ${response.status}")
            }
            response.bodyAsText()
        }
        return DesktopPackageManifest.parse(text)
    }

    /**
     * 请求发布包 [packageUrl] 的 [range] (闭区间) 并把响应体交给 [block]. 服务器返回的不是 206, 或 Content-Range
     * 里的总大小与清单不符, 说明它给的不是这个包或者不支持 Range, 抛 [IOException].
     *
     * 错误信息只带 [packageName]: 地址可能带有签名.
     */
    private suspend fun fetchRange(
        packageUrl: String,
        packageName: String,
        manifest: DesktopPackageManifest,
        range: LongRange,
        block: suspend (ByteReadChannel) -> Unit,
    ) {
        client.use {
            prepareGet(packageUrl) {
                header(HttpHeaders.Range, "bytes=${range.first}-${range.last}")
                timeout { requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds }
            }.execute { response ->
                if (response.status != HttpStatusCode.PartialContent) {
                    throw IOException("Expected 206 Partial Content for $packageName, got ${response.status}")
                }
                val total = response.headers[HttpHeaders.ContentRange]?.substringAfterLast('/')?.toLongOrNull()
                if (total != manifest.packageSize) {
                    throw IOException("Size of $packageName is $total, manifest says ${manifest.packageSize}")
                }
                val channel = response.bodyAsChannel()
                try {
                    block(channel)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // 响应体读到一半就抛出时, execute 收尾要等没读完的响应体, Windows 上观察到这一步再也不返回.
                    // 先把剩下的读完 (限时), 让收尾能正常结束, 再把错误抛给调用方回退整包下载.
                    runCatching { withTimeout(DRAIN_TIMEOUT) { channel.discard() } }
                    throw e
                }
            }
        }
    }

    private suspend fun ByteReadChannel.discardExactly(count: Long) {
        if (count == 0L) return
        val discarded = discard(count)
        if (discarded != count) throw IOException("Response ended after $discarded of $count bytes to skip")
    }

    private suspend fun ByteReadChannel.copyExactly(count: Long, out: OutputStream) {
        val buffer = ByteArray(DEFAULT_COPY_BUFFER)
        var remaining = count
        while (remaining > 0) {
            val read = readAvailable(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IOException("Response ended with $remaining of $count bytes missing")
            out.write(buffer, 0, read)
            remaining -= read
        }
    }

    private sealed interface Step {
        val entry: DesktopPackageEntry

        class Directory(override val entry: DesktopPackageEntry) : Step
        class Local(override val entry: DesktopPackageEntry, val file: File) : Step
        class Remote(override val entry: DesktopPackageEntry) : Step
    }

    /**
     * 连续的一段 entry. [range] 不为空时这一段里有需要下载的 entry, 整段的字节用一个 Range 请求取回,
     * 中间本地已有的 entry 在流里跳过.
     */
    private class Segment(val steps: List<Step>, val range: LongRange?)

    private class Plan(val segments: List<Segment>, val totalBytes: Long, val remoteBytes: Long) {
        val localEntries: Int = segments.sumOf { segment -> segment.steps.count { it is Step.Local } }
        val remoteEntries: Int = segments.sumOf { segment -> segment.steps.count { it is Step.Remote } }
        val requestCount: Int = segments.count { it.range != null }
    }

    /**
     * 决定每个 entry 从哪里来, 并把需要下载的 entry 按 [COALESCE_GAP_BYTES] 合并成 Range 请求.
     */
    private fun plan(manifest: DesktopPackageManifest, index: LocalFileIndex): Plan {
        val steps = manifest.entries.map { entry ->
            when {
                entry.isDirectory -> Step.Directory(entry)
                else -> entry.sha256?.let { index[it] }?.let { Step.Local(entry, it) } ?: Step.Remote(entry)
            }
        }
        val segments = ArrayList<Segment>()
        var pending = ArrayList<Step>()
        var rangeStart = -1L
        var rangeEnd = -1L
        fun flush() {
            if (pending.isEmpty()) return
            segments += Segment(pending, if (rangeStart >= 0) rangeStart..rangeEnd else null)
            pending = ArrayList()
            rangeStart = -1
            rangeEnd = -1
        }
        for (step in steps) {
            if (step is Step.Remote) {
                val dataEnd = step.entry.dataOffset + step.entry.compressedSize - 1
                if (rangeStart < 0) {
                    // 前面的本地 entry 不属于任何请求, 先独立成段.
                    flush()
                    rangeStart = step.entry.dataOffset
                } else if (step.entry.dataOffset - rangeEnd - 1 > COALESCE_GAP_BYTES) {
                    flush()
                    rangeStart = step.entry.dataOffset
                }
                rangeEnd = maxOf(rangeEnd, dataEnd)
            }
            pending += step
        }
        flush()
        // 一段的末尾可能跟着本地 entry, 它们不需要在流里跳过, 但仍在这段里按顺序写出.
        val remoteBytes = steps.filterIsInstance<Step.Remote>().sumOf { it.entry.compressedSize }
        val localBytes = steps.filterIsInstance<Step.Local>().sumOf { it.entry.size }
        return Plan(segments, totalBytes = remoteBytes + localBytes, remoteBytes = remoteBytes)
    }

    private companion object {
        private val logger = logger<DesktopDeltaPackageAssembler>()

        /** 两个要下载的 entry 相隔不超过这么多字节时合并成一个请求, 中间的字节多下一点换来少一次往返. */
        const val COALESCE_GAP_BYTES = 256 * 1024L

        /**
         * entry 校验失败后放弃这个 Range 请求前, 最多花这么久把剩下的响应体读完.
         */
        val DRAIN_TIMEOUT = 30.seconds
        const val DEFAULT_COPY_BUFFER = 64 * 1024

        /** 一个请求可能覆盖几百 MB, 不能用 client 默认的请求超时. */
        val REQUEST_TIMEOUT = 30.minutes
    }
}
