/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.coroutines.CoroutineContext

/**
 * 播放器倍速的唯一写入者.
 *
 * ## 心智模型: 倍速是两个各自有主的状态, 生效值是导出的
 *
 * - **基础倍速** [baseSpeed]: 用户选择的倍速. 它的 source of truth 是构造时传入的 [baseSpeedFlow]
 *   (通常是「配置值 + 本次播放的 override」), 本类只是跟随它.
 * - **临时倍速**: 只在长按快进期间存在, 不持久化, 见 [beginTemporarySpeed] / [endTemporarySpeed].
 *
 * 播放器上生效的倍速恒为 `临时 ?: 基础`. 注意生效值是**导出**的, 不是存储的——
 * 所以它不可能与两层状态不一致, 也不需要任何人维护它.
 *
 * 由此得到三个结构性保证:
 *
 * 1. **长按结束是「丢弃临时倍速」而不是「恢复按下时的快照」.** 快照的正确性取决于快照时机,
 *    而丢弃不可能出错. 旧实现快照 `playbackSpeed.value` 并在松手时写回, 一旦 onStart/onStop
 *    不配对, 快进倍速本身就会被当成基础倍速存下来 (甚至写回初值 `0f`, 在 mpv 上会直接抛异常).
 * 2. **只有这里调用 [PlaybackSpeed.set], 因此主线程规则只需在这一处保证.**
 *    ExoPlayer 要求主线程访问, 而 [PlaybackSpeed.set] 没有 `@UiThread` 标注, 从签名上看不出来;
 *    过去在后台线程调用会抛 wrong-thread 异常并终止倍速同步任务.
 * 3. **本类不从播放器读回倍速.** 各后端的 `PlaybackSpeed.valueFlow` 基本上只是「最后一次写入值」
 *    的镜像 (mpv 例外, 它会从事件线程回写真实值), 读回来只是自己的回声. 状态在本类手里,
 *    因此不需要区分「这次变化是不是我造成的」.
 *
 * ## 关于换 media
 *
 * 三个主力后端换 media 都不会丢失倍速, 因此不需要在切集/切数据源后重新应用:
 * - ExoPlayer: 倍速是 `PlaybackParameters`, 播放器级设置, `stop()` / `clearMediaItems()` 都不影响.
 * - mpv: `speed` 是全局属性, 跨 `loadfile` 保持.
 * - AVKit: `AVPlayer.rate` 兼任播放/暂停, 换 item 确实会清零, 但 `resumeImpl` 会用
 *   `playbackSpeedFeature.value` 重新起播. 这条自愈依赖那个镜像值是准的——也就依赖本类是唯一写者.
 *
 * @param baseSpeedFlow 基础倍速的 source of truth.
 * @param scope 与 [player] 同生命周期的 scope. 临时倍速必须活得比任何一次 composition 长,
 *   否则重组或离开 composition 会把长按状态丢在播放器上.
 */
class PlaybackSpeedController(
    private val player: MediampPlayer,
    baseSpeedFlow: Flow<Float>,
    scope: CoroutineScope,
    private val mainDispatcher: CoroutineContext = Dispatchers.Main,
) {
    private val _baseSpeed = MutableStateFlow(1f)

    /**
     * 当前的基础倍速. 倍速 UI 显示的是它, 因此长按快进期间 UI 不会跳到快进倍速.
     */
    val baseSpeed: StateFlow<Float> = _baseSpeed.asStateFlow()

    /**
     * 长按快进期间的临时倍速; `null` 表示当前没有临时倍速.
     */
    private val temporarySpeed = MutableStateFlow<Float?>(null)

    /**
     * 播放器上实际生效的倍速, 长按快进期间即为快进倍速.
     *
     * 它是 [baseSpeed] 与临时倍速的导出结果, 不单独保存, 因此不可能与两者不一致.
     * 需要按真实播放速率计算的显示 (例如剩余时间) 应当读它, 而倍速控件读 [baseSpeed].
     */
    val effectiveSpeed: StateFlow<Float> =
        combine(_baseSpeed, temporarySpeed) { base, temporary -> temporary ?: base }
            .stateIn(scope, SharingStarted.Eagerly, _baseSpeed.value)

    init {
        scope.launch {
            baseSpeedFlow.collect { _baseSpeed.value = it }
        }
        scope.launch {
            // StateFlow 本身去重, 相同值不会重复写入播放器.
            effectiveSpeed.collect { speed ->
                withContext(mainDispatcher) {
                    player.features[PlaybackSpeed]?.set(speed)
                }
            }
        }
    }

    /**
     * 开始临时倍速 (长按快进). 不改变基础倍速, 因此不会被持久化, 也不会污染倍速 UI.
     *
     * 幂等: 重复调用只是替换临时倍速, 基础倍速始终不受影响.
     */
    fun beginTemporarySpeed(speed: Float) {
        temporarySpeed.value = speed
    }

    /**
     * 结束临时倍速, 回到**当前**的基础倍速——不是长按开始时的那个值.
     *
     * 幂等: 没有临时倍速时调用是安全的.
     */
    fun endTemporarySpeed() {
        temporarySpeed.value = null
    }
}
