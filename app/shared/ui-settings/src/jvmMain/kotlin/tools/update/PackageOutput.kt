/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import java.io.File
import java.util.zip.ZipFile

/**
 * [DesktopDeltaPackageAssembler] 拼出的包的形态, 由安装器按自己能安装什么来选.
 */
sealed interface PackageOutput {
    /**
     * 拼好的包在下载目录里的名字, [packageName] 是发布包资产名. 名字里保留发布包名, 清理旧安装包时按它判断归属.
     */
    fun targetName(packageName: String): String

    /**
     * 逐 entry 核对 [target] 与清单, 不一致时抛 [IllegalStateException].
     */
    fun verify(target: File, manifest: DesktopPackageManifest)

    /**
     * 与发布包等价的 zip. 压缩字节可能与发布包不同, 但 entry 的名字、顺序、内容、权限和时间相同.
     */
    data object Zip : PackageOutput {
        override fun targetName(packageName: String): String = packageName

        override fun verify(target: File, manifest: DesktopPackageManifest) {
            ZipFile(target).use { zip ->
                val entries = zip.entries().toList()
                check(entries.map { it.name } == manifest.entries.map { it.name }) {
                    "Entries of ${target.name} do not match the manifest"
                }
                for ((actual, expected) in entries.zip(manifest.entries)) {
                    if (expected.isDirectory) continue
                    check(actual.size == expected.size && actual.crc == expected.crc32) {
                        "Entry ${expected.name} has size=${actual.size} crc=${actual.crc}, " +
                                "expected size=${expected.size} crc=${expected.crc32}"
                    }
                    val sha256 = zip.getInputStream(actual).use { it.sha256Hex() }
                    check(sha256 == expected.sha256) { "Entry ${expected.name} content does not match the manifest" }
                }
            }
        }
    }

    /**
     * 解压好的新版本目录, 发布包顶层的应用目录这一层已去掉. 安装器可以把整个目录换进安装目录, 省掉再解压一次.
     */
    data object Directory : PackageOutput {
        const val SUFFIX = ".staging"

        override fun targetName(packageName: String): String = packageName + SUFFIX

        override fun verify(target: File, manifest: DesktopPackageManifest) =
            StagingDirectoryWriter.verify(target, manifest)
    }
}

internal fun PackageOutput.createWriter(target: File): PackageWriter = when (this) {
    PackageOutput.Zip -> PackageZipWriter(target.outputStream())
    PackageOutput.Directory -> StagingDirectoryWriter(target.apply { mkdirs() })
}
