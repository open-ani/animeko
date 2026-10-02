/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import androidx.compose.runtime.Stable
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.update.UpdateManager
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.ktor.getPlatformKtorEngine
import me.him188.ani.utils.platform.currentPlatform
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 开发者功能「安装 main 分支的指定 commit」页面的 ViewModel. 持有 GitHub 请求用的 [HttpClient] 并在页面销毁时关闭.
 */
@Stable
class DevBuildsViewModel : AbstractViewModel(), KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    private val updateManager: UpdateManager by inject()
    private val installer: UpdateInstaller by inject()

    private val client = HttpClient(getPlatformKtorEngine()) {
        // GitHubDevBuildApi 手动处理 artifact 下载的重定向, 并自行检查状态码
        followRedirects = false
        expectSuccess = false
    }

    /**
     * 当前平台不支持时为 `null`.
     */
    val state: DevBuildsState? = DevBuildPackageSpec.forPlatform(currentPlatform())?.let { spec ->
        DevBuildsState(
            api = GitHubDevBuildApi(client),
            spec = spec,
            installer = installer,
            saveDir = updateManager.devBuildsDir,
            getToken = { settingsRepository.debugSettings.flow.first().devBuildGitHubToken },
            currentVersionName = currentAniBuildConfig.versionName,
            backgroundScope = backgroundScope,
        )
    }

    val gitHubToken: StateFlow<String> = settingsRepository.debugSettings.flow
        .map { it.devBuildGitHubToken }
        .stateInBackground("")

    fun setGitHubToken(token: String) {
        backgroundScope.launch {
            settingsRepository.debugSettings.update { copy(devBuildGitHubToken = token.trim()) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.close()
    }
}
