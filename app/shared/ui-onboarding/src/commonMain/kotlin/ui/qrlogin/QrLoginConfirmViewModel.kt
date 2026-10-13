/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.qrlogin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.user.QrLoginRepository
import me.him188.ani.app.data.repository.user.QrLoginScanResult
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.canAccessAniApiNow
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * 已登录的设备扫码后的确认页: 先向服务器标记已扫码并取得待登录设备的信息, 再由用户批准或拒绝.
 */
class QrLoginConfirmViewModel(
    private val requestId: String,
    private val repository: QrLoginRepository,
    private val isLoggedIn: suspend () -> Boolean,
    val selfNickname: Flow<String?>,
) : AbstractViewModel() {
    private val _state = MutableStateFlow<QrLoginConfirmUiState>(QrLoginConfirmUiState.Loading)
    val state: StateFlow<QrLoginConfirmUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** 重新标记扫码. 用于 [QrLoginConfirmUiState.Failed] 后重试. */
    fun load() {
        _state.value = QrLoginConfirmUiState.Loading
        backgroundScope.launch {
            _state.value = try {
                if (!isLoggedIn()) {
                    QrLoginConfirmUiState.RequiresLogin
                } else when (val result = repository.scan(requestId)) {
                    is QrLoginScanResult.Success -> QrLoginConfirmUiState.Confirming(result.deviceName)
                    QrLoginScanResult.Expired -> QrLoginConfirmUiState.Expired
                    QrLoginScanResult.AlreadyHandled -> QrLoginConfirmUiState.AlreadyHandled
                }
            } catch (e: RepositoryException) {
                QrLoginConfirmUiState.Failed(LoadError.fromException(e))
            }
        }
    }

    fun confirm(approve: Boolean) {
        val current = _state.value as? QrLoginConfirmUiState.Confirming ?: return
        if (current.submitting) return
        _state.value = current.copy(submitting = true, error = null)
        backgroundScope.launch {
            _state.value = try {
                when {
                    !repository.confirm(requestId, approve) -> QrLoginConfirmUiState.Expired
                    approve -> QrLoginConfirmUiState.Approved(current.deviceName)
                    else -> QrLoginConfirmUiState.Rejected
                }
            } catch (e: RepositoryException) {
                current.copy(submitting = false, error = LoadError.fromException(e))
            }
        }
    }

    companion object : KoinComponent {
        fun create(requestId: String): QrLoginConfirmViewModel {
            val sessionStateProvider = get<SessionStateProvider>()
            return QrLoginConfirmViewModel(
                requestId,
                repository = get(),
                isLoggedIn = { sessionStateProvider.canAccessAniApiNow() },
                selfNickname = get<UserRepository>().selfInfoFlow.map { it?.nickname?.takeIf(String::isNotBlank) },
            )
        }
    }
}

sealed interface QrLoginConfirmUiState {
    /** 正在向服务器标记已扫码 */
    data object Loading : QrLoginConfirmUiState

    /** 当前设备没有登录, 无法为其他设备登录 */
    data object RequiresLogin : QrLoginConfirmUiState

    data class Confirming(
        val deviceName: String?,
        val submitting: Boolean = false,
        /** 上一次提交失败的原因. 可以重试. */
        val error: LoadError? = null,
    ) : QrLoginConfirmUiState

    data class Approved(val deviceName: String?) : QrLoginConfirmUiState
    data object Rejected : QrLoginConfirmUiState

    /** 二维码已过期 */
    data object Expired : QrLoginConfirmUiState

    /** 二维码已被其他账号扫描, 或已经处理过 */
    data object AlreadyHandled : QrLoginConfirmUiState
    data class Failed(val error: LoadError) : QrLoginConfirmUiState
}
