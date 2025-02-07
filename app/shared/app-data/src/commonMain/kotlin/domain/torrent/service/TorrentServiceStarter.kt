/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service

/**
 * 启动 torrent 服务的接口, 并获取[服务通信对象][T].
 */
interface TorrentServiceStarter<T : Any> {
    /**
     * 启动服务.
     *
     * 这个方法会抛出 [ServiceStartException] 异常, 并且可能会在任何时机取消.
     *
     * @return 服务通信对象
     */
    suspend fun start(): T
}

sealed class ServiceStartException : Exception() {
    /**
     * 详情查看 `Context.startForegroundService`.
     */
    data class StartFailed(override val cause: Throwable? = null) : ServiceStartException()

    /**
     * 服务启动了, 但服务回应了启动失败, 详情查看 [AniTorrentService][me.him188.ani.app.domain.torrent.service.AniTorrentService] 中的 `INTENT_STARTUP`.
     */
    data object StartRespondFailure : ServiceStartException()

    /**
     * 绑定服务失败, 详情查看 `Context.bindService`.
     */
    data object BindServiceFailed : ServiceStartException()

    /**
     * 绑定成功, 但是获取了空服务通信对象.
     */
    data object NullBinder : ServiceStartException()

    /**
     * 服务在等待通信对象的时候意外断开了连接. 详情查看 `android.content.ServiceConnection` 中的 `onServiceDisconnected`.
     */
    data object DisconnectedUnexpectedly : ServiceStartException()
}