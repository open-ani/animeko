/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException

/**
 * 把下载好的 zip 安装包解压成安装器能直接换进安装目录的目录, 实现桌面端的 [UpdateInstaller.prepareForInstallation].
 *
 * 解到 zip 旁边的 `<zip>.staging`, 与增量拼包的目录同名同形: 自动更新的 zip 在更新目录里, 按名字清理旧安装包时顺带覆盖它;
 * 安装器安装时把目录整个移走. 先解到 `.part` 目录, 核对通过后再改名, 不留下看起来完整的半成品. 已有的同名目录直接
 * 删掉重解: 没有清单无法判断它是否完整, 重新解一次只要几秒.
 */
internal object DesktopPackagePreparation {
    private val logger = logger<DesktopPackagePreparation>()

    /**
     * Windows: 去掉发布包顶层的应用目录, 解出来的就是安装目录的内容, 更新器 `tools/ani-update` 以 `--staging` 整个换进去,
     * 不再由它在用户等待时解压.
     */
    suspend fun prepareWindowsPackage(zip: File, appExecutable: String): File = stage(zip) { into ->
        extractZip(zip, into, stripCommonRoot = true)
        if (!into.resolve(appExecutable).isFile) {
            throw PackagePreparationException("$appExecutable not found in ${zip.name}")
        }
    }

    /**
     * macOS: 用 `unzip` 解压, 解出来的目录下是 `.app`; 随即校验签名, 安装脚本对这样的目录不再校验.
     *
     * 不用更快的 `ditto -x -k`: Gradle 打的发布包外部属性里只有权限位没有文件类型位, ditto 不认, 解出来的启动器没有可执行权限;
     * `unzip` 按权限位恢复. 反过来 ditto 或 Finder 打的 zip (拖拽安装时可能遇到) 带 AppleDouble 文件 (`._*`、`__MACOSX/`),
     * `unzip` 把它们当普通文件解出来, 留在 `.app` 里会破坏签名封印, 解完删掉; 它们只存放扩展属性, 应用用不到.
     */
    suspend fun prepareMacPackage(zip: File, appName: String): File = stage(zip) { into ->
        val result = runCommand("unzip", "-q", zip.absolutePath, "-d", into.absolutePath)
        check(result.exitCode == 0) { "Failed to extract ${zip.name}: ${result.output.trim()}" }
        deleteAppleDoubleFiles(into)
        val app = into.resolve(appName)
        if (!app.isDirectory) throw PackagePreparationException("$appName not found in ${zip.name}")
        verifyMacAppSignature(app)
    }

    private fun deleteAppleDoubleFiles(root: File) {
        root.walkTopDown()
            .onEnter { dir -> if (dir.name == "__MACOSX") dir.deleteRecursively().let { false } else true }
            .filter { it.isFile && it.name.startsWith("._") }
            .forEach { it.delete() }
    }

    /**
     * 校验 [app] 的签名, 与安装脚本替换 `.app` 前对 zip 解出的内容做的检查相同. 不通过时抛 [PackagePreparationException]:
     * 内容已经按清单或校验和核对过, 是发布包本身的签名有问题, 重新下载也没用.
     */
    suspend fun verifyMacAppSignature(app: File) {
        val result = runCommand("codesign", "--verify", "--deep", "--strict", app.absolutePath)
        if (result.exitCode != 0) {
            throw PackagePreparationException("Code signature of ${app.name} is invalid: ${result.output.trim()}")
        }
    }

    fun stagingDirOf(zip: File): File = File(zip.path + PackageOutput.Directory.SUFFIX)

    private suspend fun stage(zip: File, fill: suspend (File) -> Unit): File = withContext(Dispatchers.IO_) {
        val staging = stagingDirOf(zip)
        val part = File(staging.path + ".part")
        part.deleteRecursively()
        staging.deleteRecursively()
        check(part.mkdirs()) { "Cannot create $part" }
        try {
            fill(part)
            check(part.renameTo(staging)) { "Failed to move $part to $staging" }
        } finally {
            part.deleteRecursively()
        }
        logger.info { "Prepared ${zip.name} for installation: $staging" }
        staging
    }

    /**
     * 用 [ZipFile] 把 [zip] 解到 [into], 逐 entry 核对 CRC. [stripCommonRoot] 为 true 时去掉所有 entry 共有的顶层目录,
     * 规则与 Windows 更新器 `tools/ani-update` 的 `extract_archive` 相同. 不恢复 unix 权限, 只在 Windows 上用.
     */
    fun extractZip(zip: File, into: File, stripCommonRoot: Boolean) {
        ZipFile(zip).use { archive ->
            val entries = archive.entries().toList()
            val root = if (stripCommonRoot) commonRoot(entries.map { it.name }) else null
            val intoCanonical = into.canonicalPath + File.separator
            val buffer = ByteArray(64 * 1024)
            for (entry in entries) {
                val relative = (if (root == null) entry.name else entry.name.removePrefix("$root/")).trimEnd('/')
                if (relative.isEmpty()) continue
                val target = File(into, relative)
                check(target.canonicalPath.startsWith(intoCanonical)) { "Unsafe entry name: ${entry.name}" }
                if (entry.isDirectory) {
                    check(target.isDirectory || target.mkdirs()) { "Cannot create directory $target" }
                    continue
                }
                target.parentFile?.mkdirs()
                val crc = CRC32()
                archive.getInputStream(entry).use { input ->
                    target.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            crc.update(buffer, 0, read)
                        }
                    }
                }
                check(entry.crc < 0 || crc.value == entry.crc) { "CRC of ${entry.name} does not match, ${zip.name} is corrupted" }
            }
        }
    }

    /**
     * 所有 entry 名字共有的第一段. 有 entry 直接在顶层 (名字里没有 `/`), 或第一段不一致时为 `null`.
     */
    fun commonRoot(names: List<String>): String? {
        var root: String? = null
        for (name in names) {
            val first = name.substringBefore('/', "")
            if (first.isEmpty()) return null
            if (root == null) root = first else if (root != first) return null
        }
        return root
    }

    class CommandResult(val exitCode: Int, val output: String)

    /**
     * 运行 [command], 等它结束, 返回退出码和合并的标准输出与错误输出. 协程取消时结束进程.
     */
    suspend fun runCommand(vararg command: String): CommandResult {
        val process = withContext(Dispatchers.IO_) {
            ProcessBuilder(*command).redirectErrorStream(true).start()
        }
        try {
            return runInterruptible(Dispatchers.IO_) {
                val output = process.inputStream.bufferedReader().readText()
                CommandResult(process.waitFor(), output)
            }
        } catch (e: CancellationException) {
            process.destroyForcibly()
            throw e
        }
    }
}
