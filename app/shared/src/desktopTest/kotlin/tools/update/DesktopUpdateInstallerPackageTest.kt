/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.io.files.Path
import me.him188.ani.utils.io.inSystem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopUpdateInstallerPackageTest {
    @Test
    fun `macOS installs dmg and zip packages`() {
        assertTrue(MacOSUpdateInstaller.isInstallablePackage(Path("/downloads/ani.dmg").inSystem))
        assertTrue(MacOSUpdateInstaller.isInstallablePackage(Path("/downloads/Ani-4.0.0.DMG").inSystem))
        assertTrue(MacOSUpdateInstaller.isInstallablePackage(Path("/downloads/ani.zip").inSystem))
        assertFalse(MacOSUpdateInstaller.isInstallablePackage(Path("/downloads/ani.exe").inSystem))
        assertFalse(MacOSUpdateInstaller.isInstallablePackage(Path("/downloads/dmg").inSystem))
    }

    @Test
    fun `windows installs zip packages only`() {
        assertTrue(WindowsUpdateInstaller.isInstallablePackage(Path("C:\\downloads\\ani.zip").inSystem))
        assertTrue(WindowsUpdateInstaller.isInstallablePackage(Path("C:\\downloads\\ani.ZIP").inSystem))
        assertFalse(WindowsUpdateInstaller.isInstallablePackage(Path("C:\\downloads\\ani.dmg").inSystem))
        assertFalse(WindowsUpdateInstaller.isInstallablePackage(Path("C:\\downloads\\ani.exe").inSystem))
    }

    @Test
    fun `linux does not install local packages`() {
        assertTrue(LinuxUpdateInstaller.installablePackageExtensions.isEmpty())
        assertFalse(LinuxUpdateInstaller.isInstallablePackage(Path("/downloads/ani.AppImage").inSystem))
    }
}
