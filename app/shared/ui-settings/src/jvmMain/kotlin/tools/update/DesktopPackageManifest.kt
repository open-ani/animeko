/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 桌面发布包 (zip) 的清单: 每个 entry 的内容哈希和它的压缩数据在发布包中的位置.
 *
 * CI 把它作为发布包的伴随资产发布, 地址是发布包地址加 [MANIFEST_SUFFIX]; 字段与 build-logic 的
 * `desktopPackageManifest.kt` 一一对应. [DesktopDeltaPackageAssembler] 用它判断哪些文件已经在本地,
 * 其余的按 [DesktopPackageEntry.dataOffset] 用 HTTP Range 从发布包取.
 */
@Serializable
class DesktopPackageManifest(
    val formatVersion: Int,
    /** 发布包的字节数, 用来核对镜像返回的是同一个文件. */
    val packageSize: Long,
    /** 顺序与发布包的 central directory 一致. */
    val entries: List<DesktopPackageEntry>,
) {
    companion object {
        const val SUPPORTED_FORMAT_VERSION = 1
        const val MANIFEST_SUFFIX = DeltaPackageAssembler.MANIFEST_SUFFIX

        fun manifestUrlOf(packageUrl: String): String = packageUrl + MANIFEST_SUFFIX

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * 解析清单. 格式版本不认识时抛 [UnsupportedPackageManifestException], 调用方回退到整包下载.
         */
        fun parse(text: String): DesktopPackageManifest {
            val manifest = json.decodeFromString(serializer(), text)
            if (manifest.formatVersion != SUPPORTED_FORMAT_VERSION) {
                throw UnsupportedPackageManifestException(manifest.formatVersion)
            }
            return manifest
        }
    }
}

class UnsupportedPackageManifestException(formatVersion: Int) :
    Exception("Unsupported package manifest format version $formatVersion")

@Serializable
class DesktopPackageEntry(
    /** zip 内的名字, 目录以 `/` 结尾. */
    val name: String,
    /** 压缩方法, `0` 为 STORED, `8` 为 DEFLATED. */
    val method: Int,
    /** 解压后内容的 CRC-32, 无符号. */
    val crc32: Long,
    val compressedSize: Long,
    val size: Long,
    /** 压缩数据在发布包中的绝对偏移. */
    val dataOffset: Long,
    /** zip 记录的 DOS 日期时间, 高 16 位是日期, 低 16 位是时间. */
    val dosTime: Long,
    /** central directory 的外部属性, 高 16 位是 unix mode, 可执行位在其中. */
    val externalAttributes: Long,
    /** 解压后内容的 SHA-256 十六进制; 目录为 `null`. */
    val sha256: String? = null,
) {
    val isDirectory: Boolean get() = name.endsWith("/")
}
