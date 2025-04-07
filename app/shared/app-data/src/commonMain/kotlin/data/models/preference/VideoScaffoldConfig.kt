/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import me.him188.ani.utils.platform.annotations.SerializationOnly

@Immutable
@Serializable
enum class FullscreenSwitchMode {
    /**
     * 在小屏 (竖屏) 模式下也在右下角总是显示全屏按钮.
     */
    ALWAYS_SHOW_FLOATING,

    /**
     * 在小屏 (竖屏) 模式下也在右下角显示全屏按钮, 但在五秒后自动隐藏
     */
    AUTO_HIDE_FLOATING,

    /**
     * 仅在控制器显示时才有全屏按钮.
     */
    ONLY_IN_CONTROLLER
}

@Serializable
@Immutable
data class VideoScaffoldConfig @SerializationOnly constructor(
    // TODO: 这个名字可能不好 
    /**
     * 在小屏 (竖屏) 模式下也在右下角显示全屏按钮.
     */
    val fullscreenSwitchMode: FullscreenSwitchMode = FullscreenSwitchMode.ALWAYS_SHOW_FLOATING,
    /**
     * 在编辑弹幕时暂停视频.
     * @since 3.2.0-beta01
     */
    val pauseVideoOnEditDanmaku: Boolean = true,
    /**
     * 在观看到 90% 进度后, 自动标记看过
     */
    val autoMarkDone: Boolean = true,
    /**
     * 在点击选择剧集后, 立即隐藏 media selector
     */
    val hideSelectorOnSelect: Boolean = false,
    /**
     * 横屏时自动全屏
     */
    val autoFullscreenOnLandscapeMode: Boolean = false,
    /**
     * 自动连播
     */
    val autoPlayNext: Boolean = true,
    /**
     * 跳过 OP 和 ED
     */
    val autoSkipOpEd: Boolean = true,
    /**
     * 在播放器错误时自动切换视频源
     */
    val autoSwitchMediaOnPlayerError: Boolean = true,
    /**
     * 用于在安卓上设置屏幕刷新率, 解决某些设备会自动限制刷新率的问题 (三星).
     *
     * 0 为不设置 (使用系统默认).
     *
     * @since 4.8
     */
    val displayModeId: Int = 0,
    // WARNING: if you add new property here, review Companion properties.
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {
        @OptIn(SerializationOnly::class)
        @Stable
        val Default = VideoScaffoldConfig()

        @OptIn(SerializationOnly::class)
        val AllDisabled = VideoScaffoldConfig(
            fullscreenSwitchMode = FullscreenSwitchMode.ONLY_IN_CONTROLLER,
            pauseVideoOnEditDanmaku = false,
            autoMarkDone = false,
            hideSelectorOnSelect = false,
            autoFullscreenOnLandscapeMode = false,
            autoPlayNext = false,
            autoSkipOpEd = false,
            autoSwitchMediaOnPlayerError = false,
        )
    }
}
