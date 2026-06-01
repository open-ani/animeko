/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.effects

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.preference.NoticeSoundKind
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

private val logger = logger("NoticeSound")

/**
 * 各音色落到哪一颗系统按键音.
 *
 * 与 ROM 里的音频文件的对应关系 (2026-08-11 真机 `/product/media/audio/ui/` 一共就这 6 个文件):
 * `FX_KEYPRESS_RETURN` → `KeypressReturn.ogg`, `FX_KEYPRESS_STANDARD` → `KeypressStandard.ogg`,
 * `FX_KEYPRESS_INVALID` → `KeypressInvalid.ogg`, `FX_KEYPRESS_DELETE` → `KeypressDelete.ogg`,
 * `FX_KEYPRESS_SPACEBAR` → `KeypressSpacebar.ogg`, `FX_KEY_CLICK` → `Effect_Tick.ogg`.
 *
 * 即"系统有几个音就给几个选项". `FX_FOCUS_NAVIGATION_*` 不单列: AOSP 里那 4 个与 `FX_KEY_CLICK`
 * 指向同一个 `Effect_Tick.ogg`, 列出来是四个一模一样的选项.
 */
private fun NoticeSoundKind.toSoundEffect(): Int? = when (this) {
    NoticeSoundKind.None -> null
    NoticeSoundKind.Confirm -> AudioManager.FX_KEYPRESS_RETURN
    NoticeSoundKind.Standard -> AudioManager.FX_KEYPRESS_STANDARD
    NoticeSoundKind.Alert -> AudioManager.FX_KEYPRESS_INVALID
    NoticeSoundKind.Tick -> AudioManager.FX_KEY_CLICK
    NoticeSoundKind.Delete -> AudioManager.FX_KEYPRESS_DELETE
    NoticeSoundKind.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
}

/**
 * 音量给满. 默认那一档 (传 -1 是"音乐音量 -3dB") 在电视喇叭上偏小, 而这声提示的前提就是用户
 * 没在看屏幕, 听不见等于没有.
 */
private const val NOTICE_SOUND_VOLUME = 1f

/**
 * 系统的"界面音效"开关 (设置里的按键音/触摸提示音), 用户关了就不响.
 *
 * 必须自己读: 只有不带 volume 的 `playSoundEffect(int)` 走的服务端方法里有
 * `querySoundEffectsEnabled` 这层门控, 我们为了 [NOTICE_SOUND_VOLUME] 用的是带 volume 的重载
 * (`IAudioService.playSoundEffectVolume`), 它是给"自己有音量面板的应用"用的, **不查**这个开关.
 * 也就是说不加这层判断的话, 用户在系统里关掉界面音效, 这声提示照样满音量响.
 */
private fun soundEffectsEnabled(context: Context): Boolean =
    Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0

/**
 * 响一声系统自带的按键音.
 *
 * 用 [AudioManager.playSoundEffect] 走系统 UI 音效那一套, 而**不是**通知音, 原因:
 * - **电视上通常压根没配通知音**. 2026-08-11 真机: `settings get system notification_sound` → `null`,
 *   整个 ROM 的 `/product/media/audio` 下只有 6 个按键音 + 2 个闹钟音, 一个通知音都没有 ——
 *   所以 `RingtoneManager` 那条路 (含枚举可用通知音) 在电视上必然是一声不响.
 *   **坑**: `RingtoneManager.getDefaultUri()` 返回的是 `content://settings/system/notification_sound`
 *   这个**符号 URI**, 系统有没有真的配过它都不为 null, 于是"拿不到就退回别的音"这种写法是死代码;
 *   真要判存在只能用 `getActualDefaultRingtoneUri(context, type)` (未设置时才返回 null).
 * - [android.media.ToneGenerator] 合成音试过, 音色难听.
 * - Animeko 自己一处按键音都不用, 所以这声按键音在应用内不会被误当成操作反馈.
 *
 * 这条路尊重系统的"界面音效"开关 (`Settings.System.SOUND_EFFECTS_ENABLED`), 用户关了就不响 ——
 * 这是刻意的, 不再另找退路硬响. 开关不是 [AudioManager] 帮我们查的, 见 [soundEffectsEnabled].
 */
@Composable
actual fun rememberNoticeSoundPlayer(): (NoticeSoundKind) -> Unit {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    LaunchedEffect(audioManager) {
        // 预载, 否则第一声经常被吞: 系统的 SoundPool 是懒加载的, 第一次 playSoundEffect 常常只
        // 触发加载而放不出声 —— 而这声提示往往一次会话只响一回, 吞掉就等于没有.
        //
        // 必须挪出主线程: loadSoundEffects() 是到 system_server 的**同步** binder 调用, 服务端
        // 还会等 SoundPool 把一批音频文件读完 (内部按 5 秒 ×3 轮超时). 开机后台正忙时, 主线程
        // 会被它按住数百毫秒到数秒, 表现为首页迟迟不出帧, 极端情况直接 ANR —— 而这只是一次预热,
        // 晚一点完成毫无影响.
        //
        // 不配对调用 unloadSoundEffects: 那是**全局**状态 (整机共用一个 SoundPool), 卸掉会连带
        // 影响系统 UI 自己的音效; 官方文档也只把它定位成"想省内存时才调".
        withContext(Dispatchers.IO) {
            runCatching { audioManager?.loadSoundEffects() }
                .onFailure { logger.warn(it) { "Failed to preload system sound effects" } }
        }
    }

    return remember(context, audioManager) {
        { kind ->
            val effect = kind.toSoundEffect()
            if (effect != null) {
                // 每次响之前重新读一次开关: 用户可能在应用运行期间改, 而这声提示一次会话最多响几回,
                // 读 Settings 的开销 (进程内有缓存) 可以忽略
                runCatching {
                    if (soundEffectsEnabled(context)) {
                        audioManager?.playSoundEffect(effect, NOTICE_SOUND_VOLUME)
                    }
                }.onFailure { logger.warn(it) { "Failed to play notice sound effect for $kind" } }
            }
        }
    }
}
