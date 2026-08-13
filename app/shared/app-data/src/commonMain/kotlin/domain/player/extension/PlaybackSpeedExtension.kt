/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.coroutines.CoroutineContext

/**
 * 将当前生效的倍速同步到播放器, 并持续跟随其变更.
 *
 * [playbackSpeedFlow] 由调用方 (通常是播放页 ViewModel) 提供, 其作用域即为一次播放:
 * 播放页内切集时本扩展会随新的 [EpisodeSession] 重新应用当前值, 因此倍速在切集后保持;
 * 播放页退出后该 flow 随之消失.
 *
 * 下发倍速有两处, 少一处就会得到一个"假倍速" (界面显示变了, 声音画面还是原速), 见 [onStart].
 */
class PlaybackSpeedExtension(
    private val context: PlayerExtensionContext,
    private val playbackSpeedFlow: Flow<Float>,
    /** 见 [onStart] 里为什么必须切回主线程. */
    private val mainDispatcher: CoroutineContext = Dispatchers.Main.immediate,
) : PlayerExtension("PlaybackSpeed") {
    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("PlaybackSpeed") {
            playbackSpeedFlow
                .distinctUntilChanged()
                .collect { speed -> applySpeed(speed) }
        }
        backgroundTaskScope.launch("PlaybackSpeedReapply") {
            // 起播之后补一次, 否则"记住的倍速"是个假倍速.
            //
            // 本扩展在会话开始时就下发倍速, 那时还没有任何媒体 —— 播放器层面的参数确实变成了
            // 新倍速 (界面显示的就是它), 但音频管线还不存在; 随后 PlayerSession.loadMedia 先
            // stopPlayback() 再 setMediaData(), 音频管线是在起播时才按新资源建起来的, 建好时
            // 没人再把倍速交给它, 于是画面声音都是原速, 界面上却写着 1.25x.
            //
            // 等到真的在播 (音频管线已就绪) 再下发一次, 才是真的变速.
            // 不加 distinctUntilChanged: mediaData 是 StateFlow, 本身就按值去重 ——
            // coroutines 1.11 起对 StateFlow 调它是编译错误 (Operator Fusion)
            context.player.mediaData.collectLatest { data ->
                if (data == null) return@collectLatest
                // 要的是**严格** isPlaying (时钟真的在走 = 音频管线已就绪), 不是 playWhenReady ——
                // 后者在管线还没建起来时就已经是 true, 那时补发等于没补
                context.player.state.first { it.isPlaying }
                // 1f 也要补: 它同样是一个需要下发的目标, 不是"不用管".
                //
                // 原来这里写着 `if (speed != 1f)`, 与 mediamp 0.3.0 里两处补发分支
                // (`desiredRate != 1f`) 犯的是同一个错. 后果见 TV 播放页"倍速还原补发"那段注释:
                // 长按倍速撞上缓冲时, 还原成 1f 的那一发会被整条链路一致地忽略掉, 播放器就一直
                // 停在长按时的倍速上, 而界面显示的是原速.
                applySpeed(playbackSpeedFlow.first(), force = true)
            }
        }
    }

    /**
     * @param force 播放器层面的参数已经是 [speed] 时也强制走一遍下发 (见实现里的注释).
     */
    private suspend fun applySpeed(speed: Float, force: Boolean = false) {
        // 必须切到主线程再碰播放器 (同 PlayerSession.loadMedia 里的 player.setMediaData()):
        // 本任务跑在 Dispatchers.Default, 而 ExoPlayer 有 application thread 检查,
        // 从别的线程调会抛 "Player is accessed on the wrong thread".
        //
        // 抛出的后果不只是"倍速没应用": mediamp 的实现是先写 valueFlow 再调播放器
        // (PlaybackSpeedImpl.set), 于是界面上的倍速已经变成新值而播放器还是原速 ——
        // 又是一种假倍速; 而且这个任务就此挂掉, 之后倍速的任何变更也都不再生效.
        withContext(mainDispatcher) {
            val playbackSpeed = context.player.features[PlaybackSpeed] ?: return@withContext
            if (force && playbackSpeed.value == speed) {
                // ExoPlayer 对"设成当前值"直接返回, 音频管线就收不到这次变更 (这正是补发时的处境:
                // 播放器层面早就是这个值了). 先设一个别的值再设回去, 逼它把变更下发下去.
                //
                // 中转值必须与目标**不同**: 原来固定用 1f, 于是目标本身是 1f 时等于没中转 ——
                // 恰好就是"长按倍速没还原干净"那条路上最需要它的场景.
                playbackSpeed.set(if (speed == 1f) FORCE_NUDGE_SPEED else 1f)
            }
            logger.info { "Applying playback speed $speed (force=$force)" }
            playbackSpeed.set(speed)
        }
    }

    class Factory(
        private val playbackSpeedFlow: Flow<Float>,
    ) : EpisodePlayerExtensionFactory<PlaybackSpeedExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): PlaybackSpeedExtension {
            return PlaybackSpeedExtension(context, playbackSpeedFlow)
        }
    }

    private companion object {
        private val logger = logger<PlaybackSpeedExtension>()

        /** 目标是 1x 时的中转值: 只为让播放器认出"这是一次变更", 同一轮命令内就被目标值覆盖. */
        private const val FORCE_NUDGE_SPEED = 1.01f
    }
}
