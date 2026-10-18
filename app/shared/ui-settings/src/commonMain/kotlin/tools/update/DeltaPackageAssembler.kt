/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import me.him188.ani.utils.io.SystemPath

/**
 * 增量拼包: 按 zip 发布包的清单复用已安装目录里内容相同的文件, 其余 entry 用 HTTP Range 从发布包取,
 * 在本地拼出可交给 [UpdateInstaller.install] 的安装包.
 *
 * 自动更新 (经 [UpdateInstaller.createPackageDownloader]) 和开发者设置的「安装 CI 构建」都用它.
 * 只有能整体替换安装目录的桌面平台提供实现, 见 [UpdateInstaller.createDeltaPackageAssembler].
 */
interface DeltaPackageAssembler {
    /**
     * 拼出 [packageName] 对应的安装包, 放在 [saveDir] 里. 已经拼好且与清单一致的包直接复用.
     *
     * 任何一步失败 (清单不存在、服务器不支持 Range、拼出的内容与清单不符等) 都抛出异常, 不留下半成品;
     * 调用方应回退到整包下载.
     *
     * @param packageName 发布包的文件名. 拼出的安装包名字由它派生, 清理旧安装包时按名字判断归属.
     * @param manifestUrl 清单地址.
     * @param packageUrl 返回发布包地址, 每个 Range 请求前调用一次. 地址会过期时 (例如 GitHub artifact 的直链) 由它换新.
     * @return 拼好的安装包, 是 zip 文件还是解压好的目录由实现决定, 安装器两种都能装.
     */
    suspend fun assemble(
        packageName: String,
        manifestUrl: String,
        packageUrl: suspend () -> String,
        saveDir: SystemPath,
        onProgress: (DeltaPackageProgress) -> Unit = {},
    ): SystemPath

    companion object {
        /**
         * 清单的文件名是发布包文件名加上这个后缀. 与 build-logic 的 `DESKTOP_PACKAGE_MANIFEST_SUFFIX` 一致.
         */
        const val MANIFEST_SUFFIX = ".manifest.json"
    }
}

/**
 * [DeltaPackageAssembler.assemble] 的进度. 比对完本地文件, 知道哪些 entry 要下载之后才开始报告.
 *
 * @param downloadedBytes 已从发布包下载的字节数.
 * @param totalDownloadBytes 需要从发布包下载的字节数, 只计内容有变化的 entry.
 * @param fraction 整体进度 `[0, 1]`, 包括写入复用的本地文件.
 */
data class DeltaPackageProgress(
    val downloadedBytes: Long,
    val totalDownloadBytes: Long,
    val fraction: Float,
)
