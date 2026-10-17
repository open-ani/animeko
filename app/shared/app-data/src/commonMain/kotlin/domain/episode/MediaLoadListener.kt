/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.player.VideoLoadingState
import kotlin.time.Duration

/**
 * [PlayerSession.loadMedia] 的阶段.
 */
enum class MediaLoadPhase {
    /** 解析资源: 网页取流链接, 下载种子元数据. */
    RESOLVE,

    /** 打开资源: 选择种子内文件, 等待云盘就绪, 准备 HLS 代理. */
    OPEN,

    /** 交给播放器打开. */
    SET_MEDIA_DATA,
}

/**
 * 观察一次 [PlayerSession.loadMedia] 的各阶段. 时长都是自 `loadMedia` 开始的累计时长.
 *
 * 一次调用以 [onReady], [onFailed] 或 [onCancelled] 之一结束.
 */
interface MediaLoadListener {
    /**
     * 开始解析. [resolverName] 为将要处理该资源的解析器的简单类名, 没有解析器支持时为 `null`.
     */
    fun onResolveStart(resolverName: String?) {}

    /**
     * 解析完成. [engineKey] 为将要承载播放的种子引擎, 不经过种子引擎时为 `null`.
     */
    fun onResolved(engineKey: MediaCacheEngineKey?, elapsed: Duration) {}

    /**
     * 打开完成. [engineKey] 为实际承载播放的引擎, 打开阶段可能从云盘回退到本地 BT.
     */
    fun onOpened(engineKey: MediaCacheEngineKey?, elapsed: Duration) {}

    /**
     * 播放器已打开媒体 ([VideoLoadingState.Succeed]).
     */
    fun onReady(elapsed: Duration) {}

    /**
     * 加载失败. 具体原因在 [state] 里, [VideoLoadingState.UnknownError.cause] 带原始异常.
     */
    fun onFailed(phase: MediaLoadPhase, state: VideoLoadingState.Failed, elapsed: Duration) {}

    /**
     * 加载被取消: 切换资源, 切集或离开播放页.
     */
    fun onCancelled(phase: MediaLoadPhase, elapsed: Duration) {}

    companion object Noop : MediaLoadListener
}
