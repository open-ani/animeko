/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import me.him188.ani.utils.platform.Uuid
import platform.Foundation.*

actual fun SystemPath.length(): Long = SystemFileSystem.metadataOrNull(path)?.size ?: 0

actual fun SystemPath.isDirectory(): Boolean = SystemFileSystem.metadataOrNull(path)?.isDirectory ?: false

actual fun SystemPath.isRegularFile(): Boolean = SystemFileSystem.metadataOrNull(path)?.isRegularFile ?: false

// actual fun SystemPath.moveDirectoryRecursively(target: SystemPath, visitor: ((SystemPath) -> Unit)?) {
//     // TODO: move directory recursively for native target
// }

actual inline fun <T> SystemPath.useDirectoryEntries(block: (Sequence<SystemPath>) -> T): T {
    return block(SystemFileSystem.list(path).asSequence().map { SystemPath(it) })
}

private fun resolveImpl(parent: String, child: String): String {
    if (child.isEmpty()) return parent
    if (child[0] == '/') {
        if (parent == "/") return child
        return parent + child
    }
    if (parent == "/") return parent + child
    return "$parent/$child"
}

private const val appName = "org.openani.Animeko"

@OptIn(ExperimentalForeignApi::class)
val SystemDocumentDir by lazy {
    Path(
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory.convert(), NSUserDomainMask.convert(), true)
            .firstOrNull()?.toString() ?: error("Cannot get SystemDocumentDir"),
    ).inSystem.resolve(appName)
}

@OptIn(ExperimentalForeignApi::class)
val SystemSupportDir by lazy {
    Path(
        NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory.convert(), NSUserDomainMask.convert(), true)
            .firstOrNull()?.toString() ?: error("Cannot get SystemSupportDir"),
    ).inSystem.resolve(appName)
}

@OptIn(ExperimentalForeignApi::class)
val SystemCacheDir by lazy {
    Path(
        NSSearchPathForDirectoriesInDomains(NSCachesDirectory.convert(), NSUserDomainMask.convert(), true)
            .firstOrNull()?.toString() ?: error("Cannot get SystemCacheDir"),
    ).inSystem.resolve(appName)
}

/**
 * Move a directory tree to [target].
 *
 * 1. First attempt an atomic move (O(1) on the same volume).
 * 2. If that fails (typically due to cross-container moves in iOS),
 *    fall back to a recursive copy-then-delete operation.
 *
 * The optional [onBeforeMove] callback is invoked with the destination path
 * before each move operation.
 *
 * @param target The destination path
 * @param onBeforeMove Optional callback invoked with each destination path before moving
 */
actual fun SystemPath.moveDirectoryRecursively(
    target: SystemPath,
    onBeforeMove: ((SystemPath) -> Unit)?
) {
    val fs = SystemFileSystem
    val src = path
    val dst = target.path

    if (src == dst) return

    // Create parent directories if they don't exist
    fs.createDirectories(dst.parent ?: return)

    try {
        // First try atomic move (optimal if on same volume)
        fs.atomicMove(src, dst)
        onBeforeMove?.invoke(target)
    } catch (e: Throwable) {
        // Fall back to recursive copy-then-delete
        if (isDirectory()) {
            // Create target directory
            fs.createDirectories(dst)
            onBeforeMove?.invoke(target)

            // Copy all contents recursively
            useDirectoryEntries { entries ->
                entries.forEach { childPath ->
                    val childName = childPath.path.name ?: return@forEach
                    val targetChild = target.resolve(childName)

                    if (childPath.isDirectory()) {
                        childPath.moveDirectoryRecursively(targetChild, onBeforeMove)
                    } else {
                        fs.createDirectories(targetChild.path.parent ?: return@forEach)
                        onBeforeMove?.invoke(childPath)
                        fs.atomicMove(childPath.path, targetChild.path)
                    }
                }
            }

            // Delete source directory after copying all contents
            fs.delete(src)
        } else {
            onBeforeMove?.invoke(src.inSystem)
            // For single files, just copy and delete
            fs.atomicMove(src, dst)
            fs.delete(src)
        }
    }
}


actual val SystemPath.absolutePath: String
    get() {
        return resolveImpl("/", path.toString())
    }

actual fun SystemPaths.createTempDirectory(
    prefix: String,
): SystemPath = SystemPath(SystemTemporaryDirectory.resolve(prefix + Uuid.randomString().take(8))).apply {
    createDirectories()
}

actual fun SystemPaths.createTempFile(
    prefix: String,
    suffix: String
): SystemPath = SystemPath(SystemTemporaryDirectory.resolve(prefix + Uuid.randomString().take(8) + suffix)).apply {
    writeBytes(byteArrayOf())
}
