/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.deleteIfExists

/**
 * 可复现 jar 中每个 entry 的时间戳. 与 Gradle 归档任务和 Kotlin 发布产物使用的常量相同:
 * 2 月 1 日而不是 1 月 1 日, 是给用本地时区解读 DOS 时间的工具留出余量, 避免落到 1980 年之前.
 */
val REPRODUCIBLE_JAR_ENTRY_TIME: LocalDateTime = LocalDateTime.of(1980, 2, 1, 0, 0)

private const val MANIFEST_DIR = "META-INF/"
private const val MANIFEST_NAME = "META-INF/MANIFEST.MF"

/**
 * 把 app image 中 `runtime` 之外的所有 jar 重写为可复现的形式, 见 [writeReproducibleJar].
 *
 * 桌面增量更新按文件内容比对已安装文件与发布包, 内容没变的文件不下载. 这要求同一依赖在两次发布里的
 * jar 字节相同. ProGuard 和 [unpackComposeDesktopNativeLibraries] 里的重打包都把写入时刻记成 entry
 * 时间戳, 结果每次发布里所有 jar 都不同, 即使类文件一个字节没变. 这一步必须在所有重打包之后运行.
 *
 * `runtime` 下的 JBR 由 jlink 产生, 不经过 ProGuard; 在 macOS 上它还是一个单独签名的嵌套 bundle, 不碰.
 */
fun AbstractJPackageTask.normalizeJarsForReproducibility() {
    val destination = destinationDir.get().asFile
    val jars = destination.walk()
        .filter { it.isFile && it.extension == "jar" }
        .filterNot { jar -> "runtime" in jar.relativeTo(destination).invariantSeparatorsPath.split('/') }
        .map { it.toPath() }
        .toList()

    // 几百个 jar 重新压缩要几十秒, 彼此独立, 并行处理.
    jars.parallelStream().forEach { jar ->
        val temp = Files.createTempFile(jar.parent, ".${jar.fileName}.", ".reproducible")
        try {
            writeReproducibleJar(jar, temp)
            Files.move(temp, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temp.deleteIfExists()
        }
    }

    logger.lifecycle("Rewrote ${jars.size} jars with fixed entry timestamps and order for reproducible packages.")
}

/**
 * 把 [jar] 重写到 [target], 使得内容相同的 jar 字节也相同:
 *
 * - 每个 entry 的时间戳固定为 [REPRODUCIBLE_JAR_ENTRY_TIME], 不写 extra 字段 (扩展时间戳就在其中);
 * - `META-INF/` 与 `META-INF/MANIFEST.MF` 保持在最前, 其余 entry 按名字排序;
 * - entry 的内容、压缩方法和注释不变. 压缩数据由当前 JDK 的 Deflater 产生, 相同输入得到相同输出.
 *
 * 重复的 entry 名会让写入失败, 这样的 jar 本身就不合法.
 */
fun writeReproducibleJar(jar: Path, target: Path) {
    ZipFile(jar.toFile()).use { source ->
        val entries = source.entries().asSequence().sortedWith(ENTRY_ORDER).toList()
        ZipOutputStream(BufferedOutputStream(Files.newOutputStream(target))).use { output ->
            for (entry in entries) {
                output.putNextEntry(reproducibleEntry(entry))
                if (!entry.isDirectory) {
                    source.getInputStream(entry).use { it.copyTo(output) }
                }
                output.closeEntry()
            }
        }
    }
}

private val ENTRY_ORDER: Comparator<ZipEntry> = compareBy<ZipEntry> {
    when (it.name) {
        MANIFEST_DIR -> 0
        MANIFEST_NAME -> 1
        else -> 2
    }
}.thenBy { it.name }

private fun reproducibleEntry(entry: ZipEntry): ZipEntry = ZipEntry(entry.name).apply {
    method = entry.method
    if (entry.method == ZipEntry.STORED) {
        // STORED entry 的大小和 CRC 必须在写入前给出; DEFLATED 的由输出流重新计算.
        size = entry.size
        compressedSize = entry.compressedSize
        crc = entry.crc
    }
    entry.comment?.let { comment = it }
    // setTimeLocal 直接写 DOS 时间, 不经过系统时区换算, 且在 DOS 范围内不会附加扩展时间戳字段.
    setTimeLocal(REPRODUCIBLE_JAR_ENTRY_TIME)
}
