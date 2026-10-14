/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * 桌面自动更新端到端测试, 见 [DesktopUpdateE2e].
 *
 * 没有声明输出, 每次都会运行.
 */
abstract class DesktopUpdateE2eTask : DefaultTask() {
    /**
     * 打包好的应用 (Windows 是 `Ani` 目录, macOS 是 `Ani.app`), 或者包含它的目录.
     */
    @get:Internal
    abstract val appDir: DirectoryProperty

    /**
     * 安装目录副本、新版本发布包、数据目录和日志都放在这里, 开始时清空.
     */
    @get:Internal
    abstract val workDir: DirectoryProperty

    @get:Internal
    @set:Option(option = "scenario", description = "Only run scenarios whose name contains this text")
    var scenario: String? = null

    @TaskAction
    fun run() {
        val log = E2eLog(lifecycle = logger::lifecycle, warn = logger::warn, error = { message, e -> logger.error(message, e) })
        DesktopUpdateE2e(locatePackagedApp(appDir.get().asFile), workDir.get().asFile, log).run(scenario)
    }
}

/**
 * 测试驱动器的日志出口: 作为 Gradle 任务运行时接到任务的 logger, 作为独立程序运行时接到标准输出.
 */
class E2eLog(
    val lifecycle: (String) -> Unit,
    val warn: (String) -> Unit,
    val error: (String, Throwable?) -> Unit,
)

/**
 * 在 [dir] 下找打包好的应用: [dir] 本身, 或它下面三层以内的 `Ani` (含 `Ani.exe`) 或 `Ani.app`. CI 下载的产物外面可能多一两层目录.
 */
fun locatePackagedApp(dir: File): File {
    val platform = E2ePlatform.current()
    fun isApp(candidate: File) = candidate.isDirectory && platform.executable(candidate).isFile
    if (isApp(dir)) return dir
    val found = dir.walkTopDown().maxDepth(3).firstOrNull { it.name == platform.appDirName && isApp(it) }
    return checkNotNull(found) { "No packaged app (${platform.appDirName}) found under $dir" }
}

/**
 * 用打包好的应用当旧版本, 从它派生一个新版本发布包 (加文件、改文件、删文件、改名), 由本机 mock 的版本 API 和下载服务器提供;
 * 以测试任务 `update-e2e` 启动旧版本, 让它走真实的检查、下载、安装、重启流程; 最后核对安装目录与新版本一致, 并按场景核对
 * 服务器收到的请求 (增量时只有 Range 请求且流量远小于整包, 回退时下载了整包). 安装器还不能自动完成安装的平台
 * ([E2ePlatform.installs] 为 `false`) 到下载为止, 核对的是下载到的包.
 *
 * 每个场景都从干净的安装目录和数据目录开始. 应用通过 `ANIMEKO_DESKTOP_TEST_DATA_DIR` 把数据放在工作目录里, 不碰机器上
 * 正常安装的应用. macOS 的安装脚本用 `open` 启动新版本, 它不继承本进程的环境, 所以环境变量同时通过 `launchctl setenv`
 * 放进 launchd, 测试结束后撤掉.
 *
 * 新版本与旧版本是同一个构建, 只多了标记文件等: 应用的版本号不变, 版本 API 返回什么版本就更新到什么版本, 不需要真的构建
 * 两个版本. 改动只落在运行时的 `legal` 文本和新增文件上, 不改任何会被加载的 jar 或库, 新版本才能正常启动.
 */
class DesktopUpdateE2e(
    private val appDir: File,
    private val workDir: File,
    private val logger: E2eLog,
) {
    private val platform = E2ePlatform.current()

    /**
     * 原始打包产物每个文件的 sha256. 每个场景的安装目录都是它的副本, 算一次给所有场景用.
     */
    private val distTree: Map<String, String> by lazy { hashTree(appDir) }

    fun run(scenarioFilter: String?) {
        check(platform.executable(appDir).isFile) { "Not a packaged app: $appDir" }
        workDir.deleteRecursively()
        workDir.mkdirs()

        val selected = SCENARIOS
            .filter { scenarioFilter == null || it.name.contains(scenarioFilter) }
            .filter { scenario ->
                if (scenario.dataDirOnOtherVolume && otherVolumeDir() == null) {
                    logger.lifecycle("[update-e2e] ${scenario.name}: skipped, no other volume on this machine")
                    false
                } else {
                    true
                }
            }
        check(selected.isNotEmpty()) { "No scenario matches '$scenarioFilter'" }

        val fixture = buildNewVersion()
        val failures = ArrayList<String>()
        for ((index, scenario) in selected.withIndex()) {
            val version = "9999.${index + 1}.0"
            logger.lifecycle("[update-e2e] ${scenario.name} ($version): ${scenario.description}")
            val run = ScenarioRun(scenario, version, fixture)
            try {
                run.execute()
                logger.lifecycle("[update-e2e] ${scenario.name}: passed")
            } catch (e: Exception) {
                logger.error("[update-e2e] ${scenario.name}: FAILED: ${e.message}", e)
                logger.error(run.diagnostics(), null)
                failures += "${scenario.name}: ${e.message}"
            } finally {
                run.killLeftoverProcesses()
            }
        }
        if (failures.isNotEmpty()) {
            throw GradleException("Desktop update E2E failed:\n" + failures.joinToString("\n"))
        }
    }

    // region 新版本

    private class NewVersionFixture(
        val dir: File,
        val zip: File,
        val manifest: File,
        val sha1Hex: String,
        val payloadSize: Long,
    )

    private fun buildNewVersion(): NewVersionFixture {
        val newDir = workDir.resolve("new").resolve(platform.appDirName)
        copyTree(appDir, newDir)
        val root = platform.appRoot(newDir)

        // 运行时的 legal 文本不会被加载, 改它们不影响启动.
        val legalFiles = root.walkTopDown()
            .filter { it.isFile && it.parentFile.path.contains("${File.separator}legal${File.separator}") }
            .sortedBy { it.path }
            .take(3)
            .toList()
        check(legalFiles.size == 3) { "Need 3 files under a 'legal' directory to derive a new version, found ${legalFiles.size}" }
        val (modified, removed, renamed) = legalFiles
        modified.appendText("\nupdate-e2e: modified\n")
        check(removed.delete())
        check(renamed.renameTo(renamed.resolveSibling(renamed.name + ".renamed")))

        root.resolve(MARKER_PATH).apply { parentFile.mkdirs() }.writeText(NEW_VERSION_MARKER)
        val payload = Random(20241014).nextBytes(PAYLOAD_SIZE)
        root.resolve(PAYLOAD_PATH).writeBytes(payload)

        val zip = workDir.resolve("new").resolve("package.zip")
        logger.lifecycle("[update-e2e] zipping new version to $zip")
        writeZip(newDir, zip)
        val manifest = File(zip.path + DESKTOP_PACKAGE_MANIFEST_SUFFIX)
        readDesktopPackageManifest(zip).writeTo(manifest)
        return NewVersionFixture(
            dir = newDir,
            zip = zip,
            manifest = manifest,
            sha1Hex = zip.digestHex("SHA-1"),
            payloadSize = payload.size.toLong(),
        )
    }

    /**
     * 与 ci-helper 的 `zipDesktopDistribution` 产出相同形态的发布包: 顶层是应用目录, 保留 unix 权限, 不用 data descriptor.
     */
    private fun writeZip(dir: File, zip: File) {
        ZipArchiveOutputStream(zip).use { out ->
            out.setUseZip64(Zip64Mode.Never)
            for (file in dir.walkTopDown().sortedBy { it.path }) {
                val relative = file.relativeTo(dir).invariantSeparatorsPath
                val name = buildString {
                    append(dir.name)
                    if (relative.isNotEmpty()) append('/').append(relative)
                    if (file.isDirectory) append('/')
                }
                val entry = ZipArchiveEntry(name)
                entry.time = file.lastModified()
                entry.unixMode = unixMode(file)
                if (file.isFile) entry.size = file.length()
                out.putArchiveEntry(entry)
                if (file.isFile) file.inputStream().use { it.copyTo(out) }
                out.closeArchiveEntry()
            }
        }
    }

    private fun unixMode(file: File): Int {
        if (file.isDirectory) return "755".toInt(8)
        val permissions = runCatching { Files.getPosixFilePermissions(file.toPath()) }.getOrNull()
            ?: return "644".toInt(8)
        var mode = 0
        for (permission in permissions) {
            mode = mode or when (permission) {
                PosixFilePermission.OWNER_READ -> "400".toInt(8)
                PosixFilePermission.OWNER_WRITE -> "200".toInt(8)
                PosixFilePermission.OWNER_EXECUTE -> "100".toInt(8)
                PosixFilePermission.GROUP_READ -> "40".toInt(8)
                PosixFilePermission.GROUP_WRITE -> "20".toInt(8)
                PosixFilePermission.GROUP_EXECUTE -> "10".toInt(8)
                PosixFilePermission.OTHERS_READ -> 4
                PosixFilePermission.OTHERS_WRITE -> 2
                PosixFilePermission.OTHERS_EXECUTE -> 1
            }
        }
        return mode
    }

    // endregion

    // region 场景

    private enum class Transfer { DELTA, FULL, NONE }

    private class ServerBehavior(
        val hasUpdate: Boolean = true,
        val serveManifest: Boolean = true,
        val honorRange: Boolean = true,
        /**
         * 第一个 Range 响应的内容按位取反, 模拟镜像给了错的数据.
         */
        val corruptFirstRange: Boolean = false,
    )

    private class Scenario(
        val name: String,
        val description: String,
        val behavior: ServerBehavior,
        val transfer: Transfer,
        /**
         * 数据目录 (暂存目录在里面) 放到与安装目录不同的卷上. Windows 更新器在不同卷时逐文件移动而不是重命名交换.
         */
        val dataDirOnOtherVolume: Boolean = false,
    )

    private inner class ScenarioRun(
        private val scenario: Scenario,
        private val version: String,
        private val fixture: NewVersionFixture,
    ) {
        private val dir = workDir.resolve(scenario.name)
        private val installParent = dir.resolve("install")
        private val installDir = installParent.resolve(platform.appDirName)
        private val dataDir = if (scenario.dataDirOnOtherVolume) {
            otherVolumeDir()!!.resolve("animeko-update-e2e-${scenario.name}")
        } else {
            dir.resolve("data")
        }
        private val resultFile = dir.resolve("result.jsonl")
        private val configFile = dir.resolve("config.json")
        private val stdoutFile = dir.resolve("app-stdout.log")
        private var recordedRequests: List<MockUpdateServer.RecordedRequest> = emptyList()

        fun execute() {
            platform.copyApp(appDir, installDir)
            val originalTree = distTree
            dataDir.deleteRecursively()
            dataDir.mkdirs()
            MockUpdateServer(platform, fixture, version, scenario.behavior).use { server ->
                configFile.writeText(
                    JsonOutput.toJson(
                        mapOf(
                            "serverUrl" to server.baseUrl,
                            "resultFile" to resultFile.absolutePath,
                            "installedMarker" to platform.appRoot(installDir).resolve(MARKER_PATH).absolutePath,
                            "install" to platform.installs,
                        ),
                    ),
                )
                val environment = mapOf(
                    "ANIMEKO_DESKTOP_TEST_TASK" to "update-e2e",
                    "ANIMEKO_DESKTOP_TEST_ARGC" to "1",
                    "ANIMEKO_DESKTOP_TEST_ARGV_0" to configFile.absolutePath,
                    "ANIMEKO_DESKTOP_TEST_DATA_DIR" to dataDir.absolutePath,
                    "ANI_DISALLOW_PROJECT_DIRECTORIES_FALLBACK" to "true",
                )
                try {
                    platform.withLaunchdEnvironment(environment) {
                        runApp(environment)
                    }
                } finally {
                    recordedRequests = server.requests.toList()
                }
                verify(server, originalTree)
            }
        }

        private fun runApp(environment: Map<String, String>) {
            val process = ProcessBuilder(platform.executable(installDir).absolutePath)
                .directory(installDir)
                .redirectErrorStream(true)
                .redirectOutput(stdoutFile)
                .apply { environment().putAll(environment) }
                .start()
            if (!process.waitFor(APP_TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IllegalStateException("The app did not exit within $APP_TIMEOUT")
            }
            if (scenario.transfer != Transfer.NONE && platform.installs) {
                awaitUntil("the updated app to report", RELAUNCH_TIMEOUT) { phases().contains("relaunched") }
            }
            awaitUntil("the app and updater processes to exit", EXIT_TIMEOUT) { appProcesses().isEmpty() }
        }

        private fun verify(server: MockUpdateServer, originalTree: Map<String, String>) {
            val reports = reports()
            val phases = reports.map { it.phase }
            val failed = reports.firstOrNull { it.phase == "failed" }
            check(failed == null) { "The app reported a failure: ${failed!!.detail}" }

            val zipRequests = server.requests.filter { it.path == "/${server.zipName}" }
            val manifestRequests = server.requests.filter { it.path == "/${server.zipName}${DESKTOP_PACKAGE_MANIFEST_SUFFIX}" }
            when (scenario.transfer) {
                Transfer.NONE -> {
                    check(phases == listOf("up-to-date")) { "Expected only 'up-to-date', got $phases" }
                    check(zipRequests.isEmpty() && manifestRequests.isEmpty()) { "No package should be requested: ${server.requests}" }
                    check(hashTree(installDir) == originalTree) { "The installed app must not change" }
                    return
                }

                Transfer.DELTA -> {
                    check(manifestRequests.any { it.status == 200 }) { "The manifest was not fetched: ${server.requests}" }
                    val notPartial = zipRequests.filter { it.status != 206 }
                    check(notPartial.isEmpty()) { "Delta download must only use Range requests, got $notPartial" }
                    val served = zipRequests.sumOf { it.bytes }
                    val packageSize = fixture.zip.length()
                    check(served >= fixture.payloadSize) { "Served $served bytes, less than the new payload ${fixture.payloadSize}" }
                    check(served <= packageSize / 5) { "Served $served bytes, expected far less than the package size $packageSize" }
                    logger.lifecycle("[update-e2e] delta transferred $served of $packageSize bytes in ${zipRequests.size} requests")
                }

                Transfer.FULL -> {
                    if (scenario.behavior.serveManifest) {
                        check(manifestRequests.any { it.status == 200 }) { "The manifest was not fetched: ${server.requests}" }
                    } else {
                        check(manifestRequests.any { it.status == 404 }) { "The manifest was not requested: ${server.requests}" }
                    }
                    if (scenario.behavior.corruptFirstRange) {
                        check(server.corruptedRanges == 1) { "Expected one corrupted Range response, got ${server.corruptedRanges}" }
                    }
                    val served = zipRequests.sumOf { it.bytes }
                    check(served >= fixture.zip.length()) { "Served $served bytes, expected the whole package to be downloaded" }
                }
            }

            val downloaded = reports.firstOrNull { it.phase == "downloaded" }
            checkNotNull(downloaded) { "The app never reported 'downloaded': $phases" }
            val expected = hashTree(fixture.dir)
            if (!platform.installs) {
                check(reports.last() === downloaded) { "Expected the app to stop after downloading, got $phases" }
                verifyDownloadedPackage(File(downloaded.detail!!), expected)
                check(hashTree(installDir) == originalTree) { "The installed app must not change without installing" }
                return
            }

            val relaunched = reports.last()
            check(relaunched.phase == "relaunched" && relaunched.detail == NEW_VERSION_MARKER) {
                "The updated app did not report after relaunch: $reports"
            }
            val actual = hashTree(installDir)
            val missing = expected.keys - actual.keys
            val different = expected.keys.intersect(actual.keys).filter { expected[it] != actual[it] }
            val extra = actual.keys - expected.keys
            check(missing.isEmpty() && different.isEmpty() && extra.isEmpty()) {
                "Installed app differs from the new version. Missing: $missing, different: $different, extra: $extra"
            }
            // 更新器交换完会删 `<安装目录>.old`, 删不掉 (比如被杀毒软件占着) 只记日志, 应用下次启动再删, 所以这里不算失败.
            val leftovers = installParent.listFiles().orEmpty().filter { it.name != platform.appDirName }
            if (leftovers.isNotEmpty()) logger.warn("[update-e2e] leftovers next to the installed app: $leftovers")
            check(platform.executable(installDir).canExecute()) { "The updated launcher is not executable" }
        }

        /**
         * 下载到的 (增量拼出或整包下载的) zip 必须与新版本逐文件一致.
         */
        private fun verifyDownloadedPackage(file: File, expected: Map<String, String>) {
            check(file.isFile) { "Downloaded package does not exist: $file" }
            val actual = ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .associate { entry ->
                        entry.name.removePrefix("${platform.appDirName}/") to zip.getInputStream(entry).use { it.digestHex("SHA-256") }
                    }
            }
            val missing = expected.keys - actual.keys
            val different = expected.keys.intersect(actual.keys).filter { expected[it] != actual[it] }
            val extra = actual.keys - expected.keys
            check(missing.isEmpty() && different.isEmpty() && extra.isEmpty()) {
                "Downloaded package differs from the new version. Missing: $missing, different: $different, extra: $extra"
            }
        }

        private fun reports(): List<Report> {
            if (!resultFile.isFile) return emptyList()
            return resultFile.readLines().filter { it.isNotBlank() }.map { line ->
                @Suppress("UNCHECKED_CAST")
                val map = JsonSlurper().parseText(line) as Map<String, Any?>
                Report(map["phase"] as String, map["detail"] as String?)
            }
        }

        private fun phases() = reports().map { it.phase }

        private fun awaitUntil(what: String, timeout: Duration, condition: () -> Boolean) {
            val start = TimeSource.Monotonic.markNow()
            while (!condition()) {
                if (start.elapsedNow() > timeout) throw IllegalStateException("Timed out after $timeout waiting for $what")
                Thread.sleep(500)
            }
        }

        /**
         * 这个场景启动的应用和更新器进程: 可执行文件在安装目录里, 或是 Windows 更新器 (在临时目录运行).
         */
        private fun appProcesses(): List<ProcessHandle> = ProcessHandle.allProcesses().filter { handle ->
            val command = handle.info().command().orElse(null) ?: return@filter false
            command.startsWith(installDir.absolutePath) || platform.isUpdaterProcess(command)
        }.toList()

        fun killLeftoverProcesses() {
            val leftovers = appProcesses()
            if (leftovers.isEmpty()) return
            logger.warn("[update-e2e] killing leftover processes: ${leftovers.map { it.info().command().orElse("?") }}")
            leftovers.forEach { it.destroyForcibly() }
        }

        fun diagnostics(): String = buildString {
            appendLine("[update-e2e] ${scenario.name} diagnostics")
            appendLine("reports: ${runCatching { reports().map { "${it.phase} ${it.detail.orEmpty()}" } }.getOrNull()}")
            appendLine("requests received by the mock server:")
            recordedRequests.forEach { appendLine("  $it") }
            fun tail(name: String, file: File?) {
                if (file == null || !file.isFile) return
                appendLine("---- $name: $file (last $DIAGNOSTIC_LINES lines)")
                file.readLines().takeLast(DIAGNOSTIC_LINES).forEach { appendLine(it) }
            }
            tail("stdout", stdoutFile)
            tail("app log", dataDir.resolve("data/logs").listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() })
            tail("updater log", platform.updaterLog())
            val threads = File(resultFile.path + ".threads.txt")
            if (threads.isFile) {
                appendLine("---- thread dump of the stuck app: $threads")
                appendLine(threads.readText())
            }
        }
    }

    // endregion

    /**
     * 应用通过报告文件写出的一行.
     */
    private class Report(val phase: String, val detail: String?)

    private fun otherVolumeDir(): File? = platform.otherVolumeDir(workDir)

    companion object {
        /**
         * 相对应用根目录 (Windows 是安装目录, macOS 是 `Contents`). 新版本独有, 更新后的应用凭它知道自己是新版本.
         */
        const val MARKER_PATH = "app/update-e2e-version.txt"
        const val NEW_VERSION_MARKER = "update-e2e new version"

        /**
         * 新版本新增的不可压缩数据, 增量下载至少要传这么多.
         */
        const val PAYLOAD_PATH = "app/update-e2e-payload.bin"
        const val PAYLOAD_SIZE = 3 * 1024 * 1024

        val APP_TIMEOUT = 10.minutes
        val RELAUNCH_TIMEOUT = 5.minutes
        val EXIT_TIMEOUT = 2.minutes
        const val DIAGNOSTIC_LINES = 80

        private val SCENARIOS = listOf(
            Scenario(
                name = "delta",
                description = "清单和 Range 都可用, 只下载新版本里变化的部分",
                behavior = ServerBehavior(),
                transfer = Transfer.DELTA,
            ),
            Scenario(
                name = "delta-other-volume",
                description = "暂存目录与安装目录不在同一卷, 更新器逐文件移动",
                behavior = ServerBehavior(),
                transfer = Transfer.DELTA,
                dataDirOnOtherVolume = true,
            ),
            Scenario(
                name = "no-manifest",
                description = "发布包没有清单, 回退整包下载",
                behavior = ServerBehavior(serveManifest = false),
                transfer = Transfer.FULL,
            ),
            Scenario(
                name = "no-range",
                description = "镜像不支持 Range, 回退整包下载",
                behavior = ServerBehavior(honorRange = false),
                transfer = Transfer.FULL,
            ),
            Scenario(
                name = "corrupt-range",
                description = "Range 取回的数据损坏, 校验失败后回退整包下载",
                behavior = ServerBehavior(corruptFirstRange = true),
                transfer = Transfer.FULL,
            ),
            Scenario(
                name = "up-to-date",
                description = "没有新版本, 什么都不下载",
                behavior = ServerBehavior(hasUpdate = false),
                transfer = Transfer.NONE,
            ),
        )
    }

    /**
     * mock 的版本 API 和发布包下载服务器. 按 [ServerBehavior] 决定是否有清单、是否支持 Range、是否给坏数据, 并记录每个请求.
     */
    private class MockUpdateServer(
        private val platform: E2ePlatform,
        private val fixture: NewVersionFixture,
        private val version: String,
        private val behavior: ServerBehavior,
    ) : AutoCloseable {
        class RecordedRequest(val method: String, val path: String, val range: String?, val status: Int, val bytes: Long) {
            override fun toString(): String = "$method $path${range?.let { " Range: $it" }.orEmpty()} -> $status ($bytes bytes)"
        }

        val zipName = platform.zipAssetName(version)
        private val packageName = platform.packageAssetName(version)
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/"
        val requests = CopyOnWriteArrayList<RecordedRequest>()

        @Volatile
        var corruptedRanges = 0
            private set

        init {
            server.executor = Executors.newCachedThreadPool()
            server.createContext("/") { exchange -> handle(exchange) }
            server.start()
        }

        private fun handle(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            val range = exchange.requestHeaders.getFirst("Range")
            var status = 404
            var bytes = 0L
            try {
                when (path) {
                    "/v1/updates/incremental/details" -> {
                        val updates = if (behavior.hasUpdate) {
                            listOf(
                                mapOf(
                                    "version" to version,
                                    "downloadUrlAlternatives" to listOf(baseUrl + packageName),
                                    "publishTime" to System.currentTimeMillis() / 1000,
                                    "description" to "update-e2e $version",
                                ),
                            )
                        } else {
                            emptyList()
                        }
                        val body = JsonOutput.toJson(mapOf("updates" to updates)).toByteArray()
                        exchange.responseHeaders.set("Content-Type", "application/json")
                        status = 200
                        bytes = exchange.send(200, body)
                    }

                    "/$zipName" -> {
                        val (s, b) = servePackage(exchange, range)
                        status = s
                        bytes = b
                    }

                    "/$zipName$DESKTOP_PACKAGE_MANIFEST_SUFFIX" -> {
                        if (behavior.serveManifest) {
                            exchange.responseHeaders.set("Content-Type", "application/json")
                            status = 200
                            bytes = exchange.send(200, fixture.manifest.readBytes())
                        } else {
                            exchange.send(404, ByteArray(0))
                        }
                    }

                    "/$zipName.sha1" -> {
                        status = 200
                        bytes = exchange.send(200, (fixture.sha1Hex + "\n").toByteArray())
                    }

                    else -> exchange.send(404, ByteArray(0))
                }
            } catch (e: Exception) {
                // 客户端中途断开 (比如看到 200 就放弃) 是正常的, 不影响记录.
            } finally {
                requests += RecordedRequest(exchange.requestMethod, path, range, status, bytes)
                exchange.close()
            }
        }

        /**
         * 返回状态码和响应体长度. 支持 `bytes=a-b`、`bytes=a-`、`bytes=-n`; 不支持 Range 时无论如何都返回整个文件.
         */
        private fun servePackage(exchange: HttpExchange, range: String?): Pair<Int, Long> {
            val size = fixture.zip.length()
            val parsed = if (behavior.honorRange && range != null) parseRange(range, size) else null
            if (parsed == null) {
                if (behavior.honorRange) exchange.responseHeaders.set("Accept-Ranges", "bytes")
                return 200 to exchange.sendFile(200, fixture.zip, 0, size, corrupt = false)
            }
            val length = parsed.last - parsed.first + 1
            val corrupt = synchronized(this) {
                if (behavior.corruptFirstRange && corruptedRanges == 0 && length > 1) {
                    corruptedRanges++
                    true
                } else {
                    false
                }
            }
            exchange.responseHeaders.set("Accept-Ranges", "bytes")
            exchange.responseHeaders.set("Content-Range", "bytes ${parsed.first}-${parsed.last}/$size")
            return 206 to exchange.sendFile(206, fixture.zip, parsed.first, length, corrupt)
        }

        private fun parseRange(header: String, size: Long): LongRange? {
            val spec = header.removePrefix("bytes=")
            val first = spec.substringBefore('-').toLongOrNull()
            val last = spec.substringAfter('-').toLongOrNull()
            return when {
                first != null -> first..minOf(last ?: (size - 1), size - 1)
                last != null -> maxOf(size - last, 0)..(size - 1)
                else -> null
            }
        }

        private fun HttpExchange.send(status: Int, body: ByteArray): Long {
            if (requestMethod == "HEAD") {
                responseHeaders.set("Content-Length", body.size.toString())
                sendResponseHeaders(status, -1)
                return 0
            }
            sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            responseBody.use { it.write(body) }
            return body.size.toLong()
        }

        private fun HttpExchange.sendFile(status: Int, file: File, offset: Long, length: Long, corrupt: Boolean): Long {
            if (requestMethod == "HEAD") {
                responseHeaders.set("Content-Length", length.toString())
                sendResponseHeaders(status, -1)
                return 0
            }
            sendResponseHeaders(status, length)
            RandomAccessFile(file, "r").use { input ->
                input.seek(offset)
                responseBody.use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var remaining = length
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) break
                        if (corrupt) for (i in 0 until read) buffer[i] = (buffer[i].toInt() xor 0xFF).toByte()
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
            return length
        }

        override fun close() {
            server.stop(0)
            (server.executor as ExecutorService).shutdownNow()
        }
    }
}

/**
 * 两个平台在安装目录布局、发布包命名和安装方式上的差异.
 */
private sealed class E2ePlatform(val packageOs: String) {
    abstract val appDirName: String
    abstract fun executable(installDir: File): File

    /**
     * 应用文件所在的根: Windows 是安装目录本身, macOS 是 `Contents`.
     */
    abstract fun appRoot(installDir: File): File

    /**
     * 版本 API 返回的安装包资产名. macOS 返回 dmg, 客户端自己换成同名 zip.
     */
    abstract fun packageAssetName(version: String): String
    fun zipAssetName(version: String): String = "ani-$version-$packageOs-${currentReleaseHostArch()}.zip"

    /**
     * 安装器能否自动完成安装并重启. 能的话测试走到新版本启动为止, 否则到下载为止.
     */
    abstract val installs: Boolean

    open fun isUpdaterProcess(command: String): Boolean = false
    open fun updaterLog(): File? = null

    /**
     * 与 [workDir] 不在同一卷上的可写目录, 没有则为 `null`.
     */
    open fun otherVolumeDir(workDir: File): File? = null

    open fun withLaunchdEnvironment(environment: Map<String, String>, block: () -> Unit) = block()

    /**
     * 把打包产物复制成一个安装目录.
     */
    open fun copyApp(source: File, target: File) = copyTree(source, target)

    object Windows : E2ePlatform("windows") {
        override val appDirName: String get() = "Ani"
        override fun executable(installDir: File): File = installDir.resolve("Ani.exe")
        override fun appRoot(installDir: File): File = installDir
        override fun packageAssetName(version: String): String = zipAssetName(version)
        override val installs: Boolean get() = true
        override fun isUpdaterProcess(command: String): Boolean = command.endsWith("ani_update.exe", ignoreCase = true)
        override fun updaterLog(): File = File(System.getProperty("java.io.tmpdir"), "ani-update.log")

        override fun otherVolumeDir(workDir: File): File? {
            val temp = File(System.getProperty("java.io.tmpdir")).absoluteFile
            if (temp.toPath().root == workDir.absoluteFile.toPath().root) return null
            return temp
        }
    }

    object MacOS : E2ePlatform("macos") {
        override val appDirName: String get() = "Ani.app"
        override fun executable(installDir: File): File = installDir.resolve("Contents/MacOS/Ani")
        override fun appRoot(installDir: File): File = installDir.resolve("Contents")
        override fun packageAssetName(version: String): String = "ani-$version-macos-${currentReleaseHostArch()}.dmg"

        /**
         * 现在的安装脚本把新 app 复制到旧 app 上, 遇到运行时里的只读文件 (CEF 的 `gpu_shader_cache.bin`) 就失败, 也会留下
         * 签名无效的 bundle. 整包替换 .app 的安装器合入后改为 `true`, 届时派生新版本后要重新 ad-hoc 签名, 它的校验才能通过.
         */
        override val installs: Boolean get() = false

        override fun updaterLog(): File? = File(System.getProperty("java.io.tmpdir")).listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("ani-macos-update-") }
            ?.maxByOrNull { it.lastModified() }
            ?.resolve("update-output.log")

        /**
         * APFS 的 clonefile (`cp -c`) 复制 600 MB 的 `.app` 只要不到一秒, 逐字节复制要十几秒. 不支持时退回逐字节复制.
         */
        override fun copyApp(source: File, target: File) {
            target.parentFile?.mkdirs()
            val exit = runCatching {
                ProcessBuilder("cp", "-c", "-R", source.absolutePath, target.absolutePath).inheritIO().start().waitFor()
            }.getOrDefault(-1)
            if (exit != 0) {
                target.deleteRecursively()
                copyTree(source, target)
            }
        }

        /**
         * 安装脚本用 `open` 启动新版本, 它从 launchd 而不是本进程继承环境.
         */
        override fun withLaunchdEnvironment(environment: Map<String, String>, block: () -> Unit) {
            for ((key, value) in environment) launchctl("setenv", key, value)
            try {
                block()
            } finally {
                for (key in environment.keys) launchctl("unsetenv", key)
            }
        }

        private fun launchctl(vararg args: String) {
            val exit = ProcessBuilder("launchctl", *args).inheritIO().start().waitFor()
            check(exit == 0) { "launchctl ${args.joinToString(" ")} exited with $exit" }
        }
    }

    companion object {
        fun current(): E2ePlatform = when (getOs()) {
            Os.Windows -> Windows
            Os.MacOS -> MacOS
            else -> throw GradleException("Desktop update E2E supports Windows and macOS only")
        }
    }
}

private fun copyTree(source: File, target: File) {
    val sourcePath = source.toPath()
    val targetPath = target.toPath()
    Files.walk(sourcePath).use { paths ->
        paths.forEach { path ->
            val destination = targetPath.resolve(sourcePath.relativize(path).toString())
            if (Files.isDirectory(path)) {
                Files.createDirectories(destination)
            } else {
                Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

/**
 * 目录下每个文件的 sha256, 键是以 `/` 分隔的相对路径.
 */
private fun hashTree(root: File): Map<String, String> = root.walkTopDown()
    .filter { it.isFile }
    .associate { it.relativeTo(root).invariantSeparatorsPath to it.digestHex("SHA-256") }

private fun File.digestHex(algorithm: String): String = inputStream().use { it.digestHex(algorithm) }

private fun InputStream.digestHex(algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
