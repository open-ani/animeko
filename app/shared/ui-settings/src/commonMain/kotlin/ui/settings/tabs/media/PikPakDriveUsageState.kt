/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.ui.settings.framework.ConnectionTestResult
import me.him188.ani.app.ui.settings.framework.ConnectionTester
import me.him188.ani.app.ui.settings.framework.SingleTester
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.torrent.pikpak.PikPakDriveUsage
import me.him188.ani.torrent.pikpak.PikPakNotConfiguredException
import kotlin.coroutines.cancellation.CancellationException

@Stable
sealed interface PikPakDriveUsagePresentation {
    data object Idle : PikPakDriveUsagePresentation

    data object SignedOut : PikPakDriveUsagePresentation

    data class Failed(val message: String) : PikPakDriveUsagePresentation

    data class Loaded(val usage: PikPakDriveUsage) : PikPakDriveUsagePresentation {
        val free: FileSize get() = (usage.accountLimitBytes - usage.accountUsedBytes).coerceAtLeast(0L).bytes
    }
}

@Stable
class PikPakDriveUsageState(
    backgroundScope: CoroutineScope,
    private val fetchUsage: suspend () -> PikPakDriveUsage?,
) {
    var presentation: PikPakDriveUsagePresentation by mutableStateOf(PikPakDriveUsagePresentation.Idle)
        private set

    private val runner = SingleTester(ConnectionTester(id = "pikpak-drive", testConnection = ::runCheck), backgroundScope)

    val tester get() = runner.tester

    fun check() = runner.testAll()

    private suspend fun runCheck(): ConnectionTestResult {
        val result = load()
        presentation = result
        return if (result is PikPakDriveUsagePresentation.Loaded) {
            ConnectionTestResult.SUCCESS
        } else {
            ConnectionTestResult.FAILED
        }
    }

    // 自己吞掉失败而不是让 Tester 的 onError 兜: 失败的原因要显示出来, 而 onError 只剩一个 FAILED.
    private suspend fun load(): PikPakDriveUsagePresentation = try {
        val usage = fetchUsage()
        if (usage == null) PikPakDriveUsagePresentation.SignedOut
        else PikPakDriveUsagePresentation.Loaded(usage)
    } catch (e: CancellationException) {
        throw e
    } catch (e: PikPakNotConfiguredException) {
        // Credentials missing or incomplete: not a failure to show, the account is simply not set up
        PikPakDriveUsagePresentation.SignedOut
    } catch (e: Throwable) {
        PikPakDriveUsagePresentation.Failed(e.describe())
    }

    /** Forgets what was shown, for an account that changed since. */
    fun reset() {
        presentation = PikPakDriveUsagePresentation.Idle
    }
}

private fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: toString()
