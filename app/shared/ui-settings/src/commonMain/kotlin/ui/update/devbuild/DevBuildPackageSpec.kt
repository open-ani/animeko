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
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform

/**
 * Build workflow 在每个 commit 上传的安装包 artifact 中, 当前平台可用的那些, 以及从 artifact zip 中取出安装包的方式.
 *
 * artifact 名称与 `.github/workflows/src.main.kts` 中 `ArtifactNames` 及 Android APK 的上传步骤保持一致.
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
) {
    init {
        require(artifactNames.isNotEmpty()) { "artifactNames must not be empty" }
    }

    /**
     * 所有可用的 artifact 名称, 按优先级排序: 先 [artifactNames] 再 [debugArtifactNames].
     */
    val candidateArtifactNames: List<String> get() = artifactNames + debugArtifactNames

    fun isDebugArtifact(name: String): Boolean = name in debugArtifactNames

    /**
     * 下载到本地后安装包的文件名.
     */
    fun packageFileName(shortSha: String): String = "ani-main-$shortSha.${kind.packageExtension}"

    companion object {
        /**
         * 当前平台没有可用的 CI 安装包时返回 `null`.
         */
        fun forPlatform(platform: Platform): DevBuildPackageSpec? = when (platform) {
            is Platform.Windows -> when (platform.arch) {
                Arch.X86_64 -> DevBuildPackageSpec(
                    listOf("ani-windows-portable"),
                    DevBuildPackageKind.WINDOWS_PORTABLE_ZIP,
                )

                Arch.AARCH64 -> DevBuildPackageSpec(
                    listOf("ani-windows-aarch64-portable"),
                    DevBuildPackageKind.WINDOWS_PORTABLE_ZIP,
                )

                Arch.ARMV7A, Arch.ARMV8A -> null
            }

            is Platform.MacOS -> when (platform.arch) {
                Arch.AARCH64 -> DevBuildPackageSpec(
                    listOf("ani-macos-dmg-aarch64"),
                    DevBuildPackageKind.MACOS_DMG,
                )

                // x86_64 的 CI 产物是直接上传的 Ani.app 目录. GitHub Actions artifact 不保留可执行权限, 解压后无法启动.
                Arch.X86_64, Arch.ARMV7A, Arch.ARMV8A -> null
            }

            is Platform.Linux -> when (platform.arch) {
                Arch.X86_64 -> DevBuildPackageSpec(
                    listOf("ani-linux-appimage-x64"),
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
                    listOf("ani-android-$abi-release", "ani-android-universal-release"),
                    DevBuildPackageKind.ANDROID_APK,
                    debugArtifactNames = listOf("ani-android-$abi-debug", "ani-android-universal-debug"),
                )
            }

            Platform.Ios -> null
        }
    }
}

/**
 * artifact zip 中安装包的形态, 决定如何取出安装包以及能否交给 [me.him188.ani.app.tools.update.UpdateInstaller] 自动安装.
 */
enum class DevBuildPackageKind(
    /**
     * 安装包扩展名, 不含点. 也用于在 artifact zip 中按扩展名查找安装包.
     */
    val packageExtension: String,
    /**
     * 安装包是否可由安装器自动安装并重启.
     */
    val supportsAutomaticInstall: Boolean,
) {
    /**
     * artifact zip 内是 `Ani/` 目录, 与 Release 的 Windows 便携版 zip 结构相同, 整个 zip 直接交给安装器.
     */
    WINDOWS_PORTABLE_ZIP(packageExtension = "zip", supportsAutomaticInstall = true),

    /**
     * artifact zip 内含 `Ani-*.dmg`.
     */
    MACOS_DMG(packageExtension = "dmg", supportsAutomaticInstall = true),

    /**
     * artifact zip 内含 `android-default-*-release.apk`.
     */
    ANDROID_APK(packageExtension = "apk", supportsAutomaticInstall = true),

    /**
     * artifact zip 内含 `Animeko-x86_64.AppImage`.
     * Linux 的自动更新依赖 Release 附带的 zsync 元数据, CI 产物没有, 因此只能解压后由用户手动替换当前 AppImage.
     */
    LINUX_APPIMAGE(packageExtension = "AppImage", supportsAutomaticInstall = false),
    ;

    /**
     * 安装包是否需要从 artifact zip 中解压出来. 为 `false` 时 artifact zip 本身就是安装包.
     */
    val extractsFromArchive: Boolean get() = this != WINDOWS_PORTABLE_ZIP
}
