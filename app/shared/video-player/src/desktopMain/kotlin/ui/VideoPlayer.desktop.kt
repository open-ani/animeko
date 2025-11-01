/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.VideoAspectRatio
import org.openani.mediamp.vlc.VlcMediampPlayer
import org.openani.mediamp.vlc.compose.VlcMediampPlayerSurface

@Composable
actual fun VideoPlayer(
    player: MediampPlayer,
    modifier: Modifier,
    aspectRatioMode: AspectRatioMode,
) {
    check(player is VlcMediampPlayer)

    // 设置 mediamp 的 aspect ratio mode
    player.features[VideoAspectRatio.Key]?.setMode(aspectRatioMode)
    VlcMediampPlayerSurface(player, modifier)
}