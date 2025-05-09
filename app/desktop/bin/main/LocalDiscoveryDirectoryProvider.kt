/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatformDesktop
import uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider
import uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryProviderPriority
import java.io.File

// 在 test-sandbox 运行时使用
class TestDiscoveryDirectoryProvider : DiscoveryDirectoryProvider {
    override fun priority(): Int = DiscoveryProviderPriority.USER_DIR

    override fun directories(): Array<String> {
        val platform = currentPlatformDesktop()
        val os = when (platform) {
            is Platform.MacOS -> "macos"
            is Platform.Windows -> "windows"
            is Platform.Linux -> "linux"
        }

        val arch = when (platform.arch) {
            Arch.X86_64 -> "x64"
            Arch.AARCH64 -> "arm64"

            Arch.ARMV7A, Arch.ARMV8A ->
                throw UnsupportedOperationException("Unsupported architecture: ${platform.arch}")
        }

        val libs = File(System.getProperty("user.dir")).resolve("../appResources/${os}-${arch}/lib")
        if (!libs.exists()) return emptyArray()
        return arrayOf(libs.absolutePath)
    }

    override fun supported(): Boolean = true
}

// 在打包后的 app 使用
class ComposeResourcesDiscoveryDirectoryProvider : DiscoveryDirectoryProvider {
    override fun priority(): Int = DiscoveryProviderPriority.USER_DIR

    override fun directories(): Array<String> {
        val path = System.getProperty("compose.application.resources.dir") ?: return emptyArray()
        val libs = File(path).resolve("lib")
        if (!libs.exists()) return emptyArray()
        return arrayOf(libs.absolutePath)
    }

    override fun supported(): Boolean = true
}