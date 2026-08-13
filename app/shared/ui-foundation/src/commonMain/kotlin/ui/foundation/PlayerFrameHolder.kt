/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.graphics.ImageBitmap

/**
 * TV 播放器暂停帧快照传递 (跨导航): 播放器在跳转缓存页前捕获当前画面写入,
 * 缓存页取走作为半透明遮罩背景 (导航离开播放器时播放自动暂停, 该帧即暂停画面).
 *
 * 一次性消费 ([take] 即清空): 只有从播放器进入的缓存页会拿到帧,
 * 其他入口 (如条目详情页) 不会误用陈旧画面.
 */
object PlayerFrameHolder {
    private var frame: ImageBitmap? = null

    /** 播放器侧: 跳转前写入捕获的帧 (捕获失败传 null 则缓存页回退普通背景). */
    fun put(frame: ImageBitmap?) {
        this.frame = frame
    }

    /** 缓存页侧: 取走并清空. */
    fun take(): ImageBitmap? = frame.also { frame = null }
}
