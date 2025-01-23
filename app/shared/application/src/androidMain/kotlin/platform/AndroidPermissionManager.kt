/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger

class AndroidPermissionManager : PermissionManager {
    private val logger = logger<AndroidPermissionManager>()

    override fun checkNotificationPermission(context: ContextMP): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val activity = context.findActivity() as? BaseComponentActivity ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(activity).areNotificationsEnabled()
        }
    }
    
    override suspend fun requestNotificationPermission(context: ContextMP): Boolean {
        // To send notifications on API levels below 32, you don't need to request the POST_NOTIFICATIONS permission,
        // as it is only required for API level 33 (Android 13) and above.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val activity = context.findActivity() as? BaseComponentActivity ?: return false
        return activity.requestPermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun openSystemNotificationSettings(context: ContextMP) {
        val openSystemNotificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(openSystemNotificationIntent)
    }

    override suspend fun requestExternalDocumentTree(context: ContextMP): String? {
        val activity = context.findActivity() as? BaseComponentActivity ?: return null
        val result = activity.requestExternalDocumentTree()
        logger.info { "request external document tree result: $result" }
        return result
    }
}
