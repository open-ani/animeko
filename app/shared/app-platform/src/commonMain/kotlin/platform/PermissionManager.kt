/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

interface PermissionManager {
    /**
     * 检查应用是否有通知权限
     */
    fun checkNotificationPermission(context: ContextMP): Boolean

    /**
     * 请求应用通知权限
     *
     * @return `true` 成功授权, `false` 拒绝授权或保持现状
     */
    suspend fun requestNotificationPermission(context: ContextMP): Boolean

    /**
     * 打开系统设置手动授权通知权限
     */
    fun openSystemNotificationSettings(context: ContextMP)

    /**
     * Android only. 请求一个完全授予可读写权限的外部共享空间路径，返回其 URL string
     */
    suspend fun requestExternalDocumentTree(context: ContextMP): String?
}

object GrantedPermissionManager : PermissionManager {
    override fun checkNotificationPermission(context: ContextMP): Boolean {
        return true
    }
    
    override suspend fun requestNotificationPermission(context: ContextMP): Boolean {
        return true
    }

    override fun openSystemNotificationSettings(context: ContextMP) {

    }

    override suspend fun requestExternalDocumentTree(context: ContextMP): String? {
        return null
    }
}
