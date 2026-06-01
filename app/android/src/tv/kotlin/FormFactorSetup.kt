/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.android.tv.InstallTvPageVariants
import me.him188.ani.android.tv.TvHomeChannels
import me.him188.ani.app.ui.foundation.AniUiBehavior
import me.him188.ani.app.ui.tv.TvAniUiBehavior
import org.koin.android.ext.android.getKoin

/*
 * 形态适配接缝 (tv 变体): 与 src/phone 下的同名文件一一对应, MainActivity 只调用它.
 * 遥控器形态的全部差异都收在这里 —— 界面行为开关 + 页面变体 + 主屏频道初始化.
 */

/** 遥控器设备的界面行为. */
internal val formFactorUiBehavior: AniUiBehavior get() = TvAniUiBehavior

/** 把遥控器形态的页面实现注入共享页面的变体插槽. */
@Composable
internal fun InstallFormFactorUi(content: @Composable () -> Unit) = InstallTvPageVariants(content)

/** 主屏预览频道 (热门动画 / 继续观看): 每进程只跑一次, 延迟到启动高峰之后. */
internal fun onFormFactorActivityCreated(activity: ComponentActivity) {
    activity.lifecycleScope.launch {
        delay(TV_HOME_CHANNELS_DELAY_MILLIS)
        // 需要 activity context 才能弹出添加频道的系统确认框
        TvHomeChannels.updateOnce(activity, activity.getKoin())
    }
}

private const val TV_HOME_CHANNELS_DELAY_MILLIS = 10_000L
