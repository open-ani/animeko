/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.player

import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.avkit.AVKitMediampPlayer
import org.openani.mediamp.avkit.AVKitMediampPlayerFactory
import platform.AVFoundation.preferredForwardBufferDuration
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.time.DurationUnit

/**
 * 应用 [PlayerBufferPolicy] 的 AVKit 播放器工厂.
 *
 * AVFoundation 只暴露往前预读时长 (`preferredForwardBufferDuration`); 已播放内容保留多久由系统决定, 无法配置.
 */
class AniAVKitMediampPlayerFactory(
    private val delegate: AVKitMediampPlayerFactory = AVKitMediampPlayerFactory(),
) : MediampPlayerFactory<AVKitMediampPlayer> {
    override val forClass: KClass<AVKitMediampPlayer> get() = AVKitMediampPlayer::class

    override fun create(context: Any, parentCoroutineContext: CoroutineContext): AVKitMediampPlayer {
        return delegate.create(
            parentCoroutineContext,
            configurePlayerItem = { item, _ ->
                item.preferredForwardBufferDuration = PlayerBufferPolicy.forward.toDouble(DurationUnit.SECONDS)
            },
        )
    }
}
