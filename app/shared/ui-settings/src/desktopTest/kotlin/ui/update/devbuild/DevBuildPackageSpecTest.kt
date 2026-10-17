/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DevBuildPackageSpecTest {
    @Test
    fun `windows installs and assembles from the zip package of its arch`() {
        assertEquals(
            DevBuildPackageSpec(
                listOf("ani-windows-x64.zip"),
                DevBuildPackageKind.DESKTOP_ZIP,
                deltaPackageName = "ani-windows-x64.zip",
            ),
            DevBuildPackageSpec.forPlatform(Platform.Windows(Arch.X86_64)),
        )
        assertEquals(
            DevBuildPackageSpec(
                listOf("ani-windows-aarch64.zip"),
                DevBuildPackageKind.DESKTOP_ZIP,
                deltaPackageName = "ani-windows-aarch64.zip",
            ),
            DevBuildPackageSpec.forPlatform(Platform.Windows(Arch.AARCH64)),
        )
    }

    @Test
    fun `macos aarch64 installs the dmg and assembles from the zip package`() {
        val spec = DevBuildPackageSpec.forPlatform(Platform.MacOS(Arch.AARCH64))
        assertEquals(
            DevBuildPackageSpec(
                listOf("ani-macos-aarch64.dmg"),
                DevBuildPackageKind.MACOS_DMG,
                deltaPackageName = "ani-macos-aarch64.zip",
            ),
            spec,
        )
        assertEquals("ani-macos-aarch64.zip.manifest.json", spec!!.deltaManifestName)
        assertEquals("ani-main-aaaaaaaa.zip", spec.deltaPackageFileName("aaaaaaaa"))
    }

    @Test
    fun `macos x64 installs and assembles from the zip package`() {
        assertEquals(
            DevBuildPackageSpec(
                listOf("ani-macos-x64.zip"),
                DevBuildPackageKind.DESKTOP_ZIP,
                deltaPackageName = "ani-macos-x64.zip",
            ),
            DevBuildPackageSpec.forPlatform(Platform.MacOS(Arch.X86_64)),
        )
    }

    @Test
    fun `linux x64 downloads the appimage for manual install`() {
        val spec = DevBuildPackageSpec.forPlatform(Platform.Linux(Arch.X86_64))
        assertEquals(DevBuildPackageSpec(listOf("ani-linux-x64.AppImage"), DevBuildPackageKind.LINUX_APPIMAGE), spec)
        assertEquals(false, spec!!.kind.supportsAutomaticInstall)
        assertNull(spec.deltaManifestName)
        assertNull(DevBuildPackageSpec.forPlatform(Platform.Linux(Arch.AARCH64)))
    }

    @Test
    fun `android prefers the abi specific release apk and falls back to universal`() {
        val spec = DevBuildPackageSpec.forPlatform(Platform.Android(Arch.ARMV8A))!!
        assertEquals(listOf("ani-android-arm64-v8a-release.apk", "ani-android-universal-release.apk"), spec.artifactNames)
        assertEquals(listOf("ani-android-arm64-v8a-debug.apk", "ani-android-universal-debug.apk"), spec.debugArtifactNames)
        assertEquals(
            listOf("ani-android-armeabi-v7a-release.apk", "ani-android-universal-release.apk"),
            DevBuildPackageSpec.forPlatform(Platform.Android(Arch.ARMV7A))!!.artifactNames,
        )
        assertEquals(
            listOf("ani-android-x86_64-release.apk", "ani-android-universal-release.apk"),
            DevBuildPackageSpec.forPlatform(Platform.Android(Arch.X86_64))!!.artifactNames,
        )
        assertEquals(DevBuildPackageKind.ANDROID_APK, spec.kind)
    }

    @Test
    fun `ios is unsupported`() {
        assertNull(DevBuildPackageSpec.forPlatform(Platform.Ios))
    }

    @Test
    fun `package file name uses the short sha and the package extension`() {
        val spec = DevBuildPackageSpec.forPlatform(Platform.MacOS(Arch.AARCH64))!!
        assertEquals("ani-main-28ec14ac.dmg", spec.packageFileName("28ec14ac"))
        assertEquals(
            "ani-main-28ec14ac.AppImage",
            DevBuildPackageSpec.forPlatform(Platform.Linux(Arch.X86_64))!!.packageFileName("28ec14ac"),
        )
    }
}
