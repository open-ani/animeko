/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.macos.share

import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream

/**
 * macOS 系统分享菜单 (`NSSharingServicePicker`) 的 JNI 入口.
 *
 * 原生库 `libanimeko_macos_share.dylib` 作为资源装在本模块的 jar 里, 只有在 macOS 主机上构建时才存在 ([isAvailable]).
 * 打包的应用里它被解到 jar 旁边的运行库目录 (`java.library.path`), `System.loadLibrary` 直接找到;
 * 开发时 (gradlew run、测试) 首次使用从 jar 里解到临时目录加载. 只在 macOS 上有意义.
 */
object MacosShareSheetNative {
    private const val LIBRARY_NAME = "animeko_macos_share"
    private const val RESOURCE_NAME = "lib$LIBRARY_NAME.dylib"

    private val loadResult: Result<Unit> by lazy { runCatching { load() } }

    /** 原生库是否已成功加载. */
    val isAvailable: Boolean get() = loadResult.isSuccess

    /** 加载原生库; 失败时抛出加载时的异常, 通常是 [UnsatisfiedLinkError]. */
    fun ensureLoaded() {
        loadResult.getOrThrow()
    }

    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun load() {
        try {
            System.loadLibrary(LIBRARY_NAME)
            return
        } catch (_: UnsatisfiedLinkError) {
            // 没有打包 (开发时): 从 jar 里解出来
        }
        val resource = MacosShareSheetNative::class.java.classLoader.getResourceAsStream(RESOURCE_NAME)
            ?: throw UnsatisfiedLinkError("$RESOURCE_NAME is not on the classpath; this module was not built on macOS")
        val directory = Files.createTempDirectory("animeko-macos-share-").apply { toFile().deleteOnExit() }
        val file = directory.resolve(RESOURCE_NAME).apply { toFile().deleteOnExit() }
        resource.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        System.load(file.absolutePathString())
    }

    /**
     * 在主线程上为 [path] 显示分享菜单, 返回是否已投递到主线程 (显示本身在主线程上异步完成).
     *
     * @param window NSWindow 指针 (Compose 窗口的 `windowHandle`); 传 NSView 指针也可以.
     * @param hasAnchor 是否给了锚点. 锚点是窗口内容区坐标 (原点在左上角, 单位是点) 中的矩形, 菜单从它上方弹出;
     * 没有锚点时菜单贴着内容区.
     */
    @JvmStatic
    external fun showSharingServicePicker(
        window: Long,
        path: String,
        hasAnchor: Boolean,
        left: Double,
        top: Double,
        width: Double,
        height: Double,
    ): Boolean
}
