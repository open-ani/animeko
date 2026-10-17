/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import androidx.compose.runtime.Immutable
import me.him188.ani.app.tools.update.DeltaPackageAssembler
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform

/**
 * Build workflow 在每个 commit 上传的安装包 artifact 中, 当前平台可用的那些.
 *
 * 安装包都以 `archive: false` 上传, 下载得到的就是安装包本身, artifact 名就是它的文件名.
 * 名称与 `.github/workflows/src.main.kts` 中的 `ArtifactNames` 保持一致.
 */
@Immutable
data class DevBuildPackageSpec(
    /**
     * 候选 artifact 名称, 靠前的优先. Android 在架构专用包之后回退到 universal 包.
     */
    val artifactNames: List<String>,
    val kind: DevBuildPackageKind,
    /**
     * [artifactNames] 都不存在时退而求其次的 debug 包名称, 靠前的优先. 只有 Android 有:
     * pull_request 事件触发的构建 (来自 fork 的 PR 只有这种) 没有签名密钥, 不产生 release APK.
     */
    val debugArtifactNames: List<String> = emptyList(),
    /**
     * 用于增量拼包的 zip 发布包 artifact 的名称, 清单 artifact 的名称是它加 [DeltaPackageAssembler.MANIFEST_SUFFIX].
     * Windows 和 macOS x64 的安装包就是它; macOS aarch64 的安装包是 dmg, zip 另外上传. 不支持增量拼包时为 `null`.
     */
    val deltaPackageName: String? = null,
) {
    init {
        require(artifactNames.isNotEmpty()) { "artifactNames must not be empty" }
    }

    val deltaManifestName: String? get() = deltaPackageName?.plus(DeltaPackageAssembler.MANIFEST_SUFFIX)

    /**
     * 所有可用的 artifact 名称, 按优先级排序: 先 [artifactNames] 再 [debugArtifactNames].
     */
    val candidateArtifactNames: List<String> get() = artifactNames + debugArtifactNames

    fun isDebugArtifact(name: String): Boolean = name in debugArtifactNames

    /**
     * 下载到本地后安装包的文件名.
     */
    fun packageFileName(shortSha: String): String = "ani-main-$shortSha.${kind.packageExtension}"

    /**
     * 增量拼出的安装包的文件名. 发布包总是 zip, 与 [kind] 无关.
     */
    fun deltaPackageFileName(shortSha: String): String = "ani-main-$shortSha.zip"

    companion object {
        /**
         * 当前平台没有可用的 CI 安装包时返回 `null`.
         */
        fun forPlatform(platform: Platform): DevBuildPackageSpec? = when (platform) {
            is Platform.Windows -> when (platform.arch) {
                Arch.X86_64 -> desktopZip("ani-windows-x64.zip")
                Arch.AARCH64 -> desktopZip("ani-windows-aarch64.zip")
                Arch.ARMV7A, Arch.ARMV8A -> null
            }

            is Platform.MacOS -> when (platform.arch) {
                Arch.AARCH64 -> DevBuildPackageSpec(
                    listOf("ani-macos-aarch64.dmg"),
                    DevBuildPackageKind.MACOS_DMG,
                    deltaPackageName = "ani-macos-aarch64.zip",
                )

                Arch.X86_64 -> desktopZip("ani-macos-x64.zip")
                Arch.ARMV7A, Arch.ARMV8A -> null
            }

            is Platform.Linux -> when (platform.arch) {
                Arch.X86_64 -> DevBuildPackageSpec(
                    listOf("ani-linux-x64.AppImage"),
                    DevBuildPackageKind.LINUX_APPIMAGE,
                )

                Arch.AARCH64, Arch.ARMV7A, Arch.ARMV8A -> null
            }

            is Platform.Android -> {
                val abi = when (platform.arch) {
                    Arch.ARMV8A, Arch.AARCH64 -> "arm64-v8a"
                    Arch.ARMV7A -> "armeabi-v7a"
                    Arch.X86_64 -> "x86_64"
                }
                DevBuildPackageSpec(
                    listOf("ani-android-$abi-release.apk", "ani-android-universal-release.apk"),
                    DevBuildPackageKind.ANDROID_APK,
                    debugArtifactNames = listOf("ani-android-$abi-debug.apk", "ani-android-universal-debug.apk"),
                )
            }

            Platform.Ios -> null
        }

        private fun desktopZip(name: String) = DevBuildPackageSpec(
            listOf(name),
            DevBuildPackageKind.DESKTOP_ZIP,
            deltaPackageName = name,
        )
    }
}

/**
 * 安装包的形态, 决定本地文件的扩展名以及能否交给 [me.him188.ani.app.tools.update.UpdateInstaller] 自动安装.
 */
enum class DevBuildPackageKind(
    /**
     * 安装包扩展名, 不含点.
     */
    val packageExtension: String,
    /**
     * 安装包是否可由安装器自动安装并重启.
     */
    val supportsAutomaticInstall: Boolean,
) {
    /**
     * 与 Release 的 zip 发布包格式相同: Windows 里是 `Ani/` 目录, macOS 里是 `Ani.app`.
     */
    DESKTOP_ZIP(packageExtension = "zip", supportsAutomaticInstall = true),

    MACOS_DMG(packageExtension = "dmg", supportsAutomaticInstall = true),

    ANDROID_APK(packageExtension = "apk", supportsAutomaticInstall = true),

    /**
     * Linux 的自动更新依赖 Release 附带的 zsync 元数据, CI 产物没有, 因此只能由用户手动替换当前 AppImage.
     */
    LINUX_APPIMAGE(packageExtension = "AppImage", supportsAutomaticInstall = false),
}
