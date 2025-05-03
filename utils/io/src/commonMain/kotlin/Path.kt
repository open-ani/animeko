/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.io

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.jvm.JvmInline

/*
 * 此文件放置了一些有关 Path 的扩展函数, 因为 kotlinx-io 目前比较早期.
 */

/**
 * 获取子目录路径
 *
 * See Java `File.resolve`
 */
fun Path.resolve(vararg parts: String): Path = Path(this, parts = parts)

/**
 * 获取子目录路径
 *
 * See Java `File.resolve`
 */
fun Path.resolve(part: String): Path = Path(this, part)

/**
 * 获取同级目录路径
 *
 * See Java `File.resolveSibling`
 */
fun Path.resolveSibling(part: String): Path = parent?.resolve(part) ?: Path(part)

/**
 * 获取同级目录路径
 *
 * See Java `File.resolveSibling`
 */
fun Path.resolveSibling(part: String, vararg parts: String): Path = parent?.resolve(part, *parts) ?: Path(part, *parts)

val Path.extension: String get() = name.substringAfterLast('.', "")
val SystemPath.extension: String get() = path.extension

val Path.nameWithoutExtension: String get() = name.substringBeforeLast('.', "")
val SystemPath.nameWithoutExtension: String get() = path.nameWithoutExtension

///////////////////////////////////////////////////////////////////////////
// SystemPath
///////////////////////////////////////////////////////////////////////////

/**
 * 将该路径放在 [SystemFileSystem] 中, 以便使用 [exists], [list] 等.
 */
inline val Path.inSystem get() = SystemPath(this)

/**
 * 由于 [Path] 是文件系统无关的, 我们需要显式表示将它于做系统文件系统.
 */
@JvmInline // Path 在绝大部分情况下都是直接使用的, 只有放入 collection 才会 box, 所以这里性能上是有保证的
value class SystemPath @PublishedApi internal constructor(
    val path: Path
) {
    override fun toString(): String = path.toString()
}

val SystemPath.name get() = path.name

expect fun SystemPath.length(): Long

expect fun SystemPath.isDirectory(): Boolean

expect fun SystemPath.isRegularFile(): Boolean

// 什么时候需要全平台实现再写
// expect fun SystemPath.moveDirectoryRecursively(target: SystemPath, visitor: ((SystemPath) -> Unit)? = null)

/**
 * @see Path.resolve
 */
fun SystemPath.resolve(part: String) = path.resolve(part).inSystem

/**
 * @see Path.resolve
 */
fun SystemPath.resolve(vararg parts: String) = path.resolve(*parts).inSystem

/**
 * @see Path.resolveSibling
 */
fun SystemPath.resolveSibling(part: String) = path.resolveSibling(part).inSystem

/**
 * @see Path.resolveSibling
 */
fun SystemPath.resolveSibling(part: String, vararg parts: String) = path.resolveSibling(part, *parts).inSystem

/**
 * @see FileSystem.exists
 */
fun SystemPath.exists(): Boolean = SystemFileSystem.exists(path)

/**
 * @see FileSystem.delete
 * @throws kotlinx.io.files.FileNotFoundException when [this] does not exist and [mustExist] is `true`.
 * @throws kotlinx.io.IOException if deletion failed.
 */
fun SystemPath.delete(mustExist: Boolean = false) = SystemFileSystem.delete(path, mustExist)

@Deprecated("For migration. Use delete() instead", ReplaceWith("this.delete()"), level = DeprecationLevel.ERROR)
fun SystemPath.deleteIfExists() = delete()

/**
 * 目录或文件实际大小 (recursive)
 */
fun SystemPath.actualSize(): Long {
    return if (isRegularFile()) {
        length()
    } else {
        useDirectoryEntries { seq ->
            seq.sumOf { it.actualSize() }
        }
    }
}

fun SystemPath.deleteRecursively(mustExist: Boolean = false) {
    if (isDirectory()) {
        useDirectoryEntries { seq ->
            seq.forEach { it.deleteRecursively(mustExist) }
        }
    }
    delete(mustExist)
}

fun FileSystem.deleteRecursively(path: Path, mustExist: Boolean = false) {
    if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
        SystemFileSystem.list(path).asSequence().forEach { path ->
            deleteRecursively(path, mustExist)
        }
    }
    delete(path, mustExist)
}


/**
 * @see FileSystem.list
 */
fun SystemPath.list(): Collection<Path> = SystemFileSystem.list(path)

/**
 * @see FileSystem.list
 */
@Deprecated("For migration. Use list() instead", ReplaceWith("this.list()"), level = DeprecationLevel.ERROR)
fun SystemPath.listFiles(): Collection<Path> = SystemFileSystem.list(path)

/**
 * @see FileSystem.createDirectories
 */
fun SystemPath.createDirectories(mustCreate: Boolean = false): Unit =
    SystemFileSystem.createDirectories(path, mustCreate)

/**
 * @see FileSystem.atomicMove
 */
fun SystemPath.moveTo(target: Path): Unit = SystemFileSystem.atomicMove(path, target)

/**
 * @see FileSystem.atomicMove
 */
fun SystemPath.moveTo(target: SystemPath): Unit = moveTo(target.path)

/**
 * @see FileSystem.source
 */
fun SystemPath.source() = SystemFileSystem.source(path)

/**
 * @see Source.buffer
 */
fun SystemPath.bufferedSource() = this.source().buffered()

/**
 * @see FileSystem.sink
 */
fun SystemPath.sink(append: Boolean = false) = SystemFileSystem.sink(path, append)

/**
 * @see Sink.buffer
 */
fun SystemPath.bufferedSink(append: Boolean = false) = sink(append).buffered()

/**
 * @see FileSystem.metadataOrNull
 */
fun SystemPath.metadataOrNull() = SystemFileSystem.metadataOrNull(path)

/**
 * Will throw if the file does not exist.
 *
 * @see FileSystem.resolve
 */
fun SystemPath.resolveToAbsolute() = SystemFileSystem.resolve(path)

/**
 * Will not throw if the file does not exist.
 */
expect val SystemPath.absolutePath: String

///////////////////////////////////////////////////////////////////////////
// Extensions
///////////////////////////////////////////////////////////////////////////

expect inline fun <T> SystemPath.useDirectoryEntries(block: (Sequence<SystemPath>) -> T): T

expect fun SystemPath.moveDirectoryRecursively(target: SystemPath, onBeforeMove: ((SystemPath) -> Unit)? = null)

/**
 * 以 UTF-8 读取文件的所有内容
 */
fun SystemPath.readText(): String {
    return this.bufferedSource().use { source ->
        source.readString()
    }
}

/**
 * 读取文件的所有内容
 */
fun SystemPath.readBytes(): ByteArray {
    return this.bufferedSource().use {
        it.readByteArray()
    }
}

/**
 * 写入 UTF-8 字符串到文件, 覆盖文件内容.
 */
fun SystemPath.writeText(string: String, startIndex: Int = 0, endIndex: Int = string.length) {
    this.bufferedSink(append = false).use {
        it.writeString(string, startIndex, endIndex)
    }
}

/**
 * 写入 UTF-8 字符串到文件, 覆盖文件内容.
 */
fun SystemPath.appendText(string: String, startIndex: Int = 0, endIndex: Int = string.length) {
    this.bufferedSink(append = true).use {
        it.writeString(string, startIndex, endIndex)
    }
}

/**
 * 写入 [ByteArray] 到文件, 覆盖文件内容.
 */
fun SystemPath.writeBytes(array: ByteArray, startIndex: Int = 0, endIndex: Int = array.size) {
    this.bufferedSink(append = false).use {
        it.write(array, startIndex, endIndex)
    }
}

/**
 * 追加 [ByteArray] 字符串到文件.
 */
fun SystemPath.appendBytes(array: ByteArray, startIndex: Int = 0, endIndex: Int = array.size) {
    this.bufferedSink(append = true).use {
        it.write(array, startIndex, endIndex)
    }
}

/**
 * 复制文件到目标路径, 将会覆盖目标文件.
 */
fun SystemPath.copyTo(target: SystemPath) {
    this.bufferedSource().use { source ->
        target.bufferedSink(append = false).use { sink ->
            source.transferTo(sink)
        }
    }
}

object SystemPaths // namespace

/**
 * 创建一个临时目录, 将会调用 [createDirectories]
 */
expect fun SystemPaths.createTempDirectory(prefix: String = "tmp"): SystemPath
expect fun SystemPaths.createTempFile(prefix: String = "tmp", suffix: String = ""): SystemPath

///////////////////////////////////////////////////////////////////////////
// 在 kotlinx-io 实现这些功能之前, 我们使用 okio
///////////////////////////////////////////////////////////////////////////
//
//private fun SystemPath.toOkioPath(): okio.Path {
//    return this.toString().toPath()
//}

