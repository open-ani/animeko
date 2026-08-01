/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.hls.HlsPlaybackPreparer
import me.him188.ani.app.domain.media.hls.PlatformHlsPlaybackPreparer
import me.him188.ani.app.platform.AndroidPermissionManager
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.videoplayer.media.LibassExoPlayerMediampPlayerFactory
import org.koin.dsl.module
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.MediampPlayerFactoryLoader
import org.openani.mediamp.compose.MediampPlayerSurfaceProviderLoader
import org.openani.mediamp.exoplayer.compose.ExoPlayerMediampPlayerSurfaceProvider

/**
 * `default` (手机) 与 `tv` 两个 flavor 共用的 Android 平台绑定.
 *
 * 本文件位于交集源集 `src/main` —— 按 variant 各编译一次, 引用任一 flavor 专属符号会打挂另一个
 * variant 的编译, 交集纯净性由编译器守护 (atv-architecture.md §4.3-R2).
 *
 * flavor 专属绑定 (torrent/缓存链路、BrowserNavigator、Captcha、UpdateInstaller、MediaResolver、
 * AppTerminator 等) 分别位于 `src/default` 的 [getAndroidModules] 与 `src/tv` 的 TvAndroidModules.
 */
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun getCommonAndroidModules(coroutineScope: CoroutineScope) = module {
    single<PermissionManager> {
        AndroidPermissionManager()
    }
    single<HlsPlaybackPreparer> { PlatformHlsPlaybackPreparer(get()) }

    single<MediampPlayerFactory<*>> {
        val videoScaffoldConfig = get<SettingsRepository>().videoScaffoldConfig
        MediampPlayerFactoryLoader.register(
            LibassExoPlayerMediampPlayerFactory {
                // 音频处理链在 ExoPlayer 构造时确定, 无法在已创建的播放器上切换.
                // 工厂接口是同步的, 因此每次创建播放器时在此读取 DataStore 中的当前值.
                runBlocking { videoScaffoldConfig.flow.first().enableHighQualityAudioTimeStretch }
            },
        )
        MediampPlayerSurfaceProviderLoader.register(ExoPlayerMediampPlayerSurfaceProvider())
        MediampPlayerFactoryLoader.first()
    }
}
