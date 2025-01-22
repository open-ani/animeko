/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import android.content.Context
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.app.ui.framework.takeSnapshot
import me.him188.ani.app.ui.settings.framework.SettingsState
import org.junit.jupiter.api.Test
import org.koin.test.KoinTest
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private typealias MediaCacheSettingsDelegation =
        SettingsState<MediaCacheSettings>

private val INTERNAL_PRIVATE_BASE by lazy { File("/data/data/me.him188.ani/files").absolutePath }
private val EXTERNAL_PRIVATE_BASE by lazy {
    File("/storage/emulated/0/Android/data/me.him188.ani/files").absolutePath
}

class AndroidTorrentCacheViewModelTest : KoinTest {
    @Test
    fun `test default storage location`() = runComposeStateTest {
        val context = createMockContext(externalAvailable = true)
        val permissionManager = createTestPermissionManager(null)
        // 默认情况下，MediaCacheSettings.saveDir 在 App 启动时被赋值为内部私有存储路径
        val mediaCacheSettings by createMockMediaCacheSettingsDelegation()

        assertEquals(true, mediaCacheSettings.value.saveDir?.startsWith(INTERNAL_PRIVATE_BASE))

        val vm = AndroidTorrentCacheViewModel(context, mediaCacheSettings, permissionManager)
        vm.refreshStorageState()
        takeSnapshot()

        val current = vm.currentSelection
        assertIs<AndroidTorrentCacheLocation.InternalPrivate>(current)
        assertEquals(current.path, File(INTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath)

        val locationPresentation = vm.torrentLocationPresentation
        assertEquals(2, locationPresentation.size)
        val internalPrivate = locationPresentation[0].value
        assertIs<AndroidTorrentCacheLocation.InternalPrivate>(internalPrivate)
        assertEquals(File(INTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath, internalPrivate.path)
        val externalPrivate = locationPresentation[1].value
        assertIs<AndroidTorrentCacheLocation.ExternalPrivate>(externalPrivate)
        assertTrue { locationPresentation[1].enabled }
        assertEquals(File(EXTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath, externalPrivate.path)
    }

    @Test
    fun `test default storage location with external unavailable`() = runComposeStateTest {
        val context = createMockContext(externalAvailable = false)
        val permissionManager = createTestPermissionManager(null)
        // 默认情况下，MediaCacheSettings.saveDir 在 App 启动时被赋值为内部私有存储路径
        val mediaCacheSettings by createMockMediaCacheSettingsDelegation()

        assertEquals(true, mediaCacheSettings.value.saveDir?.startsWith(INTERNAL_PRIVATE_BASE))

        val vm = AndroidTorrentCacheViewModel(context, mediaCacheSettings, permissionManager)
        vm.refreshStorageState()
        takeSnapshot()

        val current = vm.currentSelection
        assertIs<AndroidTorrentCacheLocation.InternalPrivate>(current)
        assertEquals(current.path, File(INTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath)

        val locationPresentation = vm.torrentLocationPresentation
        assertEquals(2, locationPresentation.size)
        val internalPrivate = locationPresentation[0].value
        assertIs<AndroidTorrentCacheLocation.InternalPrivate>(internalPrivate)
        assertEquals(File(INTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath, internalPrivate.path)
        val externalPrivate = locationPresentation[1].value
        assertIs<AndroidTorrentCacheLocation.ExternalPrivate>(externalPrivate)
        assertFalse { locationPresentation[1].enabled }
        assertNull(externalPrivate.path)
    }

    @Test
    fun `test media cache settings is external private`() = runComposeStateTest {
        val context = createMockContext(externalAvailable = true)
        val permissionManager = createTestPermissionManager(null)
        // 默认情况下，MediaCacheSettings.saveDir 在 App 启动时被赋值为内部私有存储路径
        val mediaCacheSettings by createMockMediaCacheSettingsDelegation(
            File(EXTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath,
        )

        assertEquals(true, mediaCacheSettings.value.saveDir?.startsWith(EXTERNAL_PRIVATE_BASE))

        val vm = AndroidTorrentCacheViewModel(context, mediaCacheSettings, permissionManager)
        vm.refreshStorageState()
        takeSnapshot()

        val current = vm.currentSelection
        assertIs<AndroidTorrentCacheLocation.ExternalPrivate>(current)
        assertEquals(current.path, File(EXTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath)

        val locationPresentation = vm.torrentLocationPresentation
        assertEquals(2, locationPresentation.size)
        val internalPrivate = locationPresentation[0].value
        assertIs<AndroidTorrentCacheLocation.InternalPrivate>(internalPrivate)
        assertEquals(File(INTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath, internalPrivate.path)
        val externalPrivate = locationPresentation[1].value
        assertIs<AndroidTorrentCacheLocation.ExternalPrivate>(externalPrivate)
        assertTrue { locationPresentation[1].enabled }
        assertEquals(File(EXTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath, externalPrivate.path)
    }

    @Test
    fun `test media cache settings is external private with external unavailable`() = runComposeStateTest {
        val context = createMockContext(externalAvailable = false)
        val permissionManager = createTestPermissionManager(null)
        // 默认情况下，MediaCacheSettings.saveDir 在 App 启动时被赋值为内部私有存储路径
        val mediaCacheSettings by createMockMediaCacheSettingsDelegation(
            File(EXTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath,
        )

        assertEquals(true, mediaCacheSettings.value.saveDir?.startsWith(EXTERNAL_PRIVATE_BASE))

        val vm = AndroidTorrentCacheViewModel(context, mediaCacheSettings, permissionManager)
        vm.refreshStorageState()
        takeSnapshot()

        assertNull(vm.currentSelection)

        val locationPresentation = vm.torrentLocationPresentation
        assertEquals(2, locationPresentation.size)
        val internalPrivate = locationPresentation[0].value
        assertIs<AndroidTorrentCacheLocation.InternalPrivate>(internalPrivate)
        assertEquals(File(INTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath, internalPrivate.path)
        val externalPrivate = locationPresentation[1].value
        assertIs<AndroidTorrentCacheLocation.ExternalPrivate>(externalPrivate)
        assertFalse { locationPresentation[1].enabled }
        assertNull(externalPrivate.path)
    }

    @Test
    fun `test unknown media cache settings`() = runComposeStateTest {
        val context = createMockContext(externalAvailable = true)
        val permissionManager = createTestPermissionManager(null)
        // 默认情况下，MediaCacheSettings.saveDir 在 App 启动时被赋值为内部私有存储路径
        val mediaCacheSettings by createMockMediaCacheSettingsDelegation(
            File("/system/app/unavailable_path").absolutePath,
        )

        val vm = AndroidTorrentCacheViewModel(context, mediaCacheSettings, permissionManager)
        vm.refreshStorageState()
        takeSnapshot()

        assertNull(vm.currentSelection)

        val locationPresentation = vm.torrentLocationPresentation
        assertEquals(2, locationPresentation.size)
        val internalPrivate = locationPresentation[0].value
        assertIs<AndroidTorrentCacheLocation.InternalPrivate>(internalPrivate)
        assertEquals(File(INTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath, internalPrivate.path)
        val externalPrivate = locationPresentation[1].value
        assertIs<AndroidTorrentCacheLocation.ExternalPrivate>(externalPrivate)
        assertTrue { locationPresentation[1].enabled }
        assertEquals(File(EXTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath, externalPrivate.path)
    }
}

private fun createMockContext(externalAvailable: Boolean): Context {
    return mock<Context> {
        on { filesDir } doReturn File(INTERNAL_PRIVATE_BASE)
        on { getExternalFilesDir(null) } doReturn if (externalAvailable) File(EXTERNAL_PRIVATE_BASE) else null
    }
}

private fun createMockMediaCacheSettingsDelegation(
    initialSaveDir: String? = File(INTERNAL_PRIVATE_BASE, DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath
): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, MediaCacheSettingsDelegation>> {
    return PropertyDelegateProvider { _, property ->
        var currentValue = MediaCacheSettings(saveDir = initialSaveDir)

        val mocked = mock<MediaCacheSettingsDelegation> {
            on { value }.doAnswer { currentValue }
            on { update(any()) }.thenAnswer {
                currentValue = it.arguments[0] as MediaCacheSettings
                it.arguments[0]
            }
            onBlocking { updateSuspended(any()) }.thenAnswer {
                currentValue = it.arguments[0] as MediaCacheSettings
                it.arguments[0]
            }
        }
        ReadOnlyProperty { _, _ -> mocked }
    }
}

private fun createTestPermissionManager(
    requestedPath: String?,/* = File(EXTERNAL_SHARED_BASE, "Movies/Ani").absolutePath*/
    getAccessiblePath: String? = null,
    hasPermission: Boolean = requestedPath != null,
): PermissionManager {
    return object : PermissionManager {
        var requested = false

        override fun checkNotificationPermission(context: ContextMP): Boolean {
            error("unreachable test")
        }

        override suspend fun requestNotificationPermission(context: ContextMP): Nothing {
            error("unreachable test")
        }

        override suspend fun requestExternalDocumentTree(context: ContextMP): String? {
            error("unreachable test")
        }
    }
}