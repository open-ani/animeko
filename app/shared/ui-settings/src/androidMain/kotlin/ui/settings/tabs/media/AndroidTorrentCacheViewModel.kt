/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import androidx.annotation.UiThread
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.storage.DataStoreMediaCacheStorage
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SingleSelectionElement
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.moveDirectoryRecursively
import me.him188.ani.utils.io.name
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.warn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

const val DEFAULT_TORRENT_CACHE_DIR_NAME = "torrent-caches"

class AndroidTorrentCacheViewModel(
    private val context: ContextMP,
    private val mediaCacheSettings: SettingsState<MediaCacheSettings>,
    private val permissionManager: PermissionManager,
) : AbstractViewModel(), KoinComponent {
    private val cacheManager: MediaCacheManager by inject()
    private val appTerminator: AppTerminator by inject()

    private val defaultTorrentCacheDir by lazy {
        context.filesDir.resolve(DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath
    }

    var torrentLocationPresentation: List<SingleSelectionElement<AndroidTorrentCacheLocation>>
            by mutableStateOf(emptyList())
        private set
    var currentSelection: AndroidTorrentCacheLocation? by mutableStateOf(null)
        private set
    val currentSelectionIndex by derivedStateOf {
        val current = currentSelection ?: return@derivedStateOf -1
        torrentLocationPresentation.indexOfFirst { it.value == current }
    }

    /**
     * 申请外部共享目录授权时需要弹出对话框等待用户的回应
     */
    private var selectExternalSharedStorageRequest: CompletableDeferred<Boolean>? by mutableStateOf(null)
    val showExternalSharedStorageRequestDialog by derivedStateOf { selectExternalSharedStorageRequest != null }

    private val json by lazy { Json { ignoreUnknownKeys = true } }
    private val migrationTasker = MonoTasker(backgroundScope)
    val showMigrationDialog by derivedStateOf { migrationTasker.isRunning }
    var migrationStatus: MigrationStatus by mutableStateOf(MigrationStatus.Init)
        private set
    private var lastMigrationError: Throwable? = null

    /**
     * 获取 [`内部私有存储`][android.content.Context.getFilesDir] 和 [`外部私有存储`][android.content.Context.getExternalFilesDir]
     * 的可用状态, 根据 [MediaCacheSettings.saveDir] 判断当前的的选择，并更新至 [torrentLocationPresentation] 和 [currentSelection] UI 状态。
     *
     * @see android.content.Context.getFilesDir 内部私有存储
     * @see android.content.Context.getExternalFilesDir 外部私有存储
     */
    @UiThread
    fun refreshStorageState() {
        val settings by mediaCacheSettings
        val currentDir = settings.saveDir ?: defaultTorrentCacheDir

        val internalPrivateBasePath = context.filesDir.absolutePath
        val externalPrivateBasePath = context.getExternalFilesDir(null)?.absolutePath

        val resultList = mutableListOf<SingleSelectionElement<AndroidTorrentCacheLocation>>()

        currentSelection = if (currentDir == null) {
            // settings preference 设定的保存目录为空，这是不可能的
            // 因为在 Android 端启动时会默认设置为内部私有目录
            val value = constructInternalPrivateLocation()
            resultList.add(SingleSelectionElement(value, true))
            val externalPrivate = getExternalPrivateLocation()
            // 如果外部私有存储不可用，则选择列表中将不允许选择这一项
            resultList.add(SingleSelectionElement(externalPrivate, externalPrivate.path != null))

            value
        } else if (currentDir.startsWith(internalPrivateBasePath)) {
            // 设置保存的是内部私有目录
            val value = AndroidTorrentCacheLocation.InternalPrivate(currentDir)
            resultList.add(SingleSelectionElement(value, true))
            val externalPrivate = getExternalPrivateLocation()
            resultList.add(SingleSelectionElement(externalPrivate, externalPrivate.path != null))

            value
        } else if (externalPrivateBasePath != null && currentDir.startsWith(externalPrivateBasePath)) {
            // 设置保存的是外部私有目录
            val value = AndroidTorrentCacheLocation.ExternalPrivate(currentDir)

            resultList.add(SingleSelectionElement(constructInternalPrivateLocation(), true))
            resultList.add(SingleSelectionElement(value, true))

            value
        } else {
            // 不是上面三种存储目录的任何一种，不可能发生
            resultList.add(SingleSelectionElement(constructInternalPrivateLocation(), true))
            val externalPrivate = getExternalPrivateLocation()
            resultList.add(SingleSelectionElement(externalPrivate, externalPrivate.path != null))

            null
        }

        torrentLocationPresentation = resultList
    }

    @UiThread
    suspend fun setStorage(location: AndroidTorrentCacheLocation) {
        val newSaveDir = when (location) {
            is AndroidTorrentCacheLocation.InternalPrivate -> location.path
            is AndroidTorrentCacheLocation.ExternalPrivate -> location.path
        }

        if (newSaveDir == null) {
            // failed to set
            return
        }

        val settings by mediaCacheSettings
        val prevSaveDir = settings.saveDir
        if (prevSaveDir != null && prevSaveDir != newSaveDir) {
            requestMigrateCaches(prevSaveDir, newSaveDir)
        }
        mediaCacheSettings.updateSuspended(settings.copy(saveDir = newSaveDir))

        refreshStorageState()
    }

    /**
     * 申请一个可以访问的外部 uri
     *
     * @return `null` 表示取消申请权限，空字符串表示未在 DocumentUI 授权任何目录
     */
    @UiThread
    suspend fun requestExternalDocumentTree(): String? {
        selectExternalSharedStorageRequest?.cancel() // cancel existing request job

        val deferred = CompletableDeferred<Boolean>()
        selectExternalSharedStorageRequest = deferred

        val decision = deferred.await()
        selectExternalSharedStorageRequest = null

        return if (decision) {
            permissionManager.requestExternalDocumentTree(context)
        } else {
            null
        }
    }

    @UiThread
    fun respondSelectExternalSharedStorage(allow: Boolean) {
        selectExternalSharedStorageRequest?.complete(allow)
    }

    private fun requestMigrateCaches(prevPath: String, newPath: String) {
        migrationTasker.launch {
            logger.info { "[migration] request migrate cache, from: $prevPath, to: $newPath" }
            withContext(Dispatchers.Main) { migrationStatus = MigrationStatus.Init }

            cacheManager.closeAllCaches()
            // 即使 BT 引擎的 onTorrentRemoved 被调用，它仍然可能持有文件句柄
            // 在此处额外等待一段时间来确保文件已经关闭
            // 此处人为增加等待是可以的，因为移动 BT 缓存目录是非常不常用的操作
            delay(10000)

            withContext(Dispatchers.Main) { migrationStatus = MigrationStatus.Cache(null) }
            val prevCachePath = Path(prevPath).inSystem
            val newCachePath = Path(newPath).inSystem.apply {
                deleteRecursively() // new path should be empty
            }

            logger.info { "[migration] start move from $prevCachePath to $newCachePath" }
            prevCachePath.moveDirectoryRecursively(newCachePath) {
                migrationStatus = MigrationStatus.Cache(it.name)
            }
            logger.info { "[migration] move complete." }

            migrationStatus = MigrationStatus.Metadata(null)
            cacheManager.storages.forEach { storage ->
                @Suppress("NAME_SHADOWING")
                val storage = storage.firstOrNull() ?: return@forEach
                if (storage !is DataStoreMediaCacheStorage) return@forEach // 只移动 DirectoryMediaCacheStorage

                /*withContext(Dispatchers.IO) {
                    storage.metadataDir.useDirectoryEntries { seq ->
                        seq.forEach seq@{ file ->
                            if (!file.isRegularFile()) return@seq

                            logger.info { "[migration] migrating metadata: $file" }
                            withContext(Dispatchers.Main) { migrationStatus = MigrationStatus.Metadata(file.name) }

                            // read metadata file
                            val metadataSave = try {
                                json.decodeFromString(MediaCacheSave.serializer(), file.readText())
                            } catch (e: Exception) {
                                logger.warn(e) { "[migration] Failed to migrate metadata file ${file.name}" }
                                return@seq
                            }

                            // replace torrent cache dir
                            val torrentDir =
                                metadataSave.metadata.extra[TorrentMediaCacheEngine.EXTRA_TORRENT_CACHE_DIR]
                                    ?: return@seq // 只处理 TorrentMediaCacheEngine 创建的 metadata
                            val newTorrentDir = Path(newPath, torrentDir.substringAfter(prevPath)).toString()
                            logger.info {
                                "[migration] metadata of torrent cache changed: $torrentDir -> $newTorrentDir"
                            }

                            // write new metadata to original file
                            val migratedMetadata = MediaCacheSave(
                                metadataSave.origin,
                                metadataSave.metadata.copy(
                                    extra = metadataSave.metadata.extra.toMutableMap().apply {
                                        set(TorrentMediaCacheEngine.EXTRA_TORRENT_CACHE_DIR, newTorrentDir)
                                    },
                                ),
                            )

                            file.writeText(
                                json.encodeToString(MediaCacheSave.serializer(), migratedMetadata),
                            )
                        }
                    }
                }*/
            }
        }.apply {
            invokeOnCompletion { throwable ->
                if (throwable == null) {
                    exitApp()
                }
                if (throwable !is CancellationException) {
                    logger.warn(throwable) { "[migration] failed to migrate caches." }
                    lastMigrationError = throwable
                    migrationStatus = MigrationStatus.Error(throwable)
                } else {
                    exitApp()
                }
            }
        }
    }

    fun copyMigrationError(clipboard: ClipboardManager) {
        lastMigrationError?.also {
            clipboard.setText(AnnotatedString("$it\n${it.stackTraceToString()}"))
        }
    }

    fun cancelCacheMigration() {
        migrationTasker.cancel()
    }

    fun exitApp(): Nothing {
        appTerminator.exitApp(context, 0)
    }

    /**
     * 获取内部私有目录
     */
    private fun constructInternalPrivateLocation(): AndroidTorrentCacheLocation.InternalPrivate {
        return AndroidTorrentCacheLocation.InternalPrivate(defaultTorrentCacheDir)
    }

    /**
     * 尝试获取外部私有目录
     */
    private fun getExternalPrivateLocation(): AndroidTorrentCacheLocation.ExternalPrivate {
        val externalPrivateBasePath = context.getExternalFilesDir(null)?.absolutePath
        return AndroidTorrentCacheLocation.ExternalPrivate(
            externalPrivateBasePath?.let { File(it).resolve(DEFAULT_TORRENT_CACHE_DIR_NAME).absolutePath },
        )
    }

    sealed interface MigrationStatus {
        val isError: Boolean

        object Init : MigrationStatus {
            override val isError: Boolean = false
        }

        class Cache(val currentFile: String?) : MigrationStatus {
            override val isError: Boolean = false
        }

        class Metadata(val currentMetadata: String?) : MigrationStatus {
            override val isError: Boolean = false
        }

        class Error(val exception: Throwable?) : MigrationStatus {
            override val isError: Boolean = true
        }
    }
}