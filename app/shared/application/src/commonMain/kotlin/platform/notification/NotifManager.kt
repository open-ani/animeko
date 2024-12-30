/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.notification

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image
import coil3.annotation.ExperimentalCoilApi
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.openani.mediamp.MediampPlayer
import kotlin.concurrent.Volatile
import kotlin.time.Duration

abstract class NotifManager { // available via inject
    /**
     * 注册一条可以创建并发送多条通知的渠道
     */
    abstract fun registerNormalChannel(
        id: String,
        name: String,
        importance: NotifImportance = NotifImportance.LOW,
        description: String? = null
    ): NormalNotifChannel

    /**
     * 注册一条可以持续更新的通知渠道
     */
    abstract fun registerOngoingChannel(
        id: String,
        name: String,
        importance: NotifImportance = NotifImportance.LOW,
        description: String? = null
    ): OngoingNotifChannel

    /**
     * 注册一条可以持续更新的通知渠道, 可用于显示下载进度等
     */
    abstract fun registerMediaOngoingChannel(
        id: String,
        name: String,
        importance: NotifImportance = NotifImportance.LOW,
        description: String? = null
    ): MediaNotifChannel<MediaNotif>

    abstract fun hasPermission(): Boolean

    val downloadChannel by lazy {
        registerNormalChannel(
            id = "download",
            name = "缓存进度",
            description = "通知下载进度, 也可以帮助保持在后台运行",
            importance = NotifImportance.MIN, // 不显示图标
        )
    }

    val playChannel by lazy {
        registerMediaOngoingChannel(
            id = "playing",
            name = "正在播放",
            description = "当前有视频正在播放时显示, 可控制暂停或关闭",
            importance = NotifImportance.MIN,
        )
    }

    open fun createChannels() {
        downloadChannel
        playChannel
    }
}

enum class NotifImportance {
    NONE,
    MIN,
    LOW,
    DEFAULT,
    HIGH,
    MAX
}

enum class NotifPriority {
    MIN,
    LOW,
    DEFAULT,
    HIGH,
    MAX
}

sealed interface NotifChannel

interface OngoingNotifChannel : NotifChannel {
    val notif: Notif
}

abstract class MediaNotifChannel<out T : MediaNotif> : NotifChannel, SynchronizedObject() {
    /**
     * 当前正在显示的通知
     */
    open val notif: T?
        get() = _current

    @Volatile
    private var _current: T? = null

    fun startNew(tag: String): T {
        synchronized(this) {
            releaseCurrent()
            return create(tag).also { _current = it }
        }
    }

    fun getOrStart(tag: String): T {
        return _current ?: synchronized(this) {
            _current ?: startNew(tag)
        }
    }

    protected abstract fun create(tag: String): T

    fun releaseCurrent() {
        synchronized(this) {
            _current?.release()
            _current = null
        }
    }
}

/**
 * 可以发送多条普通一次性通知的通道
 */
interface NormalNotifChannel : NotifChannel {
    /**
     * 创建一条通知. 该通知不会发送, 直到 [Notif.show] 被调用
     */
    fun newNotif(): Notif
}

interface Notif {
    var priority: NotifPriority
    var silent: Boolean
    var ongoing: Boolean

    var contentTitle: String?
    var contentText: String?

    fun setGroup(groupKey: String)
    fun setAsGroupSummary(isGroupSummary: Boolean)
//    fun groupSummaryStyle(): GroupSummaryStyle

    fun setSmallIcon(uri: String) // required
    fun setSmallIcon(bitmap: ImageBitmap)

    @OptIn(ExperimentalCoilApi::class)
    fun setSmallIcon(bitmap: Image)

    fun setLargeIcon(bitmap: ImageBitmap)

    @OptIn(ExperimentalCoilApi::class)
    fun setLargeIcon(bitmap: Image)

    fun setProgress(max: Int, progress: Int)
    fun setIndeterminateProgress()

    /**
     * 更新信息后, 调用此方法以显示通知
     */
    fun show()

    /**
     * 清除通知
     */
    fun cancel()

    fun release()
}

//interface GroupSummaryStyle {
//   fun 
//}

enum class MediaIcon {
    PLAY,
    PAUSE,
    STOP,
}

interface MediaNotif : Notif {
    fun addOneTimeAction(
        icon: MediaIcon,
        title: String,
        action: () -> Unit,
    )

    fun onClickContent(action: () -> Unit)

    fun updateMediaMetadata(
        title: String? = null,
        album: String? = null,
        artist: String? = null,
        duration: Duration? = null,
    )

    @OptIn(ExperimentalCoilApi::class)
    fun updateAlbumArt(albumArt: Image) {
        setLargeIcon(albumArt)
    }

    fun attachPlayerState(playerState: MediampPlayer)
}

// For desktop
object NoopNotifManager : NotifManager() {
    private abstract class NoopNotif : Notif {
        override var priority: NotifPriority = NotifPriority.DEFAULT
        override var silent: Boolean = false
        override var ongoing: Boolean = false
        override var contentTitle: String? = ""
        override var contentText: String? = ""

        override fun setAsGroupSummary(isGroupSummary: Boolean) {}
        override fun setGroup(groupKey: String) {}
        override fun setSmallIcon(uri: String) {}
        override fun setSmallIcon(bitmap: ImageBitmap) {}
        override fun setLargeIcon(bitmap: ImageBitmap) {}
        override fun setProgress(max: Int, progress: Int) {}
        override fun setIndeterminateProgress() {}
        override fun show() {}
        override fun cancel() {}
        override fun release() {}

        @ExperimentalCoilApi
        override fun setLargeIcon(bitmap: Image) {
        }

        @ExperimentalCoilApi
        override fun setSmallIcon(bitmap: Image) {
        }

        companion object : NoopNotif() {
        }
    }

    private object NoopNotifChannel : OngoingNotifChannel, NormalNotifChannel {
        override val notif: Notif get() = NoopNotif
        override fun newNotif(): Notif = NoopNotif
    }


    override fun registerNormalChannel(
        id: String,
        name: String,
        importance: NotifImportance,
        description: String?
    ): NormalNotifChannel = NoopNotifChannel

    override fun registerOngoingChannel(
        id: String,
        name: String,
        importance: NotifImportance,
        description: String?
    ): OngoingNotifChannel = NoopNotifChannel


    private object NoopMediaNotif : NoopNotif(), MediaNotif {
        override fun addOneTimeAction(icon: MediaIcon, title: String, action: () -> Unit) {
        }

        override fun onClickContent(action: () -> Unit) {
        }

        override fun updateMediaMetadata(title: String?, album: String?, artist: String?, duration: Duration?) {
        }

        override fun attachPlayerState(playerState: MediampPlayer) {}
    }

    private object NoopMediaNotifChannel : MediaNotifChannel<MediaNotif>() {
        override fun create(tag: String): MediaNotif = NoopMediaNotif
    }

    override fun registerMediaOngoingChannel(
        id: String,
        name: String,
        importance: NotifImportance,
        description: String?
    ): MediaNotifChannel<MediaNotif> = NoopMediaNotifChannel

    override fun hasPermission(): Boolean = false
}