/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Platform-dependent network type detector.
 *
 * Use [createMeteredNetworkDetector] to create instance.
 */
interface MeteredNetworkDetector {
    val isMeteredNetworkFlow: Flow<Boolean>

    /** 可识别的默认网络身份与连通状态；平台无法判断时为 null。 */
    val networkEnvironmentFlow: Flow<NetworkEnvironment?> get() = flowOf(null)

    /**
     * Dispose listeners or callbacks which may be created at initializing detector.
     */
    fun dispose()
}

data class NetworkEnvironment(val identity: String?, val isConnected: Boolean)

object NoopMeteredNetworkDetector : MeteredNetworkDetector {
    override val isMeteredNetworkFlow: Flow<Boolean> = flowOf(false)

    override fun dispose() {
    }
}


expect fun createMeteredNetworkDetector(context: ContextMP): MeteredNetworkDetector