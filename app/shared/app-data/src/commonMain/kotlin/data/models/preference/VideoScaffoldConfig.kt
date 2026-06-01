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
import me.him188.ani.utils.platform.annotations.SerializationOnly
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

/**
 * 拿 OP/ED 怎么办 (见 [VideoScaffoldConfig.effectiveSkipOpEdMode]).
 *
 * @since 6.0.5
 */
@Immutable
@Serializable
enum class SkipOpEdMode {
    /** 到点自动跳过; 跳之前给一颗"取消跳过"的按钮, 来得及反悔. */
    AUTO,

    /**
     * 到点自动跳过, 但**取消之后仍然给一颗"跳过"按钮**, 直到这一段 OP/ED 放完.
     *
     * 与 [AUTO] 的差别只在取消之后: [AUTO] 按了取消这一段就彻底没声了, 想跳只能自己拖进度条;
     * 本档相当于"取消一次就临时退回 [MANUAL]", 听了两句想起来这首歌其实听过了, 还能补跳。
     *
     * 顺带覆盖另一种"没跳成": 倍速播放时位置采样窗口可能被整段迈过去 (见 PlayerSkipOpEdState
     * 里的 SKIP_TRIGGER_OVERSHOOT_MILLIS), 自动跳不成的那一次, 本档也还有按钮兜着.
     *
     * @since 6.0.5
     */
    AUTO_THEN_MANUAL,

    /** 不自动跳, 只在 OP/ED 期间给一颗"跳过"的按钮, 按不按由人决定. */
    MANUAL,

    /** 两种按钮都不出现, 也不会自动跳. */
    OFF,
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
     * 悬浮或拖动播放进度条时显示视频帧预览.
     */
    val enableFramePreview: Boolean = true,
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
     * 跳过 OP 和 ED.
     *
     * 6.0.5 起由三档的 [skipOpEdMode] 取代, 本字段只为**读旧配置**保留 (见 [effectiveSkipOpEdMode]);
     * 新代码一律读 [effectiveSkipOpEdMode], 写 [skipOpEdMode].
     */
    val autoSkipOpEd: Boolean = true,
    /**
     * 拿 OP/ED 怎么办; null = 还没选过新选项, 按旧开关 [autoSkipOpEd] 换算 (见 [effectiveSkipOpEdMode]).
     *
     * 不直接把 [autoSkipOpEd] 改成枚举: 配置是 JSON 存的, 改类型会让老配置里那个布尔值读不出来,
     * 关掉过自动跳过的人升级后会被悄悄打开.
     *
     * @since 6.0.5
     */
    val skipOpEdMode: SkipOpEdMode? = null,
    /**
     * 跳过 OP 和 ED 的时长. UI 仅提供 80, 85 和 90 秒三个选项.
     */
    val opEdSkipDuration: Duration = 85.seconds,
    /**
     * 在播放器错误时自动切换视频源
     */
    val autoSwitchMediaOnPlayerError: Boolean = true,
    /**
     * 在 Android 上使用高质量 WSOLA 处理非 1x 速度的音频.
     */
    val enableHighQualityAudioTimeStretch: Boolean = true,
    /**
     * 过滤 HLS 播放列表中的插播片段.
     *
     * @since 5.7
     */
    val enableExperimentalHlsSegmentFiltering: Boolean = false,
    /**
     * 用于在安卓上设置屏幕刷新率, 解决某些设备会自动限制刷新率的问题 (三星).
     *
     * 0 为不设置 (使用系统默认).
     *
     * @since 4.8
     */
    val displayModeId: Int = 0,
    /**
     * 长按期间使用的播放速度. 是绝对速度, 不是相对当前倍速的倍率.
     *
     * @since 4.9
     */
    val fastForwardSpeed: Float = 2.5f, // 3 倍弹幕会跳, 所以慢点, see #1524
    /**
     * 播放倍速. 语义取决于 [rememberPlaybackSpeed]:
     *
     * - [rememberPlaybackSpeed] 为 `true` 时, 这是**全局常驻倍速**: 播放器内的调整会写回这里,
     *   跨剧集、条目和应用重启保持.
     * - [rememberPlaybackSpeed] 为 `false` 时, 这是**默认倍速**: 只作为每次进入播放器的起始值,
     *   播放器内的调整不会写回, 退出播放器后即丢弃.
     *
     * 始终位于 [minPlaybackSpeed]..[maxPlaybackSpeed] 范围内; 量化由 UI 层 (SteppedSlider) 保证.
     *
     * @since 5.8
     */
    val playbackSpeed: Float = 1f,
    /**
     * 是否记住播放器内的倍速调整. 见 [playbackSpeed].
     *
     * @since 6.0
     */
    val rememberPlaybackSpeed: Boolean = true,
    /**
     * 用户可调倍速范围的下界.
     *
     * @since 5.8
     */
    val minPlaybackSpeed: Float = 0.5f,
    /**
     * 用户可调倍速范围的上界.
     *
     * @since 5.8
     */
    val maxPlaybackSpeed: Float = 2.5f,
    /**
     * 播放器的音量.
     *
     * 在 Desktop 和 iOS 使用, Android 总是使用系统音量.
     *
     * @since 4.11
     */
    val playerVolume: PlayerVolume = PlayerVolume(1f, false),
    // WARNING: if you add new property here, review Companion properties.
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    /**
     * 应用新的可调倍速范围，并将依赖该范围的值限制在其中。
     */
    fun withPlaybackSpeedRange(
        range: ClosedFloatingPointRange<Float>,
    ): VideoScaffoldConfig {
        require(range.endInclusive - range.start >= MIN_PLAYBACK_SPEED_RANGE_WIDTH) {
            "Playback speed range must span at least $MIN_PLAYBACK_SPEED_RANGE_WIDTH, but was $range"
        }
        return copy(
            minPlaybackSpeed = range.start,
            maxPlaybackSpeed = range.endInclusive,
            playbackSpeed = playbackSpeed.coerceIn(range),
            fastForwardSpeed = fastForwardSpeed.coerceIn(range),
        )
    }

    companion object {
        /**
         * 播放器统一支持的硬范围下界.
         */
        const val MIN_SUPPORTED_PLAYBACK_SPEED: Float = 0.25f

        /**
         * 播放器统一支持的硬范围上界.
         */
        const val MAX_SUPPORTED_PLAYBACK_SPEED: Float = 4.0f

        /** 用户可调倍速范围的最小宽度，即一个档位。 */
        const val MIN_PLAYBACK_SPEED_RANGE_WIDTH: Float = 0.25f

        /**
         * 将 RangeSlider 的候选值规范化为至少一个档位宽的合法范围。
         */
        fun normalizePlaybackSpeedRange(
            range: ClosedFloatingPointRange<Float>,
            previousRange: ClosedFloatingPointRange<Float>? = null,
        ): ClosedFloatingPointRange<Float> {
            val start = range.start.coerceIn(MIN_SUPPORTED_PLAYBACK_SPEED, MAX_SUPPORTED_PLAYBACK_SPEED)
            val end = range.endInclusive.coerceIn(MIN_SUPPORTED_PLAYBACK_SPEED, MAX_SUPPORTED_PLAYBACK_SPEED)
            if (end - start >= MIN_PLAYBACK_SPEED_RANGE_WIDTH) return start..end

            // RangeSlider 允许两个 thumb 重合。此时依据前一帧的值判断被拖动的一端，
            // 固定另一端，避免左 thumb 向右拖时把整个最小范围一起向右推。
            if (previousRange != null) {
                if (start > previousRange.start) {
                    return (end - MIN_PLAYBACK_SPEED_RANGE_WIDTH).coerceAtLeast(MIN_SUPPORTED_PLAYBACK_SPEED)..end
                }
                if (end < previousRange.endInclusive) {
                    return start..(start + MIN_PLAYBACK_SPEED_RANGE_WIDTH).coerceAtMost(MAX_SUPPORTED_PLAYBACK_SPEED)
                }
            }

            val adjustedEnd = (start + MIN_PLAYBACK_SPEED_RANGE_WIDTH).coerceAtMost(MAX_SUPPORTED_PLAYBACK_SPEED)
            return if (adjustedEnd - start >= MIN_PLAYBACK_SPEED_RANGE_WIDTH) {
                start..adjustedEnd
            } else {
                (end - MIN_PLAYBACK_SPEED_RANGE_WIDTH).coerceAtLeast(MIN_SUPPORTED_PLAYBACK_SPEED)..end
            }
        }

        @OptIn(SerializationOnly::class)
        @Stable
        val Default = VideoScaffoldConfig()

        @OptIn(SerializationOnly::class)
        val AllDisabled = VideoScaffoldConfig(
            fullscreenSwitchMode = FullscreenSwitchMode.ONLY_IN_CONTROLLER,
            enableFramePreview = false,
            pauseVideoOnEditDanmaku = false,
            autoMarkDone = false,
            hideSelectorOnSelect = false,
            autoFullscreenOnLandscapeMode = false,
            autoPlayNext = false,
            autoSkipOpEd = false,
            skipOpEdMode = SkipOpEdMode.OFF,
            autoSwitchMediaOnPlayerError = false,
            enableHighQualityAudioTimeStretch = false,
            enableExperimentalHlsSegmentFiltering = false,
        )
    }

    /**
     * 实际生效的 OP/ED 处理方式: 选过新选项就用它, 没选过则按旧版那个布尔开关换算.
     *
     * 计算属性而不是构造参数: 它不参与序列化, 也就不会把"没选过"这个信息写没了.
     */
    val effectiveSkipOpEdMode: SkipOpEdMode
        get() = skipOpEdMode ?: if (autoSkipOpEd) SkipOpEdMode.AUTO else SkipOpEdMode.OFF

    @Serializable
    data class PlayerVolume(val level: Float, val mute: Boolean)
}
