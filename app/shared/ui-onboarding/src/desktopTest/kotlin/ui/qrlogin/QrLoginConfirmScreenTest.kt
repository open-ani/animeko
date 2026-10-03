/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.qrlogin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.user.QrLoginPollResult
import me.him188.ani.app.data.repository.user.QrLoginRepository
import me.him188.ani.app.data.repository.user.QrLoginScanResult
import me.him188.ani.app.data.repository.user.QrLoginSession
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QrLoginConfirmScreenTest {
    private class FakeRepository(
        var scanResult: () -> QrLoginScanResult = { QrLoginScanResult.Success("Living room TV") },
        var confirmResult: () -> Boolean = { true },
    ) : QrLoginRepository {
        val confirmed = mutableListOf<Boolean>()

        override suspend fun createSession(deviceName: String?): QrLoginSession = error("unused")
        override suspend fun poll(session: QrLoginSession): QrLoginPollResult = error("unused")
        override suspend fun scan(requestId: String): QrLoginScanResult = scanResult()
        override suspend fun confirm(requestId: String, approve: Boolean): Boolean {
            confirmed += approve
            return confirmResult()
        }
    }

    private fun viewModel(repository: QrLoginRepository, loggedIn: Boolean = true) = QrLoginConfirmViewModel(
        "request-1", repository, isLoggedIn = { loggedIn }, selfNickname = flowOf("小明"),
    )

    @Test
    fun `shows the device and approves`() = runAniComposeUiTest {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        var closed = false
        setContent {
            ProvideCompositionLocalsForPreview {
                QrLoginConfirmScreen(vm, onNavigateBack = { closed = true }, onNavigateLogin = {})
            }
        }

        waitUntil { vm.state.value is QrLoginConfirmUiState.Confirming }
        onNodeWithTag("qr-login-device-name").assertIsDisplayed().assertTextEquals("Living room TV")
        onNodeWithTag("qr-login-approve").performClick()

        waitUntil { vm.state.value is QrLoginConfirmUiState.Approved }
        assertEquals(listOf(true), repository.confirmed)
        onNodeWithTag("qr-login-primary-action").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(true, closed)
    }

    @Test
    fun `rejects`() = runAniComposeUiTest {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        setContent {
            ProvideCompositionLocalsForPreview {
                QrLoginConfirmScreen(vm, onNavigateBack = {}, onNavigateLogin = {})
            }
        }

        waitUntil { vm.state.value is QrLoginConfirmUiState.Confirming }
        onNodeWithTag("qr-login-reject").performClick()
        waitUntil { vm.state.value == QrLoginConfirmUiState.Rejected }
        assertEquals(listOf(false), repository.confirmed)
    }

    @Test
    fun `a failed confirm can be retried`() = runAniComposeUiTest {
        val repository = FakeRepository(confirmResult = { throw RepositoryNetworkException() })
        val vm = viewModel(repository)
        setContent {
            ProvideCompositionLocalsForPreview {
                QrLoginConfirmScreen(vm, onNavigateBack = {}, onNavigateLogin = {})
            }
        }

        waitUntil { vm.state.value is QrLoginConfirmUiState.Confirming }
        onNodeWithTag("qr-login-approve").performClick()
        waitUntil { (vm.state.value as? QrLoginConfirmUiState.Confirming)?.error != null }

        repository.confirmResult = { true }
        onNodeWithTag("qr-login-approve").performClick()
        waitUntil { vm.state.value is QrLoginConfirmUiState.Approved }
    }

    @Test
    fun `does not scan when logged out and offers login`() = runAniComposeUiTest {
        val vm = viewModel(FakeRepository(scanResult = { error("must not scan") }), loggedIn = false)
        var login = false
        setContent {
            ProvideCompositionLocalsForPreview {
                QrLoginConfirmScreen(vm, onNavigateBack = {}, onNavigateLogin = { login = true })
            }
        }

        waitUntil { vm.state.value == QrLoginConfirmUiState.RequiresLogin }
        onNodeWithTag("qr-login-primary-action").performClick()
        waitForIdle()
        assertEquals(true, login)
    }

    @Test
    fun `expired, handled and failed scans end the flow`() = runAniComposeUiTest {
        val repository = FakeRepository(scanResult = { QrLoginScanResult.Expired })
        val vm = viewModel(repository)
        setContent {
            ProvideCompositionLocalsForPreview {
                QrLoginConfirmScreen(vm, onNavigateBack = {}, onNavigateLogin = {})
            }
        }
        waitUntil { vm.state.value == QrLoginConfirmUiState.Expired }
        onNodeWithTag("qr-login-approve").assertDoesNotExist()

        repository.scanResult = { QrLoginScanResult.AlreadyHandled }
        vm.load()
        waitUntil { vm.state.value == QrLoginConfirmUiState.AlreadyHandled }

        repository.scanResult = { throw RepositoryNetworkException() }
        vm.load()
        waitUntil { vm.state.value is QrLoginConfirmUiState.Failed }

        // 重试
        repository.scanResult = { QrLoginScanResult.Success(null) }
        onNodeWithTag("qr-login-primary-action").performClick()
        waitUntil { vm.state.value is QrLoginConfirmUiState.Confirming }
        assertIs<QrLoginConfirmUiState.Confirming>(vm.state.value)
    }
}
