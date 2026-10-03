/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.features

import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatformDesktop
import java.io.File

/**
 * 系统分享面板: 把一个本地文件交给系统, 由用户选择接收它的应用或服务.
 *
 * Windows 用 Share UI ([WindowsShareSheet]), macOS 用 `NSSharingServicePicker` ([MacosShareSheet]);
 * Linux 没有通用的分享面板, [current] 为 `null`, 调用方自行退回其他方式.
 */
interface SystemShareSheet {
    /**
     * 为 [file] 显示系统分享面板.
     *
     * @param windowHandle 宿主窗口的原生句柄: Windows 为 HWND, macOS 为 NSWindow 指针.
     * @param title 面板上展示的标题, 一般是文件名.
     * @param anchor 触发分享的控件在窗口内容区中的位置. macOS 的分享菜单从它旁边弹出; Windows 的面板由系统定位, 忽略它.
     * @return 面板是否已显示. 不抛出异常.
     */
    suspend fun shareFile(windowHandle: Long, file: File, title: String, anchor: ShareAnchor?): Boolean

    companion object {
        /** 当前桌面平台的实现; Linux 为 `null`. */
        val current: SystemShareSheet? by lazy {
            when (currentPlatformDesktop()) {
                is Platform.Windows -> WindowsShareSheet
                is Platform.MacOS -> MacosShareSheet
                is Platform.Linux -> null
            }
        }
    }
}

/**
 * 窗口内容区坐标系中的矩形: 原点在左上角, 单位是点 (与 Compose 的 dp 相同, 不含系统缩放).
 */
data class ShareAnchor(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)
