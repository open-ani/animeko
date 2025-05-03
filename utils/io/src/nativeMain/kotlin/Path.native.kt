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
import kotlinx.io.files.SystemFileSystem.metadataOrNull
import kotlinx.io.files.SystemFileSystem.atomicMove
import kotlinx.io.files.SystemFileSystem.copy
import kotlinx.io.files.SystemFileSystem.createDirectories
import kotlinx.io.files.SystemFileSystem.delete
import kotlinx.io.files.SystemFileSystem.list
import kotlinx.io.files.SystemTemporaryDirectory
import me.him188.ani.utils.platform.Uuid
import platform.Foundation.*
import kotlinx.io.files.Path

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
 * 1. We **first try** `FileSystem.atomicMove` (O(1) on the same volume).
 * 2. If that fails (most often because the source & target are on different
 *    iOS “containers”), we **fall back** to a manual *copy-then-delete*.
 *
 * After every file or directory is placed at its new location the optional
 * [onBeforeMove] is invoked with the *destination* path – mirroring the JVM
 * implementation used on desktop.
 */
actual fun SystemPath.moveDirectoryRecursively(
    target: SystemPath,
    onBeforeMove: ((SystemPath) -> Unit)?
) {
    val fs = SystemFileSystem
    val src = path
    val dst = target.path

    if (src == dst) return

    // ────────── fast path: atomic rename ──────────
    runCatching {
        fs.createDirectories(dst.parent ?: return@runCatching) // ensure parent exists
        fs.atomicMove(src, dst)
    }.onSuccess {
        onBeforeMove?.invoke(target)
        return
    }

    // ────────── slow path: copy everything, then delete originals ──────────
    fun copyDirectoryRecursively(from: Path, into: Path) {
        fs.createDirectories(into, mustCreate = false)

        for (child in fs.list(from)) {
            val destChild = into.resolve(child.name)

            if (fs.metadata(child).isDirectory) {
                copyDirectoryRecursively(child, destChild)
                // after the subtree is moved notify visitor
                onBeforeMove?.invoke(SystemPath(destChild))
            } else {
                // file: copy ➜ delete original
                fs.copy(child, destChild, overwrite = true)
                fs.delete(child)
                onBeforeMove?.invoke(SystemPath(destChild))
            }
        }
        // remove the now-empty source dir
        fs.delete(from, mustExist = false)
    }

    copyDirectoryRecursively(src, dst)
    // Finally notify for the root directory.
    onBeforeMove?.invoke(target)
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
