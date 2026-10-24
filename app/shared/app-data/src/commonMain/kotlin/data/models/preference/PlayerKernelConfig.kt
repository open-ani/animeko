/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.isAndroidTv

/**
 * 播放器内核 (而非播放页 UI) 的配置. 每项只对使用对应内核的平台生效.
 *
 * @since 6.1.0
 */
@Serializable
@Immutable
data class PlayerKernelConfig(
    /**
     * 用户自定义的 mpv 选项, 每个元素是配置文本中的一行, 可以是 `key=value`, `#` 注释或空行,
     * 解析规则见 [parseMpvOptions].
     *
     * 仅在使用 mpv 内核的平台 (桌面端) 生效.
     */
    val mpvOptions: List<String> = emptyList(),

    /**
     * 在用户未启用画质增强的情况下初始化 ExoPlayer 是否提前设置空 video effect list.
     * Media3 requires the effect graph to exist before the first prepare in order to
     * support switching effects while playback is active.
     *
     * 提前建立 effect graph 会让画面改走 GPU 合成链路, 电视厂商的系统功能 (例如运动补偿)
     * 可能因此无法生效, 所以 TV 上默认关闭, 见 [forPlatform].
     *
     * @see [me.him188.ani.app.videoplayer.videoenhancement.ExoPlayerVideoEnhancementController]
     */
    val exoPlayerInitEffectGraphInAdvance: Boolean = true,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {
        @Stable
        val Default = PlayerKernelConfig()

        /**
         * 当前平台适用的默认值.
         *
         * TV 上默认关闭 [exoPlayerInitEffectGraphInAdvance]: 提前建立 effect graph 会让画面
         * 改走 GPU 合成链路, 电视厂商的画质处理 (例如运动补偿) 可能因此失效.
         */
        fun forPlatform(platform: Platform = currentPlatform()): PlayerKernelConfig =
            if (platform.isAndroidTv()) {
                Default.copy(exoPlayerInitEffectGraphInAdvance = false)
            } else {
                Default
            }
    }
}
