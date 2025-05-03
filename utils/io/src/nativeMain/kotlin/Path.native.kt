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
import kotlinx.cinterop.*

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
 * Recursively move (i.e. rename-if-possible, otherwise copy-then-delete) the directory represented
 * by this [SystemPath] to [target].
 *
 * If [visitor] is supplied it is invoked *once* with the *target* directory after the move finishes
 * (useful for cache invalidation, DB re-opening, etc.).
 *
 * ⚠️  Preconditions:
 *  * `this` **must** be a directory.
 *  * The call is **best-effort** – any Foundation error bubbles up as `IllegalStateException`.
 */
// todo: test
@OptIn(ExperimentalForeignApi::class)
actual fun SystemPath.moveDirectoryRecursively(
    target: SystemPath,
    visitor: ((SystemPath) -> Unit)?
) {
    val fm = NSFileManager.defaultManager
    val src = path.toString()
    val dst = target.path.toString()

    // Trivial no-op.
    if (src == dst) return

    memScoped {
        val errPtr = alloc<ObjCObjectVar<NSError?>> { }

        // Create the parent directories for the destination so that the rename can succeed.
        val dstParent = target.path.parent?.toString() ?: "/"
        if (!fm.fileExistsAtPath(dstParent)) {
            fm.createDirectoryAtPath(dstParent, /*withIntermediateDirectories = */ true, null, errPtr.ptr)
            errPtr.value?.let { throw IllegalStateException(it.localizedDescription) }
        }

        // ---------- 1) Cheap path : atomic rename ----------
        if (fm.moveItemAtPath(src, dst, errPtr.ptr)) {
            visitor?.invoke(target)
            return
        }

        // If we are here the rename failed – most often “cross-device link”.
        // Clear the error object so we can reuse the pointer.
        errPtr.value = null

        // ---------- 2) Manual copy-then-delete fallback ----------
        // (a) Create the destination directory.
        fm.createDirectoryAtPath(dst, true, null, errPtr.ptr)
        errPtr.value?.let { throw IllegalStateException(it.localizedDescription) }

        // (b) Enumerate source tree and copy every item.
        val enumerator: NSDirectoryEnumerator =
            fm.enumeratorAtPath(src) ?: error("Cannot enumerate $src")

        var next = enumerator.nextObject() as NSString?
        while (next != null) {
            val rel = next as String
            val fromPath = "$src/$rel"
            val toPath = "$dst/$rel"

            // Directory? -> create, otherwise copy file
            val isDir = enumerator.fileAttributes()?.get("NSFileType") as? String == NSFileTypeDirectory
            if (isDir) {
                fm.createDirectoryAtPath(toPath, true, null, errPtr.ptr)
            } else {
                fm.copyItemAtPath(fromPath, toPath, errPtr.ptr)
            }
            errPtr.value?.let { throw IllegalStateException(it.localizedDescription) }

            next = enumerator.nextObject() as NSString?
        }

        // (c) Delete the original tree – we only attempt this after a *complete* copy.
        fm.removeItemAtPath(src, errPtr.ptr)
        errPtr.value?.let { throw IllegalStateException(it.localizedDescription) }
    }

    visitor?.invoke(target)
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
