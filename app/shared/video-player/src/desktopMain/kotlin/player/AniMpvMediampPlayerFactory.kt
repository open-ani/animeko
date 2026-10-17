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
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.MpvMediampPlayerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * 在 mediamp 默认选项之上应用 [PlayerBufferPolicy] 的 mpv 播放器工厂.
 *
 * 选项在 mpv 初始化前设置, 用户在设置中填写的自定义 mpv 选项 ([applyMpvOptions]) 在之后应用, 仍可覆盖这里的值.
 */
class AniMpvMediampPlayerFactory(
    private val delegate: MpvMediampPlayerFactory = MpvMediampPlayerFactory(),
) : MediampPlayerFactory<MpvMediampPlayer> {
    override val forClass: KClass<MpvMediampPlayer> get() = MpvMediampPlayer::class

    override fun create(context: Any, parentCoroutineContext: CoroutineContext): MpvMediampPlayer {
        return delegate.create(context, parentCoroutineContext) { handle: MPVHandle ->
            for ((key, value) in mpvBufferOptions()) {
                handle.option(key, value)
            }
        }
    }
}

/**
 * 把 [PlayerBufferPolicy] 翻译成 mpv 选项.
 *
 * - `cache-secs`: 缓存启用时 (网络流) 往前预读的秒数.
 * - `demuxer-max-bytes` / `demuxer-max-back-bytes`: 往前 / 往后的字节上限. mpv 的往后缓存只有字节维度,
 *   所以往后的时长由 [PlayerBufferPolicy.MPV_MAX_BYTES_PER_DIRECTION] 近似.
 *
 * 注意 mpv 只在 `cache` 启用时才保留往后的数据 (默认 `auto`, 对网络流启用). BT 源的数据本身在磁盘上, 往后退直接读文件, 不依赖这里.
 */
internal fun mpvBufferOptions(policy: PlayerBufferPolicy = PlayerBufferPolicy): Map<String, String> = linkedMapOf(
    "cache-secs" to policy.forward.inWholeSeconds.toString(),
    "demuxer-max-bytes" to PlayerBufferPolicy.MPV_MAX_BYTES_PER_DIRECTION.toString(),
    "demuxer-max-back-bytes" to PlayerBufferPolicy.MPV_MAX_BYTES_PER_DIRECTION.toString(),
)
