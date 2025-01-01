/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.notification

import androidx.compose.runtime.Stable
import coil3.Image
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.MediampPlayer
import kotlin.time.Duration

@Stable
class VideoNotificationState(
    private val tag: String = "ui/subject/episode/video",
) : KoinComponent {
    private val notificationManager: NotifManager by inject()

    fun setAlbumArt(albumArt: Image) {
        val notif = mediaNotif()
        notif.updateAlbumArt(albumArt)
    }

    fun setDescription(title: String, text: String, length: Duration) {
        mediaNotif().apply {
            contentTitle = title
            contentText = text
            updateMediaMetadata(album = title, duration = length)
            show()
        }
    }

    fun setPlayer(playerState: MediampPlayer) {
        val notif = mediaNotif()
        notif.attachPlayerState(playerState)
    }

    private fun mediaNotif() = notificationManager.playChannel.getOrStart(tag)

    fun release() {
        notificationManager.playChannel.releaseCurrent()
    }
}
