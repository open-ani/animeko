/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import android.view.SurfaceView
import org.openani.mediamp.MediampPlayer
import java.lang.ref.WeakReference
import java.util.WeakHashMap

private val videoSurfaces = WeakHashMap<MediampPlayer, WeakReference<SurfaceView>>()

internal fun registerAndroidVideoSurface(player: MediampPlayer, surfaceView: SurfaceView) {
    synchronized(videoSurfaces) {
        videoSurfaces[player] = WeakReference(surfaceView)
    }
}

fun MediampPlayer.findAndroidVideoSurface(): SurfaceView? = synchronized(videoSurfaces) {
    videoSurfaces[this]?.get()
}
