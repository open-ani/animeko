package me.him188.ani.utils.io

import kotlinx.io.files.Path
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.useDirectoryEntries
import java.nio.file.Path as NioPath


fun Path.toFile(): File = File(this.toString())
fun SystemPath.toFile(): File = path.toFile()

fun Path.toNioPath(): NioPath = Paths.get(this.toString())
fun SystemPath.toNioPath(): NioPath = path.toNioPath()

fun NioPath.toKtPath(): Path = Path(this.toString())
fun File.toKtPath(): Path = Path(this.toString())

actual inline fun <T> SystemPath.useDirectoryEntries(block: (Sequence<SystemPath>) -> T): T {
    return this.toNioPath().useDirectoryEntries { seq ->
        block(seq.map { SystemPath(it.toKtPath()) })
    }
}

actual fun SystemPath.length(): Long {
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    return path.file.length()
}

actual fun SystemPath.isDirectory(): Boolean {
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    return path.file.isDirectory
}

actual fun SystemPath.isRegularFile(): Boolean {
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    return path.file.isFile
}

fun SystemPath.moveDirectoryRecursively(target: SystemPath, visitor: ((SystemPath) -> Unit)?) {
    val sourceDir = toNioPath()
    val targetDir = target.toNioPath()

    if (!Files.exists(targetDir)) {
        Files.createDirectories(targetDir)
    }

    Files.walkFileTree(
        sourceDir,
        object : SimpleFileVisitor<NioPath>() {
            private fun NioPath.deleteRecursively() {
                if (Files.isDirectory(this)) {
                    useDirectoryEntries { entries ->
                        entries.forEach { it.deleteRecursively() }
                    }
                }
                Files.delete(this)
            }

            override fun visitFile(file: NioPath?, attrs: BasicFileAttributes?): FileVisitResult {
                if (file == null) return FileVisitResult.CONTINUE
                val targetFile = targetDir.resolve(sourceDir.relativize(file))

                Files.createDirectories(targetFile.parent)
                visitor?.invoke(file.toKtPath().inSystem)
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                Files.delete(file)

                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: NioPath?, exc: IOException?): FileVisitResult {
                if (dir == null) return FileVisitResult.CONTINUE

                val targetSubDir = targetDir.resolve(sourceDir.relativize(dir))
                if (!Files.exists(targetSubDir)) {
                    Files.copy(dir, targetSubDir, StandardCopyOption.REPLACE_EXISTING)
                }
                if (Files.exists(dir)) {
                    dir.deleteRecursively()
                }
                return FileVisitResult.CONTINUE
            }
        },
    )
}

actual val SystemPath.absolutePath: String
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    get() = path.file.absolutePath

actual fun SystemPaths.createTempDirectory(
    prefix: String,
): SystemPath = kotlin.io.path.createTempDirectory(prefix = prefix).toKtPath().inSystem

actual fun SystemPaths.createTempFile(
    prefix: String,
    suffix: String
): SystemPath = kotlin.io.path.createTempFile(prefix = prefix, suffix = suffix).toKtPath().inSystem
