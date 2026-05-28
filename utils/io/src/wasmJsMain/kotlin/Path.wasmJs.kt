package me.him188.ani.utils.io

import kotlinx.io.files.SystemFileSystem

actual fun SystemPath.length(): Long = metadataOrNull()?.size ?: 0L
actual fun SystemPath.isDirectory(): Boolean = metadataOrNull()?.isDirectory == true
actual fun SystemPath.isRegularFile(): Boolean = metadataOrNull()?.isRegularFile == true
actual val SystemPath.absolutePath: String get() = path.toString()

actual inline fun <T> SystemPath.useDirectoryEntries(block: (Sequence<SystemPath>) -> T): T =
    block(SystemFileSystem.list(path).asSequence().map { it.inSystem })

actual fun SystemPath.moveDirectoryRecursively(target: SystemPath, onBeforeMove: ((SystemPath) -> Unit)?) {
    onBeforeMove?.invoke(this)
    SystemFileSystem.atomicMove(path, target.path)
}

actual fun SystemPaths.createTempDirectory(prefix: String): SystemPath =
    kotlinx.io.files.Path("/$prefix-${kotlin.random.Random.nextInt()}").inSystem.also { it.createDirectories() }

actual fun SystemPaths.createTempFile(prefix: String, suffix: String): SystemPath =
    kotlinx.io.files.Path("/$prefix-${kotlin.random.Random.nextInt()}$suffix").inSystem
