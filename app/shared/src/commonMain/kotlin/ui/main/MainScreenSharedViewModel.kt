/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.foundation.ServerListFeature
import me.him188.ani.app.domain.foundation.ServerListFeatureConfig
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.foundation.withValue
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.settings.ServiceConnectionTester
import me.him188.ani.app.domain.settings.ServiceConnectionTesters
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.datasources.bangumi.BangumiClientImpl
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

open class MainScreenSharedViewModel(private val koin: Koin = GlobalKoin) : AbstractViewModel(), KoinComponent {
    final override fun getKoin(): Koin = koin
    private val userRepository: UserRepository by inject()
    private val sessionStateProvider: SessionStateProvider by inject()
    private val logoutMutex = Mutex()
    private val logoutErrorChannel = Channel<LoadError>(Channel.BUFFERED)
    val logoutErrors = logoutErrorChannel.receiveAsFlow()

    /** 网络暂不可用时保留本地账号，失效会话仍要求重新登录。 */
    val accountState = combine(sessionStateProvider.stateFlow, userRepository.selfInfoFlow) { session, info ->
        val loggedIn = session is SessionState.Valid ||
            (session is SessionState.Invalid && session.reason == InvalidSessionReason.NETWORK_ERROR && info != null)
        SelfInfoUiState(info.takeIf { loggedIn }, false, loggedIn, (session as? SessionState.Valid)?.bangumiConnected == true)
    }.stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), SelfInfoUiState(null, true, null, null))

    fun logout() {
        if (!logoutMutex.tryLock()) return
        launchInBackground {
            try {
                userRepository.clearSelfInfo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logoutErrorChannel.send(LoadError.fromException(e))
            } finally {
                logoutMutex.unlock()
            }
        }
    }

    val selfInfo = SelfInfoStateProducer(koin = getKoin()).flow

    private val clientProvider: HttpClientProvider by inject()

    private val networkCheckFailedChannel = Channel<Unit>(Channel.BUFFERED)

    /**
     * 启动时检测 Animeko 服务连接, 失败时发出一个事件, UI 提示用户检查网络或配置代理.
     */
    val networkCheckFailed: Flow<Unit> = networkCheckFailedChannel.receiveAsFlow()

    init {
        launchInBackground {
            val client = clientProvider.get(
                setOf(ServerListFeature.withValue(ServerListFeatureConfig.Default)),
            )
            val tester = ServiceConnectionTesters.createDefault(
                bangumiClient = BangumiClientImpl(client),
                aniClient = client,
                serviceIds = setOf(ServiceConnectionTesters.ID_ANI),
            )
            coroutineScope {
                launch { tester.testAll() }
                // 内部 state 是 StateFlow, 测试完成后再订阅也能拿到最终结果
                val results = tester.results.first { it.allCompleted() }
                val state = results.findStateById(ServiceConnectionTesters.ID_ANI)
                if (state != null && state !is ServiceConnectionTester.TestState.Success) {
                    networkCheckFailedChannel.send(Unit)
                }
            }
        }
    }
}
