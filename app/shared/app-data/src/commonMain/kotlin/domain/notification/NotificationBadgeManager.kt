/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.notification

import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.data.models.notification.NotificationBadgeState

/**
 * 维护通知入口 (头像和头像菜单里的“通知”) 的未读状态. 整个 App 只有一个实例.
 *
 * 登录用户在 App 位于前台时定期刷新; 游客只在启动和回到前台时获取一次最新公告. 退出登录时立即清空未读数.
 */
interface NotificationBadgeManager {
    /**
     * 当前的未读状态. 尚未获取到时为 [NotificationBadgeState.Empty].
     */
    val state: StateFlow<NotificationBadgeState>

    /**
     * 请求尽快刷新 [state]. 在标记已读等会改变未读数的操作之后调用. 不会抛出异常, 刷新失败时保留原状态.
     */
    fun requestRefresh()

    /**
     * 通知 App 进入前台 ([foreground] 为 `true`) 或进入后台. 由 UI 根组件根据生命周期和窗口是否可见调用.
     */
    fun setAppForeground(foreground: Boolean)
}
