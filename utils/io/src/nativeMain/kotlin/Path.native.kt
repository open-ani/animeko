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
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

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
