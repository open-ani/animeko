/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.notification

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil3.Image
import coil3.annotation.ExperimentalCoilApi
import coil3.asDrawable
import me.him188.ani.R
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import java.util.concurrent.atomic.AtomicInteger

internal open class AndroidNotif(
    protected val manager: AndroidNotifManager,
    protected val getContext: () -> Context,
    private val channelId: String,
) : Notif {
    companion object {
        private val globalId = AtomicInteger(0)
        private val logger = logger<AndroidNotif>()
        fun nextNotificationId(): Int = globalId.incrementAndGet()
    }

    protected var builder = NotificationCompat.Builder(getContext(), channelId).apply {
        setSmallIcon(R.mipmap.ic_launcher) // 必须要有一个 icon
    }

    private val id = nextNotificationId()

    override var priority: NotifPriority = NotifPriority.DEFAULT
        set(value) {
            builder.setPriority(value.toAndroidPriority())
            field = value
        }
    override var silent: Boolean = false
        set(value) {
            builder.setSilent(value)
            field = value
        }
    override var ongoing: Boolean = false
        set(value) {
            builder.setOngoing(value)
            field = value
        }
    override var contentTitle: String? = ""
        set(value) {
            builder.setContentTitle(value)
            field = value
        }
    override var contentText: String? = ""
        set(value) {
            builder.setContentText(value)
            field = value
        }

    override fun setGroup(groupKey: String) {
        builder.setGroup(groupKey)
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
    }

    override fun setAsGroupSummary(isGroupSummary: Boolean) {
        builder.setGroupSummary(isGroupSummary)
    }

    override fun setSmallIcon(uri: String) {
        kotlin.runCatching {
            builder.setSmallIcon(IconCompat.createWithContentUri(uri))
        }.onFailure {
            logger.error(it) { "Failed to set small icon" }
        }
    }

    override fun setSmallIcon(bitmap: ImageBitmap) {
        kotlin.runCatching {
            builder.setSmallIcon(IconCompat.createWithAdaptiveBitmap(bitmap.asAndroidBitmap()))
        }.onFailure {
            logger.error(it) { "Failed to set small icon" }
        }
    }

    fun setSmallIcon(bitmap: Bitmap) {
        kotlin.runCatching {
            builder.setSmallIcon(IconCompat.createWithAdaptiveBitmap(bitmap))
        }.onFailure {
            logger.error(it) { "Failed to set small icon" }
        }
    }


    @ExperimentalCoilApi
    override fun setSmallIcon(bitmap: Image) {
        setSmallIcon(bitmap.asDrawable(getContext().resources).toBitmap())
    }

    override fun setLargeIcon(bitmap: ImageBitmap) {
        kotlin.runCatching {
            builder.setLargeIcon(bitmap.asAndroidBitmap())
        }.onFailure {
            logger.error(it) { "Failed to set large icon" }
        }
    }

    override fun setProgress(max: Int, progress: Int) {
        builder.setProgress(max, progress, false)
    }

    override fun setIndeterminateProgress() {
        builder.setProgress(0, 0, true)
    }

    private var permissionChecked = false

    @SuppressLint("MissingPermission")
    override fun show() {
        kotlin.runCatching {
            if (!permissionChecked) {
                permissionChecked = true
                if (ContextCompat.checkSelfPermission(
                        getContext(),
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    logger.warn { "No notification permission, ignoring" }
                }
            }

            manager.notificationManager.notify(id, builder.build())
        }.onFailure {
            logger.error(it) { "Failed to show notification" }
        }
    }

    override fun cancel() {
        kotlin.runCatching {
            manager.notificationManager.cancel(id)
        }.onFailure {
            logger.error(it) { "Failed to cancel notification" }
        }
    }

    @ExperimentalCoilApi
    override fun setLargeIcon(bitmap: Image) {
        builder.setLargeIcon(bitmap.asDrawable(getContext().resources).toBitmap())
        show()
    }

    override fun release() {
        cancel()
        // no-op
        builder.clearActions()
    }

    override fun toString(): String {
        return "AndroidNotif(id=$id, channelId=$channelId)"
    }
}

private fun NotifPriority.toAndroidPriority(): Int {
    return when (this) {
        NotifPriority.MIN -> NotificationCompat.PRIORITY_MIN
        NotifPriority.LOW -> NotificationCompat.PRIORITY_LOW
        NotifPriority.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
        NotifPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
        NotifPriority.MAX -> NotificationCompat.PRIORITY_MAX
    }
}


